package com.github.spud.tinystore.gateway.config;

import java.util.ArrayList;
import java.util.List;

public class GatewayRoutesDefinition {

	private List<RouteDefinition> routes = new ArrayList<>();

	public List<RouteDefinition> getRoutes() {
		return routes;
	}

	public void setRoutes(List<RouteDefinition> routes) {
		this.routes = routes;
	}

	public static class RouteDefinition {

		private String id;
		private String uri;
		private List<String> predicates = new ArrayList<>();
		private List<String> filters = new ArrayList<>();
		private RoutePolicies policies = new RoutePolicies();

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getUri() {
			return uri;
		}

		public void setUri(String uri) {
			this.uri = uri;
		}

		public List<String> getPredicates() {
			return predicates;
		}

		public void setPredicates(List<String> predicates) {
			this.predicates = predicates;
		}

		public List<String> getFilters() {
			return filters;
		}

		public void setFilters(List<String> filters) {
			this.filters = filters;
		}

		public RoutePolicies getPolicies() {
			return policies;
		}

		public void setPolicies(RoutePolicies policies) {
			this.policies = policies;
		}
	}

	public static class RoutePolicies {

		private RateLimitPolicy rateLimit;
		private IdempotencyPolicy idempotency;

		public RateLimitPolicy getRateLimit() {
			return rateLimit;
		}

		public void setRateLimit(RateLimitPolicy rateLimit) {
			this.rateLimit = rateLimit;
		}

		public IdempotencyPolicy getIdempotency() {
			return idempotency;
		}

		public void setIdempotency(IdempotencyPolicy idempotency) {
			this.idempotency = idempotency;
		}
	}

	public static class RateLimitPolicy {

		private String type = "ip";
		private long capacity;
		private long refillRate;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public long getCapacity() {
			return capacity;
		}

		public void setCapacity(long capacity) {
			this.capacity = capacity;
		}

		public long getRefillRate() {
			return refillRate;
		}

		public void setRefillRate(long refillRate) {
			this.refillRate = refillRate;
		}
	}

	public static class IdempotencyPolicy {

		private boolean enabled;
		private String keyTemplate;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getKeyTemplate() {
			return keyTemplate;
		}

		public void setKeyTemplate(String keyTemplate) {
			this.keyTemplate = keyTemplate;
		}
	}
}
