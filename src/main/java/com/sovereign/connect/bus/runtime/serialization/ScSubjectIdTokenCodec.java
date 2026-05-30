package com.sovereign.connect.bus.runtime.serialization;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class ScSubjectIdTokenCodec {

    private static final String PREFIX = "scid1_";
    private static final Base64.Encoder ENCODER =
        Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER =
        Base64.getUrlDecoder();

    private ScSubjectIdTokenCodec() {}

    public static String encodeScid1(String canonicalId) {
        Objects.requireNonNull(canonicalId, "canonicalId is required");
        if (canonicalId.isBlank()) {
            throw new IllegalArgumentException("canonicalId must not be blank");
        }
        return PREFIX + ENCODER.encodeToString(
            canonicalId.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeScid1(String token) {
        Objects.requireNonNull(token, "token is required");
        if (!token.startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                "token is not a valid scid1_ token: " + token);
        }

        String encoded = token.substring(PREFIX.length());
        if (encoded.isBlank()) {
            throw new IllegalArgumentException("scid1_ token payload must not be blank");
        }

        try {
            String decoded = new String(
                DECODER.decode(encoded),
                StandardCharsets.UTF_8);
            if (decoded.isBlank()) {
                throw new IllegalArgumentException("decoded canonicalId must not be blank");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "malformed scid1_ token: " + token, e);
        }
    }
}
