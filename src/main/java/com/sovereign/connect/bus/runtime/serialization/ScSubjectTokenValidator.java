package com.sovereign.connect.bus.runtime.serialization;

import java.util.Objects;

public final class ScSubjectTokenValidator {

    private static final char[] PROHIBITED = new char[]{'.', '+', '/', '=', '*', '>'};

    private ScSubjectTokenValidator() {}

    public static void requireSafeToken(String token) {
        Objects.requireNonNull(token, "token is required");
        if (token.isBlank()) {
            throw new IllegalArgumentException("subject token must not be blank");
        }
        for (char c : PROHIBITED) {
            if (token.indexOf(c) >= 0) {
                throw new IllegalArgumentException(
                    "subject token contains prohibited char '" + c + "': " + token);
            }
        }
    }

    public static boolean isSafeToken(String token) {
        if (token == null || token.isBlank()) return false;
        for (char c : PROHIBITED) {
            if (token.indexOf(c) >= 0) return false;
        }
        return true;
    }
}
