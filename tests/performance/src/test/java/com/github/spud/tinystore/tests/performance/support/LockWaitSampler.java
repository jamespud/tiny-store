package com.github.spud.tinystore.tests.performance.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Background sampler: polls pg_stat_activity lock-waiters + HikariCP active connections
 * (from /actuator/prometheus) every 500ms during a load run. Closeable; call close() to stop.
 */
public class LockWaitSampler implements AutoCloseable {

    private final PostgresClient pgClient;
    private final String prometheusUrl;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger maxLockWaiters = new AtomicInteger(0);
    private final AtomicInteger maxHikariActive = new AtomicInteger(0);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final Pattern HIKARI_ACTIVE = Pattern.compile("hikaricp_connections_active\\{[^}]*\\} (\\d+)");

    public LockWaitSampler(PostgresClient pgClient, String prometheusUrl) {
        this.pgClient = pgClient;
        this.prometheusUrl = prometheusUrl;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lock-wait-sampler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::sample, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void sample() {
        try {
            long waiters = pgClient.countLockWaiters();
            maxLockWaiters.accumulateAndGet((int) waiters, Math::max);
        } catch (Exception ignored) { }
        if (prometheusUrl != null && !prometheusUrl.isBlank()) {
            try {
                HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder().uri(URI.create(prometheusUrl)).timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    Matcher m = HIKARI_ACTIVE.matcher(resp.body());
                    int maxActive = 0;
                    while (m.find()) {
                        maxActive = Math.max(maxActive, Integer.parseInt(m.group(1)));
                    }
                    maxHikariActive.accumulateAndGet(maxActive, Math::max);
                }
            } catch (Exception ignored) { }
        }
    }

    public int getMaxLockWaiters() { return maxLockWaiters.get(); }
    public int getMaxHikariActive() { return maxHikariActive.get(); }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
