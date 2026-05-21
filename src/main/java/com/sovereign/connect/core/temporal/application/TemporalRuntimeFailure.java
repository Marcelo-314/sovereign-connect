package com.sovereign.connect.core.temporal.application;

public record TemporalRuntimeFailure(
    TemporalRuntimeFailureCode code,
    String message
) {
}
