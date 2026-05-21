package com.sovereign.connect.core.temporal.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;

public final class TemporalEngineRunner implements AutoCloseable {

    public static final long DEFAULT_POLLING_INTERVAL_MS = 1000L;

    private final TemporalEngineService engine;
    private final long pollingIntervalMs;
    private final int maxDueActsPerCycle;
    private final long failureBackoffMs;
    private final int maxConsecutiveFailures;
    private final TemporalEngineHealth health;
    private final String habitatId;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread runnerThread;
    private volatile RuntimeException lastFailure;

    public TemporalEngineRunner(TemporalEngineService engine, String habitatId, Clock clock) {
        this(engine, habitatId, clock, DEFAULT_POLLING_INTERVAL_MS);
    }

    public TemporalEngineRunner(TemporalEngineService engine, String habitatId, Clock clock, long pollingIntervalMs) {
        this(engine, habitatId, clock, pollingIntervalMs, Integer.MAX_VALUE, pollingIntervalMs, Integer.MAX_VALUE, null);
    }

    public TemporalEngineRunner(
        TemporalEngineService engine,
        String habitatId,
        Clock clock,
        long pollingIntervalMs,
        int maxDueActsPerCycle,
        long failureBackoffMs,
        int maxConsecutiveFailures,
        TemporalEngineHealth health
    ) {
        this.engine = Objects.requireNonNull(engine, "engine is required");
        this.habitatId = Objects.requireNonNull(habitatId, "habitatId is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        if (pollingIntervalMs <= 0) {
            throw new IllegalArgumentException("pollingIntervalMs must be positive");
        }
        this.pollingIntervalMs = pollingIntervalMs;
        this.maxDueActsPerCycle = maxDueActsPerCycle;
        this.failureBackoffMs = failureBackoffMs <= 0 ? pollingIntervalMs : failureBackoffMs;
        this.maxConsecutiveFailures = maxConsecutiveFailures <= 0 ? Integer.MAX_VALUE : maxConsecutiveFailures;
        this.health = health;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        runnerThread = Thread.ofVirtual()
            .name("temporal-engine-runner")
            .start(this::runLoop);
    }

    private void runLoop() {
        int consecutiveFailures = 0;
        try {
            while (running.get()) {
                try {
                    engine.pollDueOnce(habitatId, Instant.now(clock), maxDueActsPerCycle);
                    consecutiveFailures = 0;
                    if (health != null) {
                        health.recordPollSuccess();
                    }
                } catch (RuntimeException ex) {
                    lastFailure = ex;
                    consecutiveFailures++;
                    if (health != null) {
                        health.recordPollFailure(ex);
                    }
                }

                if (!running.get()) {
                    break;
                }

                long waitMs = consecutiveFailures >= maxConsecutiveFailures ? failureBackoffMs : pollingIntervalMs;
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(waitMs));
            }
        } finally {
            running.set(false);
        }
    }

    public void stop() {
        running.set(false);
        Thread thread = runnerThread;
        if (thread != null) {
            LockSupport.unpark(thread);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public RuntimeException lastFailure() {
        return lastFailure;
    }

    public boolean awaitStopped(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout is required");
        Thread thread = runnerThread;
        if (thread == null) {
            return true;
        }
        if (Thread.currentThread() == thread) {
            return !thread.isAlive();
        }
        try {
            long timeoutMillis = Math.max(1L, timeout.toMillis());
            thread.join(timeoutMillis);
            return !thread.isAlive();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public long pollingIntervalMs() {
        return pollingIntervalMs;
    }

    @Override
    public void close() {
        stop();
    }
}
