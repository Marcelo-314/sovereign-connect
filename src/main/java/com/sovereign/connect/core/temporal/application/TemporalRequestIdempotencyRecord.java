package com.sovereign.connect.core.temporal.application;

import java.util.Arrays;

public record TemporalRequestIdempotencyRecord(
    String habitatId,
    String idempotencyKey,
    String requestKind,
    byte[] semanticFingerprint,
    String temporalActId,
    String resultKind,
    String resultCode,
    String resultJson
) {
    public boolean sameFingerprint(byte[] other) {
        return Arrays.equals(semanticFingerprint, other);
    }
}
