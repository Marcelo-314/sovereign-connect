package com.sovereign.connect.core.northbound.runtime;

import java.time.Instant;

public record NorthboundDeviceHealthView(
    String deviceId,
    String status,
    Instant lastSeenAt,
    String details
) {
}
