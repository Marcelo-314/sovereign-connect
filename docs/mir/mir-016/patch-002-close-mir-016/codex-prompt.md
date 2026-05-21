# codex-prompt.md — MU-016 Patch 002 Close MIR-016

```text
Package:   execution-package-MU-016-close-mir-016-patch
Version:   v0.2.1
Patch:     patch-002-close-mir-016
```

You are closing MU-016. Read `context.md` completely before modifying any file.

This is a two-item patch. The entire code change is approximately 8 lines in one file.
The test scenarios are specified in `context.md §4`. Adapt them to the existing test infrastructure. The cleanup is a git procedure.

---

## Absolute constraints

Do NOT touch:

```
H2TemporalActRepository          TemporalActSeedTest
handleLockLoss() method          Patch-001 fixes (B-01/B-02/B-03/B-04)
.idea/**                         docs/mir/mir-001/**   docs/mir/mir-002/**
```

Do NOT introduce:

```
ActionTemporalPayload / ActionRequest / SC-B / NATS / View Composer / Effective Access Boundary
```

---

## Phase 0 — Baseline verification

```bash
mvn test
```

Expected: 132 tests, 0 failures. If baseline fails, stop and report.

---

## Phase 1 — Fix TemporalEngineLifecycle.stop() (R-01)

Open `TemporalEngineLifecycle.java`.

Locate the current `stop()` method. Its exact body is in `context.md §2`.

Replace it with the body in `context.md §3`. The change is:

1. Capture the return value: `boolean stopped = runner.awaitStopped(...)`.
2. Add an `if (!stopped)` block that transitions health to `FAILED`, sets `running = false`,
   and throws `IllegalStateException("TEMPORAL_ENGINE_STOP_TIMEOUT: ...")`.
3. Move `lockRepository.release(...)` to be conditional on `stopped == true`.
4. Move `stopHeartbeat()` so it runs only after `stopped == true`.
5. If `stopped == false`, do not stop heartbeat and do not release the lock. This timeout is fail-closed/process-fatal.

Do NOT modify `handleLockLoss()`. It is already correct.

Run `mvn test`. Must stay at 132 / 0 failures.

---

## Phase 2 — Add tests for R-01

Open `TemporalEngineReviewBlockersTest.java`.

Add or update tests inside the existing class to cover the required scenarios in `context.md §4`:

- `stopDoesNotReleaseLockIfRunnerFailsToStop` (T-A)
- `successfulStopReleasesSingleNodeLockAfterRunnerStops` (T-B)

Both tests use `Mockito.mock(TemporalEngineRunner.class)` and `Mockito.mock(TemporalEngineLockPort.class)`.
`mock`, `when`, `verify`, `never`, `any`, `anyString`, `anyLong`, `anyInt` are already imported.
`TemporalRecoveryResult` and `assertThatThrownBy` are also already imported.

Do not copy pseudocode mechanically if local helpers differ. Preserve the scenarios and assertions exactly: timeout must not release lock, and successful stop must release lock after confirmed runner stop. Also verify by source review or targeted test that timeout does not stop heartbeat before throwing.

Run `mvn test`. Expected: at least 134 tests, 0 failures.

---

## Phase 3 — Working tree cleanup (R-02)

Follow the procedure in `context.md §5` exactly.

```bash
git status --short
```

Revert `.idea` if modified. Revert `docs/mir/mir-001` and `docs/mir/mir-002` if modified.
Remove encoding-artifact filenames if present.
Resolve stale `docs/mir/mir-016` file entry if present (see `context.md §5.1`).

After cleanup:

```bash
git status --short
git diff --name-only HEAD
```

Only MU-016 relevant files should remain changed.

---

## Phase 4 — Final verification

```bash
mvn test
```

Expected: ≥ 134 tests, 0 failures, 0 errors.

---

## Final output

Complete `docs/mir/mir-016/patch-002-close-mir-016/implementation-report.md` with:

```text
Branch and baseline commit
Files changed in this patch (exhaustive list)
Test summary: total / failures
stop() fix: target behavior and before/after description
T-A assertion summary: what was verified
T-B assertion summary: what was verified
git status --short output after cleanup
git diff --name-only HEAD output
Confirmation: .idea absent from commit
Confirmation: mir-001 and mir-002 absent from commit
Confirmation: docs/mir/mir-016 is a directory (not stale file entry)
Confirmation: patch-001 guarantees intact (B-01/B-02/B-03/B-04)
H-05 debt note: temporal_act_id bound as String into BLOB columns — Option C deferral
```
