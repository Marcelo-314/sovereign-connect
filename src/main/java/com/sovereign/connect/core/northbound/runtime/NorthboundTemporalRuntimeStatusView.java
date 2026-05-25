package com.sovereign.connect.core.northbound.runtime;

import java.time.Instant;

public record NorthboundTemporalRuntimeStatusView(
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
