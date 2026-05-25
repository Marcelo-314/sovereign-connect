# codex-prompt.md — MIR-019 Northbound Facade Seed

You are implementing `MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001` for Sovereign Connect.

Read these files first:

```text
docs/mir/mir-019/MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001.md
docs/mir/mir-019/code-surface-audit.md
docs/mir/mir-019/context.md
docs/mir/mir-019/acceptance-map.md
```

Your task is to implement the first SC-C Northbound Facade seed as an in-process canonical facade only.

---

## Hard scope

Implement only:

```text
Profile A — SC-C Local Observation
Profile B — selected SC-C TemporalAct local runtime requests
```

Do not implement:

```text
HTTP controllers
Spring Web exposure
SSE
MCP
gRPC
ConnectRPC
WebSocket
GraphQL
NATS
JetStream
SC-B runtime
SC-D adapter runtime
EIB
View Composer
Hub/SApp/Surface direct integration
discovery methods
discovery execution
ActionTemporalPayload command scheduling
provider command dispatch
Flyway migrations
SQLite repository redesign
DeviceHealth normalized persistence
endpoint runtime-state persistence
```

---

## Create package

Create a new package rooted at:

```text
src/main/java/com/sovereign/connect/core/northbound/
```

Target files:

```text
ScCoreNorthboundFacade.java
DefaultScCoreNorthboundFacade.java
ScNorthboundResponse.java
ScNorthboundStatus.java
ScNorthboundError.java
ScNorthboundWarning.java
NorthboundMapper.java
```

Create DTO packages:

```text
src/main/java/com/sovereign/connect/core/northbound/topology/
src/main/java/com/sovereign/connect/core/northbound/runtime/
src/main/java/com/sovereign/connect/core/northbound/temporal/
```

DTO target names:

```text
topology/NorthboundTopologySnapshot.java
topology/NorthboundTopologyVersionView.java
topology/NorthboundRoomView.java
topology/NorthboundZoneView.java
topology/NorthboundDeviceView.java
topology/NorthboundEndpointView.java
topology/NorthboundCapabilityView.java
topology/NorthboundLocationQuery.java

runtime/NorthboundEndpointHealthView.java
runtime/NorthboundDeviceHealthView.java
runtime/NorthboundRuntimeStateView.java
runtime/NorthboundTemporalRuntimeStatusView.java
runtime/NorthboundRecoveryStatusView.java

temporal/NorthboundCreateSignalTemporalActRequest.java
temporal/NorthboundCancelTemporalActRequest.java
temporal/NorthboundTemporalActView.java
temporal/NorthboundTemporalActFilter.java
```

Use Java records where appropriate.

Do not return raw domain records as the public Northbound facade contract.

---

## Implement facade interface

Introduce:

```java
public interface ScCoreNorthboundFacade {
    ScNorthboundResponse<NorthboundTopologySnapshot> getTopologySnapshot(String habitatId);
    ScNorthboundResponse<NorthboundTopologyVersionView> getTopologyVersion(String habitatId);

    ScNorthboundResponse<NorthboundDeviceView> getDevice(String habitatId, String deviceId);
    ScNorthboundResponse<NorthboundEndpointView> getEndpoint(String habitatId, String endpointId);

    ScNorthboundResponse<List<NorthboundRoomView>> listRooms(String habitatId);
    ScNorthboundResponse<List<NorthboundZoneView>> listZones(String habitatId);
    ScNorthboundResponse<List<NorthboundDeviceView>> listDevices(String habitatId);
    ScNorthboundResponse<List<NorthboundEndpointView>> listEndpoints(String habitatId);

    ScNorthboundResponse<List<NorthboundDeviceView>> listDevicesLocatedIn(String habitatId, String roomOrZoneId);
    ScNorthboundResponse<List<NorthboundEndpointView>> listEndpointsLocatedIn(String habitatId, String roomOrZoneId);

    ScNorthboundResponse<NorthboundEndpointHealthView> getEndpointHealth(String habitatId, String endpointId);
    ScNorthboundResponse<NorthboundDeviceHealthView> getDeviceHealth(String habitatId, String deviceId);
    ScNorthboundResponse<NorthboundRuntimeStateView> getDeviceRuntimeState(String habitatId, String deviceId);
    ScNorthboundResponse<NorthboundRuntimeStateView> getEndpointRuntimeState(String habitatId, String endpointId);
    ScNorthboundResponse<NorthboundRecoveryStatusView> getRecoveryStatus(String habitatId);
    ScNorthboundResponse<NorthboundTemporalRuntimeStatusView> getTemporalRuntimeStatus(String habitatId);

    ScNorthboundResponse<NorthboundTemporalActView> createSignalTemporalAct(
        String habitatId,
        NorthboundCreateSignalTemporalActRequest request
    );

    ScNorthboundResponse<NorthboundTemporalActView> cancelTemporalAct(
        String habitatId,
        NorthboundCancelTemporalActRequest request
    );

    ScNorthboundResponse<NorthboundTemporalActView> getTemporalAct(String habitatId, String temporalActId);
    ScNorthboundResponse<List<NorthboundTemporalActView>> listTemporalActs(
        String habitatId,
        NorthboundTemporalActFilter filter
    );
}
```

You may refine DTO field details as needed, but do not broaden scope.

---

## Implement response envelope

Introduce:

```java
public record ScNorthboundResponse<T>(
    ScNorthboundStatus status,
    T payload,
    List<ScNorthboundWarning> warnings,
    ScNorthboundError error
) {}
```

Add static factories if useful, for example:

```java
ok(T payload)
created(T payload)
accepted(T payload)
cancelled(T payload)
notFound(String code, String message)
invalidRequest(String code, String message)
invalidCanonicalId(String code, String message)
unsupportedProfile(String code, String message)
unknownPendingNormalization(String code, String message)
internalError(String code, String message)
```

Required enum values:

```java
OK,
CREATED,
ACCEPTED,
CANCELLED,
NOT_FOUND,
INVALID_REQUEST,
INVALID_CANONICAL_ID,
UNSUPPORTED_PROFILE,
DEFERRED_SC_B_REQUIRED,
UNKNOWN_PENDING_NORMALIZATION,
INTERNAL_ERROR
```

Do not use SC-B `ScResponseEnvelope<TResponse>`.

---

## Implement default facade

Create:

```java
@Service
public class DefaultScCoreNorthboundFacade implements ScCoreNorthboundFacade {
    private final CoreSnapshotQueryService queryService;
    private final TemporalActApplicationPort temporalActApplicationPort;
    private final TemporalActObservationPort temporalActObservationPort;
    private final TemporalEngineHealth engineHealth;
    private final Clock clock;

    public DefaultScCoreNorthboundFacade(
        CoreSnapshotQueryService queryService,
        TemporalActApplicationPort temporalActApplicationPort,
        TemporalActObservationPort temporalActObservationPort,
        TemporalEngineHealth engineHealth,
        Clock clock
    ) { ... }
}
```

Use constructor injection.

Do not inject repositories, `JdbcTemplate`, `DataSource`, `BaseTopologyService`, SC-B classes or SC-D adapter classes.

---

## Delegation requirements

Implement exactly this delegation behavior:

```text
getTopologySnapshot -> queryService.findCurrentSnapshot(...)
getTopologyVersion -> queryService.findCurrentTopologyVersion(...)
getDevice -> queryService.findDevice(...)
getEndpoint -> queryService.findEndpoint(...)
listRooms -> queryService.findCurrentSnapshot(...).topology().rooms()
listZones -> queryService.findCurrentSnapshot(...).topology().zones()
listDevices -> queryService.findCurrentSnapshot(...).topology().devices()
listEndpoints -> queryService.findCurrentSnapshot(...).topology().endpoints()
listDevicesLocatedIn -> queryService.findLocatedDevices(...)
listEndpointsLocatedIn -> queryService.findLocatedEndpoints(...)
getEndpointHealth -> queryService.findEndpointHealth(...)
getDeviceHealth -> UNKNOWN_PENDING_NORMALIZATION
getDeviceRuntimeState -> queryService.findDeviceState(...)
getEndpointRuntimeState -> UNSUPPORTED_PROFILE unless durable endpoint runtime-state port exists
getTemporalRuntimeStatus -> TemporalEngineHealth
getRecoveryStatus -> TemporalEngineHealth proxy or UNSUPPORTED_PROFILE
createSignalTemporalAct -> temporalActApplicationPort.createSignalTemporalAct(...)
cancelTemporalAct -> temporalActApplicationPort.cancelTemporalAct(...)
getTemporalAct -> temporalActObservationPort.findById(...)
listTemporalActs -> supported TemporalActObservationPort list methods only
```

---

## DeviceHealth rule

For MU-019:

```text
getDeviceHealth must return UNKNOWN_PENDING_NORMALIZATION.
```

Do not implement `DERIVED_FROM_ENDPOINTS`.

Do not return `DeviceNode.health()` as current durable device health.

Do not read `devices.device_health_*` as current device-health authority.

---

## EndpointHealth rule

`getEndpointHealth` must use `CoreSnapshotQueryService.findEndpointHealth(...)`.

Do not use endpoint aggregate fallback health as durable evidence.

---

## Temporal request mapping

Map `NorthboundCreateSignalTemporalActRequest` to existing `CreateSignalTemporalActRequest`:

```java
new CreateSignalTemporalActRequest(
    habitatId,
    request.dueAt(),
    request.label(),
    request.signalKind(),
    request.notificationTargetRef(),
    request.createdByRef(),
    request.idempotencyKey(),
    Instant.now(clock)
)
```

Map `NorthboundCancelTemporalActRequest` to existing `CancelTemporalActRequest`:

```java
new CancelTemporalActRequest(
    habitatId,
    request.temporalActId(),
    request.requestedByRef(),
    request.idempotencyKey(),
    request.reason(),
    Instant.now(clock)
)
```

Map sealed result types to `ScNorthboundResponse`:

```text
CreateSignalTemporalActResult.Accepted -> ACCEPTED or CREATED
CreateSignalTemporalActResult.IdempotentReplay -> OK or ACCEPTED with warning
CreateSignalTemporalActResult.Rejected -> INVALID_REQUEST
CreateSignalTemporalActResult.Failed -> INTERNAL_ERROR

CancelTemporalActResult.Cancelled -> CANCELLED
CancelTemporalActResult.AlreadyTerminal -> OK with warning
CancelTemporalActResult.NotFound -> NOT_FOUND
CancelTemporalActResult.IdempotentReplay -> OK or CANCELLED with warning
CancelTemporalActResult.Rejected -> INVALID_REQUEST
CancelTemporalActResult.Failed -> INTERNAL_ERROR
```

Prefer explicit warnings for idempotent replay and already-terminal cases.

---

## TemporalAct filter

Implement `NorthboundTemporalActFilter` only over existing observation methods:

```text
ACTIVE -> listActive(habitatId)
TERMINAL -> listTerminal(habitatId, maxResults)
MISFIRED -> listMisfired(habitatId, maxResults)
null filter or null mode -> ACTIVE
```

Do not implement `ALL_SUPPORTED` or combined temporal listing in MU-019.
For TERMINAL and MISFIRED, use a conservative default maxResults of 50 when absent or invalid; you may cap very large values to a local safety bound.
Do not invent unsupported query semantics.

---

## Tests to add

Use the corrected representative T-3 name `createSignalTemporalActMapsAllResultVariantsCorrectly` for CreateSignalTemporalActResult variant coverage. Do not use the old copy-paste name `createSignalTemporalActMapsCancelledVariantsCorrectly`.


Add tests under:

```text
src/test/java/com/sovereign/connect/core/northbound/
```

Target classes:

```text
NorthboundFacadeBehavioralTest.java
NorthboundFacadeNegativeBoundaryTest.java
NorthboundFacadeSpringContextTest.java
NorthboundFacadeArchitectureTest.java
```

Required coverage:

```text
getTopologySnapshot returns canonical topology with topologyVersion
getTopologyVersion returns current topologyVersion
getDevice resolves canonical deviceId
getEndpoint resolves canonical endpointId
provider-native IDs are rejected or do not resolve as canonical lookup authority
listRooms/listZones/listDevices/listEndpoints expose Base Topology only
listDevicesLocatedIn/listEndpointsLocatedIn use canonical location query path
getEndpointHealth returns durable endpoint health
getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION
getDeviceRuntimeState delegates to durable device-state read surface
getEndpointRuntimeState is UNSUPPORTED_PROFILE
getTemporalRuntimeStatus maps TemporalEngineHealth
createSignalTemporalAct delegates to TemporalActApplicationPort
cancelTemporalAct delegates to TemporalActApplicationPort
getTemporalAct delegates to TemporalActObservationPort.findById
listTemporalActs maps only supported filter modes
common negative cases return ScNorthboundResponse statuses
```

Negative/source-level tests must verify:

```text
northbound package has no HTTP controller annotations
northbound package has no Spring Web imports
northbound package has no GraphQL imports/classes
northbound package has no gRPC/ConnectRPC imports/classes
northbound package has no MCP imports/classes
northbound package has no WebSocket imports/classes
northbound package has no NATS/JetStream imports/classes
northbound package does not import SC-D adapter classes
northbound package does not import persistence adapter classes
northbound package does not use JdbcTemplate/DataSource
northbound DTO names do not use Effective/Projected/Surface/Session/Policy/Authority
ScCoreNorthboundFacade has no discovery methods
Northbound DTOs do not expose Effective View, VisibilityRule, Session, Identity, Authority, Policy or Surface metadata
```

Spring wiring tests must verify:

```text
ScCoreNorthboundFacade bean exists
ScCoreNorthboundFacade is backed by DefaultScCoreNorthboundFacade
required collaborators resolve from the Spring context
no HTTP controller bean is introduced
no conflicting facade implementation is registered
```

---

## Files that should not change

Do not modify unless absolutely necessary and justified in the implementation report:

```text
existing service interfaces
existing repository contracts
V1/V2/V3/V4 Flyway migrations
TemporalEngineConfiguration
TopologyPersistenceConfiguration
SC-B contracts
SC-D materialization/fact contracts
HTTP/MCP/gRPC/WebSocket/GraphQL exposure layers
```

---

## Validation

Run:

```bash
mvn test
```

The implementation is not acceptable unless the full test suite passes.

Update:

```text
docs/mir/mir-019/implementation-report.md
```

Record:

```text
branch name
commit hash if available
final file list
facade method summary
exact delegation map
status mapping table
DeviceHealth strategy confirmation
EndpointHealth authority confirmation
TemporalAct filter semantics
negative boundary scan summary
Spring wiring summary
full test command
full test result
AC-019-001 through AC-019-033 result table
open retained debts
justified deviations, if any
```

Retained open items that must be explicitly recorded:

```text
1. getEndpointRuntimeState -> UNSUPPORTED_PROFILE unless a durable endpoint runtime-state port exists.
2. getRecoveryStatus -> proxy/limited status or UNSUPPORTED_PROFILE until explicit recovery read model exists.
3. DeviceHealth normalized authority / derivation debt remains open.
```
