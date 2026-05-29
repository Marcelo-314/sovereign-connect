package com.sovereign.connect.bus.runtime.dispatch.model;

import com.sovereign.connect.bus.contract.ScBusLane;

import java.util.UUID;

public record DispatchCandidate(
        UUID dispatchRecordId,
        UUID sourceRecordId,
        ScBusLane lane,
        String logicalTopic,
        String partitionKey,
        UUID correlationId,
        UUID causationId,
        UUID messageId
) {
}
