# Review Report — MU-017 Normalized Topology SQLite/Flyway Persistence

**Target:** `sovereign-connect-017.zip`  
**Commit inspected:** `ccd2221 feat(sc-c): add normalized SQLite persistence for canonical topology`  
**Date:** 2026-05-22  
**Reviewer:** ChatGPT  
**Scope:** Verify whether MU-017 preserves the H2-backed topology behavior, wires SQLite repositories into Spring services, and improves the persistence substrate in line with MIR-017 / CSA-017.

---

## 1. Disposition

```text
MU-017 implementation review:
  PARTIAL PASS+

Validated as L4 / industrial-grade:
  NOT YET

Main result:
  The patch implements the intended normalized SQLite/Flyway topology persistence path and Spring wiring, but retains two functional regressions relative to the H2 behavior and one domain/schema alignment gap.
```

### Original MU-017 target

```text
Replace seed/H2/in-memory topology persistence as the production path with normalized SQLite/Flyway persistence for SC-C Base Topology.
```

### Overall status

```text
Technical direction:        correct
Spring topology wiring:     substantially correct
V4 migration:               substantially correct
H2 functionality preserved: partially
Governance cleanliness:     still dirty in submitted ZIP
```

---

## 2. Test evidence

Included Surefire reports show:

```text
Tests run: 152
Failures:  0
Errors:    0
Skipped:   0
```

Relevant reports included:

```text
SQLiteTopologyPersistenceTest:       10 tests, 0 failures
TopologyPersistenceSpringContextTest: 3 tests, 0 failures
Full suite:                         152 tests, 0 failures/errors/skips
```

Local rerun could not be performed in this environment:

```text
mvn: command not found
```

---

## 3. What is correctly implemented

### 3.1 V4 normalized migration exists and is correctly named

The migration is present:

```text
src/main/resources/db/migration/V4__sc_c_normalized_topology_persistence.sql
```

It creates the expected normalized tables:

```text
topology_versions
topology_metadata
rooms
zones
devices
endpoints
capabilities
topology_spatial_relations
topology_snapshots
mutation_records
device_states
endpoint_health
materialization_decision_replay
```

The migration preserves the agreed `mutation_records` name, not `topology_mutations`.

### 3.2 SQLite repositories added

The patch adds:

```text
SQLiteBaseTopologyRepository
SQLiteEndpointHealthRepository
SQLiteTopologyMaterializationStateRepository
SQLiteMaterializationDecisionReplayRepository
```

Responsibilities are split better than the seed `H2BaseTopologyRepository`, which previously implemented seven ports including outbox/ledger. The new topology repository does not implement `ScLedgerWritePort` or `ScOutboxWritePort`.

### 3.3 Spring wiring added

`TopologyPersistenceConfiguration` wires:

```text
BaseTopologyRepository              -> SQLiteBaseTopologyRepository
CoreSnapshotReadPort                -> SQLiteBaseTopologyRepository
EndpointHealthWritePort             -> SQLiteEndpointHealthRepository
TopologyMaterializationStatePort     -> SQLiteTopologyMaterializationStateRepository
MaterializationDecisionReplayPort    -> SQLiteMaterializationDecisionReplayRepository
BaseTopologyService                  -> Spring bean
CoreSnapshotQueryService             -> Spring bean
TopologyMaterializationService       -> Spring bean
```

This closes the previous absence of topology beans in the Spring application context.

### 3.4 In-memory production risk reduced

`InMemoryBaseTopologyRepository` is no longer annotated with `@Repository`, so it is not production-selected by component scanning.

### 3.5 `topology_json` authority inversion is implemented

`SQLiteBaseTopologyRepository.findByHabitatId(...)` hydrates from normalized tables, not from `topology_json`. Tests corrupt `topology_json` and still verify successful topology/entity reads.

### 3.6 FK-safe structural rewrite is implemented

`save(...)` runs inside a `TransactionTemplate` and follows the required structural rewrite order:

Delete:

```text
topology_spatial_relations
capabilities
endpoints
devices
zones
rooms
```

Insert:

```text
topology_versions
topology_metadata
rooms
zones
devices
endpoints
capabilities
topology_spatial_relations
topology_snapshots
```

### 3.7 Outbox/Ledger not reabsorbed into topology

`SQLiteScLedgerOutboxRepository` remains the SQLite implementation for outbox/ledger. MU-017 adds direct SQLite coverage for that path and does not reimport ledger/outbox methods into the topology repository.

---

## 4. H2 vs SQLite method/port comparison

| Responsibility | H2 seed implementation | SQLite MU-017 implementation | Status |
|---|---|---|---|
| `BaseTopologyRepository.save` | JSON snapshot in `topology_snapshots` | Normalized structural rows + compatibility snapshot | Improved, with caveats below |
| `findByHabitatId` | Deserialize `topology_json` | Hydrate from normalized rows | Improved |
| `findCurrentVersion` | From snapshot | From `topology_versions` | Improved |
| `findSnapshot` | Snapshot from JSON | Hydrated topology + snapshot timestamp | Preserved |
| `appendMutationRecord` / `findMutationRecords` | H2 `mutation_records` | SQLite `mutation_records` | Preserved |
| `saveDeviceState` / `findDeviceState` | H2 `device_states` | SQLite `device_states` via materialization state repo / read port | Preserved for materialization path |
| `saveEndpointHealth` / `findEndpointHealth` | H2 `endpoint_health` | SQLite `endpoint_health` | Preserved |
| `findRoom` / `findZone` | JSON scan | Normalized row query | Improved |
| `findSpatialRelation` / subject query | JSON scan | `topology_spatial_relations` | Improved |
| `findLocatedDevices` / `findLocatedEndpoints` | Relation OR roomId/zoneId fallback | Relation-only | Regression |
| Ledger/outbox | H2 repo implemented ports | Separate SQLiteScLedgerOutboxRepository | Improved separation |

---

## 5. Blockers / required fixes

### B-01 — `findLocatedDevices` / `findLocatedEndpoints` lost H2 fallback semantics

The H2 implementation returned located devices/endpoints if either a primary `LOCATED_IN` relation matched or the node's `roomId` / `zoneId` matched the query value.

The SQLite implementation uses only `topology_spatial_relations` via `locatedSubjectIds(...)`.

Impact:

```text
A topology with devices/endpoints carrying roomId/zoneId but missing explicit LOCATED_IN relations returns empty for findLocatedDevices/findLocatedEndpoints.
```

This is a functional regression relative to H2 and a risk for CTI query behavior.

Required fix, choose one:

```text
Option A — preserve transitional fallback:
  findLocatedDevices/findLocatedEndpoints should return subjects matched by:
    - primary LOCATED_IN relation; OR
    - device/endpoint room_id == roomOrZoneId; OR
    - device/endpoint zone_id == roomOrZoneId.

Option B — enforce relation completeness:
  save(...) rejects any device/endpoint whose placement is not represented by a primary LOCATED_IN relation.
```

Recommendation: **Option A** for MU-017, because it preserves H2 behavior and keeps Room/Zone relation migration safer.

Required tests:

```text
findLocatedDevicesFallsBackToDeviceRoomIdWhenRelationMissing
findLocatedEndpointsFallsBackToEndpointRoomIdWhenRelationMissing
findLocatedDevicesFallsBackToDeviceZoneIdWhenRelationMissing
findLocatedEndpointsFallsBackToEndpointZoneIdWhenRelationMissing
```

---

### B-02 — Endpoint health from initial topology can be lost on hydration

`SQLiteBaseTopologyRepository.save(...)` intentionally does not write `endpoint_health`, preserving structural/runtime separation. That is correct for later structural saves.

But `findByHabitatId(...)` hydrates each endpoint using durable `endpoint_health` rows and falls back to:

```text
EndpointHealth(UNKNOWN, null, "no durable endpoint health row")
```

If an initial topology is saved with endpoint health embedded in the endpoint aggregate and no explicit endpoint_health row exists, the hydrated endpoint no longer matches the saved topology. H2 preserved this because it deserialized `topology_json`.

Impact:

```text
repository.save(topologyWithEndpointHealth)
repository.findByHabitatId(...)
  returns endpoint health UNKNOWN fallback instead of original endpoint.health()
```

Required fix, choose one:

```text
Option A — initial structural save seeds endpoint_health only when no row exists.
  Preserve MU-011 by never overwriting existing durable endpoint_health.

Option B — BaseTopologyService.createInitialTopology(...) writes initial endpoint health explicitly through EndpointHealthWritePort after repository.save(...).

Option C — Hydration fallback uses endpoint row stored health fields.
  Not possible with current endpoints table unless endpoint health columns are added.
```

Recommendation: **Option A or B**. Option A is repository-local and preserves round-trip semantics. Option B is service-correct but may not cover direct repository use.

Required tests:

```text
initialEndpointHealthRoundTripsThroughSQLiteWithoutExplicitHealthWrite
structuralSaveDoesNotOverwriteExistingEndpointHealthInSQLite
```

---

### B-03 — Habitat-global `capability_id` uniqueness is enforced by schema but not by domain validation

The V4 schema declares:

```sql
PRIMARY KEY (habitat_id, capability_id)
```

This means `capability_id` is globally unique within a habitat. That is the right direction for canonical IDs.

However, `BaseTopologyService.validateTopology(...)` currently validates capability uniqueness separately per device and per endpoint only. A topology can be domain-valid but fail at SQLite persistence time if two different owners reuse the same `capability_id`.

Impact:

```text
Domain/service layer accepts a topology that SQLite rejects with a PK violation.
```

Required fix:

```text
Add habitat-global capability_id validation in BaseTopologyService.validateTopology(...), across both device.deviceCapabilities() and endpoint.capabilities().
```

Required test:

```text
duplicateCapabilityIdAcrossOwnersRejectedBeforePersistence
```

---

## 6. Important issues / non-blocking but should be patched or documented

### H-01 — Device state is still in-memory through BaseTopologyService.updateDeviceState(...)

Spring now wires `BaseTopologyService`, but `BaseTopologyService.updateDeviceState(...)` still writes only to its internal `deviceStates` map. Durable device state exists through `TopologyMaterializationStatePort`, not through the service method.

This appears pre-existing rather than introduced by MU-017, but it is important because a north-facing facade must not use `BaseTopologyService.updateDeviceState(...)` expecting durable state.

Recommendation:

```text
Document as DEBT-017-001 or explicitly restrict BaseTopologyService.updateDeviceState(...) to seed/domain use.
```

### H-02 — `device_health` remains embedded in structural `devices` table

The V4 schema stores device health columns inside `devices`. Endpoint health is properly split into `endpoint_health`. This may be acceptable for v1 if device health has no separate write port, but it should be explicitly documented.

Recommendation:

```text
Document that MU-017 separates endpoint_health and device_states; device_health remains structural/embedded until a dedicated device health port/table is introduced.
```

### H-03 — V4 omits provider_bindings as first-class table

Provider references are stored as JSON inside device/endpoint/relation rows. This preserves current behavior but does not yet implement a normalized provider binding table.

Recommendation:

```text
Document as bounded scope: provider refs normalized as row-attached JSON in MU-017; provider_bindings table deferred.
```

### H-04 — `recordDecision(...)` uses system clock instead of injected clock

`SQLiteMaterializationDecisionReplayRepository.recordDecision(...)` uses `Instant.now()` directly. H2 used repository clock. This is minor, but for deterministic testing/observability the SQLite replay repository should accept `Clock`.

Recommended fix:

```text
Inject Clock into SQLiteMaterializationDecisionReplayRepository and use Instant.now(clock).
```

---

## 7. Governance / artifact hygiene blocker

The Git commit itself is scoped correctly, but the submitted ZIP still has a dirty working tree with unrelated changes:

```text
.gitignore
.idea/*
docs/mir/mir-001/*
docs/mir/mir-002/*
#U2014 duplicate artifacts
```

It also includes many generated `target/*.sqlite` files.

The implementation report claims these are absent from git status, but the submitted ZIP contradicts that when inspected with `git status --short`.

Required before acceptance/merge:

```text
1. Revert/remove unrelated .idea and .gitignore changes.
2. Revert docs/mir/mir-001 and docs/mir/mir-002 noise.
3. Remove #U2014 duplicate artifacts.
4. Ensure target/*.sqlite is ignored and not shipped as evidence artifact.
5. Submit a clean ZIP or confirm commit-only review scope.
```

---

## 8. Acceptance assessment

```text
AC-017-001  PASS
AC-017-002  PASS
AC-017-003  PASS
AC-017-004  PASS
AC-017-005  PASS
AC-017-006  PASS
AC-017-007  PARTIAL — schema yes, domain validation missing
AC-017-008  PASS
AC-017-009  PASS
AC-017-010  PASS
AC-017-011  PASS
AC-017-012  PASS
AC-017-013  PASS for materialization path; BaseTopologyService state remains in-memory
AC-017-014  PARTIAL — existing health preserved; initial health roundtrip gap
AC-017-015  PARTIAL — normalized queries yes; located fallback regression
AC-017-016  PARTIAL — roundtrip misses endpoint health when no durable row
AC-017-017  PASS with same health caveat
AC-017-018  PASS for explicit health repository path
AC-017-019  PASS
AC-017-020  PASS
AC-017-021  PARTIAL — relation queries yes; fallback placement regression
AC-017-022  PASS
AC-017-023  PASS
AC-017-024  PASS
AC-017-025  PASS
AC-017-026  PASS with debt
AC-017-027  PASS per included Surefire reports
AC-017-028  PASS
AC-017-029  PASS
AC-017-030  FAIL for submitted ZIP working tree; commit itself appears scoped
```

---

## 9. Final verdict

```text
MU-017 implementation:
  PARTIAL PASS+

Validated L4:
  NO

Reason:
  The implementation correctly introduces normalized SQLite topology persistence and Spring wiring, but does not fully preserve prior H2 behavior for located queries and endpoint health roundtrip, and its schema-level capability identity rule is not yet enforced by domain validation.
```

### Required patch scope

```text
P1. Restore or replace H2 located-query fallback semantics.
P2. Preserve initial endpoint health roundtrip without overwriting durable endpoint_health rows.
P3. Add domain validation for habitat-global capability_id uniqueness.
P4. Clean working tree / artifact hygiene.
```

### Recommended branch

```text
fix/sc-c-mir-017-normalized-topology-sqlite-persistence
```

### Recommended commit

```text
fix(sc-c): close MIR-017 topology SQLite persistence gaps
```

