package com.sovereign.connect.core.topology;

import com.sovereign.connect.adapter.persistence.InMemoryBaseTopologyRepository;
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
import com.sovereign.connect.core.topology.model.TopologyVersionScopeType;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseTopologyServiceTest {

    private final BaseTopologyRepository repository = new InMemoryBaseTopologyRepository();
    private final BaseTopologyService service = new BaseTopologyService(
        repository,
        Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void createsAndRetrievesFullBaseTopologyHierarchy() {
        HabitatBaseTopology created = service.createInitialTopology(
            "habitat-001",
            List.of(room()),
            List.of(zone()),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main", "endpoint.sensor.kitchen-motion"))),
            List.of(lightEndpoint(), motionEndpoint())
        );

        assertThat(created.habitatId()).isEqualTo("habitat-001");
        assertThat(created.rooms()).singleElement().satisfies(room -> {
            assertThat(room.roomId()).isEqualTo("room.kitchen");
            assertThat(room.zoneIds()).containsExactly("zone.kitchen.worktop");
            assertThat(room.deviceIds()).containsExactly("device.light.kitchen-main");
            assertThat(room.endpointIds()).containsExactly("endpoint.light.kitchen-main", "endpoint.sensor.kitchen-motion");
        });
        assertThat(created.zones()).singleElement().satisfies(zone -> {
            assertThat(zone.zoneId()).isEqualTo("zone.kitchen.worktop");
            assertThat(zone.roomId()).isEqualTo("room.kitchen");
        });
        assertThat(created.devices()).singleElement().satisfies(device -> {
            assertThat(device.deviceId()).isEqualTo("device.light.kitchen-main");
            assertThat(device.endpointIds()).containsExactly("endpoint.light.kitchen-main", "endpoint.sensor.kitchen-motion");
        });
        assertThat(created.endpoints()).hasSize(2);
        assertThat(created.endpoints().getFirst().capabilities()).extracting(CapabilityNode::capabilityId)
            .containsExactly("capability.switch", "capability.level");

        assertThat(repository.findByHabitatId("habitat-001")).contains(created);
    }

    @Test
    void supportsOneDeviceWithMultipleFirstClassEndpoints() {
        HabitatBaseTopology topology = service.createInitialTopology(
            "habitat-001",
            List.of(room()),
            List.of(zone()),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main", "endpoint.sensor.kitchen-motion"))),
            List.of(lightEndpoint(), motionEndpoint())
        );

        assertThat(topology.devices()).singleElement()
            .extracting(DeviceNode::endpointIds)
            .isEqualTo(List.of("endpoint.light.kitchen-main", "endpoint.sensor.kitchen-motion"));
        assertThat(topology.endpoints()).extracting(EndpointNode::endpointId)
            .containsExactly("endpoint.light.kitchen-main", "endpoint.sensor.kitchen-motion");
        assertThat(topology.endpoints()).extracting(EndpointNode::deviceId)
            .containsOnly("device.light.kitchen-main");
    }

    @Test
    void supportsOneEndpointWithMultipleCapabilities() {
        HabitatBaseTopology topology = service.createInitialTopology(
            "habitat-001",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        );

        assertThat(topology.endpoints()).singleElement().satisfies(endpoint ->
            assertThat(endpoint.capabilities()).extracting(CapabilityNode::capabilityId)
                .containsExactly("capability.switch", "capability.level")
        );
    }

    @Test
    void storesProviderEndpointRefAsMetadataAndNeverAsCanonicalEndpointId() {
        HabitatBaseTopology topology = service.createInitialTopology(
            "habitat-001",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        );

        EndpointNode endpoint = topology.endpoints().getFirst();
        assertThat(endpoint.endpointId()).isEqualTo("endpoint.light.kitchen-main");
        assertThat(endpoint.providerRef().providerEndpointId()).isEqualTo("tuya.dp.1");
        assertThat(endpoint.endpointId()).isNotEqualTo(endpoint.providerRef().providerEndpointId());

        assertThatThrownBy(() -> endpoint("tuya.dp.1", "tuya.dp.1", List.of(binarySwitchCapability())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("providerEndpointId must not be used as canonical endpointId");
    }

    @Test
    void keepsProviderNativeIdsOutOfCanonicalTopologyIdentity() {
        HabitatBaseTopology topology = service.createInitialTopology(
            "habitat-001",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        );

        DeviceNode device = topology.devices().getFirst();
        EndpointNode endpoint = topology.endpoints().getFirst();
        assertThat(device.deviceId()).isNotEqualTo(device.providerRef().providerDeviceId());
        assertThat(endpoint.endpointId()).isNotEqualTo(endpoint.providerRef().providerEndpointId());
        assertThat(device.providerRef().nativeCoordinates()).containsEntry("providerHomeId", "tuya-home-1");
        assertThat(endpoint.providerRef().nativeCoordinates()).containsEntry("dpCode", "switch_led");

        assertThatThrownBy(() -> service.createInitialTopology(
            "habitat-002",
            List.of(room(List.of("tuya.device.abc"), List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("tuya.device.abc"), List.of("endpoint.light.kitchen-main"))),
            List.of(device("tuya.device.abc", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("providerDeviceId must not be used as canonical deviceId");
    }

    @Test
    void topologyContainsNoUserSessionAuthorityOrProjectionFields() {
        List<String> forbidden = List.of("userId", "sessionId", "authority", "projection", "policy");

        List<String> aggregateFields = Arrays.stream(HabitatBaseTopology.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
        List<String> endpointFields = Arrays.stream(EndpointNode.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

        assertThat(aggregateFields).doesNotContainAnyElementsOf(forbidden);
        assertThat(endpointFields).doesNotContainAnyElementsOf(forbidden);

        HabitatBaseTopology topology = service.createInitialTopology(
            "habitat-001",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        );
        assertThat(repository.findByHabitatId("habitat-001")).contains(topology);
    }

    @Test
    void topologyVersionExistsOnInitialSnapshotAndIsHabitatScoped() {
        HabitatBaseTopology topology = service.createInitialTopology(
            "habitat-001",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        );

        assertThat(topology.topologyVersion().value()).isEqualTo("1");
        assertThat(topology.topologyVersion().scope().type()).isEqualTo(TopologyVersionScopeType.HABITAT);
        assertThat(topology.topologyVersion().scope().id()).isEqualTo("habitat-001");
        assertThat(service.findCurrentVersion("habitat-001")).contains(topology.topologyVersion());
    }

    @Test
    void topologyVersionAdvancesAfterAcceptedStructuralMutation() {
        service.createInitialTopology(
            "habitat-001",
            List.of(room(List.of("endpoint.light.kitchen-main"))),
            List.of(zone(List.of("endpoint.light.kitchen-main"))),
            List.of(device("device.light.kitchen-main", List.of("endpoint.light.kitchen-main"))),
            List.of(lightEndpoint())
        );

        HabitatBaseTopology mutated = service.addEndpoint(
            "habitat-001",
            endpoint("endpoint.light.kitchen-dimmer", "tuya.dp.2", List.of(levelCapability()))
        );

        assertThat(mutated.topologyVersion().value()).isEqualTo("2");
        assertThat(repository.findCurrentVersion("habitat-001")).contains(mutated.topologyVersion());
        assertThat(mutated.devices()).singleElement()
            .extracting(DeviceNode::endpointIds)
            .isEqualTo(List.of("endpoint.light.kitchen-main", "endpoint.light.kitchen-dimmer"));
        assertThat(service.emittedEvents()).singleElement().satisfies(event -> {
            assertThat(event.habitatId()).isEqualTo("habitat-001");
            assertThat(event.fromVersion()).isEqualTo("1");
            assertThat(event.toVersion()).isEqualTo("2");
        });
    }

    @Test
    void deviceRoomZoneMismatchRejected() {
        RoomNode living = new RoomNode("room.living", "Living", List.of(), List.of(), List.of(), new RoomTraits(false, false));
        RoomNode kitchen = new RoomNode("room.kitchen", "Kitchen", List.of("zone.kitchen.worktop"), List.of(), List.of(), new RoomTraits(false, false));
        ZoneNode worktop = new ZoneNode("zone.kitchen.worktop", "Kitchen Worktop", "room.kitchen", List.of(), List.of(), new ZoneTraits(false));
        DeviceNode badDevice = new DeviceNode(
            "device.light.kitchen-main",
            "alias",
            "Kitchen Main Light",
            "room.living",
            "zone.kitchen.worktop",
            DeviceKind.LIGHT,
            DeviceProvider.TUYA,
            List.of(),
            List.of(),
            new DeviceTraits(false, false, true),
            new DeviceHealth(HealthStatus.UNKNOWN, Instant.parse("2026-05-09T12:00:00Z"), "ok"),
            new ProviderDeviceRef("tuya", "tuya.device.abc", Map.of())
        );

        assertThatThrownBy(() -> service.createInitialTopology(
            "habitat-001",
            List.of(living, kitchen),
            List.of(worktop),
            List.of(badDevice),
            List.of()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zone.roomId must equal device.roomId");
    }

    @Test
    void endpointRoomZoneMismatchRejected() {
        RoomNode living = new RoomNode("room.living", "Living", List.of(), List.of(), List.of(), new RoomTraits(false, false));
        RoomNode kitchen = new RoomNode("room.kitchen", "Kitchen", List.of("zone.kitchen.worktop"), List.of(), List.of(), new RoomTraits(false, false));
        ZoneNode worktop = new ZoneNode("zone.kitchen.worktop", "Kitchen Worktop", "room.kitchen", List.of(), List.of(), new ZoneTraits(false));
        DeviceNode device = new DeviceNode(
            "device.sensor.kitchen",
            "alias",
            "Kitchen Sensor",
            "room.kitchen",
            "zone.kitchen.worktop",
            DeviceKind.SENSOR,
            DeviceProvider.TUYA,
            List.of("endpoint.sensor.kitchen-main"),
            List.of(),
            new DeviceTraits(false, false, false),
            new DeviceHealth(HealthStatus.UNKNOWN, Instant.parse("2026-05-09T12:00:00Z"), "ok"),
            new ProviderDeviceRef("tuya", "tuya.device.xyz", Map.of())
        );
        EndpointNode badEndpoint = new EndpointNode(
            "endpoint.sensor.kitchen-main",
            "device.sensor.kitchen",
            "kitchen-main",
            "endpoint.sensor.kitchen-main",
            EndpointKind.SENSOR,
            "room.living",
            "zone.kitchen.worktop",
            List.of(),
            new EndpointTraits(false, false, false, false, false, false),
            new EndpointHealth(HealthStatus.UNKNOWN, Instant.parse("2026-05-09T12:00:00Z"), "ok"),
            new ProviderEndpointRef("tuya", "tuya.device.xyz", "tuya.dp.1", Map.of()),
            EndpointMetadata.empty()
        );

        assertThatThrownBy(() -> service.createInitialTopology(
            "habitat-001",
            List.of(living, kitchen),
            List.of(worktop),
            List.of(device),
            List.of(badEndpoint)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zone.roomId must equal endpoint.roomId");
    }

    @Test
    void roomZoneListBidirectionalMismatchRejected() {
        RoomNode living = new RoomNode("room.living", "Living", List.of("zone.kitchen.worktop"), List.of(), List.of(), new RoomTraits(false, false));
        RoomNode kitchen = new RoomNode("room.kitchen", "Kitchen", List.of(), List.of(), List.of(), new RoomTraits(false, false));
        ZoneNode worktop = new ZoneNode("zone.kitchen.worktop", "Worktop", "room.kitchen", List.of(), List.of(), new ZoneTraits(false));

        assertThatThrownBy(() -> service.createInitialTopology(
            "habitat-001",
            List.of(living, kitchen),
            List.of(worktop),
            List.of(),
            List.of()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zone.roomId == room.roomId");
    }

    @Test
    void endpointRoomMismatchWithParentDeviceRejected() {
        RoomNode living = new RoomNode("room.living", "Living", List.of("zone.living.corner"), List.of(), List.of(), new RoomTraits(false, false));
        RoomNode kitchen = new RoomNode("room.kitchen", "Kitchen", List.of("zone.kitchen.worktop"), List.of("device.sensor.kitchen"), List.of(), new RoomTraits(false, false));
        ZoneNode corner = new ZoneNode("zone.living.corner", "Living Corner", "room.living", List.of(), List.of(), new ZoneTraits(false));
        ZoneNode worktop = new ZoneNode("zone.kitchen.worktop", "Kitchen Worktop", "room.kitchen", List.of("device.sensor.kitchen"), List.of(), new ZoneTraits(false));
        DeviceNode device = new DeviceNode(
            "device.sensor.kitchen",
            "alias",
            "Kitchen Sensor",
            "room.kitchen",
            "zone.kitchen.worktop",
            DeviceKind.SENSOR,
            DeviceProvider.TUYA,
            List.of("endpoint.sensor.kitchen-main"),
            List.of(),
            new DeviceTraits(false, false, false),
            new DeviceHealth(HealthStatus.UNKNOWN, Instant.parse("2026-05-09T12:00:00Z"), "ok"),
            new ProviderDeviceRef("tuya", "tuya.device.xyz", Map.of())
        );
        EndpointNode badEndpoint = new EndpointNode(
            "endpoint.sensor.kitchen-main",
            "device.sensor.kitchen",
            "kitchen-main",
            "endpoint.sensor.kitchen-main",
            EndpointKind.SENSOR,
            "room.living",
            "zone.living.corner",
            List.of(),
            new EndpointTraits(false, false, false, false, false, false),
            new EndpointHealth(HealthStatus.UNKNOWN, Instant.parse("2026-05-09T12:00:00Z"), "ok"),
            new ProviderEndpointRef("tuya", "tuya.device.xyz", "tuya.dp.1", Map.of()),
            EndpointMetadata.empty()
        );

        assertThatThrownBy(() -> service.createInitialTopology(
            "habitat-001",
            List.of(living, kitchen),
            List.of(corner, worktop),
            List.of(device),
            List.of(badEndpoint)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("split-placement deferred");
    }

    @Test
    void roomDeviceListBidirectionalMismatchRejected() {
        RoomNode living = new RoomNode("room.living", "Living", List.of(), List.of("device.sensor.kitchen"), List.of(), new RoomTraits(false, false));
        RoomNode kitchen = new RoomNode("room.kitchen", "Kitchen", List.of("zone.kitchen.worktop"), List.of(), List.of(), new RoomTraits(false, false));
        ZoneNode worktop = new ZoneNode("zone.kitchen.worktop", "Worktop", "room.kitchen", List.of("device.sensor.kitchen"), List.of(), new ZoneTraits(false));
        DeviceNode device = new DeviceNode(
            "device.sensor.kitchen",
            "alias",
            "Kitchen Sensor",
            "room.kitchen",
            "zone.kitchen.worktop",
            DeviceKind.SENSOR,
            DeviceProvider.TUYA,
            List.of(),
            List.of(),
            new DeviceTraits(false, false, false),
            new DeviceHealth(HealthStatus.UNKNOWN, Instant.parse("2026-05-09T12:00:00Z"), "ok"),
            new ProviderDeviceRef("tuya", "tuya.device.xyz", Map.of())
        );

        assertThatThrownBy(() -> service.createInitialTopology(
            "habitat-001",
            List.of(living, kitchen),
            List.of(worktop),
            List.of(device),
            List.of()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("device.roomId == room.roomId");
    }

    @Test
    void zoneEndpointListBidirectionalMismatchRejected() {
        RoomNode living = new RoomNode("room.living", "Living", List.of("zone.living.corner"), List.of(), List.of(), new RoomTraits(false, false));
        RoomNode kitchen = new RoomNode(
            "room.kitchen",
            "Kitchen",
            List.of("zone.kitchen.worktop"),
            List.of("device.light.kitchen"),
            List.of("endpoint.light.kitchen-main"),
            new RoomTraits(false, false)
        );
        ZoneNode corner = new ZoneNode("zone.living.corner", "Living Corner", "room.living", List.of(), List.of("endpoint.light.kitchen-main"), new ZoneTraits(false));
        ZoneNode worktop = new ZoneNode("zone.kitchen.worktop", "Kitchen Worktop", "room.kitchen", List.of("device.light.kitchen"), List.of(), new ZoneTraits(false));
        DeviceNode device = new DeviceNode(
            "device.light.kitchen",
            "alias",
            "Kitchen Light",
            "room.kitchen",
            "zone.kitchen.worktop",
            DeviceKind.LIGHT,
            DeviceProvider.TUYA,
            List.of("endpoint.light.kitchen-main"),
            List.of(),
            new DeviceTraits(false, false, true),
            new DeviceHealth(HealthStatus.UNKNOWN, Instant.parse("2026-05-09T12:00:00Z"), "ok"),
            new ProviderDeviceRef("tuya", "tuya.device.xyz", Map.of())
        );
        EndpointNode endpoint = new EndpointNode(
            "endpoint.light.kitchen-main",
            "device.light.kitchen",
            "kitchen-main",
            "endpoint.light.kitchen-main",
            EndpointKind.LIGHT,
            "room.kitchen",
            "zone.kitchen.worktop",
            List.of(),
            new EndpointTraits(true, true, true, true, false, false),
            new EndpointHealth(HealthStatus.UNKNOWN, Instant.parse("2026-05-09T12:00:00Z"), "ok"),
            new ProviderEndpointRef("tuya", "tuya.device.xyz", "tuya.dp.1", Map.of()),
            EndpointMetadata.empty()
        );

        assertThatThrownBy(() -> service.createInitialTopology(
            "habitat-001",
            List.of(living, kitchen),
            List.of(corner, worktop),
            List.of(device),
            List.of(endpoint)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endpoint.zoneId == zone.zoneId");
    }

    private RoomNode room() {
        return room(List.of("endpoint.light.kitchen-main", "endpoint.sensor.kitchen-motion"));
    }

    private RoomNode room(List<String> endpointIds) {
        return room(List.of("device.light.kitchen-main"), endpointIds);
    }

    private RoomNode room(List<String> deviceIds, List<String> endpointIds) {
        return new RoomNode(
            "room.kitchen",
            "Kitchen",
            List.of("zone.kitchen.worktop"),
            deviceIds,
            endpointIds,
            new RoomTraits(false, false)
        );
    }

    private ZoneNode zone() {
        return zone(List.of("endpoint.light.kitchen-main", "endpoint.sensor.kitchen-motion"));
    }

    private ZoneNode zone(List<String> endpointIds) {
        return zone(List.of("device.light.kitchen-main"), endpointIds);
    }

    private ZoneNode zone(List<String> deviceIds, List<String> endpointIds) {
        return new ZoneNode(
            "zone.kitchen.worktop",
            "Kitchen Worktop",
            "room.kitchen",
            deviceIds,
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
        return endpoint("endpoint.light.kitchen-main", "tuya.dp.1", List.of(binarySwitchCapability(), levelCapability()));
    }

    private EndpointNode motionEndpoint() {
        return endpoint("endpoint.sensor.kitchen-motion", "tuya.dp.7", List.of(readOnlyCapability()));
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
            new EndpointHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-09T11:59:00Z"), "online"),
            new ProviderEndpointRef("tuya", "tuya.device.abc", providerEndpointId, Map.of("dpCode", "switch_led")),
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

    private CapabilityNode readOnlyCapability() {
        return new CapabilityNode(
            "capability.motion",
            "Motion",
            CapabilityKind.READ_ONLY,
            new CapabilityTraits(true, false, false)
        );
    }
}
