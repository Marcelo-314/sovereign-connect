package com.sovereign.connect.core.topology.model;

import java.util.List;
import java.util.Objects;

public record EndpointNode(
    String endpointId,
    String deviceId,
    String alias,
    String displayName,
    EndpointKind kind,
    String roomId,
    String zoneId,
    List<CapabilityNode> capabilities,
    EndpointTraits traits,
    EndpointHealth health,
    ProviderEndpointRef providerRef,
    EndpointMetadata metadata
) implements TopologyNode {

    public EndpointNode {
        Objects.requireNonNull(endpointId, "endpointId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(alias, "alias is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(roomId, "roomId is required");
        Objects.requireNonNull(zoneId, "zoneId is required");
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities is required"));
        Objects.requireNonNull(traits, "traits is required");
        Objects.requireNonNull(health, "health is required");
        Objects.requireNonNull(providerRef, "providerRef is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (endpointId.equals(providerRef.providerEndpointId())) {
            throw new IllegalArgumentException("providerEndpointId must not be used as canonical endpointId");
        }
    }

    @Override
    public String canonicalId() {
        return endpointId;
    }
}
