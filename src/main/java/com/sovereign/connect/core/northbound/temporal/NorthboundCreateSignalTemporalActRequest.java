package com.sovereign.connect.core.northbound.temporal;

import java.time.Instant;

public record NorthboundCreateSignalTemporalActRequest(
    Instant dueAt,
    String label,
    String signalKind,
    String notificationTargetRef,
    String createdByRef,
    String idempotencyKey
) {
}
