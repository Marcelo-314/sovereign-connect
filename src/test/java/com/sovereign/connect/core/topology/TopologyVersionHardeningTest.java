package com.sovereign.connect.core.topology;

import com.sovereign.connect.adapter.persistence.InMemoryBaseTopologyRepository;
import com.sovereign.connect.core.topology.event.TopologyChangeKind;
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
import com.sovereign.connect.core.topology.model.IdempotencyIdentity;
import com.sovereign.connect.core.topology.model.ProviderDeviceRef;
import com.sovereign.connect.core.topology.model.ProviderEndpointRef;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.RoomTraits;
import com.sovereign.connect.core.topology.model.TargetValidationResult;
import com.sovereign.connect.core.topology.model.TopologyMutationResult;
import com.sovereign.connect.core.topology.model.TopologyTargetRef;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.TopologyVersionScopeType;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopologyVersionHardeningTest {

    private final BaseTopologyRepository repository = new InMemoryBaseTopologyRepository();
    private final BaseTopologyService service = new BaseTopologyService(
        repository,
        Clock.fixed(Instant.parse("2026-05-09T12:30:00Z"), ZoneOffset.UTC)
    );

    @Test
    void ac001ToAc004TopologyVersionIsTypedHabitatScopedInitialAndAdvancesOnlyForAcceptedStructuralMutation() {
        HabitatBaseTopology initial = createSingleEndpointTopology();

        assertThat(initial.topologyVersion()).isInstanceOf(TopologyVersion.class);
        assertThat(initial.topologyVersion().scope().type()).isEqualTo(TopologyVersionScopeType.HABITAT);
        assertThat(initial.topologyVersion().scope().id()).isEqualTo("habitat-001");
        assertThat(initial.topologyVersion().value()).isEqualTo("1");

        HabitatBaseTopology mutated = service.addEndpoint(
            "habitat-001",
            endpoint("endpoint.light.kitchen-dimmer", "tuya.dp.2", Map.of("providerRevision", "rev-2"), List.of(levelCapability()))
        );

        assertThat(mutated.topologyVersion().value()).isEqualTo("2");
        assertThat(mutated.topologyVersion().scope().id()).isEqualTo("habitat-001");
    }

    @Test
    void ac005RejectedStructuralMutationDoesNotAdvanceTopologyVersion() {
        createSingleEndpointTopology();
        TopologyVersion before = repository.findCurrentVersion("habitat-001").orElseThrow();

        assertThatThrownBy(() -> service.addEndpoint("habitat-001", lightEndpoint()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endpointId already exists");

        assertThat(repository.findCurrentVersion("habitat-001")).contains(before);
        assertThat(repository.findByHabitatId("habitat-001").orElseThrow().endpoints())
            .extracting(EndpointNode::endpointId)
            .containsExactly("endpoint.light.kitchen-main");
    }

    @Test
    void ac006DeviceStateUpdateDoesNotAdvanceTopologyVersion() {
        createSingleEndpointTopology();
        TopologyVersion before = repository.findCurrentVersion("habitat-001").orElseThrow();

        service.updateDeviceState("habitat-001", "device.light.kitchen-main", Map.of("power", "on", "level", 75));

        assertThat(repository.findCurrentVersion("habitat-001")).contains(before);
        assertThat(service.findDeviceState("habitat-001", "device.light.kitchen-main"))
            .contains(Map.of("power", "on", "level", 75));
    }

    @Test
    void ac007EndpointHealthUpdateDoesNotAdvanceTopologyVersion() {
        createSingleEndpointTopology();
        TopologyVersion before = repository.findCurrentVersion("habitat-001").orElseThrow();

        service.updateEndpointHealth("habitat-001", "endpoint.light.kitchen-main", HealthStatus.DEGRADED);

        HabitatBaseTopology retrieved = repository.findByHabitatId("habitat-001").orElseThrow();
        assertThat(retrieved.topologyVersion()).isEqualTo(before);
        assertThat(retrieved.endpoints()).singleElement()
            .extracting(endpoint -> endpoint.health().status())
            .isEqualTo(HealthStatus.DEGRADED);
    }

    @Test
    void ac008ToAc010ProjectionSessionAuthorityAndPolicyHaveNoVersionAdvancementPath() {
        createSingleEndpointTopology();
        TopologyVersion before = repository.findCurrentVersion("habitat-001").orElseThrow();

        List<String> forbidden = List.of("projection", "session", "authority", "policy");
        List<String> serviceSurface = Arrays.stream(BaseTopologyService.class.getMethods())
            .map(this::methodSurface)
            .map(String::toLowerCase)
            .toList();

        assertThat(serviceSurface)
            .noneMatch(surface -> forbidden.stream().anyMatch(surface::contains));
        assertThat(repository.findCurrentVersion("habitat-001")).contains(before);
    }

    @Test
    void ac011AndAc012ProviderNativeRevisionIsMetadataAndDoesNotAdvanceTopologyVersion() {
        HabitatBaseTopology initial = createSingleEndpointTopology();
        EndpointNode endpoint = initial.endpoints().getFirst();

        assertThat(endpoint.providerRef().nativeCoordinates()).containsEntry("providerRevision", "rev-1");
        assertThat(initial.topologyVersion().value()).isNotEqualTo(endpoint.providerRef().nativeCoordinates().get("providerRevision"));

        TopologyVersion before = initial.topologyVersion();
        service.updateDeviceState("habitat-001", "device.light.kitchen-main", Map.of("providerRevision", "rev-2"));

        assertThat(repository.findCurrentVersion("habitat-001")).contains(before);
        assertThat(repository.findByHabitatId("habitat-001").orElseThrow().endpoints().getFirst().providerRef().nativeCoordinates())
            .containsEntry("providerRevision", "rev-1");
    }

    @Test
    void ac013AndAc014SnapshotIncludesVersionAndStaysConsistentAfterMutation() {
        HabitatBaseTopology initial = createSingleEndpointTopology();
        assertThat(repository.findByHabitatId("habitat-001").orElseThrow().topologyVersion()).isEqualTo(initial.topologyVersion());

        HabitatBaseTopology mutated = service.addEndpoint(
            "habitat-001",
            endpoint("endpoint.light.kitchen-dimmer", "tuya.dp.2", Map.of("providerRevision", "rev-2"), List.of(levelCapability()))
        );

        HabitatBaseTopology retrieved = repository.findByHabitatId("habitat-001").orElseThrow();
        assertThat(retrieved.topologyVersion()).isEqualTo(mutated.topologyVersion());
        assertThat(retrieved.topologyVersion().value()).isEqualTo("2");
        assertThat(retrieved.endpoints()).extracting(EndpointNode::endpointId)
            .containsExactly("endpoint.light.kitchen-main", "endpoint.light.kitchen-dimmer");
        assertThat(retrieved.devices()).singleElement()
            .extracting(DeviceNode::endpointIds)
            .isEqualTo(List.of("endpoint.light.kitchen-main", "endpoint.light.kitchen-dimmer"));
    }

    @Test
    void ac015AndAc016MutationResultAndEventExposeFromAndToVersions() {
        createSingleEndpointTopology();

        TopologyMutationResult result = service.addEndpointWithResult(
            "habitat-001",
            endpoint("endpoint.light.kitchen-dimmer", "tuya.dp.2", Map.of("providerRevision", "rev-2"), List.of(levelCapability()))
        );

        assertThat(result.habitatId()).isEqualTo("habitat-001");
        assertThat(result.fromVersion().value()).isEqualTo("1");
        assertThat(result.toVersion().value()).isEqualTo("2");
        assertThat(result.toVersion()).isNotEqualTo(result.fromVersion());
        assertThat(result.changeKinds()).containsExactly(TopologyChangeKind.ENDPOINT_ADDED);
        assertThat(service.emittedEvents()).singleElement().satisfies(event -> {
            assertThat(event.fromVersion()).isEqualTo("1");
            assertThat(event.toVersion()).isEqualTo("2");
        });
    }

    @Test
    void ac017ToAc019StaleTargetValidationRevalidatesOrFailsBeforeDispatch() {
        HabitatBaseTopology initial = createSingleEndpointTopology();
        TopologyTargetRef validTarget = new TopologyTargetRef(
            "device.light.kitchen-main",
            "endpoint.light.kitchen-main",
            "capability.switch"
        );

        assertThat(service.validateTarget("habitat-001", validTarget, initial.topologyVersion()))
            .isEqualTo(TargetValidationResult.VALID);

        service.addEndpoint(
            "habitat-001",
            endpoint("endpoint.light.kitchen-dimmer", "tuya.dp.2", Map.of("providerRevision", "rev-2"), List.of(levelCapability()))
        );

        assertThat(service.validateTarget("habitat-001", validTarget, initial.topologyVersion()))
            .isEqualTo(TargetValidationResult.VALID_AFTER_REVALIDATION);

        TopologyTargetRef changedTarget = new TopologyTargetRef(
            "device.light.kitchen-main",
            "endpoint.light.kitchen-main",
            "capability.color-temperature"
        );
        TopologyTargetRef missingTarget = new TopologyTargetRef(
            "device.light.kitchen-main",
            "endpoint.light.missing",
            "capability.switch"
        );

        assertThat(service.validateTarget("habitat-001", changedTarget, initial.topologyVersion()))
            .isEqualTo(TargetValidationResult.CAPABILITY_MISMATCH);
        assertThat(service.validateTarget("habitat-001", missingTarget, initial.topologyVersion()))
            .isEqualTo(TargetValidationResult.TARGET_NOT_FOUND);
    }

    @Test
    void ac020TopologyVersionIsExcludedFromIdempotencyIdentity() {
        TopologyTargetRef target = new TopologyTargetRef(
            "device.light.kitchen-main",
            "endpoint.light.kitchen-main",
            "capability.switch"
        );

        IdempotencyIdentity versionOneIdentity = new IdempotencyIdentity(
            "set-level",
            target,
            Map.of("level", 80)
        );
        IdempotencyIdentity versionTwoIdentity = new IdempotencyIdentity(
            "set-level",
            target,
            Map.of("level", 80)
        );

        assertThat(versionTwoIdentity).isEqualTo(versionOneIdentity);
        assertThat(Arrays.stream(IdempotencyIdentity.class.getRecordComponents()).map(RecordComponent::getName))
            .doesNotContain("topologyVersion");
    }

    @Test
    void ac021ToAc023NoScbScdOrUserSessionAuthorityPolicyDependenciesAreRequired() {
        List<String> forbidden = List.of("scb", "scd", "user", "session", "identity", "authority", "policy");
        List<String> serviceSurface = Arrays.stream(BaseTopologyService.class.getDeclaredFields())
            .map(field -> field.getType().getName().toLowerCase() + " " + field.getName().toLowerCase())
            .toList();
        List<String> aggregateFields = Arrays.stream(HabitatBaseTopology.class.getRecordComponents())
            .map(component -> component.getType().getName().toLowerCase() + " " + component.getName().toLowerCase())
            .toList();

        assertThat(serviceSurface).noneMatch(surface -> forbidden.stream().anyMatch(surface::contains));
        assertThat(aggregateFields).noneMatch(surface -> forbidden.stream().anyMatch(surface::contains));
    }

    @Test
    void ac024StructuralAndNonStructuralBehaviorAreCoveredTogether() {
        HabitatBaseTopology initial = createSingleEndpointTopology();

        service.updateEndpointHealth("habitat-001", "endpoint.light.kitchen-main", HealthStatus.DEGRADED);
        service.updateDeviceState("habitat-001", "device.light.kitchen-main", Map.of("power", "on"));
        assertThat(repository.findCurrentVersion("habitat-001")).contains(initial.topologyVersion());

        HabitatBaseTopology mutated = service.addEndpoint(
            "habitat-001",
            endpoint("endpoint.light.kitchen-dimmer", "tuya.dp.2", Map.of("providerRevision", "rev-2"), List.of(levelCapability()))
        );
        assertThat(mutated.topologyVersion().value()).isEqualTo("2");
    }

    private String methodSurface(Method method) {
        String parameterTypes = Arrays.stream(method.getParameterTypes())
            .map(Class::getName)
            .reduce("", (left, right) -> left + " " + right);
        return method.getName() + " " + method.getReturnType().getName() + parameterTypes;
    }

    private HabitatBaseTopology createSingleEndpointTopology() {
        return service.createInitialTopology(
            "habitat-001",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        );
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
            new DeviceHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-09T11:59:00Z"), "online"),
            new ProviderDeviceRef("tuya", "tuya.device.abc", Map.of("providerHomeId", "tuya-home-1"))
        );
    }

    private EndpointNode lightEndpoint() {
        return endpoint(
            "endpoint.light.kitchen-main",
            "tuya.dp.1",
            Map.of("dpCode", "switch_led", "providerRevision", "rev-1"),
            List.of(binarySwitchCapability(), levelCapability())
        );
    }

    private EndpointNode endpoint(
        String endpointId,
        String providerEndpointId,
        Map<String, String> nativeCoordinates,
        List<CapabilityNode> capabilities
    ) {
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
            new EndpointHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-09T11:59:00Z"), "online"),
            new ProviderEndpointRef("tuya", "tuya.device.abc", providerEndpointId, nativeCoordinates),
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
