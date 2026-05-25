package com.sovereign.connect.core.northbound;

import java.util.List;
import java.util.Objects;

public record ScNorthboundResponse<T>(
    ScNorthboundStatus status,
    T payload,
    List<ScNorthboundWarning> warnings,
    ScNorthboundError error
) {

    public ScNorthboundResponse {
        Objects.requireNonNull(status, "status is required");
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public static <T> ScNorthboundResponse<T> of(
        ScNorthboundStatus status,
        T payload,
        List<ScNorthboundWarning> warnings,
        ScNorthboundError error
    ) {
        return new ScNorthboundResponse<>(status, payload, warnings, error);
    }

    public static <T> ScNorthboundResponse<T> ok(T payload) {
        return of(ScNorthboundStatus.OK, payload, List.of(), null);
    }

    public static <T> ScNorthboundResponse<T> created(T payload) {
        return of(ScNorthboundStatus.CREATED, payload, List.of(), null);
    }

    public static <T> ScNorthboundResponse<T> accepted(T payload) {
        return of(ScNorthboundStatus.ACCEPTED, payload, List.of(), null);
    }

    public static <T> ScNorthboundResponse<T> cancelled(T payload) {
        return of(ScNorthboundStatus.CANCELLED, payload, List.of(), null);
    }

    public static <T> ScNorthboundResponse<T> notFound(String code, String message) {
        return of(ScNorthboundStatus.NOT_FOUND, null, List.of(), new ScNorthboundError(code, message));
    }

    public static <T> ScNorthboundResponse<T> notFound(String message) {
        return notFound("NOT_FOUND", message);
    }

    public static <T> ScNorthboundResponse<T> invalidRequest(String code, String message) {
        return of(ScNorthboundStatus.INVALID_REQUEST, null, List.of(), new ScNorthboundError(code, message));
    }

    public static <T> ScNorthboundResponse<T> invalidCanonicalId(String code, String message) {
        return of(ScNorthboundStatus.INVALID_CANONICAL_ID, null, List.of(), new ScNorthboundError(code, message));
    }

    public static <T> ScNorthboundResponse<T> unsupportedProfile(String code, String message) {
        return of(ScNorthboundStatus.UNSUPPORTED_PROFILE, null, List.of(), new ScNorthboundError(code, message));
    }

    public static <T> ScNorthboundResponse<T> unsupportedProfile(String message) {
        return unsupportedProfile("UNSUPPORTED_PROFILE", message);
    }

    public static <T> ScNorthboundResponse<T> unknownPendingNormalization(String code, String message) {
        return of(
            ScNorthboundStatus.UNKNOWN_PENDING_NORMALIZATION,
            null,
            List.of(),
            new ScNorthboundError(code, message)
        );
    }

    public static <T> ScNorthboundResponse<T> unknownPendingNormalization(String message) {
        return unknownPendingNormalization("UNKNOWN_PENDING_NORMALIZATION", message);
    }

    public static <T> ScNorthboundResponse<T> internalError(String code, String message) {
        return of(ScNorthboundStatus.INTERNAL_ERROR, null, List.of(), new ScNorthboundError(code, message));
    }
}
