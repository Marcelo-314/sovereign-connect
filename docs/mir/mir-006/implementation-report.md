# MIR-006 Implementation Report

## 1. Summary

This increment adds a canonical read-only Core Snapshot query boundary over persisted SC-C state. The query service composes Base Topology, topologyVersion, persisted device state, and persisted endpoint health after repository/service/query recreation while preserving canonical ID lookup semantics and read/write separation.

## 2. Files Changed

- `src/main/java/com/sovereign/connect/core/topology/port/CoreSnapshotReadPort.java`
- `src/main/java/com/sovereign/connect/core/topology/query/CoreSnapshot.java`
- `src/main/java/com/sovereign/connect/core/topology/query/DeviceSnapshot.java`
- `src/main/java/com/sovereign/connect/core/topology/query/EndpointSnapshot.java`
- `src/main/java/com/sovereign/connect/core/topology/query/CoreSnapshotQueryService.java`
- `src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java`
- `src/test/java/com/sovereign/connect/core/topology/CoreSnapshotQuerySeedTest.java`
- `docs/mir/mir-006/implementation-report.md`

## 3. Query Types Added

- `CoreSnapshot`
- `DeviceSnapshot`
- `EndpointSnapshot`

## 4. Query Service/Port Added

- Added `CoreSnapshotReadPort`.
- Added `CoreSnapshotQueryService`.
- Updated `H2BaseTopologyRepository` to implement `CoreSnapshotReadPort`.
- Added `H2BaseTopologyRepository.findTopology(String)` as an alias for `findByHabitatId(String)`.

## 5. Persistence Reads Used

`CoreSnapshotQueryService` reads through `CoreSnapshotReadPort`:

- `findSnapshot`
- `findCurrentVersion`
- `findTopology`
- `findDeviceState`
- `findEndpointHealth`

It does not depend on `BaseTopologyService` and does not read `BaseTopologyService.findDeviceState(...)`.

## 6. Tests Added

- `CoreSnapshotQuerySeedTest#ac001ToAc025CoreSnapshotQueryComposesRecoveredCanonicalReadModel` - validates recovery, composed snapshot query, canonical device/endpoint lookup, capability lookup via endpoint context, provider-ID not-found behavior, persisted state/health reads, no version advancement, and reuse of `validateTarget(...)`.

Existing test classes still pass:

- `BaseTopologyServiceTest` - 8 tests.
- `TopologyVersionHardeningTest` - 12 tests.
- `PersistenceMemorySeedTest` - 1 test.

## 7. Acceptance Results

| AC | Result | Evidence |
|---|---|---|
| AC-001 | PASS | `CoreSnapshotReadPort` and `CoreSnapshotQueryService` added. |
| AC-002 | PASS | Query service exposes read methods only and has no mutation methods; test reflects no mutation method names. |
| AC-003 | PASS | Test captures version before and after query calls and asserts equality. |
| AC-004 | PASS | `findCurrentSnapshot("habitat-001")` succeeds after recovery. |
| AC-005 | PASS | `CoreSnapshot.topologyVersion()` asserted in test. |
| AC-006 | PASS | Query response version equals direct current version and aggregate version. |
| AC-007 | PASS | Test asserts H2 snapshot envelope version equals aggregate topology version. |
| AC-008 | PASS | `findCurrentTopologyVersion("habitat-001")` succeeds. |
| AC-009 | PASS | Direct current version equals current snapshot version. |
| AC-010 | PASS | `findDevice("habitat-001", "device.light.kitchen-main")` succeeds. |
| AC-011 | PASS | `findEndpoint("habitat-001", "endpoint.light.kitchen-main")` succeeds. |
| AC-012 | PASS | Capability IDs are asserted through `findEndpoint(...).endpoint().capabilities()`. |
| AC-013 | PASS | Missing canonical device and endpoint lookups return `Optional.empty()`. |
| AC-014 | PASS | Provider refs are present on returned device/endpoint nodes as metadata. |
| AC-015 | PASS | Provider-native IDs used as lookup keys return not-found; no fallback occurs. |
| AC-016 | PASS | `findDeviceState(...)` reads persisted `power=on`, `level=75` state. |
| AC-017 | PASS | Version before and after state query remains unchanged. |
| AC-018 | PASS | `findEndpointHealth(...)` returns persisted `DEGRADED` health after recovery. |
| AC-019 | PASS | Version before and after health query remains unchanged. |
| AC-020 | PASS | Test discards old repository/service/query and recreates new instances against same H2 file. |
| AC-021 | PASS | No SC-B dependency or replay path introduced; reflection asserts no SC-B surface. |
| AC-022 | PASS | No SC-D dependency or rediscovery path introduced; reflection asserts no SC-D surface. |
| AC-023 | PASS | Query service has no Hub/Projection/Session/Identity/Authority/Policy dependency. |
| AC-024 | PASS | Test creates new `BaseTopologyService` only to reuse existing `validateTarget(...)`; no `TargetResolutionSnapshot` introduced. |
| AC-025 | PASS | `mvn test` succeeds with 22 tests, 0 failures, 0 errors, 0 skipped. |

## 8. Invariants Preserved

- SC-C owns query semantics - preserved.
- query returns Base Topology not Effective View - preserved.
- query does not perform Projection - preserved.
- topology responses include topologyVersion - preserved.
- version consistency (response == snapshot == aggregate) - preserved.
- canonical lookup uses canonical IDs - preserved.
- provider refs remain metadata - preserved.
- state query does not advance version - preserved.
- health query does not advance version - preserved.
- query recovery independent of SC-B/SC-D - preserved.
- query does not require Projection/Session/Authority/Policy - preserved.
- read/write separation - preserved.
- validateTarget reused - preserved.

## 9. Deviations From Scope

None.

No Projection, Effective View, authority/session filtering, transport API, SC-B transport, MCP facade, historical snapshots, command dispatch, action execution, real SC-D adapter calls, provider rediscovery, production query DTO, external wire ABI, or mandatory `TargetResolutionSnapshot` was introduced.

## 10. Failure Signals Encountered

None.

No stop condition was reached.

## 11. Assumptions Made

- `H2BaseTopologyRepository` remains the durable read source introduced by the persistence seed.
- Capability lookup for this MU is satisfied through endpoint-context inspection, not a dedicated query method.
- State and health maps in `CoreSnapshot` include known persisted values for devices/endpoints present in the recovered topology.

## 12. Corpus Issues Discovered

None blocking.

The MIR-006 context and acceptance map were present and used for AC numbering.

## 13. State Source Statement

CoreSnapshotQueryService reads from: persistence/repository ports.

BaseTopologyService.findDeviceState used: no.

Target validation: reused `BaseTopologyService.validateTarget(...)`.

## 14. Recommended Next MU

Define the transport/API boundary that can expose the read-only Core Snapshot query service without adding Projection, actor filtering, command dispatch, or provider rediscovery semantics.

## Build Result

Command:

```bash
mvn test
```

Result:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
