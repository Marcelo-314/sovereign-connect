package com.sovereign.connect.bus.runtime.dispatch.model;

import java.util.UUID;

public record NoHandler(
        UUID dispatchRecordId,
        UUID attemptId,
        String topic,
        String sanitizedReason
) implements DispatchOutcome {
}
