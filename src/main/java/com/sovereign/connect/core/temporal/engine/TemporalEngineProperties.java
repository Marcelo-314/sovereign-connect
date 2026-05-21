package com.sovereign.connect.core.temporal.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sovereign.temporal.engine")
public record TemporalEngineProperties(
    boolean enabled,
    String habitatId,
    long pollingIntervalMs,
    int maxDueActsPerCycle,
    int maxRecoveryBatchSize,
    int maxRecoveryBatchesPerStartup,
    int terminalRetentionDays,
    int maxTerminalResults,
    int maxMisfiredResults,
    boolean singleNodeGuardEnabled,
    long singleNodeGuardTtlMs,
    long shutdownTimeoutMs,
    long failureBackoffMs,
    int maxConsecutiveFailures
) {
}
