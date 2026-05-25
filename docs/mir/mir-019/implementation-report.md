# implementation-report.md - MIR-019 Northbound Facade Seed

Document ID: IR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Version: v0.1.1
Status: Complete
Date: 2026-05-25
MU: MU-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Slot: MU-019
Branch: feat/sc-c-mir-019-northbound-facade-seed
Commit: 482fcb1

## 0. Implementation Summary

```text
Summary:
Implemented the first SC-C Northbound Facade seed as an in-process canonical
facade over existing SC-C query/application/observation surfaces.

Implementation scope:
Profile A - SC-C Local Observation
Profile B - selected Signal TemporalAct local runtime requests
In-process canonical facade only
```

## 1. Final File List

### 1.1 Main files added

```text
src/main/java/com/sovereign/connect/core/northbound/DefaultScCoreNorthboundFacade.java
src/main/java/com/sovereign/connect/core/northbound/NorthboundMapper.java
src/main/java/com/sovereign/connect/core/northbound/ScCoreNorthboundFacade.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundError.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundResponse.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundStatus.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundWarning.java
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundDeviceHealthView.java
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundEndpointHealthView.java
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundRecoveryStatusView.java
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundRuntimeStateView.java
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundTemporalRuntimeStatusView.java
src/main/java/com/sovereign/connect/core/northbound/temporal/NorthboundCancelTemporalActRequest.java
src/main/java/com/sovereign/connect/core/northbound/temporal/NorthboundCreateSignalTemporalActRequest.java
src/main/java/com/sovereign/connect/core/northbound/temporal/NorthboundTemporalActFilter.java
src/main/java/com/sovereign/connect/core/northbound/temporal/NorthboundTemporalActView.java
src/main/java/com/sovereign/connect/core/northbound/topology/NorthboundCapabilityView.java
src/main/java/com/sovereign/connect/core/northbound/topology/NorthboundDeviceView.java
src/main/java/com/sovereign/connect/core/northbound/topology/NorthboundEndpointView.java
src/main/java/com/sovereign/connect/core/northbound/topology/NorthboundLocationQuery.java
src/main/java/com/sovereign/connect/core/northbound/topology/NorthboundRoomView.java
src/main/java/com/sovereign/connect/core/northbound/topology/NorthboundTopologySnapshot.java
src/main/java/com/sovereign/connect/core/northbound/topology/NorthboundTopologyVersionView.java
src/main/java/com/sovereign/connect/core/northbound/topology/NorthboundZoneView.java
```

### 1.2 Test files added

```text
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeArchitectureTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeBehavioralTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeNegativeBoundaryTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeSpringContextTest.java
```

### 1.3 Existing files modified

```text
None.
```

## 2. Facade Method Summary

| Method | Implemented | Status behavior | Notes |
|---|---:|---|---|
| getTopologySnapshot | yes | OK / NOT_FOUND / INVALID_REQUEST | Maps CoreSnapshot to DTO. |
| getTopologyVersion | yes | OK / NOT_FOUND / INVALID_REQUEST | Maps TopologyVersion to DTO. |
| getDevice | yes | OK / NOT_FOUND / INVALID_CANONICAL_ID | Requires device.* canonical ID. |
| getEndpoint | yes | OK / NOT_FOUND / INVALID_CANONICAL_ID | Requires endpoint.* canonical ID. |
| listRooms | yes | OK / NOT_FOUND | Derived from CoreSnapshot.topology(). |
| listZones | yes | OK / NOT_FOUND | Derived from CoreSnapshot.topology(). |
| listDevices | yes | OK / NOT_FOUND | Derived from CoreSnapshot.topology(). |
| listEndpoints | yes | OK / NOT_FOUND | Derived from CoreSnapshot.topology(). |
| listDevicesLocatedIn | yes | OK / INVALID_CANONICAL_ID | Uses room.* or zone.* location IDs. |
| listEndpointsLocatedIn | yes | OK / INVALID_CANONICAL_ID | Uses room.* or zone.* location IDs. |
| getEndpointHealth | yes | OK / NOT_FOUND / INVALID_CANONICAL_ID | Durable health read path only. |
| getDeviceHealth | yes | UNKNOWN_PENDING_NORMALIZATION | No device health authority claimed. |
| getDeviceRuntimeState | yes | OK / NOT_FOUND / INVALID_CANONICAL_ID | Durable device state query surface. |
| getEndpointRuntimeState | yes | UNSUPPORTED_PROFILE | Deferred. |
| getRecoveryStatus | yes | UNSUPPORTED_PROFILE | Deferred explicit recovery read model. |
| getTemporalRuntimeStatus | yes | OK | Maps TemporalEngineHealth. |
| createSignalTemporalAct | yes | ACCEPTED / INVALID_REQUEST / INTERNAL_ERROR | Delegates to application port. |
| cancelTemporalAct | yes | CANCELLED / OK / NOT_FOUND / INVALID_REQUEST / INTERNAL_ERROR | Delegates to application port. |
| getTemporalAct | yes | OK / NOT_FOUND / INVALID_REQUEST | Delegates to observation port. |
| listTemporalActs | yes | OK | ACTIVE, TERMINAL, MISFIRED only. |

## 3. Delegation Map

| Facade method | Actual delegate | Result |
|---|---|---|
| getTopologySnapshot | CoreSnapshotQueryService.findCurrentSnapshot | DTO or NOT_FOUND |
| getTopologyVersion | CoreSnapshotQueryService.findCurrentTopologyVersion | DTO or NOT_FOUND |
| getDevice | CoreSnapshotQueryService.findDevice | DTO or NOT_FOUND |
| getEndpoint | CoreSnapshotQueryService.findEndpoint | DTO or NOT_FOUND |
| listRooms | CoreSnapshotQueryService.findCurrentSnapshot -> topology().rooms() | DTO list |
| listZones | CoreSnapshotQueryService.findCurrentSnapshot -> topology().zones() | DTO list |
| listDevices | CoreSnapshotQueryService.findCurrentSnapshot -> topology().devices() | DTO list |
| listEndpoints | CoreSnapshotQueryService.findCurrentSnapshot -> topology().endpoints() | DTO list |
| listDevicesLocatedIn | CoreSnapshotQueryService.findLocatedDevices | DTO list |
| listEndpointsLocatedIn | CoreSnapshotQueryService.findLocatedEndpoints | DTO list |
| getEndpointHealth | CoreSnapshotQueryService.findEndpointHealth | durable endpoint health DTO |
| getDeviceHealth | none | UNKNOWN_PENDING_NORMALIZATION |
| getDeviceRuntimeState | CoreSnapshotQueryService.findDeviceState | runtime state DTO |
| getEndpointRuntimeState | none | UNSUPPORTED_PROFILE |
| getTemporalRuntimeStatus | TemporalEngineHealth | runtime status DTO |
| getRecoveryStatus | none | UNSUPPORTED_PROFILE |
| createSignalTemporalAct | TemporalActApplicationPort.createSignalTemporalAct | mapped result envelope |
| cancelTemporalAct | TemporalActApplicationPort.cancelTemporalAct | mapped result envelope |
| getTemporalAct | TemporalActObservationPort.findById | DTO or NOT_FOUND |
| listTemporalActs | TemporalActObservationPort listActive/listTerminal/listMisfired | DTO list |

## 4. Status Mapping Table

| Source result / condition | ScNorthboundStatus | Notes |
|---|---|---|
| Successful observation | OK | Payload present. |
| Missing topology/entity/health/act | NOT_FOUND | Error payload only. |
| Invalid request | INVALID_REQUEST | Null request, blank habitat, missing required Temporal fields. |
| Invalid canonical ID | INVALID_CANONICAL_ID | Provider-native/non-canonical lookup key rejected before delegate call. |
| Unsupported seed profile | UNSUPPORTED_PROFILE | Endpoint runtime state and recovery status. |
| Device health | UNKNOWN_PENDING_NORMALIZATION | Required first-seed behavior. |
| CreateSignalTemporalActResult.Accepted | ACCEPTED | Payload present. |
| CreateSignalTemporalActResult.IdempotentReplay | ACCEPTED + warning | Warning IDEMPOTENT_REPLAY. |
| CreateSignalTemporalActResult.Rejected | INVALID_REQUEST | Rejection code/message in error. |
| CreateSignalTemporalActResult.Failed | INTERNAL_ERROR | Failure code/message in error. |
| CancelTemporalActResult.Cancelled | CANCELLED | Payload present. |
| CancelTemporalActResult.AlreadyTerminal | OK + warning | Warning ALREADY_TERMINAL. |
| CancelTemporalActResult.NotFound | NOT_FOUND | Error payload only. |
| CancelTemporalActResult.IdempotentReplay | CANCELLED + warning | Warning IDEMPOTENT_REPLAY. |
| CancelTemporalActResult.Rejected | INVALID_REQUEST | Rejection code/message in error. |
| CancelTemporalActResult.Failed | INTERNAL_ERROR | Failure code/message in error. |

## 5. Required Strategy Confirmations

### 5.1 DeviceHealth strategy

```text
Expected: getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION.
Actual: getDeviceHealth always returns UNKNOWN_PENDING_NORMALIZATION with no payload.
```

### 5.2 EndpointHealth authority

```text
Expected: getEndpointHealth delegates to CoreSnapshotQueryService.findEndpointHealth(...).
Actual: DefaultScCoreNorthboundFacade calls queryService.findEndpointHealth(...) directly.
It does not use EndpointSnapshot.health() and does not read aggregate fallback health.
```

### 5.3 TemporalAct filter semantics

```text
Expected: NorthboundTemporalActFilter supports ACTIVE, TERMINAL and MISFIRED only;
null/default maps to ACTIVE; ALL_SUPPORTED/combined listing remains deferred.
Actual: enum supports ACTIVE, TERMINAL and MISFIRED only. Null filter or null mode
maps to ACTIVE. TERMINAL and MISFIRED normalize invalid maxResults to 50 and cap
large values at 500.
```

### 5.4 Spring wiring

```text
Expected: DefaultScCoreNorthboundFacade registered as @Service unless @Bean is justified.
Actual: DefaultScCoreNorthboundFacade is registered with @Service.
Justification if @Bean used: not applicable.
```

## 6. Negative Boundary Scan Summary

```text
No HTTP/Spring Web imports: PASS
No MCP imports/classes: PASS
No gRPC/ConnectRPC imports/classes: PASS
No WebSocket imports/classes: PASS
No GraphQL imports/classes: PASS
No NATS/JetStream imports/classes: PASS
No SC-D adapter imports/classes: PASS
No persistence adapter imports in northbound package: PASS
No JdbcTemplate/DataSource in northbound package: PASS
No discovery methods in ScCoreNorthboundFacade: PASS
No Effective/Projected/Surface/Session/Policy/Authority DTO naming: PASS
```

## 7. Test Execution

Command:

```bash
mvn test
```

Result:

```text
[INFO] Tests run: 188, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Surefire summary:

```text
Tests run: 188
Failures: 0
Errors: 0
Skipped: 0
```

## 8. Acceptance Criteria Result Table

| AC | Result | Evidence |
|---|---|---|
| AC-019-001 | PASS | ScCoreNorthboundFacade introduced under core/northbound. |
| AC-019-002 | PASS | DefaultScCoreNorthboundFacade introduced over approved collaborators. |
| AC-019-003 | PASS | ScNorthboundResponse introduced. |
| AC-019-004 | PASS | ScNorthboundStatus includes required vocabulary. |
| AC-019-005 | PASS | Facade returns dedicated Northbound DTOs. |
| AC-019-006 | PASS | Profile A topology observation methods implemented and tested. |
| AC-019-007 | PASS | Selected Signal TemporalAct Profile B methods implemented and tested. |
| AC-019-008 | PASS | No discovery methods or execution added. |
| AC-019-009 | PASS | No Effective/Product-facing DTO concepts added. |
| AC-019-010 | PASS | Topology/entity reads delegate through CoreSnapshotQueryService. |
| AC-019-011 | PASS | Facade does not inject repositories, JdbcTemplate, DataSource or BaseTopologyService. |
| AC-019-012 | PASS | List methods derive from CoreSnapshot.topology(). |
| AC-019-013 | PASS | getEndpointHealth uses findEndpointHealth directly. |
| AC-019-014 | PASS | getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION. |
| AC-019-015 | PASS | getDeviceRuntimeState uses findDeviceState. |
| AC-019-016 | PASS | getEndpointRuntimeState returns UNSUPPORTED_PROFILE. |
| AC-019-017 | PASS | getTemporalRuntimeStatus maps TemporalEngineHealth. |
| AC-019-018 | PASS | getRecoveryStatus returns UNSUPPORTED_PROFILE. |
| AC-019-019 | PASS | createSignalTemporalAct delegates to TemporalActApplicationPort. |
| AC-019-020 | PASS | cancelTemporalAct delegates to TemporalActApplicationPort. |
| AC-019-021 | PASS | get/list TemporalActs delegate to TemporalActObservationPort. |
| AC-019-022 | PASS | Temporal request DTOs exclude Session/Identity/Authority/Policy/Surface fields. |
| AC-019-023 | PASS | Architecture tests scan no HTTP/Spring Web imports. |
| AC-019-024 | PASS | Architecture tests scan no MCP/gRPC/ConnectRPC/WebSocket/GraphQL. |
| AC-019-025 | PASS | Architecture tests scan no NATS/JetStream. |
| AC-019-026 | PASS | Architecture tests scan no SC-D adapter invocation. |
| AC-019-027 | PASS | Architecture tests scan no persistence adapter imports in northbound. |
| AC-019-028 | PASS | Retained open items recorded below. |
| AC-019-029 | PASS | NorthboundFacadeBehavioralTest covers topology, health/runtime and TemporalAct. |
| AC-019-030 | PASS | NorthboundFacadeArchitectureTest and NegativeBoundaryTest cover forbidden leakage. |
| AC-019-031 | PASS | NorthboundFacadeSpringContextTest verifies Spring wiring. |
| AC-019-032 | PASS | mvn test passed. |
| AC-019-033 | PASS | This report records final file list, test result and deviations. |

## 8.1 T-3 Test Rename Traceability

```text
Implemented with corrected method name:
createSignalTemporalActMapsAllResultVariantsCorrectly

The old copy-paste name createSignalTemporalActMapsCancelledVariantsCorrectly
was not used.
```

## 9. Retained Open Items Required by AC-019-028

```text
1. getEndpointRuntimeState -> UNSUPPORTED_PROFILE unless a durable endpoint runtime-state port exists.
2. getRecoveryStatus -> UNSUPPORTED_PROFILE until an explicit recovery read model exists.
3. DeviceHealth normalized authority / derivation debt remains open.
```

## 10. Deviations

```text
No scope-broadening deviations.

Chosen behavior: getRecoveryStatus returns UNSUPPORTED_PROFILE instead of a proxy
view. This follows the CSA-preferred option and avoids fabricating a recovery
authority from TemporalEngineHealth.
```

## 11. Final Implementation Verdict

```text
Candidate verdict:
Implementation complete. Full local validation passed.

Recommended MU status:
Validated L4 candidate after review.
```
