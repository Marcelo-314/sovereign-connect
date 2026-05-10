# MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 — Acceptance Map

```text
Document ID: MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001-ACCEPTANCE-MAP
MIR: MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
MIR Version: v1.0.0-accepted
Materialization Unit: MU-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
Status: Evidence Support Artifact
Validation Target: L4
Date: 2026-05-10
```

---

## 0. Purpose

This file preserves the official AC-001 through AC-025 mapping for:

```text
MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 v1.0.0-accepted
```

It is part of the execution package:

```text
docs/mir/mir-004/
  MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001.md
  context.md
  codex-prompt.md
  implementation-report.md
  acceptance-map.md
```

This file must be used by the implementation agent when reporting acceptance results.

Do not invent new AC numbering.

---

## 1. Acceptance Criteria Map

| AC | Criterion | Required Evidence | Result |
|---|---|---|---|
| AC-001 | A persistence-capable repository, adapter or port exists for Base Topology snapshots. | Durable adapter/port/wrapper class. | Pending |
| AC-002 | Existing `BaseTopologyRepository` behavior is preserved or explicitly backward-compatible. | Existing MU-001/MU-002 tests still pass; no breaking signature change. | Pending |
| AC-003 | A current Base Topology snapshot can be saved. | Snapshot save test. | Pending |
| AC-004 | A current Base Topology snapshot can be retrieved. | Snapshot retrieve test. | Pending |
| AC-005 | The retrieved snapshot includes `topologyVersion`. | Snapshot field/assertion test. | Pending |
| AC-006 | `snapshot.topologyVersion` equals `snapshot.topology.topologyVersion`. | Envelope consistency test. | Pending |
| AC-007 | The retrieved topology and `topologyVersion` are internally consistent. | Snapshot consistency/recovery test. | Pending |
| AC-008 | `findCurrentVersion` or equivalent returns the recovered current `topologyVersion`. | Current version after recovery test. | Pending |
| AC-009 | Persisted topology survives repository/service recreation or equivalent process-boundary simulation. | Recreate adapter/service and retrieve test. | Pending |
| AC-010 | Recovery does not require SC-B replay. | No SC-B dependency; architecture/test inspection. | Pending |
| AC-011 | Recovery does not require SC-D rediscovery. | No SC-D dependency; architecture/test inspection. | Pending |
| AC-012 | Recovery does not require Hub, Projection, Session, Identity, Authority or Policy. | No such dependency in constructors/services/tests. | Pending |
| AC-013 | Canonical `deviceId` survives recovery unchanged. | Recovery identity test. | Pending |
| AC-014 | Canonical `endpointId` survives recovery unchanged. | Recovery identity test. | Pending |
| AC-015 | Provider refs survive recovery as metadata. | Provider refs recovery test. | Pending |
| AC-016 | Provider refs do not become canonical identity after recovery. | `providerDeviceId != deviceId`; `providerEndpointId != endpointId` after recovery. | Pending |
| AC-017 | A structural mutation can be recorded with `fromVersion` and `toVersion`. | Mutation record persistence test. | Pending |
| AC-018 | Mutation memory/ledger can be read back for the Habitat. | Mutation ledger readback test. | Pending |
| AC-019 | Operational device state can be stored without advancing `topologyVersion`. | Device state non-advancement test. | Pending |
| AC-020 | Operational device state can be recovered if included in seed scope. | Device state recovery test. | Pending |
| AC-021 | Endpoint health can be stored without advancing `topologyVersion`. | Endpoint health non-advancement test. | Pending |
| AC-022 | Endpoint health can be recovered if included in seed scope. | Endpoint health recovery test. | Pending |
| AC-023 | Pure in-memory-only storage is not claimed as persistence validation. | Implementation report states durable/file-backed/embedded seed storage. | Pending |
| AC-024 | Seed storage technology is not declared final production storage technology. | Implementation report states production storage undecided; ADR required before production. | Pending |
| AC-025 | Build/test command succeeds. | `mvn test` or repository build command. | Pending |

---

## 2. Expected Result Format

Implementation report must include:

| AC | Result | Evidence |
|---|---|---|
| AC-001 | PASS/FAIL | ... |
| AC-002 | PASS/FAIL | ... |
| AC-003 | PASS/FAIL | ... |
| AC-004 | PASS/FAIL | ... |
| AC-005 | PASS/FAIL | ... |
| AC-006 | PASS/FAIL | ... |
| AC-007 | PASS/FAIL | ... |
| AC-008 | PASS/FAIL | ... |
| AC-009 | PASS/FAIL | ... |
| AC-010 | PASS/FAIL | ... |
| AC-011 | PASS/FAIL | ... |
| AC-012 | PASS/FAIL | ... |
| AC-013 | PASS/FAIL | ... |
| AC-014 | PASS/FAIL | ... |
| AC-015 | PASS/FAIL | ... |
| AC-016 | PASS/FAIL | ... |
| AC-017 | PASS/FAIL | ... |
| AC-018 | PASS/FAIL | ... |
| AC-019 | PASS/FAIL | ... |
| AC-020 | PASS/FAIL | ... |
| AC-021 | PASS/FAIL | ... |
| AC-022 | PASS/FAIL | ... |
| AC-023 | PASS/FAIL | ... |
| AC-024 | PASS/FAIL | ... |
| AC-025 | PASS/FAIL | ... |

---

## 3. Validation Level

Target validation level:

```text
L4 — implementation attempted and local tests pass.
```

This acceptance map does not require L5 TCK validation.

---

## 4. Satisfaction Notes

AC-010 and AC-011 may be satisfied negatively if no SC-B or SC-D components are introduced.

Valid negative satisfaction means:

```text
- recovery does not call SC-B;
- recovery does not replay bus messages;
- recovery does not call SC-D;
- recovery does not rediscover devices through adapters.
```

AC-012 may be satisfied negatively if constructors/services/repositories do not require Hub, Projection, Session, Identity, Authority or Policy fields.

AC-019 and AC-021 require proving state/health persistence does not advance `topologyVersion`.

AC-023 requires more than pure in-memory repository behavior.

AC-024 requires an explicit report statement:

```text
Seed storage technology:
  <technology>

Production storage decision:
  not decided

ADR required before production:
  ADR-SOV-SC-C-STORAGE-TECH-001
```

Do not treat the seed technology as a final storage ADR.
