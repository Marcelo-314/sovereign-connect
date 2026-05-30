CREATE TABLE IF NOT EXISTS sc_b_dispatch_observations (
    observation_id         TEXT    PRIMARY KEY,
    dispatch_record_id     TEXT    NOT NULL,
    attempt_id             TEXT    NULL,
    state                  TEXT    NOT NULL,
    code                   TEXT    NULL,
    sanitized_reason       TEXT    NULL,
    observed_at_ms         INTEGER NOT NULL,
    FOREIGN KEY (dispatch_record_id)
        REFERENCES sc_b_dispatch_records(dispatch_record_id),
    CHECK (state IN (
        'PENDING','CLAIMED','DISPATCHING','DISPATCHED',
        'DELIVERY_FAILED','RETRY_SCHEDULED','EXHAUSTED',
        'CANCELLED_BY_SUPERSEDE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_sc_b_dispatch_observations_record
    ON sc_b_dispatch_observations(dispatch_record_id, observed_at_ms);
