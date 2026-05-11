package com.sovereign.connect.core.topology.materialization;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record EndpointDiscoveryFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String providerDeviceId,
    String providerEndpointId,
    String providerEndpointKind,
    List<String> capabilityHints,
    Instant observedAt,
    double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact {

    public EndpointDiscoveryFact {
        Objects.requireNonNull(factId, "factId is required");
        Objects.requireNonNull(adapterInstanceId, "adapterInstanceId is required");
        Objects.requireNonNull(providerId, "providerId is required");
        Objects.requireNonNull(providerDeviceId, "providerDeviceId is required");
        Objects.requireNonNull(providerEndpointId, "providerEndpointId is required");
        capabilityHints = List.copyOf(Objects.requireNonNull(capabilityHints, "capabilityHints is required"));
        Objects.requireNonNull(observedAt, "observedAt is required");
        rawProviderMetadata = Map.copyOf(Objects.requireNonNull(rawProviderMetadata, "rawProviderMetadata is required"));
    }
}
