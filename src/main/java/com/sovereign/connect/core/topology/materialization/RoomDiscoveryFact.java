package com.sovereign.connect.core.topology.materialization;

import com.sovereign.connect.core.topology.model.RoomTraits;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RoomDiscoveryFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String roomNameHint,
    RoomTraits traitsHint,
    Instant observedAt,
    double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact {

    public RoomDiscoveryFact {
        Objects.requireNonNull(factId, "factId is required");
        Objects.requireNonNull(adapterInstanceId, "adapterInstanceId is required");
        Objects.requireNonNull(providerId, "providerId is required");
        Objects.requireNonNull(observedAt, "observedAt is required");
        rawProviderMetadata = Map.copyOf(Objects.requireNonNull(rawProviderMetadata, "rawProviderMetadata is required"));
    }
}
