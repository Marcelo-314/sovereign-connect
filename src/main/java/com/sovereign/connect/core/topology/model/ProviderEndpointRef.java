package com.sovereign.connect.core.topology.model;

import java.util.Map;
import java.util.Objects;

public record ProviderEndpointRef(
    String provider,
    String providerDeviceId,
    String providerEndpointId,
    Map<String, String> nativeCoordinates
) {

    public ProviderEndpointRef {
        Objects.requireNonNull(provider, "provider is required");
        Objects.requireNonNull(providerDeviceId, "providerDeviceId is required");
        Objects.requireNonNull(providerEndpointId, "providerEndpointId is required");
        nativeCoordinates = Map.copyOf(Objects.requireNonNull(nativeCoordinates, "nativeCoordinates is required"));
    }
}
