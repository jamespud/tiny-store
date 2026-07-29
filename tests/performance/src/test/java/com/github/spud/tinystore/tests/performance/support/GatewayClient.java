package com.github.spud.tinystore.tests.performance.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Lightweight HTTP client for gateway API calls (no Spring Test dependency)
 */
public class GatewayClient {

    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public GatewayClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.mapper = new ObjectMapper();
    }

    public HttpResponse<String> createTrade(String tradeId, String idempotencyKey, Map<String, Object> body) {
        return post("/api/order/trades", idempotencyKey, body);
    }

    public HttpResponse<String> reserveInventory(String idempotencyKey, Map<String, Object> body) {
        return post("/api/inventory/reservations/reserve", idempotencyKey, body);
    }

    public HttpResponse<String> confirmReservation(String idempotencyKey, Map<String, Object> body) {
        return post("/api/inventory/reservations/confirm", idempotencyKey, body);
    }

    private HttpResponse<String> post(String path, String idempotencyKey, Map<String, Object> body) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Trace-ID", "perf-test-" + idempotencyKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to POST " + path, e);
        }
    }

    public Map<String, Object> parseResponse(String body) {
        try {
            return mapper.readValue(body, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response: " + body, e);
        }
    }
}
