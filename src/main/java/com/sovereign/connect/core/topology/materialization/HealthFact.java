package com.sovereign.connect.core.topology.materialization;

import com.sovereign.connect.core.topology.model.HealthStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HealthFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String providerDeviceId,
    String providerEndpointId,
    HealthStatus healthStatus,
    String healthReason,
    Instant observedAt,
    double confidence
) implements TopologyFact {

    public HealthFact {
        Objects.requireNonNull(factId, "factId is required");
        Objects.requireNonNull(adapterInstanceId, "adapterInstanceId is required");
        Objects.requireNonNull(providerId, "providerId is required");
        Objects.requireNonNull(providerDeviceId, "providerDeviceId is required");
        Objects.requireNonNull(healthStatus, "healthStatus is required");
        Objects.requireNonNull(observedAt, "observedAt is required");
    }
}
