# Code Surface Audit — MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001

```text
Audit ID:            CSA-MU-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Version:             v0.1.1-draft
MU:                  MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Plane:               SC-C
Surface category:    Non-greenfield / industrial hardening over validated seed
Repository ZIP:      sovereign-connect-005-unblocked-hardening-2.zip
Repository root:     sovereign-connect
Baseline commit:     a76d261 Merge pull request #17 from Marcelo-314/feat/sc-c-temporal-acts-seed
Audit date:          2026-05-20
Status:              Draft / Approvable as formal Code Surface Audit
```

---

## Changelog v0.1.1-draft

- Refines acceptance target T-005 from a fragile production-profile assertion into a source-level boundary test that excludes H2 from industrial SQLite persistence adapters.
- Adds optional T-005b for production/industrial profile verification when the implementation package introduces a production Spring profile.
- Preserves all blockers B-01 through B-16 and all non-goals.

## Changelog v0.1.0-draft

- Opens the formal Code Surface Audit for `MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001`.
- Classifies the current TemporalActs implementation as validated seed / semantic hardening, not industrial Temporal Engine.
- Identifies blockers B-01 through B-16.

---

## 0. Audit disposition

This audit verifies the current code surface for promoting the existing TemporalActs seed into an industrial-grade SC-C Temporal Engine.

Result:

```text
Current implementation: Validated seed / semantic hardening.
Industrial Temporal Engine: NOT IMPLEMENTED.
Audit result: Formal implementation blockers identified.
Recommended next artifact: MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001.
```

This audit is not a Codex prompt and does not implement code.

It should be used as the normative code-surface input for the MIR and later execution package.

---

## 1. Approved/documentary inputs consumed

The audit assumes the following corpus decisions are accepted for the industrial hardening descent:

```text
ADR-SOV-SC-C-TEMPORAL-ACTS-SCOPE-001 v0.1.1
PDR-SOV-SC-C-TEMPORAL-RUNTIME-OBSERVATION-001 v0.1.3
PDR-SOV-SC-C-TEMPORAL-RUNTIME-REQUESTS-001 v0.1.3
SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.2.0
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001 v0.1.2
SDD-SOV-SC-C-RECOVERY-001 v0.1.3
ADR-SOV-SC-C-STORAGE-TECH-001 v0.1.1/v0.1.2 accepted lineage
MIR-SOV-SC-C-TEMPORAL-ACTS-SEED-001 / docs/mir/mir-005 evidence package
```

Normative scope for this MU:

```text
TemporalActs industrial v1 = SignalTemporalPayload only.
No ActionTemporalPayload.
No ActionRequest.
No delayed device command execution.
No SC-B runtime.
No NATS / JetStream.
No outbox dispatcher.
No View Composer / Effective Access Boundary implementation.
```

---

## 2. Evidence inspected

### 2.1 Production files relevant to TemporalActs

```text
src/main/java/com/sovereign/connect/core/temporal/model/TemporalAct.java
src/main/java/com/sovereign/connect/core/temporal/model/TemporalActStatus.java
src/main/java/com/sovereign/connect/core/temporal/model/TemporalActPayload.java
src/main/java/com/sovereign/connect/core/temporal/model/SignalTemporalPayload.java
src/main/java/com/sovereign/connect/core/temporal/model/CreatedByRef.java
src/main/java/com/sovereign/connect/core/temporal/port/TemporalActReadPort.java
src/main/java/com/sovereign/connect/core/temporal/port/TemporalActWritePort.java
src/main/java/com/sovereign/connect/core/temporal/service/TemporalActService.java
src/main/java/com/sovereign/connect/core/temporal/service/TemporalEngineService.java
src/main/java/com/sovereign/connect/core/temporal/service/TemporalEngineRunner.java
src/main/java/com/sovereign/connect/adapter/persistence/H2TemporalActRepository.java
src/main/java/com/sovereign/connect/SovereignConnectApplication.java
pom.xml
```

### 2.2 Test evidence available in ZIP

The ZIP includes Surefire reports with:

```text
Tests run: 101
Failures: 0
Errors: 0
Skipped: 0
```

Per-suite summary:

```text
OutboxLedgerStorageSeedTest:         13 / 0 failures
TemporalActSeedTest:                 26 / 0 failures
BaseTopologyServiceTest:             14 / 0 failures
ConcurrencyIdempotencySeedTest:       8 / 0 failures
CoreSnapshotQuerySeedTest:            1 / 0 failures
PersistenceBoundaryHardeningTest:     3 / 0 failures
PersistenceMemorySeedTest:            1 / 0 failures
RoomZoneTopologySeedTest:            20 / 0 failures
ScCoreKernelHardeningTest:            1 / 0 failures
TopologyMaterializationSeedTest:      2 / 0 failures
TopologyVersionHardeningTest:        12 / 0 failures
```

I could not execute `mvn test` in this environment because Maven is not installed. The audit therefore treats included Surefire reports as evidence, not as freshly reproduced execution.

---

## 3. Existing seed strengths to preserve

### S-01 — Signal-only payload surface is correctly bounded

Current `TemporalActPayload` is sealed and permits only `SignalTemporalPayload`.

```text
Preserve: no ActionTemporalPayload in industrial v1.
```

### S-02 — Core temporal services are persistence-implementation-free

`TemporalActService` and `TemporalEngineService` depend on ports plus `TransactionTemplate`; they do not import `JdbcTemplate`, `DataSource`, SQL or H2 directly.

```text
Preserve: domain/service layer remains free of physical storage imports.
```

### S-03 — Fire idempotency has correct seed direction

`markFiredIfDueAndNonTerminal(...)` uses a conditional update:

```text
status IN ('PENDING','ARMED') AND due_at_ms <= now
```

and the engine appends ledger/outbox only when update count is one.

```text
Preserve: no second semantic fire.
```

### S-04 — Cancel/fire race is semantically guarded

Cancel and fire both use conditional non-terminal updates. Tests cover cancel-before-fire and fire-before-cancel behavior.

```text
Preserve: exactly one terminal outcome wins.
```

### S-05 — MISFIRED exists and remains queryable

The repository supports `listMisfired(...)` and tests verify MISFIRED queryability.

```text
Preserve: MISFIRED terminal but diagnostic/queryable.
```

### S-06 — Ledger/outbox integration exists for fired signals

`TemporalEngineService` appends `TimerFired` ledger and `TIMER_FIRED_SIGNAL` outbox records in the firing transaction.

```text
Preserve: SC-C semantic firing authority remains in TemporalAct lifecycle, not in SC-B delivery.
```

### S-07 — Runner avoids `@Scheduled` and `Thread.sleep(...)`

`TemporalEngineRunner` uses virtual threads and `LockSupport.parkNanos(...)`, with cooperative stop and `awaitStopped(...)`.

```text
Preserve: no Spring @Scheduled shortcut; no Thread.sleep runner loop.
```

### S-08 — MU-015 PENDING-only outbox append guard is preserved

Existing tests include `nonPendingOutboxAppendRejected`.

```text
Preserve: Temporal Engine MUST NOT smuggle dispatcher/claim/retry state into append.
```

---

## 4. Industrial blockers and required changes

### B-01 — Temporal Engine is not wired into Spring lifecycle

Evidence:

```text
TemporalEngineRunner exists as a plain class.
No @Component / @Bean / SmartLifecycle / ApplicationRunner / CommandLineRunner / @PreDestroy wiring exists.
SovereignConnectApplication only runs SpringApplication.
```

Impact:

```text
In a real deployment, the engine never starts unless constructed manually by tests or external code.
```

Required change:

```text
Introduce a Spring-managed lifecycle component, preferably SmartLifecycle or equivalent:
  - starts after storage/recovery gates;
  - stops cleanly on shutdown;
  - calls awaitStopped;
  - exposes running/failed/degraded status.
```

Acceptance target:

```text
A Spring application context can start SC-C and the Temporal Engine enters RUNNING only after gates pass.
```

---

### B-02 — `classifyMisfires(...)` is not part of the startup recovery gate

Evidence:

```text
TemporalEngineService.classifyMisfires(...) exists.
No startup coordinator invokes it.
No recovery lifecycle bean exists.
```

Impact:

```text
Overdue non-terminal TemporalActs are classified only in tests/manual calls, not during real startup.
```

Required change:

```text
Add recovery startup gate integration:
  - classify all overdue non-terminal acts before engine polling;
  - block temporal request acceptance or return RECOVERY_NOT_COMPLETED / TEMPORAL_ENGINE_NOT_READY until complete;
  - record recovery outcome.
```

---

### B-03 — H2-only temporal persistence; no SQLite/Flyway industrial profile

Evidence:

```text
pom.xml includes H2 runtime dependency.
No sqlite-jdbc dependency.
No Flyway dependency.
No src/main/resources/application.properties/yml.
No migration SQL files.
H2TemporalActRepository creates schema imperatively in constructor.
```

Impact:

```text
TemporalActs are not durable under the accepted industrial storage profile.
```

Required change:

```text
Introduce SQLite/Flyway production profile:
  - configured DataSource;
  - Flyway migrations;
  - WAL / foreign_keys / synchronous / busy_timeout setup;
  - H2 restricted to test/dev fixture.
```

---

### B-04 — Industrial temporal schema is absent

Evidence:

Current H2 schema lacks industrial v1 tables/columns:

```text
temporal_request_idempotency absent
temporal_engine_locks absent
recovery_runs absent
recovery_findings absent
payload_kind absent; seed uses payload_type
label / signal_kind normalized fields absent
requested_at_ms absent
notification_target_ref nullable
terminal retention indexes absent
misfired partial index absent
```

Impact:

```text
The implementation cannot satisfy TRR v0.1.3, SDD Schema v0.1.2 or SDD Engine v0.2.0.
```

Required change:

```text
Implement production migrations for:
  - temporal_acts Signal-only v1 schema;
  - temporal_request_idempotency;
  - temporal_engine_locks or accepted single-node guard equivalent;
  - recovery_runs / recovery_findings or approved structured-log deferral;
  - required indexes for due scan, terminal retention and MISFIRED diagnostics.
```

---

### B-05 — `TemporalActObservationPort` and `TemporalActObservation` do not exist

Evidence:

```text
Existing read surface is TemporalActReadPort returning TemporalAct aggregate.
No TemporalActObservationPort exists.
No TemporalActObservation DTO exists.
```

Impact:

```text
The northbound observation contract is not implemented. Future View Composer / Effective Access Boundary has no formal SC-C runtime-observation port.
```

Required change:

```text
Add TemporalActObservationPort and TemporalActObservation DTO.
Map internal aggregate/repository rows to observation DTO.
Ensure observation exposes dueAt, payloadKind, label, signalKind, notificationTargetRef, createdByRef, timestamps and terminalReason, but not raw payload_json, remainingMs or aggregate internals.
```

---

### B-06 — `TemporalActApplicationPort` and request/result DTOs do not exist

Evidence:

```text
Existing service methods:
  createSignalTemporalAct(String habitatId, SignalTemporalPayload payload, Instant dueAt, String notificationTargetRef, CreatedByRef createdByRef) -> String
  cancelTemporalAct(String habitatId, String temporalActId, Instant now) -> int

No CreateSignalTemporalActRequest.
No CancelTemporalActRequest.
No CreateSignalTemporalActResult / CancelTemporalActResult.
No Rejected(...) vs Failed(...).
No TemporalRuntimeFailure.
```

Impact:

```text
The implementation cannot expose the industrial request contract defined by TRR v0.1.3.
```

Required change:

```text
Add TemporalActApplicationPort as write/request-only.
Add canonical request DTOs with non-null requestedAt.
Add sealed result types with Accepted / IdempotentReplay / Rejected / Failed variants.
Add TemporalRequestRejection and TemporalRuntimeFailure vocabularies.
```

---

### B-07 — Request idempotency is absent

Evidence:

```text
No idempotencyKey parameter in current create/cancel methods.
TemporalAct aggregate has no idempotencyKey.
No temporal_request_idempotency table.
Current ledger idempotency key for create is based on generated temporalActId.
```

Impact:

```text
Caller request replay cannot reconstruct create/cancel outcomes and cannot detect conflicting replay content.
```

Required change:

```text
Implement temporal_request_idempotency as the request replay substrate.
Define semantic_fingerprint for CREATE_SIGNAL and CANCEL.
Persist result_kind/result_code/result_json.
Return IdempotentReplay or IDEMPOTENCY_CONFLICT/Failed according to TRR v0.1.3.
```

---

### B-08 — `notificationTargetRef` remains nullable and is normalized as empty string in firing payloads

Evidence:

```text
TemporalAct.notificationTargetRef can be null.
H2 temporal_acts.notification_target_ref is nullable.
TemporalActService does not reject null/blank notificationTargetRef.
TemporalEngineService.nullableValue(...) serializes null as "" in ledger payload.
```

Impact:

```text
Violates industrial v1 requirement that notificationTargetRef is required and opaque, not optional.
```

Required change:

```text
Reject null/blank notificationTargetRef at request boundary.
Make production schema NOT NULL.
Remove null-to-empty coercion in semantic payloads.
```

---

### B-09 — dueAt/request validation is seed-level only

Evidence:

```text
createSignalTemporalAct(...) accepts dueAt but does not reject dueAt <= requestedAt/now.
No requestedAt is carried in the request DTO.
SignalTemporalPayload constructor checks null but not blank label/signalKind.
No typed rejection codes exist.
```

Impact:

```text
Industrial validation and deterministic rejection behavior are missing.
```

Required change:

```text
Validate habitatId, dueAt, requestedAt, label, signalKind, notificationTargetRef, createdByRef, requestedByRef and idempotencyKey.
Reject dueAt <= requestedAt unless a future accepted policy changes this.
Return typed Rejected(...) rather than throwing or silently accepting.
```

---

### B-10 — Unknown payload handling crashes via exception instead of fail-closed diagnostic/quarantine

Evidence:

```text
H2TemporalActRepository.readPayload(...)
  default -> throw new IllegalArgumentException("Unknown temporal payload type in MU-005: ...")

JSON deserialization failures throw IllegalStateException.
```

Impact:

```text
Unsupported/future/corrupt payload rows can crash query/recovery paths instead of surfacing FAILED/diagnostic/quarantine state.
```

Required change:

```text
Implement fail-closed unknown payload handling:
  - classify or quarantine record;
  - record recovery finding / diagnostic;
  - do not crash startup indefinitely;
  - do not activate ActionTemporalPayload implicitly;
  - do not coerce into SignalTemporalPayload.
```

---

### B-11 — Polling and recovery scans are unbounded

Evidence:

```text
TemporalEngineService.pollDueOnce(...) iterates all readPort.findDue(...).
classifyMisfires(...) iterates all findNonTerminalDueBefore(...).
H2TemporalActRepository.findDue(...) has no LIMIT.
listTerminal(...) and listMisfired(...) return unbounded results.
```

Impact:

```text
Downtime with many overdue acts can block startup or long-running polling cycles.
```

Required change:

```text
Add bounded methods/configuration:
  - maxDueActsPerCycle;
  - recovery batch/threshold/resume policy;
  - maxTerminalResults;
  - maxMisfiredResults;
  - optional time-window filtering for terminal lists.
```

---

### B-12 — Engine health, readiness and observability are absent

Evidence:

```text
No TemporalEngineStatus enum/service.
No health/readiness surface.
No metrics/logging/diagnostic event surface.
TemporalEngineRunner has lastFailure only, not an operational status model.
```

Impact:

```text
Operators cannot distinguish STARTING / RECOVERING / RUNNING / DEGRADED / FAILED / DISABLED.
```

Required change:

```text
Add observable engine lifecycle state and diagnostics:
  - status;
  - last poll timestamp;
  - due count / processed count / failed count;
  - misfire count;
  - last failure;
  - recovery status;
  - bounded cycle metrics.
```

---

### B-13 — Single-node guard is absent

Evidence:

```text
No temporal_engine_locks table or equivalent.
No guard in runner/lifecycle.
No detection of multiple active engines against same habitat/storage partition.
```

Impact:

```text
Industrial v1 single-node contract is not enforceable.
```

Required change:

```text
Implement accepted single-node guard:
  - SQLite lock/lease row or equivalent;
  - heartbeat;
  - violation maps to SINGLE_NODE_GUARD_VIOLATED;
  - no polling starts if guard acquisition fails.
```

---

### B-14 — Terminal retention and purge policy are absent

Evidence:

```text
No terminalRetentionDays.
No purge method/job.
No bounded terminal query defaults.
No retention tests.
```

Impact:

```text
Terminal history can grow without bound; listTerminal/listMisfired cannot satisfy TRO bounded-result-set policy.
```

Required change:

```text
Define and implement retention policy:
  - terminalRetentionDays default 30 or accepted value;
  - maxTerminalResults / maxMisfiredResults;
  - ledger preservation after act purge;
  - purge tests.
```

---

### B-15 — Runtime failure mapping is absent

Evidence:

```text
Storage/JSON/infrastructure errors currently propagate as unchecked exceptions.
No TemporalRuntimeFailureCode mapping exists.
No Failed(...) result variants exist.
```

Impact:

```text
Valid requests cannot receive distinguishable Failed(...) outcomes such as STORAGE_UNAVAILABLE, RECOVERY_NOT_COMPLETED or TEMPORAL_ENGINE_NOT_READY.
```

Required change:

```text
Map infrastructure/runtime failures into TemporalRuntimeFailure where the application contract requires a result.
Preserve exceptions only for truly unrecoverable programming/configuration errors where accepted by SDD.
```

---

### B-16 — Production application configuration is absent

Evidence:

```text
src/main/resources contains no application.properties/yml.
No configured spring.datasource.url.
No temporal engine properties.
No storage directory profile.
```

Impact:

```text
A real application has no declared storage/runtime profile for SC-C Temporal Engine.
```

Required change:

```text
Add explicit production/dev/test profiles for:
  - SQLite database path;
  - Flyway migrations;
  - temporal engine enabled flag;
  - poll interval;
  - batch size;
  - retention;
  - single-node guard.
```

---

## 5. Governance / repository hygiene observations

### G-01 — Working tree contains unrelated noise

Observed `git status --short` includes `.idea/*`, `.gitignore`, and docs/mir/mir-001 filename changes/untracked files.

Impact:

```text
Not a domain blocker, but this must be cleaned or separated before an industrial hardening branch/commit.
```

Required action:

```text
Start the MIR implementation branch from a clean baseline or explicitly exclude unrelated workspace artifacts.
```

---

## 6. Required implementation strategy

The industrial hardening MU SHOULD be implemented as a focused migration from seed to industrial runtime, not as a rewrite of unrelated topology code.

Recommended package additions:

```text
core.temporal.application
  TemporalActApplicationPort
  CreateSignalTemporalActRequest
  CancelTemporalActRequest
  CreateSignalTemporalActResult
  CancelTemporalActResult
  TemporalRequestRejection
  TemporalRuntimeFailure

core.temporal.observation
  TemporalActObservationPort
  TemporalActObservation
  TemporalActObservationMapper

core.temporal.engine
  TemporalEngineLifecycle
  TemporalEngineStatus
  TemporalEngineProperties
  TemporalEngineHealth / diagnostics surface

adapter.persistence.sqlite
  SQLiteTemporalActRepository
  SQLiteTemporalRequestIdempotencyRepository
  SQLiteTemporalEngineLockRepository
  SQLiteRecoveryObservationRepository, if recovery tables are implemented
```

Recommended resources:

```text
src/main/resources/db/migration/V*_sc_c_temporal_engine.sql
src/main/resources/application.yml or application.properties
```

H2 policy:

```text
H2 MAY remain only as test/dev fixture.
Industrial runtime MUST use SQLite/Flyway profile.
```

---

## 7. Acceptance map for the future MIR

The MIR acceptance map SHOULD include tests for at least:

```text
T-001 Spring context wires Temporal Engine lifecycle.
T-002 Engine does not start polling before recovery gate.
T-003 classifyMisfires is invoked during startup recovery.
T-004 SQLite/Flyway migrations create temporal_acts industrial schema.
T-005 H2 is excluded from industrial persistence adapters by source-level boundary test:
      - no org.h2 imports under src/main/java/**/adapter/persistence/sqlite/**;
      - no H2 classes in production persistence configuration;
      - H2 remains confined to src/test, seed legacy adapters, or explicit dev/test fixtures.
T-005b Production/industrial profile selects SQLite, not H2, if a production profile is introduced by the implementation package:
      - DataSource URL starts with jdbc:sqlite:;
      - Flyway targets that DataSource;
      - no H2 in-memory DataSource is created under the production profile.
T-006 TemporalActObservationPort returns DTO, not aggregate.
T-007 TemporalActApplicationPort returns Accepted / IdempotentReplay / Rejected / Failed.
T-008 create requires idempotencyKey and stores replay record.
T-009 duplicate create replay reconstructs result.
T-010 conflicting idempotency replay rejects.
T-011 cancel idempotency works.
T-012 notificationTargetRef null/blank rejected.
T-013 dueAt <= requestedAt rejected.
T-014 unknown payload kind is quarantined/diagnostic and does not crash startup.
T-015 due polling is limited by maxDueActsPerCycle.
T-016 MISFIRED recovery is bounded or fail-closed according to config.
T-017 listTerminal/listMisfired are bounded.
T-018 single-node guard violation prevents engine start and returns failure code.
T-019 engine reports RUNNING/RECOVERING/FAILED/DISABLED status.
T-020 terminal retention preserves ledger semantics.
T-021 TimerFired remains atomic with ledger/outbox.
T-022 no ActionTemporalPayload / ActionRequest / SC-B / NATS / dispatcher introduced.
T-023 domain services remain SQL/JDBC/Flyway-free.
T-024 every SQLite connection has foreign_keys enabled.
T-025 recovery_runs/recovery_findings or approved structured logs are produced.
```

---

## 8. Explicit non-goals for this MU

The industrial hardening MU MUST NOT implement:

```text
ActionTemporalPayload;
ActionRequest;
delayed device command execution;
endpoint fire-time target validation;
SC-B runtime;
NATS / JetStream;
outbox dispatcher / claim loop / retry loop;
View Composer;
Effective Access Boundary;
Session / Identity / Authority / Policy;
Surface rendering;
recurrence / cron / calendar rules;
late notification policy after downtime.
```

---

## 9. Failure signals for the MIR / execution package

Implementation MUST stop and report upstream if:

```text
FS-CSA-001  Engine remains manually constructed only in tests.
FS-CSA-002  classifyMisfires is not wired into startup recovery.
FS-CSA-003  H2 remains the production runtime storage.
FS-CSA-004  Flyway migrations are not introduced for SQLite.
FS-CSA-005  createSchema() constructor schema creation remains production path.
FS-CSA-006  TemporalActObservationPort is omitted.
FS-CSA-007  TemporalActApplicationPort/result types are omitted.
FS-CSA-008  request idempotency is implemented only through generated temporalActId ledger keys.
FS-CSA-009  notificationTargetRef remains nullable in industrial path.
FS-CSA-010  unknown payload kind can crash startup/recovery.
FS-CSA-011  due polling or recovery classification is unbounded.
FS-CSA-012  no single-node guard exists.
FS-CSA-013  no engine health/readiness is exposed.
FS-CSA-014  ActionTemporalPayload or ActionRequest is introduced.
FS-CSA-015  SC-B/NATS/outbox dispatcher is introduced.
FS-CSA-016  domain services import SQLite/JDBC/Flyway.
FS-CSA-017  terminal history is unbounded and no retention policy exists.
```

---

## 10. Recommended next artifact

Open:

```text
MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v0.1.0-draft
```

Do not produce `context.md` or `codex-prompt.md` until the MIR is accepted.

Suggested branch for MIR/documentation:

```text
docs/sc-c-temporal-engine-industrial-hardening-mir
```

Suggested future implementation branch:

```text
feat/sc-c-temporal-engine-industrial-hardening
```

Suggested eventual implementation commit:

```text
feat(sc-c): harden temporal engine for industrial runtime
```
