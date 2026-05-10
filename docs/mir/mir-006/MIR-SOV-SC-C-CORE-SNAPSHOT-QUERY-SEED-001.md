# MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001

## SC-C Core Snapshot Query Seed Materialization

```text
Document ID: MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
Title: SC-C Core Snapshot Query Seed Materialization
Version: v1.0.0-accepted
Status: Accepted
Date: 2026-05-10
Corpus: Sovereign Connect
Type: MIR — Materialization Increment Record
Plane: SC-C
Materialization Unit: MU-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001

Supersedes:
  - MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 v0.2.0-candidate
  - MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 v0.1.1-draft
  - MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 v0.1.0-draft

Depends on:
  - NT-SOV-SC-MATERIALIZATION-UNITS-001 v0.1.0-draft
  - PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.3-draft
  - INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.3-draft
  - SYNC-SOV-SC-MU-BACKLOG-001 v0.1.4-draft
  - RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
  - RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft
  - PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft
  - PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.6-draft
  - PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft
  - PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft
  - PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 v0.1.0-draft
  - MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 v1.0.0-accepted

Execution package:
  - docs/mir/mir-006/MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001.md
  - docs/mir/mir-006/context.md
  - docs/mir/mir-006/codex-prompt.md
  - docs/mir/mir-006/implementation-report.md
  - docs/mir/mir-006/acceptance-map.md

Execution package status:
  - context.md: authored / approved
  - codex-prompt.md: authored / approved
  - acceptance-map.md: authored / approved
  - implementation-report.md: pending post-execution

Target repository:
  - sovereign-connect

Suggested target branch:
  - feat/sc-c-mir-core-snapshot-query-006

Suggested commit:
  - feat(sc-c): add core snapshot query seed
```

---

## 0. Status Notice

This MIR is **Accepted**.

Accepted status means:

```text
- the Materialization Unit is scoped;
- dependencies are listed and pinned;
- implementation scope is bounded;
- negative scope is explicit;
- acceptance criteria are testable;
- failure signals are defined;
- context.md has been authored and approved;
- codex-prompt.md has been authored and approved;
- acceptance-map.md has been authored and approved;
- implementation-report.md remains a post-execution artifact;
- implementation is authorized.
```

Implementation is authorized under this MIR.

This MIR MUST NOT inline executable implementation context or prompt content.

The implementation context and Codex prompt live under:

```text
docs/mir/mir-006/
  MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001.md
  context.md
  codex-prompt.md
  implementation-report.md
  acceptance-map.md
```

Filenames are stable and MUST NOT include version suffixes.

Document version MUST be declared inside each document metadata header.

---

## 1. Purpose

This MIR governs the descent of:

```text
MU-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
```

The purpose of this Materialization Unit is to validate that SC-C can expose a canonical read boundary over SC-C-owned state.

`MU-001` validated Base Topology materialization.

`MU-002` validated `topologyVersion`, stale target validation and idempotency exclusion.

`MU-004` validated persistence/memory, recovery and durable access to canonical state.

`MU-006` must now validate that SC-C can query that canonical state without collapsing into Projection, Authority, Session, SC-B transport or SC-D rediscovery.

This MIR validates the following hypothesis:

```text
SC-C can expose current canonical snapshots, topologyVersion, canonical node lookup,
operational state and health through a read-only query boundary that does not
perform Projection, does not filter by actor, does not dispatch actions and does
not require SC-B replay or SC-D rediscovery.
```

---

## 2. Materialization Unit

```text
MU ID: MU-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
Title: SC-C Core Snapshot Query Seed
Type: Kernel / Query MU
Plane: SC-C
Status: Accepted MIR opened

Primary invariant validated:
  - SC-C owns Core Snapshot Query semantics.
  - Snapshot Query returns Base Topology, not Effective View.
  - Snapshot Query does not perform Projection.
  - Snapshot Query does not filter by user/session/role/authority/policy.
  - topology-bearing query responses include topologyVersion.
  - canonical lookup uses canonical IDs.
  - provider refs remain metadata.
  - state and health query do not advance topologyVersion.
  - query reads state/health from persistence/repository ports, not mutation-service memory.
  - query does not require SC-B replay.
  - query does not require SC-D rediscovery.
  - query does not dispatch commands.
```

### 2.1 MU thesis

```text
Core Snapshot Query must be validated before higher-level consumers can safely
read SC-C state.
```

Reason:

```text
Consumers need a canonical read boundary before Projection, Surfaces, admin diagnostics,
MCP exposure or command targeting can consume SC-C state without bypassing SC-C ownership.
```

### 2.2 Incremental validation value

This MU validates three things that MU-004 did not validate as a separate read boundary:

```text
1. Composition:
   CoreSnapshot integrates topology + topologyVersion + state + health
   into a coherent canonical read response.

2. Canonical ID lookup:
   findDevice and findEndpoint use canonical IDs, return not-found explicitly,
   and do not fall back to provider-native IDs.

3. Read/write separation:
   CoreSnapshotQueryService reads persisted canonical state.
   BaseTopologyService remains the mutation service.
```

### 2.3 MU expected output

The implementation attempt SHOULD produce:

```text
- CoreSnapshotQueryService or equivalent read boundary;
- CoreSnapshotReadPort or equivalent read port;
- current snapshot query by habitatId;
- current topologyVersion query by habitatId;
- device lookup by canonical deviceId;
- endpoint lookup by canonical endpointId;
- capability lookup derived from endpoint context;
- device state query from persistence/repository ports;
- endpoint health query from persistence/repository ports or persisted aggregate;
- reuse of existing validateTarget(...) for target validation support;
- tests proving query does not advance topologyVersion;
- tests proving query works after repository/service/query recreation;
- tests proving no Projection / Effective View contamination;
- tests proving no SC-B / SC-D dependency;
- implementation report with AC table.
```

---

## 3. Design Decisions

### MIR-006-D-001 — Minimal mandatory query surface

The PDR reference port is intentionally generous.

The MIR seed requires a smaller mandatory query surface.

Mandatory for L4 seed:

```text
- findCurrentSnapshot(habitatId)
- findCurrentTopologyVersion(habitatId)
- findDevice(habitatId, canonical deviceId)
- findEndpoint(habitatId, canonical endpointId)
- findDeviceState(habitatId, canonical deviceId)
- findEndpointHealth(habitatId, canonical endpointId)
```

Capability lookup may be satisfied by:

```text
- a dedicated findCapability(...) method; OR
- deriving capability lookup from findEndpoint(...) and inspecting endpoint capabilities.
```

A dedicated `findCapability(...)` method is not mandatory for this seed if endpoint-context lookup satisfies the acceptance criteria.

### MIR-006-D-002 — Reuse existing target validation

Do not introduce a second target-resolution mechanism for the seed.

The implementation SHOULD reuse the existing MU-002 method:

```java
validateTarget(String habitatId, TopologyTargetRef target, TopologyVersion requestTopologyVersion)
```

or its repository-local equivalent.

This MIR does not require:

```text
TargetResolutionSnapshot
```

A richer target-resolution read model is deferred.

### MIR-006-D-003 — Query reads persisted canonical state, not mutation-service memory

CoreSnapshotQueryService MUST read from persistence/repository ports.

It MUST NOT read operational state from `BaseTopologyService` in-process memory.

Canonical separation:

```text
BaseTopologyService owns mutations.
CoreSnapshotQueryService owns reads over persisted canonical state.
```

This is intentional.

The query layer reads what was durably persisted, not what the mutation service happens to remember in-process.

Rationale:

```text
MU-002 BaseTopologyService.findDeviceState(...) may read from an internal ConcurrentMap.
That map does not survive service recreation.
After MU-004, durable device state lives in persistence/repository memory.
Core Snapshot Query must therefore read device state through the durable read boundary,
not through BaseTopologyService.findDeviceState(...).
```

Allowed implementation approaches:

```text
Approach A:
  CoreSnapshotQueryService depends on CoreSnapshotReadPort.

Approach B:
  CoreSnapshotQueryService depends on a repository-local equivalent read port.

Approach C:
  H2BaseTopologyRepository implements CoreSnapshotReadPort for the seed,
  while preserving BaseTopologyRepository compatibility.
```

Disallowed:

```text
- CoreSnapshotQueryService delegates device-state reads to BaseTopologyService.findDeviceState(...).
- CoreSnapshotQueryService depends on BaseTopologyService internal ConcurrentMap semantics.
- query correctness depends on keeping the same BaseTopologyService instance alive.
- query recovery passes only because the service instance was not actually recreated.
```

---

## 4. Implementation Scope

This MIR allows implementation of a **minimal SC-C Core Snapshot Query seed**.

It does not authorize:

```text
- Projection;
- Effective View;
- user/session/authority/policy filtering;
- external query transport;
- REST/gRPC/WebSocket API;
- SC-B request/reply binding;
- MCP facade;
- historical snapshot query;
- command dispatch;
- provider rediscovery;
- action execution.
```

### 4.1 Query boundary

Implementation MUST introduce a read-only query boundary.

Preferred form:

```text
CoreSnapshotQueryService
```

Preferred dependency:

```text
CoreSnapshotReadPort
```

Disallowed:

```text
- mixing query semantics into SC-B;
- querying SC-D directly;
- performing Projection;
- adding actor/session/authority filters;
- dispatching commands from query;
- using BaseTopologyService in-process memory as query truth.
```

### 4.2 Current snapshot query

Implementation MUST support querying the current canonical snapshot by `habitatId`.

Minimum returned content:

```text
habitatId
topologyVersion
HabitatBaseTopology or equivalent
deviceStates map
endpointHealth map
readAt or equivalent timestamp
```

If `BaseTopologySnapshot` from MU-004 exists, the query SHOULD reuse it.

Required invariant:

```text
response.topologyVersion == response.topology.topologyVersion
```

If an internal `BaseTopologySnapshot` is used:

```text
response.topologyVersion == baseTopologySnapshot.topologyVersion
baseTopologySnapshot.topologyVersion == baseTopologySnapshot.topology.topologyVersion
```

### 4.3 Current topologyVersion query

Implementation MUST support querying current `topologyVersion` directly.

Required invariant:

```text
findCurrentTopologyVersion(habitatId) == findCurrentSnapshot(habitatId).topologyVersion
```

### 4.4 Device lookup

Implementation MUST support lookup by canonical `deviceId`.

Rules:

```text
- canonical deviceId is the lookup key;
- providerDeviceId MUST NOT be treated as canonical deviceId;
- response MUST include topologyVersion;
- missing canonical deviceId MUST return not-found.
```

Provider refs MAY be returned as metadata.

### 4.5 Endpoint lookup

Implementation MUST support lookup by canonical `endpointId`.

Rules:

```text
- canonical endpointId is the lookup key;
- providerEndpointId MUST NOT be treated as canonical endpointId;
- response MUST include topologyVersion;
- missing canonical endpointId MUST return not-found.
```

Provider refs MAY be returned as metadata.

### 4.6 Capability lookup

Implementation MUST prove capability lookup semantics through endpoint context.

Allowed:

```text
findEndpoint(...)
  -> endpoint.capabilities()
  -> contains canonical capabilityId
```

A dedicated `findCapability(...)` method is not required.

Disallowed:

```text
- provider capability key as canonical capability identity;
- capability lookup through SC-D;
- capability lookup through Projection.
```

### 4.7 Operational state query

Implementation MUST support querying operational device state if present in SC-C memory/persistence.

Rules:

```text
- query MUST read from persistence/repository ports;
- query MUST NOT delegate to BaseTopologyService.findDeviceState(...);
- query MUST NOT advance topologyVersion;
- query MUST NOT call SC-D;
- query MUST NOT become Projection;
- query MUST use canonical deviceId.
```

### 4.8 Endpoint health query

Implementation MUST support querying endpoint health if present in SC-C memory/persistence.

Rules:

```text
- query MUST read from persistence/repository ports or from the persisted aggregate;
- query MUST NOT advance topologyVersion;
- query MUST NOT call SC-D;
- query MUST NOT become Policy;
- query MUST use canonical endpointId.
```

### 4.9 Recovery query

Implementation MUST prove query works after repository/service/query layer recreation.

Minimum flow:

```text
1. create/persist topology;
2. persist device state and endpoint health through the same read boundary that query will use;
3. recreate repository/service/query layer;
4. query current snapshot;
5. query current topologyVersion;
6. query device;
7. query endpoint;
8. query device state through query service;
9. query endpoint health through query service;
10. invoke validateTarget(...) using recovered topologyVersion/current target.
```

### 4.10 Non-mutating read rule

A query MUST NOT:

```text
- create topology;
- mutate topology;
- advance topologyVersion;
- update state;
- update health;
- append mutation record;
- emit TopologyChanged;
- publish events;
- call SC-D;
- dispatch action.
```

---

## 5. Negative Scope

This MIR explicitly excludes:

```text
Projection
Effective View
user-specific filtering
role-specific filtering
guest-specific filtering
authority-aware filtering
policy-aware filtering
Surface-specific layout
Hub conversation context
SC-B query transport
SC-B request/reply binding
REST API
gRPC API
WebSocket API
MCP facade
historical snapshot query
snapshot diff
snapshot pagination
event sourcing
audit/TL retention
command dispatch
action execution
real SC-D adapter calls
provider-native query API
provider rediscovery
production query DTO schema
external wire ABI
authorization layer
TargetResolutionSnapshot as mandatory seed mechanism
```

This MIR also excludes adding final query transport doctrine.

---

## 6. Required Invariants

```text
I-001 SC-C owns Core Snapshot Query semantics.
I-002 Snapshot Query returns Base Topology, not Effective View.
I-003 Snapshot Query does not perform Projection.
I-004 Snapshot Query does not filter by user/session/role/authority/policy.
I-005 topology-bearing query responses include topologyVersion.
I-006 response topologyVersion equals snapshot topologyVersion, if snapshot envelope is used.
I-007 snapshot topologyVersion equals aggregate topologyVersion, if snapshot envelope is used.
I-008 canonical lookup uses canonical IDs.
I-009 provider refs remain metadata.
I-010 provider refs do not become canonical IDs.
I-011 state query does not advance topologyVersion.
I-012 health query does not advance topologyVersion.
I-013 query reads state/health from persistence/repository ports, not mutation-service memory.
I-014 query recovery does not depend on retaining the same BaseTopologyService instance.
I-015 query does not require SC-B replay.
I-016 query does not require SC-D rediscovery.
I-017 query does not require Hub, Projection, Session, Identity, Authority or Policy.
I-018 query does not dispatch actions.
I-019 query does not choose storage technology.
I-020 validateTarget(...) is reused rather than duplicated for seed target validation support.
```

---

## 7. Minimal Acceptance Criteria

### 7.1 Query boundary criteria

```text
AC-001
A Core Snapshot Query service, port or equivalent read boundary exists.

AC-002
The query boundary is read-only and does not mutate topology.

AC-003
The query boundary does not advance topologyVersion.
```

### 7.2 Snapshot criteria

```text
AC-004
Current snapshot can be queried by habitatId.

AC-005
Current snapshot response includes topologyVersion.

AC-006
response.topologyVersion equals snapshot.topologyVersion when a snapshot envelope is used,
or equals response.topology.topologyVersion when CoreSnapshot is the direct response.

AC-007
snapshot.topologyVersion equals snapshot.topology.topologyVersion when a snapshot envelope is used.
```

### 7.3 topologyVersion criteria

```text
AC-008
Current topologyVersion can be queried directly.

AC-009
Current topologyVersion query returns the same value as current snapshot query.
```

### 7.4 Canonical lookup criteria

```text
AC-010
Device lookup by canonical deviceId succeeds for existing device.

AC-011
Endpoint lookup by canonical endpointId succeeds for existing endpoint.

AC-012
Capability lookup succeeds for existing capability under canonical endpoint context.

AC-013
Missing canonical deviceId or endpointId returns not-found.

AC-014
Provider refs are returned only as metadata when present.

AC-015
Provider refs are not treated as canonical deviceId or endpointId.
```

### 7.5 State and health criteria

```text
AC-016
Operational device state can be queried if present in SC-C memory/persistence.

AC-017
Device state query does not advance topologyVersion.

AC-018
Endpoint health can be queried if present in SC-C memory/persistence.

AC-019
Endpoint health query does not advance topologyVersion.
```

### 7.6 Recovery and target validation criteria

```text
AC-020
Query works after repository/service/query layer recreation using persisted state.

AC-021
Query recovery does not require SC-B replay.

AC-022
Query recovery does not require SC-D rediscovery.

AC-023
Query does not require Hub, Projection, Session, Identity, Authority or Policy.

AC-024
Query result or recovered topologyVersion supports existing validateTarget(...) semantics without introducing a second target-resolution mechanism.
```

### 7.7 Build criteria

```text
AC-025
Build/test command succeeds.
```

---

## 8. Failure Signals

Implementation MUST stop and report upstream if any of the following occurs.

```text
FS-001 Snapshot Query requires Projection.
FS-002 Snapshot Query returns Effective View.
FS-003 Snapshot Query filters by user, role, guest, session, authority or policy.
FS-004 Snapshot Query requires Hub conversation memory.
FS-005 Snapshot Query requires SC-B replay.
FS-006 Snapshot Query requires SC-D rediscovery.
FS-007 Snapshot Query calls provider adapters directly.
FS-008 Snapshot Query dispatches command or action.
FS-009 Snapshot Query advances topologyVersion.
FS-010 Topology-bearing response lacks topologyVersion.
FS-011 response.topologyVersion differs from snapshot.topologyVersion when snapshot envelope is used.
FS-012 snapshot.topologyVersion differs from snapshot.topology.topologyVersion when snapshot envelope is used.
FS-013 Provider refs become canonical identity.
FS-014 Canonical deviceId or endpointId lookup requires provider-native IDs.
FS-015 State query forces topologyVersion advancement.
FS-016 Health query forces topologyVersion advancement.
FS-017 Historical snapshot query becomes required for seed.
FS-018 Final query transport must be chosen before seed can proceed.
FS-019 Storage technology choice becomes part of query doctrine.
FS-020 SC-C query cannot operate after persistence recovery.
FS-021 Implementation introduces TargetResolutionSnapshot as mandatory seed mechanism instead of reusing validateTarget(...).
FS-022 Capability lookup requires a dedicated method even though endpoint-context lookup is sufficient for seed.
FS-023 CoreSnapshotQueryService reads device state from BaseTopologyService.findDeviceState(...).
FS-024 Query correctness depends on retaining the same BaseTopologyService instance across recovery.
```

---

## 9. Upstream Patch Protocol

The implementation report MUST classify issues as one of:

```text
documentation ambiguity
missing type
missing flow
missing invariant
contradiction
wrong ownership
projection contamination
identity-boundary risk
query-transport coupling risk
target-resolution duplication risk
storage coupling risk
read-write separation violation
SC-B leakage
SC-D leakage
implementation bug
out-of-scope request
repository mismatch
```

Patch routing:

```text
If Snapshot Query ownership is unclear:
  patch PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001.

If Projection boundary is unclear:
  patch PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001
  and/or open PDR-SOV-SC-TOPOLOGY-PROJECTION-BOUNDARY-001.

If canonical lookup semantics are unclear:
  patch PDR-SOV-SC-CANONICAL-CONTRACT-001
  or PDR-SOV-SC-ENDPOINT-NODE-001.

If topologyVersion consistency is unclear:
  patch PDR-SOV-SC-TOPOLOGY-VERSION-001.

If persistence/recovery support is unclear:
  patch PDR-SOV-SC-C-PERSISTENCE-MEMORY-001.

If query must read service-local state:
  patch this MIR and review read/write boundary.

If target validation support is unclear:
  patch PDR-SOV-SC-ACTION-TARGET-001
  or PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001.

If query transport becomes necessary:
  stop implementation and report out-of-scope request.

If SC-D rediscovery becomes necessary:
  stop implementation and report out-of-scope request.

If Projection becomes necessary:
  stop implementation and report out-of-scope request.
```

---

## 10. Codex Prompt Reference

Canonical execution package:

```text
Context:
  docs/mir/mir-006/context.md

Codex Prompt:
  docs/mir/mir-006/codex-prompt.md

Implementation Report:
  docs/mir/mir-006/implementation-report.md

Acceptance Map:
  docs/mir/mir-006/acceptance-map.md
```

Rules:

```text
- context.md MUST be read before codex-prompt.md is executed.
- codex-prompt.md MUST reference context.md.
- codex-prompt.md MUST use acceptance-map.md for AC numbering.
- implementation-report.md MUST reference this MIR version.
- acceptance-map.md MUST preserve AC-001 through AC-025 numbering.
- context.md and codex-prompt.md MAY be refined without advancing this MIR version.
- Any refinement that changes scope, acceptance criteria, failure signals or required invariants MUST advance this MIR or produce a patch.
```

---

## 11. Suggested Branch

```text
feat/sc-c-mir-core-snapshot-query-006
```

---

## 12. Suggested Commit

Preferred:

```text
feat(sc-c): add core snapshot query seed
```

If the increment mainly adds query tests:

```text
test(sc-c): validate core snapshot query semantics
```

If the increment mainly adds a query service:

```text
feat(sc-c): add core snapshot query service
```

---

## 13. Post-Implementation Report Requirements

The implementation project MUST return:

```text
1. Summary
2. Files changed
3. Domain/query types added or modified
4. Query services/ports added or modified
5. Repository or persistence interactions used
6. Tests added
7. Acceptance criteria result table
8. Required invariants preserved
9. Deviations from MIR
10. Failure signals encountered
11. Assumptions made
12. Corpus issues discovered
13. Recommended upstream patches
14. Recommended next MU
```

Invariant table MUST include:

```text
SC-C owns Core Snapshot Query semantics: preserved/broken/unclear
Snapshot Query returns Base Topology, not Effective View: preserved/broken/unclear
Snapshot Query does not perform Projection: preserved/broken/unclear
Snapshot Query does not filter by actor/session/authority/policy: preserved/broken/unclear
topology-bearing responses include topologyVersion: preserved/broken/unclear
response topologyVersion equals snapshot topologyVersion: preserved/broken/unclear/not applicable
snapshot topologyVersion equals aggregate topologyVersion: preserved/broken/unclear/not applicable
canonical lookup uses canonical IDs: preserved/broken/unclear
provider refs remain metadata: preserved/broken/unclear
state query reads persisted state, not mutation-service memory: preserved/broken/unclear
state query does not advance topologyVersion: preserved/broken/unclear
health query does not advance topologyVersion: preserved/broken/unclear
query recovery does not require SC-B: preserved/broken/unclear
query recovery does not require SC-D: preserved/broken/unclear
query does not require Hub/Projection/Session/Identity/Authority/Policy: preserved/broken/unclear
query does not dispatch actions: preserved/broken/unclear
validateTarget(...) reused rather than duplicated: preserved/broken/unclear
```

---

## 14. Non-blocking Open Questions

```text
OQ-001
Should CoreSnapshot include state/health inline, or should state/health be queried separately?

Default:
  CoreSnapshot SHOULD compose known state/health for the seed.
  Separate query methods remain mandatory.

OQ-002
Should capability lookup become a dedicated query method?

Default:
  Not required for seed.
  Endpoint-context lookup is sufficient if AC-012 is satisfied.

OQ-003
Should TargetResolutionSnapshot be introduced later?

Default:
  Yes, possibly later.
  For MU-006 seed, reuse validateTarget(...).

OQ-004
Should provider-ref lookup be supported?

Default:
  No for seed.
  Provider refs may be returned as metadata.

OQ-005
Should historical snapshot query be supported?

Default:
  No.
  Current snapshot only.

OQ-006
Should query return domain records directly or query DTOs?

Default:
  Use repository-local simplest shape for seed.
  Avoid external wire ABI decisions.

OQ-007
Should query be exposed through SC-B request/reply?

Default:
  No for seed.
  Transport binding belongs downstream.
```

---

## 15. Accepted Closure

Accepted blockers resolved:

```text
B-001
context.md authored and approved.

B-002
codex-prompt.md authored and approved.

B-003
acceptance-map.md authored and approved.

B-004
implementation-report.md placeholder created as post-execution artifact.

B-005
MIR-006-D-003 added to resolve read/write split.

B-006
Endpoint health source clarified in context and prompt.

B-007
Duplicate context body removed.
```

Accepted basis:

```text
A-001
Final reviewer approval for Accepted scope: satisfied.

A-002
implementation-report.md remains post-execution artifact: satisfied.

A-003
Implementation may proceed under:
  docs/mir/mir-006/context.md
  docs/mir/mir-006/codex-prompt.md
  docs/mir/mir-006/acceptance-map.md
```

Implementation authorization:

```text
Authorized.
```

---

## 16. Changelog

```text
v1.0.0-accepted
- Promotes MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 from Candidate to Accepted.
- Authorizes implementation under docs/mir/mir-006/context.md, codex-prompt.md and acceptance-map.md.
- Confirms implementation-report.md remains pending post-execution artifact.
- Confirms target branch feat/sc-c-mir-core-snapshot-query-006.
- Confirms suggested commit feat(sc-c): add core snapshot query seed.
- Preserves target validation level L4.

v0.2.0-candidate
- Promotes MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 from Draft to Accepted.
- Confirms docs/mir/mir-006/context.md has been authored and approved.
- Confirms docs/mir/mir-006/codex-prompt.md has been authored and approved.
- Confirms docs/mir/mir-006/acceptance-map.md has been authored and approved.
- Keeps implementation-report.md as pending post-execution artifact.
- Incorporates MIR-006-D-003:
  CoreSnapshotQueryService reads persisted state through repository/read ports,
  not BaseTopologyService internal state.
- Confirms CoreSnapshotReadPort as seed read boundary.
- Confirms H2BaseTopologyRepository may implement CoreSnapshotReadPort without breaking BaseTopologyRepository.
- Confirms capability lookup may be endpoint-derived.
- Confirms validateTarget(...) is reused rather than duplicated.
- Clarifies endpoint health must be persisted/read through the same boundary the query service uses.
- Removes duplicate body from context.md.
- Preserves target validation level L4.

v0.1.1-draft
- Adds MIR-006-D-003:
  CoreSnapshotQueryService reads from persistence/repository ports,
  not from BaseTopologyService internal state.
- Clarifies BaseTopologyService owns mutations while CoreSnapshotQueryService owns reads.
- Adds rationale that MU-002 device state may live in an internal ConcurrentMap,
  which cannot be the query truth after recovery.
- Adds disallowed paths preventing delegation to BaseTopologyService.findDeviceState(...).
- Adds FS-023 for query reading state from BaseTopologyService internal state.
- Adds FS-024 for recovery correctness depending on retaining the same BaseTopologyService instance.
- Adds read/write separation violation as an implementation report issue class.
- Updates acceptance criteria and invariants to emphasize persisted-state reads.

v0.1.0-draft
- Opens MIR for MU-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001.
- Targets Core Snapshot Query validation after MU-001, MU-002 and MU-004 L4 validation.
- Depends on PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 v0.1.0-draft.
- Defines Core Snapshot Query as a canonical SC-C read boundary.
- Defines mandatory seed query surface:
  current snapshot,
  current topologyVersion,
  device lookup,
  endpoint lookup,
  device state query,
  endpoint health query.
- Adds MIR-006-D-001:
  PDR reference port is generous; seed requires smaller mandatory query surface.
- Allows capability lookup to be derived from endpoint context.
- Adds MIR-006-D-002:
  reuse validateTarget(...) from MU-002; do not introduce TargetResolutionSnapshot as mandatory seed mechanism.
- Defines read-only/non-mutating query rule.
- Defines recovery query requirement after repository/service recreation.
- Excludes Projection, Effective View, authority/session filtering, SC-B transport, SC-D rediscovery and command dispatch.
- Defines acceptance criteria, failure signals and upstream patch protocol.
- Reserves execution package under docs/mir/mir-006/.
- Sets target validation level L4.
```

---

# Dictamen de cierre

```text
MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
Version: v1.0.0-accepted
Status: Accepted
Target MU: MU-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
Target validation level: L4
Implementation authorization: yes
Open Accepted blockers: none known
```
