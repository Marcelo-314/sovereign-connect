# MIR-010 Implementation Report

## 1. Summary

MIR-010 adds one composed durable SC-C kernel hardening test that wires MU-001 topology creation, MU-002 topologyVersion semantics, MU-004 H2 recovery, and MU-006 Core Snapshot Query into a single regression path. No new feature behavior was introduced; this increment consolidates accepted behavior and reports AC classification.

## 2. Files Changed

- `src/test/java/com/sovereign/connect/core/topology/ScCoreKernelHardeningTest.java`
- `docs/mir/mir-010/implementation-report.md`

## 3. Test Added

- `ScCoreKernelHardeningTest#composedDurableKernelPreservesVersionIdentityStateHealthAndQueryBoundariesAfterRecovery` - composes durable topology mutation, recovery, query, canonical lookup, persisted state/health, target validation, and boundary checks in one kernel-level test.

## 4. Baseline Verification

Baseline was run before edits as required:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 5. Final Validation

Command:

```bash
mvn test
```

Result:

```text
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 6. AC Classification Table

| AC | Result | Classification | Evidence |
|---|---|---|---|
| AC-001 | PASS | Inherited | Full `mvn test` includes all prior MU-001, MU-002, MU-004 and MU-006 tests. |
| AC-002 | PASS | New | `ScCoreKernelHardeningTest` added. |
| AC-003 | PASS | New | This implementation report classifies AC-001 through AC-025. |
| AC-004 | PASS | Inherited | `BaseTopologyServiceTest#createsAndRetrievesFullBaseTopologyHierarchy`. |
| AC-005 | PASS | Inherited | `BaseTopologyServiceTest#supportsOneDeviceWithMultipleFirstClassEndpoints`. |
| AC-006 | PASS | Inherited | `BaseTopologyServiceTest#supportsOneDeviceWithMultipleFirstClassEndpoints`. |
| AC-007 | PASS | Inherited | `BaseTopologyServiceTest#supportsOneEndpointWithMultipleCapabilities`. |
| AC-008 | PASS | Inherited | `TopologyVersionHardeningTest` structural mutation advancement coverage. |
| AC-009 | PASS | Inherited | `TopologyVersionHardeningTest#ac005RejectedStructuralMutationDoesNotAdvanceTopologyVersion`. |
| AC-010 | PASS | Inherited | `TopologyVersionHardeningTest#ac006DeviceStateUpdateDoesNotAdvanceTopologyVersion` and `ac007EndpointHealthUpdateDoesNotAdvanceTopologyVersion`. |
| AC-011 | PASS | Inherited + consolidated | `CoreSnapshotQuerySeedTest` plus `ScCoreKernelHardeningTest` capture version before queries, query snapshot/device/endpoint, and assert unchanged version. |
| AC-012 | PASS | Inherited + consolidated | `PersistenceMemorySeedTest`, `CoreSnapshotQuerySeedTest`, and new hardening test assert recovered version equals snapshot and aggregate version. |
| AC-013 | PASS | Inherited + consolidated | `PersistenceMemorySeedTest`, `CoreSnapshotQuerySeedTest`, and new hardening test assert canonical `deviceId` survives recovery. |
| AC-014 | PASS | Inherited + consolidated | `PersistenceMemorySeedTest`, `CoreSnapshotQuerySeedTest`, and new hardening test assert canonical `endpointId` survives recovery. |
| AC-015 | PASS | Inherited + consolidated | `PersistenceMemorySeedTest` plus new hardening test assert provider refs survive as metadata. |
| AC-016 | PASS | Inherited + consolidated | `CoreSnapshotQuerySeedTest` plus new hardening test assert providerDeviceId lookup returns empty. |
| AC-017 | PASS | Inherited + consolidated | `CoreSnapshotQuerySeedTest` reflection check plus new hardening durable state assertion prove query reads persisted state, not mutation service memory. |
| AC-018 | PASS | Inherited + consolidated | `CoreSnapshotQuerySeedTest` and new hardening test discard and recreate repository/query/service instances. |
| AC-019 | PASS | Boundary guard | New hardening reflection checks and setup include no SC-B dependency or replay. |
| AC-020 | PASS | Boundary guard | New hardening reflection checks and setup include no SC-D dependency or rediscovery. |
| AC-021 | PASS | Boundary guard | New hardening reflection checks and report confirm no Projection or Effective View dependency. |
| AC-022 | PASS | Boundary guard | New hardening constructor/field checks and setup include no Session, Identity, Authority or Policy dependency. |
| AC-023 | PASS | Inherited + consolidated | `CoreSnapshotQuerySeedTest` plus new hardening test reuse `BaseTopologyService.validateTarget(...)` after recovery. |
| AC-024 | PASS | New / Boundary guard | No new feature scope introduced; only one composed durable regression test and report were added. |
| AC-025 | PASS | New | `mvn test` succeeds with 23 tests passing. |

## 7. High-Value AC Discussion

AC-011 is consolidated by the new test through a kernel-level before/after version assertion around `findCurrentSnapshot`, `findDevice`, and `findEndpoint`.

AC-017 is consolidated by proving recovered device state comes from the durable H2 repository through `CoreSnapshotQueryService`, while reflection confirms the query service has no `BaseTopologyService` field.

AC-018 is consolidated by explicitly discarding repository, mutation service, and query service instances before recreating them from the same H2 file.

AC-023 is consolidated by creating a new `BaseTopologyService` only after recovery and reusing `validateTarget(...)` with the recovered topologyVersion.

## 8. State-Source Statement

CoreSnapshotQueryService state source: persistence/repository ports.

BaseTopologyService.findDeviceState used by query service: no.

Target validation strategy: reused `validateTarget(...)`.

New feature scope introduced: no.

## 9. Boundary/Invariants

- topologyVersion stability across queries - preserved.
- Recovery cross-MU composition - preserved.
- Canonical lookup without provider fallback - preserved.
- Query reads durable state, not service memory - preserved.
- Target validation continuity - preserved.
- CoreSnapshotQueryService has no BaseTopologyService field - preserved.
- No SC-B/SC-D/Projection/Authority dependency in query constructor or field surface - preserved.

## 10. Deviations From Scope

None.

No Topology Materialization, TemporalActs, Adapter Lifecycle, SC-B transport, SC-D protocol, REST/gRPC/WebSocket API, MCP facade, Projection, Effective View, Session/Identity/Authority/Policy, TargetResolutionSnapshot, production schema, or storage ADR was introduced.

## 11. Failure Signals Encountered

None.

No stop condition was reached.

## 12. Assumptions Made

- Existing 22 tests are accepted inherited evidence as stated by `docs/mir/mir-010/context.md`.
- The new hardening test should consolidate behavior rather than duplicate every inherited assertion.
- Durable H2 wiring remains the accepted local recovery pattern.

## 13. Corpus Issues Discovered

None.

The MIR-010 context and acceptance map were present and consistent.

## 14. Recommended Next MU

Define the next external boundary only after deciding whether the kernel read path should be exposed through an application service, transport adapter, or diagnostic-only interface, while keeping Projection and command dispatch outside the Core Snapshot Query seed.
