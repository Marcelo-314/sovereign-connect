package com.sovereign.connect.core.topology.materialization;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DeviceStateFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String providerDeviceId,
    Map<String, Object> statePayload,
    Instant observedAt,
    double confidence
) implements TopologyFact {

    public DeviceStateFact {
        Objects.requireNonNull(factId, "factId is required");
        Objects.requireNonNull(adapterInstanceId, "adapterInstanceId is required");
        Objects.requireNonNull(providerId, "providerId is required");
        Objects.requireNonNull(providerDeviceId, "providerDeviceId is required");
        statePayload = Map.copyOf(Objects.requireNonNull(statePayload, "statePayload is required"));
        Objects.requireNonNull(observedAt, "observedAt is required");
    }
}
