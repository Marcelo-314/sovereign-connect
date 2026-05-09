package com.sovereign.connect.core.topology.model;

import java.util.Map;
import java.util.Objects;

public record ProviderDeviceRef(
    String provider,
    String providerDeviceId,
    Map<String, String> nativeCoordinates
) {

    public ProviderDeviceRef {
        Objects.requireNonNull(provider, "provider is required");
        Objects.requireNonNull(providerDeviceId, "providerDeviceId is required");
        nativeCoordinates = Map.copyOf(Objects.requireNonNull(nativeCoordinates, "nativeCoordinates is required"));
    }
}
