package com.sovereign.connect.adapter.northbound.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScNorthboundHttpArchitectureTest {

    private final Path allMain = Path.of("src/main/java/com/sovereign/connect");
    private final Path httpAdapter = Path.of("src/main/java/com/sovereign/connect/adapter/northbound/http");
    private final Path natsRuntime = Path.of("src/main/java/com/sovereign/connect/bus/runtime/nats");

    @Test
    void webImportsForbiddenOutsideHttpAdapterPackage() throws IOException {
        List<String> webTokens = List.of(
            "org.springframework.web",
            "@RestController", "@Controller",
            "@RequestMapping", "@GetMapping", "@PostMapping",
            "SseEmitter", "ResponseEntity"
        );
        try (Stream<Path> paths = Files.walk(allMain)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toAbsolutePath().normalize().startsWith(httpAdapter.toAbsolutePath().normalize()))
                .filter(p -> containsAny(p, webTokens))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("Spring Web imports are only allowed under adapter.northbound.http")
                .isEmpty();
        }
    }

    @Test
    void forbiddenTechnologiesAbsentFromAllMainSource() throws IOException {
        List<String> forbidden = List.of(
            "@MessageMapping", "graphql", "io.grpc", "connectrpc",
            "JetStream", "SseEmitter", "Flux<", "Mono<",
            "spring-boot-starter-webflux"
        );
        try (Stream<Path> paths = Files.walk(allMain)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toAbsolutePath().normalize().startsWith(natsRuntime.toAbsolutePath().normalize()))
                .filter(p -> containsAny(p, forbidden))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("Forbidden technologies must not appear in main source")
                .isEmpty();
        }
    }

    @Test
    void httpAdapterDelegatesOnlyToFacade() throws IOException {
        List<String> forbidden = List.of(
            "CoreSnapshotQueryService",
            "BaseTopologyService",
            "TemporalActApplicationPort",
            "TemporalActObservationPort",
            "SQLiteTemporalActRepository",
            "SQLiteBaseTopologyRepository",
            "JdbcTemplate",
            "javax.sql.DataSource",
            "Flyway"
        );
        try (Stream<Path> paths = Files.walk(httpAdapter)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p, forbidden))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("HTTP adapter must delegate only to ScCoreNorthboundFacade")
                .isEmpty();
        }
    }

    @Test
    void httpAdapterDoesNotImportRepositoriesOrDomainServices() throws IOException {
        List<String> forbidden = List.of(
            "Repository",
            "CoreSnapshotQueryService",
            "BaseTopologyService",
            "JdbcTemplate",
            "DataSource",
            "Flyway"
        );
        try (Stream<Path> paths = Files.walk(httpAdapter)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p, forbidden))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("HTTP adapter must not import repositories or domain services")
                .isEmpty();
        }
    }

    @Test
    void httpAdapterDoesNotLeakEibProjectionOrAuthorityConcepts() throws IOException {
        List<String> forbidden = List.of(
            "Projection", "EffectiveView", "Session", "Identity",
            "Authority", "Policy", "Surface"
        );
        try (Stream<Path> paths = Files.walk(httpAdapter)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p, forbidden))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("HTTP adapter must not contain EIB/Projection/Authority/Policy concepts")
                .isEmpty();
        }
    }

    private boolean containsAny(Path path, List<String> needles) {
        try {
            String content = Files.readString(path);
            return needles.stream().anyMatch(content::contains);
        } catch (IOException e) {
            return false;
        }
    }
}
