# Implementation Report - MU-017 Patch 001 Review Gaps

**Package:** `execution-package-MU-017-review-gaps-patch-001`  
**Version:** `v0.2.1`  
**Implementation branch:** `fix/sc-c-mir-017-normalized-topology-sqlite-persistence`  
**Commit:** `<filled after commit>`  
**Date:** `2026-05-22`

---

## 1. Summary

```text
MU-017 patch-001-review-gaps:
  PASS
```

Brief summary:

```text
B-01, B-02, B-03, H-04 and governance cleanup were closed.
The remaining H-01, H-02 and H-03 items were documented as deferred scope and were not expanded in this patch.
```

---

## 2. Review findings closure

| Finding | Status | Evidence |
|---|---|---|
| B-01 located fallback regression | `CLOSED` | `SQLiteBaseTopologyRepository`; `findLocatedDevicesFallsBackToDeviceRoomIdWhenRelationMissing`; `findLocatedDevicesFallsBackToDeviceZoneIdWhenRelationMissing`; `findLocatedEndpointsFallsBackToEndpointRoomIdWhenRelationMissing`; `findLocatedEndpointsFallsBackToEndpointZoneIdWhenRelationMissing`; `locatedQueryDeduplicatesRelationAndColumnFallbackMatches` |
| B-02 initial endpoint health loss | `CLOSED` | `SQLiteBaseTopologyRepository`; `SQLiteEndpointHealthRepository`; `initialEndpointHealthRoundTripsThroughSQLiteWithoutExplicitHealthWrite`; `structuralSaveDoesNotOverwriteExistingEndpointHealthInSQLite`; `everyPersistedEndpointGetsEndpointHealthRowIfAbsent` |
| B-03 global capability id validation | `CLOSED` | `BaseTopologyService`; `duplicateCapabilityIdAcrossOwnersRejectedBeforePersistence` |
| H-04 replay repository Clock | `CLOSED` | `SQLiteMaterializationDecisionReplayRepository`; `TopologyPersistenceConfiguration`; `materializationDecisionReplayUsesInjectedClock` |
| G-01 working tree/governance cleanup | `CLOSED` | `git status --short` shows only MU-017 patch code/docs before commit; `target/*.sqlite` count is 0 |
| H-01 BaseTopologyService.updateDeviceState in-memory | `DOCUMENTED` | Deferred; no service state-path redesign in patch-001 |
| H-02 device_health embedded in devices | `DOCUMENTED` | Deferred; endpoint health seeding fixed, device health schema expansion remains out of scope |
| H-03 provider_bindings deferred | `DOCUMENTED` | Deferred; provider refs remain row-attached JSON in MU-017 |

---

## 3. Code changes

List changed files:

```text
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteBaseTopologyRepository.java
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteEndpointHealthRepository.java
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteMaterializationDecisionReplayRepository.java
src/main/java/com/sovereign/connect/config/TopologyPersistenceConfiguration.java
src/main/java/com/sovereign/connect/core/topology/service/BaseTopologyService.java
src/test/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteTopologyPersistenceTest.java
src/test/java/com/sovereign/connect/core/topology/BaseTopologyServiceTest.java
src/test/java/com/sovereign/connect/core/topology/CoreSnapshotQuerySeedTest.java
src/test/java/com/sovereign/connect/core/topology/PersistenceMemorySeedTest.java
src/test/java/com/sovereign/connect/core/topology/ScCoreKernelHardeningTest.java
src/test/java/com/sovereign/connect/core/topology/TopologyVersionHardeningTest.java
docs/mir/mir-017/patch-001-review-gaps/implementation-report.md
```

Key implementation notes:

```text
Located fallback:
  findLocatedDevices/findLocatedEndpoints now return the union of explicit LOCATED_IN relation matches and direct room_id/zone_id matches from normalized device/endpoint rows.
  LinkedHashSet preserves deterministic order and deduplicates relation + column fallback duplicates.

Endpoint health seed-if-absent:
  SQLiteBaseTopologyRepository.save seeds one endpoint_health row per endpoint with INSERT OR IGNORE.
  Existing durable endpoint_health rows are preserved across structural saves.
  Seed data uses aggregate endpoint health, with UNKNOWN only as a defensive null fallback.

Capability validation:
  BaseTopologyService validates capabilityId uniqueness across DeviceNode.deviceCapabilities() and EndpointNode.capabilities() before persistence.

Clock injection:
  SQLiteMaterializationDecisionReplayRepository accepts Clock and records timestamps with Instant.now(clock).
  TopologyPersistenceConfiguration injects the application Clock into the replay repository bean.
```

---

## 4. Test evidence

Command:

```bash
mvn test
```

Result:

```text
Tests run: 162
Failures: 0
Errors: 0
Skipped: 0
Build: SUCCESS
```

Required patch tests:

```text
findLocatedDevicesFallsBackToDeviceRoomIdWhenRelationMissing: PASS
findLocatedDevicesFallsBackToDeviceZoneIdWhenRelationMissing: PASS
findLocatedEndpointsFallsBackToEndpointRoomIdWhenRelationMissing: PASS
findLocatedEndpointsFallsBackToEndpointZoneIdWhenRelationMissing: PASS
locatedQueryDeduplicatesRelationAndColumnFallbackMatches: PASS
initialEndpointHealthRoundTripsThroughSQLiteWithoutExplicitHealthWrite: PASS
structuralSaveDoesNotOverwriteExistingEndpointHealthInSQLite: PASS
everyPersistedEndpointGetsEndpointHealthRowIfAbsent: PASS
duplicateCapabilityIdAcrossOwnersRejectedBeforePersistence: PASS
materializationDecisionReplayUsesInjectedClock: PASS
```

---

## 5. Governance / working tree evidence

Command:

```bash
git status --short
```

Pre-commit result:

```text
 M src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteBaseTopologyRepository.java
 M src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteEndpointHealthRepository.java
 M src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteMaterializationDecisionReplayRepository.java
 M src/main/java/com/sovereign/connect/config/TopologyPersistenceConfiguration.java
 M src/main/java/com/sovereign/connect/core/topology/service/BaseTopologyService.java
 M src/test/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteTopologyPersistenceTest.java
 M src/test/java/com/sovereign/connect/core/topology/BaseTopologyServiceTest.java
 M src/test/java/com/sovereign/connect/core/topology/CoreSnapshotQuerySeedTest.java
 M src/test/java/com/sovereign/connect/core/topology/PersistenceMemorySeedTest.java
 M src/test/java/com/sovereign/connect/core/topology/ScCoreKernelHardeningTest.java
 M src/test/java/com/sovereign/connect/core/topology/TopologyVersionHardeningTest.java
?? docs/mir/mir-017/patch-001-review-gaps/
```

Expected:

```text
Only MU-017 patch-relevant code and docs: yes
No .idea/*: yes
No unrelated .gitignore changes: yes
No docs/mir/mir-001 or docs/mir/mir-002 noise: yes
No #U2014 duplicate artifacts: yes
No target/*.sqlite runtime artifacts: yes
```

---

## 6. Scope preservation

Confirm:

```text
No V4 redesign: yes
No graph database: yes
No northbound facade: yes
No EIB / View Composer: yes
No SC-B / SC-D runtime: yes
No ActionTemporalPayload / command dispatch: yes
No outbox dispatcher: yes
No topology_json authority regression: yes
```
