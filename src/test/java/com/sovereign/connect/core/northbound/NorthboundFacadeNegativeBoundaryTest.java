package com.sovereign.connect.core.northbound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NorthboundFacadeNegativeBoundaryTest {

    @Test
    void webImportsForbiddenOutsideHttpAdapterPackage() throws IOException {
        List<String> webTokens = List.of(
            "@RestController", "@Controller",
            "@MessageMapping", "org.springframework.web",
            "graphql", "io.grpc", "connectrpc", "JetStream"
        );
        List<String> allowedPaths = List.of("adapter/northbound/http", "bus/runtime/nats");
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/sovereign/connect"))) {
            List<String> violations = paths
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> allowedPaths.stream()
                    .noneMatch(allowed -> path.toString().replace('\\', '/').contains(allowed)))
                .filter(path -> containsAny(path, webTokens))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("Spring Web imports are only allowed under adapter.northbound.http")
                .isEmpty();
        }
    }

    @Test
    void defaultFacadeConstructorUsesOnlyApprovedCollaborators() {
        var constructors = DefaultScCoreNorthboundFacade.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
            .extracting(Class::getName)
            .containsExactly(
                "com.sovereign.connect.core.topology.query.CoreSnapshotQueryService",
                "com.sovereign.connect.core.temporal.application.TemporalActApplicationPort",
                "com.sovereign.connect.core.temporal.observation.TemporalActObservationPort",
                "com.sovereign.connect.core.temporal.engine.TemporalEngineHealth",
                "java.time.Clock"
            );
    }

    private boolean containsAny(Path path, List<String> needles) {
        try {
            String content = Files.readString(path);
            return needles.stream().anyMatch(content::contains);
        } catch (IOException ex) {
            return false;
        }
    }
}
