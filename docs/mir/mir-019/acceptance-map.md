# acceptance-map.md — MIR-019 Northbound Facade Seed

Document ID: AM-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Version: v0.1.0
Status: Execution package acceptance map
Date: 2026-05-24
MU: MU-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Slot: MU-019

---

## 0. Purpose

This file maps `AC-019-001` through `AC-019-033` to verifiable implementation evidence.

Acceptance requires:

```text
mvn test passes
all required behavioral tests pass
all negative/source-level architecture tests pass
Spring wiring tests pass if Spring bean registration is used
implementation-report.md records all required evidence and retained open items
```

---

## 1. Acceptance criteria map

| AC | Criterion | Required evidence | Expected result |
|---|---|---|---|
| AC-019-001 | Introduces `ScCoreNorthboundFacade` as in-process canonical facade. | Source file exists under `core/northbound`; interface is not annotated as HTTP/gRPC/MCP/etc. | PASS |
| AC-019-002 | Introduces `DefaultScCoreNorthboundFacade` over existing SC-C services/ports. | Source file exists; constructor injects approved collaborators only. | PASS |
| AC-019-003 | Introduces dedicated local `ScNorthboundResponse` envelope. | `ScNorthboundResponse.java` exists and is used by facade methods. | PASS |
| AC-019-004 | Introduces `ScNorthboundStatus` with required status vocabulary. | Enum contains `OK`, `CREATED`, `ACCEPTED`, `CANCELLED`, `NOT_FOUND`, `INVALID_REQUEST`, `INVALID_CANONICAL_ID`, `UNSUPPORTED_PROFILE`, `DEFERRED_SC_B_REQUIRED`, `UNKNOWN_PENDING_NORMALIZATION`, `INTERNAL_ERROR`. | PASS |
| AC-019-005 | Introduces dedicated Northbound DTOs, not raw domain records as facade contract. | Facade signatures expose only `Northbound...` DTOs and `ScNorthboundResponse<T>`. | PASS |
| AC-019-006 | Implements Profile A topology observation operations. | Behavioral tests cover topology methods. | PASS |
| AC-019-007 | Implements selected Profile B TemporalAct operations only. | Behavioral tests cover Signal TemporalAct create/cancel/get/list; no action scheduling. | PASS |
| AC-019-008 | Does not implement Profile C discovery methods or discovery execution. | Source-level test verifies no discovery methods in `ScCoreNorthboundFacade`; no discovery package imports. | PASS |
| AC-019-009 | Does not implement Profile D / product-facing effective interaction. | Source-level test verifies no Effective/Projected/Surface/Session/Policy/Authority DTO naming or fields. | PASS |
| AC-019-010 | Topology/entity reads delegate through `CoreSnapshotQueryService`. | Constructor and source scan; behavioral tests using stub/mock service if applicable. | PASS |
| AC-019-011 | Facade does not inject SQLite repositories, `JdbcTemplate`, `DataSource` or `BaseTopologyService`. | Architecture test scans imports/fields/constructor parameters. | PASS |
| AC-019-012 | list methods derive from `CoreSnapshot.topology()` or equivalent canonical read surface. | Behavioral tests for list methods; source review confirms no new port requirement. | PASS |
| AC-019-013 | `getEndpointHealth` uses durable endpoint health read path. | Behavioral test confirms delegation to `queryService.findEndpointHealth`; source scan avoids aggregate fallback. | PASS |
| AC-019-014 | `getDeviceHealth` returns `UNKNOWN_PENDING_NORMALIZATION`. | Behavioral test asserts status and no durable payload claim. | PASS |
| AC-019-015 | `getDeviceRuntimeState` uses durable device state read surface. | Behavioral test asserts delegation to `queryService.findDeviceState`. | PASS |
| AC-019-016 | `getEndpointRuntimeState` returns `UNSUPPORTED_PROFILE` unless durable endpoint state exists. | Behavioral test asserts `UNSUPPORTED_PROFILE`; implementation report records retained open item. | PASS |
| AC-019-017 | `getTemporalRuntimeStatus` maps `TemporalEngineHealth`. | Behavioral test asserts status/counters mapping. | PASS |
| AC-019-018 | `getRecoveryStatus` is limited/proxy or `UNSUPPORTED_PROFILE`; does not fabricate recovery authority. | Behavioral test asserts chosen behavior; implementation report records retained open item. | PASS |
| AC-019-019 | `createSignalTemporalAct` delegates to `TemporalActApplicationPort`. | Behavioral test `createSignalTemporalActMapsAllResultVariantsCorrectly` covers `Accepted`, `IdempotentReplay`, `Rejected`, `Failed` as feasible. | PASS |
| AC-019-020 | `cancelTemporalAct` delegates to `TemporalActApplicationPort`. | Behavioral test covers `Cancelled`, `AlreadyTerminal`, `NotFound`, `IdempotentReplay`, `Rejected`, `Failed` as feasible. | PASS |
| AC-019-021 | get/list TemporalActs delegate to `TemporalActObservationPort`. | Behavioral tests cover `findById`, active/terminal/misfired filter semantics. | PASS |
| AC-019-022 | TemporalAct request DTOs exclude Session/Identity/Authority/Policy/Surface fields. | Source-level DTO field scan. | PASS |
| AC-019-023 | No HTTP controllers or Spring Web imports are introduced. | Architecture test scans main sources. | PASS |
| AC-019-024 | No MCP/gRPC/ConnectRPC/WebSocket/GraphQL exposure is introduced. | Architecture test scans main sources. | PASS |
| AC-019-025 | No NATS/JetStream concepts or imports are introduced. | Architecture test scans main sources. | PASS |
| AC-019-026 | No SC-D adapter invocation is introduced. | Architecture test scans main sources/imports. | PASS |
| AC-019-027 | No persistence adapter imports are introduced in northbound package. | Architecture test scans `core/northbound`. | PASS |
| AC-019-028 | Implementation report records open items for future profile descent. | `implementation-report.md` explicitly lists endpoint runtime state, recovery status and device health normalization debt. | PASS |
| AC-019-029 | Behavioral tests cover topology, health/runtime and TemporalAct operations. | `NorthboundFacadeBehavioralTest` or equivalent passes. | PASS |
| AC-019-030 | Negative architecture tests cover forbidden technology and boundary leakage. | `NorthboundFacadeArchitectureTest` / `NegativeBoundaryTest` or equivalent passes. | PASS |
| AC-019-031 | Spring wiring tests pass if Spring bean registration is used. | `NorthboundFacadeSpringContextTest` passes. | PASS |
| AC-019-032 | Existing test suite passes. | `mvn test` passes. | PASS |
| AC-019-033 | Implementation report records final file list, test results and justified deviations. | `implementation-report.md` complete. | PASS |

---

## 2. Behavioral coverage checklist

Required behavioral coverage:

```text
BT-019-001 getTopologySnapshot returns canonical topology with topologyVersion.
BT-019-002 getTopologyVersion returns current topologyVersion.
BT-019-003 getDevice resolves canonical deviceId.
BT-019-004 getEndpoint resolves canonical endpointId.
BT-019-005 provider-native IDs are rejected or do not resolve as canonical lookup authority.
BT-019-006 listRooms/listZones/listDevices/listEndpoints expose Base Topology only.
BT-019-007 listDevicesLocatedIn/listEndpointsLocatedIn use canonical location/spatial relation query path.
BT-019-008 getEndpointHealth returns durable endpoint health.
BT-019-009 getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION.
BT-019-010 getDeviceRuntimeState delegates to durable device state read surface.
BT-019-011 getEndpointRuntimeState is UNSUPPORTED_PROFILE unless durable endpoint-state port exists.
BT-019-012 getTemporalRuntimeStatus maps TemporalEngineHealth.
BT-019-013 createSignalTemporalAct delegates to TemporalActApplicationPort and returns CREATED/ACCEPTED.
BT-019-014 cancelTemporalAct delegates to TemporalActApplicationPort and returns CANCELLED/current terminal status.
BT-019-015 getTemporalAct delegates to TemporalActObservationPort.findById.
BT-019-016 listTemporalActs maps only supported filter modes to existing observation methods.
BT-019-017 common negative cases return ScNorthboundResponse statuses rather than leaking normal domain exceptions.
```

---

## 3. Negative/source-level coverage checklist

Required negative coverage:

```text
NB-019-001 northbound package has no HTTP controller annotations.
NB-019-002 northbound package has no Spring Web imports.
NB-019-003 northbound package has no GraphQL imports/classes.
NB-019-004 northbound package has no gRPC/ConnectRPC imports/classes.
NB-019-005 northbound package has no MCP imports/classes.
NB-019-006 northbound package has no WebSocket imports/classes.
NB-019-007 northbound package has no NATS/JetStream imports/classes.
NB-019-008 northbound package does not import SC-D adapter classes.
NB-019-009 northbound package does not import persistence adapter classes.
NB-019-010 northbound package does not use JdbcTemplate/DataSource.
NB-019-011 northbound DTO names do not use Effective/Projected/Surface/Session/Policy/Authority.
NB-019-012 no discovery methods are introduced in ScCoreNorthboundFacade.
NB-019-013 no Effective View, VisibilityRule, Session, Identity, Authority, Policy or Surface metadata appears in Northbound DTOs.
NB-019-014 no existing HTTP/MCP/gRPC/WebSocket/GraphQL exposure layer is introduced by the MU.
```

---

## 4. Spring wiring coverage checklist

Required if `DefaultScCoreNorthboundFacade` is registered as Spring bean:

```text
SW-019-001 ScCoreNorthboundFacade bean exists.
SW-019-002 ScCoreNorthboundFacade is backed by DefaultScCoreNorthboundFacade.
SW-019-003 Required collaborators resolve from existing Spring context.
SW-019-004 No HTTP controller bean is introduced.
SW-019-005 No conflicting facade implementation is registered.
```

---

## 5. Implementation report required fields

The implementation report must include:

```text
branch name
commit hash if available
final file list
main package tree
test package tree
facade methods implemented
exact delegation map
status mapping table
DeviceHealth strategy confirmation
EndpointHealth authority confirmation
TemporalAct filter semantics confirmation
negative boundary scan summary
Spring wiring summary
full test command
full test result
AC-019-001 through AC-019-033 result table
retained open items
deviations, if any, with justification
```

Retained open items required for `AC-019-028`:

```text
1. getEndpointRuntimeState -> UNSUPPORTED_PROFILE unless a durable endpoint runtime-state port exists.
2. getRecoveryStatus -> proxy/limited status or UNSUPPORTED_PROFILE until an explicit recovery read model exists.
3. DeviceHealth normalized authority / derivation debt remains open.
```
