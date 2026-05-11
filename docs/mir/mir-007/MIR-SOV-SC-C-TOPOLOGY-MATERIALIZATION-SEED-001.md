# MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001

## SC-C Topology Materialization Seed Materialization

```text
Document ID: MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001
Title: SC-C Topology Materialization Seed Materialization
Version: v1.0.0-accepted
Status: Accepted
Date: 2026-05-10
Corpus: Sovereign Connect
Type: MIR — Materialization Increment Record
Plane: SC-C
Materialization Unit: MU-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001
Operational Slot: MU-007

Supersedes:
  - MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001 v0.3.0-candidate
  - MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001 v0.2.0-candidate
  - MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001 v0.1.0-draft

Depends on:
  - INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.4-draft
  - SYNC-SOV-SC-MU-BACKLOG-001 v0.1.5-draft
  - RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
  - RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft
  - PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft
  - PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.6-draft
  - PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft
  - PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft
  - PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 v0.1.0-draft
  - PDR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001 v0.1.1-draft
  - MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 v1.0.0-accepted

Execution package:
  - docs/mir/mir-007/MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001.md
  - docs/mir/mir-007/context.md
  - docs/mir/mir-007/codex-prompt.md
  - docs/mir/mir-007/acceptance-map.md
  - docs/mir/mir-007/implementation-report.md

Execution package status:
  - context.md: final / accepted
  - codex-prompt.md: final / accepted
  - acceptance-map.md: final / accepted
  - implementation-report.md: pending post-execution template

Target repository:
  - sovereign-connect

Suggested branch:
  - feat/sc-c-topology-materialization-007

Suggested commit:
  - feat(sc-c): add topology materialization seed
```

---

## 0. Status Notice

This MIR is **Accepted**.

Implementation is authorized under this MIR.

This MIR prepares the first seed implementation of SC-C Topology Materialization.

It preserves the central thesis:

```text
SC-D emits facts.
SC-C materializes canonical topology.
SC-C emits TopologyChanged.
```

The execution package is code-surface-aware and includes a mandatory Code Surface Audit.

---

## 1. Purpose

This MIR validates that SC-C can materialize canonical Base Topology from local topology facts without requiring a real SC-D adapter, SC-B transport, Projection, Session, Identity, Authority or Policy.

The seed must prove:

```text
TopologyFact input
  → SC-C materialization decision
  → canonical DeviceNode / EndpointNode / CapabilityNode mutation
  → topologyVersion advancement only when structural
  → persistent Base Topology
  → TopologyChanged transitional event
  → Core Snapshot Query observes the materialized topology
```

This MIR is the first bridge from a static/persisted kernel to an active topology materialization kernel.

---

## 2. Design Decisions

### MIR-007-D-001 — Local facts are sufficient for seed

The seed does not require a real SC-D adapter.

It may define local fact records in SC-C test/domain packages:

```text
DeviceDiscoveryFact
EndpointDiscoveryFact
CapabilityDiscoveryFact
DeviceStateFact
HealthFact
```

These local records are seed-level representations of future SC-D fact payloads.

They must not be treated as final SC-D protocol ABI.

### MIR-007-D-002 — SC-C owns canonical identity

Provider IDs must not become canonical IDs.

The seed uses deterministic test-local canonical IDs derived from provider refs.

Reference seed mapping:

```text
deviceId =
  "device." + providerId + "." + providerDeviceId

endpointId =
  "endpoint." + providerId + "." + providerDeviceId + "." + providerEndpointId

capabilityId =
  "capability." + providerId + "." + providerDeviceId + "." + providerEndpointId + "." + providerCapabilityKey
```

Implementations MAY sanitize illegal characters, but MUST preserve determinism.

Forbidden:

```text
deviceId == providerDeviceId
endpointId == providerEndpointId
capabilityId == providerCapabilityKey
random UUIDs for seed canonical IDs without deterministic mapping
```

### MIR-007-D-003 — Existing `TopologyChanged` shape is preserved

The seed must preserve the already implemented transitional `TopologyChanged` shape:

```text
fromVersion
toVersion
changeKinds
affectedDeviceIds
affectedEndpointIds
```

The seed must not introduce a singular `topologyVersion` / `changeKind` replacement.

Default seed path:

```text
Record causationFactId in MaterializationDecision.
Reuse existing TopologyChanged in emittedChanges.
```

### MIR-007-D-004 — Admission may be stubbed, but must exist

The seed must include an admission predicate or equivalent gate.

Default:

```text
Predicate<String adapterInstanceId> admittedAdapterPredicate
```

The seed must prove:

```text
admitted adapter fact may materialize topology;
non-admitted adapter fact must not mutate topology.
```

This does not implement full Adapter Lifecycle.

### MIR-007-D-005 — RelationFact is deferred

`RelationFact` is not mandatory for seed.

Endpoint and capability attachment may be handled directly through endpoint/capability facts.

Full relation materialization is downstream.

### MIR-007-D-006 — Materialization delegates structural mutations to BaseTopologyService

`TopologyMaterializationService` MUST NOT implement an independent topology mutation engine.

Structural materialization MUST delegate to `BaseTopologyService` or additive mutation methods on `BaseTopologyService`.

Rationale:

```text
MU-001, MU-002, MU-004, MU-006 and MU-010 validated topologyVersion advancement,
TopologyChanged emission, persistence and query behavior through the existing
SC-C kernel services.

Topology Materialization adds the fact → decision → mutation orchestration layer.
It must not bypass the validated mutation path.
```

Required delegation rule:

```text
Endpoint structural materialization SHOULD use BaseTopologyService.addEndpointWithResult(...).

Device and capability structural materialization MUST either:
  A. use existing BaseTopologyService methods if available; or
  B. add minimal additive BaseTopologyService methods following the existing
     addEndpointWithResult(...) pattern.

TopologyMaterializationService MUST NOT directly mutate HabitatBaseTopology
and save through the repository as a bypass of BaseTopologyService.
```

### MIR-007-D-007 — Capability materialization requires an update helper, not endpoint re-addition

Capability materialization MUST NOT call `addEndpointWithResult(...)` with an already existing endpointId.

Rationale:

```text
addEndpointWithResult(...) delegates to addEndpoint(...), which rejects duplicate
endpointId. Updating endpoint capabilities through this path would either throw
or violate duplicate semantics.
```

Seed rule:

```text
CapabilityDiscoveryFact MUST use an additive BaseTopologyService helper such as:

  addCapabilityWithResult(habitatId, endpointId, capability)

or the seed must stop and report if the helper cannot be implemented safely.
```

Default for MIR-007:

```text
Implement the minimal additive helper, because AC-004 requires capability materialization.
```

### MIR-007-D-008 — HealthFact is endpoint-level only for seed

The concrete durable API currently supports:

```text
saveEndpointHealth(...)
findEndpointHealth(...)
```

The seed MUST treat `HealthFact` as endpoint-level.

If `providerEndpointId` is absent:

```text
return REJECT_INVALID_FACT
or NOOP with reason "device-level health persistence not available in seed"
```

The seed MUST NOT introduce device-level health persistence unless an existing durable repository method already supports it.

---

## 3. Implementation Scope

Allowed:

```text
- topology fact records;
- materialization decision record;
- materialization decision kind enum;
- topology materialization service;
- local admission predicate;
- deterministic test-local canonical ID mapping;
- use of existing BaseTopologyService;
- minimal additive BaseTopologyService methods only if required;
- use of existing H2BaseTopologyRepository;
- use of existing CoreSnapshotQueryService;
- tests for accepted structural materialization;
- tests for duplicate/noop behavior;
- tests for invalid/rejected fact behavior;
- tests for state/health non-structural behavior;
- tests for non-admitted adapter rejection;
- tests for TopologyChanged transitional event semantics.
```

Disallowed:

```text
- materializer-local independent mutation engine;
- direct repository save bypassing BaseTopologyService for structural mutation;
- real SC-D adapter implementation;
- SC-B transport implementation;
- NATS / JetStream;
- REST/gRPC/WebSocket API;
- MCP facade;
- Projection / Effective View;
- Session / Identity / Authority / Policy;
- full Adapter Lifecycle;
- production database schema;
- external wire ABI;
- full RelationFact materialization;
- device-level HealthFact persistence unless already supported;
- deletion/removal materialization unless unavoidable.
```

---

## 4. Code Surface Audit Requirement

This MIR is an implementation-composition MIR.

Therefore, `context.md` MUST contain a Code Surface Audit listing:

```text
exact API surface;
exact Java signatures;
side effects;
exception behavior;
durability behavior;
in-process memory traps;
event accumulation behavior;
required preconditions;
missing helper methods;
methods not to call;
stop conditions.
```

The accepted `context.md` fulfills this requirement.

---

## 5. Minimal Seed Types

The seed should introduce or approximate:

```text
TopologyFact
DeviceDiscoveryFact
EndpointDiscoveryFact
CapabilityDiscoveryFact
DeviceStateFact
HealthFact
MaterializationDecision
MaterializationDecisionKind
TopologyMaterializationService
DefaultTopologyMaterializationService
```

Recommended package:

```text
com.sovereign.connect.core.topology.materialization
```

Seed may omit:

```text
TopologyProposal
RelationFact implementation
QUARANTINE_PENDING_REVIEW
REJECT_AMBIGUOUS_BINDING
REJECT_STALE_FACT
```

unless needed by tests.

---

## 6. Materialization Service Behavior

The seed materialization service should expose:

```java
public MaterializationDecision materialize(String habitatId, TopologyFact fact)
```

Required behavior:

```text
DeviceDiscoveryFact:
  accepted fact can create or update canonical DeviceNode through BaseTopologyService path.

EndpointDiscoveryFact:
  accepted fact can create canonical EndpointNode under canonical DeviceNode,
  preferably through BaseTopologyService.addEndpointWithResult(...).

CapabilityDiscoveryFact:
  accepted fact can attach canonical capability under EndpointNode through BaseTopologyService path.

DeviceStateFact:
  persists state through repository, does not advance topologyVersion.

HealthFact:
  persists endpoint health through repository, does not advance topologyVersion.

Duplicate fact:
  NOOP or REJECT_DUPLICATE, no topologyVersion advance, no TopologyChanged.

Invalid fact:
  REJECT_INVALID_FACT, no topologyVersion advance, no TopologyChanged.

Non-admitted adapter:
  REJECT_UNAUTHORIZED_ADAPTER, no topologyVersion advance, no TopologyChanged.
```

---

## 7. TopologyChanged Requirement

For accepted structural materialization, emitted events must use the existing transitional shape.

Required assertions:

```text
fromVersion == version before materialization
toVersion == version after materialization
toVersion != fromVersion
changeKinds contains structural kind
affectedDeviceIds contains canonical deviceId when device affected
affectedEndpointIds contains canonical endpointId when endpoint affected
```

For duplicate/noop/rejected/non-structural facts:

```text
emittedChanges is empty
topologyVersion unchanged
```

Causation requirement:

```text
MaterializationDecision.causationFactId == fact.factId
```

---

## 8. topologyVersion Requirements

The implementation must prove:

```text
accepted structural materialization advances topologyVersion;
duplicate fact does not advance topologyVersion;
invalid fact does not advance topologyVersion;
non-admitted fact does not advance topologyVersion;
state-only fact does not advance topologyVersion;
health-only fact does not advance topologyVersion;
query after materialization does not advance topologyVersion.
```

---

## 9. Persistence and Query Requirements

The seed must use the durable path:

```text
H2BaseTopologyRepository
BaseTopologyService
CoreSnapshotQueryService
```

After accepted materialization and repository/service/query recreation:

```text
CoreSnapshotQueryService.findCurrentSnapshot(habitatId)
```

must observe:

```text
materialized DeviceNode;
materialized EndpointNode;
materialized CapabilityNode or capability under endpoint;
current topologyVersion.
```

Rejected/noop facts must leave query snapshot unchanged.

---

## 10. Acceptance Criteria

```text
AC-001
A topology materialization service or equivalent boundary exists.

AC-002
DeviceDiscoveryFact can materialize a canonical DeviceNode.

AC-003
EndpointDiscoveryFact can materialize a canonical EndpointNode.

AC-004
CapabilityDiscoveryFact can materialize a canonical CapabilityNode or canonical capability under EndpointNode.

AC-005
Provider refs are stored as metadata.

AC-006
providerDeviceId is not used as canonical deviceId.

AC-007
providerEndpointId is not used as canonical endpointId.

AC-008
Accepted structural materialization advances topologyVersion.

AC-009
Duplicate fact does not advance topologyVersion.

AC-010
Rejected invalid fact does not advance topologyVersion.

AC-011
State-only fact does not advance topologyVersion.

AC-012
Health-only fact does not advance topologyVersion.

AC-013
Accepted structural materialization emits TopologyChanged using existing transitional shape.

AC-014
Duplicate/noop/rejected fact does not emit TopologyChanged.

AC-015
Materialized topology persists across repository/service recreation.

AC-016
Core Snapshot Query observes materialized topology after recovery.

AC-017
Repeated same fact is idempotent.

AC-018
Conflicting provider binding is rejected or quarantined, not silently accepted.

AC-019
Facts from non-admitted adapter do not mutate topology.

AC-020
Materialization requires no SC-B implementation.

AC-021
Materialization requires no SC-D implementation beyond local fact input shapes.

AC-022
Materialization requires no Projection / Effective View.

AC-023
Materialization requires no Session / Identity / Authority / Policy.

AC-024
Existing MU-001, MU-002, MU-004, MU-006 and MU-010 tests continue passing.

AC-025
Build/test command succeeds.
```

---

## 11. Failure Signals

```text
FS-001
Provider ID becomes canonical ID.

FS-002
SC-D adapter implementation becomes required.

FS-003
SC-B transport becomes required.

FS-004
Projection / Effective View appears in materialization.

FS-005
Session, Identity, Authority or Policy becomes required.

FS-006
EndpointNode is collapsed into DeviceNode metadata.

FS-007
Duplicate fact creates duplicate DeviceNode or EndpointNode.

FS-008
Duplicate/noop/rejected fact advances topologyVersion.

FS-009
State-only or health-only fact advances topologyVersion.

FS-010
Accepted structural materialization fails to advance topologyVersion.

FS-011
Accepted structural materialization fails to persist topology.

FS-012
Accepted structural materialization fails to emit TopologyChanged.

FS-013
TopologyChanged is replaced by incompatible singular topologyVersion/changeKind variant.

FS-014
Rejected fact emits TopologyChanged.

FS-015
Non-admitted adapter mutates topology.

FS-016
Conflict is silently accepted without deterministic resolution.

FS-017
Materialization requires real device/provider/adapter.

FS-018
Materialization requires production database schema.

FS-019
Existing SC-C kernel hardening tests break.

FS-020
RelationFact becomes mandatory for seed.

FS-021
TopologyMaterializationService implements an independent topology mutation engine.

FS-022
TopologyMaterializationService bypasses BaseTopologyService and persists structural mutation directly through repository save.

FS-023
Seed canonical IDs are random, unstable or equal to provider-native IDs.

FS-024
CapabilityDiscoveryFact updates an existing endpoint by calling addEndpointWithResult(...) with the same endpointId.

FS-025
HealthFact introduces unsupported device-level health persistence.
```

---

## 12. Execution Package Requirements

The execution package includes:

```text
docs/mir/mir-007/context.md
docs/mir/mir-007/codex-prompt.md
docs/mir/mir-007/acceptance-map.md
docs/mir/mir-007/implementation-report.md
```

`context.md` is code-surface-first.

`codex-prompt.md` encodes all critical traps as mandatory rules or stop conditions.

`acceptance-map.md` preserves AC-001 through AC-025.

`implementation-report.md` remains a post-execution artifact.

---

## 13. Accepted Basis

```text
A-001
PDR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001 patched to v0.1.1-draft.

A-002
TopologyChanged compatibility decision fixed.

A-003
Admission gate decision fixed.

A-004
RelationFact deferral fixed.

A-005
Materialization insertion point fixed through MIR-007-D-006.

A-006
Deterministic seed canonical ID mapping fixed.

A-007
Code Surface Audit incorporated into context.md.

A-008
emittedEvents() delta pattern trap documented.

A-009
duplicate-before-service-call trap documented.

A-010
habitat pre-condition trap documented.

A-011
addDeviceWithResult missing helper documented.

A-012
state/health durable repository path documented.

A-013
CapabilityDiscoveryFact helper requirement added.

A-014
HealthFact endpoint-level-only seed rule added.

A-015
AC numbering normalized to AC-001 through AC-025.

A-016
implementation-report.md remains post-execution artifact.

A-017
Implementation may proceed under:
  docs/mir/mir-007/context.md
  docs/mir/mir-007/codex-prompt.md
  docs/mir/mir-007/acceptance-map.md
```

Implementation authorization:

```text
Authorized.
```

---

## 14. Changelog

```text
v1.0.0-accepted
- Promotes MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001 to Accepted.
- Authorizes implementation under docs/mir/mir-007/context.md, codex-prompt.md and acceptance-map.md.
- Incorporates Code Surface Audit as mandatory execution context.
- Documents five critical implementation traps:
  emittedEvents() accumulates,
  addEndpoint()/addEndpointWithResult throws on duplicate,
  missing habitat throws,
  addDeviceWithResult is absent,
  updateDeviceState is in-process and not durable.
- Adds MIR-007-D-007:
  CapabilityDiscoveryFact requires addCapabilityWithResult or equivalent helper,
  not addEndpointWithResult on an existing endpoint.
- Adds MIR-007-D-008:
  HealthFact is endpoint-level only for seed.
- Normalizes acceptance criteria to AC-001 through AC-025.
- Confirms target branch feat/sc-c-topology-materialization-007.
- Confirms suggested commit feat(sc-c): add topology materialization seed.
- Preserves target validation level L4.

v0.3.0-candidate
- Replaces prior context/prompt with code-surface-aware versions.
- Adds exact API surface and implementation traps.
- Adds required delta pattern for emittedEvents().
- Adds duplicate detection before BaseTopologyService calls.
- Adds habitat precondition handling.
- Adds exact addDeviceWithResult guidance.
- Adds durable state/health repository path.
- Corrects AC numbering to AC-001 through AC-025.

v0.2.0-candidate
- Adds MIR-007-D-006:
  TopologyMaterializationService delegates structural mutations to BaseTopologyService.
- Adds deterministic seed canonical ID mapping examples.
- Requires context.md to define the insertion point into the existing SC-C kernel.
- Requires codex-prompt.md to forbid bypassing BaseTopologyService for structural mutation.
- Confirms execution package under docs/mir/mir-007/.

v0.1.0-draft
- Opens MIR for SC-C Topology Materialization Seed.
- Depends on PDR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001 v0.1.1-draft.
- Defines local fact records as seed-level input.
- Defines materialization service and materialization decision.
- Preserves existing transitional TopologyChanged shape.
- Defers full RelationFact implementation.
- Allows admission predicate stub.
- Defines AC-001 through AC-025 and failure signals.
- Reserves docs/mir/mir-007/ execution package.
```

---

# Dictamen de cierre

```text
MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001
Version: v1.0.0-accepted
Status: Accepted
Target MU: MU-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001
Operational Slot: MU-007
Implementation authorization: yes
Open blockers: none known
```
