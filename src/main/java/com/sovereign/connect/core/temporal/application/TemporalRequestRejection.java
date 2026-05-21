package com.sovereign.connect.core.temporal.application;

public record TemporalRequestRejection(
    TemporalRequestRejectionCode code,
    String message
) {
}
