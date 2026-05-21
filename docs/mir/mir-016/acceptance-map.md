# acceptance-map.md — MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001

```text
Document: acceptance-map.md
Version:  v0.2.1
MU:       MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Slot:     MU-016 / docs/mir/mir-016/
Purpose:  Map MIR / CSA / PDR / SDD obligations to implementation evidence and tests.
```

---

## 0. Baseline requirement

Before modifying code, run the baseline test suite.

Expected baseline evidence from ZIP:

```text
Tests run: 101
Failures: 0
Errors: 0
Skipped: 0
```

If local `mvn test` does not pass before implementation, stop and report baseline failure.

---

## 1. Acceptance targets

| ID | Target | Required evidence |
|---|---|---|
| T-001 | Existing seed tests preserved | All existing tests pass or are migrated with explicit rationale. |
| T-002 | Temporal Engine lifecycle is Spring-managed | Test starts Spring context and verifies lifecycle bean exists and does not start polling before recovery gate. |
| T-003 | `classifyMisfires` runs before polling | Startup/recovery test creates overdue non-terminal act, starts context, verifies MISFIRED before polling can fire. |
| T-004 | SQLite/Flyway migrations create industrial temporal schema | Integration test using SQLite DataSource + Flyway verifies `temporal_acts`, `temporal_request_idempotency`, `temporal_engine_locks` or equivalent, indexes/constraints. |
| T-005 | H2 excluded from industrial persistence adapters | Source-level boundary test scans `src/main/java`: no `org.h2` imports under `adapter/persistence/sqlite` or production persistence config. H2 allowed only test/dev/seed paths. |
| T-005b | Production/industrial profile selects SQLite, not H2 | Required only if package introduces production/industrial Spring profile. Test asserts DataSource URL starts with `jdbc:sqlite:` and no H2 DataSource is selected. |
| T-006 | SQLite FK enforcement is per-connection | Test verifies `PRAGMA foreign_keys = ON` for runtime/repository connections, not only bootstrap. |
| T-007 | WAL/profile settings applied | Test or implementation report evidence verifies WAL, busy_timeout, foreign_keys and `synchronous=FULL` for the industrial SQLite profile; `NORMAL` requires explicit deployment-profile evidence. |
| T-008 | `TemporalActObservationPort` exists | Source/test verifies port and DTO exist and return observations, not aggregate objects. |
| T-009 | Observation DTO hides internals | Test verifies observation does not expose raw `payload_json`, `remainingMs`, countdown ticks or repository rows. |
| T-010 | `TemporalActApplicationPort` exists | Source/test verifies create/cancel request surface exists and is write/request-only. |
| T-011 | Rejected vs Failed separated | Tests verify invalid request returns `Rejected(...)`; storage/recovery/engine availability failure returns `Failed(...)`. |
| T-012 | `notificationTargetRef` required | Create request with null/blank `notificationTargetRef` is rejected; targetless topology timer remains supported. |
| T-013 | `requestedAt` non-null at canonical DTO | Test or constructor validation rejects null `requestedAt`; convenience APIs derive from injected `Clock` before DTO construction. |
| T-014 | Create idempotency works | Same idempotency key + same semantic create returns stable replay. |
| T-015 | Create idempotency conflict works | Same idempotency key + conflicting semantic create returns `Rejected(IDEMPOTENCY_CONFLICT)`. |
| T-016 | Cancel idempotency works | Repeated cancel with same identity gives stable result / `AlreadyTerminal` according to contract. |
| T-017 | Bounded polling | Test creates more due acts than `maxDueActsPerCycle`; one poll processes no more than configured bound. |
| T-018 | Single-node guard exists | Test verifies second engine instance against same habitat/storage partition fails fast or remains disabled/degraded. |
| T-018b | Equivalent guard evidence | If not using `temporal_engine_locks`, implementation report provides fail-fast, observability and no-double-poll proof. |
| T-019 | Recovery classification bounded | Test verifies recovery classification obeys configured batch/threshold/resume behavior. |
| T-020 | Unknown payload fail-closed | Seed a row with unsupported payload kind; startup/recovery does not crash indefinitely and records diagnostic/quarantine/failed state. |
| T-021 | MISFIRED terminal/queryable | Overdue non-terminal after recovery becomes MISFIRED, remains queryable, is not active and never fires. |
| T-022 | Terminal retention bounded | `listTerminal` and `listMisfired` do not perform unbounded history reads; verify max result / retention behavior. |
| T-023 | Engine status observable | Tests verify statuses such as RECOVERING/RUNNING/FAILED/DISABLED or equivalent. |
| T-024 | Disabled engine failure mapping | With engine disabled, create/cancel or engine-dependent request path returns `Failed(TEMPORAL_ENGINE_DISABLED)` where applicable. |
| T-025 | Recovery incomplete failure mapping | Before gate completes, temporal requests return `Failed(RECOVERY_NOT_COMPLETED)` or `Failed(TEMPORAL_ENGINE_NOT_READY)`. |
| T-026 | Fire idempotency preserved | Existing double-fire prevention test still passes after SQLite/industrial refactor. |
| T-027 | Cancel/fire race preserved | Existing cancel/fire race tests still pass. |
| T-028 | TimerFired ledger/outbox preserved | Firing a signal appends exactly one ledger and PENDING outbox record. |
| T-029 | No ActionTemporalPayload | Source-level test verifies no `ActionTemporalPayload` class and `TemporalActPayload` permits only `SignalTemporalPayload`. |
| T-030 | No SC-B/NATS/dispatcher | Source-level boundary test verifies no NATS/JetStream/SC-B runtime/outbox dispatcher introduced by this MU. |
| T-031 | Domain/service storage boundary preserved | Domain/application services do not import H2, SQLite, JDBC, Flyway or SQL. Repository/adapters own persistence. |
| T-032 | Application starts with configured temporal beans | Spring context test verifies required beans are wired without manual test construction. |
| T-033 | Implementation report documents migration decisions | Report states whether `temporal_engine_locks` is used; if not, includes equivalent guard evidence. |

---

## 2. Stop conditions

Stop implementation and report if any of the following occurs:

```text
SC-STOP-001 Existing baseline tests fail before implementation.
SC-STOP-002 SQLite/Flyway addition requires broad non-temporal topology refactor.
SC-STOP-003 H2 appears in industrial SQLite adapter path or production config.
SC-STOP-004 Recovery gate cannot be wired before polling.
SC-STOP-005 SmartLifecycle or equivalent causes engine to poll before migration/recovery gate.
SC-STOP-006 Request idempotency requires changing generic ledger/outbox semantics.
SC-STOP-007 Unknown payload handling requires ActionTemporalPayload.
SC-STOP-008 Bounded polling cannot be implemented without losing fire idempotency.
SC-STOP-009 Single-node guard cannot provide fail-fast behavior.
SC-STOP-010 Implementation introduces SC-B, NATS, dispatcher, View Composer or Effective Access Boundary.
SC-STOP-011 Domain services begin importing persistence technologies.
SC-STOP-012 Existing TimerFired ledger/outbox semantics are broken.
SC-STOP-013 Equivalent single-node guard is claimed without fail-fast, observability and no-double-poll evidence.
```

---

## 3. Expected implementation report sections

The final implementation report MUST include:

```text
1. Baseline commit and branch.
2. Test summary before and after.
3. Files changed by phase.
4. SQLite/Flyway migration list.
5. DataSource / profile behavior, including synchronous durability setting.
6. Recovery gate behavior.
7. Single-node guard decision and evidence.
8. Idempotency backing store evidence.
9. Unknown payload handling evidence.
10. Observability/health behavior.
11. Boundary tests evidence.
12. Known deferrals, if any.
```
