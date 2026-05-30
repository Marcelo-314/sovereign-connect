package com.sovereign.connect.bus.runtime.serialization;

import java.util.Objects;
import java.util.UUID;

public final class ScCorrelationTokenCodec {

    private ScCorrelationTokenCodec() {}

    /** UUID -> 32 lowercase hex chars, no hyphens. scid1_ is NOT used. */
    public static String encode(UUID id) {
        Objects.requireNonNull(id, "id is required");
        return id.toString().replace("-", "");
    }

    /** 32 lowercase hex chars -> UUID. */
    public static UUID decode(String token) {
        Objects.requireNonNull(token, "token is required");
        if (token.length() != 32) {
            throw new IllegalArgumentException(
                "correlation token must be 32 chars, got: " + token.length());
        }
        if (!token.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException(
                "correlation token must be lowercase hex: " + token);
        }
        String canonical = token.substring(0, 8) + "-"
            + token.substring(8, 12) + "-"
            + token.substring(12, 16) + "-"
            + token.substring(16, 20) + "-"
            + token.substring(20);
        return UUID.fromString(canonical);
    }
}
