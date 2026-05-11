package com.sovereign.connect.core.topology.service;

import com.sovereign.connect.core.topology.event.TopologyChangeKind;
import com.sovereign.connect.core.topology.event.TopologyChanged;
import com.sovereign.connect.core.topology.model.CapabilityNode;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.TargetValidationResult;
import com.sovereign.connect.core.topology.model.TopologyMetadata;
import com.sovereign.connect.core.topology.model.TopologyMutationResult;
import com.sovereign.connect.core.topology.model.TopologyNode;
import com.sovereign.connect.core.topology.model.TopologyTargetRef;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BaseTopologyService {

    private static final String SCHEMA_VERSION = "base-topology.seed.v1";
    private static final String SOURCE = "SC-C";

    private final BaseTopologyRepository repository;
    private final Clock clock;
    private final List<TopologyChanged> emittedEvents = new ArrayList<>();
    private final ConcurrentMap<String, Map<String, Object>> deviceStates = new ConcurrentHashMap<>();

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

    public TopologyMutationResult addEndpointWithResult(String habitatId, EndpointNode endpoint) {
        TopologyVersion fromVersion = repository.findCurrentVersion(habitatId)
            .orElseThrow(() -> new IllegalArgumentException("base topology does not exist for habitatId " + habitatId));
        HabitatBaseTopology mutated = addEndpoint(habitatId, endpoint);
        return new TopologyMutationResult(
            habitatId,
            fromVersion,
            mutated.topologyVersion(),
            Set.of(TopologyChangeKind.ENDPOINT_ADDED),
            List.of(endpoint.deviceId()),
            List.of(endpoint.endpointId())
        );
    }

    public TopologyMutationResult addDeviceWithResult(String habitatId, DeviceNode device) {
        Objects.requireNonNull(device, "device is required");
        HabitatBaseTopology current = repository.findByHabitatId(habitatId)
            .orElseThrow(() -> new IllegalArgumentException("base topology does not exist for habitatId " + habitatId));
        if (current.devices().stream().anyMatch(existing -> existing.deviceId().equals(device.deviceId()))) {
            throw new IllegalArgumentException("deviceId already exists in habitat topology: " + device.deviceId());
        }

        List<RoomNode> rooms = current.rooms().stream()
            .map(room -> room.roomId().equals(device.roomId()) ? appendDevice(room, device.deviceId()) : room)
            .toList();
        List<ZoneNode> zones = current.zones().stream()
            .map(zone -> zone.zoneId().equals(device.zoneId()) ? appendDevice(zone, device.deviceId()) : zone)
            .toList();
        List<DeviceNode> devices = new ArrayList<>(current.devices());
        devices.add(device);
        HabitatBaseTopology mutated = withVersionAndMetadata(new HabitatBaseTopology(
            current.habitatId(),
            current.topologyVersion(),
            rooms,
            zones,
            devices,
            current.endpoints(),
            current.metadata()
        ));
        validateTopology(mutated);
        repository.save(mutated);
        emit(
            current,
            mutated,
            Set.of(TopologyChangeKind.DEVICE_ADDED),
            List.of(device.deviceId()),
            List.of(),
            "device added via materialization"
        );

        return new TopologyMutationResult(
            habitatId,
            current.topologyVersion(),
            mutated.topologyVersion(),
            Set.of(TopologyChangeKind.DEVICE_ADDED),
            List.of(device.deviceId()),
            List.of()
        );
    }

    public TopologyMutationResult addCapabilityWithResult(String habitatId, String endpointId, CapabilityNode capability) {
        Objects.requireNonNull(endpointId, "endpointId is required");
        Objects.requireNonNull(capability, "capability is required");
        HabitatBaseTopology current = repository.findByHabitatId(habitatId)
            .orElseThrow(() -> new IllegalArgumentException("base topology does not exist for habitatId " + habitatId));
        EndpointNode currentEndpoint = current.endpoints().stream()
            .filter(endpoint -> endpoint.endpointId().equals(endpointId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("endpointId does not exist in habitat topology: " + endpointId));
        if (currentEndpoint.capabilities().stream()
            .anyMatch(existing -> existing.capabilityId().equals(capability.capabilityId()))) {
            throw new IllegalArgumentException("capabilityId already exists in endpoint topology: " + capability.capabilityId());
        }

        List<CapabilityNode> capabilities = new ArrayList<>(currentEndpoint.capabilities());
        capabilities.add(capability);
        EndpointNode mutatedEndpoint = new EndpointNode(
            currentEndpoint.endpointId(),
            currentEndpoint.deviceId(),
            currentEndpoint.alias(),
            currentEndpoint.displayName(),
            currentEndpoint.kind(),
            currentEndpoint.roomId(),
            currentEndpoint.zoneId(),
            capabilities,
            currentEndpoint.traits(),
            currentEndpoint.health(),
            currentEndpoint.providerRef(),
            currentEndpoint.metadata()
        );
        List<EndpointNode> endpoints = current.endpoints().stream()
            .map(endpoint -> endpoint.endpointId().equals(endpointId) ? mutatedEndpoint : endpoint)
            .toList();
        HabitatBaseTopology mutated = withVersionAndMetadata(new HabitatBaseTopology(
            current.habitatId(),
            current.topologyVersion(),
            current.rooms(),
            current.zones(),
            current.devices(),
            endpoints,
            current.metadata()
        ));
        validateTopology(mutated);
        repository.save(mutated);
        emit(
            current,
            mutated,
            Set.of(TopologyChangeKind.CAPABILITY_ADDED),
            List.of(currentEndpoint.deviceId()),
            List.of(endpointId),
            "capability added via materialization"
        );

        return new TopologyMutationResult(
            habitatId,
            current.topologyVersion(),
            mutated.topologyVersion(),
            Set.of(TopologyChangeKind.CAPABILITY_ADDED),
            List.of(currentEndpoint.deviceId()),
            List.of(endpointId)
        );
    }

    public void updateEndpointHealth(String habitatId, String endpointId, HealthStatus status) {
        Objects.requireNonNull(status, "status is required");
        HabitatBaseTopology current = repository.findByHabitatId(habitatId)
            .orElseThrow(() -> new IllegalArgumentException("base topology does not exist for habitatId " + habitatId));
        if (current.endpoints().stream().noneMatch(endpoint -> endpoint.endpointId().equals(endpointId))) {
            throw new IllegalArgumentException("endpointId does not exist in habitat topology: " + endpointId);
        }

        List<EndpointNode> endpoints = current.endpoints().stream()
            .map(endpoint -> endpoint.endpointId().equals(endpointId) ? withHealth(endpoint, status) : endpoint)
            .toList();
        repository.save(new HabitatBaseTopology(
            current.habitatId(),
            current.topologyVersion(),
            current.rooms(),
            current.zones(),
            current.devices(),
            endpoints,
            current.metadata()
        ));
    }

    public void updateDeviceState(String habitatId, String deviceId, Map<String, Object> state) {
        Objects.requireNonNull(state, "state is required");
        HabitatBaseTopology current = repository.findByHabitatId(habitatId)
            .orElseThrow(() -> new IllegalArgumentException("base topology does not exist for habitatId " + habitatId));
        if (current.devices().stream().noneMatch(device -> device.deviceId().equals(deviceId))) {
            throw new IllegalArgumentException("deviceId does not exist in habitat topology: " + deviceId);
        }
        deviceStates.put(deviceStateKey(habitatId, deviceId), Map.copyOf(state));
    }

    public Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId) {
        return Optional.ofNullable(deviceStates.get(deviceStateKey(habitatId, deviceId)));
    }

    public TargetValidationResult validateTarget(
        String habitatId,
        TopologyTargetRef target,
        TopologyVersion requestTopologyVersion
    ) {
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(requestTopologyVersion, "requestTopologyVersion is required");
        HabitatBaseTopology current = repository.findByHabitatId(habitatId)
            .orElseThrow(() -> new IllegalArgumentException("base topology does not exist for habitatId " + habitatId));
        if (!requestTopologyVersion.isScopedToHabitat(habitatId)) {
            return TargetValidationResult.TOPOLOGY_VERSION_CONFLICT;
        }

        TargetValidationResult targetResult = validateCurrentTarget(current, target);
        boolean sameVersion = current.topologyVersion().equals(requestTopologyVersion);
        if (sameVersion) {
            return targetResult == TargetValidationResult.VALID
                ? TargetValidationResult.VALID
                : targetResult;
        }
        return targetResult == TargetValidationResult.VALID
            ? TargetValidationResult.VALID_AFTER_REVALIDATION
            : targetResult;
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

    private EndpointNode withHealth(EndpointNode endpoint, HealthStatus status) {
        EndpointHealth currentHealth = endpoint.health();
        EndpointHealth health = new EndpointHealth(status, Instant.now(clock), currentHealth.details());
        return new EndpointNode(
            endpoint.endpointId(),
            endpoint.deviceId(),
            endpoint.alias(),
            endpoint.displayName(),
            endpoint.kind(),
            endpoint.roomId(),
            endpoint.zoneId(),
            endpoint.capabilities(),
            endpoint.traits(),
            health,
            endpoint.providerRef(),
            endpoint.metadata()
        );
    }

    private TargetValidationResult validateCurrentTarget(HabitatBaseTopology topology, TopologyTargetRef target) {
        Optional<DeviceNode> device = topology.devices().stream()
            .filter(candidate -> candidate.deviceId().equals(target.deviceId()))
            .findFirst();
        if (device.isEmpty()) {
            return TargetValidationResult.TARGET_NOT_FOUND;
        }
        Optional<EndpointNode> endpoint = topology.endpoints().stream()
            .filter(candidate -> candidate.endpointId().equals(target.endpointId()))
            .filter(candidate -> candidate.deviceId().equals(target.deviceId()))
            .findFirst();
        if (endpoint.isEmpty() || !device.get().endpointIds().contains(target.endpointId())) {
            return TargetValidationResult.TARGET_NOT_FOUND;
        }
        boolean capabilityExists = endpoint.get().capabilities().stream()
            .anyMatch(capability -> capability.capabilityId().equals(target.capabilityId()));
        return capabilityExists ? TargetValidationResult.VALID : TargetValidationResult.CAPABILITY_MISMATCH;
    }

    private String deviceStateKey(String habitatId, String deviceId) {
        return habitatId + ":" + deviceId;
    }

    private RoomNode appendEndpoint(RoomNode room, String endpointId) {
        List<String> endpointIds = appendIfMissing(room.endpointIds(), endpointId);
        return new RoomNode(room.roomId(), room.roomName(), room.zoneIds(), room.deviceIds(), endpointIds, room.traits());
    }

    private ZoneNode appendEndpoint(ZoneNode zone, String endpointId) {
        List<String> endpointIds = appendIfMissing(zone.endpointIds(), endpointId);
        return new ZoneNode(zone.zoneId(), zone.zoneName(), zone.roomId(), zone.deviceIds(), endpointIds, zone.traits());
    }

    private RoomNode appendDevice(RoomNode room, String deviceId) {
        List<String> deviceIds = appendIfMissing(room.deviceIds(), deviceId);
        return new RoomNode(room.roomId(), room.roomName(), room.zoneIds(), deviceIds, room.endpointIds(), room.traits());
    }

    private ZoneNode appendDevice(ZoneNode zone, String deviceId) {
        List<String> deviceIds = appendIfMissing(zone.deviceIds(), deviceId);
        return new ZoneNode(zone.zoneId(), zone.zoneName(), zone.roomId(), deviceIds, zone.endpointIds(), zone.traits());
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
