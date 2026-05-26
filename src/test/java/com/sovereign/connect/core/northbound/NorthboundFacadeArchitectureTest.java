package com.sovereign.connect.core.northbound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NorthboundFacadeArchitectureTest {

    private final Path northboundMain = Path.of("src/main/java/com/sovereign/connect/core/northbound");

    @Test
    void northboundPackageHasNoForbiddenImportsOrTechnologyBindings() throws IOException {
        List<String> forbidden = List.of(
            "springframework.web",
            "springframework.stereotype.Controller",
            "RestController",
            "RequestMapping",
            "GetMapping",
            "PostMapping",
            "graphql",
            "io.grpc",
            "connectrpc",
            "mcp",
            "websocket",
            "io.nats",
            "jetstream",
            "adapter.persistence",
            "adapter.discovery",
            "javax.sql.DataSource",
            "JdbcTemplate",
            "Flyway",
            "SQLite",
            "topology_json",
            ".device().health()",
            ".endpoint().health()",
            "EndpointSnapshot.health",
            "BaseTopologyService",
            "ObjectMapper",
            "DeviceDiscoveryFact",
            "EndpointDiscoveryFact",
            "MaterializationDecision"
        );
        assertThat(northboundMain).exists();
        try (Stream<Path> paths = Files.walk(northboundMain)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> assertFileDoesNotContain(path, forbidden));
        }
    }

    @Test
    void facadeHasNoDiscoveryMethods() {
        List<String> methodNames = Stream.of(ScCoreNorthboundFacade.class.getDeclaredMethods())
            .map(Method::getName)
            .toList();

        assertThat(methodNames)
            .noneMatch(name -> name.toLowerCase().contains("discovery"))
            .noneMatch(name -> name.toLowerCase().contains("discover"))
            .noneMatch(name -> name.toLowerCase().contains("candidate"));
    }

    @Test
    void facadeDoesNotExposeRawDomainRecordsAsReturnContract() {
        List<String> forbiddenTypeFragments = List.of(
            "com.sovereign.connect.core.topology.query.CoreSnapshot",
            "com.sovereign.connect.core.topology.model.HabitatBaseTopology",
            "com.sovereign.connect.core.topology.model.DeviceNode",
            "com.sovereign.connect.core.topology.model.EndpointNode",
            "com.sovereign.connect.core.topology.model.RoomNode",
            "com.sovereign.connect.core.topology.model.ZoneNode",
            "com.sovereign.connect.core.topology.model.CapabilityNode",
            "com.sovereign.connect.core.topology.model.EndpointHealth",
            "com.sovereign.connect.core.temporal.observation.TemporalActObservation"
        );

        for (Method method : ScCoreNorthboundFacade.class.getDeclaredMethods()) {
            assertThat(method.getGenericReturnType().getTypeName())
                .as("facade method %s must not return raw domain records", method.getName())
                .doesNotContain(forbiddenTypeFragments.toArray(String[]::new));
        }
    }

    @Test
    void northboundDtoNamesAndFieldsAvoidEffectiveProductFacingConcepts() {
        List<Class<?>> dtoTypes = List.of(
            com.sovereign.connect.core.northbound.topology.NorthboundTopologySnapshot.class,
            com.sovereign.connect.core.northbound.topology.NorthboundTopologyVersionView.class,
            com.sovereign.connect.core.northbound.topology.NorthboundRoomView.class,
            com.sovereign.connect.core.northbound.topology.NorthboundZoneView.class,
            com.sovereign.connect.core.northbound.topology.NorthboundDeviceView.class,
            com.sovereign.connect.core.northbound.topology.NorthboundEndpointView.class,
            com.sovereign.connect.core.northbound.topology.NorthboundCapabilityView.class,
            com.sovereign.connect.core.northbound.topology.NorthboundLocationQuery.class,
            com.sovereign.connect.core.northbound.runtime.NorthboundEndpointHealthView.class,
            com.sovereign.connect.core.northbound.runtime.NorthboundDeviceHealthView.class,
            com.sovereign.connect.core.northbound.runtime.NorthboundDiagnosticsView.class,
            com.sovereign.connect.core.northbound.runtime.NorthboundMigrationReadinessView.class,
            com.sovereign.connect.core.northbound.runtime.NorthboundRuntimeStateView.class,
            com.sovereign.connect.core.northbound.runtime.NorthboundTemporalRuntimeStatusView.class,
            com.sovereign.connect.core.northbound.runtime.NorthboundRecoveryStatusView.class,
            com.sovereign.connect.core.northbound.temporal.NorthboundCreateSignalTemporalActRequest.class,
            com.sovereign.connect.core.northbound.temporal.NorthboundCancelTemporalActRequest.class,
            com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActView.class,
            com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActFilter.class
        );
        List<String> forbidden = List.of(
            "Effective",
            "Projected",
            "Surface",
            "Session",
            "Policy",
            "Authority",
            "Identity",
            "VisibilityRule"
        );

        for (Class<?> dtoType : dtoTypes) {
            assertThat(dtoType.getSimpleName())
                .as("DTO type name must avoid product/effective concepts")
                .doesNotContain(forbidden.toArray(String[]::new));
            for (RecordComponent component : dtoType.getRecordComponents()) {
                assertThat(component.getName())
                    .as("DTO field %s.%s must avoid product/effective concepts", dtoType.getSimpleName(), component.getName())
                    .doesNotContainIgnoringCase("surface")
                    .doesNotContainIgnoringCase("session")
                    .doesNotContainIgnoringCase("policy")
                    .doesNotContainIgnoringCase("authority")
                    .doesNotContainIgnoringCase("identity")
                    .doesNotContainIgnoringCase("visibility");
            }
        }
    }

    private void assertFileDoesNotContain(Path path, List<String> forbidden) {
        try {
            String content = Files.readString(path);
            for (String banned : forbidden) {
                assertThat(content)
                    .as("northbound file %s must not contain %s", path.getFileName(), banned)
                    .doesNotContain(banned);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
