# Implementation Report - MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001

```text
Document ID:  implementation-report-MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001
Version:      v0.1.0
Status:       Validated L4
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B
MU Slot:      MU-027
```

## 1. Implementation identity

```text
Branch: feat/sc-b-mir-027-dispatch-observation-persistence
Implementation commit: pending
Evidence/docs commit: pending
Executor: Codex
Final pushed state: pending
```

## 2. Scope executed

Implemented H3 dispatch observation persistence only:

```text
JdbcDispatchObservationRepository implements DispatchObservationPort.
V101__sc_b_dispatch_observation_persistence.sql creates sc_b_dispatch_observations.
Dispatch observations are restart-visible and queryable by dispatchRecordId.
RuntimeDispatchService uses the existing DispatchObservationPort hook unchanged.
Architecture tests now allow V100 + V101 SC-B hardening migrations.
```

Not implemented:

```text
NATS / JetStream
lifecycle-channel runtime
productive ScDeliveryError event emission
productive ScdCommand
SC-D adapter execution
mutation of SC-C OutboxEntryStatus / ledger records
CANDIDATE_SELECTED emission
```

## 3. Changed files

```text
A docs/mir/mir-027/MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001.md
A docs/mir/mir-027/acceptance-map.md
A docs/mir/mir-027/code-surface-audit.md
A docs/mir/mir-027/codex-prompt.md
A docs/mir/mir-027/context.md
A docs/mir/mir-027/execution-package-manifest.md
A docs/mir/mir-027/implementation-report.md
A src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchObservationRepository.java
A src/main/resources/db/migration/V101__sc_b_dispatch_observation_persistence.sql
A src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchObservationRepositoryTest.java
A src/test/java/com/sovereign/connect/bus/runtime/dispatch/RuntimeDispatchObservationPersistenceTest.java
A src/test/java/com/sovereign/connect/bus/ScBusObservationPersistenceArchitectureTest.java
M src/test/java/com/sovereign/connect/bus/ScBusOutboxBridgeArchitectureTest.java
M src/test/java/com/sovereign/connect/bus/ScBusHardeningArchitectureTest.java
```

## 4. Validation commands

```bash
mvn -q compile
mvn -q test -Dtest="JdbcDispatchObservationRepositoryTest"
mvn -q test -Dtest="RuntimeDispatchObservationPersistenceTest,ScBusObservationPersistenceArchitectureTest,ScBusHardeningArchitectureTest,ScBusOutboxBridgeArchitectureTest"
mvn -q test
```

## 5. Validation results

```text
compile:
  PASS

JdbcDispatchObservationRepositoryTest:
  total:    11
  failures: 0
  errors:   0
  skipped:  0

Targeted runtime/architecture tests:
  PASS

Full sovereign-connect module:
  total:    378
  failures: 0
  errors:   0
  skipped:  0

SC-C / non-bus baseline:
  240 tests still green in full regression.

EIB module:
  not run; MU-027 modified only the sovereign-connect Maven module.
```

## 6. Migration record

```text
Migration added: V101__sc_b_dispatch_observation_persistence.sql
FK to sc_b_dispatch_records(dispatch_record_id): yes
FK rationale: V100 creates sc_b_dispatch_records before V101, and runtime observations are recorded after dispatch state writes create/update the dispatch record.
Objects created only under sc_b_*: yes
SC-C tables altered: no
```

## 7. CANDIDATE_SELECTED disposition

```text
CANDIDATE_SELECTED emitted: no
Disposition: reserved vocabulary only. RuntimeDispatchService has no current separate candidate-selected hook, and MU-027 did not redesign the runtime lifecycle.
```

## 8. AC mapping summary

```text
AC-027-001 PASS - H3-only changed files and report scope.
AC-027-002 PASS - Dispatch state persistence not reimplemented.
AC-027-003 PASS - Outbox bridge projection not reimplemented.
AC-027-004 PASS - Broker dependency architecture tests pass.
AC-027-005 PASS - Lifecycle / SC-D onboarding absent.
AC-027-006 PASS - No productive ScdCommand or fact family added.
AC-027-007 PASS - No SC-C outbox or ledger mutation changes.
AC-027-008 PASS - bus.runtime.persistence imports no core.*.
AC-027-009 PASS - core.* imports no bus.runtime.*.
AC-027-010 PASS - JdbcDispatchObservationRepository exists.
AC-027-011 PASS - record(...) implemented and tested.
AC-027-012 PASS - observationsFor(...) implemented and tested.
AC-027-013 PASS - InMemoryDispatchObservationRepository preserved.
AC-027-014 PASS - JDBC/Flyway storage; no JPA added.
AC-027-015 PASS - observationSurvivesRepositoryRecreation.
AC-027-016 PASS - observationsForUnknownDispatchRecordIdReturnsEmptyList.
AC-027-017 PASS - multipleObservationsReturnedInObservedAtOrder.
AC-027-018 PASS - recordPersistsObservationWithAllMandatoryFields.
AC-027-019 PASS - claimEmitsPersistentClaimedObservation.
AC-027-020 PASS - dispatchEmitsPersistentDispatchingAndDispatchedObservations.
AC-027-021 PASS - dispatchEmitsPersistentDispatchingAndDispatchedObservations.
AC-027-022 PASS - noHandlerEmitsPersistentDeliveryFailedObservation.
AC-027-023 PASS - noHandler and deliveryFailed observation tests.
AC-027-024 PASS - retryEmitsPersistentRetryScheduledObservation.
AC-027-025 PASS - exhaustionEmitsPersistentExhaustedObservation.
AC-027-026 PASS - supersedeCancellationEmitsPersistentCancelledObservation.
AC-027-027 PASS - dispatchedObservationIsTechnicalNotSemantic.
AC-027-028 PASS - DELIVERY_FAILED retained as technical delivery evidence.
AC-027-029 PASS - exhaustedObservationIsTechnicalNotSemantic.
AC-027-030 PASS - no SC-C terminal request-state writes.
AC-027-031 PASS - no adapter admission / policy / authority decisions.
AC-027-032 PASS - code and sanitizedReason persisted from RuntimeDispatchService.
AC-027-033 PASS - no raw provider payload / credential / stack persistence added.
AC-027-034 PASS - no source/correlation enrichment added; existing observations remain technical.
AC-027-035 PASS - no productive ScDeliveryError event emission.
AC-027-036 PASS - scBObservationMigrationCreatesOnlyScBTables.
AC-027-037 PASS - scBObservationMigrationUsesV101.
AC-027-038 PASS - migration strategy recorded in this report.
AC-027-039 PASS - branch, pending commits and changed files recorded.
AC-027-040 PASS - test commands and final summary recorded.
AC-027-041 PASS - all AC-027 criteria mapped here and in acceptance-map.md.
AC-027-042 PASS - retained debts recorded below.
```

## 9. Boundary confirmations

```text
DispatchObservationRecord shape preserved: yes
DispatchObservationPort shape preserved: yes
InMemoryDispatchObservationRepository preserved: yes
RuntimeDispatchService lifecycle unchanged: yes
observationId generated by RuntimeDispatchService preserved by JDBC repository: yes
observedAt persisted as epoch milliseconds: yes
observations ordered by observed_at_ms ASC, observation_id ASC: yes
PerConnectionPragmaDataSource used in bus tests: no
JPA added: no
broker dependencies added: no
bus.* production imports core.*: no
core.* production imports bus.runtime.*: no
OutboxEntryStatus mutation: no
SC-C ledger mutation: no
```

## 10. Retained debts

```text
DEBT-BRD-H-005 - ScDeliveryError productive event emission remains deferred.
DEBT-BRD-H-006 - Physical NATS Core + JetStream binding remains absent.
DEBT-BRD-H-007 - SC-D lifecycle channel runtime remains absent.
DEBT-SEED-001 - ScdCommand canonical shape remains deferred.
DEBT-SEED-002 - SC-D fact family canonical shapes remain deferred.
DEBT-SEED-003 - adapter-scoped route assignment remains lifecycle-channel downstream work.
```

## 11. Final dictum

```text
MU-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001:
  Validated L4
```
