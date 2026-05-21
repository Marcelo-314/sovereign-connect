package com.sovereign.connect.core.temporal.observation;

import com.sovereign.connect.core.temporal.model.TemporalActStatus;

import java.time.Instant;

public record TemporalActObservation(
    String temporalActId,
    String habitatId,
    TemporalActStatus status,
    Instant dueAt,
    String payloadKind,
    String label,
    String signalKind,
    String notificationTargetRef,
    String createdByRef,
    Instant createdAt,
    Instant updatedAt,
    Instant firedAt,
    Instant terminalAt,
    String terminalReason
) {
}
