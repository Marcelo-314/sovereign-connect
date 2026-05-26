# MIR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001

## SC-C Northbound Facade Hardening

**Document ID:** MIR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001  
**Title:** SC-C Northbound Facade Hardening  
**Version:** v0.2.0-candidate  
**Status:** Candidate / execution package enabled  
**Date:** 2026-05-25  
**Corpus:** Sovereign Connect  
**Type:** MIR  
**Plane:** SC-C / Northbound  
**Materialization Unit:** MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001  
**Operational Slot:** MU-020  
**Scope:** Hardens the existing in-process SC-C Northbound Facade produced by MU-019 so that SC-C + canonical Northbound can serve as the first technical product artifact candidate. This MIR does not implement EIB, View Composer, Projection, Effective View, HTTP/SSE, gRPC, ConnectRPC, MCP, GraphQL, WebSocket, SC-B runtime, NATS/JetStream, SC-D adapters, discovery execution, command dispatch or product-facing Hub/SApp/Surface integration.

---

## Changelog v0.2.0-candidate

Candidate promotion.

This version:

1. Promotes the MIR after review approval and enables the external execution package.
2. Records the `VALIDATION_ERROR` reachability observation: `VALIDATION_ERROR` MUST be exercised by at least one facade behavioral test, not merely added as an enum value.
3. Requires the execution package to copy the CSA exact replacement table for `ScNorthboundError` / `ScNorthboundWarning` source-field migration.
4. Hardens `G-020-007`, `D-MIR-020-007`, behavioral test obligations and `AC-020-018` accordingly.
5. Preserves all non-goals and boundary exclusions from v0.1.0-draft.

## Changelog v0.1.0-draft

Initial MIR draft.

This version:

1. Opens `MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001` as MU-020 after SDD and CSA approval.
2. Materializes `PDR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.2.0-candidate` and `SDD-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.1.1-draft` as a bounded hardening increment over the MU-019 Northbound Facade seed.
3. Adopts `CSA-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.1.1-merged` as the controlling code-surface audit for MIR-020.
4. Authorizes `DeviceHealth` derivation using `DERIVED_FROM_ENDPOINTS` over canonical endpoint enumeration and durable endpoint health lookup.
5. Keeps `EndpointRuntimeState` as `UNSUPPORTED_PROFILE` because no durable endpoint runtime-state authority exists.
6. Keeps `RecoveryStatus` as `UNSUPPORTED_PROFILE` because no Northbound-safe recovery/readiness read model exists.
7. Authorizes `getNorthboundDiagnostics(String habitatId)` with mandatory `topologyVersion`, `temporalEngineStatus`, `migrationReadiness` and `readAt` fields.
8. Sets `migrationReadiness` to `UNKNOWN_PENDING_NORMALIZATION` with source `migration.readiness` until a future migration/readiness read authority exists.
9. Authorizes adding `VALIDATION_ERROR` to `ScNorthboundStatus`.
10. Authorizes adding `source` to `ScNorthboundError` and `ScNorthboundWarning`, with exact call-site migration required by the execution package.
11. Preserves all MU-019 boundary exclusions: no external exposure, no EIB implementation, no View Composer, no SC-B runtime, no SC-D invocation, no discovery execution and no provider command dispatch.
12. Externalizes context, prompt, acceptance map and implementation report according to MU/MIR governance.

---

## 0. Governance note

This MIR does not include Codex prompts, implementation context or acceptance-map details inline.

Execution assets MUST be produced separately under:

```text
docs/mir/mir-020/
  MIR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001.md
  code-surface-audit.md
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report-template.md
  implementation-report.md
```

Operational rule:

```text
MIR defines the materialization scope.
CSA constrains the actual code surface.
context.md and codex-prompt.md operationalize implementation.
acceptance-map.md maps MIR/SDD/CSA criteria to tests and evidence.
implementation-report.md returns post-execution evidence.
```

This MIR is promoted to candidate.
Execution package preparation is enabled by this candidate promotion.

---

## 1. Disposition

```text
MIR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.2.0-candidate:
  Candidate MIR for MU-020.

Execution readiness:
  PDR candidate exists.
  SDD draft is approvable.
  CSA merged is approved for MIR.
  Execution package is enabled.

Implementation authorization:
  Direct implementation is authorized only after acceptance of context.md,
  codex-prompt.md, acceptance-map.md and implementation-report-template.md
  as the external execution package.
```

Candidate constraints:

```text
- MU-020 identity and operational slot remain bound to current INDEX/SYNC governance.
- CSA decisions are binding for the future execution package.
- Execution package MUST NOT broaden scope beyond this MIR.
- This MIR does not authorize EIB, external exposure or cross-plane runtime.
- This MIR hardens the in-process canonical Northbound Facade only.
```

---

## 2. Background

MU-019 produced an in-process canonical SC-C Northbound Facade with dedicated DTOs, local `ScNorthboundResponse<T>`, Profile A local observation and selected Signal TemporalAct Profile B operations.

MU-019 intentionally retained several seed-grade dispositions:

```text
DeviceHealth -> UNKNOWN_PENDING_NORMALIZATION
EndpointRuntimeState -> UNSUPPORTED_PROFILE
RecoveryStatus -> UNSUPPORTED_PROFILE
migrationReadiness -> absent
Northbound diagnostics -> absent
ScNorthboundError / ScNorthboundWarning -> no source field
ScNorthboundStatus -> no VALIDATION_ERROR
```

The post-MU-019 strategic decision is:

```text
EIB contracts may proceed.
EIB implementation remains deferred.
SC-C Northbound hardening is the next active implementation-oriented track.
```

This MIR therefore opens MU-020 to harden the existing Northbound Facade without turning it into a product-facing API or external transport binding.

The intended downstream product-facing chain remains:

```text
Hub / SApp / Surfaces
  -> EIB
  -> SC-C Northbound Facade
  -> SC-C application/query services and ports
```

MU-020 strengthens the middle boundary before EIB is implemented.

---

## 3. Depends on

```text
Sovereign Connect — Curator & Descent Instructions
RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.13-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.19-draft
SYNC-SOV-SC-MU-BACKLOG-001 v0.1.20-draft
PDR-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft
ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-TECH-001 v0.1.1-draft
SDD-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft
MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001 v1.0.0-accepted
PDR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.2.0-candidate
SDD-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.1.1-draft
CSA-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.1.1-merged
MIR-SOV-SC-C-TEMPORAL-RUNTIME-INDUSTRIALIZATION-001 v1.0.0-accepted
MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v1.0.0-accepted
MIR-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001 v1.0.0-accepted
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001 v0.1.1-draft
SDD-SOV-SC-C-RECOVERY-001 v0.1.2-draft
SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.1.2-draft
```

If governance documents use newer versions in the repository at execution time, the execution package MUST reference the newer canonical versions while preserving the same MU identity and decisions.

---

## 4. Related

```text
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001, contract-preparation / implementation-deferred
ADR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-draft
PDR-SOV-SC-VIEW-COMPOSER-BOUNDARY-001, downstream / deferred
SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001, downstream / not authorized
SDD-SOV-SC-C-NORTHBOUND-GRPC-CONNECTRPC-001, downstream / not authorized
PDR-SOV-SC-MCP-FACADE-001, outside primary SC-C Northbound hardening
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001, outside SC-C Northbound hardening
PDR-SOV-SC-X-DISCOVERY-REQUEST-FLOW-001, planned / SC-B-gated
```

---

## 5. Materialization thesis

MU-020 hardens the existing SC-C Northbound Facade so that SC-C + canonical Northbound can be treated as a first technical product artifact candidate.

Canonical statement:

```text
SC-C owns canonical truth.
Northbound exposes canonical truth and bounded SC-C requests.
EIB later decides effective admission/projection.
View Composer later composes effective read models.
Hub, SApp and Surfaces do not call SC-C directly.
```

Implementation statement:

```text
MU-020 modifies the existing in-process Northbound Facade.
It does not introduce a network boundary.
It does not introduce EIB or View Composer.
It does not execute discovery or provider commands.
It does not introduce SC-B or SC-D runtime.
```

Hardening statement:

```text
MU-020 closes or disposition seed-grade Northbound debts:
  DeviceHealth derivation;
  endpoint runtime-state unsupported status;
  recovery status unsupported status;
  diagnostics/readiness surface;
  response/error/warning vocabulary;
  DTO and architecture regression coverage.
```

---

## 6. Goals

### G-020-001 — Preserve the MU-019 Northbound boundary

Preserve the existing in-process SC-C-owned Northbound Facade as canonical and transport-neutral.

The facade MUST remain distinct from:

```text
EIB
View Composer
Projection / Effective View
HTTP/SSE exposure
gRPC / ConnectRPC exposure
MCP
GraphQL
WebSocket
SC-B runtime
SC-D adapter runtime
Hub / SApp / Surfaces
```

### G-020-002 — Harden DeviceHealth

Replace first-seed `UNKNOWN_PENDING_NORMALIZATION` for `getDeviceHealth(...)` with `DERIVED_FROM_ENDPOINTS`, using only:

```text
queryService.findDevice(habitatId, deviceId)
deviceSnapshot.device().endpointIds()
queryService.findEndpointHealth(habitatId, endpointId)
```

No provider-native health, stale aggregate health, `DeviceNode.health()`, `EndpointNode.health()`, `EndpointSnapshot.health()` fallback or `topology_json` may be used as authority.

### G-020-003 — Preserve EndpointRuntimeState as unsupported

Keep `getEndpointRuntimeState(...)` as `UNSUPPORTED_PROFILE` because no durable endpoint runtime-state read authority exists.

### G-020-004 — Preserve RecoveryStatus as unsupported

Keep `getRecoveryStatus(...)` as `UNSUPPORTED_PROFILE` because no Northbound-safe recovery/readiness read model exists.

### G-020-005 — Add diagnostics/readiness surface

Add:

```java
ScNorthboundResponse<NorthboundDiagnosticsView> getNorthboundDiagnostics(String habitatId);
```

The diagnostics surface MUST include at least:

```text
topologyVersion
temporalEngineStatus
migrationReadiness
readAt
```

### G-020-006 — Add migration readiness deferred status

Expose `migrationReadiness` inside diagnostics as:

```text
status = UNKNOWN_PENDING_NORMALIZATION
source = migration.readiness
message = No migration readiness read port exists; deferred to future MU.
```

MU-020 MUST NOT infer `READY` from Flyway bean presence, Spring context startup or repository availability.

### G-020-007 — Harden response vocabulary

Add `VALIDATION_ERROR` to `ScNorthboundStatus`.

`VALIDATION_ERROR` MUST be reachable through at least one facade execution path and MUST be covered by at least one behavioral test. Adding the enum value without any path returning it is insufficient.

Recommended path:

```text
createSignalTemporalAct(...) with dueAt in the past
  -> ScNorthboundStatus.VALIDATION_ERROR
```

The execution package SHOULD add a `validationError(...)` factory on `ScNorthboundResponse` to keep this mapping explicit and avoid overloading `INVALID_REQUEST`.

### G-020-008 — Add source to Northbound errors and warnings

Migrate:

```java
ScNorthboundError(String code, String message)
ScNorthboundWarning(String code, String message)
```

to:

```java
ScNorthboundError(String code, String message, String source)
ScNorthboundWarning(String code, String message, String source)
```

The execution package MUST provide exact replacements for all current constructor call sites.

### G-020-009 — Preserve TemporalAct Signal-only hardening

Preserve selected TemporalAct Profile B operations as Signal-only:

```text
createSignalTemporalAct
cancelTemporalAct
getTemporalAct
listTemporalActs
getTemporalRuntimeStatus
```

MU-020 MUST NOT introduce:

```text
ActionTemporalPayload execution
provider-level command scheduling
device command dispatch
SC-B dispatcher control
recurrence / cron / calendar engine
policy / authority scheduling
surface countdown UX
session / user identity binding
```

### G-020-010 — Strengthen regression coverage

Add or preserve tests that verify the hardening behavior and all forbidden-boundary constraints.

---

## 7. Non-goals

MU-020 MUST NOT implement:

```text
EIB
View Composer
Projection
Effective View
HTTP controller layer
SSE stream
gRPC service
ConnectRPC service
MCP facade
GraphQL schema/resolver
WebSocket endpoint
SC-B runtime
NATS / JetStream binding
SC-D adapter invocation
discovery execution
provider scanning/polling
command dispatch
ActionTemporalPayload execution
recurrence / cron / calendar behavior
outbox dispatcher
Hub/SApp/Surface direct integration
product UX
```

MU-020 MUST NOT modify:

```text
Flyway migrations
SQLite schema
TemporalEngineConfiguration
TopologyPersistenceConfiguration
SC-B packages
SC-D packages
external transport packages
EIB / View Composer packages
```

unless the implementation report records a narrowly justified exception and tests prove boundary preservation. The expected implementation path requires no such changes.

---

## 8. CSA decisions adopted by this MIR

### D-MIR-020-001 — DeviceHealth DERIVED_FROM_ENDPOINTS

Decision:

```text
DeviceHealth DERIVED_FROM_ENDPOINTS is authorized for MU-020.
```

Required algorithm:

```text
1. Validate habitatId/deviceId according to existing Northbound validation discipline.
2. Resolve device through queryService.findDevice(habitatId, deviceId).
3. If absent, return NOT_FOUND.
4. Extract deviceSnapshot.device().endpointIds().
5. If endpointIds is empty, return UNKNOWN_PENDING_NORMALIZATION with source DERIVED_FROM_ENDPOINTS.
6. For each endpointId, call queryService.findEndpointHealth(habitatId, endpointId).
7. Missing endpoint health is treated as UNKNOWN evidence.
8. Derive:
     all HEALTHY -> HEALTHY;
     any OFFLINE -> OFFLINE;
     any DEGRADED / UNKNOWN / missing -> DEGRADED.
9. Return OK with NorthboundDeviceHealthView.
10. readAt MUST use Instant.now(clock).
```

Required forbidden sources:

```text
DeviceNode.health()
EndpointNode.health()
EndpointSnapshot.health() fallback
provider-native health
service-local mutable cache
topology_json
```

### D-MIR-020-002 — EndpointRuntimeState remains UNSUPPORTED_PROFILE

Decision:

```text
getEndpointRuntimeState(...) remains UNSUPPORTED_PROFILE in MU-020.
```

Reason:

```text
No durable endpoint runtime-state authority exists.
```

### D-MIR-020-003 — RecoveryStatus remains UNSUPPORTED_PROFILE

Decision:

```text
getRecoveryStatus(...) remains UNSUPPORTED_PROFILE in MU-020.
```

Reason:

```text
No Northbound-safe recovery/readiness read model exists.
```

### D-MIR-020-004 — Northbound diagnostics is authorized

Decision:

```text
Add getNorthboundDiagnostics(String habitatId) to ScCoreNorthboundFacade.
```

Required method:

```java
ScNorthboundResponse<NorthboundDiagnosticsView> getNorthboundDiagnostics(String habitatId);
```

Required field mapping:

```text
topologyVersion:
  queryService.findCurrentTopologyVersion(habitatId)

temporalEngineStatus:
  existing TemporalEngineHealth mapping via NorthboundTemporalRuntimeStatusView

migrationReadiness:
  UNKNOWN_PENDING_NORMALIZATION, source = migration.readiness

readAt:
  Instant.now(clock)
```

### D-MIR-020-005 — migrationReadiness is UNKNOWN_PENDING_NORMALIZATION

Decision:

```text
migrationReadiness.status = UNKNOWN_PENDING_NORMALIZATION
migrationReadiness.source = migration.readiness
migrationReadiness.message = No migration readiness read port exists; deferred to future MU.
```

This records the absence of a normalized migration/readiness authority without blocking the diagnostics method.

### D-MIR-020-006 — Error/warning source field migration is authorized

Decision:

```text
Add String source to ScNorthboundError and ScNorthboundWarning.
```

The execution package MUST update all constructor call sites in the same patch.

Known call-site inventory:

```text
ScNorthboundError:
  6 constructor calls, all inside ScNorthboundResponse.java.

ScNorthboundWarning:
  3 constructor calls, all inside DefaultScCoreNorthboundFacade.java.
```

### D-MIR-020-007 — VALIDATION_ERROR is added and made reachable

Decision:

```text
Add VALIDATION_ERROR to ScNorthboundStatus.
Add a reachable facade path that returns VALIDATION_ERROR.
Add at least one behavioral test that asserts status == VALIDATION_ERROR.
```

Recommended execution package mapping:

```text
createSignalTemporalAct(...) with dueAt before Instant.now(clock)
  -> ScNorthboundResponse.validationError(
       "INVALID_DUE_AT",
       "dueAt must be in the future"
     )
```

No other status values are required by MU-020.

### D-MIR-020-008 — Boundary exclusions remain binding

Decision:

```text
No external exposure, EIB, View Composer, SC-B runtime, SC-D invocation,
discovery execution, provider command dispatch or product surface integration
is authorized by MU-020.
```

---

## 9. Operation disposition matrix

| Operation | MU-019 status | MU-020 disposition |
|---|---|---|
| `getTopologySnapshot` | implemented | keep; Base Topology only, no Effective View |
| `getTopologyVersion` | implemented | keep; authority for diagnostics `topologyVersion` |
| `getDevice` | implemented | keep; canonical ID lookup only |
| `getEndpoint` | implemented | keep; canonical ID lookup only |
| `listRooms` | implemented | keep; canonical topology snapshot |
| `listZones` | implemented | keep; canonical topology snapshot |
| `listDevices` | implemented | keep; canonical topology snapshot |
| `listEndpoints` | implemented | keep; canonical topology snapshot |
| `listDevicesLocatedIn` | implemented | keep; LOCATED_IN only |
| `listEndpointsLocatedIn` | implemented | keep; LOCATED_IN only |
| `getEndpointHealth` | implemented | keep; direct durable endpoint health lookup |
| `getDeviceHealth` | `UNKNOWN_PENDING_NORMALIZATION` | harden to `DERIVED_FROM_ENDPOINTS` |
| `getDeviceRuntimeState` | implemented | keep; `queryService.findDeviceState(...)` |
| `getEndpointRuntimeState` | `UNSUPPORTED_PROFILE` | keep unsupported |
| `getRecoveryStatus` | `UNSUPPORTED_PROFILE` | keep unsupported |
| `getTemporalRuntimeStatus` | implemented | keep; diagnostics authority for `temporalEngineStatus` |
| `createSignalTemporalAct` | implemented | keep Signal-only |
| `cancelTemporalAct` | implemented | keep |
| `getTemporalAct` | implemented | keep |
| `listTemporalActs` | implemented | keep ACTIVE / TERMINAL / MISFIRED only |
| `getNorthboundDiagnostics` | absent | add |
| discovery methods | absent | remain absent |
| command-dispatch methods | absent | remain absent |

---

## 10. Required DTOs and response changes

### 10.1 NorthboundDiagnosticsView

Expected file:

```text
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundDiagnosticsView.java
```

Required shape:

```java
public record NorthboundDiagnosticsView(
    String habitatId,
    String topologyVersion,
    NorthboundTemporalRuntimeStatusView temporalEngineStatus,
    NorthboundMigrationReadinessView migrationReadiness,
    Instant readAt,
    List<ScNorthboundWarning> warnings
) {}
```

### 10.2 NorthboundMigrationReadinessView

Expected file:

```text
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundMigrationReadinessView.java
```

Required shape:

```java
public record NorthboundMigrationReadinessView(
    String status,
    String source,
    String message
) {}
```

Allowed status values:

```text
READY
DEGRADED
UNSUPPORTED_PROFILE
UNKNOWN_PENDING_NORMALIZATION
```

MU-020 value:

```text
status:  UNKNOWN_PENDING_NORMALIZATION
source:  migration.readiness
message: No migration readiness read port exists; deferred to future MU.
```

### 10.3 NorthboundDeviceHealthView

The existing view SHOULD be hardened or replaced to include at least:

```text
deviceId
status
source
endpointCount
readAt
warnings
```

Allowed source values:

```text
DERIVED_FROM_ENDPOINTS
UNKNOWN_PENDING_NORMALIZATION
UNSUPPORTED_PROFILE
```

Derived MU-020 values use:

```text
source = DERIVED_FROM_ENDPOINTS
```

### 10.4 ScNorthboundError

Target shape:

```java
public record ScNorthboundError(
    String code,
    String message,
    String source
) {}
```

Required source values for factory methods:

```text
notFound -> northbound.query
invalidRequest -> northbound.validation
validationError -> northbound.validation
invalidCanonicalId -> northbound.validation
unsupportedProfile -> northbound.unsupported_profile
unknownPendingNormalization -> northbound.pending_normalization
internalError -> northbound.internal
```

The execution package MUST copy the CSA exact replacement table for existing error/warning constructor call sites.

### 10.5 ScNorthboundWarning

Target shape:

```java
public record ScNorthboundWarning(
    String code,
    String message,
    String source
) {}
```

Required current replacements:

```text
IDEMPOTENT_REPLAY / Request already processed -> temporal.application
ALREADY_TERMINAL / Act was already in terminal state -> temporal.application
IDEMPOTENT_REPLAY / Cancel already recorded -> temporal.application
```

---

## 11. Expected file scope

Expected main-source changes:

```text
src/main/java/com/sovereign/connect/core/northbound/ScCoreNorthboundFacade.java
src/main/java/com/sovereign/connect/core/northbound/DefaultScCoreNorthboundFacade.java
src/main/java/com/sovereign/connect/core/northbound/NorthboundMapper.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundResponse.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundStatus.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundError.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundWarning.java
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundDeviceHealthView.java
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundDiagnosticsView.java
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundMigrationReadinessView.java
```

Expected tests:

```text
src/test/java/.../NorthboundFacadeBehavioralTest.java
src/test/java/.../NorthboundFacadeNegativeBoundaryTest.java
src/test/java/.../NorthboundFacadeSpringContextTest.java
src/test/java/.../NorthboundFacadeArchitectureTest.java
```

No changes expected to:

```text
Flyway migrations
SQLite schema
TemporalEngineConfiguration
TopologyPersistenceConfiguration
SC-B packages
SC-D packages
external transport packages
EIB/View Composer packages
```

---

## 12. Test obligations

The execution package MUST require fresh local validation:

```text
mvn test
```

or the repository's accepted equivalent if Maven wrapper/build conventions change.

### 12.1 Behavioral coverage

Behavioral tests MUST prove:

```text
getDeviceHealth derives HEALTHY when all endpoints are HEALTHY.
getDeviceHealth derives OFFLINE when any endpoint is OFFLINE.
getDeviceHealth derives DEGRADED when any endpoint is DEGRADED, UNKNOWN or missing.
getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION when endpoint list is empty.
getDeviceHealth does not use DeviceNode.health() as authority.
getNorthboundDiagnostics returns topologyVersion.
getNorthboundDiagnostics returns temporalEngineStatus.
getNorthboundDiagnostics returns migrationReadiness UNKNOWN_PENDING_NORMALIZATION.
getNorthboundDiagnostics includes readAt.
ScNorthboundWarning source is populated.
ScNorthboundError source is populated.
VALIDATION_ERROR exists and is reachable through at least one facade behavioral path, preferably dueAt in the past for createSignalTemporalAct(...).
```

### 12.2 Negative / architecture coverage

Negative and architecture tests MUST prove:

```text
No HTTP / SSE annotations or imports.
No gRPC / ConnectRPC imports.
No MCP imports.
No GraphQL imports.
No WebSocket imports.
No NATS / JetStream imports.
No SC-B runtime dependency.
No SC-D adapter invocation.
No persistence adapter imports in northbound package.
No topology_json usage in northbound package.
No Effective / Projected / Surface / Session / Policy / Authority DTO naming drift.
DeviceHealth derivation does not call DeviceNode.health(), EndpointNode.health() or EndpointSnapshot.health().
```

### 12.3 Spring wiring coverage

Spring wiring tests MUST prove:

```text
DefaultScCoreNorthboundFacade remains a Spring bean.
getNorthboundDiagnostics(...) is accessible through ScCoreNorthboundFacade.
No new configuration class is required unless explicitly justified.
Existing safe constructor wiring remains valid.
```

---

## 13. Acceptance criteria

- AC-020-001: MIR opens `MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001` as MU-020.
- AC-020-002: MIR depends on the approved PDR, SDD and merged CSA for Northbound hardening.
- AC-020-003: MIR preserves Northbound as SC-C-owned, in-process, canonical, transport-neutral and EIB-intended.
- AC-020-004: MIR forbids EIB, View Composer, Projection and Effective View implementation.
- AC-020-005: MIR forbids HTTP/SSE/gRPC/ConnectRPC/MCP/GraphQL/WebSocket exposure.
- AC-020-006: MIR forbids SC-B runtime, NATS/JetStream, SC-D adapter invocation and discovery execution.
- AC-020-007: MIR adopts `DERIVED_FROM_ENDPOINTS` for `DeviceHealth`.
- AC-020-008: MIR requires durable endpoint health lookup through `queryService.findEndpointHealth(...)` for DeviceHealth derivation.
- AC-020-009: MIR forbids `DeviceNode.health()`, `EndpointNode.health()`, `EndpointSnapshot.health()` fallback, provider-native status, service-local cache and `topology_json` as DeviceHealth authority.
- AC-020-010: MIR preserves `getEndpointRuntimeState(...)` as `UNSUPPORTED_PROFILE`.
- AC-020-011: MIR preserves `getRecoveryStatus(...)` as `UNSUPPORTED_PROFILE`.
- AC-020-012: MIR authorizes `getNorthboundDiagnostics(String habitatId)`.
- AC-020-013: Diagnostics include `topologyVersion`, `temporalEngineStatus`, `migrationReadiness` and `readAt`.
- AC-020-014: `topologyVersion` diagnostics use `queryService.findCurrentTopologyVersion(...)`.
- AC-020-015: `temporalEngineStatus` diagnostics use existing `TemporalEngineHealth` mapping.
- AC-020-016: `migrationReadiness` returns `UNKNOWN_PENDING_NORMALIZATION` with source `migration.readiness`.
- AC-020-017: MIR forbids inferring migration readiness from Flyway bean presence, Spring context startup or repository availability.
- AC-020-018: MIR authorizes adding `VALIDATION_ERROR` to `ScNorthboundStatus` and requires at least one behavioral test proving a facade path returns `VALIDATION_ERROR`.
- AC-020-019: MIR authorizes adding `source` to `ScNorthboundError` and `ScNorthboundWarning`.
- AC-020-020: Execution package must copy the CSA exact call-site replacements for the error/warning source migration.
- AC-020-021: MIR preserves `ScNorthboundResponse<T>` as distinct from SC-B `ScResponseEnvelope<TResponse>`.
- AC-020-022: MIR requires dedicated Northbound DTOs and forbids returning internal persistence/schema details as Northbound contract.
- AC-020-023: MIR preserves Signal-only TemporalAct Profile B operations.
- AC-020-024: MIR forbids ActionTemporalPayload execution, provider command scheduling, recurrence/cron/calendar behavior and product UX timers.
- AC-020-025: MIR requires behavioral tests for DeviceHealth derivation: HEALTHY, OFFLINE, DEGRADED and empty endpoint set.
- AC-020-026: MIR requires behavioral tests for diagnostics mandatory fields.
- AC-020-027: MIR requires tests proving error/warning source fields are populated.
- AC-020-028: MIR requires negative/architecture tests for forbidden external exposure and cross-plane imports.
- AC-020-029: MIR requires architecture tests proving Northbound does not use persistence adapters, `topology_json`, Flyway/JdbcTemplate/DataSource or provider clients directly.
- AC-020-030: MIR requires Spring wiring tests for facade bean and diagnostics method access.
- AC-020-031: MIR requires the implementation report to record retained debts after MU-020.
- AC-020-032: MIR requires fresh test validation evidence in the implementation report.
- AC-020-033: MIR externalizes context, prompt, acceptance-map and implementation-report template outside the MIR.
- AC-020-034: MIR does not authorize implementation until candidate promotion and execution-package acceptance.

---

## 14. Expected retained debt after MU-020

If MU-020 executes according to this MIR, expected debt state is:

```text
DEBT-019-001 — EndpointRuntimeState remains UNSUPPORTED_PROFILE.
  Status after MU-020: retained.

DEBT-019-002 — RecoveryStatus remains UNSUPPORTED_PROFILE.
  Status after MU-020: retained.

DEBT-019-003 — DeviceHealth normalized authority / derivation.
  Status after MU-020: partially closed by DERIVED_FROM_ENDPOINTS.
  Remaining debt: DeviceHealth is derived, not normalized independent authority.

DEBT-020-001 — Migration readiness read authority absent.
  Status after MU-020: new retained debt.

DEBT-019-004 — EIB / View Composer implementation absent.
  Status after MU-020: retained outside this track.

DEBT-019-005 — Outbox dispatcher / SC-B delivery runtime absent.
  Status after MU-020: retained outside this track.
```

The implementation report MUST record these dispositions explicitly.

---

## 15. Failure value

MU-020 is valuable even if it fails, because failure would identify one or more of the following:

```text
- DeviceHealth cannot be safely derived from current endpoint health authority.
- Northbound diagnostics cannot be exposed without unsafe migration/recovery authority leakage.
- The response vocabulary/source-field migration creates excessive coupling or compile risk.
- The Northbound package still depends on seed-grade shortcuts that prevent product artifact hardening.
- The facade boundary cannot be hardened without accidentally implementing EIB, external exposure or cross-plane runtime.
```

A failed MU-020 would therefore block SC technical product artifact planning until the unsafe surface is redesigned.

---

## 16. Candidate promotion record

This MIR is promoted to candidate because the following criteria are accepted:

```text
1. DERIVED_FROM_ENDPOINTS as the MU-020 DeviceHealth strategy.
2. EndpointRuntimeState remains UNSUPPORTED_PROFILE.
3. RecoveryStatus remains UNSUPPORTED_PROFILE.
4. getNorthboundDiagnostics(...) is mandatory for MU-020.
5. migrationReadiness = UNKNOWN_PENDING_NORMALIZATION.
6. VALIDATION_ERROR is added to ScNorthboundStatus and must be reachable by test.
7. source-field migration for ScNorthboundError and ScNorthboundWarning is authorized.
8. The execution package must copy the CSA exact source-field call-site replacement table.
9. EIB/external exposure/SC-B/SC-D remain outside scope.
10. AC-020-001 through AC-020-034 are accepted with the AC-020-018 reachability hardening.
11. Creation of the external execution package is authorized.
```

---

## 17. Branch and commit recommendation

Suggested branch:

```text
feat/sc-c-mir-020-northbound-facade-hardening
```

Suggested commit:

```text
feat(sc-c): harden northbound facade
```

---

## 18. Summary decision

```text
D-MIR-020-SUMMARY — Open Northbound Facade Hardening as MU-020

Decision:
  Open MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 as the next SC-C
  production-readiness hardening unit after MU-019.

  MU-020 hardens the in-process canonical Northbound Facade by adding
  DeviceHealth DERIVED_FROM_ENDPOINTS, diagnostics/readiness, response
  vocabulary/source metadata and regression coverage.

  MU-020 keeps EndpointRuntimeState and RecoveryStatus unsupported because
  no safe authority exists yet.

  MU-020 does not implement EIB, View Composer, external exposure, SC-B,
  SC-D, discovery execution or product-facing surfaces.

Rationale:
  SC-C + Northbound must be hardened into a technical product artifact candidate
  before EIB descends to implementation.

Consequence:
  EIB contract preparation may continue.
  EIB CSA/MIR/execution package remain deferred.
  Northbound hardening becomes the immediate implementation-oriented track.
```
