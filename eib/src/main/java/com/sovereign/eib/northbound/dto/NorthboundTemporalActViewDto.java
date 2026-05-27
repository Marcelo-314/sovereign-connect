package com.sovereign.eib.northbound.dto;

import java.time.Instant;

public record NorthboundTemporalActViewDto(
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
