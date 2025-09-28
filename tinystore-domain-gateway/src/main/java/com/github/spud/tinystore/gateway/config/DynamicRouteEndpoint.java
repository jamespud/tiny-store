package com.github.spud.tinystore.gateway.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "gateway-refresh")
public class DynamicRouteEndpoint {

	private final DynamicRouteService dynamicRouteService;

	public DynamicRouteEndpoint(DynamicRouteService dynamicRouteService) {
		this.dynamicRouteService = dynamicRouteService;
	}

	@WriteOperation
	public Map<String, Object> refresh() {
		dynamicRouteService.refreshRoutes();
		Map<String, Object> response = new HashMap<>();
		dynamicRouteService.getLastSuccessfulRefresh().ifPresent(instant -> response.put("lastRefresh", instant));
		response.putIfAbsent("lastRefresh", Instant.now());
		return response;
	}

	@ReadOperation
	public Map<String, Object> current() {
		Map<String, Object> response = new HashMap<>();
		dynamicRouteService.getLastSuccessfulRefresh().ifPresent(instant -> response.put("lastRefresh", instant));
		return response;
	}
}
