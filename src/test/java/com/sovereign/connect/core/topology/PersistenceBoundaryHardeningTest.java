package com.sovereign.connect.core.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
import com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService;
import com.sovereign.connect.core.topology.materialization.DeviceDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.EndpointDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.MaterializationDecision;
import com.sovereign.connect.core.topology.materialization.MaterializationDecisionKind;
import com.sovereign.connect.core.topology.model.CapabilityKind;
import com.sovereign.connect.core.topology.model.CapabilityNode;
import com.sovereign.connect.core.topology.model.CapabilityTraits;
import com.sovereign.connect.core.topology.model.DeviceHealth;
import com.sovereign.connect.core.topology.model.DeviceKind;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.DeviceProvider;
import com.sovereign.connect.core.topology.model.DeviceTraits;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointKind;
import com.sovereign.connect.core.topology.model.EndpointMetadata;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.EndpointTraits;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.model.ProviderDeviceRef;
import com.sovereign.connect.core.topology.model.ProviderEndpointRef;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.RoomTraits;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceBoundaryHardeningTest {

    @TempDir
    private Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void structuralSaveDoesNotTouchEndpointHealthTable() {
        DataSource dataSource = dataSource(jdbcUrl("structural-only"));
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource, mapper(), clock);
        BaseTopologyService service = new BaseTopologyService(repository, repository, clock);

        service.createInitialTopology(
            "habitat-boundary",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device(List.of("endpoint.light.kitchen-main"))),
            List.of(endpoint("endpoint.light.kitchen-main"))
        );
        HabitatBaseTopology topology = repository.findByHabitatId("habitat-boundary").orElseThrow();

        assertThat(repository.findEndpointHealth("habitat-boundary", "endpoint.light.kitchen-main"))
            .as("structural save must not create endpoint_health rows")
            .isEmpty();

        EndpointHealth degraded = new EndpointHealth(
            HealthStatus.DEGRADED,
            Instant.parse("2026-05-14T11:55:00Z"),
            "durable health"
        );
        repository.saveEndpointHealth("habitat-boundary", "endpoint.light.kitchen-main", degraded);

        repository.save(topology);
        assertThat(repository.findEndpointHealth("habitat-boundary", "endpoint.light.kitchen-main"))
            .as("structural save must not overwrite endpoint_health rows")
            .contains(degraded);

        new JdbcTemplate(dataSource).update(
            "DELETE FROM endpoint_health WHERE habitat_id = ? AND endpoint_id = ?",
            "habitat-boundary",
            "endpoint.light.kitchen-main"
        );

        repository.save(topology);
        assertThat(repository.findEndpointHealth("habitat-boundary", "endpoint.light.kitchen-main"))
            .as("structural save must not recreate missing endpoint_health rows")
            .isEmpty();
    }

    @Test
    void initialEndpointHealthIsWrittenOnMaterialization() {
        String jdbcUrl = jdbcUrl("initial-health");
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        BaseTopologyService mutationService = new BaseTopologyService(repository, repository, clock);
        DefaultTopologyMaterializationService materializer = new DefaultTopologyMaterializationService(
            mutationService,
            repository,
            adapterInstanceId -> true,
            clock
        );

        mutationService.createInitialTopology(
            "habitat-materialized-health",
            List.of(emptyRoom()),
            List.of(emptyZone()),
            List.of(),
            List.of()
        );
        materializer.materialize("habitat-materialized-health", deviceFact());
        TopologyVersion beforeEndpoint = repository.findCurrentVersion("habitat-materialized-health").orElseThrow();

        MaterializationDecision decision = materializer.materialize("habitat-materialized-health", endpointFact());

        String endpointId = materializer.canonicalEndpointId("tuya", "tuya-device-abc", "dp-1");
        assertThat(decision.kind()).isEqualTo(MaterializationDecisionKind.ACCEPT_STRUCTURAL_MUTATION);
        assertThat(repository.findEndpointHealth("habitat-materialized-health", endpointId))
            .contains(new EndpointHealth(
                HealthStatus.UNKNOWN,
                Instant.parse("2026-05-14T12:00:02Z"),
                "materialized from discovery fact"
            ));
        assertThat(repository.findCurrentVersion("habitat-materialized-health"))
            .contains(decision.resultingTopologyVersion().orElseThrow());
        assertThat(decision.resultingTopologyVersion().orElseThrow()).isNotEqualTo(beforeEndpoint);
    }

    @Test
    void serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect() {
        String jdbcUrl = jdbcUrl("service-health");
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        BaseTopologyService service = new BaseTopologyService(repository, repository, clock);

        service.createInitialTopology(
            "habitat-service-health",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device(List.of("endpoint.light.kitchen-main"))),
            List.of(endpoint("endpoint.light.kitchen-main"))
        );
        TopologyVersion before = repository.findCurrentVersion("habitat-service-health").orElseThrow();

        service.updateEndpointHealth("habitat-service-health", "endpoint.light.kitchen-main", HealthStatus.DEGRADED);

        assertThat(repository.findCurrentVersion("habitat-service-health")).contains(before);
        assertThat(repository.findEndpointHealth("habitat-service-health", "endpoint.light.kitchen-main"))
            .contains(new EndpointHealth(HealthStatus.DEGRADED, Instant.parse("2026-05-14T12:00:00Z"), "online"));

        repository = null;
        service = null;

        H2BaseTopologyRepository recoveredRepository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        CoreSnapshotQueryService recoveredQueryService = new CoreSnapshotQueryService(recoveredRepository, clock);

        assertThat(recoveredQueryService.findEndpointHealth("habitat-service-health", "endpoint.light.kitchen-main"))
            .contains(new EndpointHealth(HealthStatus.DEGRADED, Instant.parse("2026-05-14T12:00:00Z"), "online"));
        assertThat(recoveredQueryService.findCurrentTopologyVersion("habitat-service-health")).contains(before);
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private String jdbcUrl(String name) {
        String dbPath = tempDir.resolve(name).toAbsolutePath().toString().replace('\\', '/');
        return "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=0";
    }

    private DataSource dataSource(String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, "sa", "");
    }

    private RoomNode room(List<String> endpointIds) {
        return new RoomNode(
            "room.kitchen",
            "Kitchen",
            List.of("zone.kitchen.worktop"),
            List.of("device.light.kitchen-main"),
            endpointIds,
            new RoomTraits(false, false)
        );
    }

    private ZoneNode zone(List<String> endpointIds) {
        return new ZoneNode(
            "zone.kitchen.worktop",
            "Kitchen Worktop",
            "room.kitchen",
            List.of("device.light.kitchen-main"),
            endpointIds,
            new ZoneTraits(true)
        );
    }

    private RoomNode emptyRoom() {
        return new RoomNode(
            "room.kitchen",
            "Kitchen",
            List.of("zone.kitchen.worktop"),
            List.of(),
            List.of(),
            new RoomTraits(false, false)
        );
    }

    private ZoneNode emptyZone() {
        return new ZoneNode(
            "zone.kitchen.worktop",
            "Kitchen Worktop",
            "room.kitchen",
            List.of(),
            List.of(),
            new ZoneTraits(true)
        );
    }

    private DeviceNode device(List<String> endpointIds) {
        return new DeviceNode(
            "device.light.kitchen-main",
            "kitchen-main-light",
            "Kitchen Main Light",
            "room.kitchen",
            "zone.kitchen.worktop",
            DeviceKind.LIGHT,
            DeviceProvider.TUYA,
            endpointIds,
            List.of(),
            new DeviceTraits(false, false, true),
            new DeviceHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-14T11:59:00Z"), "online"),
            new ProviderDeviceRef("tuya", "tuya.device.abc", Map.of("providerHomeId", "tuya-home-1"))
        );
    }

    private EndpointNode endpoint(String endpointId) {
        return new EndpointNode(
            endpointId,
            "device.light.kitchen-main",
            endpointId.substring(endpointId.lastIndexOf('.') + 1),
            endpointId,
            EndpointKind.LIGHT,
            "room.kitchen",
            "zone.kitchen.worktop",
            List.of(binarySwitchCapability()),
            new EndpointTraits(true, true, true, true, false, false),
            new EndpointHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-14T11:59:00Z"), "online"),
            new ProviderEndpointRef(
                "tuya",
                "tuya.device.abc",
                "tuya.dp.1",
                Map.of("dpCode", "switch_led", "providerRevision", "rev-1")
            ),
            EndpointMetadata.empty()
        );
    }

    private CapabilityNode binarySwitchCapability() {
        return new CapabilityNode(
            "capability.switch",
            "Switch",
            CapabilityKind.BINARY_SWITCH,
            new CapabilityTraits(true, true, false)
        );
    }

    private DeviceDiscoveryFact deviceFact() {
        return new DeviceDiscoveryFact(
            UUID.fromString("00000000-0000-0000-0000-000000000011"),
            "adapter-1",
            "tuya",
            "tuya-device-abc",
            "LIGHT",
            "Kitchen Worktop Light",
            "Tuya",
            "room.kitchen",
            "zone.kitchen.worktop",
            Instant.parse("2026-05-14T12:00:01Z"),
            0.98,
            Map.of("manufacturer", "tuya")
        );
    }

    private EndpointDiscoveryFact endpointFact() {
        return new EndpointDiscoveryFact(
            UUID.fromString("00000000-0000-0000-0000-000000000012"),
            "adapter-1",
            "tuya",
            "tuya-device-abc",
            "dp-1",
            "LIGHT",
            List.of(),
            Instant.parse("2026-05-14T12:00:02Z"),
            0.98,
            Map.of("dpCode", "switch_led")
        );
    }
}
