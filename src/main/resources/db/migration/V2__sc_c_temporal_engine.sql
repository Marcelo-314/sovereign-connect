-- temporal_acts: industrial v1 Signal-only
CREATE TABLE IF NOT EXISTS temporal_acts (
  habitat_id              TEXT NOT NULL,
  temporal_act_id         BLOB NOT NULL,
  status                  TEXT NOT NULL
    CHECK(status IN ('PENDING','ARMED','FIRED','CANCELLED','MISFIRED','FAILED','EXPIRED')),
  payload_kind            TEXT NOT NULL CHECK(payload_kind IN ('SIGNAL')),
  due_at_ms               INTEGER NOT NULL,
  label                   TEXT NOT NULL,
  signal_kind             TEXT NOT NULL,
  notification_target_ref TEXT NOT NULL,
  created_by_ref          TEXT NOT NULL,
  requested_at_ms         INTEGER,
  created_at_ms           INTEGER NOT NULL,
  updated_at_ms           INTEGER NOT NULL,
  fired_at_ms             INTEGER,
  terminal_at_ms          INTEGER,
  terminal_reason         TEXT,
  signal_payload_json     TEXT,
  metadata_json           TEXT,
  PRIMARY KEY (habitat_id, temporal_act_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE INDEX IF NOT EXISTS ix_temporal_acts_active_due
  ON temporal_acts(habitat_id, status, due_at_ms)
  WHERE status IN ('PENDING','ARMED');

CREATE INDEX IF NOT EXISTS ix_temporal_acts_terminal
  ON temporal_acts(habitat_id, status, terminal_at_ms)
  WHERE status IN ('FIRED','CANCELLED','MISFIRED','FAILED','EXPIRED');

CREATE INDEX IF NOT EXISTS ix_temporal_acts_misfired
  ON temporal_acts(habitat_id, terminal_at_ms)
  WHERE status = 'MISFIRED';

CREATE INDEX IF NOT EXISTS ix_temporal_acts_notification_target
  ON temporal_acts(habitat_id, notification_target_ref);

CREATE TABLE IF NOT EXISTS temporal_request_idempotency (
  habitat_id          TEXT NOT NULL,
  idempotency_key     TEXT NOT NULL,
  request_kind        TEXT NOT NULL CHECK(request_kind IN ('CREATE_SIGNAL','CANCEL')),
  semantic_fingerprint BLOB NOT NULL,
  temporal_act_id     BLOB,
  result_kind         TEXT NOT NULL
    CHECK(result_kind IN ('ACCEPTED','IDEMPOTENT_REPLAY','REJECTED','FAILED')),
  result_code         TEXT,
  result_json         TEXT,
  created_at_ms       INTEGER NOT NULL,
  updated_at_ms       INTEGER NOT NULL,
  PRIMARY KEY (habitat_id, idempotency_key, request_kind),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id),
  FOREIGN KEY (habitat_id, temporal_act_id)
    REFERENCES temporal_acts(habitat_id, temporal_act_id)
);

CREATE INDEX IF NOT EXISTS ix_temporal_request_idempotency_act
  ON temporal_request_idempotency(habitat_id, temporal_act_id, request_kind);

CREATE TABLE IF NOT EXISTS temporal_engine_locks (
  habitat_id            TEXT NOT NULL,
  storage_partition_ref TEXT NOT NULL,
  engine_instance_id    TEXT NOT NULL,
  acquired_at_ms        INTEGER NOT NULL,
  heartbeat_at_ms       INTEGER NOT NULL,
  expires_at_ms         INTEGER,
  status                TEXT NOT NULL
    CHECK(status IN ('ACTIVE','RELEASED','EXPIRED','FAILED')),
  metadata_json         TEXT,
  PRIMARY KEY (habitat_id, storage_partition_ref),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE TABLE IF NOT EXISTS recovery_runs (
  recovery_run_id BLOB PRIMARY KEY,
  mode            TEXT NOT NULL,
  status          TEXT NOT NULL,
  started_at_ms   INTEGER NOT NULL,
  completed_at_ms INTEGER,
  schema_version  TEXT,
  storage_profile TEXT,
  summary_json    TEXT
);

CREATE TABLE IF NOT EXISTS recovery_findings (
  finding_id       BLOB PRIMARY KEY,
  recovery_run_id  BLOB NOT NULL,
  severity         TEXT NOT NULL CHECK(severity IN ('INFO','WARN','ERROR','FATAL')),
  category         TEXT NOT NULL,
  code             TEXT NOT NULL,
  entity_type      TEXT,
  entity_id        BLOB,
  message          TEXT NOT NULL,
  resolved         INTEGER NOT NULL CHECK(resolved IN (0,1)),
  metadata_json    TEXT,
  created_at_ms    INTEGER NOT NULL,
  FOREIGN KEY (recovery_run_id) REFERENCES recovery_runs(recovery_run_id)
);
