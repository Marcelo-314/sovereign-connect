package com.sovereign.connect.bus.runtime.dispatch.model;

import java.time.Instant;
import java.util.UUID;

public record DispatchAttempt(
        UUID attemptId,
        UUID dispatchRecordId,
        int attemptNumber,
        DispatchState state,
        Instant claimedAt
) {
}
