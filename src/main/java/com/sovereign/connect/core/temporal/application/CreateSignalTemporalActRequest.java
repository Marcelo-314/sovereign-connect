package com.sovereign.connect.core.temporal.application;

import java.time.Instant;

public record CreateSignalTemporalActRequest(
    String habitatId,
    Instant dueAt,
    String label,
    String signalKind,
    String notificationTargetRef,
    String createdByRef,
    String idempotencyKey,
    Instant requestedAt
) {
}
