package com.sovereign.eib.northbound.dto;

public record NorthboundCancelTemporalActRequestDto(String requestedByRef, String idempotencyKey, String reason) {
}
