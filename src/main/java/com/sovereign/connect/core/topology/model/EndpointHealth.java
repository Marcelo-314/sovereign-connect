package com.sovereign.connect.core.topology.model;

import java.time.Instant;
import java.util.Objects;

public record EndpointHealth(
    HealthStatus status,
    Instant lastSeenAt,
    String details
) {

    public EndpointHealth {
        Objects.requireNonNull(status, "status is required");
    }
}
