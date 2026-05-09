package com.sovereign.connect.core.topology.model;

import java.time.Instant;
import java.util.Objects;

public record DeviceHealth(
    HealthStatus status,
    Instant lastSeenAt,
    String details
) {

    public DeviceHealth {
        Objects.requireNonNull(status, "status is required");
    }
}
