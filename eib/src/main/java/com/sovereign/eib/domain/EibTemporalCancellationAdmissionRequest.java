package com.sovereign.eib.domain;

public record EibTemporalCancellationAdmissionRequest(String idempotencyKey, String reason) {
}
