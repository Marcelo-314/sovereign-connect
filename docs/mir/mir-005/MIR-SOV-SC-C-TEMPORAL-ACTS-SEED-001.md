# MIR-SOV-SC-C-TEMPORAL-ACTS-SEED-001

## SC-C TemporalActs Seed

```text
Document ID:  MIR-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Title:        SC-C TemporalActs Seed
Version:      v0.1.1-draft
Status:       Draft
Date:         2026-05-18
Corpus:       Sovereign Connect
Type:         MIR
Plane:        SC-C
MU:           MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Slot:         MU-005
Scope:        Signal-first durable TemporalActs seed / virtual-thread temporal engine / targetless timers / fire idempotency / ledger-outbox firing records
```

---

## Changelog v0.1.1-draft

Patch release after MIR review.

This version:

1. Closes the ARMED-state open question for the Profile A seed: `ARMED` is seed-equivalent to `PENDING`; the seed creates acts only in `PENDING` and does not implement a separate `PENDING -> ARMED` activator.
2. Adds a MISFIRED classification batching / startup-gate liveness rule: MISFIRED classification MUST NOT block startup indefinitely; processing all overdue acts in one batch is allowed for the seed, and a configurable batch limit is optional.
3. Replaces generic acceptance criterion `AC-005-028 New TemporalActs tests pass` with the verifiable criterion `All T-001 through T-022 tests pass`.
4. Requires `decision-confirmation.md` to record the ARMED-state decision before the execution package is considered complete.

---

## 0. Purpose

This MIR authorizes a bounded implementation descent for:

```text
MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
```

The goal is to materialize the first SC-C-owned TemporalActs runtime seed under **Profile A — Signal-first**.

This MU validates that SC-C can own durable temporal commitments such as:

```text
"avísame en 45 minutos"
"pon una alarma dentro de cincuenta minutos"
"recordame revisar algo a las 18:30"
```

without requiring a device target, adapter command, SC-B runtime, NATS/JetStream, Hub memory, Surface-local timers, session identity or policy evaluation inside SC-C.

The implementation MUST prove that a TemporalAct can be:

```text
created;
queried;
cancelled;
fired when due;
classified as MISFIRED on recovery;
recorded through SC-C ledger/outbox;
protected against double semantic firing;
recovered across repository/service recreation.
```

---

## 1. Classification

```text
Surface category:       Greenfield-with-neighbors / bounded infrastructure integration
Execution discipline:   Non-greenfield for execution-package purposes
Implementation profile: Profile A — Signal-first only
Expected acceptance:    Validated L4
```

Rationale:

The TemporalAct domain does not yet exist in code, but the implementation must integrate with validated neighboring surfaces:

```text
MU-014 — Concurrency / Idempotency Seed
MU-015 — Outbox / Ledger Storage Seed
H2 / JdbcTemplate persistence pattern
Clock injection pattern
SC-C topology and recovery boundaries
```

Therefore, this is not a free greenfield implementation.

---

## 2. Normative references

Depends on:

```text
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.8-draft
PDR-SOV-SC-C-TEMPORAL-ACTS-001 v0.1.1-draft
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001 v0.1.1-draft
SDD-SOV-SC-C-RECOVERY-001 v0.1.2-draft
SDD-SOV-SC-C-OUTBOX-LEDGER-001 v0.1.1-draft
SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.1.2-draft
PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.7-draft
PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft
PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 v0.1.0-draft
PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001 v0.1.1-draft
PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.5-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.14-draft
SYNC-SOV-SC-MU-BACKLOG-001 v0.1.15-draft
MIR-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001 v1.0.0-accepted
MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001 v1.0.0-accepted
```

Required execution artifact:

```text
CSA-MU-005 v0.1.1-merged
code-surface-audit-MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001 v0.1.1-merged
Status: Approvable
```

---

## 3. Implementation thesis

TemporalActs are SC-C-owned durable runtime state.

The first seed MUST implement durable, signal-oriented timers without device targets.

Canonical thesis:

```text
TemporalAct is SC-C-owned durable runtime state.
SignalTemporalPayload supports targetless timers and alarms.
Temporal Engine evaluates due TemporalActs.
Ledger/Outbox records temporal lifecycle facts.
SC-B may later transport records but does not decide whether an act fired.
SC-D executes provider protocols but is not involved in Profile A.
Hub/Surface may render countdowns and notifications but do not own temporal state.
```

---

## 4. Scope

This MU MUST implement **Profile A — Signal-first** only.

Required scope:

```text
TemporalAct aggregate
TemporalActStatus vocabulary
SignalTemporalPayload
CreatedByRef
TemporalAct read/write ports
TemporalAct service / engine service
H2 temporal_acts persistence
Virtual-thread based temporal engine runner
Deterministic one-shot engine methods for tests
Create TemporalAct
Cancel TemporalAct
Query active / due / terminal / misfired acts
Fire due SignalTemporalPayload acts
MISFIRED classification for overdue non-terminal acts during recovery flow
TemporalActCreated ledger entry
TemporalActCancelled ledger entry
TemporalActFired / TimerFired ledger entry
TemporalActFired / TimerFired outbox entry
TemporalActMisfired ledger entry
Fire idempotency through conditional database update
Atomic transaction covering temporal_acts + ledger + outbox where required
Recovery across repository/service recreation
```

Profile A must support timers with no device target, such as:

```text
SignalTemporalPayload(label="Despertador", signalKind="alarm")
notificationTargetRef="surface:bedroom-left"
```

A SignalTemporalPayload TemporalAct MUST NOT require:

```text
deviceId
endpointId
capabilityId
TopologyTargetRef
ActionRequest
adapter reference
SC-B route
```

---

## 5. Explicit non-goals

This MU MUST NOT implement:

```text
ActionTemporalPayload runtime behavior
ActionRequest materialization
Device-targeted timers
Endpoint-targeted timers
Command dispatch
Physical adapter execution
SC-B runtime
NATS / JetStream
Outbox dispatcher
Outbox claim loop
Outbox retry loop
Broker delivery worker
Surface notification rendering
Hub memory
Session identity
User identity
Authority / Policy scheduling
Projection / Effective View
Natural language time parsing
Timezone inference from user context
remainingMs / countdown ticks as SC-C canonical state
Recurrence
Cron
Calendar engine
Sunrise/sunset semantics
Distributed scheduling
Clustering / leader election
Production SQLite/Flyway migrations
```

---

## 6. Design decisions

### DEC-005-001 — Profile A only

Decision:

```text
MU-005 implements Profile A — Signal-first only.
```

Consequence:

```text
Only SignalTemporalPayload has runtime behavior.
ActionTemporalPayload remains reserved and MUST NOT be materialized as a runtime type in this MU.
```

---

### DEC-005-002 — Targetless timers are first-class

Decision:

```text
TemporalActs with SignalTemporalPayload MUST support timers and alarms without device targets.
```

Rationale:

A user-facing timer such as “avísame en 45 minutos” is not a device action. It is a durable SC-C temporal commitment carrying an opaque notification target.

Consequence:

```text
SignalTemporalPayload MUST NOT require TopologyTargetRef.
SignalTemporalPayload MUST NOT call validateTarget(...).
notificationTargetRef is carried opaquely and is not resolved by SC-C.
```

---

### DEC-005-003 — Virtual threads over Spring @Scheduled

Decision:

```text
The seed Temporal Engine MUST be based on Java virtual threads or a virtual-thread-backed executor.
It MUST NOT use Spring @Scheduled as the primary engine mechanism.
```

Rationale:

The seed should expose an explicit engine lifecycle and deterministic one-shot methods, while avoiding annotation-hidden scheduling semantics. Virtual threads align with the Java 21 stack and keep the temporal runner explicit, testable and replaceable.

Allowed shape:

```text
TemporalEngineRunner starts/stops a virtual-thread polling loop.
TemporalEngineService exposes deterministic pollDueOnce(...) and fireDueTemporalActOnce(...) methods.
Tests exercise one-shot methods and do not depend on Thread.sleep timing.
```

Forbidden shape:

```text
A method annotated with @Scheduled as the primary implementation of temporal firing.
Tests depending on real background timing to pass.
```

---

### DEC-005-004 — Durable state is authoritative

Decision:

```text
temporal_acts persisted state is authoritative.
Virtual threads are runtime mechanics only.
```

Consequence:

```text
If the process restarts, due/future eligibility is recovered from persistence.
The virtual-thread runner MUST NOT become the source of truth.
```

---

### DEC-005-005 — H2TemporalActRepository preferred

Decision:

```text
Implement a sibling H2TemporalActRepository / temporal store for temporal_acts.
Do not expand H2BaseTopologyRepository unless the execution package explicitly accepts seed-local adapter consolidation.
```

Rationale:

MU-011 and MU-012 hardened SC-C port boundaries. Temporal persistence should not further overload the topology repository unless explicitly justified.

Consequence:

```text
DefaultTemporalEngine coordinates TemporalAct persistence + ScLedgerWritePort + ScOutboxWritePort transactionally.
Domain services MUST NOT import H2, SQL, JdbcTemplate or DataSource directly.
```

---

### DEC-005-006 — Fire idempotency by conditional transition

Decision:

```text
Fire idempotency is enforced by the firing transaction, not by a separate idempotency port.
```

Required transition guard:

```sql
UPDATE temporal_acts
SET status = 'FIRED', fired_at_ms = ?, terminal_at_ms = ?, updated_at_ms = ?
WHERE temporal_act_id = ?
  AND habitat_id = ?
  AND status IN ('PENDING','ARMED')
  AND due_at_ms <= ?
```

If update count is zero:

```text
No ledger entry.
No outbox entry.
No semantic fire.
```

---

### DEC-005-007 — Atomic transaction over temporal_acts + ledger + outbox

Decision:

```text
Required temporal lifecycle transitions MUST commit with their required ledger/outbox records in one transaction.
```

Implementation requirement:

```text
Use TransactionTemplate + DataSourceTransactionManager, or an equivalent explicit transaction boundary over the same DataSource.
```

Consequence:

```text
No split transaction where temporal_acts commits but required ledger/outbox record does not.
```

---

### DEC-005-008 — TemporalActCreated is ledger-first

Decision:

```text
Profile A MUST write TemporalActCreated to ledger.
TemporalActCreated outbox entry is optional and not required for the first seed.
```

Consequence:

```text
Create = temporal_acts row + TemporalActCreated ledger entry atomically.
TimerFired / TemporalActFired remains dispatchable and MUST create ledger + outbox entry.
```

---

### DEC-005-009 — No countdown state in SC-C

Decision:

```text
SC-C exposes dueAt.
SC-C MUST NOT expose remainingMs, countdown ticks or UX countdown state as canonical TemporalAct state.
```

Consequence:

```text
Hub/Surface/Projection consumers may compute remaining = dueAt - Instant.now().
```

---

### DEC-005-010 — ARMED is seed-equivalent to PENDING

Decision:

```text
For the Profile A seed, ARMED is semantically equivalent to PENDING for firing eligibility.
The seed creates TemporalActs only in PENDING.
The seed does not implement a separate PENDING -> ARMED activator.
```

Required behavior:

```text
Create: status = PENDING.
Due polling: status IN ('PENDING','ARMED').
Fire transition guard: status IN ('PENDING','ARMED') AND due_at_ms <= now.
Cancel transition guard: status IN ('PENDING','ARMED').
MISFIRED transition guard: status IN ('PENDING','ARMED') AND due_at_ms <= recoveryNow.
```

Rationale:

```text
Profile A has no separate arming lifecycle.
ARMED remains in the status vocabulary for compatibility with the TemporalActs PDR and future profiles, but it is not produced by the seed.
```

Consequence:

```text
No markArmed(...), armTemporalAct(...), or PENDING -> ARMED transition is required or allowed in MU-005.
Decision-confirmation.md MUST record this closure before the execution package is considered complete.
```

---

## 7. Required model surface

Package:

```text
com.sovereign.connect.core.temporal.model
```

Required types:

```text
TemporalAct
TemporalActStatus
TemporalActPayload
SignalTemporalPayload
CreatedByRef
```

Reserved but not implemented in MU-005:

```text
ActionTemporalPayload
```

### 7.1 TemporalAct

Minimum aggregate shape:

```text
temporalActId: String
habitatId: String
status: TemporalActStatus
dueAt: Instant
payload: TemporalActPayload
notificationTargetRef: String nullable
createdByRef: CreatedByRef
topologyVersionAtRegistration: TopologyVersion nullable
createdAt: Instant
updatedAt: Instant
firedAt: Instant nullable
terminalAt: Instant nullable
terminalReason: String nullable
```

Rules:

```text
temporalActId is assigned by SC-C service code, not by caller-facing create APIs.
dueAt is absolute UTC Instant.
notificationTargetRef is opaque and non-personal.
createdByRef is opaque but MUST NOT encode userId, sessionId or conversationThreadId.
topologyVersionAtRegistration is nullable and non-operative for SignalTemporalPayload in Profile A.
```

### 7.2 TemporalActStatus

Exact vocabulary:

```text
PENDING
ARMED
FIRED
CANCELLED
EXPIRED
MISFIRED
FAILED
```

Terminal states:

```text
FIRED
CANCELLED
EXPIRED
MISFIRED
FAILED
```

Non-terminal states:

```text
PENDING
ARMED
```

Seed rule:

```text
MU-005 creates TemporalActs only in PENDING.
ARMED is accepted in query and transition guards but is not produced by the Profile A seed.
PENDING and ARMED are equivalent for Profile A firing eligibility.
```

### 7.3 TemporalActPayload

For MU-005:

```text
TemporalActPayload supports SignalTemporalPayload only.
```

SignalTemporalPayload minimum shape:

```text
label: String
signalKind: String
```

Rules:

```text
label is a hint, not authoritative Surface rendering state.
signalKind is an opaque category such as alarm, timer or reminder.
SignalTemporalPayload MUST NOT contain deviceId, endpointId or capabilityId.
```

---

## 8. Required persistence surface

Preferred production seed adapter:

```text
com.sovereign.connect.adapter.persistence.H2TemporalActRepository
```

Required table:

```sql
CREATE TABLE IF NOT EXISTS temporal_acts (
    temporal_act_id         VARCHAR(36)   PRIMARY KEY,
    habitat_id              VARCHAR(255)  NOT NULL,
    status                  VARCHAR(64)   NOT NULL
                            CHECK(status IN ('PENDING','ARMED','FIRED',
                                             'CANCELLED','EXPIRED','MISFIRED','FAILED')),
    due_at_ms               BIGINT        NOT NULL,
    payload_type            VARCHAR(255)  NOT NULL,
    payload_json            CLOB          NOT NULL,
    notification_target_ref VARCHAR(512),
    created_by_ref_json     CLOB          NOT NULL,
    topology_version_at_registration VARCHAR(255),
    created_at_ms           BIGINT        NOT NULL,
    updated_at_ms           BIGINT        NOT NULL,
    fired_at_ms             BIGINT,
    terminal_at_ms          BIGINT,
    terminal_reason         VARCHAR(2048)
);
```

Required indexes:

```sql
CREATE INDEX IF NOT EXISTS ix_temporal_acts_due_status
ON temporal_acts(habitat_id, status, due_at_ms);

CREATE INDEX IF NOT EXISTS ix_temporal_acts_status
ON temporal_acts(habitat_id, status);
```

Repository-level duplicate behavior:

```text
Duplicate temporalActId MUST be rejected by persistence.
Public service create APIs MUST NOT accept caller-supplied temporalActId.
```

---

## 9. Required ports

Package:

```text
com.sovereign.connect.core.temporal.port
```

Required ports:

```text
TemporalActWritePort
TemporalActReadPort
```

The write port MUST NOT expose arbitrary status mutation.

Allowed write semantics:

```text
insertCreated(...)
cancelIfNonTerminal(...)
markFiredIfDueAndNonTerminal(...)
markMisfiredIfDueAndNonTerminal(...)
```

Explicitly excluded for the seed:

```text
markArmed(...)
armTemporalAct(...)
any generic transitionTo(status) method
```

Read semantics:

```text
findById(habitatId, temporalActId)
listActive(habitatId)
findDue(habitatId, now)
findNonTerminalDueBefore(habitatId, cutoff)
listTerminal(habitatId, optionalStatus)
listMisfired(habitatId)
```

---

## 10. Required services

Package:

```text
com.sovereign.connect.core.temporal.service
```

Required services:

```text
TemporalActService
TemporalEngineService
TemporalEngineRunner
```

### 10.1 TemporalActService

Responsibilities:

```text
create SignalTemporalPayload TemporalAct;
cancel non-terminal TemporalAct;
query active / terminal / misfired acts;
validate createdByRef and notificationTargetRef opacity constraints;
assign temporalActId in SC-C.
```

### 10.2 TemporalEngineService

Responsibilities:

```text
pollDueOnce(habitatId, now);
fireDueTemporalActOnce(habitatId, temporalActId, now);
classifyMisfires(habitatId, recoveryNow);
```

These methods MUST be deterministic and testable without real background sleeps.

### 10.3 TemporalEngineRunner

Responsibilities:

```text
start virtual-thread polling loop;
stop polling loop;
respect configurable polling interval;
call pollDueOnce(...);
not act as outbox dispatcher.
```

The runner is runtime wiring. The semantic engine is TemporalEngineService.

---

## 11. Ledger / Outbox integration

MU-005 consumes validated MU-015 surfaces:

```text
ScLedgerWritePort.appendLedgerEntry(...)
ScOutboxWritePort.appendOutboxEntry(...)
```

Required records:

```text
TemporalActCreated      -> ledger entry
TemporalActCancelled    -> ledger entry
TemporalActFired        -> ledger entry
TimerFired              -> ledger entry and outbox entry when used as dispatchable signal
TemporalActMisfired     -> ledger entry
TemporalActFailed       -> ledger entry if failure occurs after eligibility
```

Required firing outbox entry:

```text
OutboundKind: TIMER_FIRED_SIGNAL
DeliveryLane: SIGNAL
OutboxEntryStatus: PENDING
notificationTargetRef: copied opaquely from TemporalAct
```

Outbox append MUST respect MU-015 PENDING-only guard.

Idempotency key for firing:

```text
temporal-act-fired:{temporalActId}
```

The existing unique constraint on ledger `(habitat_id, idempotency_key)` is a secondary guard, not the primary fire-idempotency mechanism.

---

## 12. Atomicity requirements

### 12.1 Create

Must commit atomically:

```text
temporal_acts row with status=PENDING
TemporalActCreated ledger entry
```

A persisted TemporalAct without required creation ledger entry is a consistency violation.

### 12.2 Cancel

Must commit atomically:

```text
status = CANCELLED
terminalAt
updatedAt
TemporalActCancelled ledger entry
```

If the act is already terminal:

```text
Do not create cancellation ledger entry.
Return existing terminal outcome or no-op result.
```

### 12.3 Fire

Must commit atomically:

```text
conditional status transition to FIRED
firedAt
terminalAt
TemporalActFired / TimerFired ledger entry
TimerFired outbox entry
```

If conditional update count is zero:

```text
No ledger.
No outbox.
No semantic fire.
```

### 12.4 MISFIRED

Must commit atomically per act:

```text
status = MISFIRED
terminalAt
updatedAt
TemporalActMisfired ledger entry
```

MISFIRED outbox entry is optional in Profile A and not required for first seed.

Startup-gate liveness rule:

```text
MISFIRED classification MUST NOT block the startup gate indefinitely.
The seed MAY process all overdue acts in a single batch.
A configurable batch limit is optional for MU-005.
If a batch limit is introduced, classification MUST make deterministic progress and MUST NOT enable the runner before due non-terminal acts covered by the recovery gate are classified.
```

---

## 13. Recovery integration

During startup recovery, before TemporalEngineRunner starts:

```text
classifyMisfires(habitatId, recoveryNow)
```

must mark overdue non-terminal acts as MISFIRED.

Rules:

```text
MISFIRED acts are terminal.
MISFIRED acts remain queryable.
MISFIRED acts MUST NOT be rearmed.
MISFIRED acts MUST NOT be fired by the runner.
Recovery MUST NOT dispatch ActionTemporalPayload.
```

TemporalActs recovery feature-gate:

```text
If temporal_acts table exists for the selected schema version, classify and validate.
If the selected schema version predates temporal_acts, record NOT_APPLICABLE.
If the selected schema version requires temporal_acts but the table is absent, fail schema consistency.
```

---

## 14. Virtual-thread runner constraints

Required:

```text
Use Java virtual threads or a virtual-thread-backed executor for the seed polling runner.
Polling interval MUST be configurable.
Profile A seed default SHOULD be <= 1 second.
```

Forbidden:

```text
Spring @Scheduled as primary engine mechanism.
Hardcoded polling interval.
Tests relying on Thread.sleep for correctness.
```

The default polling interval supports near-real-time TimerFired observability for user-facing timers.
Countdown rendering remains outside SC-C and is computed by consumers from dueAt.

---

## 15. Boundary invariants

MU-005 MUST preserve:

```text
MU-011: structural topology persistence remains separated from health/state persistence.
MU-012: materialization services do not depend on concrete persistence adapters.
MU-013: spatial topology consistency checks remain intact.
MU-014: materialization replay/idempotency behavior remains intact.
MU-015: appendOutboxEntry accepts only PENDING entries.
MU-015: appendLedgerEntry remains INSERT-only, not MERGE.
SC-C does not absorb SC-B runtime.
SC-C does not absorb SC-D provider execution.
SC-C does not absorb Projection, Hub, Session, Identity, Authority or Policy.
```

---

## 16. Acceptance criteria

```text
AC-005-001  TemporalAct aggregate exists in core.temporal.model.
AC-005-002  TemporalActStatus exact vocabulary exists.
AC-005-003  SignalTemporalPayload exists and supports targetless timers.
AC-005-004  ActionTemporalPayload runtime behavior is not implemented.
AC-005-005  CreatedByRef exists and is stored opaquely.
AC-005-006  temporal_acts table exists with status CHECK constraint.
AC-005-007  temporal_acts indexes exist for due/status queries.
AC-005-008  TemporalActReadPort and TemporalActWritePort exist.
AC-005-009  Write port does not expose arbitrary status mutation.
AC-005-010  Public create API assigns temporalActId in SC-C.
AC-005-011  Create commits temporal_acts row and TemporalActCreated ledger entry atomically.
AC-005-012  Cancel terminalizes non-terminal acts and appends TemporalActCancelled ledger entry atomically.
AC-005-013  Fire uses conditional UPDATE with status guard and due_at_ms <= now.
AC-005-014  Fire update count 0 creates no ledger/outbox entry.
AC-005-015  Fire update count 1 creates TemporalActFired/TimerFired ledger entry.
AC-005-016  Fire update count 1 creates TimerFired outbox entry with PENDING status.
AC-005-017  notificationTargetRef is copied opaquely into firing record/outbox entry.
AC-005-018  Fire idempotency prevents double semantic fire.
AC-005-019  MISFIRED classification marks overdue non-terminal acts terminal.
AC-005-020  MISFIRED acts remain queryable but are not active.
AC-005-021  Virtual-thread runner exists and does not use @Scheduled as primary mechanism.
AC-005-022  Polling interval is configurable with seed default <= 1 second.
AC-005-023  Deterministic one-shot engine methods exist for tests.
AC-005-024  Tests do not rely on Thread.sleep for correctness.
AC-005-025  SC-C exposes dueAt but not remainingMs/countdown ticks as canonical state.
AC-005-026  Services do not import SQL/JdbcTemplate/DataSource directly.
AC-005-027  Existing 75 tests still pass.
AC-005-028  All T-001 through T-022 tests pass.
AC-005-029  MISFIRED classification cannot block startup indefinitely; all-overdue single-batch processing is allowed and any batch-limited variant makes deterministic progress.
```

---

## 17. Minimum test map

Required tests:

```text
T-001  createSignalTemporalActPersistsDurably
T-002  repositoryDuplicateTemporalActIdRejected
T-003  createTemporalActAppendsCreatedLedgerEntry
T-004  createTemporalActAtomicityPreserved
T-005  cancelNonTerminalActSucceeds
T-006  fireThenCancelReturnsAlreadyTerminal
T-007  cancelThenFireDoesNotCreateTimerFired
T-008  listActiveReturnsOnlyNonTerminalActs
T-009  fireTransitionAdvancesStatusToFired
T-010  fireRequiresDueAtReached
T-011  fireIdempotencyGuardPreventsDoubleFire
T-012  fireLedgerEntryCreatedAtomically
T-013  fireOutboxEntryCreatedAtomically
T-014  notificationTargetRefCarriedInFiringRecord
T-015  misfiredClassificationOnStartupForOverdueActs
T-016  misfiredActIsNotRefired
T-017  misfiredActRemainsQueryable
T-018  pollingIntervalIsConfigurable
T-019  durabilityAfterRepositoryRecreation
T-020  domainServicesHaveNoSqlImports
T-021  virtualThreadRunnerDoesNotUseScheduledAnnotation
T-022  targetlessSignalTimerDoesNotRequireTopologyTarget
```

Expected minimum:

```text
Existing tests: 75
New tests:      >= 20
Expected total: >= 95
Failures:       0
Errors:         0
Skipped:        0
```

---

## 18. Failure signals

Implementation MUST stop and report upstream if:

```text
FS-005-001  TemporalAct is implemented only as in-memory timer state.
FS-005-002  Profile A requires deviceId / endpointId / capabilityId.
FS-005-003  SignalTemporalPayload calls validateTarget(...).
FS-005-004  ActionTemporalPayload runtime behavior is implemented.
FS-005-005  SC-B or NATS is required for Profile A.
FS-005-006  Spring @Scheduled is used as primary engine mechanism.
FS-005-007  Fire can occur before dueAt.
FS-005-008  Fire can occur twice for the same TemporalAct.
FS-005-009  Ledger/outbox append happens outside required transaction.
FS-005-010  TimerFired outbox entry is non-PENDING at append.
FS-005-011  Outbox dispatcher, claim loop or retry loop is introduced.
FS-005-012  notificationTargetRef is interpreted as user/session/policy.
FS-005-013  MISFIRED act is active or refired.
FS-005-014  Domain service imports SQL/JdbcTemplate/DataSource.
FS-005-015  Existing MU-015 PENDING-only guard is removed or weakened.
FS-005-016  Existing 75 tests regress.
FS-005-017  Profile A introduces a separate PENDING -> ARMED activator or requires ARMED before due firing.
FS-005-018  MISFIRED classification can block the startup gate indefinitely.
```

---

## 19. Execution package requirements

The execution package MUST include:

```text
docs/mir/mir-005/context.md
docs/mir/mir-005/code-surface-audit.md
docs/mir/mir-005/decision-confirmation.md
docs/mir/mir-005/acceptance-map.md
docs/mir/mir-005/codex-prompt.md
docs/mir/mir-005/implementation-report.md
```

The MIR MUST NOT include the Codex prompt inline.

`decision-confirmation.md` MUST explicitly record:

```text
DEC-005-010 — ARMED is seed-equivalent to PENDING.
The seed creates acts only in PENDING and does not implement PENDING -> ARMED activation.
```

Recommended branch:

```text
feat/sc-c-temporal-acts-seed
```

Recommended commit:

```text
feat(sc-c): add signal-first temporal acts seed
```

---

## 20. Open questions

```text
OQ-005-001 — ARMED state semantics
Status: Closed by DEC-005-010.
Resolution: ARMED is seed-equivalent to PENDING in Profile A. The seed creates acts only in PENDING, does not implement a PENDING -> ARMED activator, and uses status IN ('PENDING','ARMED') for due polling, fire, cancel and MISFIRED transition guards.

OQ-005-002
Should TemporalActCreated create an outbox entry in Profile A?
Current MIR decision: no, ledger-only for first seed.

OQ-005-003
Should TemporalActMisfired create an outbox entry in Profile A?
Current MIR decision: no, ledger-only for first seed.

OQ-005-004
Should query by notificationTargetRef be implemented in the first seed?
Current MIR decision: no unless explicitly added by execution package; avoid identity/session/policy ambiguity.

OQ-005-005
Should H2BaseTopologyRepository be expanded instead of adding H2TemporalActRepository?
Current MIR decision: prefer H2TemporalActRepository; consolidation requires explicit exception.
```

---

## 21. Version disposition

`MIR-SOV-SC-C-TEMPORAL-ACTS-SEED-001 v0.1.1-draft` is ready for review.

If accepted, it authorizes the execution package for:

```text
MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Profile A only
Virtual-thread based temporal engine
Targetless SignalTemporalPayload timers
Durable fire idempotency through ledger/outbox-backed transaction
```
