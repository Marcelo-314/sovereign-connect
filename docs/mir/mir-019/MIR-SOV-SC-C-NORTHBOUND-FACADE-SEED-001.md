# MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001

## SC-C Northbound Facade Seed

**Document ID:** MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
**Title:** SC-C Northbound Facade Seed
**Version:** v0.2.0-candidate
**Status:** Candidate / execution-package enabled
**Date:** 2026-05-24
**Corpus:** Sovereign Connect
**Type:** MIR
**Plane:** SC-C
**Materialization Unit:** MU-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
**Operational Slot:** MU-019
**Scope:** Materializes the first SC-C Northbound Facade as an in-process canonical facade over SC-C-owned topology, runtime/health observation and selected SC-C-owned TemporalAct operations. This MIR does not implement EIB, View Composer, HTTP/SSE, gRPC, ConnectRPC, MCP, WebSocket, GraphQL, SC-B runtime, SC-D adapter runtime, discovery execution or product-facing surface integration.

---

## Changelog v0.2.0-candidate

Candidate release.

This version:

1. Promotes the MIR from `v0.1.1-draft` to `v0.2.0-candidate` after reviewer approval.
2. Records that the execution package is enabled for `docs/mir/mir-019/`.
3. Preserves the approved `AC-019-028` clarification as a candidate-level acceptance requirement.
4. Preserves `@Service` direct registration as the recommended Spring wiring path for MU-019, while correcting the rationale so it does not misstate the existing `@Bean`-based configuration style.
5. Keeps implementation authorization gated by acceptance of the external execution package and subsequent implementation report.

---

## Changelog v0.1.1-draft

Patch release.

This version:

1. Clarifies `AC-019-028` so it is directly verifiable by the future acceptance map and implementation report.
2. Requires the implementation report to explicitly record the three open future-profile descent items retained after MU-019: endpoint runtime state, recovery status and device health normalization.
3. Tightens `D-019-009` to make `@Service` the recommended Spring registration path for MU-019, with explicit `@Bean` allowed only if justified by repository conventions.
4. Updates promotion/evidence references from `AC-019-001` through `AC-019-032` to `AC-019-001` through `AC-019-033` after adding the explicit open-items criterion.

---

## Changelog v0.1.0-draft

Initial MIR draft.

This version:

1. Opens `MU-SOV-SC-C-NORTHBOUND-FACADE-SEED-001` as MU-019 after CSA approval.
2. Materializes `PDR-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft` and `SDD-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft` as a bounded in-process facade seed.
3. Applies `ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-TECH-001 v0.1.1-draft`: the first descent is an in-process canonical facade, not an external network API.
4. Incorporates closed decisions from `CSA-SOV-SC-C-NORTHBOUND-FACADE-SEED-001 v0.2.0-draft`.
5. Restricts implementation to Profile A and selected Profile B.
6. Excludes Profile C discovery and all cross-plane execution from the first seed.
7. Selects `UNKNOWN_PENDING_NORMALIZATION` for first-seed `DeviceHealth`.
8. Requires `EndpointHealth` to be exposed only from the durable endpoint health read path.
9. Requires topology/entity reads to delegate through `CoreSnapshotQueryService` over `CoreSnapshotReadPort`.
10. Requires dedicated Northbound DTOs and a local `ScNorthboundResponse<T>` envelope distinct from SC-B response envelopes.
11. Externalizes context, prompt, acceptance map and implementation report according to MU/MIR governance.

---

## 0. Governance note

This MIR does not include Codex prompts or implementation context inline.

Execution assets MUST be produced separately under:

```text
docs/mir/mir-019/
  MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001.md
  code-surface-audit.md
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report-template.md
  implementation-report.md
```

This MIR authorizes a bounded implementation attempt only after the execution package is accepted.

Operational rule:

```text
MIR defines the materialization scope.
CSA constrains the actual code surface.
context.md and codex-prompt.md operationalize implementation.
acceptance-map.md maps MIR/SDD/CSA criteria to tests and evidence.
```

---

## 1. Disposition

```text
MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001 v0.2.0-candidate:
  Candidate MIR for MU-019.

Execution readiness:
  CSA-approved.
  Execution package enabled.
  Implementation remains gated by execution-package acceptance.

Implementation authorization:
  Candidate-level scope approved.
  Direct implementation is authorized only after context.md, codex-prompt.md,
  acceptance-map.md and implementation-report-template.md are accepted as the
  external execution package.
```

Candidate constraints:

```text
- MU-019 identity and slot remain bound to the current INDEX/SYNC successor;
- CSA decisions are binding for the execution package;
- execution package MUST NOT broaden scope beyond this MIR;
- no Profile C discovery methods are required in first seed;
- no external exposure binding is introduced.
```

---

## 2. Background

SC-C now has an industrialized enough substrate to expose a canonical read/request boundary without exposing internal services or persistence adapters directly.

The relevant already-materialized substrate includes:

```text
MU-016 — Temporal Runtime Industrial Hardening
MU-017 — Canonical Topology SQLite/Flyway Persistence
MU-018 — Storage Legacy Cleanup / Serialization Boundary Hardening
```

The Northbound Facade is the next descent because EIB and downstream product-facing layers need a controlled, canonical SC-C-owned surface instead of relying on internal repositories, services or persistence details.

The intended product-facing chain remains:

```text
Hub / SApp / Surfaces
  -> EIB
  -> SC-C Northbound Facade
  -> SC-C application/query services and ports
```

The facade is not a user-facing API. It is an SC-C-owned in-process canonical boundary intended for EIB or another explicitly authorized internal consumer.

---

## 3. Depends on

```text
Sovereign Connect — Curator & Descent Instructions
RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.11-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.17-draft or current successor
SYNC-SOV-SC-MU-BACKLOG-001 v0.1.18-draft or current successor
PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001 v0.1.1-draft
ADR-SOV-SC-BUS-TECH-001 v0.2.2-draft
ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-TECH-001 v0.1.1-draft
NT-SOV-SC-C-NORTHBOUND-EXPOSURE-TECH-SOTA-001 v0.1.0-draft
PDR-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft
SDD-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft
CSA-SOV-SC-C-NORTHBOUND-FACADE-SEED-001 v0.2.0-draft
MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v1.0.0-accepted
MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v1.0.0-accepted or current accepted state
MIR-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001 v1.0.0-accepted or current accepted state
```

If the current repository INDEX/SYNC uses a newer version than the one named here, the execution package MUST reference the newer canonical version and preserve the same MU identity.

---

## 4. Related

```text
ADR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-draft
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001, downstream
PDR-SOV-SC-X-DISCOVERY-REQUEST-FLOW-001, planned
SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001, possible downstream
SDD-SOV-SC-C-NORTHBOUND-GRPC-CONNECTRPC-001, possible downstream
PDR-SOV-SC-MCP-FACADE-001, pending / outside SC-C primary Northbound
PDR-SOV-SC-VIEW-COMPOSER-BOUNDARY-001, to be revised under EIB framing
SDD-SOV-SC-VIEW-COMPOSER-001, to be revised under EIB framing
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001, planned
MU-SOV-SC-B-ABSTRACT-BUS-SEED-001, planned
ADR-SOV-SC-SERIALIZATION-001, planned
```

---

## 5. Materialization thesis

MU-019 materializes the first SC-C Northbound Facade as a dedicated in-process canonical facade.

Canonical statement:

```text
SC-C owns canonical state and canonical request lifecycle.
Northbound Facade exposes SC-C-owned canonical surfaces.
EIB consumes the facade and performs effective admission/projection.
Hub / SApp / Surfaces consume EIB, not SC-C directly.
SC-B routes and correlates cross-plane operations.
SC-D observes, translates and executes provider-level behavior.
```

Implementation statement:

```text
MU-019 creates a clean northbound package over existing SC-C query/application ports.
It does not create a network API.
It does not create an EIB.
It does not create projection/effective view.
It does not execute discovery or adapter operations.
```

Authority statement:

```text
Topology/entity reads MUST go through CoreSnapshotQueryService over CoreSnapshotReadPort.
EndpointHealth MAY be exposed from the durable endpoint health path.
DeviceHealth MUST be UNKNOWN_PENDING_NORMALIZATION in the first seed.
Temporal Profile B MUST use existing TemporalAct application/observation ports.
```

---

## 6. Goals

### G-019-001 — Introduce dedicated Northbound package

Add a dedicated SC-C Northbound package under:

```text
src/main/java/com/sovereign/connect/core/northbound/
```

The package MUST contain the in-process facade interface, implementation, local response envelope, local status vocabulary, local error/warning structures, mapper and DTOs.

The package MUST remain separate from:

```text
adapter.persistence
adapter.bus
adapter.discovery
controller
mcp
graphql
grpc
websocket
```

---

### G-019-002 — Introduce `ScCoreNorthboundFacade`

Introduce one primary facade interface:

```text
ScCoreNorthboundFacade
```

It SHOULD expose the first-seed operations listed in §9.

The interface is an in-process canonical contract. It MUST NOT be an HTTP controller, gRPC service, MCP server, GraphQL resolver, WebSocket endpoint or SC-B bus binding.

---

### G-019-003 — Introduce `DefaultScCoreNorthboundFacade`

Introduce a default implementation:

```text
DefaultScCoreNorthboundFacade
```

It MUST delegate to existing SC-C services and ports.

It MUST NOT directly use:

```text
SQLite connection APIs
Flyway APIs
JdbcTemplate
DataSource
NATS APIs
JetStream APIs
HTTP request/response APIs
MCP SDKs
gRPC stubs
WebSocket sessions
GraphQL resolvers
SC-D adapter classes
provider-native clients
```

---

### G-019-004 — Introduce local Northbound response envelope

Introduce a local in-process envelope:

```text
ScNorthboundResponse<T>
```

with status, payload, warnings and error fields.

This response envelope MUST remain distinct from `ScResponseEnvelope<TResponse>` from SC-B.

---

### G-019-005 — Expose Profile A topology observation

Implement topology observation over `CoreSnapshotQueryService`.

The implementation MUST support:

```text
getTopologySnapshot
getTopologyVersion
getDevice
getEndpoint
listRooms
listZones
listDevices
listEndpoints
listDevicesLocatedIn
listEndpointsLocatedIn
```

List methods MAY be implemented by reading `findCurrentSnapshot(habitatId)` and extracting lists from `CoreSnapshot.topology()`.

No new `CoreSnapshotReadPort` list methods are required for MU-019.

---

### G-019-006 — Expose runtime / health observation conservatively

Implement the allowed runtime/health methods as follows:

```text
getEndpointHealth:
  use durable endpoint health read path only.

getDeviceHealth:
  return UNKNOWN_PENDING_NORMALIZATION.

getDeviceRuntimeState:
  use CoreSnapshotQueryService.findDeviceState(...).

getEndpointRuntimeState:
  return UNSUPPORTED_PROFILE unless a durable endpoint runtime state port exists.

getTemporalRuntimeStatus:
  use TemporalEngineHealth.

getRecoveryStatus:
  return proxy/limited recovery status based on TemporalEngineHealth or UNSUPPORTED_PROFILE,
  unless an explicit recovery read surface exists.
```

The facade MUST NOT fabricate device health from aggregate structural fields, stale snapshots, `topology_json`, provider metadata or in-memory defaults.

---

### G-019-007 — Expose selected TemporalAct Profile B operations

Implement only SC-C-owned TemporalAct operations already industrialized by MU-016:

```text
createSignalTemporalAct
cancelTemporalAct
getTemporalAct
listTemporalActs
getTemporalRuntimeStatus
```

Profile B operations are intended for EIB or another explicitly authorized in-process consumer.

Hub, SApp and Surfaces MUST NOT call Profile B operations directly as product architecture.

---

### G-019-008 — Use dedicated Northbound DTOs

The facade MUST return dedicated Northbound DTOs.

It MUST NOT expose raw domain records as the public facade contract, including:

```text
CoreSnapshot
HabitatBaseTopology
DeviceNode
EndpointNode
RoomNode
ZoneNode
CapabilityNode
EndpointHealth
TemporalActObservation
```

Raw domain records may be used internally as mapping inputs only.

---

### G-019-009 — Add behavioral, negative boundary, wiring and architecture tests

The MU MUST add tests covering:

```text
behavioral facade semantics;
negative boundary invariants;
source-level forbidden technology/import checks;
Spring wiring if @Service is used;
no regressions in existing test suite.
```

---

## 7. Non-goals

MU-019 MUST NOT implement or modify:

```text
HTTP controllers
Spring Web exposure
SSE
MCP facade
gRPC services
ConnectRPC services
WebSocket subscriptions
GraphQL schema/resolvers
NATS subjects
JetStream streams or consumers
SC-B runtime
SC-D adapter runtime
EIB implementation
View Composer implementation
Hub/SApp/Surface direct integration
Discovery E2E implementation
DiscoveryCandidate admission workflow
Action command dispatch
Adapter health probes
Provider refresh/resync
Outbox dispatcher
Flyway schema changes
SQLite repository redesign
SemanticPayloadCodec extraction
DeviceHealth normalized persistence
Endpoint runtime-state persistence
Product UX
```

Concrete external exposure technology belongs to downstream SDD/ADR work after the in-process facade is stable.

---

## 8. Closed decisions from CSA

### D-019-001 — Topology/entity authority

Decision:

```text
Topology/entity reads MUST delegate to CoreSnapshotQueryService.
```

Implementation consequence:

```text
DefaultScCoreNorthboundFacade may inject CoreSnapshotQueryService.
It MUST NOT inject SQLite repositories, JdbcTemplate, DataSource or BaseTopologyService as read authority.
```

---

### D-019-002 — List operation strategy

Decision:

```text
listRooms/listZones/listDevices/listEndpoints may derive from CoreSnapshot.topology().
```

Rationale:

```text
CoreSnapshot already carries HabitatBaseTopology with room, zone, device and endpoint lists.
No new port method is required for this seed.
```

---

### D-019-003 — DeviceHealth strategy

Decision:

```text
First-seed DeviceHealth = UNKNOWN_PENDING_NORMALIZATION.
```

Rationale:

```text
Device health columns and DeviceNode.health() exist, but they are not independently maintained through a dedicated durable DeviceHealth port equivalent to endpoint health.
Exposing them would silently turn structural aggregate health into durable current health authority.
```

The first seed MUST NOT implement `DERIVED_FROM_ENDPOINTS`.

---

### D-019-004 — EndpointHealth authority

Decision:

```text
EndpointHealth may be exposed only from the durable endpoint health read path.
```

The facade MUST avoid treating aggregate fallback health as durable endpoint evidence.

---

### D-019-005 — Endpoint runtime state

Decision:

```text
Endpoint runtime state is UNSUPPORTED_PROFILE in first seed unless a durable endpoint state read port exists in the inspected code surface.
```

---

### D-019-006 — Temporal Profile B subset

Decision:

```text
Only Signal TemporalAct create/cancel/get/list/status operations are in scope.
```

Forbidden in first seed:

```text
ActionTemporalPayload
provider-level command scheduling
device command dispatch
SC-B outbox dispatcher control
recurrence/cron unless already industrialized
policy/authority-based scheduling
surface countdown UX
```

---

### D-019-007 — Discovery placeholder

Decision:

```text
Do not add discovery methods to the first facade seed.
```

No `requestDiscovery`, `getDiscoverySession`, `listDiscoveryCandidates`, `admitDiscoveryCandidate` or `rejectDiscoveryCandidate` method should be introduced in MU-019.

---

### D-019-008 — DTO discipline

Decision:

```text
Use dedicated Northbound DTOs and a Northbound mapper.
```

Recommended implementation:

```text
Use a package-private NorthboundMapper.
```

---

### D-019-009 — Spring wiring

Decision:

```text
DefaultScCoreNorthboundFacade SHOULD be registered directly as a Spring @Service for MU-019.
```

Rationale:

```text
The approved MU-019 target is a lightweight in-process facade component.
Direct @Service registration keeps the facade local to the northbound package and avoids
expanding existing configuration classes unless construction requirements demand it.

Note: the current repository does define several services through explicit @Bean methods.
This MIR still selects @Service for MU-019 as a scoped implementation decision, not as a
claim that @Bean is absent from the existing codebase.
```

Explicit `@Bean` wiring remains allowed if repository conventions or construction requirements make component scanning unsuitable. If explicit `@Bean` wiring is used, the implementation report MUST justify it.

Execution-package requirement:

```text
context.md and codex-prompt.md SHOULD instruct Codex to use @Service direct registration for DefaultScCoreNorthboundFacade.
```

---

### D-019-010 — Idempotency policy

Decision:

```text
The facade should accept an idempotencyKey from the Northbound request.
It MUST NOT invent product-level idempotency semantics.
```

If the existing TemporalAct service requires non-null idempotency keys, the facade may reject missing keys as `INVALID_REQUEST`.

---

## 9. Initial facade surface

The first implementation SHOULD expose the following operation families.

### 9.1 Topology observation

```text
getTopologySnapshot(habitatId)
getTopologyVersion(habitatId)
getDevice(habitatId, deviceId)
getEndpoint(habitatId, endpointId)
listRooms(habitatId)
listZones(habitatId)
listDevices(habitatId)
listEndpoints(habitatId)
listDevicesLocatedIn(habitatId, roomOrZoneId)
listEndpointsLocatedIn(habitatId, roomOrZoneId)
```

Rules:

```text
Provider-native IDs MUST NOT be accepted as canonical lookup authority.
Effective View MUST NOT be returned.
topologyVersion or equivalent consistency marker MUST be present where relevant.
```

---

### 9.2 Runtime and health observation

```text
getEndpointHealth(habitatId, endpointId)
getDeviceHealth(habitatId, deviceId)
getDeviceRuntimeState(habitatId, deviceId)
getEndpointRuntimeState(habitatId, endpointId)
getRecoveryStatus(habitatId)
getTemporalRuntimeStatus(habitatId)
```

First-seed behavior:

```text
getEndpointHealth:
  OK when durable endpoint health exists;
  NOT_FOUND when canonical endpoint does not resolve or no durable health exists, according to existing query semantics.

getDeviceHealth:
  UNKNOWN_PENDING_NORMALIZATION.

getDeviceRuntimeState:
  use durable device state read surface through CoreSnapshotQueryService.

getEndpointRuntimeState:
  UNSUPPORTED_PROFILE unless durable endpoint runtime state exists.

getRecoveryStatus:
  limited/proxy status or UNSUPPORTED_PROFILE.

getTemporalRuntimeStatus:
  map from TemporalEngineHealth.
```

---

### 9.3 TemporalAct Profile B

```text
createSignalTemporalAct(habitatId, request)
cancelTemporalAct(habitatId, request)
getTemporalAct(habitatId, temporalActId)
listTemporalActs(habitatId, filter)
```

Rules:

```text
Only SignalTemporalPayload-compatible operations are allowed.
No provider-level command scheduling is allowed.
No ActionTemporalPayload is allowed.
No Authority/Policy/Session/Surface fields are allowed.
```

`listTemporalActs` MUST map to existing observation-port capabilities only:

```text
listActive(habitatId)
findById(habitatId, temporalActId)
listTerminal(habitatId, maxResults)
listMisfired(habitatId, maxResults)
```

The facade MUST NOT invent arbitrary temporal query semantics beyond the existing observation port.

---

## 10. Required response envelope

Introduce:

```text
ScNorthboundResponse<T>
```

Recommended shape:

```text
status: ScNorthboundStatus
payload: T
warnings: List<ScNorthboundWarning>
error: ScNorthboundError
```

Introduce:

```text
ScNorthboundStatus
ScNorthboundError
ScNorthboundWarning
```

Required status vocabulary:

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

Rules:

```text
OK is used for successful observation.
CREATED or ACCEPTED may be used for successful TemporalAct creation.
CANCELLED may be used for successful cancellation.
NOT_FOUND is used when a canonical ID does not resolve.
INVALID_CANONICAL_ID is used when the supplied key is malformed or provider-native according to seed rules.
UNSUPPORTED_PROFILE is used when a method is outside seed-supported behavior.
DEFERRED_SC_B_REQUIRED is reserved but should not be exercised by discovery methods in MU-019.
UNKNOWN_PENDING_NORMALIZATION is used for DeviceHealth first-seed response.
```

---

## 11. Required package/file scope

### 11.1 New main files

The execution package SHOULD target:

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

The exact DTO field set may be refined by execution package after final code-surface confirmation, but DTOs MUST remain dedicated Northbound views/requests.

---

### 11.2 New test files

The execution package SHOULD target:

```text
src/test/java/com/sovereign/connect/core/northbound/
  NorthboundFacadeBehavioralTest.java
  NorthboundFacadeNegativeBoundaryTest.java
  NorthboundFacadeSpringContextTest.java
  NorthboundFacadeArchitectureTest.java
```

Equivalent test class names are allowed if the acceptance-map preserves coverage.

---

### 11.3 Files that should not change

MU-019 SHOULD NOT modify:

```text
existing service interfaces
existing repository contracts
V1/V2/V3/V4 Flyway migrations
TemporalEngineConfiguration, unless explicit @Bean is chosen and justified
TopologyPersistenceConfiguration
SC-B contracts
SC-D materialization/fact contracts
HTTP/MCP/gRPC/WebSocket/GraphQL exposure layers
```

If any such file must change, the implementation report MUST justify the change against MIR scope.

---

## 12. Delegation requirements

The execution package MUST encode the following delegation chain.

```text
getTopologySnapshot
  -> CoreSnapshotQueryService.findCurrentSnapshot(...)

getTopologyVersion
  -> CoreSnapshotQueryService.findCurrentTopologyVersion(...)

getDevice
  -> CoreSnapshotQueryService.findDevice(...)

getEndpoint
  -> CoreSnapshotQueryService.findEndpoint(...)

listRooms/listZones/listDevices/listEndpoints
  -> CoreSnapshotQueryService.findCurrentSnapshot(...).topology().<list>()

listDevicesLocatedIn
  -> CoreSnapshotQueryService.findLocatedDevices(...)

listEndpointsLocatedIn
  -> CoreSnapshotQueryService.findLocatedEndpoints(...)

getEndpointHealth
  -> CoreSnapshotQueryService.findEndpointHealth(...)

getDeviceHealth
  -> UNKNOWN_PENDING_NORMALIZATION

getDeviceRuntimeState
  -> CoreSnapshotQueryService.findDeviceState(...)

getEndpointRuntimeState
  -> UNSUPPORTED_PROFILE unless durable endpoint-state port exists

getTemporalRuntimeStatus
  -> TemporalEngineHealth

getRecoveryStatus
  -> TemporalEngineHealth proxy or UNSUPPORTED_PROFILE

createSignalTemporalAct
  -> TemporalActApplicationPort.createSignalTemporalAct(...)

cancelTemporalAct
  -> TemporalActApplicationPort.cancelTemporalAct(...)

getTemporalAct
  -> TemporalActObservationPort.findById(...)

listTemporalActs
  -> TemporalActObservationPort list methods supported by filter
```

---

## 13. Forbidden implementation patterns

MU-019 MUST NOT introduce any of the following patterns:

```text
Returning raw domain records as Northbound contract.
Injecting SQLite repositories directly into the facade.
Injecting JdbcTemplate/DataSource into the facade.
Using BaseTopologyService as Northbound read authority.
Using topology_json as query authority.
Using DeviceNode.health() as durable DeviceHealth.
Using endpoint aggregate fallback as durable endpoint health.
Creating discovery/requestDiscovery methods in the first seed.
Importing materialization discovery facts into northbound package.
Adding HTTP controllers or Spring Web imports.
Adding GraphQL/gRPC/MCP/WebSocket/NATS/JetStream imports.
Creating arbitrary temporal query semantics beyond existing observation port methods.
Adding Session/Identity/Authority/Policy/Surface fields to Northbound DTOs.
Treating the in-process facade as Hub/SApp/Surface direct product API.
```

---

## 14. Behavioral acceptance requirements

The implementation MUST include behavioral tests covering, at minimum:

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

## 15. Negative boundary acceptance requirements

The implementation MUST include negative tests or source-level architecture tests covering:

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

## 16. Spring wiring acceptance requirements

If `DefaultScCoreNorthboundFacade` is registered as a Spring bean, tests MUST verify:

```text
SW-019-001 ScCoreNorthboundFacade bean exists.
SW-019-002 ScCoreNorthboundFacade is backed by DefaultScCoreNorthboundFacade.
SW-019-003 Required collaborators resolve from existing Spring context.
SW-019-004 No HTTP controller bean is introduced.
SW-019-005 No conflicting facade implementation is registered.
```

Required collaborators SHOULD include:

```text
CoreSnapshotQueryService
TemporalActApplicationPort
TemporalActObservationPort
TemporalEngineHealth
Clock
```

---

## 17. MIR acceptance criteria

This MIR is acceptable if:

```text
AC-019-001 Introduces ScCoreNorthboundFacade as in-process canonical facade.
AC-019-002 Introduces DefaultScCoreNorthboundFacade over existing SC-C services/ports.
AC-019-003 Introduces dedicated local ScNorthboundResponse envelope.
AC-019-004 Introduces ScNorthboundStatus with required status vocabulary.
AC-019-005 Introduces dedicated Northbound DTOs, not raw domain records as facade contract.
AC-019-006 Implements Profile A topology observation operations.
AC-019-007 Implements selected Profile B TemporalAct operations only.
AC-019-008 Does not implement Profile C discovery methods or discovery execution.
AC-019-009 Does not implement Profile D / product-facing effective interaction.
AC-019-010 Topology/entity reads delegate through CoreSnapshotQueryService.
AC-019-011 Facade does not inject SQLite repositories, JdbcTemplate, DataSource or BaseTopologyService.
AC-019-012 listRooms/listZones/listDevices/listEndpoints derive from CoreSnapshot.topology() or equivalent canonical read surface.
AC-019-013 getEndpointHealth uses durable endpoint health read path.
AC-019-014 getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION.
AC-019-015 getDeviceRuntimeState uses durable device state read surface.
AC-019-016 getEndpointRuntimeState returns UNSUPPORTED_PROFILE unless durable endpoint state exists.
AC-019-017 getTemporalRuntimeStatus maps TemporalEngineHealth.
AC-019-018 getRecoveryStatus is limited/proxy or UNSUPPORTED_PROFILE; it does not fabricate recovery authority.
AC-019-019 createSignalTemporalAct delegates to TemporalActApplicationPort.
AC-019-020 cancelTemporalAct delegates to TemporalActApplicationPort.
AC-019-021 get/list TemporalActs delegate to TemporalActObservationPort.
AC-019-022 TemporalAct request DTOs exclude Session/Identity/Authority/Policy/Surface fields.
AC-019-023 No HTTP controllers or Spring Web imports are introduced.
AC-019-024 No MCP/gRPC/ConnectRPC/WebSocket/GraphQL exposure is introduced.
AC-019-025 No NATS/JetStream concepts or imports are introduced.
AC-019-026 No SC-D adapter invocation is introduced.
AC-019-027 No persistence adapter imports are introduced in the northbound package.
AC-019-028 Implementation report explicitly records open items for future profile descent, including:
           - getEndpointRuntimeState -> UNSUPPORTED_PROFILE unless a durable endpoint runtime-state port exists;
           - getRecoveryStatus -> proxy/limited status or UNSUPPORTED_PROFILE until an explicit recovery read model exists;
           - DeviceHealth normalized authority / derivation debt remains open.
AC-019-029 Behavioral tests cover topology, health/runtime and TemporalAct operations.
AC-019-030 Negative architecture tests cover forbidden technology and boundary leakage.
AC-019-031 Spring wiring tests pass if Spring bean registration is used.
AC-019-032 Existing test suite passes.
AC-019-033 Implementation report records final file list, test results and any justified deviation from CSA/MIR scope.
```

---

## 18. Explicit debt retained after MU-019

```text
DEBT-019-001 DeviceHealth normalized authority remains deferred.
```

Device health can later be implemented through either:

```text
- a normalized DeviceHealth read/write port and persistence surface; or
- a formally accepted DERIVED_FROM_ENDPOINTS rule aligned with actual HealthStatus vocabulary.
```

MU-019 MUST NOT close this debt implicitly.

```text
DEBT-019-002 Endpoint runtime state remains deferred unless a durable read port already exists.
```

```text
DEBT-019-003 Recovery status remains limited/proxy unless an explicit recovery observation read model exists.
```

```text
DEBT-019-004 External Northbound exposure remains deferred.
```

Future external exposure requires downstream SDD/ADR, likely HTTP/OpenAPI + SSE or gRPC/ConnectRPC depending on EIB deployment constraints.

```text
DEBT-019-005 Discovery lifecycle remains reserved but not implemented.
```

Discovery requires cross-plane request-flow, SC-B runtime and SC-D contracts.

---

## 19. Implementation evidence required

The implementation report MUST include:

```text
- branch name;
- commit hash;
- final file list;
- package tree for src/main/java/com/sovereign/connect/core/northbound;
- package tree for src/test/java/com/sovereign/connect/core/northbound;
- summary of facade methods implemented;
- exact delegation map;
- status mapping table;
- DeviceHealth strategy confirmation;
- EndpointHealth authority confirmation;
- TemporalAct filter semantics confirmation;
- negative boundary scan summary;
- Spring wiring summary;
- full test command;
- full test result;
- acceptance-map result for AC-019-001 through AC-019-033;
- deviations, if any, with justification.
```

A clean local validation MUST execute:

```text
mvn test
```

Archived Surefire reports from CSA are not sufficient for accepting the implementation.

---

## 20. Promotion criteria

### 20.1 Draft -> Candidate

Status:

```text
Satisfied by v0.2.0-candidate promotion.
```

Promotion basis:

```text
- CSA v0.2.0-draft accepted as the governing CSA;
- MU-019 scope and non-goals approved;
- execution package enabled;
- no unresolved question requires expanding to Profile C or external exposure;
- AC-019-028 clarified as verifiable before candidate promotion.
```

### 20.2 Candidate -> Accepted

Promote this MIR from candidate to accepted when:

```text
- execution package is accepted;
- implementation attempt completes;
- implementation report is provided;
- AC-019-001 through AC-019-033 are mapped;
- mvn test passes locally;
- boundary/source-level tests pass;
- no forbidden exposure technology or authority leakage is introduced.
```

### 20.3 Accepted -> Validated L4

Classify MU-019 as Validated L4 when:

```text
- implementation is merged or otherwise recorded as successful;
- full local test suite passes;
- implementation report records zero blocking deviations;
- acceptance-map records PASS for all required criteria or justified non-applicability for explicitly conditional criteria.
```

---

## 21. Branch and commit

Suggested branch:

```text
feat/sc-c-mir-019-northbound-facade-seed
```

Suggested commit:

```text
feat(sc-c): add northbound facade seed
```

---

## 22. Next descent

After this MIR is approved, produce the execution package:

```text
docs/mir/mir-019/context.md
docs/mir/mir-019/codex-prompt.md
docs/mir/mir-019/acceptance-map.md
docs/mir/mir-019/implementation-report-template.md
```

The execution package MUST incorporate:

```text
CSA-SOV-SC-C-NORTHBOUND-FACADE-SEED-001 v0.2.0-draft
D-019-001 through D-019-010
AC-019-001 through AC-019-033
all non-goals from §7
all forbidden implementation patterns from §13
```
