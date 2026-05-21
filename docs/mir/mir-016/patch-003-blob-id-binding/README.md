# MU-016 Patch 003 — BLOB ID Binding Closure

Document: README.md
Version: v0.2.1
MU: MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot: MU-016
Patch: patch-003-blob-id-binding
Status: Draft / Execution package

## Purpose

This patch closes the remaining H-05 technical debt from MU-016: temporal IDs are declared as BLOB columns by the SQLite/Flyway schema but are currently bound as Java Strings. SQLite accepts this through dynamic typing, but that is not strict conformance with the persistence schema doctrine.

## Scope

Implement adapter-local conversion between canonical String temporal IDs and SQLite BLOB(16) storage representation.

## Files

```text
docs/mir/mir-016/patch-003-blob-id-binding/
  README.md
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report-template.md
```

## Non-goals

- Do not change domain/API ID types from String.
- Do not patch SDD-SCHEMA to TEXT.
- Do not reopen Temporal Engine lifecycle, recovery gate, request idempotency, unknown payload handling, or single-node guard semantics.
- Do not implement SC-B, dispatcher, View Composer, ActionTemporalPayload or ActionRequest.


## v0.2.1 notes

This version aligns the package after review:

```text
- Internal package versioning is unified at v0.2.1.
- T-5 nullTemporalActIdInIdempotencyRecordStoredAsNull is mandatory.
- Acceptance map includes P3-015 for null-preserving idempotency temporal_act_id.
- Implementation report requires explicit NULL-path evidence.
```
