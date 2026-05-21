# context.md — MU-016 patch-001-review-blockers

```text
Package:              execution-package-MU-016-review-blockers-patch
Version:              v0.2.3
MU:                   MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot:     MU-016
Patch scope:          Review blockers after sovereign-connect-016.zip
Target branch:        fix/sc-c-mir-016-temporal-engine-industrial-hardening
Input review:         final-technical-review-MU-016-temporal-engine-industrial-hardening-v0.1.1
Baseline ZIP:         sovereign-connect-016.zip
Baseline tests:       109 / 0 failures
```

---

## 0. How to use this file

Every section resolves something Codex would otherwise infer. Read completely before touching code.
Sections 2–5 give exact current code. Sections 6–13 give exact target code and binding design decisions; §14 gives required test scenarios.
Do not deviate from the strategy in §6 for B-02 — it is the architecture decision for this patch.

---

## 1. Scope — what changes and what does not

**Change only:**

```text
TemporalEngineLifecycle            (B-01: recovery loop)
TemporalActApplicationService      (B-02: atomic idempotency — needs TransactionTemplate injected)
TemporalEngineService              (B-03: failUnknownPayload ledger)
TemporalActObservationService      (B-03: null-safe toObservation)
SQLiteTemporalEngineLockRepository (B-04: heartbeat SQL + affectedRows check)
TemporalEngineLockPort             (B-04: heartbeat signature)
TemporalEngineLifecycle            (B-04: pass ttlMs to heartbeat; lock-loss handling)
TemporalEngineHealth               (H-01: DEGRADED→RUNNING; H-03: precise notReady; H-04: counters)
TemporalEngineProperties           (B-01: maxRecoveryBatchesPerStartup)
TemporalEngineConfiguration        (wire TransactionTemplate into ApplicationService)
Tests                              (all 16 required tests in §14)
```

**Do NOT touch:**

```text
H2TemporalActRepository           (seed legacy — preserve exactly)
TemporalActSeedTest               (preserve all 26 tests)
BaseTopologyService and topology tests
MIR or corpus documents outside docs/mir/mir-016/
.idea/ files, mir-001/, mir-002/
```

**Do NOT introduce:**

```text
ActionTemporalPayload, ActionRequest, SC-B, NATS, dispatcher, View Composer,
Effective Access Boundary, recurrence, cron semantics, SC-D
```

---

## 2. Current code surface — TemporalActApplicationService

File: `src/main/java/com/sovereign/connect/core/temporal/application/TemporalActApplicationService.java`

**Current constructor (5 args — missing TransactionTemplate):**

```java
public TemporalActApplicationService(
    TemporalActService actService,
    TemporalActObservationPort observationPort,
    TemporalRequestIdempotencyPort idempotencyPort,
    TemporalEngineHealth health,
    ObjectMapper objectMapper
)
```

**Current create flow (non-atomic — the bug):**

```java
// TX-1: actService.createSignalTemporalAct(...) — opens its own txTemplate.executeWithoutResult
String id = actService.createSignalTemporalAct(
    request.habitatId(),
    new SignalTemporalPayload(request.label(), request.signalKind()),
    request.dueAt(), request.notificationTargetRef(),
    new CreatedByRef(request.createdByRef())
);
// Gap: crash here leaves act without idempotency record
TemporalActObservation observation = observationPort.findById(request.habitatId(), id).orElseThrow();
idempotencyPort.insert(new TemporalRequestIdempotencyRecord(...), request.requestedAt()); // TX-2: separate
return new CreateSignalTemporalActResult.Accepted(observation);
```

**Current cancel flow (non-atomic — same bug):**

```java
int updated = actService.cancelTemporalAct(...);  // TX-1: its own transaction
TemporalActObservation after = observationPort.findById(...).orElseThrow();
idempotencyPort.insert(new TemporalRequestIdempotencyRecord(...), request.requestedAt()); // TX-2: separate
```

**notReady() — always returns same code regardless of status:**

```java
private TemporalRuntimeFailure notReady() {
    return new TemporalRuntimeFailure(TemporalRuntimeFailureCode.RECOVERY_NOT_COMPLETED, "engine not yet ready");
}
```

---

## 3. Current code surface — TemporalActService

File: `src/main/java/com/sovereign/connect/core/temporal/service/TemporalActService.java`

**Current constructor (6 args):**

```java
public TemporalActService(
    TemporalActWritePort writePort,
    TemporalActReadPort readPort,
    ScLedgerWritePort ledgerPort,
    TransactionTemplate txTemplate,
    ObjectMapper objectMapper,
    Clock clock
)
```

**Current createSignalTemporalAct — starts its OWN transaction:**

```java
public String createSignalTemporalAct(
    String habitatId, SignalTemporalPayload payload, Instant dueAt,
    String notificationTargetRef, CreatedByRef createdByRef
) {
    String temporalActId = UUID.randomUUID().toString();
    Instant now = Instant.now(clock);
    TemporalAct act = new TemporalAct(temporalActId, habitatId, TemporalActStatus.PENDING,
        dueAt, payload, notificationTargetRef, createdByRef, null, now, now, null, null, null);
    txTemplate.executeWithoutResult(status -> {       // ← owns transaction
        writePort.insertCreated(act);
        ledgerPort.appendLedgerEntry(createdLedgerEntry(act, now));
    });
    return temporalActId;
}
```

**Current cancelTemporalAct — starts its OWN transaction:**

```java
public int cancelTemporalAct(String habitatId, String temporalActId, Instant now) {
    return txTemplate.execute(status -> {             // ← owns transaction
        int updated = writePort.cancelIfNonTerminal(habitatId, temporalActId, now);
        if (updated == 1) {
            ledgerPort.appendLedgerEntry(cancelledLedgerEntry(habitatId, temporalActId, now));
        }
        return updated;
    });
}
```

**Problem:** When called from `TemporalActApplicationService`, these start nested independent transactions because `TransactionTemplate` uses `PROPAGATION_REQUIRED` by default — but only if the caller is already in a transaction managed by Spring's transaction synchronization. Since `TemporalActApplicationService` currently has NO `TransactionTemplate`, it cannot open a wrapping transaction.

---

## 4. Current code surface — TemporalEngineLifecycle

File: `src/main/java/com/sovereign/connect/core/temporal/engine/TemporalEngineLifecycle.java`

**Current start() — calls classifyMisfires ONCE, ignores result.complete():**

```java
@Override
public void start() {
    if (!properties.enabled()) {
        health.transitionTo(TemporalEngineStatus.DISABLED);
        return;
    }
    health.transitionTo(TemporalEngineStatus.RECOVERING);
    TemporalRecoveryResult result = engineService.classifyMisfires(      // ← called ONCE
        properties.habitatId(), Instant.now(clock), properties.maxRecoveryBatchSize()
    );
    health.incrementMisfired(result.misfiredCount());
    health.incrementFailed(result.failedCount());
    if (properties.singleNodeGuardEnabled()) {
        boolean acquired = lockRepository.tryAcquire(...);
        if (!acquired) { health.transitionTo(TemporalEngineStatus.FAILED); throw ...; }
        startHeartbeat();
    }
    health.transitionTo(TemporalEngineStatus.RUNNING);   // ← runs even if complete == false
    runner.start();                                       // ← starts polling regardless
    running = true;
}
```

**current startHeartbeat() — passes no ttlMs to heartbeat:**

```java
lockRepository.heartbeat(
    properties.habitatId(),
    STORAGE_PARTITION_REF,
    engineInstanceId,
    Instant.now(clock)                     // ← no ttlMs arg; heartbeat computes wrongly
);
```

---

## 5. Current code surface — lock repository and engine service

**Current TemporalEngineLockPort.heartbeat signature (no ttlMs):**

```java
void heartbeat(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now);
```

**Current SQLiteTemporalEngineLockRepository.heartbeat SQL (wrong expiry):**

```java
jdbcTemplate.update("""
    UPDATE temporal_engine_locks
    SET heartbeat_at_ms = ?,
        expires_at_ms = expires_at_ms + (expires_at_ms - heartbeat_at_ms)   ← BUG
    WHERE ...
""", now.toEpochMilli(), habitatId, storagePartitionRef, engineInstanceId);
```

**Current TemporalEngineService.failUnknownPayload (no ledger — the gap):**

```java
private void failUnknownPayload(String habitatId, String temporalActId, Instant now) {
    txTemplate.executeWithoutResult(status ->
        writePort.markFailed(habitatId, temporalActId, "unknown payload kind", now)
        // ← ledger entry MISSING
    );
}
```

**SemanticKind.TEMPORAL_ACT_FAILED exists in the enum** — use it for the missing ledger entry.

**Current TemporalActObservationService.toObservation (unsafe cast):**

```java
private TemporalActObservation toObservation(TemporalAct act) {
    SignalTemporalPayload payload = (SignalTemporalPayload) act.payload();  // ← NPE if null
    return new TemporalActObservation(
        ..., payload.label(), payload.signalKind(), ...
    );
}
```

---

## 6. B-02 fix — atomic idempotency (strategy decided, not optional)

**Strategy:** `TemporalActApplicationService` becomes the transactional orchestrator.
It receives a `TransactionTemplate` and opens ONE transaction per request that encloses
both the domain side effect and the idempotency record insert.
`TemporalActService.createSignalTemporalAct` and `cancelTemporalAct` must NOT start their own
transactions when called from within `TemporalActApplicationService`.

### 6.1 New methods to add to TemporalActService

Add two **public transaction-participating methods** that do NOT use `txTemplate` — they execute
the domain logic without starting a transaction, so the caller's transaction encloses them.
They cannot be package-private because `TemporalActApplicationService` is in
`com.sovereign.connect.core.temporal.application` while `TemporalActService` is in
`com.sovereign.connect.core.temporal.service`; Java subpackages are different packages.
They SHOULD assert an active Spring transaction via `TransactionSynchronizationManager.isActualTransactionActive()`
and throw `IllegalStateException` if called outside a transaction:

```java
// Public transaction-participating: no txTemplate.execute — participates in caller's transaction
public TemporalAct createSignalTemporalActInExistingTransaction(
    String habitatId,
    SignalTemporalPayload payload,
    Instant dueAt,
    String notificationTargetRef,
    CreatedByRef createdByRef,
    Instant requestedAt            // from request, stored in requested_at_ms if schema supports
) {
    String temporalActId = UUID.randomUUID().toString();
    Instant now = Instant.now(clock);
    TemporalAct act = new TemporalAct(temporalActId, habitatId, TemporalActStatus.PENDING,
        dueAt, payload, notificationTargetRef, createdByRef, null, now, now, null, null, null);
    writePort.insertCreated(act);
    ledgerPort.appendLedgerEntry(createdLedgerEntry(act, now));
    return act;
}

// Public transaction-participating: no txTemplate.execute — participates in caller's transaction
public int cancelTemporalActInExistingTransaction(String habitatId, String temporalActId, Instant now) {
    int updated = writePort.cancelIfNonTerminal(habitatId, temporalActId, now);
    if (updated == 1) {
        ledgerPort.appendLedgerEntry(cancelledLedgerEntry(habitatId, temporalActId, now));
    }
    return updated;
}
```

Add helper and import:

```java
private void assertActiveTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
        throw new IllegalStateException("TemporalActService transaction-participating method called without active transaction");
    }
}
```

```java
import org.springframework.transaction.support.TransactionSynchronizationManager;
```

Each transaction-participating method MUST call `assertActiveTransaction()` before writing.

The existing `createSignalTemporalAct(...)` and `cancelTemporalAct(...)` public methods MUST
be preserved for backward compatibility — they wrap the new transaction-participating methods in `txTemplate`:

```java
public String createSignalTemporalAct(
    String habitatId, SignalTemporalPayload payload, Instant dueAt,
    String notificationTargetRef, CreatedByRef createdByRef
) {
    String[] idRef = new String[1];
    txTemplate.executeWithoutResult(status -> {
        TemporalAct act = createSignalTemporalActInExistingTransaction(
            habitatId, payload, dueAt, notificationTargetRef, createdByRef, null
        );
        idRef[0] = act.temporalActId();
    });
    return idRef[0];
}
```

**The 26 existing seed tests call the public API via `actService.createSignalTemporalAct(...)`.
They must continue to pass without modification.**

### 6.2 Updated TemporalActApplicationService constructor

Add `TransactionTemplate txTemplate` as the 6th constructor parameter:

```java
public TemporalActApplicationService(
    TemporalActService actService,
    TemporalActObservationPort observationPort,
    TemporalRequestIdempotencyPort idempotencyPort,
    TemporalEngineHealth health,
    ObjectMapper objectMapper,
    TransactionTemplate txTemplate        // ← add this
)
```

Update `TemporalEngineConfiguration.temporalActApplicationPort(...)` to inject `transactionTemplate`.

### 6.3 Atomic create flow in TemporalActApplicationService

Replace the non-atomic try block with:

```java
// Inside createSignalTemporalAct, after validation and readiness check:
byte[] fingerprint = createFingerprint(request);

return txTemplate.execute(txStatus -> {
    // Step 1: read-or-claim idempotency row (inside transaction)
    Optional<TemporalRequestIdempotencyRecord> existing =
        idempotencyPort.find(request.habitatId(), request.idempotencyKey(), CREATE_SIGNAL);

    if (existing.isPresent()) {
        if (!existing.get().sameFingerprint(fingerprint)) {
            return new CreateSignalTemporalActResult.Rejected(rejection(
                TemporalRequestRejectionCode.IDEMPOTENCY_CONFLICT,
                "idempotency key conflicts with a different create request"
            ));
        }
        // Same fingerprint — replay
        return observationPort.findById(request.habitatId(), existing.get().temporalActId())
            .<CreateSignalTemporalActResult>map(CreateSignalTemporalActResult.IdempotentReplay::new)
            .orElseGet(() -> new CreateSignalTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.STORAGE_UNAVAILABLE,
                "idempotency record references a missing temporal act"
            )));
    }

    // Step 2: create act + ledger IN THIS transaction
    TemporalAct act = actService.createSignalTemporalActInExistingTransaction(
        request.habitatId(),
        new SignalTemporalPayload(request.label(), request.signalKind()),
        request.dueAt(), request.notificationTargetRef(),
        new CreatedByRef(request.createdByRef()),
        request.requestedAt()
    );

    // Step 3: build observation (reads from same transaction — use act directly)
    TemporalActObservation observation = toObservationDirect(act);

    // Step 4: insert idempotency record IN THIS transaction
    idempotencyPort.insert(new TemporalRequestIdempotencyRecord(
        request.habitatId(), request.idempotencyKey(), CREATE_SIGNAL,
        fingerprint, act.temporalActId(), "ACCEPTED", null, writeJson(observation)
    ), request.requestedAt());

    // Step 5: commit — act + ledger + idempotency record commit atomically
    return new CreateSignalTemporalActResult.Accepted(observation);
});
// If idempotencyPort.insert throws DataIntegrityViolationException (concurrent race):
// txTemplate wraps this in a runtime exception -> transaction rolls back act + ledger
// The caller should catch and re-read the idempotency row to determine replay/conflict.
```

**Note on concurrent race handling:** If `idempotencyPort.insert` throws on unique constraint
(concurrent request with same key), the `TransactionTemplate` will propagate the exception and
roll back the entire transaction (act + ledger + idempotency). The outer catch in
`createSignalTemporalAct` should detect `DataIntegrityViolationException` and re-read the
idempotency row to return `IdempotentReplay` or `Rejected(IDEMPOTENCY_CONFLICT)`:

```java
} catch (org.springframework.dao.DataIntegrityViolationException race) {
    // Race on unique idempotency key — re-read outside a transaction
    Optional<TemporalRequestIdempotencyRecord> winner =
        idempotencyPort.find(request.habitatId(), request.idempotencyKey(), CREATE_SIGNAL);
    if (winner.isPresent() && winner.get().sameFingerprint(fingerprint)) {
        return observationPort.findById(request.habitatId(), winner.get().temporalActId())
            .<CreateSignalTemporalActResult>map(CreateSignalTemporalActResult.IdempotentReplay::new)
            .orElseGet(() -> new CreateSignalTemporalActResult.Failed(...));
    }
    return new CreateSignalTemporalActResult.Rejected(rejection(
        TemporalRequestRejectionCode.IDEMPOTENCY_CONFLICT,
        "concurrent conflict on idempotency key"
    ));
}
```

### 6.4 toObservationDirect — avoid N+1 read after in-transaction create

Add a private helper that builds `TemporalActObservation` directly from a `TemporalAct` object
without a round-trip to the DB (only safe when the payload is known to be `SignalTemporalPayload`):

```java
private TemporalActObservation toObservationDirect(TemporalAct act) {
    if (act.payload() instanceof SignalTemporalPayload p) {
        return new TemporalActObservation(
            act.temporalActId(), act.habitatId(), act.status(), act.dueAt(),
            "SIGNAL", p.label(), p.signalKind(),
            act.notificationTargetRef(), act.createdByRef().value(),
            act.createdAt(), act.updatedAt(), null, null, null
        );
    }
    throw new IllegalStateException("createSignalTemporalActInTx returned non-signal payload");
}
```

This replaces the `observationPort.findById(...)` call that previously did a DB read after
creation — avoiding a potential phantom-read edge case while inside the transaction.

### 6.5 Atomic cancel flow

Important: `temporal_request_idempotency.result_kind` uses schema-supported coarse values only (`ACCEPTED`, `IDEMPOTENT_REPLAY`, `REJECTED`, `FAILED`). Use `result_code` for the precise semantic outcome.

Mandatory result encoding:

```text
First-time cancelled:
  result_kind = ACCEPTED
  result_code = CANCELLED

First-time already terminal:
  result_kind = ACCEPTED
  result_code = ALREADY_TERMINAL

First-time not found:
  result_kind = ACCEPTED
  result_code = NOT_FOUND

Internal inconsistency / runtime failure:
  result_kind = FAILED
  result_code = CANCEL_STATUS_INCONSISTENT or concrete failure code

Replay of any previous cancel result:
  return IdempotentReplay when an observation exists;
  return NotFound when the stored prior result_code is NOT_FOUND;
  do not recompute the side effect.
```

Do **not** insert the idempotency record before the final result classification is known. The previous pseudocode inserted `ALREADY_TERMINAL` before proving the observed status; that is forbidden because it can persist a result different from the result returned to the caller.

```java
return txTemplate.execute(txStatus -> {
    byte[] fingerprint = cancelFingerprint(request);

    Optional<TemporalRequestIdempotencyRecord> existing =
        idempotencyPort.find(request.habitatId(), request.idempotencyKey(), CANCEL);
    if (existing.isPresent()) {
        if (!existing.get().sameFingerprint(fingerprint)) {
            return new CancelTemporalActResult.Rejected(rejection(
                TemporalRequestRejectionCode.IDEMPOTENCY_CONFLICT, "..."));
        }
        return replayCancel(existing.get());
    }

    Optional<TemporalAct> beforeOpt = actService.findById(
        request.habitatId(), request.temporalActId()
    );

    if (beforeOpt.isEmpty()) {
        CancelTemporalActResult result = new CancelTemporalActResult.NotFound(
            request.habitatId(), request.temporalActId()
        );
        idempotencyPort.insert(new TemporalRequestIdempotencyRecord(
            request.habitatId(), request.idempotencyKey(), CANCEL,
            fingerprint,
            request.temporalActId(),
            "ACCEPTED",
            "NOT_FOUND",
            writeJson(result)
        ), request.requestedAt());
        return result;
    }

    int updated = actService.cancelTemporalActInExistingTransaction(
        request.habitatId(), request.temporalActId(), request.requestedAt()
    );

    Optional<TemporalAct> afterOpt = actService.findById(
        request.habitatId(), request.temporalActId()
    );
    if (afterOpt.isEmpty()) {
        CancelTemporalActResult result = new CancelTemporalActResult.Failed(
            new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.INTERNAL_FAILURE,
                "cancel result disappeared after attempted transition"));
        idempotencyPort.insert(new TemporalRequestIdempotencyRecord(
            request.habitatId(), request.idempotencyKey(), CANCEL,
            fingerprint,
            request.temporalActId(),
            "FAILED",
            "CANCEL_RESULT_DISAPPEARED",
            writeJson(result)
        ), request.requestedAt());
        return result;
    }

    TemporalAct afterAct = afterOpt.get();
    TemporalActObservation after = toObservationSafe(afterAct);

    CancelTemporalActResult result;
    String resultKind;
    String resultCode;

    if (updated == 1) {
        result = new CancelTemporalActResult.Cancelled(after);
        resultKind = "ACCEPTED";
        resultCode = "CANCELLED";
    } else if (afterAct.status() != TemporalActStatus.PENDING
            && afterAct.status() != TemporalActStatus.ARMED) {
        result = new CancelTemporalActResult.AlreadyTerminal(after);
        resultKind = "ACCEPTED";
        resultCode = "ALREADY_TERMINAL";
    } else {
        result = new CancelTemporalActResult.Failed(new TemporalRuntimeFailure(
            TemporalRuntimeFailureCode.INTERNAL_FAILURE,
            "cancel did not update non-terminal act"));
        resultKind = "FAILED";
        resultCode = "CANCEL_STATUS_INCONSISTENT";
    }

    idempotencyPort.insert(new TemporalRequestIdempotencyRecord(
        request.habitatId(), request.idempotencyKey(), CANCEL,
        fingerprint,
        request.temporalActId(),
        resultKind,
        resultCode,
        writeJson(result)
    ), request.requestedAt());

    return result;
});
```

`replayCancel(...)` MUST use the stored `result_code`, not the current mutable world, to reconstruct the stable replay result. Minimum behavior:

```text
result_code = CANCELLED:
  return IdempotentReplay(current observation if available; otherwise Failed/diagnostic according to implementation policy)

result_code = ALREADY_TERMINAL:
  return IdempotentReplay(current observation if available; otherwise Failed/diagnostic according to implementation policy)

result_code = NOT_FOUND:
  return NotFound(habitatId, temporalActId)

stored result_kind = FAILED:
  return Failed(stored/runtime failure reconstructed from result_json or result_code)
```

The implementation MAY simplify replay reconstruction if the existing `TemporalRequestIdempotencyRecord` already stores a full `result_json`, but it MUST preserve stable semantics for `NOT_FOUND`, `ALREADY_TERMINAL`, and `FAILED`.

---

## 7. B-01 fix — recovery completion loop

**Add to TemporalEngineProperties:**

```java
int maxRecoveryBatchesPerStartup   // default: 50 in application.yml
```

**Rewrite TemporalEngineLifecycle.start() recovery section:**

```java
health.transitionTo(TemporalEngineStatus.RECOVERING);
int batchesDone = 0;
int maxBatches = properties.maxRecoveryBatchesPerStartup(); // default 50
while (true) {
    TemporalRecoveryResult result = engineService.classifyMisfires(
        properties.habitatId(), Instant.now(clock), properties.maxRecoveryBatchSize()
    );
    health.incrementMisfired(result.misfiredCount());
    health.incrementFailed(result.failedCount());
    batchesDone++;

    if (result.complete()) {
        break;   // all overdue acts classified — safe to proceed
    }

    // Safety: if batch reported incomplete but zero progress, fail closed
    if (result.misfiredCount() == 0 && result.failedCount() == 0) {
        health.transitionTo(TemporalEngineStatus.FAILED);
        throw new IllegalStateException(
            "Recovery stalled: incomplete batch made zero progress after " + batchesDone + " batches");
    }

    if (batchesDone >= maxBatches) {
        health.transitionTo(TemporalEngineStatus.FAILED);
        throw new IllegalStateException(
            "Recovery exceeded maxRecoveryBatchesPerStartup=" + maxBatches
            + " — engine fails closed to protect misfire classification guarantee");
    }
}
// Only after complete() == true: proceed to lock acquisition and runner.start()
```

**Add to application.yml:**

```yaml
sovereign:
  temporal:
    engine:
      max-recovery-batches-per-startup: 50
```

---

## 8. B-03 fix — unknown payload ledger + observation safety

### 8.1 Update failUnknownPayload in TemporalEngineService

Current (no ledger):

```java
private void failUnknownPayload(String habitatId, String temporalActId, Instant now) {
    txTemplate.executeWithoutResult(status ->
        writePort.markFailed(habitatId, temporalActId, "unknown payload kind", now)
    );
}
```

Required (add ledger entry in same transaction):

```java
private void failUnknownPayload(String habitatId, String temporalActId, Instant now) {
    txTemplate.executeWithoutResult(status -> {
        writePort.markFailed(habitatId, temporalActId, "UNKNOWN_PAYLOAD_KIND", now);
        ledgerPort.appendLedgerEntry(new LedgerEntry(
            UUID.randomUUID(),
            habitatId,
            LedgerRecordClass.LEDGER_ONLY,
            "TEMPORAL_ACT",
            temporalActId,
            SemanticKind.TEMPORAL_ACT_FAILED,
            "TemporalActFailed",
            writeJson(Map.of(
                "temporalActId", temporalActId,
                "habitatId", habitatId,
                "failedAt", now.toString(),
                "reason", "UNKNOWN_PAYLOAD_KIND"
            )),
            "temporal-act-failed:" + temporalActId + ":unknown-payload",  // idempotency key
            now,
            null
        ));
    });
}
```

`SemanticKind.TEMPORAL_ACT_FAILED` already exists in the enum. `LedgerRecordClass.LEDGER_ONLY`
matches the pattern used by `misfiredLedgerEntry`.

### 8.2 Update toObservation in TemporalActObservationService

Replace the unsafe cast:

```java
private TemporalActObservation toObservation(TemporalAct act) {
    if (act.payload() instanceof SignalTemporalPayload p) {
        return new TemporalActObservation(
            act.temporalActId(), act.habitatId(), act.status(), act.dueAt(),
            "SIGNAL", p.label(), p.signalKind(),
            act.notificationTargetRef(), act.createdByRef().value(),
            act.createdAt(), act.updatedAt(), act.firedAt(), act.terminalAt(), act.terminalReason()
        );
    }
    // null payload = quarantined/FAILED act — still observable, no NPE
    return new TemporalActObservation(
        act.temporalActId(), act.habitatId(), act.status(), act.dueAt(),
        "UNKNOWN", null, null,
        act.notificationTargetRef(),
        act.createdByRef() != null ? act.createdByRef().value() : null,
        act.createdAt(), act.updatedAt(), act.firedAt(), act.terminalAt(), act.terminalReason()
    );
}
```

Add `toObservationSafe` alias in `TemporalActApplicationService` that calls the port's
`findById` and handles null payload acts safely — or reference the same pattern above.

---

## 9. B-04 fix — heartbeat signature, SQL, and lock-loss

### 9.1 Update TemporalEngineLockPort

```java
public interface TemporalEngineLockPort {
    boolean tryAcquire(String habitatId, String storagePartitionRef,
                       String engineInstanceId, Instant now, long ttlMs);

    // Add ttlMs parameter and return affected rows for lock-loss detection:
    int heartbeat(String habitatId, String storagePartitionRef,
                  String engineInstanceId, Instant now, long ttlMs);

    void release(String habitatId, String storagePartitionRef,
                 String engineInstanceId, Instant now);

    boolean isHeld(String habitatId, String storagePartitionRef, Instant now);
}
```

### 9.2 Update SQLiteTemporalEngineLockRepository.heartbeat

```java
@Override
public int heartbeat(String habitatId, String storagePartitionRef,
                     String engineInstanceId, Instant now, long ttlMs) {
    return jdbcTemplate.update("""
        UPDATE temporal_engine_locks
        SET heartbeat_at_ms = ?,
            expires_at_ms   = ?
        WHERE habitat_id = ?
          AND storage_partition_ref = ?
          AND engine_instance_id = ?
          AND status = 'ACTIVE'
        """,
        now.toEpochMilli(),
        now.toEpochMilli() + ttlMs,   // ← correct: now + ttl (not delta)
        habitatId,
        storagePartitionRef,
        engineInstanceId
    );
}
```

Return `int` (affected rows) instead of `void` so callers can detect lock loss.
Update the port interface accordingly.

### 9.3 Update TemporalEngineLifecycle.startHeartbeat for lock-loss handling

```java
private void startHeartbeat() {
    if (!heartbeatRunning.compareAndSet(false, true)) return;
    heartbeatThread = Thread.ofVirtual()
        .name("temporal-engine-lock-heartbeat")
        .start(() -> {
            long intervalMs = Math.max(1L, properties.singleNodeGuardTtlMs() / 3L);
            while (heartbeatRunning.get()) {
                int affected = lockRepository.heartbeat(
                    properties.habitatId(),
                    STORAGE_PARTITION_REF,
                    engineInstanceId,
                    Instant.now(clock),
                    properties.singleNodeGuardTtlMs()   // ← pass ttlMs
                );
                if (affected != 1) {
                    // Lock lost — stop engine
                    health.transitionTo(TemporalEngineStatus.FAILED);
                    // Safe from heartbeat thread: runner.stop() only flips AtomicBoolean
                    // and unparks the runner; it does not join/await.
                    runner.stop();
                    heartbeatRunning.set(false);
                    return;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(intervalMs));
            }
        });
}
```

**Why `runner.stop()` is safe here:** `stop()` sets `running.set(false)` and calls
`LockSupport.unpark(runnerThread)`. It does NOT join or await. The heartbeat virtual thread
continues to its `return` without blocking.

---

## 10. H-01 fix — DEGRADED auto-recovery (one line)

In `TemporalEngineHealth.recordPollSuccess()`:

```java
public void recordPollSuccess() {
    Instant now = Instant.now();
    lastPollAt = now;
    lastSuccessfulPollAt = now;
    lastFailure = null;
    if (status == TemporalEngineStatus.DEGRADED) {   // ← add this
        status = TemporalEngineStatus.RUNNING;
    }
}
```

---

## 11. H-03 fix — precise runtime failure mapping

Replace `notReady()` in `TemporalActApplicationService` with `toRuntimeFailure(TemporalEngineStatus)`:

```java
private TemporalRuntimeFailure toRuntimeFailure(TemporalEngineStatus status) {
    return switch (status) {
        case DISABLED  -> new TemporalRuntimeFailure(
            TemporalRuntimeFailureCode.TEMPORAL_ENGINE_DISABLED, "engine is disabled");
        case RECOVERING -> new TemporalRuntimeFailure(
            TemporalRuntimeFailureCode.RECOVERY_NOT_COMPLETED, "engine is still recovering");
        case STARTING  -> new TemporalRuntimeFailure(
            TemporalRuntimeFailureCode.TEMPORAL_ENGINE_NOT_READY, "engine is starting");
        case STOPPED   -> new TemporalRuntimeFailure(
            TemporalRuntimeFailureCode.TEMPORAL_ENGINE_NOT_READY, "engine is stopped");
        case FAILED    -> new TemporalRuntimeFailure(
            TemporalRuntimeFailureCode.INTERNAL_FAILURE, "engine has failed");
        default        -> new TemporalRuntimeFailure(
            TemporalRuntimeFailureCode.TEMPORAL_ENGINE_NOT_READY, "engine not ready: " + status);
    };
}
```

Replace the `notReady()` call sites:

```java
if (!health.isReady()) {
    return new CreateSignalTemporalActResult.Failed(toRuntimeFailure(health.status()));
}
```

`TemporalEngineHealth` must expose `status()` (it already does).

---

## 12. H-04 fix — health counter increments

Health counters are declared but not incremented at fire/cancel/skip transitions.
Add calls in `TemporalEngineService`:

```java
// In fireDueTemporalActOnce — after confirmed fire (updated == 1):
if (health != null) health.incrementFired();

// In pollDueOnce — when update count == 0 (lost race / already terminal):
if (health != null) health.incrementSkipped();

// In TemporalActApplicationService after successful cancel result is committed:
health.incrementCancelled();

// After failUnknownPayload:
if (health != null) health.incrementFailed(1);
```

`TemporalEngineService` currently has no reference to `TemporalEngineHealth`. Two options:

**Option A (preferred):** Pass `TemporalEngineHealth` as an optional constructor parameter
(nullable — backwards compatible with the 7-arg constructor used in tests):

```java
// 8-arg constructor — health parameter added
public TemporalEngineService(
    TemporalActWritePort writePort, TemporalActReadPort readPort,
    ScLedgerWritePort ledgerPort, ScOutboxWritePort outboxPort,
    TransactionTemplate txTemplate, ObjectMapper objectMapper,
    Clock clock, TemporalRecoveryObservationPort recoveryObservationPort,
    @Nullable TemporalEngineHealth health   // nullable for seed test compatibility
)
```

**The existing 7-arg convenience constructor already delegates to the 8-arg one with
`TemporalRecoveryObservationPort.noOp()`** — add a 9-arg version or extend the 8-arg.

Update `TemporalEngineConfiguration.temporalEngineService(...)` to pass `health`.

---

## 13. H-05 — BLOB ID binding decision

The schema declares `temporal_act_id BLOB NOT NULL` but adapters currently bind `String`
via `jdbcTemplate.update(..., act.temporalActId(), ...)`. SQLite accepts this via dynamic
typing but it does not match the declared schema.

**Decision for this patch:** Option C — document as accepted technical debt for this patch;
do NOT silently change schema to TEXT and do NOT attempt a partial UUID<->byte[16] conversion.

Add a note in the implementation report:

```text
H-05 / Option C: temporal_act_id is currently bound as String (UUID text) into BLOB columns.
SQLite stores it as TEXT due to dynamic typing. The intended production representation
is BLOB(16). A follow-on patch must either implement UUID<->byte[16] conversion in SQLite
adapters or produce a SDD-SCHEMA-001 patch changing the column type to TEXT.
This patch does not resolve physical ID representation; it records the debt explicitly.
```

Do NOT change schema or adapter code for H-05 in this patch.

---


## 14. Required test scenarios — adapt using existing test infrastructure

The following tests must be placed in new test classes (not in `TemporalActSeedTest`).
They use the same infrastructure pattern as the existing seed tests. Key helpers:

**Existing helpers available in TemporalActSeedTest (copy their pattern):**

```java
// jdbcUrl(name)  → "jdbc:h2:file:{tempDir}/{name};DB_CLOSE_DELAY=0"
// dataSource(url) → new DriverManagerDataSource(url, "sa", "")
// fixture(url)   → H2TemporalActRepository + H2BaseTopologyRepository + TemporalActService + TemporalEngineService
// ledgerCount(jdbcUrl, habitatId, idempotencyKey) → COUNT(*) from sc_c_ledger_entries
// createDueAct(fixture, habitatId) → actService.createSignalTemporalAct(..., dueAt=2026-05-18T12:00:00Z, ...)
// signalPayload()  → new SignalTemporalPayload("Wake up", "alarm")
// createdBy()      → new CreatedByRef("creator:test")
```

**H2 schema uses `payload_type` (not `payload_kind`) for the column name.**

**New helper needed — noOpLockPort() — a lock port that always grants:**

```java
private TemporalEngineLockPort noOpLockPort() {
    return new TemporalEngineLockPort() {
        public boolean tryAcquire(String h, String p, String id, Instant now, long ttl) { return true; }
        public int heartbeat(String h, String p, String id, Instant now, long ttl) { return 1; }
        public void release(String h, String p, String id, Instant now) {}
        public boolean isHeld(String h, String p, Instant now) { return true; }
    };
}
```

**New helper needed — testProperties(batchSize, maxBatches) — creates TemporalEngineProperties for tests:**

```java
private TemporalEngineProperties testProperties(int batchSize, int maxBatches) {
    return new TemporalEngineProperties(
        true,           // enabled
        "habitat-001",  // habitatId
        5_000L,         // pollingIntervalMs (slow — won't fire during test)
        10,             // maxDueActsPerCycle
        batchSize,      // maxRecoveryBatchSize ← key parameter
        30,             // terminalRetentionDays
        500,            // maxTerminalResults
        500,            // maxMisfiredResults
        false,          // singleNodeGuardEnabled ← disabled so noOpLockPort not exercised
        30_000L,        // singleNodeGuardTtlMs
        1_000L,         // shutdownTimeoutMs
        5_000L,         // failureBackoffMs
        5,              // maxConsecutiveFailures
        maxBatches      // maxRecoveryBatchesPerStartup ← key parameter (new field after patch)
    );
}
```

Note: `TemporalEngineProperties` gains `maxRecoveryBatchesPerStartup` in Phase 5 of this patch.
This helper should be written after that field is added.

---

### T-1: recoveryDoesNotStartPollingWhenMisfireBatchIncomplete

Place in `TemporalEngineRecoveryTest` (new class). Uses H2 fixtures. Uses `createDueAct` pattern
but with `Instant.now(clock).minusSeconds(120)` as `dueAt` so acts are overdue.

```java
@ExtendWith(TempDirectory.class)     // or use @TempDir field
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemporalEngineRecoveryTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-18T12:05:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void recoveryDoesNotStartPollingWhenMisfireBatchIncomplete() {
        // Arrange: 3 acts overdue; batchSize=1 means each classifyMisfires call handles 1 act
        String jdbcUrl = "jdbc:h2:file:" + tempDir.resolve("recovery-incomplete").toAbsolutePath()
            .toString().replace('\\', '/') + ";DB_CLOSE_DELAY=0";
        DataSource dataSource = new DriverManagerDataSource(jdbcUrl, "sa", "");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        H2TemporalActRepository repository = new H2TemporalActRepository(dataSource, mapper, clock);
        H2BaseTopologyRepository ledgerOutbox = new H2BaseTopologyRepository(dataSource, mapper, clock);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        TemporalActService actService = new TemporalActService(
            repository, repository, ledgerOutbox, tx, mapper, clock);
        TemporalEngineService engine = new TemporalEngineService(
            repository, repository, ledgerOutbox, ledgerOutbox, tx, mapper, clock,
            TemporalRecoveryObservationPort.noOp());

        // Create 3 acts with dueAt in the past
        Instant overdueDueAt = Instant.parse("2026-05-18T11:00:00Z");   // < clock fixed time
        for (int i = 0; i < 3; i++) {
            actService.createSignalTemporalAct(
                "habitat-001",
                new SignalTemporalPayload("Wake up", "alarm"),
                overdueDueAt,
                "surface:test",
                new CreatedByRef("creator:test")
            );
        }

        // batchSize=1, maxBatches=50 → lifecycle loops 3 times until complete() == true
        TemporalEngineProperties props = testProperties(1, 50);
        // Use slow runner so polling does not fire acts between recovery batches
        TemporalEngineRunner runner = new TemporalEngineRunner(engine, "habitat-001", clock, 30_000L);
        TemporalEngineHealth health = new TemporalEngineHealth();

        TemporalEngineLifecycle lifecycle = new TemporalEngineLifecycle(
            engine, runner, props, health, noOpLockPort(), clock);
        lifecycle.start();
        lifecycle.stop();

        // Assert: ALL 3 acts are MISFIRED (not FIRED, not PENDING)
        List<TemporalAct> active = repository.listActive("habitat-001");
        assertThat(active).isEmpty();

        List<TemporalAct> terminal = repository.listTerminal("habitat-001", 10);
        assertThat(terminal).hasSize(3);
        assertThat(terminal).allMatch(a -> a.status() == TemporalActStatus.MISFIRED);

        // Assert: no FIRED ledger entries exist
        int firedLedgerCount = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM sc_c_ledger_entries WHERE semantic_kind = 'TIMER_FIRED'",
            Integer.class
        );
        assertThat(firedLedgerCount).isZero();
    }
}
```

---

### T-2: incompleteRecoveryBatchDoesNotFireOverdueActs

```java
@Test
void incompleteRecoveryBatchDoesNotFireOverdueActs() {
    // Same pattern as T-1 but with batchSize=1 and only 2 acts.
    // Key: if the loop completes (both acts misfired), none should be FIRED.
    // This test proves that recovery completes and the runner starts without
    // firing any of the originally-overdue acts.

    // [setup same as T-1 but 2 acts, batchSize=1]

    // After lifecycle.start() + lifecycle.stop():
    // All acts MISFIRED
    // No TIMER_FIRED ledger entries
    // No TIMER_FIRED outbox entries
    int firedOutboxCount = new JdbcTemplate(dataSource).queryForObject(
        "SELECT COUNT(*) FROM sc_c_outbox_entries WHERE outbound_kind = 'TIMER_FIRED_SIGNAL'",
        Integer.class
    );
    assertThat(firedOutboxCount).isZero();
}
```

---

### T-3: heartbeatExtendsLockToNowPlusTtl

Place in `TemporalEngineLockTest` (new class). Uses SQLite fixtures from `TemporalEngineIndustrialBoundaryTest`.

```java
class TemporalEngineLockTest {

    // Reuse sqliteDataSource helper from TemporalEngineIndustrialBoundaryTest pattern:
    private DataSource sqliteDataSource(String path) {
        SQLiteDataSource delegate = new SQLiteDataSource();
        delegate.setUrl("jdbc:sqlite:" + path);
        return new PerConnectionPragmaDataSource(delegate);
    }

    private void flywayMigrate(DataSource ds) {
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @Test
    void heartbeatExtendsLockToNowPlusTtl(@TempDir Path tmp) {
        DataSource ds = sqliteDataSource(tmp.resolve("lock-ttl.sqlite").toString());
        flywayMigrate(ds);
        SQLiteTemporalEngineLockRepository repo = new SQLiteTemporalEngineLockRepository(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        String habitatId = "habitat-001";
        String partition = "sqlite.default";
        String instance  = "inst-1";
        long ttl = 30_000L;

        // Use fixed epoch millis for precise assertion
        Instant t0 = Instant.ofEpochMilli(1_000_000L);
        repo.tryAcquire(habitatId, partition, instance, t0, ttl);
        // After acquire: expires_at_ms = 1_000_000 + 30_000 = 1_030_000

        // Heartbeat at t1 = t0 + 10s
        Instant t1 = Instant.ofEpochMilli(1_010_000L);
        int affected = repo.heartbeat(habitatId, partition, instance, t1, ttl);
        assertThat(affected).isEqualTo(1);

        Long expiresAt = jdbc.queryForObject(
            "SELECT expires_at_ms FROM temporal_engine_locks WHERE habitat_id=? AND storage_partition_ref=?",
            Long.class, habitatId, partition
        );
        // Correct: t1 + ttl = 1_010_000 + 30_000 = 1_040_000
        assertThat(expiresAt).isEqualTo(1_040_000L);
        // Bug value would be: 1_030_000 + (1_030_000 - 1_000_000) = 1_060_000
        assertThat(expiresAt).isNotEqualTo(1_060_000L);
    }
}
```

---

### T-4: heartbeatDoesNotGrowExpiryExponentially

```java
@Test
void heartbeatDoesNotGrowExpiryExponentially(@TempDir Path tmp) {
    DataSource ds = sqliteDataSource(tmp.resolve("lock-exp.sqlite").toString());
    flywayMigrate(ds);
    SQLiteTemporalEngineLockRepository repo = new SQLiteTemporalEngineLockRepository(ds);
    JdbcTemplate jdbc = new JdbcTemplate(ds);

    String habitatId = "habitat-001"; String partition = "sqlite.default"; String instance = "inst-1";
    long ttl = 30_000L;
    long intervalMs = ttl / 3;   // = 10_000

    Instant t0 = Instant.ofEpochMilli(1_000_000L);
    repo.tryAcquire(habitatId, partition, instance, t0, ttl);

    // 5 heartbeats at ttl/3 intervals
    for (int i = 1; i <= 5; i++) {
        Instant ti = Instant.ofEpochMilli(1_000_000L + (long) i * intervalMs);
        int affected = repo.heartbeat(habitatId, partition, instance, ti, ttl);
        assertThat(affected).as("heartbeat %d must affect 1 row", i).isEqualTo(1);

        Long expiresAt = jdbc.queryForObject(
            "SELECT expires_at_ms FROM temporal_engine_locks WHERE habitat_id=? AND storage_partition_ref=?",
            Long.class, habitatId, partition
        );
        long expected = ti.toEpochMilli() + ttl;
        assertThat(expiresAt)
            .as("After heartbeat %d: expires must be t%d + ttl = %d", i, i, expected)
            .isEqualTo(expected);
        // With the bug: after heartbeat 5, expected would be ~1_960_000 instead of 1_050_000
    }
    // Final expires = 1_050_000 (t5=1_050_000 + ttl=30_000)
    // Bug final: would be 1_030_000 + exponentially growing delta ≈ 1_960_000
}
```

---

### T-5: heartbeatLossStopsRunnerOrMarksFailed

```java
@Test
void heartbeatLossStopsRunnerOrMarksFailed() throws InterruptedException {
    // heartbeat port that returns 1 for the first call, 0 for all subsequent calls
    AtomicInteger callCount = new AtomicInteger(0);
    TemporalEngineLockPort losingLock = new TemporalEngineLockPort() {
        public boolean tryAcquire(String h, String p, String id, Instant now, long ttl) { return true; }
        public int heartbeat(String h, String p, String id, Instant now, long ttl) {
            return callCount.incrementAndGet() == 1 ? 1 : 0;   // first call ok, rest fail
        }
        public void release(String h, String p, String id, Instant now) {}
        public boolean isHeld(String h, String p, Instant now) { return false; }
    };

    // Minimal engine and runner that never actually poll (very long interval)
    // We only test the heartbeat path, not the polling path
    String jdbcUrl = "jdbc:h2:mem:heartbeat-loss-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    DataSource ds = new DriverManagerDataSource(jdbcUrl, "sa", "");
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    H2TemporalActRepository repo = new H2TemporalActRepository(ds, mapper, Clock.systemUTC());
    H2BaseTopologyRepository ledger = new H2BaseTopologyRepository(ds, mapper, Clock.systemUTC());
    TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    TemporalEngineService engine = new TemporalEngineService(
        repo, repo, ledger, ledger, tx, mapper, Clock.systemUTC(), TemporalRecoveryObservationPort.noOp());

    TemporalEngineHealth health = new TemporalEngineHealth();
    // Use very short ttl so heartbeat fires quickly; very long polling so poll never fires
    TemporalEngineProperties props = new TemporalEngineProperties(
        true, "habitat-001", 60_000L, 10, 200, 30, 500, 500,
        true, 300L,   // singleNodeGuardTtlMs = 300ms → heartbeat interval = 100ms
        5_000L, 5_000L, 5, 50
    );
    TemporalEngineRunner runner = new TemporalEngineRunner(
        engine, "habitat-001", Clock.systemUTC(), 60_000L);

    TemporalEngineLifecycle lifecycle = new TemporalEngineLifecycle(
        engine, runner, props, health, losingLock, Clock.systemUTC());
    lifecycle.start();

    // Wait long enough for the second heartbeat to fire and detect lock loss (300ms ttl/3 = 100ms)
    Thread.sleep(600L);

    // After lock loss: health must be FAILED and runner must have stopped
    assertThat(health.status()).isEqualTo(TemporalEngineStatus.FAILED);
    assertThat(runner.isRunning()).isFalse();

    // Cleanup (stop may be called again safely)
    lifecycle.stop();
}
```

---

### T-6: secondLifecycleFailsFastWhenLockHeld

```java
@Test
void secondLifecycleFailsFastWhenLockHeld(@TempDir Path tmp) {
    DataSource ds = sqliteDataSource(tmp.resolve("second-lifecycle.sqlite").toString());
    flywayMigrate(ds);
    SQLiteTemporalEngineLockRepository lockRepo = new SQLiteTemporalEngineLockRepository(ds);

    // First instance acquires the lock
    Instant now = Instant.now();
    boolean first = lockRepo.tryAcquire("habitat-001", "sqlite.default", "inst-A", now, 30_000L);
    assertThat(first).isTrue();

    // Second instance must be denied while lock is active and not expired
    boolean second = lockRepo.tryAcquire("habitat-001", "sqlite.default", "inst-B", now, 30_000L);
    assertThat(second).isFalse();

    // After release, second instance can acquire
    lockRepo.release("habitat-001", "sqlite.default", "inst-A", now);
    boolean third = lockRepo.tryAcquire("habitat-001", "sqlite.default", "inst-C",
        now.plusMillis(1), 30_000L);
    assertThat(third).isTrue();
}
```

---

### T-7: successfulPollAfterFailureRestoresRunningStatus

```java
class TemporalEngineHealthTest {

    @Test
    void successfulPollAfterFailureRestoresRunningStatus() {
        TemporalEngineHealth health = new TemporalEngineHealth();
        health.transitionTo(TemporalEngineStatus.RUNNING);

        health.recordPollFailure(new RuntimeException("db unavailable"));
        assertThat(health.status()).isEqualTo(TemporalEngineStatus.DEGRADED);
        assertThat(health.lastFailure()).isNotNull();

        health.recordPollSuccess();
        assertThat(health.status()).isEqualTo(TemporalEngineStatus.RUNNING);
        assertThat(health.lastFailure()).isNull();
    }
}
```

---

### T-8: unknownPayloadFailureAppendsTemporalActFailedLedgerEntry

Place in `TemporalEngineUnknownPayloadTest` (new class). Uses H2 fixture.
Note: **H2 schema uses `payload_type`, not `payload_kind`.**

```java
class TemporalEngineUnknownPayloadTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-18T12:05:00Z"), ZoneOffset.UTC);
    @TempDir Path tempDir;

    private Fixture fixture(String name) {
        String jdbcUrl = "jdbc:h2:file:" + tempDir.resolve(name).toAbsolutePath()
            .toString().replace('\\', '/') + ";DB_CLOSE_DELAY=0";
        DataSource ds = new DriverManagerDataSource(jdbcUrl, "sa", "");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        H2TemporalActRepository repo = new H2TemporalActRepository(ds, mapper, clock);
        H2BaseTopologyRepository ledger = new H2BaseTopologyRepository(ds, mapper, clock);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        TemporalEngineService engine = new TemporalEngineService(
            repo, repo, ledger, ledger, tx, mapper, clock, TemporalRecoveryObservationPort.noOp());
        return new Fixture(jdbcUrl, repo, engine, new JdbcTemplate(ds));
    }

    record Fixture(String jdbcUrl, H2TemporalActRepository repository,
                   TemporalEngineService engine, JdbcTemplate jdbc) {}

    @Test
    void unknownPayloadFailureAppendsTemporalActFailedLedgerEntry() {
        Fixture f = fixture("unknown-payload-ledger");

        // Insert act with unknown payload type directly — H2 uses payload_type column
        f.jdbc.update(
            "INSERT INTO temporal_acts (temporal_act_id, habitat_id, status, due_at_ms, " +
            "payload_type, payload_json, notification_target_ref, created_by_ref_json, " +
            "created_at_ms, updated_at_ms) " +
            "VALUES ('act-unknown-1', 'habitat-001', 'PENDING', -1000, " +
            "'FUTURE_PAYLOAD', '{}', 'surface:test', '{\"value\":\"creator:test\"}', 1, 1)"
        );

        // classifyMisfires should detect unknown payload and fail-close the act
        f.engine.classifyMisfires("habitat-001", Instant.now(clock), 10);

        // Act must be FAILED (not MISFIRED, not PENDING)
        Optional<TemporalAct> act = f.repository.findById("habitat-001", "act-unknown-1");
        assertThat(act).isPresent();
        assertThat(act.get().status()).isEqualTo(TemporalActStatus.FAILED);

        // Ledger entry for TEMPORAL_ACT_FAILED must exist with correct idempotency key
        Integer count = f.jdbc.queryForObject(
            "SELECT COUNT(*) FROM sc_c_ledger_entries " +
            "WHERE habitat_id = 'habitat-001' AND idempotency_key = ?",
            Integer.class,
            "temporal-act-failed:act-unknown-1:unknown-payload"
        );
        assertThat(count).isEqualTo(1);

        // SemanticKind must be TEMPORAL_ACT_FAILED
        String semanticKind = f.jdbc.queryForObject(
            "SELECT semantic_kind FROM sc_c_ledger_entries WHERE idempotency_key = ?",
            String.class,
            "temporal-act-failed:act-unknown-1:unknown-payload"
        );
        assertThat(semanticKind).isEqualTo("TEMPORAL_ACT_FAILED");
    }
}
```

---

### T-9: unknownPayloadFailedActIsObservableByFindById

```java
@Test
void unknownPayloadFailedActIsObservableByFindById() {
    Fixture f = fixture("unknown-payload-observe");

    // Insert and fail-close an unknown payload act (same as T-8 setup)
    f.jdbc.update(
        "INSERT INTO temporal_acts (temporal_act_id, habitat_id, status, due_at_ms, " +
        "payload_type, payload_json, notification_target_ref, created_by_ref_json, " +
        "created_at_ms, updated_at_ms) " +
        "VALUES ('act-unknown-2', 'habitat-001', 'PENDING', -1000, " +
        "'FUTURE_PAYLOAD', '{}', 'surface:test', '{\"value\":\"creator:test\"}', 1, 1)"
    );
    f.engine.classifyMisfires("habitat-001", Instant.now(clock), 10);

    // The act is now FAILED. Build ObservationService and call findById.
    TemporalActObservationService observationService = new TemporalActObservationService(f.repository);

    // Must not throw NPE or ClassCastException
    Optional<TemporalActObservation> obs = observationService.findById("habitat-001", "act-unknown-2");
    assertThat(obs).isPresent();

    TemporalActObservation observation = obs.get();
    assertThat(observation.status()).isEqualTo(TemporalActStatus.FAILED);
    assertThat(observation.payloadKind()).isEqualTo("UNKNOWN");
    assertThat(observation.label()).isNull();
    assertThat(observation.signalKind()).isNull();
    // terminalReason should be set by markFailed
    assertThat(observation.terminalReason()).isNotNull();
}
```

---

### T-10: createIdempotencyInsertFailureDoesNotLeaveUntrackedTemporalAct

```java
@Test
void createIdempotencyInsertFailureDoesNotLeaveUntrackedTemporalAct() {
    // Arrange: a TemporalRequestIdempotencyPort that always throws on insert
    TemporalRequestIdempotencyPort failingIdempotency = new TemporalRequestIdempotencyPort() {
        public Optional<TemporalRequestIdempotencyRecord> find(String h, String k, String kind) {
            return Optional.empty();   // no existing record — new request
        }
        public void insert(TemporalRequestIdempotencyRecord record, Instant now) {
            throw new org.springframework.dao.DataIntegrityViolationException(
                "simulated unique constraint violation");
        }
    };

    // Build applicationService with real H2 repo and failing idempotency port
    String jdbcUrl = "jdbc:h2:file:" + tempDir.resolve("idempotency-fail").toAbsolutePath()
        .toString().replace('\\', '/') + ";DB_CLOSE_DELAY=0";
    DataSource ds = new DriverManagerDataSource(jdbcUrl, "sa", "");
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    H2TemporalActRepository repo = new H2TemporalActRepository(ds, mapper, clock);
    H2BaseTopologyRepository ledger = new H2BaseTopologyRepository(ds, mapper, clock);
    TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    TemporalActService actService = new TemporalActService(repo, repo, ledger, tx, mapper, clock);
    TemporalActObservationService obsService = new TemporalActObservationService(repo);
    TemporalEngineHealth health = new TemporalEngineHealth();
    health.transitionTo(TemporalEngineStatus.RUNNING);

    TemporalActApplicationService appService = new TemporalActApplicationService(
        actService, obsService, failingIdempotency, health, mapper, tx   // tx injected
    );

    CreateSignalTemporalActRequest request = new CreateSignalTemporalActRequest(
        "habitat-001",
        Instant.parse("2026-05-18T13:00:00Z"),
        "Wake up",
        "alarm",
        "surface:test",
        "creator:test",
        "idem-key-001",
        Instant.now(clock)
    );

    // Act — idempotency insert fails → should roll back act creation
    CreateSignalTemporalActResult result = appService.createSignalTemporalAct(request);

    // Result must be IdempotentReplay (race with same key) or Rejected/Failed — NOT Accepted
    assertThat(result).isNotInstanceOf(CreateSignalTemporalActResult.Accepted.class);

    // No temporal act row should survive in the DB
    Integer actCount = new JdbcTemplate(ds).queryForObject(
        "SELECT COUNT(*) FROM temporal_acts WHERE habitat_id = 'habitat-001'",
        Integer.class
    );
    assertThat(actCount).isZero();

    // No ledger entry should survive
    Integer ledgerCount = new JdbcTemplate(ds).queryForObject(
        "SELECT COUNT(*) FROM sc_c_ledger_entries WHERE habitat_id = 'habitat-001'",
        Integer.class
    );
    assertThat(ledgerCount).isZero();
}
```

---

## 15. Stop conditions

Stop and report if any of these arise:

```text
1. The atomic idempotency pattern requires a new table or schema change beyond V2.
2. SemanticKind.TEMPORAL_ACT_FAILED is absent from the enum (it exists — verify first).
3. TemporalActService transaction-participating methods cannot be made public with an
   active-transaction guard; stop and report instead of using package-private/protected
   visibility, because application/service are different Java packages.
4. heartbeat return type change from void to int breaks a test — adapt the affected test.
5. mvn test fails after applying any single phase change.
6. H2 schema column `payload_type` does not match what TemporalEngineService passes to
   readPayload — verify H2TemporalActRepository.readPayload handles "FUTURE_PAYLOAD" default
   (it throws; that is intentional — the new failUnknownPayload path must convert it to null
   BEFORE calling toObservation).
```
