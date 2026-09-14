package com.github.spud.tinystore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.FilterDefinition;

/**
 * The compile step is where the read-failover policy stops being text and becomes gateway
 * behaviour, so the safety rules are asserted directly instead of only through the route table.
 */
@DisplayName("route filter compilation")
class GatewayRouteFiltersTest {

    @Test
    @DisplayName("the retry is emitted after the declared filters")
    void retryComesAfterThePathRewrite() {
        GatewayRoutesDefinition.RouteDefinition route = route("order-service", List.of("StripPrefix=1"),
            retry(1, "GET", "HEAD"));

        List<FilterDefinition> compiled = GatewayRouteFilters.compile(route);

        assertThat(compiled).extracting(FilterDefinition::getName)
            .containsExactly("StripPrefix", "Retry");
        assertThat(compiled.get(1).getArgs())
            .containsEntry("retries", "1")
            .containsEntry("methods", "GET,HEAD");
    }

    @Test
    @DisplayName("methods are normalised, so 'get' cannot slip past the mutation check")
    void methodsAreNormalised() {
        GatewayRoutesDefinition.RouteDefinition route = route("product-service", List.of("StripPrefix=0"),
            retry(2, "get", "head"));

        assertThat(GatewayRouteFilters.compile(route).get(1).getArgs()).containsEntry("methods", "GET,HEAD");
    }

    @Test
    @DisplayName("a retry that would replay a mutation is refused at load time")
    void mutatingMethodsAreRefused() {
        GatewayRoutesDefinition.RouteDefinition route = route("order-service", List.of("StripPrefix=1"),
            retry(1, "GET", "POST"));

        assertThatThrownBy(() -> GatewayRouteFilters.compile(route))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("order-service")
            .hasMessageContaining("POST");
    }

    @Test
    @DisplayName("a retry without methods, without attempts, or declared twice is refused")
    void malformedRetryPoliciesAreRefused() {
        assertThatThrownBy(() -> GatewayRouteFilters.compile(
            route("pay-service", List.of("StripPrefix=0"), retry(1))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("without methods");

        assertThatThrownBy(() -> GatewayRouteFilters.compile(
            route("pay-service", List.of("StripPrefix=0"), retry(0, "GET"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("retries=0");

        assertThatThrownBy(() -> GatewayRouteFilters.compile(
            route("pay-service", List.of("Retry=1,500,GET"), retry(1, "GET"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("declares retry twice");
    }

    @Test
    @DisplayName("a route without a retry policy compiles to its declared filters only")
    void routesWithoutRetryAreUntouched() {
        GatewayRoutesDefinition.RouteDefinition route = route("order-health", List.of("SetPath=/actuator/health"));

        assertThat(GatewayRouteFilters.compile(route)).extracting(FilterDefinition::getName)
            .containsExactly("SetPath");
    }

    private static GatewayRoutesDefinition.RouteDefinition route(String id, List<String> filters) {
        return route(id, filters, null);
    }

    private static GatewayRoutesDefinition.RouteDefinition route(String id, List<String> filters,
        GatewayRoutesDefinition.RouteRetry retry) {
        GatewayRoutesDefinition.RouteDefinition route = new GatewayRoutesDefinition.RouteDefinition();
        route.setId(id);
        route.setUri("lb://" + id);
        route.setFilters(filters);
        route.setRetry(retry);
        return route;
    }

    private static GatewayRoutesDefinition.RouteRetry retry(int retries, String... methods) {
        GatewayRoutesDefinition.RouteRetry retry = new GatewayRoutesDefinition.RouteRetry();
        retry.setRetries(retries);
        retry.setMethods(List.of(methods));
        return retry;
    }
}
