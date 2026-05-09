package com.sovereign.connect.core.topology.service;

import com.sovereign.connect.core.topology.event.TopologyChangeKind;
import com.sovereign.connect.core.topology.event.TopologyChanged;
import com.sovereign.connect.core.topology.model.CapabilityNode;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.TopologyMetadata;
import com.sovereign.connect.core.topology.model.TopologyNode;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class BaseTopologyService {

    private static final String SCHEMA_VERSION = "base-topology.seed.v1";
    private static final String SOURCE = "SC-C";

    private final BaseTopologyRepository repository;
    private final Clock clock;
    private final List<TopologyChanged> emittedEvents = new ArrayList<>();

    public BaseTopologyService(BaseTopologyRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public BaseTopologyService(BaseTopologyRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public HabitatBaseTopology createInitialTopology(
        String habitatId,
        List<RoomNode> rooms,
        List<ZoneNode> zones,
        List<DeviceNode> devices,
        List<EndpointNode> endpoints
    ) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        if (repository.findByHabitatId(habitatId).isPresent()) {
            throw new IllegalStateException("base topology already exists for habitatId " + habitatId);
        }
        HabitatBaseTopology topology = new HabitatBaseTopology(
            habitatId,
            TopologyVersion.initialForHabitat(habitatId),
            rooms,
            zones,
            devices,
            endpoints,
            metadataNow()
        );
        validateTopology(topology);
        repository.save(topology);
        return topology;
    }

    public HabitatBaseTopology addEndpoint(String habitatId, EndpointNode endpoint) {
        Objects.requireNonNull(endpoint, "endpoint is required");
        HabitatBaseTopology current = repository.findByHabitatId(habitatId)
            .orElseThrow(() -> new IllegalArgumentException("base topology does not exist for habitatId " + habitatId));
        if (current.endpoints().stream().anyMatch(existing -> existing.endpointId().equals(endpoint.endpointId()))) {
            throw new IllegalArgumentException("endpointId already exists in habitat topology: " + endpoint.endpointId());
        }
        if (current.devices().stream().noneMatch(device -> device.deviceId().equals(endpoint.deviceId()))) {
            throw new IllegalArgumentException("endpoint must belong to an existing deviceId: " + endpoint.deviceId());
        }
        if (current.rooms().stream().noneMatch(room -> room.roomId().equals(endpoint.roomId()))) {
            throw new IllegalArgumentException("endpoint must belong to an existing roomId: " + endpoint.roomId());
        }
        if (current.zones().stream().noneMatch(zone -> zone.zoneId().equals(endpoint.zoneId()))) {
            throw new IllegalArgumentException("endpoint must belong to an existing zoneId: " + endpoint.zoneId());
        }

        List<RoomNode> rooms = current.rooms().stream()
            .map(room -> room.roomId().equals(endpoint.roomId()) ? appendEndpoint(room, endpoint.endpointId()) : room)
            .toList();
        List<ZoneNode> zones = current.zones().stream()
            .map(zone -> zone.zoneId().equals(endpoint.zoneId()) ? appendEndpoint(zone, endpoint.endpointId()) : zone)
            .toList();
        List<DeviceNode> devices = current.devices().stream()
            .map(device -> device.deviceId().equals(endpoint.deviceId()) ? appendEndpoint(device, endpoint.endpointId()) : device)
            .toList();
        List<EndpointNode> endpoints = new ArrayList<>(current.endpoints());
        endpoints.add(endpoint);
        HabitatBaseTopology mutated = withVersionAndMetadata(
            new HabitatBaseTopology(
                current.habitatId(),
                current.topologyVersion(),
                rooms,
                zones,
                devices,
                endpoints,
                current.metadata()
            )
        );
        validateTopology(mutated);
        repository.save(mutated);
        emit(current, mutated, Set.of(TopologyChangeKind.ENDPOINT_ADDED), List.of(endpoint.deviceId()), List.of(endpoint.endpointId()), "endpoint added");
        return mutated;
    }

    public Optional<TopologyVersion> findCurrentVersion(String habitatId) {
        return repository.findCurrentVersion(habitatId);
    }

    public List<TopologyChanged> emittedEvents() {
        return List.copyOf(emittedEvents);
    }

    private HabitatBaseTopology withVersionAndMetadata(HabitatBaseTopology topology) {
        return new HabitatBaseTopology(
            topology.habitatId(),
            topology.topologyVersion().next(),
            topology.rooms(),
            topology.zones(),
            topology.devices(),
            topology.endpoints(),
            metadataNow()
        );
    }

    private void emit(
        HabitatBaseTopology from,
        HabitatBaseTopology to,
        Set<TopologyChangeKind> changeKinds,
        List<String> deviceIds,
        List<String> endpointIds,
        String reason
    ) {
        emittedEvents.add(new TopologyChanged(
            UUID.randomUUID(),
            Instant.now(clock),
            to.habitatId(),
            from.topologyVersion().value(),
            to.topologyVersion().value(),
            changeKinds,
            deviceIds,
            endpointIds,
            reason
        ));
    }

    private TopologyMetadata metadataNow() {
        return new TopologyMetadata(SCHEMA_VERSION, Instant.now(clock), SOURCE, null);
    }

    private RoomNode appendEndpoint(RoomNode room, String endpointId) {
        List<String> endpointIds = appendIfMissing(room.endpointIds(), endpointId);
        return new RoomNode(room.roomId(), room.roomName(), room.zoneIds(), room.deviceIds(), endpointIds, room.traits());
    }

    private ZoneNode appendEndpoint(ZoneNode zone, String endpointId) {
        List<String> endpointIds = appendIfMissing(zone.endpointIds(), endpointId);
        return new ZoneNode(zone.zoneId(), zone.zoneName(), zone.roomId(), zone.deviceIds(), endpointIds, zone.traits());
    }

    private DeviceNode appendEndpoint(DeviceNode device, String endpointId) {
        List<String> endpointIds = appendIfMissing(device.endpointIds(), endpointId);
        return new DeviceNode(
            device.deviceId(),
            device.alias(),
            device.displayName(),
            device.roomId(),
            device.zoneId(),
            device.kind(),
            device.provider(),
            endpointIds,
            device.deviceCapabilities(),
            device.traits(),
            device.health(),
            device.providerRef()
        );
    }

    private List<String> appendIfMissing(List<String> values, String value) {
        if (values.contains(value)) {
            return values;
        }
        List<String> appended = new ArrayList<>(values);
        appended.add(value);
        return appended;
    }

    private void validateTopology(HabitatBaseTopology topology) {
        validateUnique("roomId", topology.rooms().stream().map(RoomNode::roomId).toList());
        validateUnique("zoneId", topology.zones().stream().map(ZoneNode::zoneId).toList());
        validateUnique("deviceId", topology.devices().stream().map(DeviceNode::deviceId).toList());
        validateUnique("endpointId", topology.endpoints().stream().map(EndpointNode::endpointId).toList());

        Set<String> deviceIds = new HashSet<>(topology.devices().stream().map(DeviceNode::deviceId).toList());
        Set<String> endpointIds = new HashSet<>(topology.endpoints().stream().map(EndpointNode::endpointId).toList());
        Set<String> roomIds = new HashSet<>(topology.rooms().stream().map(RoomNode::roomId).toList());
        Set<String> zoneIds = new HashSet<>(topology.zones().stream().map(ZoneNode::zoneId).toList());

        for (RoomNode room : topology.rooms()) {
            ensureCanonicalId(room);
            if (!zoneIds.containsAll(room.zoneIds())) {
                throw new IllegalArgumentException("room zoneIds must refer to ZoneNode zoneId values");
            }
            if (!deviceIds.containsAll(room.deviceIds())) {
                throw new IllegalArgumentException("room deviceIds must refer to DeviceNode deviceId values");
            }
            if (!endpointIds.containsAll(room.endpointIds())) {
                throw new IllegalArgumentException("room endpointIds must refer to EndpointNode endpointId values");
            }
        }

        for (ZoneNode zone : topology.zones()) {
            ensureCanonicalId(zone);
            if (!roomIds.contains(zone.roomId())) {
                throw new IllegalArgumentException("zone roomId must refer to a RoomNode");
            }
            if (!deviceIds.containsAll(zone.deviceIds())) {
                throw new IllegalArgumentException("zone deviceIds must refer to DeviceNode deviceId values");
            }
            if (!endpointIds.containsAll(zone.endpointIds())) {
                throw new IllegalArgumentException("zone endpointIds must refer to EndpointNode endpointId values");
            }
        }

        for (DeviceNode device : topology.devices()) {
            ensureCanonicalId(device);
            if (!roomIds.contains(device.roomId())) {
                throw new IllegalArgumentException("device roomId must refer to a RoomNode");
            }
            if (!zoneIds.contains(device.zoneId())) {
                throw new IllegalArgumentException("device zoneId must refer to a ZoneNode");
            }
            if (device.deviceId().equals(device.providerRef().providerDeviceId())) {
                throw new IllegalArgumentException("providerDeviceId must not be used as canonical deviceId");
            }
            validateUnique("device capabilityId", device.deviceCapabilities().stream().map(CapabilityNode::capabilityId).toList());
            if (!endpointIds.containsAll(device.endpointIds())) {
                throw new IllegalArgumentException("device endpointIds must refer to EndpointNode endpointId values");
            }
        }

        for (EndpointNode endpoint : topology.endpoints()) {
            ensureCanonicalId(endpoint);
            if (!deviceIds.contains(endpoint.deviceId())) {
                throw new IllegalArgumentException("endpoint deviceId must refer to a DeviceNode");
            }
            if (!roomIds.contains(endpoint.roomId())) {
                throw new IllegalArgumentException("endpoint roomId must refer to a RoomNode");
            }
            if (!zoneIds.contains(endpoint.zoneId())) {
                throw new IllegalArgumentException("endpoint zoneId must refer to a ZoneNode");
            }
            validateUnique("endpoint capabilityId", endpoint.capabilities().stream().map(CapabilityNode::capabilityId).toList());
        }
    }

    private void ensureCanonicalId(TopologyNode node) {
        if (node.canonicalId().isBlank()) {
            throw new IllegalArgumentException("canonical topology identity must not be blank");
        }
    }

    private void validateUnique(String fieldName, List<String> values) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException(fieldName + " must be unique: " + value);
            }
        }
    }
}
