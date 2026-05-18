package com.sovereign.connect.core.temporal.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class TemporalEngineRunner {

    public static final long DEFAULT_POLLING_INTERVAL_MS = 1000L;

    private final TemporalEngineService engine;
    private final long pollingIntervalMs;
    private final String habitatId;
    private final Clock clock;
    private volatile boolean running;
    private Thread runnerThread;

    public TemporalEngineRunner(TemporalEngineService engine, String habitatId, Clock clock) {
        this(engine, habitatId, clock, DEFAULT_POLLING_INTERVAL_MS);
    }

    public TemporalEngineRunner(TemporalEngineService engine, String habitatId, Clock clock, long pollingIntervalMs) {
        this.engine = Objects.requireNonNull(engine, "engine is required");
        this.habitatId = Objects.requireNonNull(habitatId, "habitatId is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        if (pollingIntervalMs <= 0) {
            throw new IllegalArgumentException("pollingIntervalMs must be positive");
        }
        this.pollingIntervalMs = pollingIntervalMs;
    }

    public void start() {
        running = true;
        runnerThread = Thread.ofVirtual().name("temporal-engine-runner").start(() -> {
            while (running) {
                try {
                    engine.pollDueOnce(habitatId, Instant.now(clock));
                } catch (Exception ignored) {
                    // The runner is a thin runtime loop; one failed poll must not stop it.
                }
                try {
                    Thread.sleep(pollingIntervalMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void stop() {
        running = false;
        if (runnerThread != null) {
            runnerThread.interrupt();
        }
    }

    public long pollingIntervalMs() {
        return pollingIntervalMs;
    }
}
