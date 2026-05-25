package com.sovereign.connect.core.northbound.runtime;

import java.time.Instant;

public record NorthboundRecoveryStatusView(
    String habitatId,
    String engineStatus,
    boolean proxy,
    Instant observedAt
) {
}
