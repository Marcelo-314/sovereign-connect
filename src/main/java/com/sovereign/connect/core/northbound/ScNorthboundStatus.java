package com.sovereign.connect.core.northbound;

public enum ScNorthboundStatus {
    OK,
    CREATED,
    ACCEPTED,
    CANCELLED,
    NOT_FOUND,
    INVALID_REQUEST,
    INVALID_CANONICAL_ID,
    UNSUPPORTED_PROFILE,
    DEFERRED_SC_B_REQUIRED,
    UNKNOWN_PENDING_NORMALIZATION,
    INTERNAL_ERROR
}
