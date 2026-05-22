package com.sovereign.connect.core.topology;

import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
import com.sovereign.connect.core.topology.event.TopologyChangeKind;
import com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService;
import com.sovereign.connect.core.topology.model.BaseTopologySnapshot;
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
import com.sovereign.connect.core.topology.model.TopologyMutationRecord;
import com.sovereign.connect.core.topology.model.TopologyMutationResult;
import com.sovereign.connect.core.topology.model.TopologyTargetRef;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;
import com.sovereign.connect.core.topology.port.TopologyMaterializationStatePort;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceMemorySeedTest {

    @TempDir
    private Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void ac001ToAc025TopologyMemorySurvivesAdapterAndServiceRecreation() {
        String jdbcUrl = jdbcUrl();
        H2BaseTopologyRepository firstRepository = new H2BaseTopologyRepository(dataSource(jdbcUrl), clockedMapper(), clock);
        BaseTopologyService firstService = new BaseTopologyService(firstRepository, firstRepository, clock);

        HabitatBaseTopology initial = firstService.createInitialTopology(
            "habitat-001",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        );
        TopologyVersion versionBeforeState = initial.topologyVersion();

        TopologyMutationResult mutationResult = firstService.addEndpointWithResult(
            "habitat-001",
            endpoint("endpoint.light.kitchen-dimmer", "tuya.dp.2", List.of(dimmerLevelCapability()))
        );
        firstRepository.appendMutationRecord(TopologyMutationRecord.fromResult(
            mutationResult,
            "endpoint added",
            Instant.parse("2026-05-10T12:01:00Z")
        ));

        TopologyVersion versionAfterMutation = firstRepository.findCurrentVersion("habitat-001").orElseThrow();
        firstRepository.saveDeviceState("habitat-001", "device.light.kitchen-main", Map.of("power", "on", "level", 75));
        assertThat(firstRepository.findCurrentVersion("habitat-001")).contains(versionAfterMutation);

        firstService.updateEndpointHealth("habitat-001", "endpoint.light.kitchen-main", HealthStatus.DEGRADED);
        TopologyVersion versionAfterHealth = firstRepository.findCurrentVersion("habitat-001").orElseThrow();
        assertThat(versionAfterHealth).isEqualTo(versionAfterMutation);
        assertThat(versionBeforeState.value()).isEqualTo("1");

        firstRepository = null;
        firstService = null;

        H2BaseTopologyRepository recoveredRepository = new H2BaseTopologyRepository(dataSource(jdbcUrl), clockedMapper(), clock);
        BaseTopologyService recoveredService = new BaseTopologyService(recoveredRepository, recoveredRepository, clock);

        BaseTopologySnapshot snapshot = recoveredRepository.findSnapshot("habitat-001").orElseThrow();
        HabitatBaseTopology recoveredTopology = recoveredRepository.findByHabitatId("habitat-001").orElseThrow();
        TopologyVersion recoveredVersion = recoveredRepository.findCurrentVersion("habitat-001").orElseThrow();

        assertThat(snapshot.topologyVersion()).isEqualTo(snapshot.topology().topologyVersion());
        assertThat(snapshot.topologyVersion()).isEqualTo(recoveredVersion);
        assertThat(recoveredTopology.topologyVersion()).isEqualTo(recoveredVersion);
        assertThat(recoveredVersion.value()).isEqualTo("2");

        DeviceNode recoveredDevice = recoveredTopology.devices().getFirst();
        EndpointNode recoveredMainEndpoint = recoveredTopology.endpoints().stream()
            .filter(endpoint -> endpoint.endpointId().equals("endpoint.light.kitchen-main"))
            .findFirst()
            .orElseThrow();
        assertThat(recoveredDevice.deviceId()).isEqualTo("device.light.kitchen-main");
        assertThat(recoveredTopology.endpoints()).extracting(EndpointNode::endpointId)
            .containsExactly("endpoint.light.kitchen-main", "endpoint.light.kitchen-dimmer");
        assertThat(recoveredDevice.providerRef().providerDeviceId()).isEqualTo("tuya.device.abc");
        assertThat(recoveredMainEndpoint.providerRef().providerEndpointId()).isEqualTo("tuya.dp.1");
        assertThat(recoveredDevice.providerRef().providerDeviceId()).isNotEqualTo(recoveredDevice.deviceId());
        assertThat(recoveredMainEndpoint.providerRef().providerEndpointId()).isNotEqualTo(recoveredMainEndpoint.endpointId());

        assertThat(recoveredRepository.findMutationRecords("habitat-001")).singleElement().satisfies(record -> {
            assertThat(record.fromVersion().value()).isEqualTo("1");
            assertThat(record.toVersion().value()).isEqualTo("2");
            assertThat(record.changeKinds()).containsExactly(TopologyChangeKind.ENDPOINT_ADDED);
            assertThat(record.affectedDeviceIds()).containsExactly("device.light.kitchen-main");
            assertThat(record.affectedEndpointIds()).containsExactly("endpoint.light.kitchen-dimmer");
        });

        assertThat(recoveredRepository.findDeviceState("habitat-001", "device.light.kitchen-main"))
            .contains(Map.of("power", "on", "level", 75));
        assertThat(recoveredRepository.findCurrentVersion("habitat-001")).contains(recoveredVersion);

        assertThat(recoveredRepository.findEndpointHealth("habitat-001", "endpoint.light.kitchen-main"))
            .contains(new EndpointHealth(HealthStatus.DEGRADED, Instant.parse("2026-05-10T12:00:00Z"), "online"));
        assertThat(recoveredTopology.endpoints().stream()
            .filter(endpoint -> endpoint.endpointId().equals("endpoint.light.kitchen-main"))
            .findFirst()
            .orElseThrow()
            .health()
            .status()).isEqualTo(HealthStatus.DEGRADED);

        TopologyTargetRef target = new TopologyTargetRef(
            "device.light.kitchen-main",
            "endpoint.light.kitchen-main",
            "capability.switch"
        );
        assertThat(recoveredService.validateTarget("habitat-001", target, recoveredVersion))
            .isEqualTo(TargetValidationResult.VALID);

        assertThat(BaseTopologyRepository.class.getDeclaredMethods()).extracting(Method::getName)
            .contains("save", "findByHabitatId", "findCurrentVersion");
        assertThat(Arrays.stream(BaseTopologyService.class.getConstructors()).map(this::constructorSurface).toList())
            .noneMatch(surface -> surface.contains("H2BaseTopologyRepository"));
        assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getDeclaredFields())
            .map(field -> field.getType().getName())
            .toList())
            .noneMatch(type -> type.contains("H2BaseTopologyRepository"));
        assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getConstructors())
            .map(this::constructorSurface)
            .toList())
            .anyMatch(surface -> surface.contains("MaterializationDecisionReplayPort"));
        assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getConstructors())
            .map(this::constructorSurface)
            .toList())
            .noneMatch(surface -> surface.contains("H2BaseTopologyRepository"));
        assertThat(H2BaseTopologyRepository.class.getInterfaces())
            .extracting(Class::getSimpleName)
            .contains(TopologyMaterializationStatePort.class.getSimpleName());
        assertThat(H2BaseTopologyRepository.class.getInterfaces())
            .extracting(Class::getSimpleName)
            .contains("MaterializationDecisionReplayPort");
        assertThat(Arrays.stream(H2BaseTopologyRepository.class.getDeclaredFields())
            .map(field -> field.getType().getName().toLowerCase() + " " + field.getName().toLowerCase())
            .toList())
            .noneMatch(surface -> List.of("scb", "scd", "hub", "projection", "session", "identity", "authority", "policy")
                .stream()
                .anyMatch(surface::contains));
    }

    private com.fasterxml.jackson.databind.ObjectMapper clockedMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    }

    private String constructorSurface(Constructor<?> constructor) {
        return Arrays.stream(constructor.getParameterTypes())
            .map(Class::getName)
            .reduce("", (left, right) -> left + " " + right);
    }

    private String jdbcUrl() {
        String dbPath = tempDir.resolve("sc-topology").toAbsolutePath().toString().replace('\\', '/');
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
            new DeviceHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-10T11:59:00Z"), "online"),
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
            new EndpointHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-10T11:59:00Z"), "online"),
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
