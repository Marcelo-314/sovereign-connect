# codex-prompt.md — MU-017 Normalized Topology SQLite/Flyway Persistence

Package version: v0.2.1  
Target: MU-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001  
Operational slot: MU-017

---

## Role

You are implementing MU-017 for Sovereign Connect Core (`SC-C`).

Your task is to replace seed/H2/in-memory topology persistence as the production path with normalized SQLite/Flyway topology persistence.

Read first:

```text
docs/mir/mir-017/context.md
docs/mir/mir-017/code-surface-audit.md
docs/mir/mir-017/MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001.md
docs/mir/mir-017/acceptance-map.md
```

Do not implement outside the stated scope.

---

## Primary implementation tasks

### 1. Add Flyway V4

Create exactly:

```text
src/main/resources/db/migration/V4__sc_c_normalized_topology_persistence.sql
```

It must create normalized SQLite tables for:

```text
topology_versions
topology_metadata
rooms
zones
devices
endpoints
capabilities
topology_spatial_relations
topology_snapshots        // compatibility/read model only
mutation_records
device_states
endpoint_health
materialization_decision_replay
```

Use the schema shape and FK/index guidance from `context.md`.

Capability identity rule:

```text
capability_id MUST be globally unique within a habitat.
owner_kind / owner_id are ownership metadata, not part of the primary key.
Do not reuse the same capability_id for device-owned and endpoint-owned capabilities.
```

### 2. Implement SQLite topology repositories

Implement:

```text
SQLiteBaseTopologyRepository
SQLiteEndpointHealthRepository
SQLiteTopologyMaterializationStateRepository
SQLiteMaterializationDecisionReplayRepository
```

`SQLiteBaseTopologyRepository` must implement `BaseTopologyRepository` and `CoreSnapshotReadPort`.

### 3. Implement normalized write pattern

`SQLiteBaseTopologyRepository.save(HabitatBaseTopology)` must:

```text
- run in a single transaction;
- UPSERT topology_versions;
- UPSERT topology_metadata;
- delete structural rows in exact FK-safe order;
- insert rooms, zones, devices, endpoints, capabilities, spatial relations;
- UPSERT topology_snapshots as derived compatibility snapshot;
- never delete/write endpoint_health or device_states from structural save.
```

Delete order:

```text
1. topology_spatial_relations
2. capabilities
3. endpoints
4. devices
5. zones
6. rooms
```

Insert order:

```text
1. topology_versions
2. topology_metadata
3. rooms
4. zones
5. devices
6. endpoints
7. capabilities
8. topology_spatial_relations
9. topology_snapshots
```

### 4. Implement normalized hydration pattern

`findByHabitatId(...)`, `findTopology(...)` and `findSnapshot(...)` must hydrate `HabitatBaseTopology` from normalized tables, not from `topology_json`.

`findRoom`, `findZone`, `findSpatialRelation`, `findSpatialRelationsBySubject`, `findLocatedDevices`, `findLocatedEndpoints` and `resolvePrimaryPlacement` must query normalized tables and must not require full `topology_json` deserialization.

### 5. Preserve health/state separation

Structural save must not overwrite durable endpoint health.

`EndpointHealthWritePort.saveEndpointHealth(...)` and `TopologyMaterializationStatePort.saveEndpointHealth(...)` must write `endpoint_health` explicitly.

`CoreSnapshotReadPort.findEndpointHealth(...)` must read `endpoint_health`.

### 6. Wire Spring production context

Add:

```text
TopologyPersistenceConfiguration
```

It must wire SQLite topology repositories and services as production beans:

```text
BaseTopologyRepository
CoreSnapshotReadPort
EndpointHealthWritePort
TopologyMaterializationStatePort
MaterializationDecisionReplayPort
BaseTopologyService
CoreSnapshotQueryService
TopologyMaterializationService
```

Make SQLite implementations `@Primary` or explicit bean-returned primary paths.

Remove or profile-gate `InMemoryBaseTopologyRepository @Repository` so it is not production default.

Do not make `H2BaseTopologyRepository` production-selected.

### 7. Add/adjust tests

Add the tests listed in `context.md §10` and `acceptance-map.md`.

At minimum, verify:

```text
- V4 creates normalized tables;
- Spring context wires SQLite topology ports;
- H2/InMemory are not selected in production context;
- each topology entity persists as rows;
- hydration round-trips from normalized rows;
- individual queries do not require topology_json authority;
- structural save does not overwrite endpoint health;
- materialization decision replay uses SQLite;
- SQLiteScLedgerOutboxRepository has production-path coverage or H2 outbox tests are explicitly marked legacy.
```

### 8. Update implementation report

Fill:

```text
docs/mir/mir-017/implementation-report.md
```

Use `implementation-report-template.md` as the template.

The report must state:

```text
- final test count;
- whether mvn test passed;
- changed files;
- exact V4 migration name;
- normalized tables implemented;
- health/state separation evidence;
- topology_json authority inversion evidence;
- H2/InMemory confinement;
- OutboxLedgerStorageSeedTest / SQLiteScLedgerOutboxSeedTest disposition;
- Jackson shim disposition;
- working tree hygiene evidence.
```

---

## Hard constraints

Do not add:

```text
Kuzu / graph database
Neo4j / Apache AGE / PostgreSQL
SC-C north-facing facade
EIB / View Composer
SC-B runtime
NATS / JetStream
SC-D adapter runtime
ActionTemporalPayload
command dispatch
outbox dispatcher
```

Do not modify unrelated historical MIR folders.

Do not include `.idea`, `target/*.sqlite`, or duplicate `#U2014` artifacts in the commit.

---

## Expected branch and commit

Branch:

```text
feat/sc-c-mir-017-canonical-topology-sqlite-persistence
```

Commit:

```text
feat(sc-c): add normalized SQLite topology persistence
```
