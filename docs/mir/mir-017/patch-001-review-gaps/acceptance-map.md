# Acceptance Map — MU-017 Patch 001 Review Gaps

**Package:** `execution-package-MU-017-review-gaps-patch-001`  
**Version:** `v0.2.1`  
**Target:** MU-017 patch closure after review report v0.1.0  

---

## Acceptance criteria

| ID | Requirement | Verification |
|---|---|---|
| P17-001 | `findLocatedDevices` preserves H2 fallback semantics. | `findLocatedDevicesFallsBackToDeviceRoomIdWhenRelationMissing`; `findLocatedDevicesFallsBackToDeviceZoneIdWhenRelationMissing` |
| P17-002 | `findLocatedEndpoints` preserves H2 fallback semantics. | `findLocatedEndpointsFallsBackToEndpointRoomIdWhenRelationMissing`; `findLocatedEndpointsFallsBackToEndpointZoneIdWhenRelationMissing` |
| P17-003 | Located query result is union of LOCATED_IN relation and direct `room_id`/`zone_id` columns. | Test assertions or implementation report code reference |
| P17-004 | Located query results are deduplicated by canonical id. | `locatedQueryDeduplicatesRelationAndColumnFallbackMatches` or equivalent |
| P17-005 | Structural save seeds endpoint health when no durable row exists. | `initialEndpointHealthRoundTripsThroughSQLiteWithoutExplicitHealthWrite` |
| P17-006 | Structural save seeds an `endpoint_health` row for every endpoint if absent. | `everyPersistedEndpointGetsEndpointHealthRowIfAbsent` |
| P17-007 | Structural save does not overwrite existing durable endpoint health. | `structuralSaveDoesNotOverwriteExistingEndpointHealthInSQLite` |
| P17-008 | Endpoint health hydration prefers durable `endpoint_health` rows. | B-02 tests plus code reference |
| P17-009 | `BaseTopologyService.validateTopology(...)` rejects duplicate habitat-global `capability_id`. | `duplicateCapabilityIdAcrossOwnersRejectedBeforePersistence` |
| P17-010 | Duplicate capability validation covers device-level and endpoint-level capabilities. | Fixture uses same capabilityId in `deviceCapabilities()` and endpoint `capabilities()` |
| P17-011 | Duplicate capability rejection occurs before SQLite PK violation is first visible signal. | Test uses service/domain validation path and asserts validation message |
| P17-012 | `SQLiteMaterializationDecisionReplayRepository` uses injected `Clock`. | `materializationDecisionReplayUsesInjectedClock` |
| P17-013 | Normalized tables remain authority; `topology_json` is not re-promoted. | No code path reads `topology_json` for individual CTI queries; existing corruption tests remain passing |
| P17-014 | H2 and InMemory are not production-selected topology repositories. | Existing Spring context tests remain passing |
| P17-015 | Ledger/outbox remain outside topology repositories. | No `ScLedgerWritePort` / `ScOutboxWritePort` implementation added to topology repos |
| P17-016 | Working tree / evidence package is clean. | `git status --short`; no `.idea`, `mir-001/mir-002`, `#U2014`, or `target/*.sqlite` noise |
| P17-017 | Full suite passes. | `mvn test` summary |
| P17-018 | Implementation report maps every review finding to closed/deferred status. | Implementation report review |

---

## Required tests

```text
findLocatedDevicesFallsBackToDeviceRoomIdWhenRelationMissing
findLocatedDevicesFallsBackToDeviceZoneIdWhenRelationMissing
findLocatedEndpointsFallsBackToEndpointRoomIdWhenRelationMissing
findLocatedEndpointsFallsBackToEndpointZoneIdWhenRelationMissing
locatedQueryDeduplicatesRelationAndColumnFallbackMatches
initialEndpointHealthRoundTripsThroughSQLiteWithoutExplicitHealthWrite
structuralSaveDoesNotOverwriteExistingEndpointHealthInSQLite
everyPersistedEndpointGetsEndpointHealthRowIfAbsent
duplicateCapabilityIdAcrossOwnersRejectedBeforePersistence
materializationDecisionReplayUsesInjectedClock
```

---

## Pass / fail rule

This patch is accepted only if:

```text
B-01 CLOSED
B-02 CLOSED
B-03 CLOSED
H-04 CLOSED
G-01 CLOSED
mvn test passes
implementation report is truthful and clean
```

H-01/H-02/H-03 may remain bounded documented debts.
