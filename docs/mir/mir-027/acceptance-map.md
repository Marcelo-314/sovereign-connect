# acceptance-map — MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001

```text
Document ID:  acceptance-map-MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001
Version:      v0.1.0
Status:       Execution Package Acceptance Map
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B
MU Slot:      MU-027
```

---

## Acceptance criteria mapping

| AC | Criterion | Verification |
|---|---|---|
| AC-027-001 | H3-only scope | MIR/context negative scope; implementation report changed files |
| AC-027-002 | Does not reimplement H1 dispatch state persistence | No changes beyond observation repository; implementation report rationale |
| AC-027-003 | Does not reimplement H2 outbox bridge projection | No changes to integration.scledgerdispatch except none expected; architecture/source inspection |
| AC-027-004 | No NATS/JetStream/broker dependencies | ScBusObservationPersistenceArchitectureTest.noBrokerDependencyIntroduced |
| AC-027-005 | No lifecycle-channel runtime or SC-D onboarding | Changed files + implementation report retained debt |
| AC-027-006 | No productive ScdCommand or SC-D fact family | Changed files + grep/source inspection |
| AC-027-007 | Does not mutate SC-C OutboxEntryStatus or ledger | Changed files; no core.scledger mutation changes |
| AC-027-008 | bus.** production does not import core.** | ScBusObservationPersistenceArchitectureTest.busRuntimePersistenceDoesNotImportCore |
| AC-027-009 | core.** production does not import bus.runtime.** | ScBusObservationPersistenceArchitectureTest.coreDoesNotImportBusRuntime |
| AC-027-010 | Persistent adapter exists | JdbcDispatchObservationRepository.java |
| AC-027-011 | Adapter supports record(...) | JdbcDispatchObservationRepository.record + tests |
| AC-027-012 | Adapter supports observationsFor(...) | JdbcDispatchObservationRepository.observationsFor + tests |
| AC-027-013 | InMemory repository remains usable | No removal/change; compile/tests |
| AC-027-014 | JDBC/Flyway storage without JPA | Repository + migration + pom inspection |
| AC-027-015 | Observations survive repository recreation | JdbcDispatchObservationRepositoryTest.observationSurvivesRepositoryRecreation |
| AC-027-016 | Unknown dispatchRecordId returns empty list | JdbcDispatchObservationRepositoryTest.observationsForUnknownDispatchRecordIdReturnsEmptyList |
| AC-027-017 | Deterministic ordering | JdbcDispatchObservationRepositoryTest.multipleObservationsReturnedInObservedAtOrder |
| AC-027-018 | Fields preserved | JdbcDispatchObservationRepositoryTest.recordPersistsObservationWithAllMandatoryFields |
| AC-027-019 | CLAIMED observation persisted | RuntimeDispatchObservationPersistenceTest.claimEmitsPersistentClaimedObservation |
| AC-027-020 | DISPATCHING observation persisted | RuntimeDispatchObservationPersistenceTest.dispatchEmitsPersistentDispatchingAndDispatchedObservations |
| AC-027-021 | DISPATCHED observation persisted | RuntimeDispatchObservationPersistenceTest.dispatchEmitsPersistentDispatchingAndDispatchedObservations |
| AC-027-022 | NO_HANDLER observation persisted | RuntimeDispatchObservationPersistenceTest.noHandlerEmitsPersistentDeliveryFailedObservation |
| AC-027-023 | DELIVERY_FAILED observation persisted | RuntimeDispatchObservationPersistenceTest.noHandlerEmitsPersistentDeliveryFailedObservation and repository failure-code tests |
| AC-027-024 | RETRY_SCHEDULED observation persisted | RuntimeDispatchObservationPersistenceTest.retryEmitsPersistentRetryScheduledObservation |
| AC-027-025 | EXHAUSTED observation persisted | RuntimeDispatchObservationPersistenceTest.exhaustionEmitsPersistentExhaustedObservation |
| AC-027-026 | CANCELLED_BY_SUPERSEDE observation persisted | RuntimeDispatchObservationPersistenceTest.supersedeCancellationEmitsPersistentCancelledObservation |
| AC-027-027 | DISPATCHED not semantic success | RuntimeDispatchObservationPersistenceTest.dispatchedObservationIsTechnicalNotSemantic + implementation report |
| AC-027-028 | DELIVERY_FAILED not domain failure | Implementation report; comments/assertions in tests |
| AC-027-029 | EXHAUSTED not provider/device failure | RuntimeDispatchObservationPersistenceTest.exhaustedObservationIsTechnicalNotSemantic |
| AC-027-030 | Observations not SC-C terminal request state | No core terminal response writes; implementation report |
| AC-027-031 | No adapter admission/policy/visibility decisions | Changed files + implementation report retained debts |
| AC-027-032 | Failure code/reason sanitized before persistence | RuntimeDispatchService existing sanitizedReason path + repository tests |
| AC-027-033 | No raw provider payloads/credentials/stacks persisted | Implementation report + source inspection |
| AC-027-034 | Optional correlation metadata technical only | Not implemented or nullable technical-only if added; implementation report |
| AC-027-035 | ScDeliveryError prep not emitted as productive event | No event emission; implementation report retained debt |
| AC-027-036 | Migration creates only sc_b_* objects | ScBusObservationPersistenceArchitectureTest.scBObservationMigrationCreatesOnlyScBTables |
| AC-027-037 | Migration does not collide with V1-V100 | ScBusObservationPersistenceArchitectureTest.scBObservationMigrationUsesV101 |
| AC-027-038 | Migration strategy recorded | implementation-report.md |
| AC-027-039 | Branch/commit/files recorded | implementation-report.md |
| AC-027-040 | Test commands and summary recorded | implementation-report.md |
| AC-027-041 | Every AC mapped | acceptance-map.md + implementation-report.md |
| AC-027-042 | Retained debts recorded | implementation-report.md |

---

## Required test delta

```text
JdbcDispatchObservationRepositoryTest:        11 tests
RuntimeDispatchObservationPersistenceTest:     8 tests
ScBusObservationPersistenceArchitectureTest:   5 tests
Architecture test update:                      existing test changed for V101
Expected full regression:                    >= 378 tests, 0 failures, 0 errors, 0 skipped
```
