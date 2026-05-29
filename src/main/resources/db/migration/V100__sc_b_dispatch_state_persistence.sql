CREATE TABLE IF NOT EXISTS sc_b_dispatch_records (
    dispatch_record_id TEXT PRIMARY KEY,
    source_record_id TEXT NULL,
    current_attempt_id TEXT NULL,
    current_attempt_number INTEGER NOT NULL DEFAULT 0,
    current_state TEXT NOT NULL,
    supersession_evidence_ref TEXT NULL,
    created_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    CHECK (current_state IN (
        'PENDING', 'CLAIMED', 'DISPATCHING', 'DISPATCHED',
        'DELIVERY_FAILED', 'RETRY_SCHEDULED', 'EXHAUSTED',
        'CANCELLED_BY_SUPERSEDE'
    ))
);

CREATE TABLE IF NOT EXISTS sc_b_dispatch_attempts (
    attempt_id TEXT PRIMARY KEY,
    dispatch_record_id TEXT NOT NULL,
    attempt_number INTEGER NOT NULL,
    state TEXT NOT NULL,
    claimed_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    supersession_evidence_ref TEXT NULL,
    FOREIGN KEY (dispatch_record_id) REFERENCES sc_b_dispatch_records(dispatch_record_id),
    UNIQUE (dispatch_record_id, attempt_number),
    CHECK (state IN (
        'PENDING', 'CLAIMED', 'DISPATCHING', 'DISPATCHED',
        'DELIVERY_FAILED', 'RETRY_SCHEDULED', 'EXHAUSTED',
        'CANCELLED_BY_SUPERSEDE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_sc_b_dispatch_attempts_record
    ON sc_b_dispatch_attempts(dispatch_record_id);
