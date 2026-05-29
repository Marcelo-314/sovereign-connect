package com.sovereign.connect.bus.runtime.dispatch.model;

import java.time.Instant;
import java.util.UUID;

public record DispatchObservationRecord(
        UUID observationId,
        UUID dispatchRecordId,
        UUID attemptId,
        DispatchState state,
        String code,
        String sanitizedReason,
        Instant observedAt
) {
}
