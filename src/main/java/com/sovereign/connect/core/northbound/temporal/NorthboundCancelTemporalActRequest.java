package com.sovereign.connect.core.northbound.temporal;

public record NorthboundCancelTemporalActRequest(
    String temporalActId,
    String requestedByRef,
    String idempotencyKey,
    String reason
) {
}
