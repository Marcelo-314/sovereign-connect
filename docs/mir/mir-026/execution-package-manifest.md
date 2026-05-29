# Execution Package Manifest — MU-026 SC-B Outbox Bridge Seed

```text
Package ID:   MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001-execution-package
Version:      v0.2.0-candidate
Status:       Candidate / ready for Codex execution
Date:         2026-05-29
MIR:          MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001 v0.2.0-candidate
MU:           MU-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
Slot:         MU-026
Track:        SC-B Runtime Dispatch Hardening / H2
Branch:       feat/sc-b-mir-026-outbox-bridge-seed
Commit:       feat(sc-b): bridge sc-c outbox to runtime dispatch
```

---

## Included files

```text
docs/mir/mir-026/
  MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001.md
  context.md
  codex-prompt.md
  acceptance-map.md
  code-surface-audit.md
  implementation-report.md
  execution-package-manifest.md
```

Root versioned files are also provided for review/download.

---

## Scope

H2 only: SC-C outbox bridge into SC-B runtime dispatch candidates.

Explicitly excluded:

```text
NATS / JetStream
serialization runtime
lifecycle channel implementation
SC-D adapter implementation
production ScdCommand
fact family production classes
dispatch observation persistence
OutboxEntryStatus mutation
```

---

## Expected validation

```text
mvn -q compile
mvn -q test -Dtest="SQLiteScOutboxDispatchReadPortTest"
mvn -q test -Dtest="OutboxEntryDispatchProjectorTest,ScLedgerDispatchCandidateReadAdapterTest"
mvn -q test -Dtest="ScLedgerDispatchBridgeRuntimeTest,ScBusOutboxBridgeArchitectureTest,ScBusHardeningArchitectureTest,ScBusArchitectureTest"
mvn -q test
```

Expected full regression:

```text
>=354 tests, 0 failures, 0 errors, 0 skipped
SC-C/non-bus baseline: 240 tests still green
```


## v0.2.0-candidate additions

```text
- Explicitly forbids attempt_count in findDispatchableEntries SELECT.
- Records sc-c.timer-fired as verified TemporalEngineService logical topic.
- Provides exact seven-dependency RuntimeDispatchService manual wiring for bridge runtime tests.
- Expected test delta: +37 tests; full regression expected >=354 tests.
```
