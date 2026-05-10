# MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 — Acceptance Map

```text
Document ID: MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001-ACCEPTANCE-MAP
MIR: MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001
MIR Version: v1.0.0-accepted
Materialization Unit: MU-SOV-SC-C-KERNEL-HARDENING-SEED-001
Operational Slot: MU-010
Status: Evidence Support Artifact
Validation Target: L4
Date: 2026-05-10
```

---

## 0. Purpose

This file preserves the official AC-001 through AC-025 mapping for:

```text
MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 v1.0.0-accepted
```

It is part of the execution package:

```text
docs/mir/mir-010/
  MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001.md
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report.md
```

This file must be used by the implementation agent when reporting acceptance results.

Do not invent new AC numbering.

---

## 1. Coverage Classifications

Allowed classifications:

```text
Inherited
Inherited + consolidated
New
Boundary guard
```

Equivalent expanded terminology:

```text
Inherited:
  already covered by accepted MIR tests with no new test required.

Inherited + consolidated:
  covered before, but rechecked through the composed hardening test.

New:
  new artifact or new evidence added by MIR-010.

Boundary guard:
  evidence that forbidden dependencies/responsibilities are absent.
```

The implementation report must classify every AC.

---

## 2. Acceptance Criteria Map

| AC | Criterion | Expected Classification | Required Evidence | Result |
|---|---|---|---|---|
| AC-001 | All existing MU-001, MU-002, MU-004 and MU-006 tests still pass. | Inherited | Full `mvn test` report including prior tests. | Pending |
| AC-002 | A dedicated SC-C kernel hardening test suite exists. | New | `ScCoreKernelHardeningTest` or equivalent. | Pending |
| AC-003 | Implementation report classifies each AC. | New | Coverage classification table. | Pending |
| AC-004 | Base Topology morphology is regression-tested. | Inherited | `BaseTopologyServiceTest#createsAndRetrievesFullBaseTopologyHierarchy`. | Pending |
| AC-005 | DeviceNode remains stable topological container. | Inherited | `BaseTopologyServiceTest#supportsOneDeviceWithMultipleFirstClassEndpoints`. | Pending |
| AC-006 | EndpointNode remains addressable operational locus. | Inherited | `BaseTopologyServiceTest#supportsOneDeviceWithMultipleFirstClassEndpoints`. | Pending |
| AC-007 | CapabilityNode remains canonical affordance. | Inherited | `BaseTopologyServiceTest#supportsOneEndpointWithMultipleCapabilities`. | Pending |
| AC-008 | Structural mutation advances topologyVersion. | Inherited | `TopologyVersionHardeningTest` structural mutation coverage. | Pending |
| AC-009 | Rejected mutation does not advance topologyVersion. | Inherited | `TopologyVersionHardeningTest` rejected mutation coverage. | Pending |
| AC-010 | State update does not advance topologyVersion. | Inherited | `TopologyVersionHardeningTest` state update non-advance coverage. | Pending |
| AC-011 | Core Snapshot Query does not advance topologyVersion. | Inherited + consolidated | `CoreSnapshotQuerySeedTest` plus composed hardening query assertions. | Pending |
| AC-012 | Recovered topologyVersion equals recovered snapshot/aggregate version. | Inherited + consolidated | `PersistenceMemorySeedTest`, `CoreSnapshotQuerySeedTest`, composed hardening recovery assertions. | Pending |
| AC-013 | Recovery preserves canonical deviceId. | Inherited + consolidated | `PersistenceMemorySeedTest`, `CoreSnapshotQuerySeedTest`, composed hardening recovery assertions. | Pending |
| AC-014 | Recovery preserves canonical endpointId. | Inherited + consolidated | `PersistenceMemorySeedTest`, `CoreSnapshotQuerySeedTest`, composed hardening recovery assertions. | Pending |
| AC-015 | Provider refs survive recovery as metadata. | Inherited + consolidated | `PersistenceMemorySeedTest`, composed hardening provider metadata assertion. | Pending |
| AC-016 | Provider refs are not accepted as canonical IDs. | Inherited + consolidated | `CoreSnapshotQuerySeedTest`, composed hardening provider lookup assertion. | Pending |
| AC-017 | CoreSnapshotQueryService reads persisted state, not BaseTopologyService internal state. | Inherited + consolidated | `CoreSnapshotQuerySeedTest` reflection check plus composed hardening durable state assertion. | Pending |
| AC-018 | CoreSnapshotQueryService does not depend on retaining the same BaseTopologyService instance. | Inherited + consolidated | `CoreSnapshotQuerySeedTest` recreation pattern plus composed hardening recreation pattern. | Pending |
| AC-019 | SC-C kernel tests require no SC-B replay. | Boundary guard | No SC-B dependency in test setup/imports/report. | Pending |
| AC-020 | SC-C kernel tests require no SC-D rediscovery. | Boundary guard | No SC-D dependency in test setup/imports/report. | Pending |
| AC-021 | SC-C kernel tests require no Projection or Effective View. | Boundary guard | No Projection/Effective View dependency. | Pending |
| AC-022 | SC-C kernel tests require no Session, Identity, Authority or Policy. | Boundary guard | No such dependency in constructors/tests. | Pending |
| AC-023 | validateTarget(...) remains usable after persistence/recovery/query. | Inherited + consolidated | `CoreSnapshotQuerySeedTest` plus composed hardening target validation assertion. | Pending |
| AC-024 | No new feature scope is introduced. | New / Boundary guard | Implementation report states no feature scope; diff inspection. | Pending |
| AC-025 | Build/test command succeeds. | New | `mvn test` success, at least 23 tests passing. | Pending |

---

## 3. High-Value ACs

The implementation report must explicitly discuss:

```text
AC-011
AC-017
AC-018
AC-023
```

These were already directly covered by `CoreSnapshotQuerySeedTest`, but MIR-010 consolidates them as kernel-level regression evidence in the composed durable hardening test.

---

## 4. Expected Result Format

Implementation report must include:

| AC | Result | Classification | Evidence |
|---|---|---|---|
| AC-001 | PASS/FAIL | ... | ... |
| AC-002 | PASS/FAIL | ... | ... |
| AC-003 | PASS/FAIL | ... | ... |
| AC-004 | PASS/FAIL | ... | ... |
| AC-005 | PASS/FAIL | ... | ... |
| AC-006 | PASS/FAIL | ... | ... |
| AC-007 | PASS/FAIL | ... | ... |
| AC-008 | PASS/FAIL | ... | ... |
| AC-009 | PASS/FAIL | ... | ... |
| AC-010 | PASS/FAIL | ... | ... |
| AC-011 | PASS/FAIL | ... | ... |
| AC-012 | PASS/FAIL | ... | ... |
| AC-013 | PASS/FAIL | ... | ... |
| AC-014 | PASS/FAIL | ... | ... |
| AC-015 | PASS/FAIL | ... | ... |
| AC-016 | PASS/FAIL | ... | ... |
| AC-017 | PASS/FAIL | ... | ... |
| AC-018 | PASS/FAIL | ... | ... |
| AC-019 | PASS/FAIL | ... | ... |
| AC-020 | PASS/FAIL | ... | ... |
| AC-021 | PASS/FAIL | ... | ... |
| AC-022 | PASS/FAIL | ... | ... |
| AC-023 | PASS/FAIL | ... | ... |
| AC-024 | PASS/FAIL | ... | ... |
| AC-025 | PASS/FAIL | ... | ... |

---

## 5. Validation Level

```text
L4 — implementation attempted and local tests pass.
```

This acceptance map does not require L5 TCK validation.
