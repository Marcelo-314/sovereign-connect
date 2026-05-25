package com.sovereign.connect.core.northbound.temporal;

import java.time.Instant;

public record NorthboundTemporalActView(
    String temporalActId,
    String habitatId,
    String status,
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
