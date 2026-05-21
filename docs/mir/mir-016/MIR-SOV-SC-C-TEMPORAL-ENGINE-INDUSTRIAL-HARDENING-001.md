# MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001

## SC-C Temporal Engine Industrial Hardening

```text
Document ID: MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Title:       SC-C Temporal Engine Industrial Hardening
Version:     v0.1.1-draft
Status:      Draft / Ready for review
Date:        2026-05-20
Corpus:      Sovereign Connect
Type:        MIR
Plane:       SC-C
MU ID:       MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Scope:       Industrial hardening of the existing TemporalActs seed into a real SC-C Temporal Engine v1 runtime.
```

---

## Changelog v0.1.1-draft

Patch release after single-node guard review.

This version:

1. Clarifies §5.11 single-node guard equivalence: if the implementation does not use the recommended SQLite lock/lease row, the alternative mechanism must be documented in the implementation report.
2. Requires evidence for three properties of any equivalent mechanism: fail-fast validation, observability, and no-double-poll proof.
3. Adds acceptance target `T-018b` for implementation-report evidence when an alternative single-node guard is selected.
4. Adds stop condition `SC-STOP-013` if an equivalent guard mechanism is claimed without the required evidence.

## Changelog v0.1.0-draft

Initial draft.

This version:

1. Opens the industrial hardening MIR for the SC-C Temporal Engine.
2. Consumes the formal Code Surface Audit v0.1.1-draft.
3. Turns audit blockers B-01 through B-16 into implementation targets.
4. Defines scope, non-scope, acceptance map and stop conditions for industrial Temporal Engine v1.

---

## 0. Governance posture

This MIR opens the implementation descent for the industrial hardening of SC-C TemporalActs and the Temporal Engine.

It does **not** include a Codex prompt or execution context. Per current MIR governance, those artifacts belong to the later execution package after this MIR and the Code Surface Audit are accepted.

This MIR consumes the formal Code Surface Audit:

```text
code-surface-audit-MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v0.1.1-draft
```

---

## 1. Purpose

The purpose of this MU is to promote the current TemporalActs implementation from validated seed / semantic hardening into an industrial-grade SC-C Temporal Engine v1.

The existing implementation proves important semantics:

```text
SignalTemporalPayload only;
TemporalAct lifecycle state exists;
create / cancel / fire / misfire behavior exists;
TimerFired ledger + outbox append exists;
fire idempotency is enforced through conditional update;
MISFIRED remains queryable;
TemporalEngineRunner avoids @Scheduled and Thread.sleep(...);
SC-B / NATS / dispatcher are not introduced.
```

However, the implementation is not yet an industrial Temporal Engine because the formal audit identified blockers B-01 through B-16.

This MIR turns those blockers into an implementation target.

---

## 2. Normative inputs

This MIR depends on:

```text
ADR-SOV-SC-C-TEMPORAL-ACTS-SCOPE-001 v0.1.1-draft
PDR-SOV-SC-C-TEMPORAL-RUNTIME-OBSERVATION-001 v0.1.3-draft
PDR-SOV-SC-C-TEMPORAL-RUNTIME-REQUESTS-001 v0.1.3-draft
SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.2.0-draft
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001 v0.1.2-draft
SDD-SOV-SC-C-RECOVERY-001 v0.1.3-draft
ADR-SOV-SC-C-STORAGE-TECH-001 accepted SQLite/Flyway lineage
MIR-SOV-SC-C-TEMPORAL-ACTS-SEED-001 / docs/mir/mir-005 evidence package
MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001 v1.0.0-accepted
code-surface-audit-MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v0.1.1-draft
```

Related but not implemented by this MIR:

```text
PDR-SOV-SC-VIEW-COMPOSER-BOUNDARY-001
SDD-SOV-SC-VIEW-COMPOSER-001
ADR-SOV-SC-ACCESS-PATTERN-001
PDR-SOV-SC-BUS-CONTRACT-001
ADR-SOV-SC-BUS-TECH-001
PDR-SOV-SC-C-DELAYED-ACTION-EXECUTION-001, future / not opened here
```

---

## 3. Industrial v1 decision boundary

TemporalActs industrial v1 is:

```text
SignalTemporalPayload only.
SC-C-owned durable temporal commitments.
SC-C-owned Temporal Engine lifecycle.
SC-C-owned request acceptance/rejection.
SC-C-owned observation surface.
SC-C-owned recovery classification.
SC-C-owned firing/misfire/cancel lifecycle transitions.
```

TemporalActs industrial v1 is not:

```text
ActionTemporalPayload;
ActionRequest;
delayed device command execution;
endpoint fire-time command target validation;
SC-B runtime;
NATS / JetStream binding;
outbox dispatcher;
View Composer;
Effective Access Boundary;
Session / Identity / Authority / Policy;
Surface rendering;
recurrence / cron / calendar rules;
late notification after downtime.
```

---

## 4. Current implementation state

The Code Surface Audit classifies the current implementation as:

```text
Current implementation: Validated seed / semantic hardening.
Industrial Temporal Engine: NOT IMPLEMENTED.
```

Preserve these strengths:

```text
S-01 Signal-only payload surface.
S-02 Core temporal services remain persistence-implementation-free.
S-03 Fire idempotency via conditional update.
S-04 Cancel/fire race semantically guarded.
S-05 MISFIRED exists and remains queryable.
S-06 TimerFired ledger + outbox append.
S-07 Runner avoids @Scheduled and Thread.sleep(...).
S-08 MU-015 PENDING-only outbox append guard.
```

Close these blockers:

```text
B-01 Temporal Engine not wired into Spring lifecycle.
B-02 classifyMisfires not in startup recovery gate.
B-03 H2-only temporal persistence; no SQLite/Flyway industrial profile.
B-04 Industrial temporal schema absent.
B-05 TemporalActObservationPort / TemporalActObservation absent.
B-06 TemporalActApplicationPort and industrial request/result DTOs absent.
B-07 Request idempotency absent.
B-08 notificationTargetRef nullable / null-to-empty coercion.
B-09 request validation seed-level only.
B-10 unknown payload handling crashes instead of fail-closed diagnostic/quarantine.
B-11 polling/recovery/terminal queries unbounded.
B-12 engine health/readiness/observability absent.
B-13 single-node guard absent.
B-14 terminal retention/purge policy absent.
B-15 runtime failure mapping absent.
B-16 production application configuration absent.
```

---

## 5. Required implementation scope

### 5.1 Spring lifecycle

Implement a Spring-managed Temporal Engine lifecycle.

Preferred implementation shape:

```text
SmartLifecycle or equivalent lifecycle component.
```

Required behavior:

```text
starts only after storage/recovery gates pass;
stops cleanly on shutdown;
invokes awaitStopped or equivalent stop completion;
reports RUNNING / RECOVERING / DEGRADED / FAILED / DISABLED status;
does not use @Scheduled as the primary engine runtime;
does not use Thread.sleep(...) in the runner loop.
```

### 5.2 Startup recovery gate

Implement startup integration so that:

```text
classifyMisfires runs before Temporal Engine polling;
write-capable temporal services are gated by recovery state;
requests before recovery completion return Failed(RECOVERY_NOT_COMPLETED) or Failed(TEMPORAL_ENGINE_NOT_READY);
engine polling cannot start before recovery classification completes.
```

### 5.3 SQLite/Flyway industrial storage

Introduce the accepted industrial storage profile:

```text
SQLite JDBC;
Flyway migrations;
WAL profile;
foreign_keys ON for every SQLite connection;
synchronous production setting;
busy_timeout;
configured data directory;
no H2 production adapter path.
```

H2 may remain only as:

```text
src/test fixture;
seed legacy adapter;
explicit dev/test profile fixture.
```

### 5.4 Industrial temporal schema

Implement Flyway migrations for:

```text
temporal_acts;
temporal_request_idempotency;
temporal_engine_locks or accepted single-node guard equivalent;
recovery_runs / recovery_findings, or approved structured-log deferral;
indexes for due scan, active query, terminal query, MISFIRED diagnostics and retention.
```

### 5.5 Observation contract

Implement:

```text
TemporalActObservationPort
TemporalActObservation
TemporalActObservationMapper
```

Observation must expose:

```text
temporalActId;
habitatId;
status;
dueAt;
payloadKind;
label;
signalKind;
notificationTargetRef;
createdByRef;
createdAt;
updatedAt;
firedAt;
terminalAt;
terminalReason.
```

Observation must not expose:

```text
internal aggregate;
repository row;
raw payload_json;
remainingMs;
countdown ticks;
Effective Habitat View;
Projection metadata.
```

### 5.6 Request/application contract

Implement:

```text
TemporalActApplicationPort
CreateSignalTemporalActRequest
CancelTemporalActRequest
CreateSignalTemporalActResult
CancelTemporalActResult
TemporalRequestRejection
TemporalRuntimeFailure
TemporalRuntimeFailureCode
```

The port is write/request-only. It must not expose read/query operations.

Result variants:

```text
Accepted
IdempotentReplay
Rejected   -- domain/canonical request rejection
Failed     -- runtime/infrastructure/engine lifecycle failure
```

### 5.7 Request idempotency

Implement dedicated request idempotency storage:

```text
temporal_request_idempotency
```

Required semantics:

```text
same idempotency key + same semantic fingerprint => stable replay;
same idempotency key + conflicting semantic fingerprint => IDEMPOTENCY_CONFLICT;
create and cancel both idempotent;
result reconstruction supports Accepted / IdempotentReplay / Rejected / Failed.
```

### 5.8 Request validation

Implement typed validation for:

```text
habitatId;
temporalActId for cancel;
dueAt;
requestedAt;
label;
signalKind;
notificationTargetRef;
createdByRef;
requestedByRef;
idempotencyKey.
```

Rules:

```text
requestedAt is non-null in canonical request DTOs;
dueAt <= requestedAt is rejected in v1;
notificationTargetRef is required and opaque;
createdByRef/requestedByRef are required and opaque;
SC-C does not parse identity/session/authority/policy from refs;
create produces PENDING only;
cancel mutates non-terminal only;
terminal acts are immutable.
```

### 5.9 Unknown payload handling

Unknown or unsupported payload kind must:

```text
fail closed;
create diagnostic/recovery finding;
quarantine or mark diagnostic according to SDD;
not crash startup indefinitely;
not activate ActionTemporalPayload implicitly;
not coerce into SignalTemporalPayload.
```

### 5.10 Bounded polling and recovery

Implement configurable bounds:

```text
maxDueActsPerCycle;
recovery batch/threshold/resume policy;
maxTerminalResults;
maxMisfiredResults;
terminalRetentionDays.
```

Polling must not process unbounded due acts in one cycle.

Recovery classification must not block startup indefinitely without a bounded / resumable / fail-closed policy.

### 5.11 Single-node guard

Implement v1 single-node guard:

```text
one active Temporal Engine per habitat/storage partition;
SQLite lock/lease row or equivalent accepted mechanism;
heartbeat;
expiry;
violation maps to SINGLE_NODE_GUARD_VIOLATED;
engine does not poll if guard acquisition fails.
```

The recommended implementation path is the schema-supported `temporal_engine_locks` / SQLite lock-lease row described by the Persistence Schema SDD.

An equivalent mechanism is acceptable only if the implementation report documents evidence for all three properties below:

```text
1. fail-fast validation:
   a second Temporal Engine instance for the same habitat/storage partition is refused before polling begins;

2. observability:
   guard acquisition, heartbeat, expiry, violation and release are visible through logs, diagnostics or health/status output;

3. no-double-poll proof:
   tests or deterministic reasoning show that two active engines cannot concurrently poll/fire the same due TemporalAct set under the selected mechanism.
```

If an equivalent mechanism is selected, the implementation report MUST name the mechanism, justify why it satisfies the industrial single-node contract, and provide the evidence above.

The execution package MUST NOT treat "equivalent accepted mechanism" as an unconstrained implementation choice.

### 5.12 Observability and health

Implement temporal engine operational state:

```text
STOPPED;
STARTING;
RECOVERING;
RUNNING;
DEGRADED;
FAILED;
DISABLED.
```

Expose diagnostics for:

```text
last poll timestamp;
due count;
processed count;
fired count;
misfired count;
failed count;
skipped count;
last failure;
recovery state;
single-node guard state.
```

### 5.13 Retention

Implement terminal retention policy:

```text
terminalRetentionDays default defined by SDD;
listTerminal/listMisfired bounded;
purge respects ledger preservation;
purge does not remove audit/ledger semantics;
retention does not affect active TemporalActs.
```

---

## 6. Explicit non-goals

This MU MUST NOT implement:

```text
ActionTemporalPayload;
ActionRequest;
delayed device command execution;
endpoint fire-time target validation;
Profile B;
SC-B runtime;
NATS / JetStream;
outbox dispatcher;
outbox claim/retry/dead-letter worker;
View Composer;
Effective Access Boundary;
Session / Identity / Authority / Policy;
Surface rendering;
recurrence / cron / calendar rules;
late notification policy after downtime;
physical provider dispatch.
```

---

## 7. Acceptance map

The implementation MUST provide tests or equivalent verification for all targets below.

```text
T-001 Spring context wires Temporal Engine lifecycle.
T-002 Engine does not start polling before recovery gate.
T-003 classifyMisfires is invoked during startup recovery.
T-004 SQLite/Flyway migrations create TemporalActs industrial schema.
T-005 H2 is excluded from industrial persistence adapters by source-level boundary test.
T-005b Production/industrial profile selects SQLite, not H2, if a production profile is introduced.
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
T-018b if an equivalent single-node guard is used, implementation report provides fail-fast, observability and no-double-poll evidence.
T-019 engine reports RUNNING/RECOVERING/FAILED/DISABLED status.
T-020 terminal retention preserves ledger semantics.
T-021 TimerFired remains atomic with ledger/outbox.
T-022 no ActionTemporalPayload / ActionRequest / SC-B / NATS / dispatcher introduced.
T-023 domain services remain SQL/JDBC/Flyway-free.
T-024 every SQLite connection has foreign_keys enabled.
T-025 recovery_runs/recovery_findings or approved structured logs are produced.
T-026 create/cancel ledger entries remain mandatory and atomic with lifecycle changes.
T-027 request runtime failures map to Failed(...), not Rejected(...).
T-028 TemporalActApplicationPort remains write/request-only.
T-029 TemporalActObservationPort remains read/query-only.
T-030 H2 remains allowed only in src/test, seed legacy adapters or explicit dev/test fixtures.
```

### 7.1 T-005 source-level boundary detail

T-005 MUST verify:

```text
no org.h2 imports under src/main/java/**/adapter/persistence/sqlite/**;
no H2 classes in production persistence configuration;
H2-specific repository classes do not participate in the industrial SQLite adapter path;
domain/application services do not import H2, SQLite, JDBC or Flyway directly.
```

Runtime profile verification may be added as T-005b, but T-005 itself MUST remain a source-level boundary test.

---

## 8. Stop conditions

Implementation MUST stop and report upstream if any of the following occurs:

```text
SC-STOP-001 Engine remains manually constructed only in tests.
SC-STOP-002 classifyMisfires cannot be placed inside startup recovery gate without redesigning recovery ownership.
SC-STOP-003 SQLite/Flyway cannot be introduced without contaminating domain/application services with SQL/JDBC/Flyway.
SC-STOP-004 H2 remains the effective industrial runtime storage.
SC-STOP-005 createSchema() constructor schema creation remains the production path.
SC-STOP-006 TemporalActObservationPort is omitted or returns aggregates.
SC-STOP-007 TemporalActApplicationPort is omitted or exposes reads.
SC-STOP-008 request idempotency is implemented only through generated temporalActId ledger keys.
SC-STOP-009 notificationTargetRef remains nullable in the industrial request path.
SC-STOP-010 unknown payload kind can crash startup/recovery or activate ActionTemporalPayload implicitly.
SC-STOP-011 due polling or recovery classification remains unbounded.
SC-STOP-012 no single-node guard exists.
SC-STOP-013 equivalent single-node guard is claimed without fail-fast, observability and no-double-poll evidence.
SC-STOP-013 no engine health/readiness exists.
SC-STOP-014 ActionTemporalPayload or ActionRequest is introduced.
SC-STOP-015 SC-B/NATS/outbox dispatcher is introduced.
SC-STOP-016 domain services import SQLite/JDBC/Flyway.
SC-STOP-017 terminal history remains unbounded with no retention policy.
SC-STOP-018 acceptance tests cannot distinguish Rejected(domain) from Failed(runtime).
SC-STOP-019 implementation requires View Composer / Effective Access Boundary to complete this MU.
```

---

## 9. Expected implementation shape

Recommended additions:

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
  TemporalEngineDiagnostics

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

The exact package names may be adjusted by Code Surface Audit and implementation constraints, but the responsibility separation MUST remain.

---

## 10. Required preservation rules

Implementation must preserve:

```text
SC-C / SC-B / SC-D boundaries;
Base Topology vs Temporal Runtime separation;
Temporal Runtime Observation vs Temporal Runtime Requests separation;
H2 seed/test legacy vs SQLite industrial runtime separation;
SC-C semantic firing authority vs SC-B delivery boundary;
Outbox/Ledger append-only semantics;
TemporalAct fire idempotency;
Terminal immutability;
MISFIRED terminal/queryable behavior;
notificationTargetRef opacity;
createdByRef/requestedByRef opacity;
no remainingMs / countdown ticks from SC-C;
no ActionTemporalPayload in v1.
```

---

## 11. Deliverables expected from implementation

The implementation report MUST include:

```text
branch name;
commit hash;
summary of files changed;
new migrations added;
configuration profiles added;
test summary;
acceptance map result T-001 through T-030;
known limitations, if any;
confirmation of non-goals preserved.
```

The codebase MUST include tests proving the acceptance map, not only manual claims.

---

## 12. Failure value

This MU fails usefully if it proves that the current TemporalActs seed cannot be promoted to industrial Temporal Engine without either:

```text
re-architecting SC-C startup/recovery;
reworking storage boundaries;
changing the accepted Signal-only v1 scope;
or splitting Temporal Engine industrial hardening into multiple smaller MUs.
```

Any such failure should be reported upstream rather than hidden behind partial implementation.

---

## 13. Branch and commit suggestions

Documentation branch:

```text
docs/sc-c-temporal-engine-industrial-hardening-mir
```

Future implementation branch:

```text
feat/sc-c-temporal-engine-industrial-hardening
```

Future implementation commit:

```text
feat(sc-c): harden temporal engine for industrial runtime
```

---

# Dictamen de apertura

```text
MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v0.1.1-draft
Status: Draft / Ready for review
```

This MIR is suitable for review after approval of:

```text
code-surface-audit-MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v0.1.1-draft
```

After this MIR is accepted, the next artifacts are the execution package files:

```text
context.md
codex-prompt.md
acceptance-map.md
```
