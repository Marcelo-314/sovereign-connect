# Implementation Report - MU-025 SC-B Dispatch State Persistence

```text
Document ID:  implementation-report-MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
Version:      v0.2.0-candidate
Status:       Implemented / Validated L4
Corpus:       Sovereign Connect
Plane:        SC-B
MU ID:        MU-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
MU Slot:      MU-025
Branch:       feat/sc-b-mir-025-dispatch-state-persistence
```

## 1. Execution summary

```text
Implementation branch: feat/sc-b-mir-025-dispatch-state-persistence
Implementation commit: fcdbf4d642e08e4592940307d0b6d4b2239047cd
Evidence/docs commit, if separate: 137ac2dca20f5142885dbd73e609e0b4ed9572cd
Executor: Codex
Date: 2026-05-29
```

Summary:

```text
Implemented H1 dispatch state persistence only.
Added JDBC/Flyway-backed DispatchStateWritePort adapter under bus.runtime.persistence.
Added V100 SC-B migration creating sc_b_dispatch_records and sc_b_dispatch_attempts only.
Preserved InMemoryDispatchStateRepository and existing RuntimeDispatchService semantics.
Added restart-visible state, retry continuity, terminal state and architecture tests.
No outbox bridge, ScOutboxDispatchReadPort, integration.scledgerdispatch,
dispatch observation persistence, broker dependency or core import from bus.** was introduced.
```

## 2. Changed files

```text
A docs/mir/mir-025/MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001.md
A docs/mir/mir-025/acceptance-map.md
A docs/mir/mir-025/code-surface-audit.md
A docs/mir/mir-025/codex-prompt.md
A docs/mir/mir-025/context.md
A docs/mir/mir-025/execution-package-manifest.md
A docs/mir/mir-025/implementation-report.md
A src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepository.java
A src/main/resources/db/migration/V100__sc_b_dispatch_state_persistence.sql
A src/test/java/com/sovereign/connect/bus/ScBusHardeningArchitectureTest.java
A src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepositoryTest.java
```

New package:

```text
com.sovereign.connect.bus.runtime.persistence
```

## 3. Migration strategy

```text
Chosen strategy: V100+ single-stream high-number SC-B offset
Migration file: src/main/resources/db/migration/V100__sc_b_dispatch_state_persistence.sql
sourceRecordId included? yes
```

`source_record_id` is nullable and opaque schema preparation for H2. H1 does not populate it from `OutboxEntry`, does not read SC-C outbox rows, does not join against `core.scledger`, and does not treat it as semantic authority.

Migration creates only:

```text
sc_b_dispatch_records
sc_b_dispatch_attempts
```

## 4. Test evidence

Commands run:

```bash
mvn -q compile
mvn -q test -Dtest=JdbcDispatchStateRepositoryTest
mvn -q test -Dtest="JdbcDispatchStateRepositoryTest,ScBusHardeningArchitectureTest"
mvn -q test
```

Results:

```text
sovereign-connect tests:
  total:    315
  failures: 0
  errors:   0
  skipped:  0

Bus delta:
  total:    75
  failures: 0
  errors:   0
  skipped:  0

SC-C/non-bus baseline:
  total:    240
  failures: 0
  errors:   0
  skipped:  0

EIB module: not run; H1 modified only the sovereign-connect Maven module.
```

Expected `>= 306` tests was exceeded. Bus delta increased from 54 to 75.

## 5. Acceptance map

| AC | Status | Evidence |
|---|---|---|
| AC-025-001 | PASS | H1-only files: JDBC state repository, V100 migration, tests. |
| AC-025-002 | PASS | `ScBusHardeningArchitectureTest.h1DidNotIntroduceOutboxBridgeSurfaces`. |
| AC-025-003 | PASS | No `DispatchObservationPort` persistence added; in-memory implementation unchanged. |
| AC-025-004 | PASS | Broker API and POM architecture tests pass. |
| AC-025-005 | PASS | `bus.runtime.persistence` imports no `core.*`. |
| AC-025-006 | PASS | `core.**` imports no `bus.runtime.**`. |
| AC-025-007 | PASS | `JdbcDispatchStateRepository` implements `DispatchStateWritePort`. |
| AC-025-008 | PASS | Repository tests cover all port methods. |
| AC-025-009 | PASS | `InMemoryDispatchStateRepository` remains unchanged and existing tests pass. |
| AC-025-010 | PASS | Uses `JdbcTemplate` and Flyway migration; no JPA dependency. |
| AC-025-011 | PASS | Allowed transitions covered by persistence tests. |
| AC-025-012 | PASS | `invalidTransitionsAreRejected`. |
| AC-025-013 | PASS | Report preserves technical-only state meaning; no adapter acceptance semantics added. |
| AC-025-014 | PASS | Report preserves `DISPATCHED != semantic success`; no semantic success code added. |
| AC-025-015 | PASS | Report preserves `EXHAUSTED != domain failure`; terminal technical state only. |
| AC-025-016 | PASS | `currentAttemptSurvivesRepositoryRecreation`. |
| AC-025-017 | PASS | Restart visibility tests cover CLAIMED, DISPATCHING, DELIVERY_FAILED and RETRY_SCHEDULED paths. |
| AC-025-018 | PASS | `exhaustedIsPersistentAndNotClaimable`. |
| AC-025-019 | PASS | `supersedeCancellationPersistsEvidenceRefAndTerminalState`. |
| AC-025-020 | PASS | `retryAttemptNumberContinuitySurvivesRepositoryRecreation`. |
| AC-025-021 | PASS | `supersedeCancellationRequiresEvidenceRef`. |
| AC-025-022 | PASS | Evidence persisted in `supersession_evidence_ref`. |
| AC-025-023 | PASS | `retryFromDeliveryFailedCreatesNewClaimedAttempt`. |
| AC-025-024 | PASS | Migration SQL architecture test verifies only `sc_b_*` table creation. |
| AC-025-025 | PASS | Migration naming architecture test verifies SC-B migrations are V100+. |
| AC-025-026 | PASS | Migration strategy and nullable `sourceRecordId` decision recorded above. |
| AC-025-027 | PASS | Branch, commit and changed files recorded. |
| AC-025-028 | PASS | Test commands and summaries recorded. |
| AC-025-029 | PASS | AC-025-001..030 mapped in this table. |
| AC-025-030 | PASS | Retained H2/H3 debts preserved below. |

## 6. Boundary verification

```text
bus.** imports core.**: PASS
core.** imports bus.runtime.**: PASS
broker dependency absent: PASS
ScOutboxDispatchReadPort absent: PASS
integration.scledgerdispatch absent: PASS
OutboxEntryStatus not imported in bus.runtime.persistence: PASS
SC-B migration V100+ or separated: PASS
```

Additional confirmations:

```text
No OutboxEntry, LedgerEntry or OutboxEntryStatus read or mutation was introduced.
No DispatchObservationPort persistence was introduced.
No NATS, JetStream, Redis, Vert.x, MQTT, Kafka, gRPC or broker client dependency was introduced.
```

## 7. Retained debts

```text
DEBT-BRD-H-001 - SC-C outbox bridge absent.
DEBT-BRD-H-002 - ScOutboxDispatchReadPort absent.
DEBT-BRD-H-003 - integration.scledgerdispatch absent.
DEBT-BRD-H-004 - Dispatch observation persistence absent.
DEBT-BRD-H-005 - SIGNAL lane bridge mapping unresolved for execution.
DEBT-BRD-H-006 - Physical broker binding absent.
DEBT-BRD-H-007 - SC-D lifecycle channel not implemented.
```

## 8. Final verdict

```text
Recommended status: Validated L4
Rationale: H1 dispatch state persistence is implemented, restart-visible,
covered by JDBC and architecture tests, and full regression is green with
315 tests / 0 failures / 0 errors / 0 skipped.
```

## 9. Deviation closure patch

```text
Patch package: PATCH-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-DEVIATION-CLOSURE-001
Patch commit: pending
Executor: Codex
Date: 2026-05-29
Status: Validated L4
```

Summary:

```text
Closed the STOP-1 evidence-transition deviation by restricting
transitionWithEvidence(...) to CANCELLED_BY_SUPERSEDE only.
Closed the bus-test datasource deviation by using inline SQLiteDataSource
and Flyway migration setup inside JdbcDispatchStateRepositoryTest.
Closed the architecture guard deviation by adding a bus test source check
that rejects adapter imports and PerConnectionPragmaDataSource references.
Aligned JdbcDispatchStateRepositoryTest method names with the MIR-025
patch contract while preserving 14 repository tests.
No dispatch port contract, runtime service, in-memory repository, migration,
outbox bridge, broker, or SC-C integration surface was changed.
```

Changed files:

```text
M src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepository.java
M src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepositoryTest.java
M src/test/java/com/sovereign/connect/bus/ScBusHardeningArchitectureTest.java
M docs/mir/mir-025/implementation-report.md
```

New regression evidence:

```text
JdbcDispatchStateRepositoryTest.transitionWithEvidenceRejectsNonSupersedeTargetState
ScBusHardeningArchitectureTest.busTestsDoNotDependOnAdapterOwnedInfrastructure
```

Commands run:

```bash
mvn -q compile
mvn -q test -Dtest="JdbcDispatchStateRepositoryTest"
mvn -q test -Dtest="JdbcDispatchStateRepositoryTest,ScBusHardeningArchitectureTest,ScBusArchitectureTest"
mvn -q test
```

Results:

```text
JdbcDispatchStateRepositoryTest:
  total:    14
  failures: 0
  errors:   0
  skipped:  0

sovereign-connect tests:
  total:    317
  failures: 0
  errors:   0
  skipped:  0
```

Retained debts:

```text
DEBT-BRD-H-001 - SC-C outbox bridge absent.
DEBT-BRD-H-002 - ScOutboxDispatchReadPort absent.
DEBT-BRD-H-003 - integration.scledgerdispatch absent.
DEBT-BRD-H-004 - Dispatch observation persistence absent.
DEBT-BRD-H-005 - SIGNAL lane bridge mapping unresolved for execution.
DEBT-BRD-H-006 - Physical broker binding absent.
DEBT-BRD-H-007 - SC-D lifecycle channel not implemented.
```

Final status:

```text
Validated L4.
```
