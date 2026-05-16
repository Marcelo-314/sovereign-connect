# acceptance-map.md — MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001

```text
Document:         acceptance-map.md
MU:               MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001
Operational Slot: MU-014
Version:          v0.1.1-draft
Status:           Draft execution evidence map
```

---

## 0. Purpose

This document maps MIR acceptance criteria to expected implementation evidence for MU-014.

MU-014 validates materialization fact replay and duplicate semantic-effect prevention. It does not validate generic command/request idempotency, terminal response persistence, TemporalAct fire idempotency, outbox/ledger runtime, SC-B delivery or production SQLite migrations.

---

## 1. Acceptance criteria mapping

| AC | Requirement | Expected evidence |
|---:|---|---|
| AC-001 | A fact-scoped replay/idempotency port exists or equivalent boundary is implemented. | `MaterializationDecisionReplayPort` or accepted alias under `core/topology/port`. |
| AC-002 | Replay key is `habitatId + factId` or equivalent fact-scoped key. | Replay table primary key and tests use `(habitatId, factId)`. |
| AC-003 | Replaying same factId returns recorded `MaterializationDecision`. | Test: `sameFactIdReplaysStoredMaterializationDecisionWithoutVersionAdvance`. |
| AC-004 | Replaying same factId does not execute structural mutation again. | Test checks no extra topology change/event/version advancement; `BaseTopologyService.emittedEvents()` does not grow on replay. |
| AC-005 | Replaying same factId does not advance `topologyVersion`. | Test compares version before/after replay. |
| AC-006 | Accepted materialization decisions can be recorded for replay. | Accepted device/room/zone decision replay test. |
| AC-007 | Rejected materialization decisions can be recorded for replay where decision shape permits it. | Unauthorized/duplicate/invalid decision replay test or implementation-report explanation for any unsupported kind. |
| AC-007A | Replay store is H2-backed unless concrete blocker is recorded. | H2 adapter implements replay port; implementation report confirms table. |
| AC-007B | Replay survives repository/service recreation when H2-backed. | Test: `replaySurvivesRepositoryAndServiceRecreation`. |
| AC-008 | Different factId targeting an already materialized canonical entity is rejected as duplicate/invalid. | Tests for device, room and zone duplicate facts with different factIds. |
| AC-009 | Semantic duplicate rejection does not advance `topologyVersion`. | Duplicate tests compare topologyVersion. |
| AC-010 | factId replay and semantic duplicate detection are not conflated. | Tests distinguish same factId replay from different factId duplicate. |
| AC-011 | Duplicate/replay paths preserve previous/resulting version equality. | Replay/duplicate tests assert version invariants. |
| AC-012 | `VALID_AFTER_REVALIDATION` remains a valid result, not rejection. | Test: `validAfterRevalidationRemainsNonTerminalWarning`. |
| AC-013 | `TOPOLOGY_VERSION_CONFLICT` remains covered or is added. | Test: `wrongHabitatTopologyVersionScopeReturnsConflict`. |
| AC-014 | No MU-014 code path bypasses `validateTopology(...)`. | No direct topology save from materializer; tests still pass MU-013 validation suite. |
| AC-015 | Materializer does not depend on concrete H2/JDBC/SQL/DataSource/ObjectMapper. | Static inspection or reflection test; imports/fields show only port dependency. |
| AC-016 | MU-011 structural persistence vs health/state separation remains intact. | Existing `PersistenceBoundaryHardeningTest` passes. |
| AC-017 | MU-012 `TopologyMaterializationStatePort` boundary remains intact. | Existing architecture assertions pass; materializer uses ports. |
| AC-018 | MU-013 Room/Zone/LOCATED_IN coherence checks remain intact. | Existing `RoomZoneTopologySeedTest` passes. |
| AC-019 | No SC-B, SC-D, NATS, JetStream, Projection, Hub, Session, Identity, Authority or Policy dependency introduced. | Static/package scan or implementation report states no new dependencies. |
| AC-020 | Implementation report states H2/InMemory tests do not prove production-grade concurrent write safety. | Required statement in implementation-report.md. |
| AC-021 | Any concurrent/interleaved test states that full concurrent same-fact atomicity requires SQLite/WAL plus transaction/lock/claim protocol, and is not proven by H2/InMemory tests. | Test comment and implementation report. |
| AC-021A | All `DefaultTopologyMaterializationService` constructor call sites are updated, including the six known audited test call sites. | Compile passes; implementation report lists updated call sites or states grep found no additional sites. |
| AC-021B | Replayed `emittedChanges`, if returned, are treated as historical decision evidence and not as newly emitted events. | Test verifies no new event append on replay; implementation report states replayed changes are not re-emitted. |
| AC-022 | Existing tests continue to pass. | Final `mvn test` report. |
| AC-023 | New tests cover same-fact replay, semantic duplicate with different factId, stale topology revalidation and duplicate non-advancement. | `ConcurrencyIdempotencySeedTest` and/or existing tests extended. |
| AC-024 | Build/test command succeeds. | `mvn test` success with final test count. |

---

## 2. Recommended test matrix

| Test | Covers |
|---|---|
| `sameFactIdReplaysStoredMaterializationDecisionWithoutVersionAdvance` | AC-003, AC-004, AC-005, AC-006, AC-010, AC-011 |
| `replayDoesNotAppendNewTopologyChangedEvent` | AC-004, AC-005, AC-021B, FS-006 |
| `replaySurvivesRepositoryAndServiceRecreation` | AC-007A, AC-007B |
| `sameCanonicalDeviceDifferentFactIdRejectedAsDuplicateWithoutVersionAdvance` | AC-008, AC-009, AC-010 |
| `sameCanonicalRoomDifferentFactIdRejectedAsDuplicateWithoutVersionAdvance` | AC-008, AC-009, AC-010 |
| `sameCanonicalZoneDifferentFactIdRejectedAsDuplicateWithoutVersionAdvance` | AC-008, AC-009, AC-010 |
| `rejectedUnauthorizedAdapterDecisionIsReplayable` | AC-007 |
| `validAfterRevalidationRemainsNonTerminalWarning` | AC-012 |
| `wrongHabitatTopologyVersionScopeReturnsConflict` | AC-013 |
| `serializedMutationDocumentationTest` | AC-020, AC-021 |


## 2.1 Constructor call-site coverage

At the audited baseline, the execution package must update all constructor call sites, including:

```text
TopologyMaterializationSeedTest.java x3
PersistenceBoundaryHardeningTest.java x1
RoomZoneTopologySeedTest.java x2
```

The implementation must grep/search for any additional `DefaultTopologyMaterializationService` constructor call site and update it with the same pattern.

---

## 3. Existing regression suites that must remain green

```text
BaseTopologyServiceTest
CoreSnapshotQuerySeedTest
PersistenceBoundaryHardeningTest
PersistenceMemorySeedTest
RoomZoneTopologySeedTest
ScCoreKernelHardeningTest
TopologyMaterializationSeedTest
TopologyVersionHardeningTest
```

---

## 4. Failure signals to check during review

| FS | Review question |
|---:|---|
| FS-001 | Did any target `TopologyFact` lack `factId`? |
| FS-002 | Did implementation require broad sealed hierarchy changes? |
| FS-003 | Did H2-backed replay storage fail for a concrete reason? |
| FS-004 | Did replay storage violate port boundaries? |
| FS-005 | Did replay or duplicate rejection advance topologyVersion? |
| FS-006 | Did replay append newly emitted `TopologyChanged` events or grow `BaseTopologyService.emittedEvents()`? |
| FS-007 | Was a different factId for same canonical entity treated as replay? |
| FS-008 | Was `VALID_AFTER_REVALIDATION` converted to rejection? |
| FS-009 | Did target revalidation require ActionRequest, SC-B or adapter dispatch? |
| FS-010 | Did tests require topology removal APIs that do not exist? |
| FS-011 | Did implementation require TemporalActs, outbox/ledger or SC-B runtime? |
| FS-012 | Did SQL/JDBC leak into domain/materialization services? |
| FS-013 | Did SC-B delivery success become semantic idempotency authority? |
| FS-014 | Did user/session/authority/policy/projection metadata enter replay identity? |
| FS-015 | Did the implementation leave any audited constructor call site unpatched? |
| FS-016 | Did the implementation claim H2/InMemory tests prove production-grade concurrent same-fact atomicity? |
| FS-015 | Did existing validated MU behavior regress? |

---

## 5. Evidence expected in implementation-report.md

```text
Branch:
Commit:
Files added:
Files modified:
Replay port name:
Replay adapter:
Replay table:
Replay key:
Final mvn test result:
Final test count:
New tests added:
Existing regression suites status:
Known limitations:
Deferred items preserved:
```
