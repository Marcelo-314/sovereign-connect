# implementation-report.md — MU-016 Review Blockers Patch

```text
Document:            implementation-report.md
Version:             v0.2.3-patch
MU:                  MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot:    MU-016
Patch package:        docs/mir/mir-016/patch-001-review-blockers
Branch:
Baseline commit:
Implementation commit:
Executor:
Date:
Status:
```

---

## 1. Patch summary

Describe the focused correction patch. Confirm that this is not a new MU and not a scope expansion.

---

## 2. Blockers closed

| Blocker | Status | Evidence |
|---|---|---|
| B-01 Recovery completion gate |  |  |
| B-02 Atomic request idempotency |  |  |
| B-03 Unknown payload FAILED ledger + observation |  |  |
| B-04 Heartbeat TTL + lock loss |  |  |

---

## 3. High-priority items closed or documented

| Item | Status | Evidence / rationale |
|---|---|---|
| H-01 DEGRADED -> RUNNING recovery |  |  |
| H-02 tryAcquire TOCTOU / fail-fast evidence |  |  |
| H-03 runtime failure mapping |  |  |
| H-04 health counters |  |  |
| H-05 BLOB ID binding |  |  |

---

## 4. Atomic idempotency strategy

Document the chosen strategy.

Required confirmation:

```text
No accepted TemporalAct creation can commit without final idempotency record.
No idempotency insert failure leaves a committed TemporalAct side effect.
Replay/conflict behavior is stable.
```


## 4.1 Cancel idempotency result classification

Confirm stable persisted result behavior:

```text
First-time Cancelled:
  result_kind:
  result_code:

First-time AlreadyTerminal:
  result_kind:
  result_code:

First-time NotFound:
  result_kind:
  result_code:

Failed / inconsistent cancel:
  result_kind:
  result_code:
```

Confirm that the idempotency row is written only after final result classification is known.
Confirm replay behavior for `NOT_FOUND`, `ALREADY_TERMINAL`, and `FAILED`.

---

## 5. Recovery completion strategy

Document whether the implementation:

```text
loops until complete;
fails closed on incomplete recovery;
uses maxRecoveryBatchesPerStartup;
uses zero-progress guard.
```

---

## 6. Lock / heartbeat strategy

Document:

```text
heartbeat SQL;
TTL behavior;
affected row handling;
lock loss behavior;
tryAcquire hardening or deployment rationale;
second lifecycle test evidence.
```

---

## 7. Unknown payload strategy

Document:

```text
FAILED transition;
TemporalActFailed ledger evidence;
recovery finding evidence;
observation behavior for UNKNOWN payload;
no ActionTemporalPayload implicit activation.
```

---

## 8. Runtime failure mapping

Provide final mapping:

```text
DISABLED   ->
RECOVERING ->
STARTING   ->
STOPPED    ->
FAILED     ->
SINGLE_NODE_GUARD_VIOLATED ->
```

---

## 9. Health counters

Confirm which counters exist and where each is incremented.

```text
firedTotal:
misfiredTotal:
cancelledTotal:
failedTotal:
skippedTotal:
```

---

## 10. BLOB ID binding

Confirm strategy:

```text
Option A — UUID string <-> byte[16] binding implemented.
Option B — schema TEXT chosen; corpus patch required.
Option C — explicit deferred debt accepted for this patch; no schema/code change.
```

List affected columns and tests.

---

## 11. Tests

Before patch:

```text
Tests run:
Failures:
Errors:
Skipped:
```

After patch:

```text
Tests run:
Failures:
Errors:
Skipped:
```

List added/modified tests.

---

## 12. Scope preservation

Confirm:

```text
No ActionTemporalPayload.
No ActionRequest.
No SC-B.
No NATS / JetStream.
No outbox dispatcher.
No View Composer.
No Effective Access Boundary.
No unrelated .idea / mir-001 / mir-002 noise.
```

---

## 13. Final disposition

```text
MU-016 patch result:
  PASS / PARTIAL PASS / BLOCKED

Candidate L4:
  yes / no
```
