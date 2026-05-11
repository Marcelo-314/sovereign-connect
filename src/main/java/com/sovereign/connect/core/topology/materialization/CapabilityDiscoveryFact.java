package com.sovereign.connect.core.topology.materialization;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CapabilityDiscoveryFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String providerDeviceId,
    String providerEndpointId,
    String providerCapabilityKey,
    String capabilityKindHint,
    Instant observedAt,
    double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact {

    public CapabilityDiscoveryFact {
        Objects.requireNonNull(factId, "factId is required");
        Objects.requireNonNull(adapterInstanceId, "adapterInstanceId is required");
        Objects.requireNonNull(providerId, "providerId is required");
        Objects.requireNonNull(providerDeviceId, "providerDeviceId is required");
        Objects.requireNonNull(providerEndpointId, "providerEndpointId is required");
        Objects.requireNonNull(providerCapabilityKey, "providerCapabilityKey is required");
        Objects.requireNonNull(observedAt, "observedAt is required");
        rawProviderMetadata = Map.copyOf(Objects.requireNonNull(rawProviderMetadata, "rawProviderMetadata is required"));
    }
}
