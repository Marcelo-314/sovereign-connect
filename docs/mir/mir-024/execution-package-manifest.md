# execution-package-manifest — MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001

```text
Document ID:  execution-package-manifest-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001
Version:      v0.2.0-candidate
Status:       Candidate Package Manifest
Date:         2026-05-29
Corpus:       Sovereign Connect
Plane:        SC-B
MU:           MU-SOV-SC-B-ABSTRACT-BUS-SEED-001
Slot:         MU-024
Branch:       feat/sc-b-mir-024-abstract-bus-seed
```

---

# 0. Package contents

Download-level files:

```text
context-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001-v0.2.0-candidate.md
codex-prompt-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001-v0.2.0-candidate.md
acceptance-map-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001-v0.2.0-candidate.md
code-surface-audit-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001-v0.2.0-merged.md
implementation-report-template-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001-v0.2.0-candidate.md
execution-package-manifest-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001-v0.2.0-candidate.md
```

Repository layout inside zip:

```text
docs/mir/mir-024/context.md
docs/mir/mir-024/codex-prompt.md
docs/mir/mir-024/acceptance-map.md
docs/mir/mir-024/code-surface-audit.md
docs/mir/mir-024/implementation-report.md
docs/mir/mir-024/execution-package-manifest.md
```

---

# 1. Execution target

```text
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate
```

---

# 2. Scope

```text
SC-B abstract bus seed.
No broker.
No NATS.
No JetStream.
No Redis.
No Vert.x.
No gRPC/WebSocket/HTTP physical binding.
No serialization ADR implementation.
No adapter lifecycle channel.
No real SC-D payload shapes.
```

---

# 3. Candidate improvements over v0.1.0

```text
1. Baseline facts verified from code inspection.
2. DeliveryLane extension classified as verified-safe additive operation.
3. DispatchOutcome specified as sealed interface with Dispatched / Failed / NoHandler.
4. Architecture test aligned with repository file-walk + Files.readString + AssertJ pattern.
5. STOP-5 full regression gate added for DeliveryLane extension.
6. Paste-ready contract/model/port code moved into context.md.
7. Verification made normative through Java architecture tests rather than shell-only grep.
8. Physical binding dependency rule clarified as: no new dependency and no bus imports.
```

---

# 4. Expected branch and commit

```text
Branch: feat/sc-b-mir-024-abstract-bus-seed
Commit: feat(sc-b): add abstract bus seed runtime dispatch
```
