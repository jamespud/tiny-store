package com.github.spud.tinystore.gateway.config;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class GatewayPolicyRegistry {

	private final Map<String, GatewayRoutesDefinition.RoutePolicies> policiesByRoute = new ConcurrentHashMap<>();

	public void registerPolicies(String routeId, GatewayRoutesDefinition.RoutePolicies policies) {
		if (policies == null) {
			policiesByRoute.remove(routeId);
		} else {
			policiesByRoute.put(routeId, policies);
		}
	}

	public void clear() {
		policiesByRoute.clear();
	}

	public Optional<GatewayRoutesDefinition.RoutePolicies> findPolicies(String routeId) {
		return Optional.ofNullable(policiesByRoute.get(routeId));
	}
}
