# MU-017 Patch 001 — Review Gaps Execution Package

**Package:** `execution-package-MU-017-review-gaps-patch-001`  
**Version:** `v0.2.1`  
**Status:** Draft / ready for Codex after review  
**Date:** 2026-05-22  
**Target MU:** `MU-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001`  
**Operational slot:** MU-017  
**Patch scope:** close review gaps found after first normalized SQLite topology implementation.

## Purpose

This package is a narrow patch package for MU-017. It does **not** reopen the MIR and does **not** change the accepted normalized-SQLite direction.

It closes the review findings from `review-report-MU-017-normalized-topology-sqlite-persistence-v0.1.0.md`:

```text
B-01  findLocatedDevices/findLocatedEndpoints lost H2 fallback semantics.
B-02  initial endpoint health can be lost on SQLite hydration.
B-03  habitat-global capability_id uniqueness is enforced by schema but not by domain validation.
G-01  submitted ZIP / working tree includes unrelated governance noise.
```

It also closes the low-risk implementation hardening item:

```text
H-04  SQLiteMaterializationDecisionReplayRepository must use injected Clock.
```

## Files

```text
README.md
context.md
codex-prompt.md
acceptance-map.md
implementation-report-template.md
review-report.md
MANIFEST.md
```

## Branch

```text
fix/sc-c-mir-017-normalized-topology-sqlite-persistence
```

## Commit

```text
fix(sc-c): close MIR-017 topology SQLite persistence gaps
```

## Non-goals

```text
No new MIR.
No V4 migration redesign.
No northbound facade.
No Effective Interaction Boundary.
No View Composer.
No SC-B runtime.
No SC-D adapter runtime.
No graph database.
No ActionTemporalPayload.
No command dispatch.
No outbox dispatcher.
No normalized provider_bindings table in this patch.
```
