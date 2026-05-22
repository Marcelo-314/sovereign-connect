CREATE TABLE IF NOT EXISTS topology_versions (
  habitat_id    TEXT PRIMARY KEY,
  version_value TEXT NOT NULL,
  updated_at_ms INTEGER NOT NULL,
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE TABLE IF NOT EXISTS topology_metadata (
  habitat_id       TEXT PRIMARY KEY,
  schema_version   TEXT NOT NULL,
  last_modified_ms INTEGER NOT NULL,
  source           TEXT NOT NULL,
  checksum         TEXT,
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE TABLE IF NOT EXISTS rooms (
  habitat_id  TEXT NOT NULL,
  room_id     TEXT NOT NULL,
  room_name   TEXT NOT NULL,
  traits_json TEXT NOT NULL,
  PRIMARY KEY (habitat_id, room_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE TABLE IF NOT EXISTS zones (
  habitat_id  TEXT NOT NULL,
  zone_id     TEXT NOT NULL,
  zone_name   TEXT NOT NULL,
  room_id     TEXT NOT NULL,
  traits_json TEXT NOT NULL,
  PRIMARY KEY (habitat_id, zone_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id),
  FOREIGN KEY (habitat_id, room_id) REFERENCES rooms(habitat_id, room_id)
);

CREATE TABLE IF NOT EXISTS devices (
  habitat_id                  TEXT NOT NULL,
  device_id                   TEXT NOT NULL,
  alias                       TEXT NOT NULL,
  display_name                TEXT NOT NULL,
  room_id                     TEXT NOT NULL,
  zone_id                     TEXT NOT NULL,
  kind                        TEXT NOT NULL,
  provider                    TEXT NOT NULL,
  traits_json                 TEXT NOT NULL,
  device_health_status        TEXT NOT NULL,
  device_health_last_seen_ms  INTEGER,
  device_health_details       TEXT,
  provider_ref_json           TEXT NOT NULL,
  PRIMARY KEY (habitat_id, device_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id),
  FOREIGN KEY (habitat_id, room_id) REFERENCES rooms(habitat_id, room_id)
);

CREATE INDEX IF NOT EXISTS ix_devices_room
  ON devices(habitat_id, room_id);
CREATE INDEX IF NOT EXISTS ix_devices_zone
  ON devices(habitat_id, zone_id) WHERE zone_id != '';

CREATE TABLE IF NOT EXISTS endpoints (
  habitat_id        TEXT NOT NULL,
  endpoint_id       TEXT NOT NULL,
  device_id         TEXT NOT NULL,
  alias             TEXT NOT NULL,
  display_name      TEXT NOT NULL,
  kind              TEXT NOT NULL,
  room_id           TEXT NOT NULL,
  zone_id           TEXT NOT NULL,
  traits_json       TEXT NOT NULL,
  provider_ref_json TEXT NOT NULL,
  metadata_json     TEXT NOT NULL,
  PRIMARY KEY (habitat_id, endpoint_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id),
  FOREIGN KEY (habitat_id, device_id) REFERENCES devices(habitat_id, device_id)
);

CREATE INDEX IF NOT EXISTS ix_endpoints_device
  ON endpoints(habitat_id, device_id);
CREATE INDEX IF NOT EXISTS ix_endpoints_room
  ON endpoints(habitat_id, room_id);

CREATE TABLE IF NOT EXISTS capabilities (
  habitat_id     TEXT NOT NULL,
  capability_id  TEXT NOT NULL,
  owner_kind     TEXT NOT NULL CHECK(owner_kind IN ('DEVICE','ENDPOINT')),
  owner_id       TEXT NOT NULL,
  name           TEXT NOT NULL,
  kind           TEXT NOT NULL,
  traits_json    TEXT NOT NULL,
  PRIMARY KEY (habitat_id, capability_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE INDEX IF NOT EXISTS ix_capabilities_owner
  ON capabilities(habitat_id, owner_kind, owner_id);

CREATE TABLE IF NOT EXISTS topology_spatial_relations (
  habitat_id                 TEXT NOT NULL,
  relation_id                TEXT NOT NULL,
  kind                       TEXT NOT NULL,
  subject_type               TEXT NOT NULL,
  subject_id                 TEXT NOT NULL,
  target_type                TEXT NOT NULL,
  target_id                  TEXT NOT NULL,
  is_primary                 INTEGER NOT NULL CHECK(is_primary IN (0,1)),
  confidence                 TEXT NOT NULL,
  source                     TEXT NOT NULL,
  provider_spatial_ref_json  TEXT,
  observed_at_ms             INTEGER,
  metadata_json              TEXT NOT NULL,
  PRIMARY KEY (habitat_id, relation_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE INDEX IF NOT EXISTS ix_tsr_subject
  ON topology_spatial_relations(habitat_id, subject_type, subject_id);
CREATE INDEX IF NOT EXISTS ix_tsr_target
  ON topology_spatial_relations(habitat_id, target_type, target_id);
CREATE INDEX IF NOT EXISTS ix_tsr_target_primary
  ON topology_spatial_relations(habitat_id, target_type, target_id, subject_type)
  WHERE is_primary = 1;

CREATE TABLE IF NOT EXISTS topology_snapshots (
  habitat_id       TEXT PRIMARY KEY,
  topology_version TEXT NOT NULL,
  topology_json    TEXT NOT NULL,
  captured_at_ms   INTEGER NOT NULL,
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE TABLE IF NOT EXISTS mutation_records (
  mutation_id                TEXT PRIMARY KEY,
  habitat_id                 TEXT NOT NULL,
  from_version               TEXT NOT NULL,
  to_version                 TEXT NOT NULL,
  change_kinds_json          TEXT NOT NULL,
  affected_device_ids_json   TEXT NOT NULL,
  affected_endpoint_ids_json TEXT NOT NULL,
  reason                     TEXT NOT NULL,
  accepted_at_ms             INTEGER NOT NULL,
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE INDEX IF NOT EXISTS ix_mutations_habitat
  ON mutation_records(habitat_id, accepted_at_ms);

CREATE TABLE IF NOT EXISTS device_states (
  habitat_id    TEXT NOT NULL,
  device_id     TEXT NOT NULL,
  state_json    TEXT NOT NULL,
  updated_at_ms INTEGER NOT NULL,
  PRIMARY KEY (habitat_id, device_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE TABLE IF NOT EXISTS endpoint_health (
  habitat_id       TEXT NOT NULL,
  endpoint_id      TEXT NOT NULL,
  status           TEXT NOT NULL,
  last_seen_at_ms  INTEGER,
  details          TEXT,
  updated_at_ms    INTEGER NOT NULL,
  PRIMARY KEY (habitat_id, endpoint_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE TABLE IF NOT EXISTS materialization_decision_replay (
  habitat_id               TEXT NOT NULL,
  fact_id                  TEXT NOT NULL,
  decision_id              TEXT NOT NULL,
  kind                     TEXT NOT NULL,
  previous_version_value   TEXT,
  resulting_version_value  TEXT,
  emitted_changes_json     TEXT NOT NULL,
  reason                   TEXT NOT NULL,
  recorded_at_ms           INTEGER NOT NULL,
  PRIMARY KEY (habitat_id, fact_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);
