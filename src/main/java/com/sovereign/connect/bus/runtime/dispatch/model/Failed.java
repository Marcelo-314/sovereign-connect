package com.sovereign.connect.bus.runtime.dispatch.model;

import java.util.UUID;

public record Failed(
        UUID dispatchRecordId,
        UUID attemptId,
        String code,
        String sanitizedReason,
        boolean retryable
) implements DispatchOutcome {
}
