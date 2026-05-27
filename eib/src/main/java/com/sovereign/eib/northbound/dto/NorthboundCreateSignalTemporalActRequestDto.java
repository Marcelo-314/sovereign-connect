package com.sovereign.eib.northbound.dto;

import java.time.Instant;

public record NorthboundCreateSignalTemporalActRequestDto(
        Instant dueAt,
        String label,
        String signalKind,
        String notificationTargetRef,
        String createdByRef,
        String idempotencyKey
) {
}
