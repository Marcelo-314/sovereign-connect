package com.sovereign.connect.core.temporal.engine;

import com.sovereign.connect.core.temporal.service.TemporalEngineRunner;
import com.sovereign.connect.core.temporal.service.TemporalEngineService;
import com.sovereign.connect.core.temporal.service.TemporalRecoveryResult;
import org.springframework.context.SmartLifecycle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class TemporalEngineLifecycle implements SmartLifecycle {

    private static final String STORAGE_PARTITION_REF = "sqlite.default";

    private final TemporalEngineService engineService;
    private final TemporalEngineRunner runner;
    private final TemporalEngineProperties properties;
    private final TemporalEngineHealth health;
    private final TemporalEngineLockPort lockRepository;
    private final Clock clock;
    private final String engineInstanceId = UUID.randomUUID().toString();
    private final AtomicBoolean heartbeatRunning = new AtomicBoolean(false);
    private volatile boolean running;
    private volatile Thread heartbeatThread;

    public TemporalEngineLifecycle(
        TemporalEngineService engineService,
        TemporalEngineRunner runner,
        TemporalEngineProperties properties,
        TemporalEngineHealth health,
        TemporalEngineLockPort lockRepository,
        Clock clock
    ) {
        this.engineService = Objects.requireNonNull(engineService, "engineService is required");
        this.runner = Objects.requireNonNull(runner, "runner is required");
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.health = Objects.requireNonNull(health, "health is required");
        this.lockRepository = Objects.requireNonNull(lockRepository, "lockRepository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public void start() {
        if (!properties.enabled()) {
            health.transitionTo(TemporalEngineStatus.DISABLED);
            return;
        }
        health.transitionTo(TemporalEngineStatus.RECOVERING);
        recoverMisfires();
        if (properties.singleNodeGuardEnabled()) {
            boolean acquired = lockRepository.tryAcquire(
                properties.habitatId(),
                STORAGE_PARTITION_REF,
                engineInstanceId,
                Instant.now(clock),
                properties.singleNodeGuardTtlMs()
            );
            if (!acquired) {
                health.transitionTo(TemporalEngineStatus.FAILED);
                throw new IllegalStateException("SINGLE_NODE_GUARD_VIOLATED: another engine holds the lock");
            }
            startHeartbeat();
        }
        health.transitionTo(TemporalEngineStatus.RUNNING);
        runner.start();
        running = true;
    }

    private void recoverMisfires() {
        boolean complete = false;
        for (int batch = 0; batch < properties.maxRecoveryBatchesPerStartup(); batch++) {
            TemporalRecoveryResult result = engineService.classifyMisfires(
                properties.habitatId(),
                Instant.now(clock),
                properties.maxRecoveryBatchSize()
            );
            health.incrementMisfired(result.misfiredCount());
            health.incrementFailed(result.failedCount());
            if (result.complete()) {
                complete = true;
                break;
            }
            if (result.misfiredCount() + result.failedCount() == 0) {
                health.transitionTo(TemporalEngineStatus.FAILED);
                throw new IllegalStateException("TEMPORAL_RECOVERY_STALLED: recovery batch incomplete with zero progress");
            }
        }
        if (!complete) {
            health.transitionTo(TemporalEngineStatus.FAILED);
            throw new IllegalStateException("TEMPORAL_RECOVERY_INCOMPLETE: max recovery batches exceeded");
        }
    }

    @Override
    public void stop() {
        runner.stop();
        boolean stopped = runner.awaitStopped(Duration.ofMillis(properties.shutdownTimeoutMs()));
        if (!stopped) {
            health.transitionTo(TemporalEngineStatus.FAILED);
            running = false;
            throw new IllegalStateException(
                "TEMPORAL_ENGINE_STOP_TIMEOUT: runner did not stop within "
                    + properties.shutdownTimeoutMs() + "ms - single-node lock NOT released; heartbeat preserved"
            );
        }
        stopHeartbeat();
        if (properties.singleNodeGuardEnabled()) {
            lockRepository.release(properties.habitatId(), STORAGE_PARTITION_REF, engineInstanceId, Instant.now(clock));
        }
        running = false;
        health.transitionTo(TemporalEngineStatus.STOPPED);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private void startHeartbeat() {
        if (!heartbeatRunning.compareAndSet(false, true)) {
            return;
        }
        heartbeatThread = Thread.ofVirtual()
            .name("temporal-engine-lock-heartbeat")
            .start(() -> {
                long intervalMs = Math.max(1L, properties.singleNodeGuardTtlMs() / 3L);
                while (heartbeatRunning.get()) {
                    int affected = lockRepository.heartbeat(
                        properties.habitatId(),
                        STORAGE_PARTITION_REF,
                        engineInstanceId,
                        Instant.now(clock),
                        properties.singleNodeGuardTtlMs()
                    );
                    if (affected != 1) {
                        handleLockLoss();
                        return;
                    }
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(intervalMs));
                }
            });
    }

    private void handleLockLoss() {
        heartbeatRunning.set(false);
        runner.stop();
        runner.awaitStopped(Duration.ofMillis(properties.shutdownTimeoutMs()));
        running = false;
        health.transitionTo(TemporalEngineStatus.FAILED);
    }

    private void stopHeartbeat() {
        heartbeatRunning.set(false);
        Thread thread = heartbeatThread;
        if (thread != null) {
            LockSupport.unpark(thread);
        }
    }
}
