package com.github.spud.tinystore.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.tests.api.support.AbstractE2EBase;
import com.github.spud.tinystore.tests.api.support.E2ePostgres;

/**
 * Distributed (multi-instance) assertions.
 *
 * <p>The rest of the E2E suite talks to one URL and cannot tell whether the platform is
 * one process or ten. These tests exist to falsify the "it is really distributed" claim:
 *
 * <ol>
 *   <li>every service is registered in Nacos with the expected number of <em>live</em> replicas;</li>
 *   <li>the gateway's {@code lb://} routes actually spread load over the replicas
 *       (measured from each replica's own request counters, not from a single URL);</li>
 *   <li>state that must be shared (idempotency) really is shared across gateway replicas,
 *       so a duplicate submit that lands on a <em>different</em> replica is still rejected.</li>
 * </ol>
 *
 * <p>Skipped unless {@code -Dmulti.instance.mode=true}, so the single-instance {@code make e2e}
 * run is unaffected.
 */
@EnabledIfSystemProperty(named = "multi.instance.mode", matches = "true")
class DistributedMultiInstanceIT extends AbstractE2EBase {

    /** Service ids as registered in Nacos / used by the gateway's lb:// routes. */
    private static final List<String> REGISTERED_SERVICES = List.of(
        "tinystore-gateway",
        "tinystore-auth",
        "tinystore-domain-account",
        "product-service",
        "promotion-service",
        "tinystore-inventory-service",
        "order-service",
        "pay-service");

    private static final int EXPECTED_REPLICAS = Integer.getInteger("multi.instance.replicas", 2);

    private static final String NACOS_BASE_URL =
        System.getProperty("nacos.base.url", "http://localhost:8849");

    /** Comma separated base URL of every gateway replica. */
    private static final List<String> GATEWAY_REPLICA_URLS =
        splitProperty("gateway.replica.urls", "http://localhost:38080,http://localhost:38081");

    /** Comma separated base URL of every order replica (direct, for per-replica metrics). */
    private static final List<String> ORDER_REPLICA_URLS =
        splitProperty("order.replica.urls", "http://localhost:38280,http://localhost:38281");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static List<String> splitProperty(String key, String defaultValue) {
        String raw = System.getProperty(key, defaultValue);
        List<String> urls = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                urls.add(part.trim());
            }
        }
        return List.copyOf(urls);
    }

    // ------------------------------------------------------------------
    // 1. Every service really runs N replicas and all of them are healthy
    // ------------------------------------------------------------------

    @Test
    void everyServiceIsRegisteredInNacosWithAllReplicasHealthy() throws Exception {
        // Registration is eventually consistent: a replica that is still booting (product takes ~60s)
        // may not be in Nacos yet. Poll instead of sampling once, otherwise the assertion is racy.
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        List<String> failures = new ArrayList<>();
        do {
            failures = registrationFailures();
            if (failures.isEmpty()) {
                return;
            }
            Thread.sleep(2000);
        } while (System.nanoTime() < deadline);

        assertThat(failures)
            .withFailMessage("Multi-instance discovery contract broken: %s", failures)
            .isEmpty();
    }

    private List<String> registrationFailures() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String service : REGISTERED_SERVICES) {
            JsonNode response = MAPPER.readTree(httpGet(
                NACOS_BASE_URL + "/nacos/v1/ns/instance/list?serviceName=" + service + "&healthyOnly=false"));
            JsonNode hosts = response.path("hosts");

            long healthy = 0;
            for (JsonNode host : hosts) {
                if (host.path("healthy").asBoolean(false) && host.path("enabled").asBoolean(true)) {
                    healthy++;
                }
            }
            if (healthy != EXPECTED_REPLICAS) {
                failures.add(service + ": expected " + EXPECTED_REPLICAS + " healthy replicas, saw " + healthy
                    + " of " + hosts.size() + " registered");
            }
        }
        return failures;
    }

    // ------------------------------------------------------------------
    // 2. The gateway load-balances -- measured per replica, not per entry point
    // ------------------------------------------------------------------

    @Test
    void gatewayDistributesBusinessTrafficAcrossOrderReplicas() throws Exception {
        List<Long> before = orderTradeRequestCounts();

        int requests = 40;
        for (int i = 0; i < requests; i++) {
            httpGet(gatewayBaseUrl + "/api/order/trades/lb-probe-" + UUID.randomUUID());
        }

        List<Long> after = orderTradeRequestCounts();
        List<Long> deltas = new ArrayList<>();
        for (int i = 0; i < after.size(); i++) {
            deltas.add(after.get(i) - before.get(i));
        }

        assertThat(deltas).hasSameSizeAs(ORDER_REPLICA_URLS);
        long total = deltas.stream().mapToLong(Long::longValue).sum();
        assertThat(total)
            .withFailMessage("Gateway did not forward all %s probes to order replicas (per-replica deltas=%s)",
                requests, deltas)
            .isEqualTo(requests);

        // Every replica must have served something: that is what "distributed" means here.
        assertThat(deltas)
            .withFailMessage("Traffic was NOT load-balanced over the order replicas (deltas=%s). "
                + "One replica served everything, so this deployment is not really distributed.", deltas)
            .allSatisfy(delta -> assertThat(delta).isPositive());
    }

    // ------------------------------------------------------------------
    // 3. Idempotency state is shared across gateway replicas
    // ------------------------------------------------------------------

    @Test
    void idempotencyKeyIsEnforcedAcrossDifferentGatewayReplicas() {
        assertThat(GATEWAY_REPLICA_URLS)
            .withFailMessage("Need at least two gateway replicas to prove shared idempotency")
            .hasSizeGreaterThanOrEqualTo(2);

        String tradeId = UUID.randomUUID().toString();
        String idempotencyKey = "idem-multi-" + tradeId;
        Map<String, Object> create = createTradeBody(tradeId);

        // First submission lands on replica #1.
        ResponseEntity<Map> first = postTrade(GATEWAY_REPLICA_URLS.get(0), create, idempotencyKey);
        assertThat(first.getStatusCode())
            .withFailMessage("First create on gateway replica #1 failed: %s body=%s",
                first.getStatusCode(), first.getBody())
            .isEqualTo(HttpStatus.OK);

        // Identical submission lands on replica #2. If idempotency were node-local, this
        // would create a second trade / return 200 instead of being rejected.
        ResponseEntity<Map> duplicate = postTrade(GATEWAY_REPLICA_URLS.get(1), create, idempotencyKey);
        assertThat(duplicate.getStatusCode())
            .withFailMessage("Duplicate Idempotency-Key accepted on a different gateway replica "
                + "(%s body=%s) -- idempotency state is node-local, not shared",
                duplicate.getStatusCode(), duplicate.getBody())
            .isEqualTo(HttpStatus.CONFLICT);

        try (E2ePostgres pg = new E2ePostgres()) {
            assertThat(pg.countTradesByTradeId(tradeId))
                .withFailMessage("Idempotent duplicate created a second trade for %s", tradeId)
                .isEqualTo(1L);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Map<String, Object> createTradeBody(String tradeId) {
        Map<String, Object> create = new HashMap<>();
        create.put("tradeId", tradeId);
        create.put("buyerId", "buyer-multi-" + tradeId);
        create.put("buyerNick", "buyer-multi");
        create.put("addressId", "addr-001");
        create.put("traceId", "trace-multi-" + tradeId);
        create.put("orderLines", List.of(
            orderLine(SKU_A, PRODUCT_A, "Product A", SHOP_A, SELLER_A, 1, 1000L)));
        return create;
    }

    private ResponseEntity<Map> postTrade(String baseUrl, Map<String, Object> body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange(baseUrl + "/api/order/trades",
            org.springframework.http.HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    /** Sum of {@code http_server_requests_seconds_count} for the trade-detail endpoint, per order replica. */
    private List<Long> orderTradeRequestCounts() throws Exception {
        List<Long> counts = new ArrayList<>();
        for (String baseUrl : ORDER_REPLICA_URLS) {
            String metrics = httpGet(baseUrl + "/actuator/prometheus");
            // The gateway strips "/api", so the order service records the path as "/order/trades/{tradeId}".
            counts.add(sumMetric(metrics, "http_server_requests_seconds_count", "/order/trades/{tradeId}"));
        }
        return counts;
    }

    private static long sumMetric(String prometheusText, String metric, String uriLabel) {
        Pattern pattern = Pattern.compile("^" + metric + "\\{.*uri=\"" + Pattern.quote(uriLabel) + "\".*\\} (\\S+)$");
        long total = 0L;
        for (String line : prometheusText.split("\n")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                total += (long) Double.parseDouble(matcher.group(1));
            }
        }
        return total;
    }

    private static String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
