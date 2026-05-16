# implementation-report.md - MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001

```text
Branch: feat/sc-c-concurrency-idempotency-seed
Commit: not created by Codex
Replay port name: MaterializationDecisionReplayPort
Replay adapter: H2BaseTopologyRepository
Replay table: materialization_decision_replay
Replay key: habitatId + factId
Final mvn test result: 62 tests, 0 failures, 0 errors, 0 skipped
```

## Files Added

```text
src/main/java/com/sovereign/connect/core/topology/port/MaterializationDecisionReplayPort.java
src/test/java/com/sovereign/connect/core/topology/ConcurrencyIdempotencySeedTest.java
docs/mir/mir-014/implementation-report.md
```

## Files Modified

```text
src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
src/main/java/com/sovereign/connect/core/topology/materialization/DefaultTopologyMaterializationService.java
src/test/java/com/sovereign/connect/core/topology/PersistenceBoundaryHardeningTest.java
src/test/java/com/sovereign/connect/core/topology/PersistenceMemorySeedTest.java
src/test/java/com/sovereign/connect/core/topology/RoomZoneTopologySeedTest.java
src/test/java/com/sovereign/connect/core/topology/TopologyMaterializationSeedTest.java
```

## Implementation Summary

`DefaultTopologyMaterializationService` now checks `MaterializationDecisionReplayPort`
before executing any materialization branch. A stored decision for the same
`habitatId + factId` is returned directly, so replay does not call structural
mutation helpers, advance `topologyVersion`, or append new `TopologyChanged` events.

`H2BaseTopologyRepository` implements the replay port with the H2-backed
`materialization_decision_replay` table. Accepted and rejected decisions are recorded
with nullable previous/resulting version values and serialized historical
`emittedChanges`.

Constructor call sites were updated by grep. The six audited test call sites now pass
the H2 repository as both `TopologyMaterializationStatePort` and
`MaterializationDecisionReplayPort`.

## New Tests Added

```text
sameFactIdReplaysStoredDecisionWithoutVersionAdvance
replaySurvivesRepositoryAndServiceRecreation
sameCanonicalDeviceDifferentFactIdRejectedAsDuplicate
sameCanonicalRoomDifferentFactIdRejectedAsDuplicate
sameCanonicalZoneDifferentFactIdRejectedAsDuplicate
rejectedDecisionIsReplayable
validAfterRevalidationRemainsNonRejection
wrongHabitatScopeReturnsConflict
```

## Validation

```text
mvn compile: success
mvn test: success
Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
```

Existing regression suites remain green:

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

## Known Limitations

The tests validate seed replay/idempotency behavior under serialized assumptions.
They do not prove production-grade concurrent structural write safety for H2 or
in-memory repositories. Production concurrent write safety remains a storage-layer
and transaction/claim-protocol concern for the SQLite/WAL production profile.

Replayed `emittedChanges` are historical decision evidence only. They are returned
as part of the stored `MaterializationDecision`, but are not re-emitted through
`BaseTopologyService.emittedEvents()`.

## Deferred Items Preserved

MU-014 did not implement generic command/request idempotency, terminal response
persistence, `sc_c_terminal_responses`, in-flight request lifecycle, TemporalAct
engine behavior, outbox/ledger tables, SC-B runtime, NATS/JetStream integration,
projection/session/identity/authority/policy code, production SQLite migrations,
provider binding fingerprint migration, or Room/Zone removal.
