# Execution Package Manifest — PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001 v0.1.0

```text
Document ID:  execution-package-manifest-PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001
Version:      v0.1.0
Status:       Ready for Codex
Generated:    2026-06-01
Corpus:       Sovereign Connect
Plane:        SC-B
Patch type:   Micro refactor / quality hardening
```

## Files

```text
context-PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001-v0.1.0.md
codex-prompt-PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001-v0.1.0.md
acceptance-map-PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001-v0.1.0.md
implementation-report-template-PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001-v0.1.0.md
execution-package-manifest-PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001-v0.1.0.md
```

## Branch

```text
feat/sc-b-mir-031-nats-jetstream-debt-closure
```

## Commit

```text
refactor(sc-b): centralize nats connection creation
```

## Expected regression

```text
sovereign-connect: >=483 tests, 0 failures, 0 errors, 0 skipped
EIB: 56 tests, 0 failures, 0 errors, 0 skipped, if included by the local regression command
```
