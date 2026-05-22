package com.sovereign.connect.core.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
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
import com.sovereign.connect.core.topology.model.TargetValidationResult;
import com.sovereign.connect.core.topology.model.TopologyTargetRef;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.query.CoreSnapshot;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
import com.sovereign.connect.core.topology.query.DeviceSnapshot;
import com.sovereign.connect.core.topology.query.EndpointSnapshot;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoreSnapshotQuerySeedTest {

    @TempDir
    private Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-10T13:00:00Z"), ZoneOffset.UTC);

    @Test
    void ac001ToAc025CoreSnapshotQueryComposesRecoveredCanonicalReadModel() {
        String jdbcUrl = jdbcUrl();
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        BaseTopologyService mutationService = new BaseTopologyService(repository, clock);
        CoreSnapshotQueryService queryService = new CoreSnapshotQueryService(repository, clock);

        mutationService.createInitialTopology(
            "habitat-001",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        );
        mutationService.addEndpointWithResult(
            "habitat-001",
            endpoint("endpoint.light.kitchen-dimmer", "tuya.dp.2", List.of(dimmerLevelCapability()))
        );
        repository.saveDeviceState("habitat-001", "device.light.kitchen-main", Map.of("power", "on", "level", 75));
        repository.saveEndpointHealth(
            "habitat-001",
            "endpoint.light.kitchen-main",
            new EndpointHealth(HealthStatus.DEGRADED, Instant.parse("2026-05-10T12:59:00Z"), "radio intermittent")
        );
        TopologyVersion persistedVersion = queryService.findCurrentTopologyVersion("habitat-001").orElseThrow();

        repository = null;
        mutationService = null;
        queryService = null;

        H2BaseTopologyRepository recoveredRepository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        CoreSnapshotQueryService recoveredQueryService = new CoreSnapshotQueryService(recoveredRepository, clock);

        TopologyVersion beforeQueries = recoveredQueryService.findCurrentTopologyVersion("habitat-001").orElseThrow();
        CoreSnapshot currentSnapshot = recoveredQueryService.findCurrentSnapshot("habitat-001").orElseThrow();
        TopologyVersion directVersion = recoveredQueryService.findCurrentTopologyVersion("habitat-001").orElseThrow();
        DeviceSnapshot deviceSnapshot = recoveredQueryService.findDevice("habitat-001", "device.light.kitchen-main").orElseThrow();
        EndpointSnapshot endpointSnapshot = recoveredQueryService.findEndpoint("habitat-001", "endpoint.light.kitchen-main").orElseThrow();

        assertThat(beforeQueries).isEqualTo(persistedVersion);
        assertThat(currentSnapshot.habitatId()).isEqualTo("habitat-001");
        assertThat(currentSnapshot.topologyVersion()).isEqualTo(directVersion);
        assertThat(currentSnapshot.topologyVersion()).isEqualTo(currentSnapshot.topology().topologyVersion());
        assertThat(recoveredRepository.findSnapshot("habitat-001").orElseThrow().topologyVersion())
            .isEqualTo(currentSnapshot.topologyVersion());

        assertThat(deviceSnapshot.topologyVersion()).isEqualTo(currentSnapshot.topologyVersion());
        assertThat(deviceSnapshot.device().deviceId()).isEqualTo("device.light.kitchen-main");
        assertThat(deviceSnapshot.state()).containsEntry("power", "on").containsEntry("level", 75);
        assertThat(recoveredQueryService.findDeviceState("habitat-001", "device.light.kitchen-main"))
            .contains(Map.of("power", "on", "level", 75));

        assertThat(endpointSnapshot.topologyVersion()).isEqualTo(currentSnapshot.topologyVersion());
        assertThat(endpointSnapshot.endpoint().endpointId()).isEqualTo("endpoint.light.kitchen-main");
        assertThat(endpointSnapshot.endpoint().capabilities()).extracting(CapabilityNode::capabilityId)
            .contains("capability.switch", "capability.level");
        assertThat(endpointSnapshot.health().status()).isEqualTo(HealthStatus.DEGRADED);
        assertThat(recoveredQueryService.findEndpointHealth("habitat-001", "endpoint.light.kitchen-main"))
            .contains(endpointSnapshot.health());

        assertThat(currentSnapshot.deviceStates()).containsKey("device.light.kitchen-main");
        assertThat(currentSnapshot.endpointHealth()).containsKey("endpoint.light.kitchen-main");

        assertThat(recoveredQueryService.findDevice("habitat-001", "device.missing")).isEmpty();
        assertThat(recoveredQueryService.findEndpoint("habitat-001", "endpoint.missing")).isEmpty();
        assertThat(recoveredQueryService.findDevice("habitat-001", "tuya.device.abc")).isEmpty();
        assertThat(recoveredQueryService.findEndpoint("habitat-001", "tuya.dp.1")).isEmpty();

        DeviceNode recoveredDevice = deviceSnapshot.device();
        EndpointNode recoveredEndpoint = endpointSnapshot.endpoint();
        assertThat(recoveredDevice.providerRef().providerDeviceId()).isEqualTo("tuya.device.abc");
        assertThat(recoveredEndpoint.providerRef().providerEndpointId()).isEqualTo("tuya.dp.1");
        assertThat(recoveredDevice.providerRef().providerDeviceId()).isNotEqualTo(recoveredDevice.deviceId());
        assertThat(recoveredEndpoint.providerRef().providerEndpointId()).isNotEqualTo(recoveredEndpoint.endpointId());

        TopologyVersion afterQueries = recoveredQueryService.findCurrentTopologyVersion("habitat-001").orElseThrow();
        assertThat(afterQueries).isEqualTo(beforeQueries);

        BaseTopologyService recoveredMutationService = new BaseTopologyService(recoveredRepository, clock);
        assertThat(recoveredMutationService.validateTarget(
            "habitat-001",
            new TopologyTargetRef("device.light.kitchen-main", "endpoint.light.kitchen-main", "capability.switch"),
            afterQueries
        )).isEqualTo(TargetValidationResult.VALID);

        assertThat(Arrays.stream(CoreSnapshotQueryService.class.getDeclaredFields())
            .map(field -> field.getType().getName().toLowerCase() + " " + field.getName().toLowerCase())
            .toList())
            .noneMatch(surface -> List.of("basetopologyservice", "scb", "scd", "hub", "projection", "session", "identity", "authority", "policy")
                .stream()
                .anyMatch(surface::contains));
        assertThat(Arrays.stream(CoreSnapshotQueryService.class.getDeclaredMethods()).map(Method::getName).toList())
            .contains("findCurrentSnapshot", "findCurrentTopologyVersion", "findDevice", "findEndpoint", "findDeviceState", "findEndpointHealth")
            .doesNotContain("addEndpoint", "createInitialTopology", "updateDeviceState", "updateEndpointHealth");

        HabitatBaseTopology topology = currentSnapshot.topology();
        assertThat(topology.topologyVersion()).isEqualTo(currentSnapshot.topologyVersion());
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private String jdbcUrl() {
        String dbPath = tempDir.resolve("sc-query").toAbsolutePath().toString().replace('\\', '/');
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

    private DeviceNode device(String deviceId, List<String> endpointIds) {
        return new DeviceNode(
            deviceId,
            "kitchen-main-light",
            "Kitchen Main Light",
            "room.kitchen",
            "zone.kitchen.worktop",
            DeviceKind.LIGHT,
            DeviceProvider.TUYA,
            endpointIds,
            List.of(),
            new DeviceTraits(false, false, true),
            new DeviceHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-10T12:58:00Z"), "online"),
            new ProviderDeviceRef("tuya", "tuya.device.abc", Map.of("providerHomeId", "tuya-home-1"))
        );
    }

    private EndpointNode lightEndpoint() {
        return endpoint("endpoint.light.kitchen-main", "tuya.dp.1", List.of(binarySwitchCapability(), levelCapability()));
    }

    private EndpointNode endpoint(String endpointId, String providerEndpointId, List<CapabilityNode> capabilities) {
        return new EndpointNode(
            endpointId,
            "device.light.kitchen-main",
            endpointId.substring(endpointId.lastIndexOf('.') + 1),
            endpointId,
            EndpointKind.LIGHT,
            "room.kitchen",
            "zone.kitchen.worktop",
            capabilities,
            new EndpointTraits(true, true, true, true, false, false),
            new EndpointHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-10T12:58:00Z"), "online"),
            new ProviderEndpointRef(
                "tuya",
                "tuya.device.abc",
                providerEndpointId,
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

    private CapabilityNode levelCapability() {
        return new CapabilityNode(
            "capability.level",
            "Level",
            CapabilityKind.LEVEL,
            new CapabilityTraits(true, true, false)
        );
    }

    private CapabilityNode dimmerLevelCapability() {
        return new CapabilityNode(
            "capability.level.dimmer",
            "Dimmer Level",
            CapabilityKind.LEVEL,
            new CapabilityTraits(true, true, false)
        );
    }
}
