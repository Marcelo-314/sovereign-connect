# Code Surface Audit — MU-019 Northbound Facade Seed

```text
Document ID:  CSA-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Title:        Code Surface Audit — SC-C Northbound Facade Seed
Version:      v0.2.0-draft
Status:       Merged CSA / execution-package-ready with bounded decisions
Date:         2026-05-24
Corpus:       Sovereign Connect
Plane:        SC-C
MU:           MU-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Slot:         MU-019
Baseline:     sovereign-connect-develop-csa.zip
Repo branch:  fix/sc-c-mir-018-storage-legacy-cleanup
Repo HEAD:    e9ba036
Tests:        168 / 0 failures / 0 errors / 0 skipped, from archived Surefire reports
Sources:      merged from two independent CSA drafts over the same service snapshot
```

---

## 0. Purpose

This document is the merged Code Surface Audit for `MU-SOV-SC-C-NORTHBOUND-FACADE-SEED-001`.

It performs the pre-MIR read of the actual code surface before producing the execution package for:

```text
MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
```

Its goals are to:

1. identify the existing services, ports, DTOs, persistence surfaces and tests available for the Northbound Facade seed;
2. decide which SDD open questions must be closed before Codex implementation;
3. prevent authority leakage from persistence adapters, aggregate snapshots, `topology_json`, legacy in-memory services, transport technology or SC-D materialization internals;
4. constrain the implementation scope to an in-process canonical facade over SC-C-owned state and selected SC-C-owned runtime requests;
5. provide precise implementation constraints for `context.md`, `codex-prompt.md` and `acceptance-map.md`.

---

## 1. Final CSA verdict

```text
CSA result: APPROVABLE FOR MIR / EXECUTION PACKAGE.
Implementation risk: moderate.
Transport leakage risk: low.
Profile-C leakage risk: low if execution package forbids discovery methods/imports.
Main implementation risks: health authority, device-state authority, DTO discipline and filter semantics.
```

The current service surface is fit for descent into `MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001`, provided the first implementation remains strictly:

```text
in-process
canonical
SC-C-owned
transport-neutral
EIB-intended
adapter-free
broker-free
projection-free
Profile A + selected Profile B only
```

No evidence was found that the current main source already exposes HTTP controllers, gRPC, MCP, GraphQL, WebSocket, NATS or JetStream APIs.

No `northbound` package exists yet. The MU must create the facade layer from scratch over existing SC-C services and ports.

The main design risk is not transport leakage. The main risk is authority leakage inside SC-C reads: accidentally exposing structural/aggregate `DeviceHealth`, aggregate fallback endpoint health, legacy in-memory device state or raw domain records as if they were the canonical Northbound contract.

---

## 2. Audit method and limitations

### 2.1 Static inspection performed

The audit inspected the service archive:

```text
sovereign-connect-develop-csa.zip
```

The inspected surfaces include:

```text
pom.xml
src/main/java/com/sovereign/connect/**
src/main/resources/db/migration/**
src/test/java/com/sovereign/connect/**
target/surefire-reports/**
```

### 2.2 Test evidence available in archive

Archived Surefire reports record:

```text
18 test suites
168 tests
0 failures
0 errors
0 skipped
```

The suites include topology persistence, snapshot query, kernel hardening, temporal engine, storage legacy cleanup and ledger/outbox storage seed tests.

### 2.3 Execution limitation

A fresh `mvn test` was not executable in the audit container because Maven was not installed:

```text
mvn: command not found
```

Therefore, this CSA treats existing `target/surefire-reports` as snapshot evidence, not as a fresh validation run.

The execution package should still require Codex / local developer execution of:

```text
mvn test
```

before accepting the implementation report.

---

## 3. Existing code surface summary

### 3.1 No Northbound package exists yet

No files were found under:

```text
src/main/java/**/northbound/**
src/test/java/**/northbound/**
```

Implications:

```text
The MIR implementation must introduce the Northbound facade package, DTOs, envelope, implementation and tests.
There is no pre-existing Northbound implementation to patch.
There is no naming conflict with an existing ScCoreNorthboundFacade.
```

### 3.2 Main source packages relevant to Northbound

Relevant topology packages:

```text
com.sovereign.connect.core.topology.model
com.sovereign.connect.core.topology.port
com.sovereign.connect.core.topology.query
com.sovereign.connect.core.topology.service
com.sovereign.connect.adapter.persistence.sqlite
com.sovereign.connect.config
```

Relevant temporal packages:

```text
com.sovereign.connect.core.temporal.application
com.sovereign.connect.core.temporal.observation
com.sovereign.connect.core.temporal.port
com.sovereign.connect.core.temporal.engine
com.sovereign.connect.core.temporal.service
com.sovereign.connect.adapter.persistence.sqlite
com.sovereign.connect.config
```

### 3.3 Main dependencies

`pom.xml` currently has:

```text
Spring Boot starter
Spring Boot JDBC
Jackson JSR310
SQLite JDBC
Flyway core
Flyway SQLite support
Jackson annotations
Spring Boot test
H2 test scope only
```

No NATS, JetStream, gRPC, GraphQL, MCP, WebSocket or Spring Web dependency was found in the main dependency set.

### 3.4 Confirmed non-issues

```text
No H2 in main source after MU-018 cleanup.
No fake Jackson annotations after MU-018 cleanup.
No northbound package exists yet.
No existing ScCoreNorthboundFacade interface exists.
DEFERRED_SC_B_REQUIRED and UNKNOWN_PENDING_NORMALIZATION are not yet defined and must be created.
```

---

## 4. Forbidden exposure technology scan

### 4.1 Main source scan

Search terms:

```text
@RestController
@Controller
@MessageMapping
org.springframework.web
GraphQL / graphql
gRPC / grpc / Grpc
MCP / mcp
WebSocket / websocket
NATS / io.nats
JetStream
```

Result:

```text
No forbidden exposure technology occurrences in src/main/java.
```

Only expected Spring configuration annotations were found:

```text
@Configuration
@Bean
@Primary
@DependsOn
@ConfigurationProperties
```

### 4.2 Test source scan

A test mentions `io.nats` / `JetStream` as forbidden strings in a boundary assertion, not as a dependency or implementation use.

Result:

```text
No blocking technology leak found.
```

### 4.3 Execution-package consequence

The implementation MUST NOT introduce:

```text
HTTP controllers
Spring Web imports
MCP tools/resources/prompts
gRPC / ConnectRPC services
WebSocket endpoints or subscriptions
GraphQL schema/resolvers
NATS / JetStream clients or concepts
SC-D adapter APIs
Persistence adapter dependencies inside northbound package
```

---

## 5. Topology observation surface

### 5.1 Primary read abstraction exists

`CoreSnapshotReadPort` exposes the relevant read boundary:

```java
Optional<BaseTopologySnapshot> findSnapshot(String habitatId);
Optional<TopologyVersion> findCurrentVersion(String habitatId);
Optional<HabitatBaseTopology> findTopology(String habitatId);
Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);
Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);
Optional<RoomNode> findRoom(String habitatId, String roomId);
Optional<ZoneNode> findZone(String habitatId, String zoneId);
Optional<TopologySpatialRelation> findSpatialRelation(String habitatId, String relationId);
List<TopologySpatialRelation> findSpatialRelationsBySubject(...);
List<DeviceNode> findLocatedDevices(String habitatId, String roomOrZoneId);
List<EndpointNode> findLocatedEndpoints(String habitatId, String roomOrZoneId);
Optional<TopologySpatialRelation> resolvePrimaryPlacement(...);
```

Decision:

```text
The Northbound facade should depend on CoreSnapshotQueryService, not on SQLite repositories directly.
CoreSnapshotQueryService depends on CoreSnapshotReadPort.
```

### 5.2 Query service exists

`CoreSnapshotQueryService` is available as a Spring bean via `TopologyPersistenceConfiguration` and exposes:

```java
Optional<CoreSnapshot> findCurrentSnapshot(String habitatId);
Optional<TopologyVersion> findCurrentTopologyVersion(String habitatId);
Optional<DeviceSnapshot> findDevice(String habitatId, String deviceId);
Optional<EndpointSnapshot> findEndpoint(String habitatId, String endpointId);
Optional<Map<String,Object>> findDeviceState(String habitatId, String deviceId);
Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);
Optional<RoomNode> findRoom(String habitatId, String roomId);
Optional<ZoneNode> findZone(String habitatId, String zoneId);
Optional<TopologySpatialRelation> findSpatialRelation(String habitatId, String relationId);
List<TopologySpatialRelation> findSpatialRelationsBySubject(...);
List<DeviceNode> findLocatedDevices(String habitatId, String roomOrZoneId);
List<EndpointNode> findLocatedEndpoints(String habitatId, String roomOrZoneId);
Optional<TopologySpatialRelation> resolvePrimaryPlacement(...);
```

This is sufficient for the Profile A topology subset.

### 5.3 Snapshot result types

The code already provides rich snapshot types:

```java
CoreSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    HabitatBaseTopology topology,
    Map<String, Map<String, Object>> deviceStates,
    Map<String, EndpointHealth> endpointHealth,
    Instant readAt
)
```

```java
DeviceSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    DeviceNode device,
    Map<String, Object> state,
    Instant readAt
)
```

```java
EndpointSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    EndpointNode endpoint,
    EndpointHealth health, // may contain durable value or aggregate fallback
    Instant readAt
)
```

The facade should map:

```text
DeviceSnapshot -> NorthboundDeviceView
EndpointSnapshot -> NorthboundEndpointView
CoreSnapshot -> NorthboundTopologySnapshot
```

It should not return raw domain records as the public Northbound contract.

### 5.4 Normalized topology authority exists

`SQLiteBaseTopologyRepository` implements both:

```java
BaseTopologyRepository
CoreSnapshotReadPort
```

It persists normalized tables:

```text
topology_versions
topology_metadata
rooms
zones
devices
endpoints
capabilities
topology_spatial_relations
topology_snapshots
mutation_records
device_states
endpoint_health
materialization_decision_replay
```

The normalized read path reconstructs `HabitatBaseTopology` from normalized tables, not from `topology_json`.

Evidence from code behavior:

```text
findByHabitatId(...) loads rooms, zones, devices, endpoints, capabilities, endpoint health and spatial relations from normalized tables.
findSnapshot(...) calls findByHabitatId(...) and uses topology_snapshots only for captured_at_ms fallback.
```

A test intentionally corrupts `topology_snapshots.topology_json` and verifies query behavior still works. This supports the SDD rule that `topology_json` is compatibility snapshot, not authority.

### 5.5 List operations gap and resolution

Finding:

```text
Neither CoreSnapshotReadPort nor CoreSnapshotQueryService exposes named listRooms(habitatId), listZones(habitatId), listDevices(habitatId), or listEndpoints(habitatId) methods.
```

Resolution for MU-019:

```text
Do not add new port methods for listRooms/listZones/listDevices/listEndpoints.
Implement these facade methods by reading the full topology snapshot and extracting lists from HabitatBaseTopology.
```

Rationale:

```text
HabitatBaseTopology returned inside CoreSnapshot already carries rooms(), zones(), devices(), endpoints() and spatialRelations() as immutable lists.
This is acceptable for the first in-process seed and expected v1 habitat scale.
```

Canonical example:

```java
public ScNorthboundResponse<List<NorthboundRoomView>> listRooms(String habitatId) {
    return queryService.findCurrentSnapshot(habitatId)
        .map(snapshot -> ScNorthboundResponse.ok(
            snapshot.topology().rooms().stream()
                .map(NorthboundMapper::toRoomView)
                .toList()))
        .orElse(ScNorthboundResponse.notFound("no topology found for habitat"));
}
```

The same pattern applies to:

```text
listZones
listDevices
listEndpoints
```

### 5.6 DTO warning

`CoreSnapshot` currently contains `HabitatBaseTopology` directly. `DeviceSnapshot` contains `DeviceNode`. `EndpointSnapshot` contains `EndpointNode`.

For Northbound, the MIR must not return these domain records directly as the public facade DTOs.

Decision:

```text
Use dedicated Northbound DTOs.
Do not expose raw CoreSnapshot, HabitatBaseTopology, DeviceNode, EndpointNode, RoomNode, ZoneNode or CapabilityNode as the public facade contract.
```

Reason:

```text
Returning domain records directly would leak internal aggregate shape and make future HTTP/gRPC/ConnectRPC binding harder to govern.
Dedicated DTOs also prevent Effective View / Projection / Session / Policy concepts from entering the SC-C canonical surface later by accident.
```

---

## 6. Health and runtime surface

### 6.1 EndpointHealth is implementation-ready as durable surface

The durable endpoint health authority exists:

```text
endpoint_health table
SQLiteEndpointHealthRepository.saveEndpointHealth(...)
SQLiteEndpointHealthRepository.findEndpointHealth(...)
SQLiteEndpointHealthRepository.findEndpointHealthByHabitat(...)
SQLiteBaseTopologyRepository.findEndpointHealth(...)
CoreSnapshotReadPort.findEndpointHealth(...)
CoreSnapshotQueryService.findEndpointHealth(...)
```

Decision:

```text
getEndpointHealth(...) may be implemented in the first seed.
It MUST read through CoreSnapshotQueryService.findEndpointHealth(...).
```

### 6.2 Endpoint health fallback risk

`CoreSnapshotQueryService.findEndpoint(...)` resolves endpoint health as:

```java
readPort.findEndpointHealth(habitatId, endpointId)
    .orElse(endpoint.get().health())
```

This may be acceptable for legacy snapshot hydration, but it is not strict enough for a `getEndpointHealth(...)` facade method whose contract claims durable endpoint health.

Decision:

```text
getEndpointHealth(...) MUST call CoreSnapshotQueryService.findEndpointHealth(...) directly.
Endpoint view mapping MAY include health only if the source is explicitly durable or marked unknown/deferred.
```

Recommended DTO rule:

```text
If NorthboundEndpointView includes health, include a source marker or map only from direct durable findEndpointHealth evidence.
Do not silently expose aggregate fallback health as durable endpoint health.
```

### 6.3 DeviceHealth is not implementation-ready as durable surface

The code has `DeviceHealth` in `DeviceNode`, and SQLite stores device health columns in the `devices` table:

```text
device_health_status
device_health_last_seen_ms
device_health_details
```

These fields are persisted during structural save and are accessible via:

```text
DeviceSnapshot.device().health()
DeviceNode.health()
```

However:

```text
DeviceHealth.status() reflects the health embedded in the structural aggregate at last save() time.
It is not independently maintained through a dedicated DeviceHealthWritePort.
There is no normalized device_health table equivalent to endpoint_health.
There is no durable device-health port equivalent to endpoint health.
```

Decision for MU-019:

```text
Strategy B — UNKNOWN_PENDING_NORMALIZATION.
getDeviceHealth(...) MUST return UNKNOWN_PENDING_NORMALIZATION in the first seed.
Do not expose DeviceNode.health() or devices.device_health_* as authoritative Northbound DeviceHealth.
```

This closes:

```text
OQ-SDD-NBF-005 — First-seed device health strategy
```

### 6.4 HealthStatus vocabulary mismatch

The SDD derivation rule references:

```text
UNHEALTHY
```

The current code enum is:

```java
HEALTHY,
DEGRADED,
OFFLINE,
UNKNOWN
```

There is no `UNHEALTHY` value.

Decision:

```text
DERIVED_FROM_ENDPOINTS is blocked for MU-019 unless the execution package explicitly maps OFFLINE -> UNHEALTHY-equivalent or the SDD/PDR vocabulary is patched.
For first seed, choose UNKNOWN_PENDING_NORMALIZATION for device health.
```

Future resolution:

```text
Patch SDD/PDR to map OFFLINE as the code-level equivalent of UNHEALTHY, or introduce a canonical UNHEALTHY/UNAVAILABLE vocabulary decision.
Then implement DERIVED_FROM_ENDPOINTS with behavioral coverage.
```

### 6.5 Device runtime state can be exposed only through durable read port

There are two device-state surfaces:

1. Durable SQLite state:

```text
device_states table
CoreSnapshotReadPort.findDeviceState(...)
CoreSnapshotQueryService.findDeviceState(...)
SQLiteTopologyMaterializationStateRepository.saveDeviceState(...)
SQLiteTopologyMaterializationStateRepository.findDeviceState(...)
```

2. Legacy in-process state inside `BaseTopologyService`:

```java
private final ConcurrentMap<String, Map<String, Object>> deviceStates = new ConcurrentHashMap<>();
updateDeviceState(...)
findDeviceState(...)
```

Decision:

```text
Northbound MUST NOT use BaseTopologyService.findDeviceState(...).
Northbound MAY expose getDeviceRuntimeState(...) only through CoreSnapshotQueryService.findDeviceState(...).
```

### 6.6 Endpoint runtime state not yet independently available

No dedicated endpoint runtime state table/port was found. Endpoint health exists; endpoint runtime state does not.

Decision:

```text
getEndpointRuntimeState(...) should return UNSUPPORTED_PROFILE or UNKNOWN_PENDING_NORMALIZATION in first seed.
Do not fabricate endpoint runtime state from endpoint health.
```

Recommended first-seed behavior:

```text
Return UNSUPPORTED_PROFILE for getEndpointRuntimeState(...), unless the execution package deliberately defines UNKNOWN_PENDING_NORMALIZATION as the preferred status for valid-but-deferred runtime observations.
```

---

## 7. TemporalAct / Profile B surface

### 7.1 Application command port exists

`TemporalActApplicationPort` is available as a Spring bean and exposes:

```java
CreateSignalTemporalActResult createSignalTemporalAct(CreateSignalTemporalActRequest request);
CancelTemporalActResult cancelTemporalAct(CancelTemporalActRequest request);
```

This is sufficient for selected Profile B request operations.

### 7.2 Request records and exact fields

`CreateSignalTemporalActRequest` contains:

```java
String habitatId;
Instant dueAt;
String label;
String signalKind;
String notificationTargetRef;
String createdByRef;
String idempotencyKey;
Instant requestedAt;
```

`CancelTemporalActRequest` contains:

```java
String habitatId;
String temporalActId;
String requestedByRef;
String idempotencyKey;
String reason;
Instant requestedAt;
```

These fields are SC-C-owned and aligned with the SDD-allowed Profile B request fields.

### 7.3 Observation port exists

`TemporalActObservationPort` is available as a Spring bean and exposes:

```java
List<TemporalActObservation> listActive(String habitatId);
Optional<TemporalActObservation> findById(String habitatId, String temporalActId);
List<TemporalActObservation> listTerminal(String habitatId, int maxResults);
List<TemporalActObservation> listMisfired(String habitatId, int maxResults);
```

`TemporalActObservation` contains:

```java
String temporalActId;
String habitatId;
TemporalActStatus status;
Instant dueAt;
String payloadKind;
String label;
String signalKind;
String notificationTargetRef;
String createdByRef;
Instant createdAt;
Instant updatedAt;
Instant firedAt;
Instant terminalAt;
String terminalReason;
```

This supports:

```text
getTemporalAct
listTemporalActs with constrained filters
```

### 7.4 Temporal runtime health exists

`TemporalEngineHealth` is available as a Spring bean via `TemporalEngineConfiguration` and exposes:

```java
TemporalEngineStatus status();
boolean isReady();
Instant lastPollAt();
Instant lastSuccessfulPollAt();
RuntimeException lastFailure();
long firedTotal();
long misfiredTotal();
long cancelledTotal();
long failedTotal();
long skippedTotal();
```

This supports:

```text
getTemporalRuntimeStatus(...)
```

### 7.5 Recovery status surface

`TemporalRecoveryObservationPort` and `SQLiteTemporalRecoveryObservationRepository` exist and the port is wired in `TemporalEngineConfiguration`.

However:

```text
No query-side port exists for the facade to read a last recovery result as a stable Northbound recovery status.
```

Decision:

```text
getRecoveryStatus(...) should return UNSUPPORTED_PROFILE in the first seed, or derive a clearly marked coarse proxy from TemporalEngineHealth.status().
```

Preferred first-seed behavior:

```text
Return UNSUPPORTED_PROFILE for getRecoveryStatus(...), unless the execution package explicitly defines a coarse temporal-engine recovery proxy and labels it as non-authoritative.
```

### 7.6 Profile B allowed subset

The first seed may implement:

```text
createSignalTemporalAct
cancelTemporalAct
getTemporalAct
listTemporalActs
getTemporalRuntimeStatus
```

The first seed must not implement:

```text
ActionTemporalPayload
provider-level command scheduling
device command dispatch
SC-B outbox dispatcher control
recurrence/cron
policy/authority scheduling
surface countdown UX
```

### 7.7 NorthboundCreateSignalTemporalActRequest mapping

The facade inbound request maps directly to the existing `CreateSignalTemporalActRequest`.

Canonical mapping:

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

The facade must inject `Clock` for `requestedAt` construction.

### 7.8 Cancel request mapping

Canonical mapping:

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

### 7.9 TemporalAct filter constraint

The existing observation port has explicit read categories:

```text
listActive(habitatId)
listTerminal(habitatId, maxResults)
listMisfired(habitatId, maxResults)
```

Decision:

```text
NorthboundTemporalActFilter must map only to these supported categories.
Do not create arbitrary query semantics in the first seed.
```

Execution-package decision:

```text
Use a constrained enum filter with ACTIVE, TERMINAL and MISFIRED only.
null filter or null mode defaults to ACTIVE.
ALL_SUPPORTED / combined temporal listing is deferred beyond MU-019.
```

---

## 8. Discovery / Profile C surface

### 8.1 Existing materialization discovery facts are not Northbound discovery lifecycle

The codebase contains topology materialization and discovery fact classes such as:

```text
DeviceDiscoveryFact
EndpointDiscoveryFact
CapabilityDiscoveryFact
RoomDiscoveryFact
ZoneDiscoveryFact
HealthFact
DefaultTopologyMaterializationService
TopologyMaterializationService
```

These are not Northbound Discovery lifecycle contracts.

### 8.2 No Northbound discovery lifecycle exists

No stable Northbound discovery classes were found for:

```text
DiscoverySession
DiscoveryCandidate
RequestDiscovery
admitDiscoveryCandidate
rejectDiscoveryCandidate
```

### 8.3 Decision for MU-019

```text
Do NOT add discovery methods to the first seed facade.
Do NOT add requestDiscovery(...), DiscoverySession, DiscoveryCandidate admission, provider refresh, adapter probes or direct SC-D interactions.
Do NOT import materialization discovery fact classes into the Northbound package.
```

This closes:

```text
OQ-SDD-NBF-003 — Discovery placeholder method
```

---

## 9. Northbound facade delegation chain

The execution package must define the exact delegation chain for each facade method.

| Facade method | Delegates to | Result type mapped from | First-seed decision |
|---|---|---|---|
| `getTopologySnapshot` | `queryService.findCurrentSnapshot()` | `CoreSnapshot` | Implement |
| `getTopologyVersion` | `queryService.findCurrentTopologyVersion()` | `TopologyVersion` | Implement |
| `getDevice` | `queryService.findDevice()` | `DeviceSnapshot` | Implement |
| `getEndpoint` | `queryService.findEndpoint()` | `EndpointSnapshot` | Implement |
| `listRooms` | `queryService.findCurrentSnapshot().topology().rooms()` | `List<RoomNode>` | Implement via snapshot extraction |
| `listZones` | `queryService.findCurrentSnapshot().topology().zones()` | `List<ZoneNode>` | Implement via snapshot extraction |
| `listDevices` | `queryService.findCurrentSnapshot().topology().devices()` | `List<DeviceNode>` | Implement via snapshot extraction |
| `listEndpoints` | `queryService.findCurrentSnapshot().topology().endpoints()` | `List<EndpointNode>` | Implement via snapshot extraction |
| `listDevicesLocatedIn` | `queryService.findLocatedDevices()` | `List<DeviceNode>` | Implement |
| `listEndpointsLocatedIn` | `queryService.findLocatedEndpoints()` | `List<EndpointNode>` | Implement |
| `getEndpointHealth` | `queryService.findEndpointHealth()` | `EndpointHealth` | Implement; durable only |
| `getDeviceHealth` | none | none | Return `UNKNOWN_PENDING_NORMALIZATION` |
| `getDeviceRuntimeState` | `queryService.findDeviceState()` | `Map<String,Object>` | Implement |
| `getEndpointRuntimeState` | none | none | Return `UNSUPPORTED_PROFILE` or `UNKNOWN_PENDING_NORMALIZATION` |
| `getTemporalRuntimeStatus` | `engineHealth.status()` + counters | `TemporalEngineHealth` | Implement |
| `getRecoveryStatus` | none preferred; optional `engineHealth.status()` proxy | derived | Prefer `UNSUPPORTED_PROFILE` |
| `createSignalTemporalAct` | `temporalActApplicationPort.createSignalTemporalAct()` | `CreateSignalTemporalActResult` | Implement |
| `cancelTemporalAct` | `temporalActApplicationPort.cancelTemporalAct()` | `CancelTemporalActResult` | Implement |
| `getTemporalAct` | `temporalActObservationPort.findById()` | `TemporalActObservation` | Implement |
| `listTemporalActs` | `temporalActObservationPort.listActive/listTerminal/listMisfired()` | `List<TemporalActObservation>` | Implement constrained filters |

---

## 10. Recommended facade dependencies

`DefaultScCoreNorthboundFacade` should depend only on:

```java
CoreSnapshotQueryService queryService;
TemporalActApplicationPort temporalActApplicationPort;
TemporalActObservationPort temporalActObservationPort;
TemporalEngineHealth engineHealth;
Clock clock;
```

Optional only if justified by the execution package:

```java
TemporalEngineProperties temporalEngineProperties;
```

It must not depend on:

```java
SQLiteBaseTopologyRepository
SQLiteEndpointHealthRepository
SQLiteTopologyMaterializationStateRepository
BaseTopologyService
DefaultTopologyMaterializationService
DataSource
JdbcTemplate
TransactionTemplate
ObjectMapper
TemporalEngineRunner
SC-D adapter classes
NATS / JetStream clients
HTTP / gRPC / MCP / GraphQL / WebSocket APIs
```

Notes:

```text
ObjectMapper should not be needed in Northbound DTO mapping.
TransactionTemplate should remain inside application services/repositories, not the facade.
BaseTopologyService is a mutation service and contains legacy in-process device-state memory; it should not be the Northbound read authority.
```

---

## 11. Spring wiring decision

### 11.1 Required bean

`DefaultScCoreNorthboundFacade` requires:

```java
@Service
public class DefaultScCoreNorthboundFacade implements ScCoreNorthboundFacade {
    private final CoreSnapshotQueryService queryService;
    private final TemporalActApplicationPort temporalActApplicationPort;
    private final TemporalActObservationPort temporalActObservationPort;
    private final TemporalEngineHealth engineHealth;
    private final Clock clock;
}
```

### 11.2 Preferred approach

Preferred for MU-019:

```text
Use @Service on DefaultScCoreNorthboundFacade if component scan already covers com.sovereign.connect.core.
```

Alternative:

```text
Add explicit @Bean in NorthboundFacadeConfiguration.
```

Execution package must decide one approach explicitly.

### 11.3 Wiring tests required

Required if the facade is registered as a Spring bean:

```text
ScCoreNorthboundFacade bean exists.
Bean is backed by DefaultScCoreNorthboundFacade.
No @RestController/@Controller/@MessageMapping bean is introduced.
No conflicting facade implementation is registered.
```

---

## 12. Required package layout

Recommended implementation package:

```text
src/main/java/com/sovereign/connect/core/northbound/
  ScCoreNorthboundFacade.java
  DefaultScCoreNorthboundFacade.java
  ScNorthboundResponse.java
  ScNorthboundStatus.java
  ScNorthboundError.java
  ScNorthboundWarning.java

src/main/java/com/sovereign/connect/core/northbound/topology/
  NorthboundTopologySnapshot.java
  NorthboundTopologyVersionView.java
  NorthboundRoomView.java
  NorthboundZoneView.java
  NorthboundDeviceView.java
  NorthboundEndpointView.java
  NorthboundCapabilityView.java
  NorthboundLocationQuery.java

src/main/java/com/sovereign/connect/core/northbound/runtime/
  NorthboundEndpointHealthView.java
  NorthboundDeviceHealthView.java
  NorthboundRuntimeStateView.java
  NorthboundRecoveryStatusView.java
  NorthboundTemporalRuntimeStatusView.java

src/main/java/com/sovereign/connect/core/northbound/temporal/
  NorthboundCreateSignalTemporalActRequest.java
  NorthboundCancelTemporalActRequest.java
  NorthboundTemporalActView.java
  NorthboundTemporalActFilter.java
```

Recommended tests:

```text
src/test/java/com/sovereign/connect/core/northbound/
  NorthboundFacadeBehavioralTest.java
  NorthboundFacadeNegativeBoundaryTest.java
  NorthboundFacadeSpringContextTest.java
  NorthboundFacadeArchitectureTest.java
```

No changes should be made to:

```text
Any existing service, port or repository
V1/V2/V3/V4 Flyway migrations
TemporalEngineConfiguration
TopologyPersistenceConfiguration
Existing validated tests, except where test harness update is strictly necessary and justified
```

---

## 13. Response envelope and status mapping

### 13.1 Required local envelope

```java
public record ScNorthboundResponse<T>(
    ScNorthboundStatus status,
    T payload,
    List<ScNorthboundWarning> warnings,
    ScNorthboundError error
) {}
```

This must remain distinct from SC-B `ScResponseEnvelope<TResponse>`.

### 13.2 Required local statuses

The enum should include at least:

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

Optional but useful for implementation clarity:

```java
TEMPORAL_ENGINE_NOT_READY
```

### 13.3 Recommended status mapping

| Existing result/source | Northbound status |
|---|---|
| Optional present | `OK` |
| Optional empty | `NOT_FOUND` |
| Provider-native ID used as canonical lookup key | `INVALID_CANONICAL_ID` or `NOT_FOUND`, according to existing seed convention |
| `CreateSignalTemporalActResult.Accepted` | `ACCEPTED` |
| `CreateSignalTemporalActResult.IdempotentReplay` | `ACCEPTED` with warning `IDEMPOTENT_REPLAY` |
| `CreateSignalTemporalActResult.Rejected` | `INVALID_REQUEST` |
| `CreateSignalTemporalActResult.Failed` | `INTERNAL_ERROR` or `TEMPORAL_ENGINE_NOT_READY` |
| `CancelTemporalActResult.Cancelled` | `CANCELLED` |
| `CancelTemporalActResult.AlreadyTerminal` | `OK` with warning `ALREADY_TERMINAL` |
| `CancelTemporalActResult.NotFound` | `NOT_FOUND` |
| `CancelTemporalActResult.IdempotentReplay` | `OK` or `CANCELLED` with warning `IDEMPOTENT_REPLAY`, depending observed state |
| DeviceHealth first seed | `UNKNOWN_PENDING_NORMALIZATION` |
| Endpoint runtime state first seed | `UNSUPPORTED_PROFILE` or `UNKNOWN_PENDING_NORMALIZATION` |
| Recovery status first seed | Prefer `UNSUPPORTED_PROFILE` unless explicit proxy is implemented |
| Profile C placeholder if accidentally added | `DEFERRED_SC_B_REQUIRED` |

---

## 14. Required behavioral tests

The execution package should require tests for:

```text
getTopologySnapshot returns Base Topology DTO with topologyVersion.
getTopologyVersion returns current habitat-scoped topologyVersion.
getDevice resolves canonical deviceId.
getEndpoint resolves canonical endpointId.
Provider-native IDs are rejected or return NOT_FOUND, not accepted as canonical authority.
listRooms/listZones/listDevices/listEndpoints expose Base Topology DTOs only.
listRooms/listZones/listDevices/listEndpoints are derived from CoreSnapshot.topology(), not new repository methods.
listDevicesLocatedIn/listEndpointsLocatedIn use canonical topology/spatial relations.
getEndpointHealth reads durable endpoint health through CoreSnapshotQueryService.findEndpointHealth(...).
getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION in first seed.
getDeviceRuntimeState reads durable state through CoreSnapshotQueryService.findDeviceState(...), not BaseTopologyService memory.
getEndpointRuntimeState returns UNSUPPORTED_PROFILE or UNKNOWN_PENDING_NORMALIZATION.
getRecoveryStatus returns UNSUPPORTED_PROFILE unless explicit non-authoritative proxy is implemented.
createSignalTemporalAct delegates to TemporalActApplicationPort.
cancelTemporalAct delegates to TemporalActApplicationPort.
getTemporalAct/listTemporalActs delegate to TemporalActObservationPort.
getTemporalRuntimeStatus maps TemporalEngineHealth.
NorthboundCreateSignalTemporalActRequest maps to CreateSignalTemporalActRequest using injected Clock.
NorthboundCancelTemporalActRequest maps to CancelTemporalActRequest using injected Clock.
```

---

## 15. Required negative boundary tests

The execution package should require tests that verify:

```text
No HTTP controllers are introduced.
No @RestController, @Controller or @MessageMapping annotations under northbound package.
No Spring Web imports under northbound package.
No MCP imports/classes under northbound package.
No gRPC imports/classes under northbound package.
No GraphQL imports/classes under northbound package.
No WebSocket imports/classes under northbound package.
No NATS/JetStream imports/classes under northbound package.
No SC-D adapter imports under northbound package.
No persistence adapter imports under northbound package.
No DataSource/JdbcTemplate/Flyway/SQLite classes under northbound package.
No BaseTopologyService dependency inside DefaultScCoreNorthboundFacade.
No ObjectMapper dependency inside DefaultScCoreNorthboundFacade.
No DTO names beginning with Effective, Projected, Surface, Session, Policy or Authority.
No requestDiscovery/Profile C method in first seed unless returning DEFERRED_SC_B_REQUIRED and explicitly justified.
```

---

## 16. Acceptance criteria mapping against SDD

| SDD AC | CSA result |
|---|---|
| AC-001 in-process canonical facade | Ready; no existing network API contamination. |
| AC-002 Profile A + selected Profile B | Ready with bounded Temporal subset. |
| AC-003 exclude Profile C discovery execution | Ready; must avoid materialization fact imports. |
| AC-004 exclude Profile D effective interaction | Ready; no Effective/View/Projection surface found in main source. |
| AC-005 facade interface + implementation | Requires new code. |
| AC-006 local response envelope distinct from SC-B | Requires new code. |
| AC-007 canonical query/read ports, not topology_json authority | Ready; CoreSnapshotQueryService + CoreSnapshotReadPort are available. |
| AC-008 topologyVersion/consistency marker | Ready; topologyVersion is present in CoreSnapshot/DeviceSnapshot/EndpointSnapshot. |
| AC-009 no Effective View/Session/Policy fields | Requires DTO discipline and architecture tests. |
| AC-010 endpoint health durable surface | Ready through CoreSnapshotQueryService.findEndpointHealth(...). |
| AC-011 device health unknown or derived rule | Choose UNKNOWN_PENDING_NORMALIZATION for first seed. Derived rule blocked by authority gap and vocabulary mismatch. |
| AC-012 TemporalAct operations are EIB/authorized-consumer only | Ready; add javadoc/comment and implementation-report note. |
| AC-013 no HTTP/MCP/gRPC/WebSocket/GraphQL | Ready; enforce with tests. |
| AC-014 no NATS/JetStream | Ready; enforce with tests. |
| AC-015 no direct SC-D adapter invocation | Ready; enforce with tests. |
| AC-016 behavioral tests | Requires new tests. |
| AC-017 source-level architecture tests | Requires new tests. |
| AC-018 Spring wiring tests | Required if facade is a bean. |
| AC-019 downstream artifacts before discovery/external exposure | Preserve in context/prompt. |
| AC-020 enables MIR after CSA | CSA result: enabled with decisions above. |

---

## 17. Closed decisions for execution package

### D-CSA-NBF-001 — Topology/entity authority

```text
Decision:
  Topology and entity queries MUST delegate to CoreSnapshotQueryService over CoreSnapshotReadPort.

Consequences:
  The facade MUST NOT depend on SQLiteBaseTopologyRepository, JdbcTemplate, DataSource, Flyway or topology_json.
  listRooms/listZones/listDevices/listEndpoints MAY extract lists from CoreSnapshot.topology().
```

### D-CSA-NBF-002 — DeviceHealth strategy

```text
Decision:
  First-seed DeviceHealth = UNKNOWN_PENDING_NORMALIZATION.

Consequences:
  getDeviceHealth(...) MUST NOT expose DeviceNode.health() or devices.device_health_* as durable authority.
  DERIVED_FROM_ENDPOINTS is deferred until health vocabulary and device health authority are normalized.
```

### D-CSA-NBF-003 — EndpointHealth authority

```text
Decision:
  EndpointHealth may be exposed from durable endpoint_health only.

Consequences:
  getEndpointHealth(...) MUST call CoreSnapshotQueryService.findEndpointHealth(...).
  Endpoint DTOs must not silently expose aggregate fallback health as durable endpoint health.
```

### D-CSA-NBF-004 — Temporal Profile B subset

```text
Decision:
  Temporal Profile B for MU-019 includes only:
    createSignalTemporalAct
    cancelTemporalAct
    getTemporalAct
    listTemporalActs
    getTemporalRuntimeStatus

Consequences:
  No ActionTemporalPayload.
  No device-command scheduling.
  No recurrence/cron.
  No policy/authority scheduling.
  No surface countdown UX.
```

### D-CSA-NBF-005 — Dedicated Northbound DTOs

```text
Decision:
  Create dedicated Northbound DTOs and local response envelope.

Consequences:
  Do not return CoreSnapshot, HabitatBaseTopology, DeviceNode, EndpointNode, RoomNode, ZoneNode or CapabilityNode as the public facade contract.
```

### D-CSA-NBF-006 — Discovery placeholder

```text
Decision:
  Do not add discovery methods to the first seed facade.

Consequences:
  No requestDiscovery(...).
  No DiscoverySession.
  No DiscoveryCandidate admission workflow.
  No materialization discovery fact imports into northbound package.
```

### D-CSA-NBF-007 — List methods

```text
Decision:
  Do not add new CoreSnapshotReadPort methods for listRooms/listZones/listDevices/listEndpoints in MU-019.

Consequences:
  Use CoreSnapshot.topology().rooms/zones/devices/endpoints for first seed list operations.
```

### D-CSA-NBF-008 — Recovery status

```text
Decision:
  Prefer UNSUPPORTED_PROFILE for getRecoveryStatus(...) in MU-019.

Alternative:
  A coarse non-authoritative proxy from TemporalEngineHealth.status() may be implemented only if explicitly documented as proxy.
```

### D-CSA-NBF-009 — Spring wiring

```text
Decision:
  Prefer @Service registration for DefaultScCoreNorthboundFacade if component scan covers the package.

Alternative:
  Use NorthboundFacadeConfiguration with explicit @Bean.

Execution package must choose one.
```

---

## 18. Open items for execution package

### OI-CSA-NBF-001 — HealthStatus vocabulary mismatch

Current code:

```java
HEALTHY, DEGRADED, OFFLINE, UNKNOWN
```

SDD derivation rule:

```text
UNHEALTHY
```

Resolution for first seed:

```text
Do not implement DERIVED_FROM_ENDPOINTS.
Return UNKNOWN_PENDING_NORMALIZATION for DeviceHealth.
```

Future resolution:

```text
Patch SDD/PDR to map OFFLINE as the code-level equivalent of UNHEALTHY, or introduce a canonical UNHEALTHY/UNAVAILABLE vocabulary decision.
```

### OI-CSA-NBF-002 — Device health columns are not device health authority

The normalized `devices` table stores device health columns, but they are structural/aggregate persistence, not a dedicated normalized health authority.

Resolution:

```text
Northbound must not expose them as durable DeviceHealth.
```

### OI-CSA-NBF-003 — Endpoint view health source marker

Endpoint DTOs may include health, but must not hide whether it came from durable `endpoint_health` or aggregate fallback.

Resolution:

```text
Prefer separate getEndpointHealth(...) as durable source.
If endpoint DTO includes health, include source marker/warning or derive from direct findEndpointHealth only.
```

### OI-CSA-NBF-004 — listTemporalActs filter must be constrained

The existing observation port has explicit methods:

```text
listActive
listTerminal(maxResults)
listMisfired(maxResults)
```

Resolution:

```text
NorthboundTemporalActFilter should map only to these supported categories.
Do not create arbitrary query semantics in first seed.
```

### OI-CSA-NBF-005 — Idempotency key generation policy

The existing TemporalAct request records accept an idempotency key.

Execution package must decide:

```text
A. Northbound request requires caller-provided idempotencyKey.
B. Northbound facade generates one when absent.
```

Recommended first seed:

```text
Require/accept idempotencyKey from the Northbound request and reject invalid missing keys only if the existing TemporalAct service requires it.
Do not invent product-level idempotency semantics in the facade.
```

### OI-CSA-NBF-006 — NorthboundMapper placement

Execution package must decide:

```text
A. Separate package-private NorthboundMapper utility.
B. Static private mapping methods inside DefaultScCoreNorthboundFacade.
```

Recommended first seed:

```text
Use package-private NorthboundMapper to keep DefaultScCoreNorthboundFacade readable and testable.
```

---

## 19. MU-019 implementation scope

### 19.1 New main files

```text
src/main/java/com/sovereign/connect/core/northbound/
  ScCoreNorthboundFacade.java
  DefaultScCoreNorthboundFacade.java
  ScNorthboundResponse.java
  ScNorthboundStatus.java
  ScNorthboundError.java
  ScNorthboundWarning.java
  NorthboundMapper.java

src/main/java/com/sovereign/connect/core/northbound/topology/
  NorthboundTopologySnapshot.java
  NorthboundTopologyVersionView.java
  NorthboundRoomView.java
  NorthboundZoneView.java
  NorthboundDeviceView.java
  NorthboundEndpointView.java
  NorthboundCapabilityView.java
  NorthboundLocationQuery.java

src/main/java/com/sovereign/connect/core/northbound/runtime/
  NorthboundEndpointHealthView.java
  NorthboundDeviceHealthView.java
  NorthboundRuntimeStateView.java
  NorthboundTemporalRuntimeStatusView.java
  NorthboundRecoveryStatusView.java

src/main/java/com/sovereign/connect/core/northbound/temporal/
  NorthboundCreateSignalTemporalActRequest.java
  NorthboundCancelTemporalActRequest.java
  NorthboundTemporalActView.java
  NorthboundTemporalActFilter.java
```

`NorthboundMapper.java` is recommended by this merged CSA, even though it may be omitted if mapping remains small and clear.

### 19.2 New test files

```text
src/test/java/com/sovereign/connect/core/northbound/
  NorthboundFacadeBehavioralTest.java
  NorthboundFacadeNegativeBoundaryTest.java
  NorthboundFacadeSpringContextTest.java
  NorthboundFacadeArchitectureTest.java
```

### 19.3 Files that should not change

```text
Any existing service, port or repository
V1/V2/V3/V4 Flyway migrations
TemporalEngineConfiguration, unless explicit @Bean is chosen and justified
TopologyPersistenceConfiguration
SC-B contracts
SC-D materialization/fact contracts
HTTP/MCP/gRPC/WebSocket/GraphQL exposure layers
```

---

## 20. Failure modes Codex must avoid

```text
Returning domain records directly as Northbound contract.
Injecting SQLite repositories directly into the facade.
Using BaseTopologyService as Northbound read authority.
Using topology_json as query authority.
Using DeviceNode.health() as durable DeviceHealth.
Using EndpointSnapshot aggregate fallback as durable endpoint health.
Creating discovery/requestDiscovery methods in the first seed.
Importing materialization discovery facts into northbound package.
Adding HTTP controllers or Spring Web imports.
Adding GraphQL/gRPC/MCP/WebSocket/NATS/JetStream imports.
Creating arbitrary temporal query semantics beyond existing observation port methods.
Generating product-facing Session/Identity/Authority/Policy fields.
Treating the in-process facade as Hub/SApp/Surface direct product API.
```

---

## 21. Required context decision for Codex

The execution package must state:

```text
The first Northbound facade is read-oriented Profile A plus selected Temporal Profile B.
It must create a clean in-process facade over existing query/application ports.
It must not implement external exposure or discovery execution.
DeviceHealth must be UNKNOWN_PENDING_NORMALIZATION in the first seed.
EndpointHealth may be exposed from durable endpoint_health only.
Topology/entity queries must use CoreSnapshotQueryService.
DTOs must be dedicated Northbound DTOs, not raw domain records.
```

---

## 22. Suggested branch and commit

Suggested branch:

```text
feat/sc-c-mir-019-northbound-facade-seed
```

Suggested commit:

```text
feat(sc-c): add northbound facade seed
```

---

## 23. Merge note

This v0.2.0-draft consolidates two independent CSA drafts over the same service snapshot.

The merged document preserves:

```text
- the broader authority-leakage and boundary-risk analysis from the first audit;
- the exact service signatures, delegation chain, Spring wiring and file-scope precision from the second audit;
- a single set of closed CSA decisions for the MIR/execution package;
- a single implementation scope for MU-019.
```

No conflict remains between the two audits. Where one audit was more conservative and the other more precise, the merged CSA adopts the stricter normative constraint and the more exact implementation mapping.
