package com.sovereign.eib.domain;

import java.time.Instant;

public record EibTemporalSignalAdmissionRequest(
        Instant dueAt,
        String label,
        String signalKind,
        String notificationTargetRef,
        String idempotencyKey
) {
}
