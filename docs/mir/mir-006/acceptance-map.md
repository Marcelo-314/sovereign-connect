# MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 — Acceptance Map

```text
Document ID: MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001-ACCEPTANCE-MAP
MIR: MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
MIR Version: v1.0.0-accepted
Materialization Unit: MU-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
Status: Evidence Support Artifact
Validation Target: L4
Date: 2026-05-10
```

---

## 0. Purpose

This file preserves the official AC-001 through AC-025 mapping for:

```text
MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 v0.2.0-candidate
```

It is part of the execution package:

```text
docs/mir/mir-006/
  MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001.md
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
| AC-001 | A Core Snapshot Query service, port or equivalent read boundary exists. | `CoreSnapshotQueryService` and/or `CoreSnapshotReadPort`. | Pending |
| AC-002 | The query boundary is read-only and does not mutate topology. | Tests/inspection showing query methods do not call mutation paths. | Pending |
| AC-003 | The query boundary does not advance `topologyVersion`. | Before/after version assertions around query calls. | Pending |
| AC-004 | Current snapshot can be queried by `habitatId`. | `findCurrentSnapshot(habitatId)` test. | Pending |
| AC-005 | Current snapshot response includes `topologyVersion`. | Snapshot response assertion. | Pending |
| AC-006 | `response.topologyVersion` equals snapshot envelope version when used, or aggregate version when direct. | Version equality assertion. | Pending |
| AC-007 | `snapshot.topologyVersion` equals `snapshot.topology.topologyVersion` when snapshot envelope is used. | Envelope/aggregate consistency assertion. | Pending |
| AC-008 | Current `topologyVersion` can be queried directly. | `findCurrentTopologyVersion(habitatId)` test. | Pending |
| AC-009 | Current `topologyVersion` query returns the same value as current snapshot query. | Query consistency assertion. | Pending |
| AC-010 | Device lookup by canonical `deviceId` succeeds for existing device. | Device lookup test. | Pending |
| AC-011 | Endpoint lookup by canonical `endpointId` succeeds for existing endpoint. | Endpoint lookup test. | Pending |
| AC-012 | Capability lookup succeeds for existing capability under canonical endpoint context. | Endpoint-context capability assertion. | Pending |
| AC-013 | Missing canonical `deviceId` or `endpointId` returns not-found. | Missing lookup test. | Pending |
| AC-014 | Provider refs are returned only as metadata when present. | Query result includes provider refs as metadata. | Pending |
| AC-015 | Provider refs are not treated as canonical `deviceId` or `endpointId`. | Provider ID lookup returns not-found / no fallback. | Pending |
| AC-016 | Operational device state can be queried if present in SC-C memory/persistence. | Query reads persisted device state. | Pending |
| AC-017 | Device state query does not advance `topologyVersion`. | Before/after version assertion. | Pending |
| AC-018 | Endpoint health can be queried if present in SC-C memory/persistence. | Query reads endpoint health after recovery. | Pending |
| AC-019 | Endpoint health query does not advance `topologyVersion`. | Before/after version assertion. | Pending |
| AC-020 | Query works after repository/service/query layer recreation using persisted state. | Recovery query test with new instances. | Pending |
| AC-021 | Query recovery does not require SC-B replay. | No SC-B dependency; architecture/test inspection. | Pending |
| AC-022 | Query recovery does not require SC-D rediscovery. | No SC-D dependency; architecture/test inspection. | Pending |
| AC-023 | Query does not require Hub, Projection, Session, Identity, Authority or Policy. | Constructor/service/test inspection. | Pending |
| AC-024 | Query result or recovered `topologyVersion` supports existing `validateTarget(...)` semantics without introducing a second target-resolution mechanism. | Test reusing `validateTarget(...)`; no mandatory `TargetResolutionSnapshot`. | Pending |
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

```text
L4 — implementation attempted and local tests pass.
```

This acceptance map does not require L5 TCK validation.

---

## 4. Satisfaction Notes

AC-012 may be satisfied through endpoint-context capability inspection.

A dedicated `findCapability(...)` method is not mandatory.

AC-016 MUST be satisfied through persisted state/read ports, not by reading `BaseTopologyService` internal state.

AC-018 MUST prove that `queryService.findEndpointHealth(...)` succeeds after repository/service/query recreation.

AC-020 must recreate repository/service/query layer instances.

Reusing the same `BaseTopologyService` instance does not satisfy AC-020.

AC-021 and AC-022 may be satisfied negatively if no SC-B or SC-D components are introduced.

AC-023 may be satisfied negatively if constructors/services/repositories do not require Hub, Projection, Session, Identity, Authority or Policy fields.

AC-024 must reuse existing `validateTarget(...)` or repository-local equivalent.

Introducing `TargetResolutionSnapshot` as mandatory seed mechanism is not required and should be avoided for this MU.
