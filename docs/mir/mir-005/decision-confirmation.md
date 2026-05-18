# Decision Confirmation — MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001

```text
Document ID:  DECISION-CONFIRMATION-MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Title:        Decision Confirmation — SC-C TemporalActs Seed
Version:      v0.1.0
Status:       Approved for Execution Package
Date:         2026-05-18
Corpus:       Sovereign Connect
Type:         Execution Package / Decision Confirmation
Plane:        SC-C
MU:           MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Slot:         MU-005
```

---

## 0. Purpose

This document closes the implementation decisions required before opening the Codex execution package for:

```text
MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
```

It is normative for `context.md`, `acceptance-map.md` and `codex-prompt.md`.

The implementation MUST follow this document when a decision could otherwise be inferred ambiguously from the MIR, SDD or Code Surface Audit.

---

## 1. Decision summary

```text
DEC-005-001  Profile A only.
DEC-005-002  Targetless timers are first-class.
DEC-005-003  Virtual threads over Spring @Scheduled.
DEC-005-004  Durable temporal_acts state is authoritative.
DEC-005-005  H2TemporalActRepository is preferred over expanding H2BaseTopologyRepository.
DEC-005-006  Fire idempotency is enforced by conditional database transition.
DEC-005-007  temporal_acts + ledger + outbox writes share one transaction where required.
DEC-005-008  TemporalActCreated is ledger-first; creation outbox is not required for seed.
DEC-005-009  SC-C exposes dueAt, not countdown state.
DEC-005-010  ARMED is seed-equivalent to PENDING.
DEC-005-011  MISFIRED classification must not block startup indefinitely.
DEC-005-012  Tests must use deterministic one-shot methods, not timing sleeps.
```

---

## 2. DEC-005-001 — Profile A only

Decision:

```text
MU-005 implements Profile A — Signal-first only.
```

Required consequence:

```text
SignalTemporalPayload is implemented.
ActionTemporalPayload is not implemented as a runtime type in MU-005.
```

Forbidden:

```text
ActionRequest materialization
ActionTemporalPayload behavior
command dispatch
physical adapter execution
SC-B runtime
NATS / JetStream
```

---

## 3. DEC-005-002 — Targetless timers are first-class

Decision:

```text
SignalTemporalPayload TemporalActs MUST support timers and alarms without device targets.
```

Canonical examples:

```text
"avísame en 45 minutos"
"pon una alarma dentro de cincuenta minutos"
"recordame revisar algo a las 18:30"
```

Rules:

```text
SignalTemporalPayload MUST NOT require deviceId.
SignalTemporalPayload MUST NOT require endpointId.
SignalTemporalPayload MUST NOT require capabilityId.
SignalTemporalPayload MUST NOT require TopologyTargetRef.
SignalTemporalPayload MUST NOT call validateTarget(...).
notificationTargetRef is carried opaquely and is not resolved by SC-C.
```

---

## 4. DEC-005-003 — Virtual threads over Spring @Scheduled

Decision:

```text
The seed Temporal Engine MUST use Java virtual threads or a virtual-thread-backed executor.
Spring @Scheduled MUST NOT be used as the primary temporal firing mechanism.
```

Allowed shape:

```text
TemporalEngineRunner starts/stops a virtual-thread polling loop.
TemporalEngineService exposes deterministic one-shot methods.
Tests exercise one-shot methods.
```

Forbidden shape:

```text
A method annotated with @Scheduled as the primary firing mechanism.
Tests whose correctness depends on Thread.sleep timing.
```

Rationale:

```text
The runner must have explicit lifecycle, be testable, and avoid annotation-hidden scheduling semantics.
```

---

## 5. DEC-005-004 — Durable state is authoritative

Decision:

```text
temporal_acts persisted state is authoritative.
Virtual threads are runtime mechanics only.
```

Consequences:

```text
Process memory is not temporal authority.
After restart, due/future eligibility is recovered from persistence.
The runner MUST NOT become the source of truth.
```

---

## 6. DEC-005-005 — H2TemporalActRepository preferred

Decision:

```text
Implement H2TemporalActRepository as a sibling persistence adapter for temporal_acts.
Do not expand H2BaseTopologyRepository unless the implementation reports an explicit, justified seed-local consolidation exception.
```

Required boundary:

```text
Domain services MUST NOT import SQL, JdbcTemplate, DataSource or H2 classes.
```

Preferred shape:

```text
H2TemporalActRepository implements TemporalActWritePort / TemporalActReadPort.
DefaultTemporalEngine coordinates TemporalAct persistence + ScLedgerWritePort + ScOutboxWritePort transactionally.
```

---

## 7. DEC-005-006 — Fire idempotency by conditional transition

Decision:

```text
Fire idempotency is enforced by the firing transaction.
No separate TemporalActFireIdempotencyPort is introduced.
```

Required guard:

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

If update count is one:

```text
Append TemporalActFired / TimerFired ledger entry.
Append TimerFired outbox entry with OutboxEntryStatus.PENDING.
```

---

## 8. DEC-005-007 — Shared transaction boundary

Decision:

```text
Required lifecycle transitions MUST commit with their required ledger/outbox records in one transaction over the same DataSource.
```

Required implementation mechanism:

```text
Use TransactionTemplate + DataSourceTransactionManager, or an equivalent explicit transaction boundary.
```

Forbidden:

```text
temporal_acts commits while required ledger record fails.
FIRED transition commits while required TimerFired outbox entry fails.
ledger/outbox append runs in a split transaction for required lifecycle records.
```

---

## 9. DEC-005-008 — TemporalActCreated is ledger-first

Decision:

```text
Profile A MUST write TemporalActCreated to ledger.
TemporalActCreated outbox entry is optional and not required for the first seed.
```

Required seed behavior:

```text
Create = temporal_acts row + TemporalActCreated ledger entry atomically.
TimerFired / TemporalActFired = FIRED transition + ledger + outbox atomically.
TemporalActCancelled = CANCELLED transition + ledger atomically.
TemporalActMisfired = MISFIRED transition + ledger atomically.
```

---

## 10. DEC-005-009 — No countdown state in SC-C

Decision:

```text
SC-C exposes dueAt.
SC-C MUST NOT expose remainingMs, countdown ticks or UX countdown state as canonical temporal state.
```

Consumer rule:

```text
Hub, Surface or Projection consumers may compute remaining = dueAt - Instant.now().
```

---

## 11. DEC-005-010 — ARMED is seed-equivalent to PENDING

Decision:

```text
For Profile A, ARMED is semantically equivalent to PENDING for eligibility.
The seed creates TemporalActs only in PENDING.
The seed does not implement PENDING -> ARMED.
```

Required behavior:

```text
Create: status = PENDING.
Due polling: status IN ('PENDING','ARMED').
Fire transition guard: status IN ('PENDING','ARMED') AND due_at_ms <= now.
Cancel transition guard: status IN ('PENDING','ARMED').
MISFIRED transition guard: status IN ('PENDING','ARMED') AND due_at_ms <= recoveryNow.
```

Forbidden:

```text
markArmed(...)
armTemporalAct(...)
PENDING -> ARMED activator
require ARMED before firing
```

---

## 12. DEC-005-011 — MISFIRED classification liveness

Decision:

```text
MISFIRED classification MUST NOT block the startup gate indefinitely.
```

Seed allowance:

```text
The seed MAY process all overdue acts in a single batch.
A configurable batch limit is optional for MU-005.
```

If batch limit is introduced:

```text
Classification MUST make deterministic progress.
The runner MUST NOT start firing before due non-terminal acts covered by the recovery gate are classified.
```

---

## 13. DEC-005-012 — Deterministic tests over background timing

Decision:

```text
TemporalEngineService MUST expose deterministic one-shot methods for tests.
```

Required methods or equivalents:

```text
pollDueOnce(habitatId, now)
fireDueTemporalActOnce(habitatId, temporalActId, now)
classifyMisfires(habitatId, recoveryNow)
```

Tests MUST NOT depend on:

```text
Thread.sleep timing
real-time waiting
background runner timing races
```

---

## 14. Closure

This decision confirmation is complete for opening the MU-005 execution package.

No open decision remains that should block `context.md`, `acceptance-map.md` or `codex-prompt.md`.
