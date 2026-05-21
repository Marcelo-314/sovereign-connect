CREATE TABLE IF NOT EXISTS sc_c_ledger_entries (
  ledger_entry_id TEXT NOT NULL,
  habitat_id TEXT NOT NULL,
  record_class TEXT NOT NULL,
  aggregate_type TEXT NOT NULL,
  aggregate_id TEXT NOT NULL,
  semantic_kind TEXT NOT NULL,
  payload_type TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  recorded_at_ms INTEGER NOT NULL,
  metadata_json TEXT,
  PRIMARY KEY (ledger_entry_id, habitat_id),
  UNIQUE (habitat_id, idempotency_key),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE TABLE IF NOT EXISTS sc_c_outbox_entries (
  outbox_entry_id TEXT PRIMARY KEY,
  ledger_entry_id TEXT NOT NULL,
  habitat_id TEXT NOT NULL,
  outbound_kind TEXT NOT NULL,
  delivery_lane TEXT NOT NULL,
  logical_topic TEXT NOT NULL,
  semantic_payload_json TEXT NOT NULL,
  notification_target_ref TEXT,
  idempotency_key TEXT NOT NULL,
  status TEXT NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  created_at_ms INTEGER NOT NULL,
  updated_at_ms INTEGER NOT NULL,
  metadata_json TEXT,
  UNIQUE (habitat_id, idempotency_key),
  FOREIGN KEY (ledger_entry_id, habitat_id)
    REFERENCES sc_c_ledger_entries(ledger_entry_id, habitat_id)
);
