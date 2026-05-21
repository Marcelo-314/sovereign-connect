package com.sovereign.connect.core.temporal.model;

import java.time.Instant;
import java.util.Objects;

public record TemporalAct(
    String temporalActId,
    String habitatId,
    TemporalActStatus status,
    Instant dueAt,
    TemporalActPayload payload,
    String notificationTargetRef,
    CreatedByRef createdByRef,
    String topologyVersionAtRegistration,
    Instant createdAt,
    Instant updatedAt,
    Instant firedAt,
    Instant terminalAt,
    String terminalReason
) {

    public TemporalAct {
        Objects.requireNonNull(temporalActId, "temporalActId is required");
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(dueAt, "dueAt is required");
        Objects.requireNonNull(createdByRef, "createdByRef is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
