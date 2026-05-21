# context.md — MU-016 Patch 002 Close MIR-016

```text
Document:            context.md
Package:             execution-package-MU-016-close-mir-016-patch
Version:             v0.2.1
Operational slot:    MU-016
Canonical MU ID:     MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Patch:               patch-002-close-mir-016
Baseline ZIP:        sovereign-connect-016-patch-001-review-blockers.zip
Baseline tests:      132 / 0 failures
Status:              Ready for Codex
```

---

## 0. How to use this file

This is a focused two-item closure patch. Read completely before touching code.

Section 2 shows the exact current `stop()` body that must change.
Section 3 shows the exact required replacement.
Section 4 defines the required test scenarios. Adapt them to the existing test infrastructure rather than treating pseudocode as literal production constraints.
Section 5 defines the working tree cleanup procedure.

---

## 1. Scope

Patch-001 closed the four original technical blockers. This patch closes the two residual
blockers identified in the post-patch review:

```text
R-01  TemporalEngineLifecycle.stop() releases the single-node lock even if the runner
      did not stop within shutdownTimeoutMs.

R-02  Working tree contains unrelated governance/IDE noise that must not enter the
      MU-016 commit.
```

**Only change:**

```text
TemporalEngineLifecycle.stop()                   (R-01 fix — approximately 8 lines)
TemporalEngineReviewBlockersTest                 (R-01 test — two new test methods)
```

**Do NOT touch:**

```text
H2TemporalActRepository               (seed legacy — preserve exactly)
TemporalActSeedTest                   (26 tests — preserve exactly)
Patch-001 fixes                       (B-01/B-02/B-03/B-04 — must remain intact)
Any other production class or test
SovereignConnectApplication
.idea/**
docs/mir/mir-001/**
docs/mir/mir-002/**
```

**Do NOT introduce:**

```text
ActionTemporalPayload / ActionRequest
SC-B / NATS / JetStream / outbox dispatcher
View Composer / Effective Access Boundary
Multi-node clustering
```

---

## 2. Exact current stop() body — what must change

File: `src/main/java/com/sovereign/connect/core/temporal/engine/TemporalEngineLifecycle.java`

```java
@Override
public void stop() {
    runner.stop();
    runner.awaitStopped(Duration.ofMillis(properties.shutdownTimeoutMs()));  // ← return value IGNORED
    stopHeartbeat();
    if (properties.singleNodeGuardEnabled()) {
        lockRepository.release(properties.habitatId(), STORAGE_PARTITION_REF, engineInstanceId, Instant.now(clock));
        // ↑ executes unconditionally — even if runner is still alive
    }
    running = false;
    health.transitionTo(TemporalEngineStatus.STOPPED);
}
```

**The defect:** If `awaitStopped(...)` times out, `runner.isRunning()` may still return `true`,
but the lock is released and health transitions to `STOPPED` as if shutdown succeeded.
A second engine instance could acquire the lock and start polling while the first is still alive.

---

## 3. Required replacement — target behavior

Replace the `stop()` method with behavior equivalent to this body:

```java
@Override
public void stop() {
    runner.stop();
    boolean stopped = runner.awaitStopped(Duration.ofMillis(properties.shutdownTimeoutMs()));

    if (!stopped) {
        health.transitionTo(TemporalEngineStatus.FAILED);
        running = false;
        throw new IllegalStateException(
            "TEMPORAL_ENGINE_STOP_TIMEOUT: runner did not stop within "
            + properties.shutdownTimeoutMs() + "ms — single-node lock NOT released; heartbeat preserved"
        );
    }

    stopHeartbeat();

    if (properties.singleNodeGuardEnabled()) {
        lockRepository.release(
            properties.habitatId(), STORAGE_PARTITION_REF, engineInstanceId, Instant.now(clock));
    }
    running = false;
    health.transitionTo(TemporalEngineStatus.STOPPED);
}
```

**Why this ordering:**

- `runner.stop()` signals the virtual thread to stop.
- `awaitStopped(...)` waits up to `shutdownTimeoutMs` for the thread to terminate
  (`thread.join(timeoutMillis)` in `TemporalEngineRunner.awaitStopped`).
- If `awaitStopped(...)` returns `false`, the runner may still be alive and capable of polling.
- On timeout: health → `FAILED`, `running = false`, throw to surface the failure.
- On timeout, **do not call `stopHeartbeat()` and do not release the lock**. Keeping the heartbeat alive while the process is still alive prevents another instance from acquiring the lock before the old runner is actually gone.
- Only after confirmed runner stop should `stopHeartbeat()` run.
- **Only after confirmed runner stop and heartbeat stop** does the lock release happen.

**Interaction with `handleLockLoss()`:** That path (heartbeat thread detecting lock loss) calls
`runner.stop()` + `awaitStopped(...)` + `health → FAILED`, but does NOT call `lockRepository.release()`.
This is already correct — do not change `handleLockLoss()`.

**`SmartLifecycle.stop()` contract:** Spring calls `stop()` during context shutdown. Throwing
`IllegalStateException` from `stop()` is acceptable as a fail-closed signal. The important invariant is that `isRunning()` returns `false` after the throw so Spring does not retry. Setting `running = false` before the throw satisfies this. The heartbeat preservation on timeout is intentional: this timeout is a process-fatal condition, not a normal shutdown.

---

## 4. Required test scenarios — adapt using existing test infrastructure

Add tests to `TemporalEngineReviewBlockersTest` covering the following scenarios. Use the existing helper infrastructure:
`FixedLockPort`, `properties(...)`, `migratedDataSource()`, Mockito (`mock`, `verify`, `never`).

### Test infrastructure note

`FixedLockPort` (already in the class) never invokes `release`. For the new tests, use
`Mockito.mock(TemporalEngineLockPort.class)` so `verify(lockPort, never()).release(...)` works.
The existing `FixedLockPort` can't be used for `never().release(...)` assertions.

`TemporalEngineRunner` has a constructor:

```java
public TemporalEngineRunner(TemporalEngineService engine, String habitatId, Clock clock, long pollingIntervalMs)
```

To make the runner not stop, we need `awaitStopped(...)` to return `false`.
The runner's `awaitStopped` does `thread.join(timeoutMillis)` — if the thread is alive at timeout,
it returns `!thread.isAlive()` = `false`.

The simplest approach: use `Mockito.mock(TemporalEngineRunner.class)` and stub
`awaitStopped(any())` to return `false`. This avoids timing-dependent test behavior.

Check that Mockito is already on the classpath (it is — `mock(...)` is already imported in
`TemporalEngineReviewBlockersTest`).

---

### T-A: stopDoesNotReleaseLockIfRunnerFailsToStop

```java
@Test
void stopDoesNotReleaseLockIfRunnerFailsToStop() {
    // Arrange: a runner mock that confirms it never stopped
    TemporalEngineRunner runnerMock = mock(TemporalEngineRunner.class);
    when(runnerMock.awaitStopped(any())).thenReturn(false);   // simulate timeout

    TemporalEngineLockPort lockPortMock = mock(TemporalEngineLockPort.class);
    when(lockPortMock.tryAcquire(anyString(), anyString(), anyString(), any(), anyLong()))
        .thenReturn(true);
    when(lockPortMock.heartbeat(anyString(), anyString(), anyString(), any(), anyLong()))
        .thenReturn(1);

    TemporalEngineHealth health = new TemporalEngineHealth();
    health.transitionTo(TemporalEngineStatus.RUNNING);  // simulate started state

    TemporalEngineProperties props = properties(50, 200, true);  // singleNodeGuardEnabled = true

    // Use a no-op engine to avoid recovery overhead; pass a real Clock
    TemporalEngineService engineMock = mock(TemporalEngineService.class);
    when(engineMock.classifyMisfires(anyString(), any(), anyInt()))
        .thenReturn(new TemporalRecoveryResult(0, 0, true));  // complete immediately

    TemporalEngineLifecycle lifecycle = new TemporalEngineLifecycle(
        engineMock, runnerMock, props, health, lockPortMock, Clock.systemUTC()
    );

    // Act + Assert: stop() must throw because runner did not stop
    assertThatThrownBy(() -> lifecycle.stop())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TEMPORAL_ENGINE_STOP_TIMEOUT");

    // Critical: release must NOT have been called
    verify(lockPortMock, never()).release(anyString(), anyString(), anyString(), any());

    // Health must be FAILED (not STOPPED)
    assertThat(health.status()).isEqualTo(TemporalEngineStatus.FAILED);

    // isRunning() must be false so Spring does not retry stop
    assertThat(lifecycle.isRunning()).isFalse();
}
```

Additional source-review requirement for T-A:

```text
When awaitStopped returns false:
  stopHeartbeat() MUST NOT run before throwing.
  lockRepository.release(...) MUST NOT run.
```

If a direct unit assertion for `stopHeartbeat()` is not practical because it is private lifecycle internals, the implementation report MUST provide source-review evidence showing `stopHeartbeat()` is located only after the `if (!stopped)` branch.


---

### T-B: successfulStopReleasesSingleNodeLockAfterRunnerStops

```java
@Test
void successfulStopReleasesSingleNodeLockAfterRunnerStops() {
    // Arrange: a runner mock that confirms it stopped immediately
    TemporalEngineRunner runnerMock = mock(TemporalEngineRunner.class);
    when(runnerMock.awaitStopped(any())).thenReturn(true);    // simulate clean stop

    TemporalEngineLockPort lockPortMock = mock(TemporalEngineLockPort.class);
    when(lockPortMock.tryAcquire(anyString(), anyString(), anyString(), any(), anyLong()))
        .thenReturn(true);
    when(lockPortMock.heartbeat(anyString(), anyString(), anyString(), any(), anyLong()))
        .thenReturn(1);

    TemporalEngineHealth health = new TemporalEngineHealth();
    health.transitionTo(TemporalEngineStatus.RUNNING);

    TemporalEngineProperties props = properties(50, 200, true);  // singleNodeGuardEnabled = true

    TemporalEngineService engineMock = mock(TemporalEngineService.class);
    when(engineMock.classifyMisfires(anyString(), any(), anyInt()))
        .thenReturn(new TemporalRecoveryResult(0, 0, true));

    TemporalEngineLifecycle lifecycle = new TemporalEngineLifecycle(
        engineMock, runnerMock, props, health, lockPortMock, Clock.systemUTC()
    );

    // Act: stop succeeds
    lifecycle.stop();

    // release MUST have been called exactly once
    verify(lockPortMock).release(anyString(), anyString(), anyString(), any());

    // Health must be STOPPED
    assertThat(health.status()).isEqualTo(TemporalEngineStatus.STOPPED);

    // isRunning() must be false
    assertThat(lifecycle.isRunning()).isFalse();
}
```

---

### Why two tests

T-A proves the lock is NOT released on failure. T-B proves the lock IS released on success.
Without T-B, a broken implementation that never releases the lock would pass T-A but break
the normal shutdown path silently. The pair is the minimal complete assertion.

---

## 5. Working tree cleanup — exact procedure

After the code change and tests pass, run the following before committing:

### 5.1 Revert unrelated changes

```bash
# Check what changed
git status --short

# Revert .idea if modified
git checkout -- .idea

# Revert if mir-001 or mir-002 were accidentally staged/modified
git checkout -- docs/mir/mir-001
git checkout -- docs/mir/mir-002

# Remove any encoding-artifact filenames (names containing literal #U2014 or em-dash)
find . -name "*#U2014*" -not -path "./.git/*" -delete
find . -name "*—*" -not -path "./.git/*" -type f | grep -v ".md" | head -5
# (Review that output before deleting — only remove accidental artifacts, not intentional names)

# Resolve docs/mir/mir-016 stale file entry if git shows it as a file
# Check:
git ls-files docs/mir/mir-016
# If it shows "docs/mir/mir-016" as a blob (file), remove the stale entry:
git rm --cached docs/mir/mir-016 2>/dev/null || true
# The directory docs/mir/mir-016/ with its contents should remain tracked
```

### 5.2 Verify what remains staged

```bash
git status --short
```

Expected: only files under `sovereign-connect/src/` and `docs/mir/mir-016/` relevant to
MU-016. Specifically:

```text
M  sovereign-connect/src/main/java/com/sovereign/connect/core/temporal/engine/TemporalEngineLifecycle.java
M  sovereign-connect/src/test/java/com/sovereign/connect/core/temporal/TemporalEngineReviewBlockersTest.java
A  docs/mir/mir-016/patch-002-close-mir-016/context.md
A  docs/mir/mir-016/patch-002-close-mir-016/codex-prompt.md
A  docs/mir/mir-016/patch-002-close-mir-016/acceptance-map.md
A  docs/mir/mir-016/patch-002-close-mir-016/implementation-report-template.md
A  docs/mir/mir-016/patch-002-close-mir-016/implementation-report.md
```

Any other modification outside these paths must be explicitly justified in the implementation
report or reverted.

### 5.3 Implementation report git evidence

The implementation report must include the literal output of:

```bash
git status --short
git diff --name-only HEAD
```

This is the minimal evidence that R-02 is closed.

---

## 6. Preservation requirements

After this patch, the following must remain true:

```text
132 tests from patch-001 all pass (+ 2 new tests = 134 minimum)
mvn test: 0 failures, 0 errors
TemporalActSeedTest: 26 passing (unchanged)
TemporalEngineIndustrialBoundaryTest: 7 passing (unchanged)
TemporalEngineSpringContextTest: 1 passing (unchanged)
TemporalEngineReviewBlockersTest: 23 → 25 (+ T-A and T-B)
B-01 recovery gate: recoverMisfires() loop unchanged
B-02 atomic idempotency: createSignalTemporalActInTransaction unchanged
B-03 unknown payload: failUnknownPayload + toObservation unchanged
B-04 heartbeat: lockRepository.heartbeat(ttlMs) unchanged; heartbeat preservation on stop timeout added
handleLockLoss(): unchanged (already does not call release — correct)
```

---

## 7. Single-node guard interaction map

The following table shows all code paths that may or may not call `lockRepository.release(...)`.
After this patch, the invariant must be: **release is called only when the runner is confirmed stopped, and heartbeat is preserved on stop timeout.**

| Path | Release called? | Correct? |
|---|---|---|
| `stop()` — runner stops within timeout | YES, after confirmed | ✓ target |
| `stop()` — runner times out | NO — throws FAILED; heartbeat preserved | ✓ target |
| `handleLockLoss()` — heartbeat detects lock stolen | NO — caller lost the lock | ✓ already correct |
| `start()` — `tryAcquire` fails | Never acquired — nothing to release | ✓ already correct |
| Normal `stop()` with `singleNodeGuardEnabled = false` | Lock not used — nothing to release | ✓ no change needed |

---

## 8. Stop conditions

Stop and report if:

```text
1. TemporalEngineRunner.awaitStopped cannot be stubbed via Mockito without additional
   dependency — use mock(TemporalEngineRunner.class) which is already supported.
2. The throw from stop() causes Spring context shutdown to hang in tests —
   catch the exception in the test with assertThatThrownBy as shown in T-A.
3. mvn test fails after the stop() change (regression in an existing test) —
   report which test and the failure message before attempting a fix.
4. R-02 cleanup would discard intentional changes outside MU-016 scope —
   report which files and why.
```
