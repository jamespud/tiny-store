package com.github.spud.tinystore.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.Duration;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The runtime settings that make the multi-instance stack judgeable (task 13B).
 *
 * <p>These are the parts of A4 / D1 / D3 that live in configuration rather than code, so one service
 * forgetting them would only show up as a subtle difference between replicas at runtime:
 *
 * <ul>
 * <li><b>A4 readiness</b>: outside Kubernetes Boot does not create the probe groups, so
 * {@code /actuator/health/readiness} 404s unless each service opts in -- and a compose healthcheck
 * that hits plain {@code /actuator/health} only proves the context started, which is exactly the
 * "JVM alive" signal the audit called out.</li>
 * <li><b>D1 tracing</b>: an empty {@code management.zipkin.tracing.endpoint} still produces a Zipkin
 * exporter pointed at an empty URI. Spans then disappear with no error, so the exporter must be
 * opt-in.</li>
 * <li><b>D3 connection budget</b>: every replica of every DB-backed service opens its own pool. The
 * sums at the documented 3-replica target must stay well under PostgreSQL's max_connections, or a
 * distributed test starts measuring database starvation instead of the system.</li>
 * </ul>
 */
@DisplayName("multi-instance runtime configuration contract")
class RuntimeConfigContractTest {

    /** Service -> config file (auth keeps its management block in the dev profile) and its port. */
    private static final Map<String, Service> SERVICES = new LinkedHashMap<>();

    static {
        SERVICES.put("order", new Service("tinystore-domain-order/src/main/resources/application.yml", 28080));
        SERVICES.put("inventory", new Service("tinystore-domain-inventory/src/main/resources/application.yml", 13000));
        SERVICES.put("promotion", new Service("tinystore-domain-promotion/src/main/resources/application.yml", 1200));
        SERVICES.put("payment", new Service("tinystore-domain-payment/src/main/resources/application.yml", 8083));
        SERVICES.put("product", new Service("tinystore-domain-product/src/main/resources/application.yml", 8090));
        SERVICES.put("account", new Service("tinystore-domain-account/src/main/resources/application.yml", 8000));
        SERVICES.put("auth", new Service("tinystore-domain-auth/src/main/resources/application-dev.yml", 9000));
        SERVICES.put("gateway", new Service("tinystore-domain-gateway/src/main/resources/application.yml", 8080));
    }

    private static final int REPLICAS = 3;
    private static final double MAX_CONNECTION_SHARE = 0.70;
    /** A `probes:` block whose `enabled` is true -- other `enabled: true` keys must not satisfy this. */
    private static final Pattern PROBES_ENABLED = Pattern.compile("probes:\\s*\\n\\s+enabled:\\s*true");
    private final Path repoRoot = findRepoRoot();

    @Test
    @DisplayName("every service exposes /actuator/health/readiness and the stack checks it")
    void readinessIsExposedAndChecked() throws Exception {
        String compose = Files.readString(repoRoot.resolve("docker/docker-compose-test.yml"));
        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, Service> entry : SERVICES.entrySet()) {
            String service = entry.getKey();
            Service config = entry.getValue();
            String yaml = Files.readString(repoRoot.resolve(config.path()));
            if (!PROBES_ENABLED.matcher(yaml).find()) {
                failures.add(service + ": " + config.path() + " does not enable health probes, so "
                    + "/actuator/health/readiness does not exist outside k8s");
            }

            String block = serviceBlock(compose, service);
            String expected = "http://localhost:" + config.port() + "/actuator/health/readiness";
            if (!block.contains("healthcheck:")) {
                failures.add(service + ": no healthcheck in docker-compose-test.yml");
            }
            else if (!block.contains(expected)) {
                failures.add(service + ": healthcheck does not probe " + expected
                    + " (plain /actuator/health only proves the JVM answers)");
            }
        }

        assertThat(failures).withFailMessage("A4 readiness contract broken: %s", failures).isEmpty();
    }

    @Test
    @DisplayName("inventory's orphan reclaim stays behind every legitimate reservation window")
    void orphanReclaimDelayOutlivesTheReservationTtl() throws Exception {
        // P0: the orphan sweep (Redis uncommit member older than orphan-check-delay + no DB row) may only run
        // once every legitimate reservation window has closed AND the consumer refuses expired
        // INVENTORY_RESERVE_DB events -- otherwise it can release a pre-deduction whose DB row is about to
        // appear, which is the oversell direction. Inventory validates its own two invariants at startup;
        // this pins the cross-service one (order's TTL <= inventory's declared maximum).
        String orderYaml = Files.readString(repoRoot.resolve("tinystore-domain-order/src/main/resources/application.yml"));
        String inventoryYaml = Files.readString(
            repoRoot.resolve("tinystore-domain-inventory/src/main/resources/application.yml"));

        long orderTtlMinutes = Long.parseLong(Regex.firstGroup(orderYaml, "ttl-minutes:\\s*(\\d+)"));
        Duration maxReservationTtl = duration(Regex.firstGroup(inventoryYaml, "max-reservation-ttl: [^:]*:(PT[0-9A-Z]+)"));
        Duration orphanDelay = duration(Regex.firstGroup(inventoryYaml, "orphan-check-delay: [^:]*:(PT[0-9A-Z]+)"));
        Duration timeout = duration(Regex.firstGroup(inventoryYaml, "timeout: [^:]*:(PT[0-9A-Z]+)"));

        assertThat(maxReservationTtl)
            .withFailMessage("order reserves for %d minutes but inventory declares max-reservation-ttl=%s -- "
                + "the orphan sweep would start while legitimate reservations are still open", orderTtlMinutes,
                maxReservationTtl)
            .isGreaterThanOrEqualTo(Duration.ofMinutes(orderTtlMinutes));
        assertThat(orphanDelay).isGreaterThan(maxReservationTtl);
        assertThat(timeout).isGreaterThan(orphanDelay);
    }

    @Test
    @DisplayName("zipkin exporting is off unless an endpoint is configured")
    void zipkinExportIsOptIn() throws Exception {
        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, Service> entry : SERVICES.entrySet()) {
            String yaml = Files.readString(repoRoot.resolve(entry.getValue().path()));
            if (!tracingIsOptIn(yaml)) {
                failures.add(entry.getKey() + ": " + entry.getValue().path()
                    + " does not default management.tracing.enabled to false, so an empty endpoint still"
                    + " builds a zipkin reporter that silently drops every span (D1)");
            }
        }

        assertThat(failures).withFailMessage("D1 tracing contract broken: %s", failures).isEmpty();
    }

    @Test
    @DisplayName("the 3-replica connection budget stays under 70% of postgres max_connections")
    void connectionBudgetFits() throws Exception {
        String compose = Files.readString(repoRoot.resolve("docker/docker-compose-test.yml"));

        Matcher maxConnections = Pattern.compile("max_connections=(\\d+)").matcher(compose);
        assertThat(maxConnections.find())
            .withFailMessage("could not find PostgreSQL's max_connections in docker/docker-compose-test.yml")
            .isTrue();
        int postgresMax = Integer.parseInt(maxConnections.group(1));

        List<Integer> pools = new ArrayList<>();
        Matcher pool = Pattern.compile("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:\\s*(\\d+)").matcher(compose);
        while (pool.find()) {
            pools.add(Integer.parseInt(pool.group(1)));
        }

        int perReplica = pools.stream().mapToInt(Integer::intValue).sum();
        int total = perReplica * REPLICAS;
        assertThat(total)
            .withFailMessage("%d DB services x %d replicas x pools %s = %d connections, more than %.0f%% of "
                    + "postgres max_connections=%d. Shrink the pools (a distributed run must not measure "
                    + "connection starvation) or raise the database limit deliberately.",
                pools.size(), REPLICAS, pools, total, MAX_CONNECTION_SHARE * 100, postgresMax)
            .isLessThanOrEqualTo((int) (postgresMax * MAX_CONNECTION_SHARE));

        assertThat(pools)
            .withFailMessage("every DB-backed service must state its pool size explicitly, otherwise the "
                + "budget is computed from a framework default that can change under us")
            .hasSize(7);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private record Service(String path, int port) {
    }

    private static Duration duration(String isoDuration) {
        return Duration.parse(isoDuration);
    }

    /** Minimal regex helper: the yml values here are simple enough that a parser would be overkill. */
    private static final class Regex {

        private Regex() {
        }

        static String firstGroup(String text, String pattern) {
            Matcher matcher = Pattern.compile(pattern).matcher(text);
            assertThat(matcher.find())
                .withFailMessage("could not find /%s/ in configuration", pattern)
                .isTrue();
            return matcher.group(1);
        }
    }

    /**
     * True when the {@code tracing:} block switches itself off, i.e. carries
     * {@code enabled: ${ZIPKIN_ENABLED:false}}.
     *
     * <p>Boot 3.5 gates the zipkin sender and span handler on {@code management.tracing.enabled} --
     * there is no {@code export.zipkin.enabled} condition to lean on. That was checked against
     * /actuator/beans: with the old empty endpoint the container still had {@code asyncZipkinSpanHandler}
     * and {@code httpClientSender}, aimed at nothing.
     */
    private static boolean tracingIsOptIn(String yaml) {
        String[] lines = yaml.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().equals("tracing:")) {
                continue;
            }
            String indent = lines[i].substring(0, lines[i].indexOf("tracing:"));
            for (int j = i + 1; j < lines.length; j++) {
                String line = lines[j];
                if (line.isBlank()) {
                    continue;
                }
                if (!line.startsWith(indent + " ")) {
                    break;                      // left the block
                }
                if (line.trim().equals("enabled: ${ZIPKIN_ENABLED:false}")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The indented YAML block of one compose service. */
    private static String serviceBlock(String compose, String service) {
        String[] lines = compose.split("\n");
        StringBuilder block = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            if (line.matches(" {2}[a-z0-9-]+:.*")) {
                inside = line.startsWith("  " + service + ":");
                continue;
            }
            if (inside) {
                block.append(line).append('\n');
            }
        }
        return block.toString();
    }

    private Path findRepoRoot() {
        Path current = Paths.get(".").toAbsolutePath().normalize();
        String mavenRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (mavenRoot != null) {
            return Paths.get(mavenRoot);
        }
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("tests"))
                && Files.exists(current.resolve("tinystore-domain-order"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
