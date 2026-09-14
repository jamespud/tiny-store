package com.github.spud.tinystore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * The public route contract (D2).
 *
 * <p>StripPrefix is not cosmetic: it decides whether {@code /api/products/1} reaches
 * {@code ProductController}'s {@code /api/products/1} or a 404. Two conventions coexist here — the
 * order service declares {@code /order/...} while every other service declares its own
 * {@code /api/...} path — and five of the seven business routes used to be wrong, which is exactly
 * why the black-box tests bypassed the gateway and called service ports directly.
 *
 * <p>These tests assert, per domain, that
 * {@code publicPrefix --(StripPrefix)--> the path the controller actually declares}, so a drift
 * fails the build instead of being masked by a direct-port test. They also pin the in-cluster
 * ConfigMap to the classpath route table, because a ConfigMap that drifts ships a different
 * contract in Kubernetes than the one the tests exercise.
 */
@DisplayName("gateway route contract")
class GatewayRouteContractTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** One public domain: public prefix, the service behind it, and the controller's base path. */
    private record Domain(String name, String serviceUri, String publicPrefix, String controllerBase) {
    }

    private static final List<Domain> DOMAINS = List.of(
        new Domain("order", "lb://order-service", "/api/order", "/order"),
        new Domain("payment", "lb://pay-service", "/api/pay", "/api/pay"),
        new Domain("promotion", "lb://promotion-service", "/api/promotion", "/api/promotion"),
        new Domain("product", "lb://product-service", "/api/products", "/api/products"),
        new Domain("product-skus", "lb://product-service", "/api/skus", "/api/skus"),
        new Domain("inventory", "lb://tinystore-inventory-service", "/api/inventory", "/api/inventory"),
        new Domain("account", "lb://tinystore-domain-account", "/api/account", "/api/account"),
        new Domain("auth", "lb://tinystore-auth", "/api/auth", "/api/auth")
    );

    /** Every service must be reachable through the gateway, if only for a health smoke check. */
    private static final List<String> HEALTH_SMOKE_PATHS = List.of(
        "/internal/health/order", "/internal/health/promotion", "/internal/health/inventory",
        "/internal/health/product", "/internal/health/auth", "/internal/health/account",
        "/internal/health/pay");

    @Test
    @DisplayName("every business domain routes to the path its controller actually declares")
    void everyDomainPreservesItsControllerPath() throws Exception {
        List<GatewayRoutesDefinition.RouteDefinition> routes = classpathRoutes();

        List<String> failures = new ArrayList<>();
        for (Domain domain : DOMAINS) {
            GatewayRoutesDefinition.RouteDefinition route = findRoute(routes, domain);
            if (route == null) {
                failures.add(domain.name() + ": no route with uri=" + domain.serviceUri()
                    + " and Path=" + domain.publicPrefix() + "/**");
                continue;
            }
            int stripped = stripPrefix(route);
            String resolved = resolve(domain.publicPrefix(), stripped);
            if (!resolved.equals(domain.controllerBase())) {
                failures.add(domain.name() + ": " + domain.publicPrefix() + " with StripPrefix=" + stripped
                    + " resolves to " + resolved + " but the controller declares " + domain.controllerBase());
            }
        }

        assertThat(failures)
            .withFailMessage("Gateway route contract broken (the public path would 404 downstream): %s", failures)
            .isEmpty();
    }

    @Test
    @DisplayName("every service is reachable through the gateway via at least a health route")
    void everyServiceHasASmokeRoute() throws Exception {
        List<GatewayRoutesDefinition.RouteDefinition> routes = classpathRoutes();

        for (String path : HEALTH_SMOKE_PATHS) {
            assertThat(routes)
                .withFailMessage("No gateway route for smoke path %s -- that service has no public entry point",
                    path)
                .anySatisfy(route -> assertThat(route.getPredicates())
                    .withFailMessage("route %s does not match %s", route.getId(), path)
                    .contains("Path=" + path));
        }

        // The two domains the plan calls out explicitly must be reachable by a business path too.
        assertThat(routeIds(routes)).contains("order-service", "product-service", "product-skus",
            "inventory-service", "promotion-service", "pay-service", "account-service", "auth-service");
    }

    @Test
    @DisplayName("the in-cluster ConfigMap defines exactly the same routes as the classpath file")
    void k8sConfigMapMatchesClasspathRoutes() throws Exception {
        Path configMap = Paths.get("k8s/gateway-configmap.yml");
        assertThat(configMap)
            .withFailMessage("Expected the gateway ConfigMap at %s", configMap.toAbsolutePath())
            .exists();

        Map<String, String> fromConfigMap = summarize(yamlMapperInConfigMap(configMap));
        Map<String, String> fromClasspath = summarize(classpathRoutes());

        assertThat(fromConfigMap)
            .withFailMessage("k8s ConfigMap route table drifted from gateway-routes.yml.%n"
                + "classpath: %s%nconfigmap: %s", fromClasspath.keySet(), fromConfigMap.keySet())
            .containsExactlyInAnyOrderEntriesOf(fromClasspath);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private List<GatewayRoutesDefinition.RouteDefinition> classpathRoutes() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("gateway-routes.yml")) {
            assertThat(in).withFailMessage("gateway-routes.yml missing from the classpath").isNotNull();
            return YAML.readValue(in, GatewayRoutesDefinition.class).getRoutes();
        }
    }

    /**
     * The ConfigMap stores the YAML as a block scalar; strip the wrapper and the block indentation so
     * the embedded document can be parsed with the same mapper the runtime loader uses.
     */
    private List<GatewayRoutesDefinition.RouteDefinition> yamlMapperInConfigMap(Path configMap)
        throws Exception {
        List<String> lines = Files.readAllLines(configMap, StandardCharsets.UTF_8);
        StringBuilder embedded = new StringBuilder();
        boolean inBlock = false;
        for (String line : lines) {
            if (!inBlock) {
                if (line.trim().startsWith("gateway-routes.yml:")) {
                    inBlock = true;
                }
                continue;
            }
            // the block scalar is indented 4 spaces inside `data:`
            embedded.append(line.startsWith("    ") ? line.substring(4) : line).append('\n');
        }
        return YAML.readValue(embedded.toString(), GatewayRoutesDefinition.class).getRoutes();
    }

    private static GatewayRoutesDefinition.RouteDefinition findRoute(
        List<GatewayRoutesDefinition.RouteDefinition> routes, Domain domain) {
        String wanted = domain.publicPrefix() + "/**";
        for (GatewayRoutesDefinition.RouteDefinition route : routes) {
            if (!domain.serviceUri().equals(route.getUri())) {
                continue;
            }
            // A Path predicate may carry several comma-separated patterns (e.g. auth serves
            // /api/auth/**, /api/admin/** and /oidc/** from one route).
            if (pathPatterns(route).contains(wanted)) {
                return route;
            }
        }
        return null;
    }

    /** Every individual path pattern declared by a route, comma-separated lists flattened. */
    private static List<String> pathPatterns(GatewayRoutesDefinition.RouteDefinition route) {
        List<String> patterns = new ArrayList<>();
        for (String predicate : route.getPredicates()) {
            if (predicate.startsWith("Path=")) {
                for (String pattern : predicate.substring("Path=".length()).split(",")) {
                    patterns.add(pattern.trim());
                }
            }
        }
        return patterns;
    }

    private static int stripPrefix(GatewayRoutesDefinition.RouteDefinition route) {
        for (String filter : route.getFilters()) {
            if (filter.startsWith("StripPrefix=")) {
                return Integer.parseInt(filter.substring("StripPrefix=".length()));
            }
        }
        return 0;
    }

    /** Applies StripPrefix to a public prefix and returns the downstream base path. */
    private static String resolve(String publicPrefix, int parts) {
        String[] segments = publicPrefix.replaceAll("^/+", "").split("/");
        if (parts >= segments.length) {
            return "/";
        }
        return "/" + String.join("/", java.util.Arrays.copyOfRange(segments, parts, segments.length));
    }

    private static List<String> routeIds(List<GatewayRoutesDefinition.RouteDefinition> routes) {
        return routes.stream().map(GatewayRoutesDefinition.RouteDefinition::getId).toList();
    }

    /** id -> "uri predicates filters", for whole-table comparison. */
    private static Map<String, String> summarize(List<GatewayRoutesDefinition.RouteDefinition> routes) {
        Map<String, String> summary = new LinkedHashMap<>();
        for (GatewayRoutesDefinition.RouteDefinition route : routes) {
            summary.put(route.getId(), route.getUri() + " " + route.getPredicates() + " " + route.getFilters());
        }
        return summary;
    }
}
