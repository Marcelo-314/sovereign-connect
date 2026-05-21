package com.sovereign.connect.core.temporal.engine;

import java.time.Instant;

public class TemporalEngineHealth {

    private volatile TemporalEngineStatus status = TemporalEngineStatus.STOPPED;
    private volatile Instant lastPollAt;
    private volatile Instant lastSuccessfulPollAt;
    private volatile RuntimeException lastFailure;
    private volatile long firedTotal;
    private volatile long misfiredTotal;
    private volatile long cancelledTotal;
    private volatile long failedTotal;
    private volatile long skippedTotal;

    public void transitionTo(TemporalEngineStatus newStatus) {
        this.status = newStatus;
    }

    public boolean isReady() {
        return status == TemporalEngineStatus.RUNNING || status == TemporalEngineStatus.DEGRADED;
    }

    public void recordPollSuccess() {
        Instant now = Instant.now();
        lastPollAt = now;
        lastSuccessfulPollAt = now;
        lastFailure = null;
        if (status == TemporalEngineStatus.DEGRADED) {
            status = TemporalEngineStatus.RUNNING;
        }
    }

    public void recordPollFailure(RuntimeException failure) {
        lastPollAt = Instant.now();
        lastFailure = failure;
        status = TemporalEngineStatus.DEGRADED;
    }

    public void incrementFired() {
        firedTotal++;
    }

    public void incrementMisfired(long count) {
        misfiredTotal += count;
    }

    public void incrementCancelled() {
        cancelledTotal++;
    }

    public void incrementFailed(long count) {
        failedTotal += count;
    }

    public void incrementSkipped() {
        skippedTotal++;
    }

    public TemporalEngineStatus status() {
        return status;
    }

    public Instant lastPollAt() {
        return lastPollAt;
    }

    public Instant lastSuccessfulPollAt() {
        return lastSuccessfulPollAt;
    }

    public RuntimeException lastFailure() {
        return lastFailure;
    }

    public long firedTotal() {
        return firedTotal;
    }

    public long misfiredTotal() {
        return misfiredTotal;
    }

    public long cancelledTotal() {
        return cancelledTotal;
    }

    public long failedTotal() {
        return failedTotal;
    }

    public long skippedTotal() {
        return skippedTotal;
    }
}
