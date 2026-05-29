# Acceptance Map — MU-026 SC-B Outbox Bridge Seed

```text
Version: v0.2.0-candidate
MIR:     MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001 v0.2.0-candidate
MU:      MU-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
Slot:    MU-026
```

## Acceptance criteria mapping

| AC | Requirement | Evidence |
|---|---|---|
| `AC-026-001` | ScOutboxDispatchReadPort in core.scledger.port | ScOutboxDispatchReadPort.java; SQLiteScOutboxDispatchReadPortTest |
| `AC-026-002` | integration.scledgerdispatch package exists | source tree; ScBusOutboxBridgeArchitectureTest |
| `AC-026-003` | bus.** does not import core.** | ScBusOutboxBridgeArchitectureTest.busDoesNotImportCoreScledger |
| `AC-026-004` | core.** does not import bus.runtime.** | ScBusOutboxBridgeArchitectureTest.coreDoesNotImportBusRuntime |
| `AC-026-005` | only integration bridge imports both scledger and bus | ScBusOutboxBridgeArchitectureTest.integrationScledgerdispatchIsOnlyPackageImportingBothScledgerAndBus |
| `AC-026-006` | no broker dependency | ScBusOutboxBridgeArchitectureTest.noBrokerDependencyIntroduced; pom inspection |
| `AC-026-007` | no production ScdCommand | ScBusOutboxBridgeArchitectureTest.noProductionScdCommandIntroduced |
| `AC-026-008` | observation persistence deferred | ScBusOutboxBridgeArchitectureTest.dispatchObservationPersistenceStillDeferred; implementation report |
| `AC-026-009` | read port returns only PENDING | SQLiteScOutboxDispatchReadPortTest.findDispatchableEntriesReturnsOnlyPendingRows |
| `AC-026-010` | read port rejects non-positive limit | SQLiteScOutboxDispatchReadPortTest.findDispatchableEntriesRejectsNonPositiveLimit |
| `AC-026-011` | read port applies limit | SQLiteScOutboxDispatchReadPortTest.findDispatchableEntriesAppliesLimitAndStableOrdering |
| `AC-026-012` | read port deterministic order | SQLiteScOutboxDispatchReadPortTest.findDispatchableEntriesAppliesLimitAndStableOrdering |
| `AC-026-013` | read port maps all OutboxEntry fields | SQLiteScOutboxDispatchReadPortTest.findDispatchableEntriesMapsAllOutboxEntryFields |
| `AC-026-014` | read method does not mutate status/updatedAt | SQLiteScOutboxDispatchReadPortTest.findDispatchableEntriesDoesNotMutateOutboxStatusOrUpdatedAt |
| `AC-026-015` | outboxEntryId maps to sourceRecordId | OutboxEntryDispatchProjectorTest.outboxEntryIdMapsToSourceRecordId |
| `AC-026-016` | dispatchRecordId deterministic; no random generation | OutboxEntryDispatchProjectorTest.dispatchRecordIdIsDeterministicForSameOutboxEntry |
| `AC-026-017` | dispatchRecordId distinct from outboxEntryId or justified | OutboxEntryDispatchProjectorTest.dispatchRecordIdIsDistinctFromRawOutboxEntryId; implementation report |
| `AC-026-018` | partitionKey maps to habitatId | OutboxEntryDispatchProjectorTest.partitionKeyAndLogicalTopicArePreserved |
| `AC-026-019` | logicalTopic preserved | OutboxEntryDispatchProjectorTest.partitionKeyAndLogicalTopicArePreserved |
| `AC-026-020` | correlation/causation/message IDs valid | OutboxEntryDispatchProjectorTest; RuntimeDispatchService runtime tests |
| `AC-026-021` | semanticPayloadJson not parsed to domain command | OutboxEntryDispatchProjectorTest.semanticPayloadJsonIsNotParsedIntoDomainCommand; code review |
| `AC-026-022` | COMMAND maps to COMMAND | OutboxEntryDispatchProjectorTest.commandEventAndResponseLanesMapDirectly |
| `AC-026-023` | EVENT maps to EVENT | OutboxEntryDispatchProjectorTest.commandEventAndResponseLanesMapDirectly |
| `AC-026-024` | RESPONSE maps to RESPONSE | OutboxEntryDispatchProjectorTest.commandEventAndResponseLanesMapDirectly |
| `AC-026-025` | SIGNAL never maps to COMMAND | OutboxEntryDispatchProjectorTest.signalNeverMapsToCommand |
| `AC-026-026` | TIMER_FIRED_SIGNAL/sc-c.timer-fired handled by seed rule | OutboxEntryDispatchProjectorTest.timerFiredSignalOnScopedTopicMapsToEvent; report |
| `AC-026-027` | non-scoped SIGNAL skipped/rejected | OutboxEntryDispatchProjectorTest.nonScopedSignalIsSkipped |
| `AC-026-028` | emit candidate with no dispatch state | ScLedgerDispatchCandidateReadAdapterTest.emitsCandidateWhenNoDispatchRecordExists |
| `AC-026-029` | emit candidate when RETRY_SCHEDULED | ScLedgerDispatchCandidateReadAdapterTest.emitsCandidateWhenDispatchStateIsRetryScheduled |
| `AC-026-030` | skip non-claimable/terminal states | ScLedgerDispatchCandidateReadAdapterTest skip-state methods |
| `AC-026-031` | candidate read does not claim/transition | ScLedgerDispatchCandidateReadAdapterTest.pendingCandidatesDoesNotClaimOrTransitionDispatchState |
| `AC-026-032` | repeated reads after DISPATCHED do not re-emit | ScLedgerDispatchCandidateReadAdapterTest.repeatedReadsAfterDispatchedDoNotReemitSameCandidate |
| `AC-026-033` | PENDING EVENT dispatches through runtime | ScLedgerDispatchBridgeRuntimeTest.pendingEventOutboxRowDispatchesThroughRuntimeDispatchServiceToEventHandler |
| `AC-026-034` | SIGNAL outcome explicitly recorded and tested | ScLedgerDispatchBridgeRuntimeTest.timerFiredSignalDispatchesThroughRuntimeDispatchServiceToEventHandler; report AC-026-040 |
| `AC-026-035` | COMMAND row dispatches without ScdCommand | ScLedgerDispatchBridgeRuntimeTest.commandOutboxRowDispatchesToCommandLaneWithoutScdCommand; architecture test |
| `AC-026-036` | no OutboxEntryStatus mutation after dispatch | ScLedgerDispatchBridgeRuntimeTest.dispatchDoesNotMutateOutboxStatus |
| `AC-026-037` | dispatch state records technical state | ScLedgerDispatchBridgeRuntimeTest.dispatchStatePersistenceRecordsTechnicalDispatchForDerivedRecordId |
| `AC-026-038` | report records branch/commits/files | implementation-report.md |
| `AC-026-039` | report records final test counts | implementation-report.md |
| `AC-026-040` | report records SIGNAL decision | implementation-report.md |
| `AC-026-041` | report records H3 debt | implementation-report.md |
| `AC-026-042` | report confirms no NATS/ScdCommand/status mutation | implementation-report.md; architecture tests |
| `AC-026-043` | acceptance map links each AC | this acceptance-map.md |

## Expected validation

```text
STOP-1 mvn -q compile                                      PASS
STOP-2 SQLiteScOutboxDispatchReadPortTest                   PASS
STOP-3 Projector + candidate reader tests                   PASS
STOP-4 Runtime + architecture tests                         PASS
STOP-5 mvn -q test                                          >=354 tests, 0 failures, 0 errors, 0 skipped
SC-C/non-bus baseline                                       240 tests still green
```
## Operational notes

```text
attempt_count MUST NOT appear in the SELECT used by findDispatchableEntries.
The verified TIMER_FIRED_SIGNAL logical topic is exactly sc-c.timer-fired.
ScLedgerDispatchBridgeRuntimeTest MUST use manual seven-dependency RuntimeDispatchService wiring.
```
