# acceptance-map.md — MU-016 Patch 002 Close MIR-016

```text
Package:             execution-package-MU-016-close-mir-016-patch
Version:             v0.2.1
Operational slot:    MU-016
Patch:               patch-002-close-mir-016
```

---

## Acceptance criteria

| ID | Requirement | Verification |
|---|---|---|
| C-001 | `TemporalEngineLifecycle.stop()` does not release the single-node lock unless runner shutdown is confirmed. | Source review + test. |
| C-002 | If `runner.awaitStopped(...)` returns false, health transitions to `FAILED` or equivalent observable failure. | Unit test. |
| C-003 | If runner shutdown times out, `lockRepository.release(...)` is not called. | Unit test with fake/stub lock port. |
| C-004 | Successful stop still releases lock and transitions to `STOPPED`. | Existing or new regression test. |
| C-005 | Heartbeat is stopped only after confirmed runner shutdown; on stop timeout heartbeat is preserved and the lock is not released. | Source review + tests or implementation-report evidence. |
| C-006 | Patch does not modify unrelated MIR folders. | `git status --short` and implementation report. |
| C-007 | Patch does not include `.idea` changes. | `git status --short` and implementation report. |
| C-008 | `docs/mir/mir-016/` is a real directory with MU-016 package content, not a stale file entry. | `git status --short` + tree listing. |
| C-009 | Patch preserves closed original blockers from patch-001. | Existing tests remain green. |
| C-010 | Full test suite passes. | `mvn test`. |

---

## Required tests

Minimum new or updated tests/scenarios:

```text
stopDoesNotReleaseLockIfRunnerFailsToStop
successfulStopReleasesSingleNodeLockAfterRunnerStops
```

Required source-review evidence if direct heartbeat assertion is not practical:

```text
On awaitStopped == false, stopHeartbeat() is not invoked before throwing.
On awaitStopped == true, stopHeartbeat() occurs before lock release.
```

---

## Stop conditions

Stop and report if:

```text
runner shutdown cannot be observed/tested without major redesign;
lock release cannot be prevented on timeout with current API;
fix requires changing the public Temporal Engine scope;
fix requires touching SC-B, NATS, dispatcher, View Composer or ActionTemporalPayload;
working tree cleanup would require discarding intentional user changes outside MU-016.
```
