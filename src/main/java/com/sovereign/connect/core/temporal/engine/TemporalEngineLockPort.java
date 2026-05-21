package com.sovereign.connect.core.temporal.engine;

import java.time.Instant;

public interface TemporalEngineLockPort {
    boolean tryAcquire(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now, long ttlMs);

    int heartbeat(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now, long ttlMs);

    void release(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now);

    boolean isHeld(String habitatId, String storagePartitionRef, Instant now);
}
