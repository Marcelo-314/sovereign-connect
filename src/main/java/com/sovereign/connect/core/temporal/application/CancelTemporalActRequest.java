package com.sovereign.connect.core.temporal.application;

import java.time.Instant;

public record CancelTemporalActRequest(
    String habitatId,
    String temporalActId,
    String requestedByRef,
    String idempotencyKey,
    String reason,
    Instant requestedAt
) {
}
