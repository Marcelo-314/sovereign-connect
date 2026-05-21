package com.sovereign.connect.core.temporal.application;

import java.time.Instant;
import java.util.Optional;

public interface TemporalRequestIdempotencyPort {
    Optional<TemporalRequestIdempotencyRecord> find(String habitatId, String idempotencyKey, String requestKind);

    void insert(TemporalRequestIdempotencyRecord record, Instant now);
}
