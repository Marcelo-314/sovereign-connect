package com.sovereign.connect.bus.contract;

import java.time.Instant;
import java.util.UUID;

public record ScMessageMetadata(
        UUID messageId,
        Instant emittedAt,
        UUID correlationId,
        UUID causationId,
        String topologyVersion
) {
}
