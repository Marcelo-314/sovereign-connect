# Code Surface Audit — MU-020 Northbound Facade Hardening

```text
Document ID:  CSA-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Version:      v0.1.1-merged
Status:       Merged Draft / Approvable for MIR-020
Date:         2026-05-25
MU:           MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Slot:         MU-020
Baseline:     sovereign-connect-020-pre-csa.zip
Input docs:   PDR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.2.0-candidate
              SDD-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.1.1-draft
Tests seen:   target/surefire-reports: 188 / 0 failures / 0 errors / 0 skipped
Runtime note: mvn/mvnw unavailable in CSA environment; test evidence is from included reports.
```

---

## 0. Purpose

Pre-MIR code surface audit for `MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001`.

This merged CSA consolidates two independent audits of the same post-MU-019 service surface. It confirms the MU-019 seed baseline, closes the SDD hardening open questions, identifies compile-risk call sites, and defines the exact decisions that the downstream MIR and execution package must preserve.

This document is not an implementation prompt. It defines code-surface facts, safe authorities, forbidden changes and implementation-risk controls.

---

## 1. Executive verdict

```text
Verdict: Approvable for MIR-020 / execution package after the decisions in this CSA are adopted.
```

The service is ready for Northbound hardening. The current Northbound facade is present, in-process, canonical and isolated from external exposure technology. The MU-020 work is narrow enough for a controlled MIR:

```text
1. Add getNorthboundDiagnostics(...).
2. Implement DeviceHealth DERIVED_FROM_ENDPOINTS through canonical endpoint enumeration + durable endpoint health.
3. Keep EndpointRuntimeState explicitly UNSUPPORTED_PROFILE.
4. Keep RecoveryStatus explicitly UNSUPPORTED_PROFILE until a real read model/readiness authority exists.
5. Add source to ScNorthboundError / ScNorthboundWarning, using exact call-site replacements.
6. Add VALIDATION_ERROR to ScNorthboundStatus.
7. Preserve all external exposure and boundary exclusions.
```

No architectural blocker was found. The main compile-risk is the arity change of `ScNorthboundError` and `ScNorthboundWarning` from two fields to three fields.

---

## 2. Evidence snapshot

### 2.1 Test evidence

A fresh local Maven run was not possible in the CSA environment:

```text
mvn: unavailable
mvnw: absent
```

Included Surefire reports show:

```text
Suites:   22
Tests:    188
Failures: 0
Errors:   0
Skipped:  0
```

Northbound-specific included reports:

```text
NorthboundFacadeArchitectureTest:     4 tests
NorthboundFacadeBehavioralTest:       11 tests
NorthboundFacadeNegativeBoundaryTest: 2 tests
NorthboundFacadeSpringContextTest:    3 tests
```

The execution package MUST require a fresh `mvn test` in the implementation environment.

### 2.2 Branch / worktree note

Observed branch lineage is post-MU-019. The implementation package should start MU-020 from a clean branch based on the committed MU-019 state. Any unrelated historical worktree noise, IDE files, line-ending churn or old MIR artifacts MUST be excluded from the MU-020 commit.

Recommended branch:

```text
feat/sc-c-mir-020-northbound-facade-hardening
```

Recommended commit message:

```text
feat(sc-c): harden northbound facade
```

---

## 3. MU-019 seed baseline — confirmed

The following seed invariants are confirmed:

```text
ScCoreNorthboundFacade exists with 21 methods.
DefaultScCoreNorthboundFacade is a Spring @Service.
DefaultScCoreNorthboundFacade has 5 constructor collaborators.
ScNorthboundResponse<T> is local to northbound and distinct from SC-B ScResponseEnvelope.
ScNorthboundStatus currently has 11 values.
ScNorthboundError is currently a 2-field record: String code, String message.
ScNorthboundWarning is currently a 2-field record: String code, String message.
getEndpointHealth delegates to queryService.findEndpointHealth(...) directly.
getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION unconditionally.
getEndpointRuntimeState returns UNSUPPORTED_PROFILE.
getRecoveryStatus returns UNSUPPORTED_PROFILE.
No discovery methods exist.
No external transport annotations or imports exist in northbound.
```

Current collaborators:

```java
CoreSnapshotQueryService queryService
TemporalActApplicationPort temporalActApplicationPort
TemporalActObservationPort temporalActObservationPort
TemporalEngineHealth engineHealth
Clock clock
```

MU-020 SHOULD preserve this minimal collaborator set unless adding a future readiness authority is explicitly justified by CSA/MIR. It MUST NOT inject persistence adapters, Flyway, JdbcTemplate, DataSource, broker clients or provider clients into the facade.

---

## 4. Current method disposition

| Method | Current MU-019 state | MU-020 CSA disposition |
|---|---|---|
| `getTopologySnapshot` | implemented | keep; Base Topology only, no Effective View |
| `getTopologyVersion` | implemented | keep; authority for diagnostics `topologyVersion` |
| `getDevice` | implemented | keep; canonical ID only |
| `getEndpoint` | implemented | keep; canonical ID only |
| `listRooms` | implemented | keep; current snapshot topology |
| `listZones` | implemented | keep; current snapshot topology |
| `listDevices` | implemented | keep; current snapshot topology |
| `listEndpoints` | implemented | keep; current snapshot topology |
| `listDevicesLocatedIn` | implemented | keep; LOCATED_IN semantics only |
| `listEndpointsLocatedIn` | implemented | keep; LOCATED_IN semantics only |
| `getEndpointHealth` | implemented | keep; direct durable endpoint health lookup |
| `getDeviceHealth` | `UNKNOWN_PENDING_NORMALIZATION` | harden to `DERIVED_FROM_ENDPOINTS` |
| `getDeviceRuntimeState` | implemented | keep; `queryService.findDeviceState(...)` |
| `getEndpointRuntimeState` | `UNSUPPORTED_PROFILE` | keep unsupported; no durable endpoint runtime-state authority exists |
| `getRecoveryStatus` | `UNSUPPORTED_PROFILE` | keep unsupported; no safe recovery/readiness read model exists |
| `getTemporalRuntimeStatus` | implemented | keep; authority for diagnostics `temporalEngineStatus` |
| `createSignalTemporalAct` | implemented | keep Signal-only; no ActionTemporalPayload / command dispatch |
| `cancelTemporalAct` | implemented | keep |
| `getTemporalAct` | implemented | keep |
| `listTemporalActs` | implemented | keep ACTIVE / TERMINAL / MISFIRED only |
| `getNorthboundDiagnostics` | absent | add for MU-020 |
| discovery methods | absent | remain absent |
| command-dispatch methods | absent | remain absent |

---

## 5. Authority inventory

### 5.1 Allowed authorities confirmed in code

```text
CoreSnapshotQueryService
  findCurrentSnapshot(habitatId)
  findCurrentTopologyVersion(habitatId)
  findDevice(habitatId, deviceId)
  findEndpoint(habitatId, endpointId)
  findDeviceState(habitatId, deviceId)
  findEndpointHealth(habitatId, endpointId)
  findLocatedDevices(habitatId, roomOrZoneId)
  findLocatedEndpoints(habitatId, roomOrZoneId)

TemporalActApplicationPort
  createSignalTemporalAct(...)
  cancelTemporalAct(...)

TemporalActObservationPort
  findById(...)
  listActive(...)
  listTerminal(...)
  listMisfired(...)

TemporalEngineHealth
  status()
  isReady()
  lastPollAt()
  lastSuccessfulPollAt()
  firedTotal()
  misfiredTotal()
  cancelledTotal()
  failedTotal()
  skippedTotal()

Clock
  readAt / requestedAt timestamps
```

### 5.2 Authorities present but not safe for direct Northbound use

```text
SQLiteBaseTopologyRepository
SQLiteEndpointHealthRepository
SQLiteTemporalRecoveryObservationRepository
Flyway
JdbcTemplate
DataSource
TemporalRecoveryObservationPort
```

Reason: these are adapters, migration tooling or write-side/implementation-side concerns. Northbound hardening MUST NOT import them directly.

### 5.3 Missing authorities relevant to MU-020

```text
Endpoint runtime-state read authority: absent
Recovery/readiness read model: absent
Migration readiness read model: absent as a Northbound-safe port/read model
Normalized DeviceHealth read/write authority: absent
```

---

## 6. CSA decisions

### D-CSA-020-001 — DeviceHealth strategy

Decision:

```text
Implement DERIVED_FROM_ENDPOINTS in MU-020.
```

Rationale:

`DeviceNode` exposes a canonical immutable endpoint set through `endpointIds()`. The list is canonical topology data and can be used to enumerate each endpoint of a device. Each endpoint health lookup can be performed through `CoreSnapshotQueryService.findEndpointHealth(habitatId, endpointId)`, which is the durable endpoint health read path.

Confirmation conditions:

```text
1. Every device endpoint set is enumerable from canonical topology.
2. Each endpoint health lookup uses durable authority via findEndpointHealth(...).
3. Missing endpoint health is detectable through Optional.empty().
4. No provider-native health, stale aggregate health or topology_json health is needed.
```

Implementation rule:

```text
getDeviceHealth(habitatId, deviceId)
  -> queryService.findDevice(habitatId, deviceId)
  -> deviceSnapshot.device().endpointIds()
  -> for each endpointId: queryService.findEndpointHealth(habitatId, endpointId)
  -> conservative derivation
```

Conservative derivation:

```text
No device found:
  NOT_FOUND.

Endpoint list empty:
  UNKNOWN_PENDING_NORMALIZATION.

All endpoints have durable HEALTHY:
  HEALTHY.

Any endpoint has durable OFFLINE:
  OFFLINE.

Any endpoint has DEGRADED, UNKNOWN, missing health row, or non-durable evidence:
  DEGRADED.
```

Vocabulary note:

```text
OFFLINE is the implementation's unhealthy-equivalent for this MU.
```

The implementation MUST NOT read `DeviceNode.health()`, `EndpointNode.health()`, `EndpointSnapshot.health()` fallback, provider-native status, service-local mutable caches or `topology_json` to derive device health.

### D-CSA-020-002 — EndpointRuntimeState strategy

Decision:

```text
Keep getEndpointRuntimeState(...) as UNSUPPORTED_PROFILE in MU-020.
```

Rationale:

No durable endpoint runtime-state read authority exists. Implementing endpoint runtime state in MU-020 would require either new persistence semantics or an unsafe inference from device state/provider payloads. Both are out of scope.

### D-CSA-020-003 — RecoveryStatus strategy

Decision:

```text
Keep getRecoveryStatus(...) as UNSUPPORTED_PROFILE in MU-020.
```

Rationale:

`TemporalRecoveryObservationPort` is not a safe read model for Northbound status exposure. No explicit recovery/readiness query port currently exists. MU-020 MUST NOT fabricate recovery status from Spring bean availability, service liveness or unrelated temporal engine state.

### D-CSA-020-004 — ScNorthboundStatus hardening

Decision:

```text
Add VALIDATION_ERROR to ScNorthboundStatus.
```

Rationale:

The hardening SDD requires a validation-error vocabulary. The current enum has 11 values:

```text
OK
CREATED
ACCEPTED
CANCELLED
NOT_FOUND
INVALID_REQUEST
INVALID_CANONICAL_ID
UNSUPPORTED_PROFILE
DEFERRED_SC_B_REQUIRED
UNKNOWN_PENDING_NORMALIZATION
INTERNAL_ERROR
```

`VALIDATION_ERROR` is absent and should be added in MU-020. Total after MU-020: 12 values.

No other enum values are required.

### D-CSA-020-005 — Diagnostics surface

Decision:

```text
Add getNorthboundDiagnostics(String habitatId) to ScCoreNorthboundFacade.
```

Required method:

```java
ScNorthboundResponse<NorthboundDiagnosticsView> getNorthboundDiagnostics(String habitatId);
```

Required authority mapping:

```text
topologyVersion:
  queryService.findCurrentTopologyVersion(habitatId)

temporalEngineStatus:
  getTemporalRuntimeStatus(habitatId) mapping from TemporalEngineHealth

migrationReadiness:
  UNKNOWN_PENDING_NORMALIZATION; no safe read authority exists

readAt:
  Instant.now(clock)
```

The diagnostics method MUST not read Flyway, JdbcTemplate, DataSource, SQLite metadata, topology_json, broker state, provider payloads, session/user/policy data or product-facing surface state.

### D-CSA-020-006 — migrationReadiness disposition

Decision:

```text
migrationReadiness = UNKNOWN_PENDING_NORMALIZATION
source = "migration.readiness"
message = "No migration readiness read port exists; deferred to future MU."
```

Rationale:

The diagnostic field is mandatory, but no Northbound-safe migration readiness authority currently exists. Returning `UNKNOWN_PENDING_NORMALIZATION` is more precise than marking the whole diagnostics profile unsupported: the diagnostics method is supported, but this sub-surface lacks a normalized read authority.

MU-020 MUST NOT infer `READY` from the existence of a Flyway bean or from successful Spring context startup.

### D-CSA-020-007 — No new Spring bean required

Decision:

```text
No new Spring collaborator is required for MU-020.
```

`DefaultScCoreNorthboundFacade` already has the required safe collaborators for MU-020:

```text
CoreSnapshotQueryService
TemporalActApplicationPort
TemporalActObservationPort
TemporalEngineHealth
Clock
```

### D-CSA-020-008 — Boundary preservation

Decision:

```text
No external exposure, EIB implementation, View Composer implementation, SC-B runtime, SC-D adapter invocation or discovery execution may be introduced in MU-020.
```

### D-CSA-020-009 — Error/warning source field migration

Decision:

```text
Add String source to ScNorthboundError and ScNorthboundWarning.
```

Current records:

```java
public record ScNorthboundError(String code, String message) {}
public record ScNorthboundWarning(String code, String message) {}
```

Target records:

```java
public record ScNorthboundError(String code, String message, String source) {}
public record ScNorthboundWarning(String code, String message, String source) {}
```

Compile-risk:

```text
All constructor call sites must be updated in the same MU-020 patch.
```

The execution package MUST provide exact replacements listed in §7.

---

## 7. Exact call-site replacements for source field addition

### 7.1 ScNorthboundError instantiation sites

`ScNorthboundError` is instantiated only inside `ScNorthboundResponse.java` factory methods.

Total instantiation sites:

```text
6
```

Required factory method updates:

```java
static <T> ScNorthboundResponse<T> notFound(String code, String message) {
    return of(NOT_FOUND, null, List.of(), new ScNorthboundError(code, message, "northbound.query"));
}

static <T> ScNorthboundResponse<T> invalidRequest(String code, String message) {
    return of(INVALID_REQUEST, null, List.of(), new ScNorthboundError(code, message, "northbound.validation"));
}

static <T> ScNorthboundResponse<T> invalidCanonicalId(String code, String message) {
    return of(INVALID_CANONICAL_ID, null, List.of(), new ScNorthboundError(code, message, "northbound.validation"));
}

static <T> ScNorthboundResponse<T> unsupportedProfile(String code, String message) {
    return of(UNSUPPORTED_PROFILE, null, List.of(), new ScNorthboundError(code, message, "northbound.unsupported_profile"));
}

static <T> ScNorthboundResponse<T> unknownPendingNormalization(String code, String message) {
    return of(UNKNOWN_PENDING_NORMALIZATION, null, List.of(), new ScNorthboundError(code, message, "northbound.pending_normalization"));
}

static <T> ScNorthboundResponse<T> internalError(String code, String message) {
    return of(INTERNAL_ERROR, null, List.of(), new ScNorthboundError(code, message, "northbound.internal"));
}
```

The single-message overloads MAY remain unchanged if they delegate to the updated two-argument factory methods.

### 7.2 ScNorthboundWarning instantiation sites

`ScNorthboundWarning` is instantiated directly in `DefaultScCoreNorthboundFacade.java`.

Total instantiation sites:

```text
3
```

Required replacements:

```java
new ScNorthboundWarning("IDEMPOTENT_REPLAY", "Request already processed", "temporal.application")

new ScNorthboundWarning("ALREADY_TERMINAL", "Act was already in terminal state", "temporal.application")

new ScNorthboundWarning("IDEMPOTENT_REPLAY", "Cancel already recorded", "temporal.application")
```

If line numbers drift, the execution package must still search by constructor expression and update all instances.

---

## 8. Diagnostics DTOs

### 8.1 New DTO: NorthboundDiagnosticsView

Recommended file:

```text
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundDiagnosticsView.java
```

Shape:

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

Rules:

```text
habitatId is echoed from the request.
topologyVersion is TopologyVersion.value() or "UNKNOWN" if absent.
temporalEngineStatus is mapped from existing TemporalEngineHealth.
migrationReadiness is mandatory and returns UNKNOWN_PENDING_NORMALIZATION for MU-020.
readAt uses Instant.now(clock).
warnings may include explicit pending-normalization warning for migrationReadiness.
```

### 8.2 New DTO: NorthboundMigrationReadinessView

Recommended file:

```text
src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundMigrationReadinessView.java
```

Shape:

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

---

## 9. DeviceHealth implementation contract

`getDeviceHealth(habitatId, deviceId)` MUST follow this algorithm:

```text
1. Validate habitatId/deviceId according to existing Northbound validation discipline.
2. Resolve the device with queryService.findDevice(habitatId, deviceId).
3. If absent, return NOT_FOUND.
4. Extract endpointIds from deviceSnapshot.device().endpointIds().
5. If endpointIds is empty, return UNKNOWN_PENDING_NORMALIZATION.
6. For each endpointId, call queryService.findEndpointHealth(habitatId, endpointId).
7. Missing endpoint health is treated as UNKNOWN evidence.
8. Apply conservative derivation:
     - all HEALTHY -> HEALTHY
     - any OFFLINE -> OFFLINE
     - any DEGRADED / UNKNOWN / missing -> DEGRADED
9. Return OK with NorthboundDeviceHealthView.
10. readAt MUST use Instant.now(clock).
```

`NorthboundDeviceHealthView` SHOULD include at least:

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

For derived MU-020 results:

```text
source = DERIVED_FROM_ENDPOINTS
```

For empty endpoint set:

```text
status = UNKNOWN_PENDING_NORMALIZATION
source = DERIVED_FROM_ENDPOINTS
warning.source = topology.health.derived
```

---

## 10. Response vocabulary and factories

### 10.1 ScNorthboundStatus

Add:

```java
VALIDATION_ERROR
```

The execution package SHOULD add a factory method only if needed by tests or implementation:

```java
static <T> ScNorthboundResponse<T> validationError(String code, String message) {
    return of(VALIDATION_ERROR, null, List.of(), new ScNorthboundError(code, message, "northbound.validation"));
}
```

If added, behavioral or negative-boundary tests must prove it is reachable.

### 10.2 Existing invalid request distinction

`INVALID_REQUEST` and `INVALID_CANONICAL_ID` remain valid. `VALIDATION_ERROR` is a hardening vocabulary addition, not a replacement for existing statuses.

---

## 11. Test obligations for MU-020

### 11.1 Behavioral tests

Add or update tests proving:

```text
getDeviceHealth derives HEALTHY when all endpoints are HEALTHY.
getDeviceHealth derives OFFLINE when any endpoint is OFFLINE.
getDeviceHealth derives DEGRADED when any endpoint is DEGRADED, UNKNOWN or missing.
getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION when endpoint list is empty.
getDeviceHealth never uses DeviceNode.health() as authority.
getNorthboundDiagnostics returns topologyVersion.
getNorthboundDiagnostics returns temporalEngineStatus.
getNorthboundDiagnostics returns migrationReadiness UNKNOWN_PENDING_NORMALIZATION.
getNorthboundDiagnostics includes readAt.
ScNorthboundWarning source is populated.
ScNorthboundError source is populated.
VALIDATION_ERROR exists and is reachable if factory method is added.
```

### 11.2 Negative / architecture tests

Preserve and extend tests proving:

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

### 11.3 Spring wiring tests

Add or update tests proving:

```text
DefaultScCoreNorthboundFacade remains a Spring bean.
getNorthboundDiagnostics(...) is accessible through ScCoreNorthboundFacade.
No new configuration class is required unless explicitly justified.
Existing 5-constructor-arg wiring remains valid.
```

---

## 12. New/modified file scope

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

## 13. Forbidden changes

MU-020 MUST NOT:

```text
Add HTTP/OpenAPI/SSE exposure.
Add gRPC/ConnectRPC exposure.
Add MCP facade.
Add GraphQL schema or resolver.
Add WebSocket endpoint.
Add SC-B runtime.
Add NATS / JetStream dependency.
Add SC-D adapter invocation.
Add discovery execution.
Add provider scanning/polling.
Add command dispatch.
Add ActionTemporalPayload execution.
Add recurrence/cron/calendar behavior.
Expose topology_json.
Read Flyway directly from Northbound.
Read JdbcTemplate/DataSource directly from Northbound.
Expose SQLite/Flyway implementation details as public DTO contract.
Implement EIB or View Composer.
Expose product-facing Hub/SApp/Surface APIs.
```

---

## 14. Retained debt after MU-020

If MU-020 executes according to this CSA, expected debt state is:

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

---

## 15. MIR readiness decisions

MIR-020 should record:

```text
D-MIR-020-001 — DeviceHealth DERIVED_FROM_ENDPOINTS is authorized.
D-MIR-020-002 — EndpointRuntimeState remains UNSUPPORTED_PROFILE.
D-MIR-020-003 — RecoveryStatus remains UNSUPPORTED_PROFILE.
D-MIR-020-004 — getNorthboundDiagnostics(...) is authorized.
D-MIR-020-005 — migrationReadiness returns UNKNOWN_PENDING_NORMALIZATION.
D-MIR-020-006 — ScNorthboundError/Warning source field migration is authorized.
D-MIR-020-007 — VALIDATION_ERROR is added to ScNorthboundStatus.
D-MIR-020-008 — no external exposure, EIB, SC-B runtime, SC-D invocation or discovery execution is authorized.
```

---

## 16. Approvability

```text
CSA result: Approvable for MIR-020.
```

Required before execution package:

```text
1. MIR-020 must adopt the CSA decisions above.
2. Execution package must include exact call-site replacements for source migration.
3. Execution package must specify DeviceHealth derivation algorithm exactly.
4. Execution package must specify getNorthboundDiagnostics(...) DTOs and field mapping exactly.
5. Execution package must require fresh mvn test.
6. Execution package must preserve all boundary exclusions.
```

