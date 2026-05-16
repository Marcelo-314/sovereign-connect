package com.sovereign.connect.core.topology.materialization;

import com.sovereign.connect.core.topology.event.TopologyChanged;
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
import com.sovereign.connect.core.topology.model.ProviderSpatialRef;
import com.sovereign.connect.core.topology.model.RelationConfidence;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.RoomTraits;
import com.sovereign.connect.core.topology.model.SpatialRelationSource;
import com.sovereign.connect.core.topology.model.TopologyMutationResult;
import com.sovereign.connect.core.topology.model.TopologySpatialEntityType;
import com.sovereign.connect.core.topology.model.TopologySpatialRelation;
import com.sovereign.connect.core.topology.model.TopologySpatialRelationKind;
import com.sovereign.connect.core.topology.model.TopologySpatialSubject;
import com.sovereign.connect.core.topology.model.TopologySpatialTarget;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.port.MaterializationDecisionReplayPort;
import com.sovereign.connect.core.topology.port.TopologyMaterializationStatePort;
import com.sovereign.connect.core.topology.service.BaseTopologyService;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class DefaultTopologyMaterializationService implements TopologyMaterializationService {

    private final BaseTopologyService baseTopologyService;
    private final TopologyMaterializationStatePort statePort;
    private final Predicate<String> admittedAdapterPredicate;
    private final MaterializationDecisionReplayPort replayPort;
    private final Clock clock;

    public DefaultTopologyMaterializationService(
        BaseTopologyService baseTopologyService,
        TopologyMaterializationStatePort statePort,
        Predicate<String> admittedAdapterPredicate,
        MaterializationDecisionReplayPort replayPort,
        Clock clock
    ) {
        this.baseTopologyService = Objects.requireNonNull(baseTopologyService, "baseTopologyService is required");
        this.statePort = Objects.requireNonNull(statePort, "statePort is required");
        this.admittedAdapterPredicate = Objects.requireNonNull(admittedAdapterPredicate, "admittedAdapterPredicate is required");
        this.replayPort = Objects.requireNonNull(replayPort, "replayPort is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public MaterializationDecision materialize(String habitatId, TopologyFact fact) {
        Objects.requireNonNull(fact, "fact is required");
        Optional<MaterializationDecision> replayed = replayPort.findDecision(habitatId, fact.factId());
        if (replayed.isPresent()) {
            return replayed.get();
        }
        MaterializationDecision decision = switch (fact) {
            case DeviceDiscoveryFact f -> materializeDevice(habitatId, f);
            case EndpointDiscoveryFact f -> materializeEndpoint(habitatId, f);
            case CapabilityDiscoveryFact f -> materializeCapability(habitatId, f);
            case DeviceStateFact f -> materializeDeviceState(habitatId, f);
            case HealthFact f -> materializeHealth(habitatId, f);
            case RoomDiscoveryFact f -> materializeRoom(habitatId, f);
            case ZoneDiscoveryFact f -> materializeZone(habitatId, f);
        };
        replayPort.recordDecision(habitatId, fact.factId(), decision);
        return decision;
    }

    public String canonicalDeviceId(String providerId, String providerDeviceId) {
        return "device." + providerId + "." + providerDeviceId;
    }

    public String canonicalEndpointId(String providerId, String providerDeviceId, String providerEndpointId) {
        return "endpoint." + providerId + "." + providerDeviceId + "." + providerEndpointId;
    }

    public String canonicalCapabilityId(
        String providerId,
        String providerDeviceId,
        String providerEndpointId,
        String providerCapabilityKey
    ) {
        return "capability." + providerId + "." + providerDeviceId + "." + providerEndpointId + "." + providerCapabilityKey;
    }

    public String canonicalRoomId(String providerId, String roomNameHint) {
        return "room." + providerId + "." + canonicalToken(roomNameHint);
    }

    public String canonicalZoneId(String providerId, String targetRoomId, String zoneNameHint) {
        return "zone." + providerId + "." + canonicalToken(targetRoomId) + "." + canonicalToken(zoneNameHint);
    }

    private MaterializationDecision materializeDevice(String habitatId, DeviceDiscoveryFact fact) {
        Optional<HabitatBaseTopology> topology = admittedTopology(habitatId, fact);
        if (topology.isEmpty()) {
            return rejectedByPrecondition(habitatId, fact);
        }
        String deviceId = canonicalDeviceId(fact.providerId(), fact.providerDeviceId());
        if (topology.get().devices().stream().anyMatch(device -> device.deviceId().equals(deviceId))) {
            return rejectDuplicate(habitatId, fact);
        }

        String roomId = chooseRoomId(topology.get(), fact.roomHint());
        String zoneId = chooseZoneId(topology.get(), fact.zoneHint());
        DeviceNode device = new DeviceNode(
            deviceId,
            aliasFrom(deviceId),
            fallback(fact.displayNameHint(), deviceId),
            roomId,
            zoneId,
            deviceKind(fact.providerDeviceKind()),
            deviceProvider(fact.providerId()),
            List.of(),
            List.of(),
            new DeviceTraits(false, false, true),
            new DeviceHealth(HealthStatus.UNKNOWN, fact.observedAt(), "materialized from discovery fact"),
            new ProviderDeviceRef(fact.providerId(), fact.providerDeviceId(), stringMetadata(fact.rawProviderMetadata()))
        );

        int before = baseTopologyService.emittedEvents().size();
        TopologyMutationResult result = baseTopologyService.addDeviceWithResult(habitatId, device);
        TopologyMutationResult finalResult = addPlacementRelations(
            habitatId,
            deviceId,
            TopologySpatialEntityType.DEVICE,
            roomId,
            zoneId,
            fact
        );
        return acceptStructural(habitatId, fact, finalResult == null ? result : finalResult, eventDelta(before), "device materialized");
    }

    private MaterializationDecision materializeEndpoint(String habitatId, EndpointDiscoveryFact fact) {
        Optional<HabitatBaseTopology> topology = admittedTopology(habitatId, fact);
        if (topology.isEmpty()) {
            return rejectedByPrecondition(habitatId, fact);
        }
        String deviceId = canonicalDeviceId(fact.providerId(), fact.providerDeviceId());
        String endpointId = canonicalEndpointId(fact.providerId(), fact.providerDeviceId(), fact.providerEndpointId());
        Optional<DeviceNode> device = topology.get().devices().stream()
            .filter(candidate -> candidate.deviceId().equals(deviceId))
            .findFirst();
        if (device.isEmpty()) {
            return rejectInvalid(habitatId, fact, "parent device not found");
        }
        if (topology.get().endpoints().stream().anyMatch(endpoint -> endpoint.endpointId().equals(endpointId))) {
            return rejectDuplicate(habitatId, fact);
        }

        EndpointHealth initialHealth = new EndpointHealth(
            HealthStatus.UNKNOWN,
            fact.observedAt(),
            "materialized from discovery fact"
        );
        EndpointNode endpoint = new EndpointNode(
            endpointId,
            deviceId,
            aliasFrom(endpointId),
            endpointId,
            endpointKind(fact.providerEndpointKind()),
            device.get().roomId(),
            device.get().zoneId(),
            fact.capabilityHints().stream()
                .map(hint -> capabilityFromHint(fact.providerId(), fact.providerDeviceId(), fact.providerEndpointId(), hint))
                .toList(),
            new EndpointTraits(true, true, true, true, false, false),
            initialHealth,
            new ProviderEndpointRef(fact.providerId(), fact.providerDeviceId(), fact.providerEndpointId(), stringMetadata(fact.rawProviderMetadata())),
            EndpointMetadata.empty()
        );

        int before = baseTopologyService.emittedEvents().size();
        TopologyMutationResult result = baseTopologyService.addEndpointWithResult(habitatId, endpoint);
        TopologyMutationResult finalResult = addPlacementRelations(
            habitatId,
            endpointId,
            TopologySpatialEntityType.ENDPOINT,
            device.get().roomId(),
            device.get().zoneId(),
            fact
        );
        statePort.saveEndpointHealth(habitatId, endpointId, initialHealth);
        return acceptStructural(habitatId, fact, finalResult == null ? result : finalResult, eventDelta(before), "endpoint materialized");
    }

    private MaterializationDecision materializeRoom(String habitatId, RoomDiscoveryFact fact) {
        Optional<HabitatBaseTopology> topology = admittedTopology(habitatId, fact);
        if (topology.isEmpty()) {
            return rejectedByPrecondition(habitatId, fact);
        }
        String roomId = canonicalRoomId(fact.providerId(), fact.roomNameHint());
        if (topology.get().rooms().stream().anyMatch(room -> room.roomId().equals(roomId))) {
            return rejectDuplicate(habitatId, fact);
        }
        RoomNode room = new RoomNode(
            roomId,
            fallback(fact.roomNameHint(), roomId),
            List.of(),
            List.of(),
            List.of(),
            fact.traitsHint() == null ? new RoomTraits(false, false) : fact.traitsHint()
        );

        int before = baseTopologyService.emittedEvents().size();
        TopologyMutationResult result = baseTopologyService.addRoomWithResult(habitatId, room);
        return acceptStructural(habitatId, fact, result, eventDelta(before), "room materialized");
    }

    private MaterializationDecision materializeZone(String habitatId, ZoneDiscoveryFact fact) {
        Optional<HabitatBaseTopology> topology = admittedTopology(habitatId, fact);
        if (topology.isEmpty()) {
            return rejectedByPrecondition(habitatId, fact);
        }
        if (topology.get().rooms().stream().noneMatch(room -> room.roomId().equals(fact.targetRoomId()))) {
            return rejectInvalid(habitatId, fact, "target room not found");
        }
        String zoneId = canonicalZoneId(fact.providerId(), fact.targetRoomId(), fact.zoneNameHint());
        if (topology.get().zones().stream().anyMatch(zone -> zone.zoneId().equals(zoneId))) {
            return rejectDuplicate(habitatId, fact);
        }
        ZoneNode zone = new ZoneNode(
            zoneId,
            fallback(fact.zoneNameHint(), zoneId),
            fact.targetRoomId(),
            List.of(),
            List.of(),
            fact.traitsHint() == null ? new ZoneTraits(true) : fact.traitsHint()
        );

        int before = baseTopologyService.emittedEvents().size();
        TopologyMutationResult result = baseTopologyService.addZoneWithResult(habitatId, zone);
        return acceptStructural(habitatId, fact, result, eventDelta(before), "zone materialized");
    }

    private MaterializationDecision materializeCapability(String habitatId, CapabilityDiscoveryFact fact) {
        Optional<HabitatBaseTopology> topology = admittedTopology(habitatId, fact);
        if (topology.isEmpty()) {
            return rejectedByPrecondition(habitatId, fact);
        }
        String endpointId = canonicalEndpointId(fact.providerId(), fact.providerDeviceId(), fact.providerEndpointId());
        String capabilityId = canonicalCapabilityId(
            fact.providerId(),
            fact.providerDeviceId(),
            fact.providerEndpointId(),
            fact.providerCapabilityKey()
        );
        Optional<EndpointNode> endpoint = topology.get().endpoints().stream()
            .filter(candidate -> candidate.endpointId().equals(endpointId))
            .findFirst();
        if (endpoint.isEmpty()) {
            return rejectInvalid(habitatId, fact, "endpoint not found");
        }
        if (endpoint.get().capabilities().stream().anyMatch(capability -> capability.capabilityId().equals(capabilityId))) {
            return rejectDuplicate(habitatId, fact);
        }

        CapabilityNode capability = new CapabilityNode(
            capabilityId,
            fact.providerCapabilityKey(),
            capabilityKind(fact.capabilityKindHint()),
            new CapabilityTraits(true, true, false)
        );
        int before = baseTopologyService.emittedEvents().size();
        TopologyMutationResult result = baseTopologyService.addCapabilityWithResult(habitatId, endpointId, capability);
        return acceptStructural(habitatId, fact, result, eventDelta(before), "capability materialized");
    }

    private MaterializationDecision materializeDeviceState(String habitatId, DeviceStateFact fact) {
        Optional<HabitatBaseTopology> topology = admittedTopology(habitatId, fact);
        if (topology.isEmpty()) {
            return rejectedByPrecondition(habitatId, fact);
        }
        String deviceId = canonicalDeviceId(fact.providerId(), fact.providerDeviceId());
        if (topology.get().devices().stream().noneMatch(device -> device.deviceId().equals(deviceId))) {
            return rejectInvalid(habitatId, fact, "device not found");
        }
        TopologyVersion version = topology.get().topologyVersion();
        statePort.saveDeviceState(habitatId, deviceId, fact.statePayload());
        return decision(
            habitatId,
            fact,
            MaterializationDecisionKind.ACCEPT_NON_STRUCTURAL_STATE,
            Optional.of(version),
            Optional.of(version),
            List.of(),
            "device state materialized"
        );
    }

    private MaterializationDecision materializeHealth(String habitatId, HealthFact fact) {
        Optional<HabitatBaseTopology> topology = admittedTopology(habitatId, fact);
        if (topology.isEmpty()) {
            return rejectedByPrecondition(habitatId, fact);
        }
        if (fact.providerEndpointId() == null || fact.providerEndpointId().isBlank()) {
            return rejectInvalid(habitatId, fact, "device-level health persistence not available in seed");
        }
        String endpointId = canonicalEndpointId(fact.providerId(), fact.providerDeviceId(), fact.providerEndpointId());
        if (topology.get().endpoints().stream().noneMatch(endpoint -> endpoint.endpointId().equals(endpointId))) {
            return rejectInvalid(habitatId, fact, "endpoint not found");
        }
        TopologyVersion version = topology.get().topologyVersion();
        statePort.saveEndpointHealth(
            habitatId,
            endpointId,
            new EndpointHealth(fact.healthStatus(), fact.observedAt(), fact.healthReason())
        );
        return decision(
            habitatId,
            fact,
            MaterializationDecisionKind.ACCEPT_HEALTH_UPDATE,
            Optional.of(version),
            Optional.of(version),
            List.of(),
            "endpoint health materialized"
        );
    }

    private Optional<HabitatBaseTopology> admittedTopology(String habitatId, TopologyFact fact) {
        if (!admittedAdapterPredicate.test(fact.adapterInstanceId())) {
            return Optional.empty();
        }
        return statePort.findByHabitatId(habitatId);
    }

    private MaterializationDecision rejectedByPrecondition(String habitatId, TopologyFact fact) {
        if (!admittedAdapterPredicate.test(fact.adapterInstanceId())) {
            return decision(
                habitatId,
                fact,
                MaterializationDecisionKind.REJECT_UNAUTHORIZED_ADAPTER,
                statePort.findCurrentVersion(habitatId),
                statePort.findCurrentVersion(habitatId),
                List.of(),
                "adapter not admitted"
            );
        }
        return rejectInvalid(habitatId, fact, "habitat not initialized");
    }

    private MaterializationDecision rejectDuplicate(String habitatId, TopologyFact fact) {
        Optional<TopologyVersion> version = statePort.findCurrentVersion(habitatId);
        return decision(
            habitatId,
            fact,
            MaterializationDecisionKind.REJECT_DUPLICATE,
            version,
            version,
            List.of(),
            "duplicate fact"
        );
    }

    private MaterializationDecision rejectInvalid(String habitatId, TopologyFact fact, String reason) {
        Optional<TopologyVersion> version = statePort.findCurrentVersion(habitatId);
        return decision(
            habitatId,
            fact,
            MaterializationDecisionKind.REJECT_INVALID_FACT,
            version,
            version,
            List.of(),
            reason
        );
    }

    private MaterializationDecision acceptStructural(
        String habitatId,
        TopologyFact fact,
        TopologyMutationResult result,
        List<TopologyChanged> emittedChanges,
        String reason
    ) {
        return decision(
            habitatId,
            fact,
            MaterializationDecisionKind.ACCEPT_STRUCTURAL_MUTATION,
            Optional.of(result.fromVersion()),
            Optional.of(result.toVersion()),
            emittedChanges,
            reason
        );
    }

    private MaterializationDecision decision(
        String habitatId,
        TopologyFact fact,
        MaterializationDecisionKind kind,
        Optional<TopologyVersion> previousVersion,
        Optional<TopologyVersion> resultingVersion,
        List<TopologyChanged> emittedChanges,
        String reason
    ) {
        return new MaterializationDecision(
            UUID.randomUUID(),
            fact.factId(),
            habitatId,
            kind,
            previousVersion,
            resultingVersion,
            emittedChanges,
            reason
        );
    }

    private List<TopologyChanged> eventDelta(int before) {
        List<TopologyChanged> events = baseTopologyService.emittedEvents();
        return List.copyOf(events.subList(before, events.size()));
    }

    private TopologyMutationResult addPlacementRelations(
        String habitatId,
        String subjectId,
        TopologySpatialEntityType subjectType,
        String roomId,
        String zoneId,
        TopologyFact fact
    ) {
        TopologyMutationResult result = baseTopologyService.addSpatialRelationWithResult(
            habitatId,
            buildLocatedIn(subjectId, subjectType, roomId, TopologySpatialEntityType.ROOM, fact)
        );
        if (zoneId != null && !zoneId.isBlank()) {
            result = baseTopologyService.addSpatialRelationWithResult(
                habitatId,
                buildLocatedIn(subjectId, subjectType, zoneId, TopologySpatialEntityType.ZONE, fact)
            );
        }
        return result;
    }

    private TopologySpatialRelation buildLocatedIn(
        String subjectId,
        TopologySpatialEntityType subjectType,
        String targetId,
        TopologySpatialEntityType targetType,
        TopologyFact fact
    ) {
        boolean providerPlacement = hasProviderPlacementHint(fact, targetType);
        return new TopologySpatialRelation(
            "relation.located-in." + subjectType.name().toLowerCase() + "." + subjectId + "."
                + targetType.name().toLowerCase() + "." + targetId,
            TopologySpatialRelationKind.LOCATED_IN,
            new TopologySpatialSubject(subjectType, subjectId),
            new TopologySpatialTarget(targetType, targetId),
            true,
            providerPlacement ? RelationConfidence.PROVIDER_REPORTED : RelationConfidence.INFERRED,
            providerPlacement ? SpatialRelationSource.PROVIDER : SpatialRelationSource.INFERENCE,
            spatialProviderRef(fact),
            fact.observedAt(),
            Map.of("materializedBy", "SC-C")
        );
    }

    private boolean hasProviderPlacementHint(TopologyFact fact, TopologySpatialEntityType targetType) {
        if (fact instanceof DeviceDiscoveryFact deviceFact) {
            return targetType == TopologySpatialEntityType.ROOM
                ? deviceFact.roomHint() != null && !deviceFact.roomHint().isBlank()
                : deviceFact.zoneHint() != null && !deviceFact.zoneHint().isBlank();
        }
        return false;
    }

    private ProviderSpatialRef spatialProviderRef(TopologyFact fact) {
        return new ProviderSpatialRef(fact.providerId(), null, null, null, Map.of());
    }

    private String chooseRoomId(HabitatBaseTopology topology, String roomHint) {
        if (roomHint != null && topology.rooms().stream().anyMatch(room -> room.roomId().equals(roomHint))) {
            return roomHint;
        }
        return topology.rooms().stream().findFirst().map(RoomNode::roomId)
            .orElseThrow(() -> new IllegalArgumentException("habitat must contain at least one room"));
    }

    private String chooseZoneId(HabitatBaseTopology topology, String zoneHint) {
        if (zoneHint != null && topology.zones().stream().anyMatch(zone -> zone.zoneId().equals(zoneHint))) {
            return zoneHint;
        }
        return topology.zones().stream().findFirst().map(ZoneNode::zoneId)
            .orElseThrow(() -> new IllegalArgumentException("habitat must contain at least one zone"));
    }

    private CapabilityNode capabilityFromHint(String providerId, String providerDeviceId, String providerEndpointId, String hint) {
        String safeHint = fallback(hint, "unknown");
        return new CapabilityNode(
            canonicalCapabilityId(providerId, providerDeviceId, providerEndpointId, safeHint),
            safeHint,
            capabilityKind(safeHint),
            new CapabilityTraits(true, true, false)
        );
    }

    private DeviceKind deviceKind(String hint) {
        return enumValue(DeviceKind.class, hint, DeviceKind.OTHER);
    }

    private DeviceProvider deviceProvider(String providerId) {
        return enumValue(DeviceProvider.class, providerId, DeviceProvider.OTHER);
    }

    private EndpointKind endpointKind(String hint) {
        return enumValue(EndpointKind.class, hint, EndpointKind.OTHER);
    }

    private CapabilityKind capabilityKind(String hint) {
        return enumValue(CapabilityKind.class, hint, CapabilityKind.OTHER);
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private Map<String, String> stringMetadata(Map<String, Object> metadata) {
        Map<String, String> converted = new LinkedHashMap<>();
        metadata.forEach((key, value) -> converted.put(key, Objects.toString(value, "")));
        return converted;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String aliasFrom(String id) {
        int lastDot = id.lastIndexOf('.');
        return lastDot >= 0 ? id.substring(lastDot + 1) : id;
    }

    private String canonicalToken(String value) {
        String token = fallback(value, "unknown").trim().toLowerCase().replaceAll("[^a-z0-9]+", ".");
        token = token.replaceAll("^\\.+|\\.+$", "");
        return token.isBlank() ? "unknown" : token;
    }
}
