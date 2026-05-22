# Code Surface Audit — MU-017 Canonical Topology SQLite/Flyway Persistence

**Document ID:** CSA-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001  
**Title:** Code Surface Audit — MU-017 Canonical Topology SQLite/Flyway Persistence  
**Version:** v0.1.3-draft  
**Status:** Draft  
**Date:** 2026-05-22  
**Corpus:** Sovereign Connect  
**Type:** Code Surface Audit  
**Plane:** SC-C  
**Baseline:** `sovereign-connect-016-patch-3.zip` — post MU-016 patch-003, reported 139 tests / 0 failures  
**Scope:** Pre-MIR audit for migrating SC-C Base Topology persistence from seed/H2 adapters to normalized SQLite/Flyway industrial persistence.

---

## Changelog v0.1.3-draft

Based on `CSA-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v0.1.2-draft`, this version:

1. Closes `OQ-017-001` by selecting normalized canonical topology tables for MU-017.
2. Supersedes the earlier hybrid `topology_json + secondary indexes` recommendation.
3. Declares that `topology_json` may remain only as a derived compatibility snapshot / read model, not as production authority.
4. Adds `DEC-017-001 — Normalized Canonical Topology Persistence v1`.
5. Adds `DEC-017-002 — No external graph database in MU-017`.
6. Records that graph-shaped topology will be modeled relationally inside SQLite, not through a separate graph database.
7. Requires the execution package to define exact write and hydration patterns for normalized tables.
8. Updates MIR readiness to require `MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v0.1.1-draft` or later.

---

## 0. Disposition

```text
CSA-MU-017 v0.1.3-draft:
  Approvable as Code Surface Audit baseline.

MIR readiness:
  Ready to patch MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001
  to v0.1.1-draft with normalized-v1 scope.

Execution readiness:
  Execution package MUST resolve normalized write/hydration patterns before Codex.
```

This CSA supersedes `v0.1.0-draft`, `v0.1.1-draft` and `v0.1.2-draft` for MU-017 descent.

---

## 1. Inputs inspected

```text
sovereign-connect-016-patch-3.zip
  Baseline: post MU-016 patch-003 (BLOB ID binding)
  Reported tests: 139 / 0 failures

Key surfaces:
  pom.xml
  src/main/resources/application.yml
  src/main/resources/db/migration/V1__sc_c_base_schema.sql
  src/main/resources/db/migration/V2__sc_c_temporal_engine.sql
  src/main/resources/db/migration/V3__sc_c_ledger_outbox_sqlite.sql
  src/main/java/com/sovereign/connect/SovereignConnectApplication.java
  src/main/java/com/sovereign/connect/config/TemporalEngineConfiguration.java
  src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
  src/main/java/com/sovereign/connect/adapter/persistence/InMemoryBaseTopologyRepository.java
  src/main/java/com/sovereign/connect/adapter/persistence/sqlite/*
  src/main/java/com/sovereign/connect/core/topology/service/BaseTopologyService.java
  src/main/java/com/sovereign/connect/core/topology/query/CoreSnapshotQueryService.java
  src/main/java/com/sovereign/connect/core/topology/materialization/DefaultTopologyMaterializationService.java
  src/main/java/com/sovereign/connect/core/topology/port/*
  src/test/java/com/sovereign/connect/core/topology/*
  src/test/java/com/sovereign/connect/adapter/persistence/OutboxLedgerStorageSeedTest.java
```

---

## 2. Executive diagnosis

The codebase has two persistence planes that are not connected:

```text
Temporal plane:
  SQLite/Flyway/Spring-wired.
  Lifecycle-managed by TemporalEngineLifecycle.
  Tested on SQLite after MU-016.
  Status: industrialized within Signal-only TemporalActs v1 scope.

Topology plane:
  H2 DDL inline and in-memory seed adapters.
  No topology Flyway schema.
  No normalized topology tables.
  No production Spring topology configuration.
  Services are POJOs, not Spring-wired production beans.
  Status: seed-grade.
```

MU-017 is therefore not a minor storage refactor. It is the industrialization of the canonical SC-C topology state substrate.

Previous hybrid proposals (`topology_json` plus indexes) are insufficient because the accepted persistence schema already selected normalized SQLite tables as the production profile. A hybrid JSON-authoritative implementation would implement below the accepted contract rather than approximate it.

---

## 3. Spring application context findings

### 3.1 Production configuration present

The only production `@Configuration` class found is `TemporalEngineConfiguration`. It wires:

```text
Clock, ObjectMapper
DataSource (SQLite + PerConnectionPragmaDataSource)
Flyway (initMethod = migrate)
TransactionTemplate
SQLiteTemporalActRepository -> TemporalActWritePort + TemporalActReadPort (@Primary)
SQLiteScLedgerOutboxRepository -> ScLedgerWritePort + ScOutboxWritePort
SQLiteTemporalRequestIdempotencyRepository -> TemporalRequestIdempotencyPort
SQLiteTemporalEngineLockRepository -> TemporalEngineLockPort
SQLiteTemporalRecoveryObservationRepository -> TemporalRecoveryObservationPort
TemporalActService
TemporalEngineService
TemporalActObservationService -> TemporalActObservationPort
TemporalActApplicationService -> TemporalActApplicationPort
TemporalEngineHealth
TemporalEngineRunner
TemporalEngineLifecycle
```

### 3.2 Topology Spring wiring absent

The following production topology beans are absent from the Spring context:

```text
BaseTopologyService
DefaultTopologyMaterializationService / TopologyMaterializationService
CoreSnapshotQueryService
BaseTopologyRepository (SQLite-backed)
CoreSnapshotReadPort (SQLite-backed)
EndpointHealthWritePort (SQLite-backed)
TopologyMaterializationStatePort (SQLite-backed)
MaterializationDecisionReplayPort (SQLite-backed)
SQLiteBaseTopologyRepository
SQLiteEndpointHealthRepository
SQLiteTopologyMaterializationStateRepository
SQLiteMaterializationDecisionReplayRepository
```

`BaseTopologyService`, `CoreSnapshotQueryService` and `DefaultTopologyMaterializationService` are plain Java constructors with no `@Component`, `@Service` or `@Bean` declaration.

### 3.3 InMemoryBaseTopologyRepository `@Repository` risk

`InMemoryBaseTopologyRepository` is annotated as a Spring repository:

```java
@Repository
public class InMemoryBaseTopologyRepository implements BaseTopologyRepository
```

This is an industrial risk. If MU-017 adds topology service beans without explicitly selecting SQLite ports, Spring may inject the in-memory seed repository rather than fail visibly.

Required MU-017 action:

```text
Remove @Repository from InMemoryBaseTopologyRepository
or restrict it to a test/dev profile.

The in-memory repository MUST NOT be production-selected as BaseTopologyRepository.
```

---

## 4. Flyway / schema findings

### 4.1 Current migrations

```text
V1__sc_c_base_schema.sql
  habitats table only; seed habitat-001.

V2__sc_c_temporal_engine.sql
  temporal_acts;
  temporal_request_idempotency;
  temporal_engine_locks;
  recovery_runs;
  recovery_findings.

V3__sc_c_ledger_outbox_sqlite.sql
  sc_c_ledger_entries;
  sc_c_outbox_entries.
```

### 4.2 Missing topology migrations

No Flyway migration creates production SQLite tables for:

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

### 4.3 Required migration

MU-017 requires:

```text
V4__sc_c_normalized_topology_persistence.sql
```

The migration MUST create normalized canonical topology tables. `topology_snapshots` may remain, but only as compatibility / derived read model.

---

## 5. H2BaseTopologyRepository surface

### 5.1 Current role

`H2BaseTopologyRepository` implements seven interfaces and acts as a seed God Object:

```text
BaseTopologyRepository
CoreSnapshotReadPort
EndpointHealthWritePort
TopologyMaterializationStatePort
MaterializationDecisionReplayPort
ScLedgerWritePort
ScOutboxWritePort
```

This concentration was acceptable as seed evidence. It is not acceptable as the production topology persistence shape.

### 5.2 Responsibility decomposition for MU-017

MU-017 must split the responsibilities into focused SQLite adapters.

Required production responsibilities:

```text
SQLiteBaseTopologyRepository
  implements BaseTopologyRepository
  may implement CoreSnapshotReadPort if hydration remains cohesive
  owns normalized structural topology tables
  does not own state/health as structural side effect
  does not own ledger/outbox

SQLiteCoreSnapshotReadRepository or SQLiteBaseTopologyRepository
  implements CoreSnapshotReadPort
  hydrates CoreSnapshot / HabitatBaseTopology from normalized tables

SQLiteEndpointHealthRepository
  implements EndpointHealthWritePort
  owns endpoint_health table

SQLiteTopologyMaterializationStateRepository
  implements TopologyMaterializationStatePort
  owns device state and materialization-scoped reads

SQLiteMaterializationDecisionReplayRepository
  implements MaterializationDecisionReplayPort
  owns materialization_decision_replay table
```

Ledger/outbox responsibilities MUST remain outside topology repositories.

---

## 6. DEC-017-001 — Normalized Canonical Topology Persistence v1

MU-017 MUST implement normalized canonical topology tables as the production persistence authority for Base Topology.

Decision:

```text
Normalized SQLite tables are authoritative for production topology persistence.

topology_json MAY remain as a compatibility snapshot / read model / recovery aid.

topology_json MUST NOT be the sole or primary production persistence mechanism.

CTI-style entity queries MUST read from normalized tables or from repository
methods backed by normalized tables.

Mandatory full CLOB deserialization is not acceptable as the production query path
for individual canonical entities.
```

Rationale:

```text
The accepted persistence schema selected normalized SQLite persistence as the
production profile.

The Canonical Topology Interface and future SC-C north-facing facade require
stable, indexed, entity-level canonical observations.

A JSON-authoritative hybrid would create a second transitional storage design
below the accepted contract.
```

### 6.1 Required normalized tables

At minimum, MU-017 must create and use:

```text
habitats                         existing V1, unchanged

topology_versions                current topologyVersion per habitat

mutation_records               mutation record / mutation history

rooms                            RoomNode rows
zones                            ZoneNode rows
devices                          DeviceNode rows
endpoints                        EndpointNode rows
capabilities                     CapabilityNode rows

topology_spatial_relations       TopologySpatialRelation rows, seed relation LOCATED_IN

provider_bindings / provider_refs
  if required to preserve current providerRef metadata shape

device_states                    runtime device state
endpoint_health                  endpoint health, separated from structural topology
materialization_decision_replay  duplicate materialization fact prevention

topology_snapshots               compatibility snapshot/read model only
```

### 6.2 Normalized write pattern required in execution package

The execution package MUST provide an exact write pattern for `save(HabitatBaseTopology)`.

Required semantics:

```text
1. Run in one transaction.
2. Upsert topology_versions / current topologyVersion.
3. Replace normalized structural rows for the habitat in dependency-safe order.
4. Insert rooms, zones, devices, endpoints, capabilities and spatial relations.
5. Preserve provider refs as metadata, not canonical identity.
6. Update topology_snapshots as derived compatibility read model.
7. Do not overwrite endpoint_health or device_states through structural save.
8. Do not write ledger/outbox.
```

Recommended implementation strategy:

```text
delete-all + insert-all per structural collection within one transaction.
```

The execution package must define delete order and insert order explicitly to avoid FK violations.

### 6.3 Hydration pattern required in execution package

The execution package MUST define how to hydrate `HabitatBaseTopology` / `CoreSnapshot` from normalized tables.

Required semantics:

```text
1. SELECT topologyVersion from topology_versions.
2. SELECT rooms.
3. SELECT zones.
4. SELECT devices.
5. SELECT endpoints.
6. SELECT capabilities.
7. SELECT topology_spatial_relations.
8. SELECT runtime state / health through their dedicated tables.
9. Assemble records in memory without relying on topology_json as authority.
10. Use topology_json only as compatibility snapshot / diagnostic fallback if explicitly documented.
```

---

## 7. DEC-017-002 — No external graph database in MU-017

MU-017 MUST NOT introduce a separate graph database, embedded graph engine or polyglot persistence authority.

Decision:

```text
The topology model is graph-shaped.

The authoritative persistence store remains SQLite.

Rooms, zones, devices, endpoints and capabilities are persisted as canonical
entity tables.

TopologySpatialRelation is persisted as the canonical edge/relation table.

No external graph DB is introduced in MU-017.
```

Rationale:

```text
Adding a graph database now would introduce dual-write, multi-store recovery,
backup/restore complexity, migration complexity and authority ambiguity.

A graph database may be useful later as an analytic read model, but it must not
own topologyVersion, canonical IDs, materialization decisions, state, health or
provider bindings.
```

Future option:

```text
A graph read model such as Kuzu MAY be considered later as a derived,
rebuildable analytic model for advanced multi-hop graph queries.

If introduced, it MUST be downstream of SQLite canonical tables and must be
rebuildable from them.
```

---

## 8. Required Spring topology configuration

MU-017 must add:

```text
com.sovereign.connect.config.TopologyPersistenceConfiguration
```

Required beans:

```text
SQLiteBaseTopologyRepository or equivalent
  @Primary BaseTopologyRepository

SQLiteCoreSnapshotReadRepository or equivalent
  @Primary CoreSnapshotReadPort

SQLiteEndpointHealthRepository
  @Primary EndpointHealthWritePort

SQLiteTopologyMaterializationStateRepository
  @Primary TopologyMaterializationStatePort

SQLiteMaterializationDecisionReplayRepository
  @Primary MaterializationDecisionReplayPort

BaseTopologyService
CoreSnapshotQueryService
DefaultTopologyMaterializationService
```

Wiring rules:

```text
All SQLite topology repositories MUST depend on Flyway migration completion.
SQLite topology implementations MUST be @Primary over seed/in-memory alternatives.
InMemoryBaseTopologyRepository MUST NOT be production-selected.
H2BaseTopologyRepository MUST NOT be production-selected.
Topology repositories MUST NOT implement ScLedgerWritePort or ScOutboxWritePort.
```

---

## 9. Test surface impact

### 9.1 H2-backed topology test landscape

Existing seed tests use `H2BaseTopologyRepository` for topology infrastructure, including:

```text
PersistenceMemorySeedTest
CoreSnapshotQuerySeedTest
PersistenceBoundaryHardeningTest
ScCoreKernelHardeningTest
TopologyMaterializationSeedTest
RoomZoneTopologySeedTest
ConcurrencyIdempotencySeedTest
TemporalActSeedTest for topology/ledger infra
OutboxLedgerStorageSeedTest for legacy ledger/outbox path
```

These tests remain useful as seed regression evidence, but MU-017 acceptance requires SQLite-backed production-path tests.

### 9.2 In-memory domain tests

Tests using `InMemoryBaseTopologyRepository` may remain as domain/unit tests, provided the in-memory repository is not production-selected by Spring.

### 9.3 OutboxLedgerStorageSeedTest coverage drift

`OutboxLedgerStorageSeedTest` validates the H2 implementation only. Production ledger/outbox persistence is backed by `SQLiteScLedgerOutboxRepository`.

Recommended disposition:

```text
Add SQLiteScLedgerOutboxSeedTest.
```

Acceptable fallback:

```text
Mark OutboxLedgerStorageSeedTest as legacy seed evidence if MU-017 scope pressure
requires deferral.
```

The implementation report must record which disposition was chosen.

---

## 10. Required MU-017 test scenarios

Required scenarios:

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

## 11. Persistence map — before and after MU-017

| Table / state | Before MU-017 | After MU-017 |
|---|---|---|
| `habitats` | SQLite V1 | SQLite V1 unchanged |
| `temporal_acts` | SQLite V2 | SQLite V2 unchanged |
| `temporal_request_idempotency` | SQLite V2 | SQLite V2 unchanged |
| `temporal_engine_locks` | SQLite V2 | SQLite V2 unchanged |
| `recovery_runs`, `recovery_findings` | SQLite V2 | SQLite V2 unchanged |
| `sc_c_ledger_entries` | SQLite V3 | SQLite V3 unchanged |
| `sc_c_outbox_entries` | SQLite V3 | SQLite V3 unchanged |
| `topology_versions` | absent | SQLite V4 normalized authority |
| `mutation_records` | H2 inline | SQLite V4 normalized authority |
| `rooms` | topology_json / H2 seed | SQLite V4 normalized authority |
| `zones` | topology_json / H2 seed | SQLite V4 normalized authority |
| `devices` | topology_json / H2 seed | SQLite V4 normalized authority |
| `endpoints` | topology_json / H2 seed | SQLite V4 normalized authority |
| `capabilities` | topology_json / H2 seed | SQLite V4 normalized authority |
| `topology_spatial_relations` | topology_json / H2 seed | SQLite V4 normalized authority |
| `device_states` | H2 inline | SQLite V4 dedicated runtime state |
| `endpoint_health` | H2 inline | SQLite V4 dedicated runtime health |
| `materialization_decision_replay` | H2 inline | SQLite V4 dedicated replay table |
| `topology_snapshots` | H2 topology_json authority | SQLite V4 derived compatibility snapshot |

---

## 12. Additional technical debt items

### 12.1 Jackson shims in third-party namespace

The codebase still contains local classes under:

```text
src/main/java/com/fasterxml/jackson/annotation/JsonDeserializeAs.java
src/main/java/com/fasterxml/jackson/annotation/JsonSerializeAs.java
```

This remains a bounded technical debt item unless MU-017 resolves dependency alignment. It must be recorded in the implementation report.

### 12.2 Working tree noise

The baseline service has previously contained unrelated working tree noise:

```text
.idea/*
docs/mir/mir-001/*
docs/mir/mir-002/*
#U2014 duplicate filename artifacts
target/*.sqlite runtime artifacts
```

MU-017 acceptance must require a clean scoped commit.

---

## 13. Invariants to preserve

```text
SC-C owns Base Topology and topologyVersion.
SC-C owns canonical topology persistence.
SQLite is the authoritative SC-C persistence store for topology after MU-017.
SC-D emits facts; SC-C materializes.
SC-B transports/correlates; SC-B is not storage authority.
Base Topology remains separate from Effective View / Projection.
topologyVersion advances only on accepted structural mutations.
State/health writes do not advance topologyVersion.
Structural topology saves do not overwrite endpoint health.
Temporal runtime SQLite stack must not regress.
Outbox/Ledger semantic ownership remains SC-C.
H2 and InMemory are test/legacy seed fixtures only after MU-017.
Topology repositories do not own ledger/outbox persistence.
No external graph DB is introduced in MU-017.
```

---

## 14. Non-goals for MU-017

```text
SC-C north-facing facade
Effective Interaction Boundary
View Composer
SC-B runtime or broker binding
NATS / JetStream
SC-D adapter manifest or runtime
ActionTemporalPayload
Command dispatch
Outbox dispatcher
External graph database
Kuzu / Neo4j / Apache AGE / other graph engine adoption
Graph analytics read model
Projection / Effective View persistence
```

---

## 15. Open questions

### OQ-017-001 — JSON snapshot vs normalized topology tables

Closed by `DEC-017-001`.

Disposition:

```text
MU-017 implements normalized canonical topology tables as the production
authority.

topology_json may remain only as derived compatibility snapshot/read model.
```

### OQ-017-002 — H2BaseTopologyRepository deletion or legacy retention

Recommendation:

```text
Retain as legacy seed/test fixture.
Do not delete.
Ensure it is never production-selected.
```

### OQ-017-003 — InMemoryBaseTopologyRepository production annotation

Recommendation:

```text
Remove @Repository or restrict to test/dev profile.
The in-memory repository must not be the default BaseTopologyRepository bean.
```

### OQ-017-004 — OutboxLedgerStorageSeedTest migration timing

Recommendation:

```text
Preferred: add SQLiteScLedgerOutboxSeedTest.
Fallback: mark OutboxLedgerStorageSeedTest as legacy seed evidence.
```

### OQ-017-005 — Future graph read model

Recommendation:

```text
Deferred.
A graph database may be considered later as derived analytic read model only.
Do not include in MU-017.
```

---

## 16. Required MIR scope

```text
MU ID:              MU-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001
MIR ID:             MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001
Operational slot:   MU-017
Branch:             feat/sc-c-mir-017-normalized-topology-sqlite-persistence
```

Scope:

```text
1. Add Flyway V4 normalized topology persistence migration.
2. Implement normalized SQLite repositories for topology/state/health/replay.
3. Wire topology services and ports in Spring.
4. Confine H2 and InMemory repositories to legacy/test/dev usage.
5. Preserve compatibility snapshot as derived read model only.
6. Preserve all topologyVersion, health/state, materialization replay and Room/Zone invariants.
7. Preserve Temporal Runtime and outbox/ledger SQLite paths.
8. Add required SQLite-backed regression tests.
```

---

## 17. Recommended next descent

```text
1. Accept CSA-MU-017 v0.1.3-draft.
2. Patch MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 to v0.1.1-draft.
3. Produce execution package:
     docs/mir/mir-017/
       code-surface-audit.md
       context.md
       codex-prompt.md
       acceptance-map.md
       implementation-report-template.md
4. Execute only after the package defines exact normalized write/hydration patterns.
```
