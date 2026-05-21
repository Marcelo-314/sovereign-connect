package com.sovereign.connect.core.temporal.service;

public record TemporalRecoveryResult(
    int misfiredCount,
    int failedCount,
    boolean complete
) {
}
