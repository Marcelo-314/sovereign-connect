package com.sovereign.eib.northbound.dto;

import java.time.Instant;

public record NorthboundTemporalRuntimeStatusViewDto(
        String habitatId,
        String engineStatus,
        boolean isReady,
        Instant lastPollAt,
        Instant lastSuccessfulPollAt,
        long firedTotal,
        long misfiredTotal,
        long cancelledTotal,
        long failedTotal,
        long skippedTotal
) {
}
