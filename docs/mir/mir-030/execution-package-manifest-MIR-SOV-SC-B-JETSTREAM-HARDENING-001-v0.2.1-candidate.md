# execution-package-manifest — MIR-SOV-SC-B-JETSTREAM-HARDENING-001

```text
Document ID: execution-package-manifest-MIR-SOV-SC-B-JETSTREAM-HARDENING-001
Version:     v0.2.1-candidate
Status:      Candidate execution package
```

---

## Included files

```text
MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.0-candidate.md
CSA-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.0-merged.md
SDD-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.0-candidate.md
context-MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.1-candidate.md
codex-prompt-MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.1-candidate.md
acceptance-map-MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.1-candidate.md
implementation-report-template-MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.1-candidate.md
execution-package-manifest-MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.1-candidate.md
```

---

## Version delta from v0.2.0 proposal

```text
H-001 — Pull consumer replay test must call sub.pull(1) before sub.nextMessage(...).
H-002 — Removed fragile incompatible-stream conflict test guidance; retain DEBT-B-NATS-009 unless a verified non-updateable conflict is tested.
```

---

## Scope

```text
IN:
  live JetStream stream application
  all five SCB_*_V1 streams
  durable SCB_LIFECYCLE_V1 consumer
  lifecycle retention / replay fixture
  explicit consumer policy
  import-level architecture hardening
  DEBT-B-NATS-004 / 005 / 009 if retained

OUT:
  Adapter Manifest runtime
  manifest-over-NATS transport
  route assignment runtime
  provider execution
  command admission runtime
  SC-C direct NATS publishing
```

---

## Suggested branch and commit

```text
Branch:
  feat/sc-b-mir-030-jetstream-hardening

Commit:
  feat(sc-b): harden jetstream stream and lifecycle setup
```
