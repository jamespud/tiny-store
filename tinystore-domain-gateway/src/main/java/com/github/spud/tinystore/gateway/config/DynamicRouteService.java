package com.github.spud.tinystore.gateway.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
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

		Set<String> newRouteIds = definition.getRoutes().stream()
			.map(GatewayRoutesDefinition.RouteDefinition::getId)
			.collect(Collectors.toSet());
		Set<String> routesToRemove = new HashSet<>(activeRouteIds);
		routesToRemove.removeAll(newRouteIds);

		for (String routeId : routesToRemove) {
			deleteRoute(routeId);
		}

		policyRegistry.clear();

		Map<String, GatewayRoutesDefinition.RoutePolicies> policies = new HashMap<>();
		for (GatewayRoutesDefinition.RouteDefinition routeDefinition : definition.getRoutes()) {
			if (routeDefinition.getId() == null || routeDefinition.getUri() == null) {
				log.warn("Skip route without id or uri: {}", routeDefinition);
				continue;
			}
			deleteRoute(routeDefinition.getId());
			RouteDefinition rd = new RouteDefinition();
			rd.setId(routeDefinition.getId());
			rd.setUri(URI.create(routeDefinition.getUri()));
			if (!CollectionUtils.isEmpty(routeDefinition.getPredicates())) {
				rd.setPredicates(routeDefinition.getPredicates().stream()
					.map(PredicateDefinition::new)
					.collect(Collectors.toList()));
			}
			if (!CollectionUtils.isEmpty(routeDefinition.getFilters())) {
				rd.setFilters(routeDefinition.getFilters().stream()
					.map(FilterDefinition::new)
					.collect(Collectors.toList()));
			}

			saveRoute(rd);
			policies.put(routeDefinition.getId(), routeDefinition.getPolicies());
		}

		policies.forEach(policyRegistry::registerPolicies);
		activeRouteIds.clear();
		activeRouteIds.addAll(policies.keySet());

		publisher.publishEvent(new RefreshRoutesEvent(this));
		lastSuccessfulRefresh = Instant.now();
		log.info("Refreshed {} gateway routes", policies.size());
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
