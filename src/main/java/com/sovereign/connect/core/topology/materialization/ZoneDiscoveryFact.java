package com.sovereign.connect.core.topology.materialization;

import com.sovereign.connect.core.topology.model.ZoneTraits;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ZoneDiscoveryFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String targetRoomId,
    String zoneNameHint,
    ZoneTraits traitsHint,
    Instant observedAt,
    double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact {

    public ZoneDiscoveryFact {
        Objects.requireNonNull(factId, "factId is required");
        Objects.requireNonNull(adapterInstanceId, "adapterInstanceId is required");
        Objects.requireNonNull(providerId, "providerId is required");
        Objects.requireNonNull(targetRoomId, "targetRoomId is required");
        Objects.requireNonNull(observedAt, "observedAt is required");
        rawProviderMetadata = Map.copyOf(Objects.requireNonNull(rawProviderMetadata, "rawProviderMetadata is required"));
    }
}
