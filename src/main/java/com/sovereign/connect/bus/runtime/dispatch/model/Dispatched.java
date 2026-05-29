package com.sovereign.connect.bus.runtime.dispatch.model;

import java.util.UUID;

public record Dispatched(
        UUID dispatchRecordId,
        UUID attemptId,
        String topic,
        String partitionKey
) implements DispatchOutcome {
}
