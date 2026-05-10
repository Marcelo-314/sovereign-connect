# Codex Prompt — MIR-010 — SC-C Kernel Hardening Seed

```text
MIR: MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001
MIR Version: v1.0.0-accepted
MU: MU-SOV-SC-C-KERNEL-HARDENING-SEED-001
Execution Status: Authorized
```

---

## Read First

Read `docs/mir/mir-010/context.md`. It contains:
- the exact existing API surface;
- the inherited coverage map (which ACs are already covered by which tests);
- the gap analysis (what MIR-010 actually needs to add);
- the durable H2 wiring pattern.

Use `docs/mir/mir-010/acceptance-map.md` for AC numbering.

---

## Goal

Add ONE composed durable integration test that validates the SC-C kernel
as a whole across MU-001, MU-002, MU-004 and MU-006.

The primary deliverable is:
1. `ScCoreKernelHardeningTest` — composed durable test
2. Implementation report with AC classification table

Most ACs are already covered by inherited tests (see context §3).
Do not duplicate them. Reference them in the report.

---

## What to build

### 1. Verify existing baseline

Run `mvn test` before writing anything.

Confirm 22 tests pass. If any fail, stop and report.

### 2. Add ScCoreKernelHardeningTest

Create:
```text
src/test/java/com/sovereign/connect/core/topology/ScCoreKernelHardeningTest.java
```

This test exercises the full composed kernel in a single durable test.

Use the durable wiring pattern from context §5 and the assertion groups below.
The older 22-step procedural flow is superseded by this prompt.

Key assertions the test MUST make:

**topologyVersion stability**
```text
- capture version before all queries
- run findCurrentSnapshot, findDevice, findEndpoint
- assert version unchanged after queries
```

**Recovery cross-MU composition**
```text
- after recreation: findCurrentSnapshot returns correct topology
- recovered topologyVersion == recovered snapshot.topologyVersion
- recovered topologyVersion == recovered topology.topologyVersion
- canonical deviceId unchanged
- canonical endpointId unchanged
```

**Canonical lookup without provider fallback**
```text
- findDevice with providerDeviceId (e.g. "tuya.device.abc") → empty
- findDevice with canonical deviceId → present
- provider refs in result are metadata (providerDeviceId != deviceId)
```

**Query reads durable state, not service memory**
```text
- device state recovered from persistence, not from BaseTopologyService
- endpoint health recovered from persistence
```

**Target validation continuity**
```text
- validateTarget with recovered version → VALID for existing target
```

**Boundary assertion**
```text
- CoreSnapshotQueryService has no BaseTopologyService field (reflection)
- no SC-B/SC-D/Projection/Authority in constructor or field surface
```

### 3. Produce implementation report

Create `docs/mir/mir-010/implementation-report.md`.

The report MUST include the classification table per context §3:

```text
| AC     | Classification             | Evidence                                           |
|--------|----------------------------|----------------------------------------------------|
| AC-001 | Inherited                  | 22 prior tests pass                                |
| AC-002 | New                        | ScCoreKernelHardeningTest created                  |
| AC-003 | New                        | This classification table                          |
| AC-004 | Inherited                  | BaseTopologyServiceTest#...                        |
| ...    | ...                        | ...                                                |
| AC-011 | Inherited + consolidated   | CoreSnapshotQuerySeedTest + new composed test      |
| ...    | ...                        | ...                                                |
| AC-025 | New                        | mvn test green                                     |
```

Include the required state-source statement:
```text
CoreSnapshotQueryService state source: persistence/repository ports
BaseTopologyService.findDeviceState used by query service: no
Target validation strategy: reused validateTarget(...)
New feature scope introduced: no
```

---

## What NOT to build

Do not duplicate existing tests without cross-MU value.
Do not add Topology Materialization, TemporalActs, SC-B, SC-D, Projection,
REST/gRPC, MCP, TargetResolutionSnapshot, or any feature from negative scope.

If any negative-scope item becomes necessary, STOP and report it.

---

## Build

```bash
mvn test
```

Result must show at least 23 tests passing (22 existing + 1 new hardening test).

---

## Stop conditions

Stop and report if:
- hardening requires new SC-C feature behavior
- hardening requires SC-B/SC-D/Projection/Hub/Authority
- provider ID satisfies canonical lookup
- query advances topologyVersion
- query depends on BaseTopologyService internal memory
- recovery requires retaining old Java instances
- topologyVersion mismatches recovered topology
- existing 22 tests fail
- hardening duplicates tests without classification or cross-MU value
