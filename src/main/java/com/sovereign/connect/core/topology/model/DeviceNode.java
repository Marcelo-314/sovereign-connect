package com.sovereign.connect.core.topology.model;

import java.util.List;
import java.util.Objects;

public record DeviceNode(
    String deviceId,
    String alias,
    String displayName,
    String roomId,
    String zoneId,
    DeviceKind kind,
    DeviceProvider provider,
    List<String> endpointIds,
    List<CapabilityNode> deviceCapabilities,
    DeviceTraits traits,
    DeviceHealth health,
    ProviderDeviceRef providerRef
) implements TopologyNode {

    public DeviceNode {
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(alias, "alias is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(roomId, "roomId is required");
        Objects.requireNonNull(zoneId, "zoneId is required");
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(provider, "provider is required");
        endpointIds = List.copyOf(Objects.requireNonNull(endpointIds, "endpointIds is required"));
        deviceCapabilities = List.copyOf(Objects.requireNonNull(deviceCapabilities, "deviceCapabilities is required"));
        Objects.requireNonNull(traits, "traits is required");
        Objects.requireNonNull(health, "health is required");
        Objects.requireNonNull(providerRef, "providerRef is required");
    }

    @Override
    public String canonicalId() {
        return deviceId;
    }
}
