# context.md — MU-017 Normalized Topology SQLite/Flyway Persistence

```text
Package version:  v0.2.1
Target:           MU-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001
Operational slot: MU-017
MIR:              MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v0.1.1-draft
CSA:              CSA-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v0.1.3-draft
Baseline ZIP:     sovereign-connect-016-patch-3.zip
Baseline tests:   139 / 0 failures
```

---

## 0. How to use this file

Read completely before writing a single line of code. Sections 2–4 show the
current domain and service surfaces that must be respected. The DDL, FK ordering,
authority rules, transaction boundary and Spring wiring rules in this file are
normative. Sections 5–9 provide mandatory implementation patterns; Java helper
names, row-mapper classes and local test helpers may be adapted to the existing
codebase as long as the stated behavior and acceptance criteria are preserved.

---

## 1. Task thesis and normative decisions

**After MU-017:**
- Normalized SQLite tables are authoritative for Base Topology.
- `topology_json` is a derived compatibility snapshot/read model only.
- `H2BaseTopologyRepository` is legacy seed/test fixture only.
- `InMemoryBaseTopologyRepository` is domain/test fixture only.
- Topology services are Spring beans backed by SQLite.

**DEC-017-001:** Normalized tables are production authority.
**DEC-017-002:** No graph database.
**DEC-017-003:** FK ordering is mandatory in the write transaction.
**DEC-017-004:** Structural save MUST NOT overwrite endpoint health or device state.

---

## 2. Exact domain model — record constructors (use these exactly)

### 2.1 HabitatBaseTopology (8 fields)

```java
public record HabitatBaseTopology(
    String habitatId,
    TopologyVersion topologyVersion,
    List<RoomNode> rooms,
    List<ZoneNode> zones,
    List<DeviceNode> devices,
    List<EndpointNode> endpoints,
    List<TopologySpatialRelation> spatialRelations,
    TopologyMetadata metadata
) {}
// Invariant: topologyVersion.isScopedToHabitat(habitatId) must be true
// Construction: use TopologyVersion.habitatVersion(habitatId, versionLong)
```

### 2.2 TopologyVersion (2 fields)

```java
public record TopologyVersion(TopologyVersionScope scope, String value) {}
// Construction from DB: TopologyVersion.habitatVersion(habitatId, Long.parseLong(versionValue))
// where versionValue is the String stored in topology_versions.version_value
```

### 2.3 TopologyMetadata (4 fields)

```java
public record TopologyMetadata(
    String schemaVersion,   // e.g. "base-topology.seed.v1"
    Instant lastModified,
    String source,          // e.g. "SC-C"
    String checksum         // nullable
) {}
```

### 2.4 RoomNode (6 fields)

```java
public record RoomNode(
    String roomId,
    String roomName,
    List<String> zoneIds,        // IDs of child zones
    List<String> deviceIds,       // IDs of devices placed in this room
    List<String> endpointIds,     // IDs of endpoints placed in this room
    RoomTraits traits
) {}
// Example from tests:
// new RoomNode("room.kitchen", "Kitchen",
//     List.of("zone.kitchen.worktop"), List.of(), List.of(), new RoomTraits(false, false))
```

### 2.5 ZoneNode (6 fields)

```java
public record ZoneNode(
    String zoneId,
    String zoneName,
    String roomId,           // parent room
    List<String> deviceIds,
    List<String> endpointIds,
    ZoneTraits traits
) {}
// Example:
// new ZoneNode("zone.kitchen.worktop", "Kitchen Worktop", "room.kitchen",
//     List.of(), List.of(), new ZoneTraits(true))
```

### 2.6 DeviceNode (12 fields)

```java
public record DeviceNode(
    String deviceId,                    // canonical: "device.<provider>.<providerDeviceId>"
    String alias,
    String displayName,
    String roomId,                      // direct placement (may be "" for unplaced)
    String zoneId,                      // direct placement (may be "" for unplaced)
    DeviceKind kind,                    // enum: LIGHT, SENSOR, etc.
    DeviceProvider provider,            // enum: TUYA, HOMEKIT, etc.
    List<String> endpointIds,
    List<CapabilityNode> deviceCapabilities,
    DeviceTraits traits,
    DeviceHealth health,                // embedded health in structural aggregate
    ProviderDeviceRef providerRef       // metadata, NOT canonical key
) {}
```

**Critical:** `DeviceNode.health` (a `DeviceHealth` record with `status`, `lastSeenAt`,
`details`) is embedded in the structural aggregate. It is persisted in the `devices`
table via `save()`. It is NOT in `endpoint_health`. Do NOT confuse the two.

`EndpointNode.health` (an `EndpointHealth` record) is NOT persisted in the structural
table. It lives in `endpoint_health` and is written only through `EndpointHealthWritePort`
or `TopologyMaterializationStatePort.saveEndpointHealth()`.

### 2.7 EndpointNode (12 fields)

```java
public record EndpointNode(
    String endpointId,                  // canonical: "endpoint.<provider>.<dev>.<ep>"
    String deviceId,
    String alias,
    String displayName,
    EndpointKind kind,                  // enum: SWITCH, SENSOR, etc.
    String roomId,
    String zoneId,
    List<CapabilityNode> capabilities,
    EndpointTraits traits,
    EndpointHealth health,              // NOT persisted in structural table — from endpoint_health
    ProviderEndpointRef providerRef,
    EndpointMetadata metadata
) {}
```

**Critical:** When hydrating `EndpointNode` from normalized tables, `EndpointNode.health`
is read from the `endpoint_health` table, NOT from `endpoints`. If no row exists in
`endpoint_health`, use:
```java
new EndpointHealth(HealthStatus.UNKNOWN, null, "no durable endpoint health row")
```

### 2.8 CapabilityNode (4 fields)

```java
public record CapabilityNode(
    String capabilityId,
    String name,
    CapabilityKind kind,
    CapabilityTraits traits
) {}
```

### 2.9 TopologySpatialRelation (10 fields)

```java
public record TopologySpatialRelation(
    String relationId,
    TopologySpatialRelationKind kind,   // enum: LOCATED_IN
    TopologySpatialSubject subject,     // subject.type() + subject.id()
    TopologySpatialTarget target,       // target.type() + target.id()
    boolean primary,
    RelationConfidence confidence,      // enum: CONFIGURED, CONFIRMED, etc.
    SpatialRelationSource source,       // enum: MANUAL, PROVIDER, etc.
    ProviderSpatialRef providerRef,     // nullable
    Instant observedAt,
    Map<String, String> metadata
) {}
// TopologySpatialSubject: record(TopologySpatialEntityType type, String id)
// TopologySpatialTarget:  record(TopologySpatialEntityType type, String id)
// Example from tests:
// new TopologySpatialRelation("rel-1", TopologySpatialRelationKind.LOCATED_IN,
//     new TopologySpatialSubject(TopologySpatialEntityType.DEVICE, "device.tuya.abc"),
//     new TopologySpatialTarget(TopologySpatialEntityType.ROOM, "room.kitchen"),
//     true, RelationConfidence.CONFIGURED, SpatialRelationSource.MANUAL, null,
//     Instant.now(), Map.of())
```

### 2.10 BaseTopologySnapshot (4 fields)

```java
public record BaseTopologySnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    HabitatBaseTopology topology,
    Instant capturedAt
) {}
// Invariant: habitatId.equals(topology.habitatId()) must be true
// Invariant: topologyVersion.equals(topology.topologyVersion()) must be true
```

---

## 3. Service constructors (exact)

### 3.1 DefaultTopologyMaterializationService (5 args)

```java
public DefaultTopologyMaterializationService(
    BaseTopologyService baseTopologyService,
    TopologyMaterializationStatePort statePort,
    Predicate<String> admittedAdapterPredicate,  // ← arg 3: use adapterInstanceId -> true for seed
    MaterializationDecisionReplayPort replayPort,
    Clock clock
)
// From test fixture:
// new DefaultTopologyMaterializationService(service, repository, adapterInstanceId -> true, repository, clock)
```

### 3.2 BaseTopologyService (3-arg canonical constructor)

```java
public BaseTopologyService(
    BaseTopologyRepository repository,
    EndpointHealthWritePort healthWritePort,
    Clock clock
)
```

### 3.3 CoreSnapshotQueryService (2 args)

```java
public CoreSnapshotQueryService(CoreSnapshotReadPort readPort, Clock clock)
```

---

## 4. Current code surface — what the SQLite adapters replace

### 4.1 H2 save() pattern (current — do NOT copy this logic)

```java
// H2 — single JSON blob write, no normalized tables
jdbcTemplate.update("""
    MERGE INTO topology_snapshots (habitat_id, topology_version, topology_json, captured_at)
    KEY(habitat_id) VALUES (?, ?, ?, ?)
    """, habitatId, version, topologyJson, capturedAt);
```

The SQLite adapter does the opposite: normalized rows first, `topology_json` snapshot last
as a derived compatibility record.

### 4.2 H2 findRoom() pattern (current — do NOT copy this)

```java
// H2 — deserializes full blob for every single entity query
public Optional<RoomNode> findRoom(String habitatId, String roomId) {
    return findByHabitatId(habitatId)        // ← full CLOB deserialize
        .map(t -> t.rooms().stream()
            .filter(r -> r.roomId().equals(roomId))
            .findFirst().orElse(null));
}
```

The SQLite adapter replaces this with direct table queries (see §7).

### 4.3 InMemoryBaseTopologyRepository @Repository (remove this)

```java
@Repository   // ← REMOVE THIS ANNOTATION only; keep the class
public class InMemoryBaseTopologyRepository implements BaseTopologyRepository
```

---

## 5. V4 Flyway migration — exact DDL

File: `src/main/resources/db/migration/V4__sc_c_normalized_topology_persistence.sql`

**Schema note:** `zone_id` in `devices` and `endpoints` is `TEXT NOT NULL` per current
model requirement. The domain model `DeviceNode.zoneId` is required. Empty string `""`
is valid for unplaced devices (the domain allows this via existing tests).

**FK rule for endpoint_health and device_states:** These tables MUST NOT have FK constraints
pointing to `endpoints` or `devices`. They have independent lifecycle from structural rows —
an endpoint can have health data before or after the endpoint row exists structurally. The
FK to `habitats` is sufficient.

```sql
-- ── Topology version (one row per habitat) ───────────────────────────────────
CREATE TABLE IF NOT EXISTS topology_versions (
  habitat_id    TEXT PRIMARY KEY,
  version_value TEXT NOT NULL,
  updated_at_ms INTEGER NOT NULL,
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

-- ── Topology metadata (one row per habitat) ──────────────────────────────────
CREATE TABLE IF NOT EXISTS topology_metadata (
  habitat_id       TEXT PRIMARY KEY,
  schema_version   TEXT NOT NULL,
  last_modified_ms INTEGER NOT NULL,
  source           TEXT NOT NULL,
  checksum         TEXT,
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

-- ── Rooms ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS rooms (
  habitat_id  TEXT NOT NULL,
  room_id     TEXT NOT NULL,
  room_name   TEXT NOT NULL,
  traits_json TEXT NOT NULL,
  PRIMARY KEY (habitat_id, room_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

-- ── Zones ────────────────────────────────────────────────────────────────────
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

-- ── Devices ──────────────────────────────────────────────────────────────────
-- device_health_status/last_seen/details: DeviceNode.health embedded in structural aggregate
-- zone_id may be empty string "" for unplaced devices
CREATE TABLE IF NOT EXISTS devices (
  habitat_id           TEXT NOT NULL,
  device_id            TEXT NOT NULL,
  alias                TEXT NOT NULL,
  display_name         TEXT NOT NULL,
  room_id              TEXT NOT NULL,
  zone_id              TEXT NOT NULL,
  kind                 TEXT NOT NULL,
  provider             TEXT NOT NULL,
  traits_json          TEXT NOT NULL,
  device_health_status TEXT NOT NULL,
  device_health_last_seen_ms INTEGER,
  device_health_details TEXT,
  provider_ref_json    TEXT NOT NULL,
  PRIMARY KEY (habitat_id, device_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id),
  FOREIGN KEY (habitat_id, room_id) REFERENCES rooms(habitat_id, room_id)
  -- NO FK to zones: zone_id may be "" (unplaced)
);

CREATE INDEX IF NOT EXISTS ix_devices_room
  ON devices(habitat_id, room_id);
CREATE INDEX IF NOT EXISTS ix_devices_zone
  ON devices(habitat_id, zone_id) WHERE zone_id != '';

-- ── Endpoints ────────────────────────────────────────────────────────────────
-- EndpointNode.health is NOT here — it lives in endpoint_health table
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
  -- NO FK to rooms/zones: same reason as devices
);

CREATE INDEX IF NOT EXISTS ix_endpoints_device
  ON endpoints(habitat_id, device_id);
CREATE INDEX IF NOT EXISTS ix_endpoints_room
  ON endpoints(habitat_id, room_id);

-- ── Capabilities ─────────────────────────────────────────────────────────────
-- owner_kind IN ('DEVICE','ENDPOINT'); owner_id is device_id or endpoint_id
-- capability_id MUST be globally unique within a habitat.
-- owner_kind / owner_id are ownership metadata, not part of capability identity.
CREATE TABLE IF NOT EXISTS capabilities (
  habitat_id   TEXT NOT NULL,
  capability_id TEXT NOT NULL,
  owner_kind   TEXT NOT NULL CHECK(owner_kind IN ('DEVICE','ENDPOINT')),
  owner_id     TEXT NOT NULL,
  name         TEXT NOT NULL,
  kind         TEXT NOT NULL,
  traits_json  TEXT NOT NULL,
  PRIMARY KEY (habitat_id, capability_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

CREATE INDEX IF NOT EXISTS ix_capabilities_owner
  ON capabilities(habitat_id, owner_kind, owner_id);

-- ── Topology spatial relations ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS topology_spatial_relations (
  habitat_id              TEXT NOT NULL,
  relation_id             TEXT NOT NULL,
  kind                    TEXT NOT NULL,
  subject_type            TEXT NOT NULL,
  subject_id              TEXT NOT NULL,
  target_type             TEXT NOT NULL,
  target_id               TEXT NOT NULL,
  is_primary              INTEGER NOT NULL CHECK(is_primary IN (0,1)),
  confidence              TEXT NOT NULL,
  source                  TEXT NOT NULL,
  provider_spatial_ref_json TEXT,
  observed_at_ms          INTEGER,
  metadata_json           TEXT NOT NULL,
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

-- ── Compatibility snapshot (derived, NOT authoritative) ───────────────────────
CREATE TABLE IF NOT EXISTS topology_snapshots (
  habitat_id       TEXT PRIMARY KEY,
  topology_version TEXT NOT NULL,
  topology_json    TEXT NOT NULL,
  captured_at_ms   INTEGER NOT NULL,
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

-- ── Mutation records ────────────────────────────────────────────────────────
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

-- ── Device runtime state ──────────────────────────────────────────────────────
-- NO FK to devices — independent lifecycle
CREATE TABLE IF NOT EXISTS device_states (
  habitat_id    TEXT NOT NULL,
  device_id     TEXT NOT NULL,
  state_json    TEXT NOT NULL,
  updated_at_ms INTEGER NOT NULL,
  PRIMARY KEY (habitat_id, device_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

-- ── Endpoint health ───────────────────────────────────────────────────────────
-- NO FK to endpoints — independent lifecycle (MU-011 invariant)
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

-- ── Materialization decision replay ──────────────────────────────────────────
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
```

---

## 6. Normalized write pattern — mandatory implementation pattern

### 6.1 Delete ordering (FK-safe — child rows before parent rows)

```java
private void deleteStructuralRows(String habitatId) {
    // Must follow FK dependency order: children first
    jdbc.update("DELETE FROM topology_spatial_relations WHERE habitat_id = ?", habitatId);
    jdbc.update("DELETE FROM capabilities WHERE habitat_id = ?", habitatId);
    jdbc.update("DELETE FROM endpoints WHERE habitat_id = ?", habitatId);
    jdbc.update("DELETE FROM devices WHERE habitat_id = ?", habitatId);
    jdbc.update("DELETE FROM zones WHERE habitat_id = ?", habitatId);
    jdbc.update("DELETE FROM rooms WHERE habitat_id = ?", habitatId);
    // DO NOT delete: device_states, endpoint_health, materialization_decision_replay,
    //                mutation_records, topology_versions, topology_metadata
}
```

### 6.2 Insert ordering (FK-safe — parent rows before child rows)

```java
// Insert order inside save() transaction:
// 1. upsertTopologyVersion(topology)        → topology_versions
// 2. upsertTopologyMetadata(topology)       → topology_metadata
// 3. deleteStructuralRows(habitatId)        → see 6.1
// 4. insertRooms(topology)                  → rooms
// 5. insertZones(topology)                  → zones  (FK to rooms)
// 6. insertDevices(topology)                → devices (FK to rooms)
// 7. insertEndpoints(topology)              → endpoints (FK to devices)
// 8. insertCapabilities(topology)           → capabilities
// 9. insertSpatialRelations(topology)       → topology_spatial_relations
// 10. upsertCompatibilitySnapshot(topology) → topology_snapshots
```

### 6.3 Full save() body

```java
@Override
public void save(HabitatBaseTopology topology) {
    Objects.requireNonNull(topology, "topology is required");
    long nowMs = Instant.now(clock).toEpochMilli();
    String habitatId = topology.habitatId();
    String versionValue = topology.topologyVersion().value();

    txTemplate.executeWithoutResult(txStatus -> {
        // 1. Version and metadata
        jdbc.update("""
            INSERT INTO topology_versions (habitat_id, version_value, updated_at_ms)
            VALUES (?, ?, ?)
            ON CONFLICT(habitat_id) DO UPDATE SET
              version_value = excluded.version_value,
              updated_at_ms = excluded.updated_at_ms
            """, habitatId, versionValue, nowMs);

        jdbc.update("""
            INSERT INTO topology_metadata
              (habitat_id, schema_version, last_modified_ms, source, checksum)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(habitat_id) DO UPDATE SET
              schema_version   = excluded.schema_version,
              last_modified_ms = excluded.last_modified_ms,
              source           = excluded.source,
              checksum         = excluded.checksum
            """,
            habitatId,
            topology.metadata().schemaVersion(),
            topology.metadata().lastModified().toEpochMilli(),
            topology.metadata().source(),
            topology.metadata().checksum());

        // 2. Delete old structural rows (FK-safe order)
        deleteStructuralRows(habitatId);

        // 3. Insert rooms
        for (RoomNode room : topology.rooms()) {
            jdbc.update("""
                INSERT INTO rooms (habitat_id, room_id, room_name, traits_json)
                VALUES (?, ?, ?, ?)
                """,
                habitatId, room.roomId(), room.roomName(), toJson(room.traits()));
        }

        // 4. Insert zones (FK to rooms)
        for (ZoneNode zone : topology.zones()) {
            jdbc.update("""
                INSERT INTO zones (habitat_id, zone_id, zone_name, room_id, traits_json)
                VALUES (?, ?, ?, ?, ?)
                """,
                habitatId, zone.zoneId(), zone.zoneName(), zone.roomId(), toJson(zone.traits()));
        }

        // 5. Insert devices (FK to rooms; zone_id may be "")
        for (DeviceNode device : topology.devices()) {
            jdbc.update("""
                INSERT INTO devices (habitat_id, device_id, alias, display_name,
                  room_id, zone_id, kind, provider, traits_json,
                  device_health_status, device_health_last_seen_ms, device_health_details,
                  provider_ref_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                habitatId, device.deviceId(), device.alias(), device.displayName(),
                device.roomId(), device.zoneId(),
                device.kind().name(), device.provider().name(),
                toJson(device.traits()),
                device.health().status().name(),
                device.health().lastSeenAt() != null
                    ? device.health().lastSeenAt().toEpochMilli() : null,
                device.health().details(),
                toJson(device.providerRef()));
        }

        // 6. Insert endpoints (FK to devices)
        for (EndpointNode endpoint : topology.endpoints()) {
            jdbc.update("""
                INSERT INTO endpoints (habitat_id, endpoint_id, device_id,
                  alias, display_name, kind, room_id, zone_id,
                  traits_json, provider_ref_json, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                habitatId, endpoint.endpointId(), endpoint.deviceId(),
                endpoint.alias(), endpoint.displayName(), endpoint.kind().name(),
                endpoint.roomId(), endpoint.zoneId(),
                toJson(endpoint.traits()),
                toJson(endpoint.providerRef()),
                toJson(endpoint.metadata()));
            // NOTE: endpoint.health() is NOT written here — it lives in endpoint_health
        }

        // 7. Insert capabilities (device and endpoint capabilities)
        for (DeviceNode device : topology.devices()) {
            for (CapabilityNode cap : device.deviceCapabilities()) {
                jdbc.update("""
                    INSERT INTO capabilities
                      (habitat_id, capability_id, owner_kind, owner_id, name, kind, traits_json)
                    VALUES (?, ?, 'DEVICE', ?, ?, ?, ?)
                    """,
                    habitatId, cap.capabilityId(), device.deviceId(),
                    cap.name(), cap.kind().name(), toJson(cap.traits()));
            }
        }
        for (EndpointNode endpoint : topology.endpoints()) {
            for (CapabilityNode cap : endpoint.capabilities()) {
                jdbc.update("""
                    INSERT INTO capabilities
                      (habitat_id, capability_id, owner_kind, owner_id, name, kind, traits_json)
                    VALUES (?, ?, 'ENDPOINT', ?, ?, ?, ?)
                    """,
                    habitatId, cap.capabilityId(), endpoint.endpointId(),
                    cap.name(), cap.kind().name(), toJson(cap.traits()));
            }
        }

        // 8. Insert spatial relations
        for (TopologySpatialRelation rel : topology.spatialRelations()) {
            jdbc.update("""
                INSERT INTO topology_spatial_relations
                  (habitat_id, relation_id, kind, subject_type, subject_id,
                   target_type, target_id, is_primary, confidence, source,
                   provider_spatial_ref_json, observed_at_ms, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                habitatId, rel.relationId(), rel.kind().name(),
                rel.subject().type().name(), rel.subject().id(),
                rel.target().type().name(), rel.target().id(),
                rel.primary() ? 1 : 0,
                rel.confidence().name(), rel.source().name(),
                rel.providerRef() != null ? toJson(rel.providerRef()) : null,
                rel.observedAt() != null ? rel.observedAt().toEpochMilli() : null,
                rel.metadata() != null ? toJson(rel.metadata()) : "{}");
        }

        // 9. Upsert compatibility snapshot (derived — not the authority)
        String topologyJson = toJson(topology);
        jdbc.update("""
            INSERT INTO topology_snapshots
              (habitat_id, topology_version, topology_json, captured_at_ms)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(habitat_id) DO UPDATE SET
              topology_version = excluded.topology_version,
              topology_json    = excluded.topology_json,
              captured_at_ms   = excluded.captured_at_ms
            """, habitatId, versionValue, topologyJson, nowMs);
    });
}
```

---

## 7. Normalized hydration pattern — mandatory implementation pattern

### 7.1 findByHabitatId — assembles from normalized rows

```java
@Override
public Optional<HabitatBaseTopology> findByHabitatId(String habitatId) {
    // Step 1: version (empty = no topology)
    List<TopologyVersion> versions = jdbc.query(
        "SELECT version_value FROM topology_versions WHERE habitat_id = ?",
        (rs, n) -> TopologyVersion.habitatVersion(habitatId, Long.parseLong(rs.getString("version_value"))),
        habitatId);
    if (versions.isEmpty()) return Optional.empty();
    TopologyVersion version = versions.get(0);

    // Step 2: metadata
    List<TopologyMetadata> metas = jdbc.query(
        "SELECT schema_version, last_modified_ms, source, checksum FROM topology_metadata WHERE habitat_id = ?",
        (rs, n) -> new TopologyMetadata(
            rs.getString("schema_version"),
            Instant.ofEpochMilli(rs.getLong("last_modified_ms")),
            rs.getString("source"),
            rs.getString("checksum")),
        habitatId);
    TopologyMetadata metadata = metas.isEmpty()
        ? new TopologyMetadata("base-topology.seed.v1", Instant.now(clock), "SC-C", null)
        : metas.get(0);

    // Step 3: rooms
    List<RoomRow> roomRows = loadRoomRows(habitatId);
    // Step 4: zones
    List<ZoneRow> zoneRows = loadZoneRows(habitatId);
    // Step 5: devices
    List<DeviceRow> deviceRows = loadDeviceRows(habitatId);
    // Step 6: endpoints
    List<EndpointRow> endpointRows = loadEndpointRows(habitatId);
    // Step 7: capabilities grouped by owner
    Map<String, List<CapabilityNode>> capsByOwner = loadCapabilitiesGroupedByOwner(habitatId);
    // Step 8: endpoint health from dedicated table
    Map<String, EndpointHealth> healthByEndpointId = loadEndpointHealthMap(habitatId);
    // Step 9: spatial relations
    List<TopologySpatialRelation> relations = loadSpatialRelations(habitatId);

    // Assemble RoomNodes — list fields are derived from the relational structure
    List<RoomNode> rooms = roomRows.stream().map(row -> new RoomNode(
        row.roomId, row.roomName,
        zoneRows.stream().filter(z -> row.roomId.equals(z.roomId))
            .map(z -> z.zoneId).collect(toList()),
        deviceRows.stream().filter(d -> row.roomId.equals(d.roomId))
            .map(d -> d.deviceId).collect(toList()),
        endpointRows.stream().filter(e -> row.roomId.equals(e.roomId))
            .map(e -> e.endpointId).collect(toList()),
        row.traits
    )).collect(toList());

    // Assemble ZoneNodes
    List<ZoneNode> zones = zoneRows.stream().map(row -> new ZoneNode(
        row.zoneId, row.zoneName, row.roomId,
        deviceRows.stream().filter(d -> row.zoneId.equals(d.zoneId))
            .map(d -> d.deviceId).collect(toList()),
        endpointRows.stream().filter(e -> row.zoneId.equals(e.zoneId))
            .map(e -> e.endpointId).collect(toList()),
        row.traits
    )).collect(toList());

    // Assemble DeviceNodes (with device capabilities and embedded health)
    List<DeviceNode> devices = deviceRows.stream().map(row -> new DeviceNode(
        row.deviceId, row.alias, row.displayName, row.roomId, row.zoneId,
        row.kind, row.provider,
        endpointRows.stream().filter(e -> row.deviceId.equals(e.deviceId))
            .map(e -> e.endpointId).collect(toList()),
        capsByOwner.getOrDefault("DEVICE:" + row.deviceId, List.of()),
        row.traits,
        row.health,           // DeviceHealth from devices table
        row.providerRef
    )).collect(toList());

    // Assemble EndpointNodes (health from endpoint_health, not structural table)
    EndpointHealth unknownHealth = new EndpointHealth(HealthStatus.UNKNOWN, null,
        "no durable endpoint health row");
    List<EndpointNode> endpoints = endpointRows.stream().map(row -> new EndpointNode(
        row.endpointId, row.deviceId, row.alias, row.displayName, row.kind,
        row.roomId, row.zoneId,
        capsByOwner.getOrDefault("ENDPOINT:" + row.endpointId, List.of()),
        row.traits,
        healthByEndpointId.getOrDefault(row.endpointId, unknownHealth),  // ← from endpoint_health
        row.providerRef,
        row.metadata
    )).collect(toList());

    return Optional.of(new HabitatBaseTopology(
        habitatId, version, rooms, zones, devices, endpoints, relations, metadata));
}
```

### 7.2 Individual entity queries — use table directly

```java
@Override
public Optional<RoomNode> findRoom(String habitatId, String roomId) {
    // Query normalized table — no blob deserialization
    List<RoomRow> rows = loadRoomRows(habitatId, roomId);
    if (rows.isEmpty()) return Optional.empty();
    RoomRow row = rows.get(0);
    // Build list fields from related tables for this single room
    List<String> zoneIds = jdbc.queryForList(
        "SELECT zone_id FROM zones WHERE habitat_id = ? AND room_id = ?",
        String.class, habitatId, roomId);
    List<String> deviceIds = jdbc.queryForList(
        "SELECT device_id FROM devices WHERE habitat_id = ? AND room_id = ?",
        String.class, habitatId, roomId);
    List<String> endpointIds = jdbc.queryForList(
        "SELECT endpoint_id FROM endpoints WHERE habitat_id = ? AND room_id = ?",
        String.class, habitatId, roomId);
    return Optional.of(new RoomNode(row.roomId, row.roomName, zoneIds, deviceIds, endpointIds, row.traits));
}

@Override
public List<DeviceNode> findLocatedDevices(String habitatId, String roomOrZoneId) {
    // Find DEVICE subjects whose LOCATED_IN target is this room or zone
    List<String> deviceIds = jdbc.queryForList("""
        SELECT subject_id FROM topology_spatial_relations
        WHERE habitat_id = ? AND kind = 'LOCATED_IN'
          AND subject_type = 'DEVICE' AND target_id = ?
        """, String.class, habitatId, roomOrZoneId);
    if (deviceIds.isEmpty()) return List.of();
    // Fetch device rows by those IDs
    return deviceIds.stream()
        .map(id -> loadDeviceRow(habitatId, id))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(row -> assembleDeviceNode(row, habitatId))
        .collect(toList());
}

@Override
public Optional<TopologySpatialRelation> resolvePrimaryPlacement(
    String habitatId, TopologySpatialEntityType type, String id) {
    List<TopologySpatialRelation> results = jdbc.query("""
        SELECT * FROM topology_spatial_relations
        WHERE habitat_id = ? AND subject_type = ? AND subject_id = ? AND is_primary = 1
        """,
        (rs, n) -> toRelation(rs),
        habitatId, type.name(), id);
    return results.stream().findFirst();
}
```

---

## 8. Spring wiring — TopologyPersistenceConfiguration (exact)

```java
@Configuration
public class TopologyPersistenceConfiguration {

    @Bean @DependsOn("flyway") @Primary
    public SQLiteBaseTopologyRepository sqliteBaseTopologyRepository(
        DataSource dataSource, ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate, Clock clock) {
        return new SQLiteBaseTopologyRepository(
            dataSource, objectMapper, transactionTemplate, clock);
    }

    @Bean @Primary
    public BaseTopologyRepository baseTopologyRepository(
        SQLiteBaseTopologyRepository r) { return r; }

    @Bean @Primary
    public CoreSnapshotReadPort coreSnapshotReadPort(
        SQLiteBaseTopologyRepository r) { return r; }

    @Bean @DependsOn("flyway") @Primary
    public SQLiteEndpointHealthRepository sqliteEndpointHealthRepository(
        DataSource dataSource, Clock clock) {
        return new SQLiteEndpointHealthRepository(dataSource, clock);
    }

    @Bean @Primary
    public EndpointHealthWritePort endpointHealthWritePort(
        SQLiteEndpointHealthRepository r) { return r; }

    @Bean @DependsOn("flyway") @Primary
    public SQLiteTopologyMaterializationStateRepository sqliteTopologyMaterializationStateRepository(
        DataSource dataSource, ObjectMapper objectMapper,
        SQLiteBaseTopologyRepository baseRepo,
        SQLiteEndpointHealthRepository healthRepo, Clock clock) {
        return new SQLiteTopologyMaterializationStateRepository(
            dataSource, objectMapper, baseRepo, healthRepo, clock);
    }

    @Bean @Primary
    public TopologyMaterializationStatePort topologyMaterializationStatePort(
        SQLiteTopologyMaterializationStateRepository r) { return r; }

    @Bean @DependsOn("flyway") @Primary
    public SQLiteMaterializationDecisionReplayRepository
    sqliteMaterializationDecisionReplayRepository(
        DataSource dataSource, ObjectMapper objectMapper) {
        return new SQLiteMaterializationDecisionReplayRepository(dataSource, objectMapper);
    }

    @Bean @Primary
    public MaterializationDecisionReplayPort materializationDecisionReplayPort(
        SQLiteMaterializationDecisionReplayRepository r) { return r; }

    @Bean
    public BaseTopologyService baseTopologyService(
        BaseTopologyRepository repository,
        EndpointHealthWritePort healthWritePort,
        Clock clock) {
        // 3-arg constructor
        return new BaseTopologyService(repository, healthWritePort, clock);
    }

    @Bean
    public CoreSnapshotQueryService coreSnapshotQueryService(
        CoreSnapshotReadPort readPort, Clock clock) {
        return new CoreSnapshotQueryService(readPort, clock);
    }

    @Bean
    public TopologyMaterializationService topologyMaterializationService(
        BaseTopologyService baseTopologyService,
        TopologyMaterializationStatePort statePort,
        MaterializationDecisionReplayPort replayPort,
        Clock clock) {
        // DefaultTopologyMaterializationService 5-arg constructor:
        // (baseTopologyService, statePort, admittedAdapterPredicate, replayPort, clock)
        return new DefaultTopologyMaterializationService(
            baseTopologyService, statePort,
            adapterInstanceId -> true,    // seed predicate — admits all adapters
            replayPort, clock);
    }
}
```

---

## 9. Test helper patterns — for new SQLite topology tests

### 9.1 migratedSQLiteDataSource helper

```java
// In new test class — reuse pattern from TemporalEngineReviewBlockersTest
private DataSource migratedDataSource(String path) {
    SQLiteDataSource delegate = new SQLiteDataSource();
    delegate.setUrl("jdbc:sqlite:" + path);
    DataSource ds = new PerConnectionPragmaDataSource(delegate);
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    return ds;
}
```

### 9.2 buildSeedTopology helper — compile-safe fixture pattern

```java
private HabitatBaseTopology buildSeedTopology(String habitatId) {
    TopologyVersion version = TopologyVersion.habitatVersion(habitatId, 1L);
    TopologyMetadata metadata = new TopologyMetadata("base-topology.seed.v1", Instant.now(), "SC-C", null);

    RoomNode room = new RoomNode(
        "room.kitchen", "Kitchen",
        List.of("zone.kitchen.worktop"),   // zoneIds
        List.of("device.tuya.light-1"),    // deviceIds
        List.of("endpoint.tuya.light-1.switch"), // endpointIds
        new RoomTraits(false, false)
    );
    ZoneNode zone = new ZoneNode(
        "zone.kitchen.worktop", "Kitchen Worktop", "room.kitchen",
        List.of("device.tuya.light-1"), List.of("endpoint.tuya.light-1.switch"),
        new ZoneTraits(true)
    );
    CapabilityNode endpointCap = new CapabilityNode(
        "cap.tuya.light-1.switch.power", "Power", CapabilityKind.TOGGLE,
        new CapabilityTraits(true, true, false)
    );
    DeviceNode device = new DeviceNode(
        "device.tuya.light-1", "Kitchen Light", "Kitchen Light",
        "room.kitchen", "zone.kitchen.worktop",
        DeviceKind.LIGHT, DeviceProvider.TUYA,
        List.of("endpoint.tuya.light-1.switch"),
        List.of(),                         // deviceCapabilities intentionally empty;
                                           // capability_id is globally unique per habitat
        new DeviceTraits(false, true, true),
        new DeviceHealth(HealthStatus.HEALTHY, Instant.now(), null),
        new ProviderDeviceRef("tuya", "light-1", Map.of())
    );
    EndpointNode endpoint = new EndpointNode(
        "endpoint.tuya.light-1.switch", "device.tuya.light-1",
        "Switch", "Switch", EndpointKind.SWITCH_CHANNEL,
        "room.kitchen", "zone.kitchen.worktop",
        List.of(endpointCap),              // endpoint capabilities
        new EndpointTraits(true, true, true, true, false, false),
        new EndpointHealth(HealthStatus.UNKNOWN, null, "no durable endpoint health row"),
        new ProviderEndpointRef("tuya", "light-1", "switch", Map.of()),
        EndpointMetadata.empty()
    );
    TopologySpatialRelation relation = new TopologySpatialRelation(
        "rel.device.tuya.light-1.room.kitchen",
        TopologySpatialRelationKind.LOCATED_IN,
        new TopologySpatialSubject(TopologySpatialEntityType.DEVICE, "device.tuya.light-1"),
        new TopologySpatialTarget(TopologySpatialEntityType.ROOM, "room.kitchen"),
        true, RelationConfidence.CONFIGURED, SpatialRelationSource.MANUAL,
        null, Instant.now(), Map.of()
    );
    return new HabitatBaseTopology(habitatId, version,
        List.of(room), List.of(zone), List.of(device), List.of(endpoint),
        List.of(relation), metadata);
}
```

### 9.3 T: roomsPersistAsRowsNotOnlyTopologyJson (required scenario pattern)

```java
@Test
void roomsPersistAsRowsNotOnlyTopologyJson(@TempDir Path tmp) {
    DataSource ds = migratedDataSource(tmp.resolve("rooms-rows.sqlite").toString());
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    SQLiteBaseTopologyRepository repo = new SQLiteBaseTopologyRepository(
        ds, new ObjectMapper().findAndRegisterModules(),
        new TransactionTemplate(new DataSourceTransactionManager(ds)), Clock.systemUTC());

    HabitatBaseTopology topology = buildSeedTopology("habitat-001");
    repo.save(topology);

    // Must have rows in normalized tables — not just topology_json
    Integer roomCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM rooms WHERE habitat_id = 'habitat-001'", Integer.class);
    assertThat(roomCount).isEqualTo(1);

    Integer zoneCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM zones WHERE habitat_id = 'habitat-001'", Integer.class);
    assertThat(zoneCount).isEqualTo(1);

    Integer deviceCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM devices WHERE habitat_id = 'habitat-001'", Integer.class);
    assertThat(deviceCount).isEqualTo(1);

    Integer capCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM capabilities WHERE habitat_id = 'habitat-001'", Integer.class);
    assertThat(capCount).isGreaterThan(0);

    // topology_json still exists (compatibility snapshot)
    Integer snapCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM topology_snapshots WHERE habitat_id = 'habitat-001'", Integer.class);
    assertThat(snapCount).isEqualTo(1);
}
```

### 9.4 T: topologyJsonIsCompatibilitySnapshotNotAuthority (required scenario pattern)

```java
@Test
void topologyJsonIsCompatibilitySnapshotNotAuthority(@TempDir Path tmp) {
    DataSource ds = migratedDataSource(tmp.resolve("json-compat.sqlite").toString());
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    SQLiteBaseTopologyRepository repo = new SQLiteBaseTopologyRepository(
        ds, new ObjectMapper().findAndRegisterModules(),
        new TransactionTemplate(new DataSourceTransactionManager(ds)), Clock.systemUTC());

    HabitatBaseTopology topology = buildSeedTopology("habitat-001");
    repo.save(topology);

    // Corrupt the topology_json blob to prove it is not the authority
    jdbc.update(
        "UPDATE topology_snapshots SET topology_json = 'CORRUPTED' WHERE habitat_id = 'habitat-001'");

    // findByHabitatId must still return correct topology from normalized rows
    Optional<HabitatBaseTopology> loaded = repo.findByHabitatId("habitat-001");
    assertThat(loaded).isPresent();
    assertThat(loaded.get().rooms()).hasSize(1);
    assertThat(loaded.get().rooms().get(0).roomId()).isEqualTo("room.kitchen");
}
```

---

## 10. Stop conditions

```text
1. RoomTraits, ZoneTraits, DeviceTraits, EndpointTraits, CapabilityTraits constructors
   have fields not visible in this context — read the actual record declaration
   before constructing them.

2. TopologyMaterializationStatePort.saveEndpointHealth: delegate to
   SQLiteEndpointHealthRepository, do NOT write endpoint_health inside
   SQLiteBaseTopologyRepository.save().

3. DefaultTopologyMaterializationService requires ScLedgerWritePort in some tests
   (the H2 test uses H2BaseTopologyRepository which implements ScLedgerWritePort).
   In TopologyPersistenceConfiguration the materializer does NOT get a ScLedgerWritePort
   unless the domain class requires it. Check the constructor — if it requires ledger,
   inject the scLedgerWritePort bean from TemporalEngineConfiguration.

4. If V4 migration fails because habitats table has no row for "habitat-001",
   add: INSERT OR IGNORE INTO habitats(habitat_id) VALUES ('habitat-001');
   at the beginning of the V4 migration.

5. Any of the 139 existing tests fail after adding TopologyPersistenceConfiguration —
   stop and report; do not delete tests.
```
