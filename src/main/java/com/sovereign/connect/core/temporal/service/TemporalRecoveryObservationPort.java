package com.sovereign.connect.core.temporal.service;

import java.time.Instant;
import java.util.UUID;

public interface TemporalRecoveryObservationPort {
    static TemporalRecoveryObservationPort noOp() {
        return new TemporalRecoveryObservationPort() {
        };
    }

    default UUID startRun(String mode, Instant startedAt) {
        return UUID.randomUUID();
    }

    default void recordFinding(UUID recoveryRunId, String severity, String category, String code, String entityType,
                               String entityId, String message, boolean resolved, Instant createdAt) {
    }

    default void completeRun(UUID recoveryRunId, String status, TemporalRecoveryResult result, Instant completedAt) {
    }
}
