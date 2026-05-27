package com.sovereign.eib.domain;

import java.time.Instant;
import java.util.List;

public record EffectiveTemporalActView(
        String effectiveTemporalActRef,
        String canonicalTemporalActId,
        String status,
        Instant dueAt,
        String label,
        String signalKind,
        String notificationTargetRef,
        String visibilityStatus,
        String operabilityStatus,
        List<EibWarning> warnings
) {
}
