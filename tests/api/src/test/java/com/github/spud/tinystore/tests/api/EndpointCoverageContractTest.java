package com.github.spud.tinystore.tests.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Endpoint Coverage Contract Test
 * 
 * Validates that all endpoints exercised in E2E tests have corresponding coverage
 * in module-level integration tests (IT) and unit tests.
 * 
 * Coverage Rule (per service):
 * - E2E endpoints ⊆ IT endpoints
 * - IT endpoints ⊆ Unit endpoints
 * 
 * Tag Format (use JUnit @Tag annotation with this pattern):
 *   ep:{service}:{METHOD}:{canonicalPath}
 * Where:
 * - service: order | inventory | promotion | product
 * - METHOD: GET | POST | PUT | DELETE
 * - canonicalPath: /api/... with template variables {id}, {tradeId}, etc.
 * 
 * Execution: runs as part of `make e2e` (surefire phase in tests/api module)
 */
class EndpointCoverageContractTest {

    private static final Set<String> VALID_SERVICES = Set.of("order", "inventory", "promotion", "product");
    private static final Pattern TAG_PATTERN = Pattern.compile("@(?:org\\.junit\\.jupiter\\.api\\.)?Tag\\(\"ep:([^:]+):([^:]+):([^\"]+)\"\\)");

    @Test
    void endpointCoverageChain_shouldSatisfy_E2E_subset_IT_subset_Unit() throws IOException {
        Path repoRoot = findRepoRoot();
        
        Map<String, Set<Endpoint>> e2eEndpoints = extractEndpoints(repoRoot, TestLayer.E2E);
        Map<String, Set<Endpoint>> itEndpoints = extractEndpoints(repoRoot, TestLayer.IT);
        Map<String, Set<Endpoint>> unitEndpoints = extractEndpoints(repoRoot, TestLayer.UNIT);

        List<String> violations = new ArrayList<>();

        for (String service : VALID_SERVICES) {
            Set<Endpoint> e2e = e2eEndpoints.getOrDefault(service, Set.of());
            Set<Endpoint> it = itEndpoints.getOrDefault(service, Set.of());
            Set<Endpoint> unit = unitEndpoints.getOrDefault(service, Set.of());

            // Rule 1: E2E ⊆ IT
            Set<Endpoint> missingInIT = new HashSet<>(e2e);
            missingInIT.removeAll(it);
            if (!missingInIT.isEmpty()) {
                violations.add(String.format("[%s] E2E endpoints missing in IT: %s", 
                    service, formatEndpoints(missingInIT)));
            }

            // Rule 2: IT ⊆ Unit
            Set<Endpoint> missingInUnit = new HashSet<>(it);
            missingInUnit.removeAll(unit);
            if (!missingInUnit.isEmpty()) {
                violations.add(String.format("[%s] IT endpoints missing in Unit: %s", 
                    service, formatEndpoints(missingInUnit)));
            }
        }

        if (!violations.isEmpty()) {
            fail("Endpoint coverage violations detected:\n" + String.join("\n", violations) +
                 "\n\nTo fix: add corresponding IT or Unit tests with matching @Tag(\"ep:...\") annotations");
        }
    }

    private Path findRepoRoot() {
        // tests/api is under <repo-root>/tests/api
        Path current = Paths.get(".").toAbsolutePath().normalize();
        
        // Try maven.multiModuleProjectDirectory first
        String mavenRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (mavenRoot != null) {
            return Paths.get(mavenRoot);
        }
        
        // Fallback: navigate up from tests/api to repo root
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && 
                Files.exists(current.resolve("tests")) &&
                Files.exists(current.resolve("tinystore-domain-order"))) {
                return current;
            }
            current = current.getParent();
        }
        
        throw new IllegalStateException("Could not locate repository root. Run from Maven or set working directory.");
    }

    private Map<String, Set<Endpoint>> extractEndpoints(Path repoRoot, TestLayer layer) throws IOException {
        Map<String, Set<Endpoint>> byService = new HashMap<>();
        
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(p -> isTestFile(p, layer))
                 .forEach(file -> {
                     try {
                         String content = Files.readString(file);
                         Matcher matcher = TAG_PATTERN.matcher(content);
                         while (matcher.find()) {
                             String service = matcher.group(1);
                             String method = matcher.group(2);
                             String path = matcher.group(3);
                             
                             if (!VALID_SERVICES.contains(service)) {
                                 fail(String.format("Invalid service in @Tag: '%s' (file: %s). Valid: %s",
                                     service, file, VALID_SERVICES));
                             }
                             
                             Endpoint ep = new Endpoint(service, method, path);
                             byService.computeIfAbsent(service, k -> new HashSet<>()).add(ep);
                         }
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to read file: " + file, e);
                     }
                 });
        }
        
        return byService;
    }

    private boolean isTestFile(Path path, TestLayer layer) {
        if (!path.toString().endsWith(".java")) {
            return false;
        }
        
        String pathStr = path.toString();
        boolean isInTestDir = pathStr.contains("/src/test/java/");
        
        if (!isInTestDir) {
            return false;
        }
        
        String fileName = path.getFileName().toString();
        
        switch (layer) {
            case E2E:
                // Only tests/api module
                return pathStr.contains("/tests/api/src/test/java/");
            case IT:
                // Non-tests/api modules, ending with IT.java
                return !pathStr.contains("/tests/api/src/test/java/") && fileName.endsWith("IT.java");
            case UNIT:
                // Non-tests/api modules, ending with Test.java but not IT.java
                return !pathStr.contains("/tests/api/src/test/java/") && 
                       fileName.endsWith("Test.java") && 
                       !fileName.endsWith("IT.java");
            default:
                return false;
        }
    }

    private String formatEndpoints(Set<Endpoint> endpoints) {
        return endpoints.stream()
            .map(Endpoint::toString)
            .sorted()
            .collect(Collectors.joining(", "));
    }

    enum TestLayer {
        E2E, IT, UNIT
    }

    static class Endpoint {
        final String service;
        final String method;
        final String path;

        Endpoint(String service, String method, String path) {
            this.service = service;
            this.method = method;
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Endpoint)) return false;
            Endpoint endpoint = (Endpoint) o;
            return service.equals(endpoint.service) &&
                   method.equals(endpoint.method) &&
                   path.equals(endpoint.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(service, method, path);
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}
