# Implementation Report - MU-026 SC-B Outbox Bridge Seed

```text
Document ID:  IMPLEMENTATION-REPORT-MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
Version:      v0.2.0-candidate
MIR:          MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001 v0.2.0-candidate
MU:           MU-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
Slot:         MU-026
Branch:       feat/sc-b-mir-026-outbox-bridge-seed
Status:       Validated L4
```

## 1. Implementation summary

```text
Branch: feat/sc-b-mir-026-outbox-bridge-seed
Implementation commit: 51f3dbda43a93635a6945e22d3e5e2b2837eaec1
Evidence/docs commit: pending
Executor: Codex
Date: 2026-05-29
```

Implemented H2 outbox bridge seed only. Added the SC-C outbox dispatch read port, exposed pending outbox rows through the SQLite repository, and created `integration.scledgerdispatch` as the sole production bridge that can see both SC-C scledger and SC-B runtime surfaces.

The bridge derives deterministic dispatch candidates from `OutboxEntry` without parsing `semanticPayloadJson`, mutating `OutboxEntryStatus`, creating `ScdCommand`, adding broker dependencies, or adding any new SC-B migration.

## 2. Changed files

```text
A docs/mir/mir-026/MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001.md
A docs/mir/mir-026/acceptance-map.md
A docs/mir/mir-026/code-surface-audit.md
A docs/mir/mir-026/codex-prompt.md
A docs/mir/mir-026/context.md
A docs/mir/mir-026/execution-package-manifest.md
A docs/mir/mir-026/implementation-report.md
A src/main/java/com/sovereign/connect/core/scledger/port/ScOutboxDispatchReadPort.java
M src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteScLedgerOutboxRepository.java
A src/main/java/com/sovereign/connect/integration/scledgerdispatch/DispatchRecordIdFactory.java
A src/main/java/com/sovereign/connect/integration/scledgerdispatch/DeliveryLaneToScBusLaneMapper.java
A src/main/java/com/sovereign/connect/integration/scledgerdispatch/OutboxEntryDispatchProjector.java
A src/main/java/com/sovereign/connect/integration/scledgerdispatch/ScLedgerDispatchCandidateReadAdapter.java
A src/test/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteScOutboxDispatchReadPortTest.java
A src/test/java/com/sovereign/connect/integration/scledgerdispatch/OutboxEntryDispatchProjectorTest.java
A src/test/java/com/sovereign/connect/integration/scledgerdispatch/ScLedgerDispatchCandidateReadAdapterTest.java
A src/test/java/com/sovereign/connect/integration/scledgerdispatch/ScLedgerDispatchBridgeRuntimeTest.java
A src/test/java/com/sovereign/connect/bus/ScBusOutboxBridgeArchitectureTest.java
M src/test/java/com/sovereign/connect/bus/ScBusHardeningArchitectureTest.java
```

## 3. Design choices

```text
dispatchRecordId strategy:
  UUID.nameUUIDFromBytes(("sc-b.dispatch:" + outboxEntryId).getBytes(UTF_8))

Raw outboxEntryId reused as dispatchRecordId:
  no

sourceRecordId:
  OutboxEntry.outboxEntryId

partitionKey:
  OutboxEntry.habitatId

logicalTopic:
  OutboxEntry.logicalTopic preserved as-is

causationId:
  OutboxEntry.ledgerEntryId
```

SIGNAL decision:

```text
TIMER_FIRED_SIGNAL + logicalTopic=sc-c.timer-fired:
  mapped to ScBusLane.EVENT

Other SIGNAL records:
  skipped as non-dispatchable

SIGNAL -> ScBusLane.COMMAND:
  forbidden and test-covered
```

Migration strategy:

```text
New migration added: no
H2 uses the existing V100 SC-B dispatch state schema.
```

Critical confirmations:

```text
findDispatchableEntries SELECT includes exactly the 13 OutboxEntry fields: yes
findDispatchableEntries SELECT excludes attempt_count: yes
Verified SIGNAL topic used: sc-c.timer-fired: yes
ScLedgerDispatchBridgeRuntimeTest uses manual 7-dependency RuntimeDispatchService wiring: yes
```

## 4. Validation commands

```bash
mvn -q compile
mvn -q test -Dtest="SQLiteScOutboxDispatchReadPortTest"
mvn -q test -Dtest="OutboxEntryDispatchProjectorTest,ScLedgerDispatchCandidateReadAdapterTest"
mvn -q test -Dtest="ScLedgerDispatchBridgeRuntimeTest,ScBusOutboxBridgeArchitectureTest,ScBusHardeningArchitectureTest,ScBusArchitectureTest"
mvn -q test
```

Results:

```text
SQLiteScOutboxDispatchReadPortTest:
  total:    5
  failures: 0
  errors:   0
  skipped:  0

OutboxEntryDispatchProjectorTest + ScLedgerDispatchCandidateReadAdapterTest:
  total:    19
  failures: 0
  errors:   0
  skipped:  0

Full sovereign-connect regression:
  total:    354
  failures: 0
  errors:   0
  skipped:  0

bus/integration delta:
  total:    37

SC-C/non-bus baseline:
  total:    240
  status:   still green in full regression

EIB:
  not run; MU-026 modifies only the sovereign-connect Maven module.
```

## 5. Acceptance criteria status

| AC | Status | Evidence |
|---|---|---|
| AC-026-001 | PASS | `ScOutboxDispatchReadPort.java`; SQLite read-port tests. |
| AC-026-002 | PASS | `integration.scledgerdispatch` package created. |
| AC-026-003 | PASS | `ScBusOutboxBridgeArchitectureTest.busDoesNotImportCoreScledger`. |
| AC-026-004 | PASS | `ScBusOutboxBridgeArchitectureTest.coreDoesNotImportBusRuntime`. |
| AC-026-005 | PASS | `integrationScledgerdispatchIsOnlyPackageImportingBothScledgerAndBus`. |
| AC-026-006 | PASS | `noBrokerDependencyIntroduced`. |
| AC-026-007 | PASS | `noProductionScdCommandIntroduced`. |
| AC-026-008 | PASS | `dispatchObservationPersistenceStillDeferred`. |
| AC-026-009 | PASS | `findDispatchableEntriesReturnsOnlyPendingRows`. |
| AC-026-010 | PASS | `findDispatchableEntriesRejectsNonPositiveLimit`. |
| AC-026-011 | PASS | `findDispatchableEntriesAppliesLimitAndStableOrdering`. |
| AC-026-012 | PASS | `findDispatchableEntriesAppliesLimitAndStableOrdering`. |
| AC-026-013 | PASS | `findDispatchableEntriesMapsAllOutboxEntryFields`. |
| AC-026-014 | PASS | `findDispatchableEntriesDoesNotMutateOutboxStatusOrUpdatedAt`. |
| AC-026-015 | PASS | `outboxEntryIdMapsToSourceRecordId`. |
| AC-026-016 | PASS | `dispatchRecordIdIsDeterministicForSameOutboxEntry`. |
| AC-026-017 | PASS | `dispatchRecordIdIsDistinctFromRawOutboxEntryId`. |
| AC-026-018 | PASS | `partitionKeyAndLogicalTopicArePreserved`. |
| AC-026-019 | PASS | `partitionKeyAndLogicalTopicArePreserved`. |
| AC-026-020 | PASS | Projector non-null ID assertions and runtime dispatch tests. |
| AC-026-021 | PASS | `semanticPayloadJsonIsNotParsedIntoDomainCommand`. |
| AC-026-022 | PASS | `commandEventAndResponseLanesMapDirectly`. |
| AC-026-023 | PASS | `commandEventAndResponseLanesMapDirectly`. |
| AC-026-024 | PASS | `commandEventAndResponseLanesMapDirectly`. |
| AC-026-025 | PASS | `signalNeverMapsToCommand`. |
| AC-026-026 | PASS | `timerFiredSignalOnScopedTopicMapsToEvent`; runtime signal test. |
| AC-026-027 | PASS | `nonScopedSignalIsSkipped`. |
| AC-026-028 | PASS | `emitsCandidateWhenNoDispatchRecordExists`. |
| AC-026-029 | PASS | `emitsCandidateWhenDispatchStateIsRetryScheduled`. |
| AC-026-030 | PASS | Skip-state candidate reader tests. |
| AC-026-031 | PASS | `pendingCandidatesDoesNotClaimOrTransitionDispatchState`. |
| AC-026-032 | PASS | `repeatedReadsAfterDispatchedDoNotReemitSameCandidate`. |
| AC-026-033 | PASS | `pendingEventOutboxRowDispatchesThroughRuntimeDispatchServiceToEventHandler`. |
| AC-026-034 | PASS | TIMER_FIRED_SIGNAL is mapped to EVENT and runtime-tested. |
| AC-026-035 | PASS | `commandOutboxRowDispatchesToCommandLaneWithoutScdCommand`. |
| AC-026-036 | PASS | `dispatchDoesNotMutateOutboxStatus`. |
| AC-026-037 | PASS | `dispatchStatePersistenceRecordsTechnicalDispatchForDerivedRecordId`. |
| AC-026-038 | PASS | Branch, pending commit slot and changed files recorded here. |
| AC-026-039 | PASS | Final test counts recorded here. |
| AC-026-040 | PASS | SIGNAL decision recorded here. |
| AC-026-041 | PASS | H3 observation persistence debt retained below. |
| AC-026-042 | PASS | No NATS/ScdCommand/status mutation confirmed here and by architecture tests. |
| AC-026-043 | PASS | `acceptance-map.md` plus this report link all criteria to evidence. |

## 6. Boundary confirmations

```text
No NATS / JetStream / broker dependency: yes
No production ScdCommand: yes
No OutboxEntryStatus mutation: yes
No dispatch observation persistence: yes
No lifecycle channel implementation: yes
No new SC-B migration: yes
No semanticPayloadJson parsing: yes
bus.** does not import core.**: yes
core.** does not import bus.runtime.**: yes
integration.scledgerdispatch is the bridge package: yes
```

## 7. Retained debts

```text
DEBT-B-RD-H-003 - Dispatch observation persistence absent. Retained for H3.
DEBT-B-RD-H-004 - Lifecycle channel not implemented.
DEBT-B-RD-H-005 - NATS / JetStream physical binding absent.
DEBT-B-RD-H-006 - ScdCommand shape absent.
DEBT-B-RD-H-007 - Fact family types absent.
DEBT-B-RD-H-008 - Adapter-scoped route assignment absent.
DEBT-B-RD-H-009 - Hot onboarding protocol absent.
DEBT-B-RD-H-010 - Terminal request-state integration remains SC-C-owned and not closed by H2.
```

## 8. Final verdict

```text
MU-SOV-SC-B-OUTBOX-BRIDGE-SEED-001:
  Validated L4
```
