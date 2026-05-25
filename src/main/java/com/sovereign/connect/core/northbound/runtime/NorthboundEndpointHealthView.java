package com.sovereign.connect.core.northbound.runtime;

import java.time.Instant;

public record NorthboundEndpointHealthView(
    String endpointId,
    String status,
    Instant lastSeenAt,
    String details
) {
}
