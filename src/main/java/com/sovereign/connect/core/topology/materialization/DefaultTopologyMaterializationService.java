package com.sovereign.connect.core.topology.materialization;

import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
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
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.TopologyMutationResult;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
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
    private final H2BaseTopologyRepository repository;
    private final Predicate<String> admittedAdapterPredicate;
    private final Clock clock;

    public DefaultTopologyMaterializationService(
        BaseTopologyService baseTopologyService,
        H2BaseTopologyRepository repository,
        Predicate<String> admittedAdapterPredicate,
        Clock clock
    ) {
        this.baseTopologyService = Objects.requireNonNull(baseTopologyService, "baseTopologyService is required");
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.admittedAdapterPredicate = Objects.requireNonNull(admittedAdapterPredicate, "admittedAdapterPredicate is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public MaterializationDecision materialize(String habitatId, TopologyFact fact) {
        Objects.requireNonNull(fact, "fact is required");
        return switch (fact) {
            case DeviceDiscoveryFact f -> materializeDevice(habitatId, f);
            case EndpointDiscoveryFact f -> materializeEndpoint(habitatId, f);
            case CapabilityDiscoveryFact f -> materializeCapability(habitatId, f);
            case DeviceStateFact f -> materializeDeviceState(habitatId, f);
            case HealthFact f -> materializeHealth(habitatId, f);
        };
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
        return acceptStructural(habitatId, fact, result, eventDelta(before), "device materialized");
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
        repository.saveEndpointHealth(habitatId, endpointId, initialHealth);
        return acceptStructural(habitatId, fact, result, eventDelta(before), "endpoint materialized");
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
        repository.saveDeviceState(habitatId, deviceId, fact.statePayload());
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
        repository.saveEndpointHealth(
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
        return repository.findByHabitatId(habitatId);
    }

    private MaterializationDecision rejectedByPrecondition(String habitatId, TopologyFact fact) {
        if (!admittedAdapterPredicate.test(fact.adapterInstanceId())) {
            return decision(
                habitatId,
                fact,
                MaterializationDecisionKind.REJECT_UNAUTHORIZED_ADAPTER,
                repository.findCurrentVersion(habitatId),
                repository.findCurrentVersion(habitatId),
                List.of(),
                "adapter not admitted"
            );
        }
        return rejectInvalid(habitatId, fact, "habitat not initialized");
    }

    private MaterializationDecision rejectDuplicate(String habitatId, TopologyFact fact) {
        Optional<TopologyVersion> version = repository.findCurrentVersion(habitatId);
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
        Optional<TopologyVersion> version = repository.findCurrentVersion(habitatId);
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
}
