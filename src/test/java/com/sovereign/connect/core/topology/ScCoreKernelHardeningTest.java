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
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScCoreKernelHardeningTest {

    @TempDir
    private Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-10T14:00:00Z"), ZoneOffset.UTC);

    @Test
    void composedDurableKernelPreservesVersionIdentityStateHealthAndQueryBoundariesAfterRecovery() {
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
            endpoint("endpoint.light.kitchen-dimmer", "tuya.dp.2", List.of(levelCapability()))
        );
        repository.saveDeviceState("habitat-001", "device.light.kitchen-main", Map.of("power", "on", "level", 75));
        repository.saveEndpointHealth(
            "habitat-001",
            "endpoint.light.kitchen-main",
            new EndpointHealth(HealthStatus.DEGRADED, Instant.parse("2026-05-10T13:59:00Z"), "radio intermittent")
        );
        assertThat(queryService.findCurrentTopologyVersion("habitat-001")).isPresent();

        repository = null;
        mutationService = null;
        queryService = null;

        H2BaseTopologyRepository recoveredRepository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        CoreSnapshotQueryService recoveredQueryService = new CoreSnapshotQueryService(recoveredRepository, clock);
        BaseTopologyService recoveredMutationService = new BaseTopologyService(recoveredRepository, clock);

        TopologyVersion versionBeforeQueries = recoveredQueryService.findCurrentTopologyVersion("habitat-001").orElseThrow();
        CoreSnapshot snapshot = recoveredQueryService.findCurrentSnapshot("habitat-001").orElseThrow();
        DeviceSnapshot canonicalDevice = recoveredQueryService.findDevice("habitat-001", "device.light.kitchen-main").orElseThrow();
        EndpointSnapshot canonicalEndpoint = recoveredQueryService.findEndpoint("habitat-001", "endpoint.light.kitchen-main").orElseThrow();
        TopologyVersion versionAfterQueries = recoveredQueryService.findCurrentTopologyVersion("habitat-001").orElseThrow();

        assertThat(versionAfterQueries).isEqualTo(versionBeforeQueries);
        assertThat(snapshot.topologyVersion()).isEqualTo(versionBeforeQueries);
        assertThat(snapshot.topologyVersion()).isEqualTo(snapshot.topology().topologyVersion());
        assertThat(recoveredRepository.findSnapshot("habitat-001").orElseThrow().topologyVersion())
            .isEqualTo(snapshot.topologyVersion());

        assertThat(canonicalDevice.device().deviceId()).isEqualTo("device.light.kitchen-main");
        assertThat(canonicalEndpoint.endpoint().endpointId()).isEqualTo("endpoint.light.kitchen-main");
        assertThat(snapshot.topology().devices()).extracting(DeviceNode::deviceId)
            .containsExactly("device.light.kitchen-main");
        assertThat(snapshot.topology().endpoints()).extracting(EndpointNode::endpointId)
            .containsExactly("endpoint.light.kitchen-main", "endpoint.light.kitchen-dimmer");

        assertThat(recoveredQueryService.findDevice("habitat-001", "tuya.device.abc")).isEmpty();
        assertThat(canonicalDevice.device().providerRef().providerDeviceId()).isEqualTo("tuya.device.abc");
        assertThat(canonicalDevice.device().providerRef().providerDeviceId()).isNotEqualTo(canonicalDevice.device().deviceId());

        assertThat(canonicalDevice.state()).containsEntry("power", "on").containsEntry("level", 75);
        assertThat(snapshot.deviceStates()).containsEntry("device.light.kitchen-main", Map.of("power", "on", "level", 75));
        assertThat(canonicalEndpoint.health().status()).isEqualTo(HealthStatus.DEGRADED);
        assertThat(snapshot.endpointHealth()).containsKey("endpoint.light.kitchen-main");

        assertThat(recoveredMutationService.validateTarget(
            "habitat-001",
            new TopologyTargetRef("device.light.kitchen-main", "endpoint.light.kitchen-main", "capability.switch"),
            versionAfterQueries
        )).isEqualTo(TargetValidationResult.VALID);

        assertThat(Arrays.stream(CoreSnapshotQueryService.class.getDeclaredFields())
            .map(field -> field.getType().getName().toLowerCase() + " " + field.getName().toLowerCase())
            .toList())
            .noneMatch(surface -> List.of("basetopologyservice", "scb", "scd", "projection", "authority")
                .stream()
                .anyMatch(surface::contains));
        assertThat(Arrays.stream(CoreSnapshotQueryService.class.getConstructors()).map(this::constructorSurface).toList())
            .noneMatch(surface -> List.of("basetopologyservice", "scb", "scd", "projection", "authority")
                .stream()
                .anyMatch(surface::contains));
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private String constructorSurface(Constructor<?> constructor) {
        return Arrays.stream(constructor.getParameterTypes())
            .map(Class::getName)
            .reduce("", (left, right) -> left + " " + right)
            .toLowerCase();
    }

    private String jdbcUrl() {
        String dbPath = tempDir.resolve("sc-kernel").toAbsolutePath().toString().replace('\\', '/');
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
            new DeviceHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-10T13:58:00Z"), "online"),
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
            new EndpointHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-10T13:58:00Z"), "online"),
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
}
