# MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001

## Canonical Topology SQLite/Flyway Persistence

**Document ID:** MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001  
**Title:** Canonical Topology SQLite/Flyway Persistence  
**Version:** v0.1.1-draft  
**Status:** Draft  
**Date:** 2026-05-22  
**Corpus:** Sovereign Connect  
**Type:** MIR  
**Plane:** SC-C  
**Materialization Unit:** MU-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001  
**Operational Slot:** MU-017  
**Scope:** Industrialize SC-C canonical topology persistence by implementing normalized SQLite/Flyway production tables, Spring-wired SQLite topology repositories and compatibility snapshot support without adding north-facing exposure, EIB/VC, SC-B, SC-D or graph database infrastructure.

---

## Changelog v0.1.1-draft

Based on `v0.1.0-draft`, this version:

1. Replaces the hybrid aggregate snapshot profile with normalized canonical topology persistence v1.
2. Closes `OQ-017-001` in favor of normalized SQLite tables as production authority.
3. Reclassifies `topology_json` as derived compatibility snapshot / read model, not canonical production authority.
4. Adds the explicit non-goal of external graph database adoption in MU-017.
5. Adds acceptance criteria for normalized rooms, zones, devices, endpoints, capabilities and spatial relations.
6. Requires the execution package to specify exact normalized `save(...)` write pattern and hydration pattern.
7. Updates dependency on the Code Surface Audit to `CSA-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v0.1.3-draft`.
8. Marks any previous hybrid execution package as superseded before implementation.

Supersedes:

```text
MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v0.1.0-draft
```

---

## 0. Governance note

This MIR does not include Codex prompts or implementation context inline.

Execution assets MUST be produced separately under:

```text
docs/mir/mir-017/
  code-surface-audit.md
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report.md
```

This MIR authorizes a bounded implementation attempt only after the execution package is accepted.

---

## 1. Disposition

```text
MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v0.1.1-draft:
  Candidate MIR for MU-017.

Execution readiness:
  Requires accepted CSA v0.1.3 and execution package.
```

---

## 2. Background

MIR-016 industrialized the Temporal Runtime path with SQLite/Flyway, Spring-managed lifecycle, request idempotency, recovery gates, BLOB(16) temporal IDs and TemporalAct observation/application ports.

However, SC-C Base Topology persistence remains seed-grade:

```text
H2BaseTopologyRepository:
  inline H2 DDL;
  multiple persistence responsibilities;
  test/seed persistence only.

InMemoryBaseTopologyRepository:
  domain/test convenience adapter;
  unsafe if production-selected by Spring.

Topology services:
  BaseTopologyService, CoreSnapshotQueryService and
  DefaultTopologyMaterializationService are not wired as production Spring beans.

Flyway:
  no normalized topology persistence migration beyond habitats table.
```

The accepted production persistence direction requires normalized SQLite persistence for SC-C-owned topology state. Therefore MU-017 must not stop at `topology_json` persistence or secondary indexes. It must materialize normalized canonical topology persistence v1.

---

## 3. Depends on

```text
RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.5-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.16-draft
SYNC-SOV-SC-MU-BACKLOG-001 v0.1.17-draft
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.10-draft
PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001 v0.1.1-draft
ADR-SOV-SC-C-ID-STRATEGY-001 v0.1.1-draft
ADR-SOV-SC-C-STORAGE-TECH-001 v0.1.2-draft
NT-SOV-SC-C-LOCAL-FIRST-STORAGE-PROFILE-001 v0.1.0-draft
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001 v0.1.1-draft
SDD-SOV-SC-C-RECOVERY-001 v0.1.2-draft
SDD-SOV-SC-C-OUTBOX-LEDGER-001 v0.1.1-draft
MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001 v1.0.0-accepted
MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001 v1.0.0-accepted
MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 v1.0.0-accepted
MIR-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001 v1.0.0-accepted
MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001 v1.0.0-accepted
MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v1.0.0-accepted
CSA-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v0.1.3-draft
```

---

## 4. Related

```text
ADR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-draft
PDR-SOV-SC-C-NORTHBOUND-FACADE-001, planned
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001, downstream
PDR-SOV-SC-VIEW-COMPOSER-BOUNDARY-001 v0.1.2-draft, existing / to be reframed
SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001, downstream
SDD-SOV-SC-VIEW-COMPOSER-001 v0.1.3-draft, existing / to be reframed
Future ADR/PDR for graph read model, not in MU-017 scope
```

---

## 5. Materialization thesis

MU-017 materializes the production SQLite/Flyway persistence path for SC-C canonical topology using normalized canonical topology tables.

Canonical statement:

```text
Temporal Runtime persistence is already SQLite/Flyway-backed.
Base Topology persistence is not.
MU-017 closes the Base Topology persistence gap by implementing normalized
SQLite/Flyway topology persistence v1.
```

Authority statement:

```text
Normalized SQLite topology tables are authoritative after MU-017.

topology_json may remain as a compatibility snapshot / read model, but MUST NOT
be treated as the primary production authority for Base Topology.
```

MU-017 does not implement SC-C north-facing exposure, EIB, View Composer, SC-B, SC-D, command dispatch or external graph database infrastructure.

---

## 6. Goals

### G-017-001 — Add normalized Flyway topology migration

Add:

```text
V4__sc_c_normalized_topology_persistence.sql
```

It MUST create normalized SQLite tables for:

```text
topology_versions
mutation_records
rooms
zones
devices
endpoints
capabilities
topology_spatial_relations
provider_bindings / provider_refs, if required by current model
device_states
endpoint_health
materialization_decision_replay
topology_snapshots compatibility read model
```

It MUST be compatible with existing V1/V2/V3 migrations.

---

### G-017-002 — Implement normalized SQLite topology repositories

Required target surface:

```text
SQLiteBaseTopologyRepository
  BaseTopologyRepository

SQLiteCoreSnapshotReadRepository or SQLiteBaseTopologyRepository
  CoreSnapshotReadPort

SQLiteEndpointHealthRepository
  EndpointHealthWritePort

SQLiteTopologyMaterializationStateRepository
  TopologyMaterializationStatePort

SQLiteMaterializationDecisionReplayRepository
  MaterializationDecisionReplayPort
```

The implementation MAY choose cohesive repository naming if the implementation report justifies it, but it MUST NOT recreate the H2 God Object.

Topology repositories MUST NOT implement `ScLedgerWritePort` or `ScOutboxWritePort`.

---

### G-017-003 — Wire topology into Spring production context

Add:

```text
TopologyPersistenceConfiguration
```

It MUST expose production beans for:

```text
BaseTopologyService
CoreSnapshotQueryService
DefaultTopologyMaterializationService
BaseTopologyRepository
CoreSnapshotReadPort
EndpointHealthWritePort
TopologyMaterializationStatePort
MaterializationDecisionReplayPort
```

The production-selected implementations MUST be SQLite-backed.

---

### G-017-004 — Confine seed adapters

After MU-017:

```text
H2BaseTopologyRepository:
  legacy seed/test fixture only.

InMemoryBaseTopologyRepository:
  domain/unit test or dev fixture only.
```

Neither may be production-selected by default.

`InMemoryBaseTopologyRepository @Repository` must be removed or restricted to a non-production profile.

---

### G-017-005 — Implement normalized write pattern

The execution package MUST define the exact `save(HabitatBaseTopology)` write pattern.

Required semantics:

```text
1. Single transaction.
2. Upsert topology_versions.
3. Delete/replace normalized structural rows for the habitat in FK-safe order.
4. Insert rooms, zones, devices, endpoints, capabilities and spatial relations.
5. Preserve provider refs as metadata, not canonical identity.
6. Write topology_snapshots as derived compatibility read model.
7. Never overwrite endpoint_health or device_states as a structural side effect.
8. Never append ledger/outbox as a topology persistence side effect.
```

---

### G-017-006 — Implement normalized hydration pattern

The execution package MUST define how `HabitatBaseTopology` and `CoreSnapshot` are hydrated from normalized tables.

Required semantics:

```text
1. Read topologyVersion from topology_versions.
2. Read rooms.
3. Read zones.
4. Read devices.
5. Read endpoints.
6. Read capabilities.
7. Read topology_spatial_relations.
8. Read runtime state and health from dedicated tables.
9. Assemble aggregate records in memory.
10. Do not rely on topology_json as authority for normal query paths.
```

---

### G-017-007 — Preserve topology semantics

MU-017 MUST preserve:

```text
topologyVersion advancement only on accepted structural mutations;
state/health writes do not advance topologyVersion;
structural saves do not overwrite endpoint health;
CoreSnapshotReadPort observes persisted topology/state/health;
Room/Zone/LOCATED_IN queries remain valid;
materialization decision replay prevents duplicate semantic effects;
canonical IDs remain opaque;
provider refs remain metadata;
Base Topology remains separate from Effective View.
```

---

### G-017-008 — Preserve Temporal Runtime and outbox/ledger paths

MU-017 MUST NOT regress:

```text
TemporalAct SQLite persistence;
Temporal request idempotency;
Temporal engine locks;
Temporal recovery observation;
SQLiteScLedgerOutboxRepository;
V2/V3 migrations;
MIR-016 tests and invariants.
```

Ledger/outbox persistence remains under SC-C outbox/ledger semantics and the existing SQLite ledger/outbox adapter.

---

## 7. Non-goals

MU-017 MUST NOT implement:

```text
SC-C north-facing facade;
Effective Interaction Boundary;
View Composer;
SC-B runtime;
NATS / JetStream;
SC-D adapter manifest or runtime;
ActionTemporalPayload;
command dispatch;
outbox dispatcher;
external graph database;
Kuzu / Neo4j / Apache AGE / other graph engine adoption;
graph analytics read model;
projection / Effective View persistence;
Hub, SApp or Surface behavior.
```

---

## 8. Schema profile decisions

### D-017-001 — Normalized canonical topology persistence v1

MU-017 adopts normalized canonical topology persistence v1.

```text
Normalized SQLite tables are authoritative for production topology persistence.

topology_json MAY remain as compatibility snapshot / read model / recovery aid.

topology_json MUST NOT be the sole or primary production persistence mechanism.

CTI-style entity queries MUST read from normalized tables or from repository
methods backed by normalized tables.
```

### D-017-002 — No external graph DB in MU-017

MU-017 models topology as a graph-shaped relational schema inside SQLite.

```text
Rooms, zones, devices, endpoints and capabilities are canonical entity tables.
TopologySpatialRelation is the canonical relation/edge table.
SQLite remains the authoritative SC-C persistence store.
No external graph database or embedded graph engine is introduced in MU-017.
```

A future graph read model may be considered only as derived, rebuildable and non-authoritative.

### D-017-003 — Compatibility snapshot status

`topology_snapshots.topology_json` may remain for compatibility, diagnostics, test continuity or read-model reconstruction.

It is not the authoritative production persistence mechanism after MU-017.

---

## 9. Acceptance criteria

### AC-017-001 — Flyway V4 exists

`V4__sc_c_normalized_topology_persistence.sql` exists and creates normalized topology persistence tables.

### AC-017-002 — Flyway migration succeeds on fresh SQLite database

A fresh SQLite database migrates through V1, V2, V3 and V4 successfully.

### AC-017-003 — Normalized rooms persist

`RoomNode` records are persisted as rows and hydrated from SQLite.

### AC-017-004 — Normalized zones persist

`ZoneNode` records are persisted as rows and hydrated from SQLite.

### AC-017-005 — Normalized devices persist

`DeviceNode` records are persisted as rows and hydrated from SQLite.

### AC-017-006 — Normalized endpoints persist

`EndpointNode` records are persisted as rows and hydrated from SQLite.

### AC-017-007 — Normalized capabilities persist

`CapabilityNode` records are persisted as rows and hydrated from SQLite.

### AC-017-008 — Normalized spatial relations persist

`TopologySpatialRelation(LOCATED_IN)` records are persisted as rows and hydrated from SQLite.

### AC-017-009 — Compatibility snapshot is written but not authoritative

`topology_snapshots.topology_json` may be written as a compatibility read model, but production entity queries must not depend on it as the primary source.

### AC-017-010 — SQLite topology repositories exist

SQLite-backed implementations exist for topology, state/health and materialization replay responsibilities.

### AC-017-011 — Spring context wires topology services

The production Spring context includes `BaseTopologyService`, `CoreSnapshotQueryService` and `DefaultTopologyMaterializationService` backed by SQLite ports.

### AC-017-012 — H2 topology repository is not production-selected

`H2BaseTopologyRepository` is not selected as the production `BaseTopologyRepository`, `CoreSnapshotReadPort`, `EndpointHealthWritePort`, `TopologyMaterializationStatePort` or `MaterializationDecisionReplayPort`.

### AC-017-013 — In-memory topology repository is not production default

`InMemoryBaseTopologyRepository` is not the production default `BaseTopologyRepository`.

### AC-017-014 — Core snapshot query reads from normalized SQLite

`CoreSnapshotQueryService` reads topology, state and health through SQLite-backed normalized persistence paths.

### AC-017-015 — Individual entity queries read from normalized tables

CTI-style `findRoom`, `findZone`, `findDevice`, `findEndpoint`, `findCapability` and relation queries do not require full `topology_json` deserialization as the primary path.

### AC-017-016 — Structural save does not overwrite endpoint health

The MU-011 invariant remains true in SQLite: a later structural save carrying stale aggregate health must not overwrite durable endpoint health.

### AC-017-017 — Endpoint health persists through SQLite

Endpoint health writes and reads round-trip through SQLite.

### AC-017-018 — Device state persists through SQLite

Device runtime state writes and reads round-trip through SQLite.

### AC-017-019 — Materialization decision replay persists through SQLite

Materialization decisions are recorded and replayed through SQLite.

### AC-017-020 — Duplicate fact replay uses SQLite

Duplicate materialization facts are detected using the SQLite replay path.

### AC-017-021 — Mutation records persist and recover

Topology mutation records persist and can be read back from SQLite.

### AC-017-022 — Domain services remain SQL-free

SC-C domain services do not import SQLite, JDBC, Flyway, H2 or SQL APIs.

### AC-017-023 — Temporal runtime tests do not regress

Existing Temporal Runtime / MIR-016 tests continue to pass.

### AC-017-024 — Ledger/outbox not reimported into topology repositories

Topology repositories do not implement `ScLedgerWritePort` or `ScOutboxWritePort`.

### AC-017-025 — Outbox/ledger production path disposition is recorded

Implementation report records one of:

```text
Preferred:
  SQLiteScLedgerOutboxSeedTest added and passing.

Fallback:
  OutboxLedgerStorageSeedTest marked as legacy seed evidence.
```

### AC-017-026 — Jackson shim disposition is recorded

Implementation report records either resolved dependency alignment or bounded technical debt for local classes in `com.fasterxml.jackson.annotation`.

### AC-017-027 — Working tree is clean and scoped

Implementation report confirms no unrelated `.idea`, `mir-001`, `mir-002`, duplicate `#U2014` artifacts or runtime `target/*.sqlite` files are included.

---

## 10. Test requirements

The execution package MUST require at least the following test scenarios:

```text
sqliteTopologyFlywayMigrationCreatesNormalizedTables
springContextWiresTopologyPortsToSQLiteRepositories
h2BaseTopologyRepositoryIsNotSelectedInProductionContext
inMemoryBaseTopologyRepositoryIsNotProductionDefault
normalizedRoomsPersistAndHydrateThroughSQLite
normalizedZonesPersistAndHydrateThroughSQLite
normalizedDevicesPersistAndHydrateThroughSQLite
normalizedEndpointsPersistAndHydrateThroughSQLite
normalizedCapabilitiesPersistAndHydrateThroughSQLite
normalizedSpatialRelationsPersistAndHydrateThroughSQLite
baseTopologySnapshotCompatibilityWrittenButNotAuthoritative
coreSnapshotQueryReadsTopologyFromNormalizedSQLite
ctiFindRoomReadsFromNormalizedTables
ctiFindEndpointReadsFromNormalizedTables
structuralSaveDoesNotOverwriteEndpointHealthInSQLite
endpointHealthRoundTripsThroughSQLite
deviceStateRoundTripsThroughSQLite
materializationDecisionReplayRoundTripsThroughSQLite
materializationDuplicateFactReplayUsesSQLite
mutationRecordsPersistAndRecoverThroughSQLite
noTopologyRepositoryImplementsLedgerOrOutboxPorts
```

Strongly recommended:

```text
sqliteScLedgerOutboxRepositoryPersistsLedgerAndOutboxRecords
```

Regression requirement:

```text
mvn test
0 failures
0 errors
0 skipped unless explicitly justified
```

---

## 11. Risks

### R-017-001 — Accidental production selection of InMemoryBaseTopologyRepository

Mitigation:

```text
Remove @Repository or restrict to non-production profile.
Add production context test.
```

### R-017-002 — Recreating H2BaseTopologyRepository as SQLite God Object

Mitigation:

```text
Split repositories by responsibility.
Do not implement ledger/outbox in topology repositories.
```

### R-017-003 — H2 tests passing while SQLite path is broken

Mitigation:

```text
Add SQLite-backed tests for topology and Spring context.
Do not rely on H2 tests as production evidence.
```

### R-017-004 — Incorrect authority split between normalized tables and topology_json

Mitigation:

```text
Normalized tables are authority.
topology_json is compatibility/read-model only.
Execution package must make write/hydration rules explicit.
```

### R-017-005 — Overexpansion into graph database

Mitigation:

```text
No graph database in MU-017.
Use graph-shaped relational schema in SQLite.
```

### R-017-006 — Regression in Temporal Runtime SQLite stack

Mitigation:

```text
Run full suite and preserve MIR-016 tests.
```

### R-017-007 — Working tree noise

Mitigation:

```text
Implementation report must include git status summary and scope confirmation.
```

---

## 12. Deliverables

MIR-017 execution must produce:

```text
docs/mir/mir-017/code-surface-audit.md
docs/mir/mir-017/context.md
docs/mir/mir-017/codex-prompt.md
docs/mir/mir-017/acceptance-map.md
docs/mir/mir-017/implementation-report.md
```

Production code deliverables are expected under:

```text
src/main/resources/db/migration/V4__sc_c_normalized_topology_persistence.sql
src/main/java/com/sovereign/connect/config/TopologyPersistenceConfiguration.java
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/*Topology*.java
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/*EndpointHealth*.java
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/*Materialization*.java
src/test/java/**
```

Exact file names may vary if the implementation report justifies the naming.

---

## 13. Branch and commit recommendation

Branch:

```text
feat/sc-c-mir-017-normalized-topology-sqlite-persistence
```

Commit:

```text
feat(sc-c): add normalized SQLite persistence for canonical topology
```

---

## 14. Decision status

```text
MIR-017 v0.1.1-draft:
  Ready for review.

Next step:
  Produce and approve execution package before implementation.
```
