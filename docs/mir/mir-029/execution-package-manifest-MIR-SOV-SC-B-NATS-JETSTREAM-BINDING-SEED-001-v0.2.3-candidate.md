# Execution Package Manifest — MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001

```text
Document ID:  execution-package-manifest-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
Version:      v0.2.3-candidate
Status:       Execution package manifest / candidate
Date:         2026-05-30
Target MIR:   MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v0.2.0-candidate
```

---

## Package contents

```text
MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.2.0-candidate.md
CSA-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.1.0-refresh.md
CSA-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.1.0-user-draft.md
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001-v0.2.0-candidate.md
PDR-SOV-SC-D-COMMAND-PAYLOAD-001-v0.3.0-candidate.md
MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001-v1.0.0-accepted.md
MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001-v1.0.0-accepted.md
context-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.2.3-candidate.md
codex-prompt-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.2.3-candidate.md
acceptance-map-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.2.3-candidate.md
implementation-report-template-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.2.3-candidate.md
execution-package-manifest-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.2.3-candidate.md
```

---

## Operational target

```text
MU-029 — SC-B NATS / JetStream Binding Seed
Branch: feat/sc-b-mir-029-nats-jetstream-binding-seed
Primary commit: feat(sc-b): add nats jetstream binding seed
```

---

## Package stance

This package authorizes a Codex implementation attempt for a bounded physical binding seed.

It does not authorize productive SC-D adapter runtime, lifecycle admission, manifest runtime, capability runtime or semantic authority transfer to NATS / JetStream.

---

## Changes from v0.2.0 proposal

```text
H-001 — Static MU-028 codecs / validator usage; no instantiation.
H-002 — ScJsonWireCodec.toWireEnvelope overloads require payloadType and schemaVersion.
H-003 — WireEnvelopeValidator.validate is static.
H-004 — Exact constructors for ScMessageMetadata and envelope records.
H-005 — Response subject grammar corrected to sc.v1.{habitat}.response.{adapter}.{correlationHex}.
H-006 — Event handler registration uses seed wildcard subscription pattern only for subscriptions.

H-007 — DispatchOutcome.Dispatched usage backed by verified existing record shape; no invented constructor ambiguity.
H-008 — Runtime E2E DataSource/Flyway guidance added for SC-B V100/V101 dispatch persistence tables.
H-009 — Failed(...) pseudocode removed; unsupported payload-type failures now use the verified Failed(UUID, UUID, String, String, boolean) record shape.
```
