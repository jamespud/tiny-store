package com.github.spud.tinystore.gateway.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import reactor.core.publisher.Mono;

@Service
public class DynamicRouteService implements InitializingBean {

	private static final Logger log = LoggerFactory.getLogger(DynamicRouteService.class);

	private final RouteDefinitionWriter routeDefinitionWriter;
	private final ApplicationEventPublisher publisher;
	private final GatewayDynamicProperties properties;
	private final ResourceLoader resourceLoader;
	private final GatewayPolicyRegistry policyRegistry;
	private final ObjectMapper yamlMapper;
	private final Set<String> activeRouteIds = new HashSet<>();
	private Instant lastSuccessfulRefresh;
	/** Last route table that applied cleanly; used to roll back a failed apply (P1-6). */
	private Snapshot lastGoodSnapshot;

	public DynamicRouteService(RouteDefinitionWriter routeDefinitionWriter,
		ApplicationEventPublisher publisher,
		GatewayDynamicProperties properties,
		ResourceLoader resourceLoader,
		GatewayPolicyRegistry policyRegistry) {
		this.routeDefinitionWriter = routeDefinitionWriter;
		this.publisher = publisher;
		this.properties = properties;
		this.resourceLoader = resourceLoader;
		this.policyRegistry = policyRegistry;
		this.yamlMapper = new ObjectMapper(new YAMLFactory())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	public void afterPropertiesSet() {
		refreshRoutes();
	}

	@Scheduled(initialDelay = 0, fixedDelayString = "${gateway.dynamic.refresh-interval}")
	public void scheduledRefresh() {
		refreshRoutes();
	}

	public synchronized void refreshRoutes() {
		Optional<GatewayRoutesDefinition> optionalDefinition = loadDefinition();
		if (optionalDefinition.isEmpty()) {
			log.warn("No gateway route definition found at {}", properties.getRoutesLocation());
			return;
		}

		GatewayRoutesDefinition definition = optionalDefinition.get();
		if (CollectionUtils.isEmpty(definition.getRoutes())) {
			log.warn("Route definition file {} contains no routes", properties.getRoutesLocation());
			clearActiveRoutes();
			return;
		}

		// P1-6: validate-then-swap. Phase 1 builds the complete replacement snapshot without touching live
		// state, so a bad edit (a route that fails to compile, a malformed uri) leaves the gateway exactly as
		// it was. The previous code deleted routes and cleared the policy registry before compiling, so one
		// bad route could leave live routes without rate-limit/idempotency policies.
		Snapshot snapshot;
		try {
			snapshot = buildSnapshot(definition);
		}
		catch (RuntimeException ex) {
			log.error("Gateway route refresh rejected; keeping the last known good route table "
				+ "({} routes active)", activeRouteIds.size(), ex);
			return;
		}

		Snapshot previous = lastGoodSnapshot;
		try {
			applySnapshot(snapshot);
			lastGoodSnapshot = snapshot;
		}
		catch (RuntimeException ex) {
			log.error("Gateway route refresh failed while applying the new table; rolling back to the "
				+ "previous snapshot", ex);
			if (previous != null) {
				try {
					applySnapshot(previous);
					lastGoodSnapshot = previous;
				}
				catch (RuntimeException rollbackFailure) {
					log.error("Gateway route rollback failed; the route table may be inconsistent until the "
						+ "next successful refresh", rollbackFailure);
				}
			}
		}
	}

	/** The complete replacement route table, built before anything live is touched. */
	private Snapshot buildSnapshot(GatewayRoutesDefinition definition) {
		Map<String, RouteDefinition> routes = new LinkedHashMap<>();
		Map<String, GatewayRoutesDefinition.RoutePolicies> policies = new LinkedHashMap<>();
		for (GatewayRoutesDefinition.RouteDefinition routeDefinition : definition.getRoutes()) {
			if (routeDefinition.getId() == null || routeDefinition.getUri() == null) {
				throw new IllegalStateException("Route without id or uri: " + routeDefinition);
			}
			RouteDefinition rd = new RouteDefinition();
			rd.setId(routeDefinition.getId());
			rd.setUri(URI.create(routeDefinition.getUri()));
			if (!CollectionUtils.isEmpty(routeDefinition.getPredicates())) {
				rd.setPredicates(routeDefinition.getPredicates().stream()
					.map(PredicateDefinition::new)
					.collect(Collectors.toList()));
			}
			// Throws for a route whose retry policy is unsafe; that is the point of compiling here.
			List<FilterDefinition> filters = GatewayRouteFilters.compile(routeDefinition);
			if (!CollectionUtils.isEmpty(filters)) {
				rd.setFilters(filters);
			}
			routes.put(rd.getId(), rd);
			policies.put(routeDefinition.getId(), routeDefinition.getPolicies());
		}
		return new Snapshot(routes, policies);
	}

	private void applySnapshot(Snapshot snapshot) {
		Set<String> routesToRemove = new HashSet<>(activeRouteIds);
		routesToRemove.removeAll(snapshot.routes().keySet());
		for (String routeId : routesToRemove) {
			deleteRoute(routeId);
		}
		for (RouteDefinition definition : snapshot.routes().values()) {
			deleteRoute(definition.getId());
			saveRoute(definition);
		}
		policyRegistry.replaceAll(snapshot.policies());
		activeRouteIds.clear();
		activeRouteIds.addAll(snapshot.routes().keySet());
		publisher.publishEvent(new RefreshRoutesEvent(this));
		lastSuccessfulRefresh = Instant.now();
		log.info("Refreshed {} gateway routes", snapshot.routes().size());
	}

	private record Snapshot(Map<String, RouteDefinition> routes,
		Map<String, GatewayRoutesDefinition.RoutePolicies> policies) {
	}

	private void clearActiveRoutes() {
		for (String routeId : activeRouteIds) {
			deleteRoute(routeId);
		}
		activeRouteIds.clear();
		policyRegistry.clear();
	}

	private void saveRoute(RouteDefinition definition) {
		routeDefinitionWriter.save(Mono.just(definition)).onErrorResume(ex -> {
			log.error("Failed to save route {}", definition.getId(), ex);
			return Mono.empty();
		}).block();
	}

	private void deleteRoute(String routeId) {
		routeDefinitionWriter.delete(Mono.just(routeId)).onErrorResume(NotFoundException.class, ex -> Mono.empty())
			.onErrorResume(ex -> {
				log.error("Failed to delete route {}", routeId, ex);
				return Mono.empty();
			}).block();
	}

	private Optional<GatewayRoutesDefinition> loadDefinition() {
		Resource resource = resourceLoader.getResource(properties.getRoutesLocation());
		if (!resource.exists()) {
			return Optional.empty();
		}

		try (InputStream inputStream = resource.getInputStream()) {
			GatewayRoutesDefinition definition = yamlMapper.readValue(inputStream, GatewayRoutesDefinition.class);
			return Optional.ofNullable(definition);
		}
		catch (IOException ex) {
			log.error("Failed to read gateway routes definition from {}", properties.getRoutesLocation(), ex);
			return Optional.empty();
		}
	}

	public Optional<Instant> getLastSuccessfulRefresh() {
		return Optional.ofNullable(lastSuccessfulRefresh);
	}
}
