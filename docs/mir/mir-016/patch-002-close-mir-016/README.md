# MU-016 Patch 002 — Close MIR-016

```text
Package:             execution-package-MU-016-close-mir-016-patch
Version:             v0.2.1
Operational slot:    MU-016
Canonical MU ID:     MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
MIR:                 MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v0.1.1-draft
Patch:               patch-002-close-mir-016
Status:              Ready for Codex
```

## Purpose

This patch package closes the two residual blockers found after `patch-001-review-blockers`:

```text
R-01 stop/lock-release ordering
R-02 working tree / governance cleanup
```

It does not reopen the original MU-016 technical blockers. Those were closed by patch-001 and must remain closed.

v0.2.1 additionally fixes the stop-timeout policy: if the runner does not stop, the lifecycle must preserve the heartbeat and must not release the single-node lock.

## Files

```text
docs/mir/mir-016/patch-002-close-mir-016/
  README.md
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report-template.md
```

## Branch

```text
fix/sc-c-mir-016-temporal-engine-industrial-hardening
```

## Commit

```text
fix(sc-c): close MIR-016 temporal engine hardening
```
