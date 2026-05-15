# MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001

## SC-C Room / Zone Topology Seed Materialization

```text
Document ID: MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001
Title: SC-C Room / Zone Topology Seed Materialization
Version: v0.1.0-draft
Status: Draft
Date: 2026-05-15
Corpus: Sovereign Connect
Type: MIR — Materialization Increment Record
Plane: SC-C
Materialization Unit: MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001
Operational Slot: MU-013
Acceptance Target: Validated L4
Surface Category: Non-Greenfield — Code Surface Audit required
Evidence path: docs/mir/mir-013/
```

---

## Changelog v0.1.0-draft

Initial draft.

This version:

1. Opens `MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001` as the implementation descent for `PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001 v0.1.1-draft`.
2. Defines the seed scope for first-class `RoomNode`, `ZoneNode` and `TopologySpatialRelation(LOCATED_IN)` materialization.
3. Preserves `LOCATED_IN` as the only implemented seed relation.
4. Reserves semantic spatial relations (`AFFECTS`, `OBSERVES`, `CONTROLS`, `ILLUMINATES`, `CONDITIONS`, `PROTECTS`) for later scope.
5. Requires reconciliation between existing direct `roomId` / `zoneId` fields and relation-graph source-of-truth semantics.
6. Requires query support for canonical primary placement and relation-aware lookup.
7. Requires explicit consistency behavior for room/zone removal so active `LOCATED_IN` relations cannot become silent orphans.
8. Records mandatory Code Surface Audit focus areas before any `context.md` or `codex-prompt.md` is finalized.

---

## Depends on

```text
Sovereign Connect — Curator & Descent Instructions
PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.5-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.10-draft
SYNC-SOV-SC-MU-BACKLOG-001 v0.1.11-draft
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.4-draft
PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001 v0.1.1-draft
RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft
RFC-SOV-TOPOLOGY-BASE-PROJECTION-SPLIT-001 v0.1-draft
PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft
PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.7-draft
PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft
PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft
PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 v0.1.0-draft
PDR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001 v0.1.1-draft
MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001 v1.0.0-accepted
MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001 v1.0.0-accepted
```

---

## Related

```text
MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001
PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001, downstream
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001, downstream
SDD-SOV-SC-C-RECOVERY-001, downstream
PDR-SOV-SC-C-SPATIAL-RELATION-ILLUMINATES-001, future / reserved
PDR-SOV-SC-C-SPATIAL-RELATION-OBSERVES-001, future / reserved
PDR-SOV-SC-C-SPATIAL-RELATION-CONDITIONS-001, future / reserved
PDR-SOV-SC-C-SPATIAL-RELATION-CONTROLS-001, future / reserved
```

---

# 0. Status Notice

This MIR is a **Draft**.

It opens the implementation descent for Room/Zone topology seed work, but implementation is **not authorized** until:

```text
1. docs/mir/mir-013/code-surface-audit.md is produced;
2. the Code Surface Audit is reviewed and approved;
3. acceptance-map.md, context.md and codex-prompt.md are produced from the approved audit;
4. the execution package is explicitly authorized.
```

This MU is Non-Greenfield. It touches the SC-C topology model, materialization service, persistence/query boundaries and existing tests.

---

# 1. Purpose

This MIR validates the first seed implementation of relation-aware Room/Zone topology in `SC-C`.

The seed must prove that SC-C can represent and materialize canonical spatial placement using first-class topology entities and a first-class relation aggregate:

```text
RoomNode
ZoneNode
TopologySpatialRelation(LOCATED_IN)
```

The seed must preserve the central doctrine:

```text
SC-C owns canonical spatial topology.
SC-D emits facts.
SC-B transports.
Projection / Effective View lives outside SC.
```

The seed must reconcile existing direct placement fields and secondary indices with the new source-of-truth rule:

```text
TopologySpatialRelation(LOCATED_IN) is the canonical source of truth for primary spatial placement.

DeviceNode.roomId / zoneId,
EndpointNode.roomId / zoneId,
RoomNode.deviceIds / endpointIds,
ZoneNode.deviceIds / endpointIds
are derived, denormalized or compatibility surfaces when present.
```

---

# 2. Design Decisions

## MIR-013-D-001 — Descend the approved Room/Zone PDR

This MU descends:

```text
PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001 v0.1.1-draft
```

The implementation MUST preserve the PDR decisions:

```text
RoomNode and ZoneNode are first-class Base Topology entities.
TopologySpatialRelation is a first-class SC-C aggregate.
LOCATED_IN is the only implemented seed relation.
Provider-side spatial refs are metadata, not canonical identity.
Projection remains outside SC-C.
```

## MIR-013-D-002 — LOCATED_IN only

The seed MUST implement only:

```text
TopologySpatialRelationKind.LOCATED_IN
```

The seed MUST NOT implement:

```text
AFFECTS
OBSERVES
CONTROLS
ILLUMINATES
CONDITIONS
PROTECTS
```

unless a later MIR explicitly expands scope.

## MIR-013-D-003 — Relation graph is placement source of truth

The seed MUST treat `TopologySpatialRelation(LOCATED_IN)` as the source of truth for primary placement.

Direct fields and index lists may remain for compatibility and query efficiency, but they MUST NOT become independent authorities.

Required rule:

```text
relation graph > compatibility roomId/zoneId fields > secondary indices
```

A mismatch between relation graph and compatibility/index state is a consistency error.

## MIR-013-D-004 — Endpoint placement may override inherited device placement

Endpoint placement follows the PDR rule:

```text
Endpoint-level LOCATED_IN relation overrides inherited Device-level LOCATED_IN relation.
```

If no endpoint-level primary placement exists, endpoint effective placement MAY inherit device placement.

The seed must not erase EndpointNode as a first-class operational locus.

## MIR-013-D-005 — Secondary indices are not source of truth

If the current implementation has secondary index fields such as:

```text
RoomNode.deviceIds
RoomNode.endpointIds
ZoneNode.deviceIds
ZoneNode.endpointIds
```

then the seed MAY update them as denormalized compatibility state.

However:

```text
secondary indices MUST remain consistent with LOCATED_IN relations;
secondary indices MUST NOT be used as the source of truth for placement.
```

If some fields do not exist in the current codebase, the Code Surface Audit MUST record that fact and the seed MUST avoid depending on nonexistent fields unless the execution package explicitly adds them.

## MIR-013-D-006 — Provider spatial refs are metadata and materialization input

Provider-side spatial refs may appear in facts or metadata, for example:

```text
providerRoomRef
providerAreaRef
providerZoneRef
```

They MAY seed `RoomDiscoveryFact`, `ZoneDiscoveryFact` or `SpatialAssignmentFact`.

They MUST NOT become canonical:

```text
roomId
zoneId
relationId
```

SC-C must canonicalize, reject or defer them according to materialization rules.

## MIR-013-D-007 — No silent spatial orphans

A RoomNode or ZoneNode MUST NOT be removed while active `LOCATED_IN` relations point to it as target unless the accepted structural mutation explicitly rejects, cascades, quarantines or repairs those relations.

Silent orphan creation is invalid.

The first seed MAY leave removal out of scope. If removal is out of scope, tests MUST still ensure that implementation does not introduce silent orphan behavior accidentally.

Final delete/cascade/quarantine/repair policy remains downstream:

```text
SDD-SOV-SC-C-RECOVERY-001
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001
```

## MIR-013-D-008 — Materialization ordering must be explicit

The Code Surface Audit MUST inspect whether current materialization already requires rooms/zones to exist before devices/endpoints are materialized.

Known suspected risk:

```text
DefaultTopologyMaterializationService.chooseRoomId(...)
may throw when no rooms exist in the current topology.
```

If confirmed, the seed must make the ordering dependency explicit:

```text
RoomDiscoveryFact / ZoneDiscoveryFact materialization precedes DeviceDiscoveryFact / EndpointDiscoveryFact when placement is required.
```

The seed MUST NOT hide this ordering dependency inside implicit fallback behavior.

## MIR-013-D-009 — QUARANTINE decision must be audited before implementation

The PDR allows ambiguous spatial metadata to be quarantined.

The Code Surface Audit MUST verify whether the current `MaterializationDecisionKind` includes:

```text
QUARANTINE_PENDING_REVIEW
```

If it does not, the execution package must choose one of two paths:

```text
A. add QUARANTINE_PENDING_REVIEW to the enum if this is safe and scoped;
B. approximate quarantine with REJECT_AMBIGUOUS_BINDING for the seed and reserve QUARANTINE_PENDING_REVIEW for later.
```

The Codex prompt MUST NOT invent a third behavior.

Recommended seed default unless the audit proves low risk:

```text
Use REJECT_AMBIGUOUS_BINDING as the seed approximation.
Reserve QUARANTINE_PENDING_REVIEW for a later materialization governance patch.
```

## MIR-013-D-010 — Preserve MU-011 and MU-012 closures

This MU MUST preserve:

```text
MU-011: structural topology persistence must not overwrite health/state.
MU-012: materializer must not depend directly on concrete persistence adapters.
```

Specifically:

```text
DefaultTopologyMaterializationService MUST NOT regain a dependency on H2BaseTopologyRepository.
H2BaseTopologyRepository.save(...) MUST NOT regain implicit health/state writes.
```

---

# 3. Implementation Scope

Allowed seed scope:

```text
- TopologySpatialRelation aggregate or record;
- TopologySpatialRelationKind.LOCATED_IN;
- relation subject/target value objects if needed;
- RoomDiscoveryFact;
- ZoneDiscoveryFact, if required by audit/PDR descent;
- SpatialAssignmentFact or DeviceRoomAssignmentFact / EndpointRoomAssignmentFact equivalent;
- SC-C materialization handling for rooms/zones/LOCATED_IN;
- relation-aware persistence in the existing seed persistence layer;
- query support for LOCATED_IN and primary placement;
- consistency checks between relation graph and compatibility fields/indices;
- topologyVersion advancement on accepted spatial structural mutation;
- duplicate/noop/rejected fact behavior;
- tests for recovery/query after relation materialization;
- tests preserving SC-C / SC-B / SC-D / Projection boundaries.
```

Disallowed seed scope:

```text
- semantic spatial relations beyond LOCATED_IN;
- ILLUMINATES / OBSERVES / CONTROLS / CONDITIONS / PROTECTS;
- 3D geometry engine;
- maps or visual spatial layout;
- Surface layout coordinates;
- Projection / Effective View;
- Session / Identity / Authority / Policy;
- Hub memory;
- SC-B runtime;
- NATS / JetStream;
- Vert.x;
- real SC-D adapter implementation;
- production persistence schema;
- final migration framework;
- final recovery/cascade/delete policy;
- MCP facade;
- provider-specific spatial ontology as canonical topology.
```

---

# 4. Code Surface Audit Requirement

This MU is Non-Greenfield.

The following file MUST be produced before `context.md` or `codex-prompt.md`:

```text
docs/mir/mir-013/code-surface-audit.md
```

The audit MUST inspect at minimum:

```text
1. Current RoomNode and ZoneNode code shape.
2. Whether DeviceNode.roomId exists.
3. Whether DeviceNode.zoneId exists.
4. Whether EndpointNode.roomId exists.
5. Whether EndpointNode.zoneId exists.
6. Whether RoomNode.deviceIds exists.
7. Whether RoomNode.endpointIds exists.
8. Whether ZoneNode.deviceIds exists.
9. Whether ZoneNode.endpointIds exists.
10. Current HabitatBaseTopology.rooms / zones shape.
11. Current BaseTopologyService mutation APIs.
12. Current DefaultTopologyMaterializationService placement logic.
13. Whether chooseRoomId(...) or equivalent creates room-before-device ordering dependency.
14. Current TopologyMaterializationStatePort methods and whether relation persistence requires extension.
15. Current CoreSnapshotReadPort query capabilities and whether relation-aware query requires extension.
16. Current H2 schema/tables and whether relation persistence can be added as seed table/collection.
17. Current topologyVersion advancement paths for room/zone/relation structural mutation.
18. Current MaterializationDecisionKind enum values.
19. Whether QUARANTINE_PENDING_REVIEW exists or must be approximated.
20. Tests that assume direct roomId/zoneId as source of truth.
21. Tests that assume RoomNode/ZoneNode are inert seed data.
22. MU-011 persistence boundary compatibility.
23. MU-012 materialization port boundary compatibility.
24. Working-tree hygiene and unrelated changes.
```

The audit MUST classify the implementation strategy as one of:

```text
D-MU013-A: implement relation seed with existing fields and minimal new relation storage.
D-MU013-B: implement relation seed but defer some secondary indices because code lacks fields.
D-MU013-C: patch prior PDR/code assumptions before implementation.
D-MU013-D: stop; current code surface contradicts PDR assumptions.
```

---

# 5. Minimal Seed Types

The seed should introduce or approximate the following canonical families.

## 5.1 TopologySpatialRelation

Reference shape from the PDR:

```text
relationId
kind = LOCATED_IN
subject
target
primary
confidence
source
providerRef
metadata
createdAt
updatedAt
```

The exact Java binding MUST be determined by the Code Surface Audit.

## 5.2 Relation kind

The seed must implement only:

```text
LOCATED_IN
```

Reserved only:

```text
AFFECTS
OBSERVES
CONTROLS
ILLUMINATES
CONDITIONS
PROTECTS
```

## 5.3 Facts

The seed may introduce:

```text
RoomDiscoveryFact
ZoneDiscoveryFact
SpatialAssignmentFact
DeviceRoomAssignmentFact
EndpointRoomAssignmentFact
```

or equivalent names if the Code Surface Audit shows that a narrower or more consistent local naming is required.

Required semantic coverage:

```text
room discovery
zone discovery, if required by seed path
assignment of device or endpoint to room/zone using LOCATED_IN
provider spatial refs as metadata
```

---

# 6. Materialization Behavior

The seed materialization behavior should satisfy the following.

## 6.1 RoomDiscoveryFact

An accepted room discovery may create or update canonical `RoomNode`.

Rules:

```text
providerRoomRef may be metadata.
providerRoomRef must not become roomId automatically.
accepted structural room creation advances topologyVersion.
duplicate room discovery does not advance topologyVersion.
invalid room discovery does not advance topologyVersion.
```

## 6.2 ZoneDiscoveryFact

An accepted zone discovery may create or update canonical `ZoneNode`.

Rules:

```text
ZoneNode must belong to a valid RoomNode.
providerAreaRef/providerZoneRef may be metadata.
providerAreaRef/providerZoneRef must not become zoneId automatically.
accepted structural zone creation advances topologyVersion.
duplicate zone discovery does not advance topologyVersion.
invalid zone discovery does not advance topologyVersion.
```

If the Code Surface Audit proves that `ZoneDiscoveryFact` is too large for the first seed, the execution package may defer explicit zone discovery only if:

```text
ZoneNode as a first-class model remains preserved;
LOCATED_IN to room still works;
PDR constraints are not violated;
the deferral is recorded in implementation-report.md.
```

## 6.3 SpatialAssignmentFact / DeviceRoomAssignmentFact

An accepted device placement fact may create or update the primary `LOCATED_IN` relation for a device.

Rules:

```text
subject = DEVICE / deviceId
target = ROOM or ZONE
kind = LOCATED_IN
primary = true for the primary placement relation
accepted structural placement change advances topologyVersion
```

If compatibility fields exist:

```text
DeviceNode.roomId / zoneId must be updated or validated as derived/denormalized compatibility state.
```

## 6.4 EndpointRoomAssignmentFact

An accepted endpoint placement fact may create or update the primary `LOCATED_IN` relation for an endpoint.

Rules:

```text
subject = ENDPOINT / endpointId
target = ROOM or ZONE
kind = LOCATED_IN
primary = true for the primary placement relation
endpoint-level primary placement overrides device inherited placement
accepted structural placement change advances topologyVersion
```

If compatibility fields exist:

```text
EndpointNode.roomId / zoneId must be updated or validated as derived/denormalized compatibility state.
```

## 6.5 Rejected / duplicate / ambiguous facts

For rejected, duplicate or ambiguous facts:

```text
do not mutate relation graph;
do not advance topologyVersion;
do not emit TopologyChanged;
return deterministic MaterializationDecisionKind;
preserve enough diagnostic reason for implementation-report.md and tests.
```

If `QUARANTINE_PENDING_REVIEW` is unavailable and not added, ambiguous spatial facts should use:

```text
REJECT_AMBIGUOUS_BINDING
```

as the seed approximation.

---

# 7. Query Requirements

The seed must expose enough query behavior to prove that the relation graph is usable.

Minimum query capability, exact port shape deferred to Code Surface Audit:

```text
findRoom(habitatId, roomId)
findZone(habitatId, zoneId)
findSpatialRelation(habitatId, relationId)
findLocatedEntities(habitatId, targetId)
findRelationsForSubject(habitatId, subjectId)
resolvePrimaryPlacement(habitatId, subjectId)
```

The seed MAY implement a narrower API if tests still prove:

```text
current room/zone lookup;
LOCATED_IN persistence/recovery;
primary device placement resolution;
primary endpoint placement resolution;
secondary index consistency where applicable;
no Projection filtering.
```

Query behavior MUST be read-only:

```text
query does not mutate topology;
query does not advance topologyVersion;
query does not call SC-D;
query does not require SC-B;
query does not perform Projection.
```

---

# 8. topologyVersion Requirements

The seed MUST preserve topologyVersion doctrine.

## 8.1 Must advance topologyVersion

`topologyVersion` MUST advance on accepted structural mutations such as:

```text
RoomNode created;
RoomNode structurally changed;
ZoneNode created;
ZoneNode structurally changed;
TopologySpatialRelation(LOCATED_IN) created;
primary LOCATED_IN relation changed;
placement target changed;
secondary index materialized or repaired as part of accepted structural topology mutation;
room/zone removal, if removal is in scope;
explicit repair of orphaned relation, if repair is in scope.
```

## 8.2 Must not advance topologyVersion

`topologyVersion` MUST NOT advance for:

```text
duplicate fact;
invalid fact;
unauthorized adapter fact;
ambiguous rejected fact;
provider metadata only if not structurally accepted;
query;
Projection change;
Session / Identity / Authority / Policy change;
state-only update;
health-only update.
```

---

# 9. Persistence and Recovery Requirements

The seed may use existing seed persistence and H2-backed persistence.

The seed MUST NOT decide final production schema.

However, accepted relation materialization must survive repository/service recreation.

Minimum recovery expectations:

```text
RoomNode persists and is visible after recovery.
ZoneNode persists and is visible after recovery when implemented.
LOCATED_IN relation persists and is visible after recovery.
Primary placement resolution works after recovery.
Compatibility fields/indices remain consistent after recovery when materialized.
Health/state durability from MU-011 remains unaffected.
Materialization port boundary from MU-012 remains intact.
```

---

# 10. TopologyChanged Requirements

Accepted structural room/zone/relation mutations MUST emit or record `TopologyChanged` using the existing topology event shape validated by earlier MUs.

Required behavior:

```text
fromVersion = version before accepted structural mutation
toVersion = version after accepted structural mutation
toVersion != fromVersion
changeKinds contains relevant structural kind when available
affectedRoomIds contains affected room IDs when room/zone/placement changes
affectedZoneIds contains affected zone IDs when zone/placement changes
affectedDeviceIds contains affected device IDs when device placement changes
affectedEndpointIds contains affected endpoint IDs when endpoint placement changes
```

If the current code shape lacks room/zone affected fields, the Code Surface Audit MUST record the gap and the execution package MUST choose a minimal compatibility strategy.

The seed MUST NOT replace `TopologyChanged` with an incompatible event shape.

---

# 11. Acceptance Criteria

```text
AC-001
MIR-013 declares MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 as the implementation descent of PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001 v0.1.1-draft.

AC-002
Code Surface Audit is mandatory before context.md or codex-prompt.md.

AC-003
RoomNode remains first-class Base Topology.

AC-004
ZoneNode remains first-class Base Topology.

AC-005
TopologySpatialRelation exists as first-class SC-C relation state or the audit explains why the exact representation must differ while preserving semantics.

AC-006
LOCATED_IN is the only implemented seed relation.

AC-007
Semantic relations AFFECTS, OBSERVES, CONTROLS, ILLUMINATES, CONDITIONS and PROTECTS are not implemented.

AC-008
Provider-side room/area/zone refs remain metadata and do not become canonical roomId, zoneId or relationId.

AC-009
RoomDiscoveryFact or equivalent can materialize a canonical RoomNode.

AC-010
ZoneDiscoveryFact or equivalent can materialize a canonical ZoneNode, unless explicitly deferred by approved audit with rationale.

AC-011
Device spatial assignment can materialize or update primary LOCATED_IN relation for a device.

AC-012
Endpoint spatial assignment can materialize or update primary LOCATED_IN relation for an endpoint.

AC-013
Endpoint-level primary LOCATED_IN relation overrides inherited device placement.

AC-014
DeviceNode.roomId / zoneId, when present, remain consistent with primary LOCATED_IN relation.

AC-015
EndpointNode.roomId / zoneId, when present, remain consistent with primary LOCATED_IN relation.

AC-016
RoomNode.deviceIds / endpointIds, when present, are treated as secondary indices and remain consistent with relation graph.

AC-017
ZoneNode.deviceIds / endpointIds, when present, are treated as secondary indices and remain consistent with relation graph.

AC-018
Mismatch between relation graph and compatibility fields/indices is detected as consistency error.

AC-019
No silent orphan LOCATED_IN relation is created when RoomNode or ZoneNode target is missing.

AC-020
Accepted room/zone/relation structural mutation advances topologyVersion.

AC-021
Duplicate/noop/rejected spatial facts do not advance topologyVersion.

AC-022
State-only and health-only behavior from prior MUs remains non-structural and does not advance topologyVersion.

AC-023
Accepted structural room/zone/relation mutation emits or records compatible TopologyChanged.

AC-024
Rejected/noop/duplicate facts do not emit TopologyChanged.

AC-025
Relation-aware query or equivalent read boundary can resolve primary placement.

AC-026
Room/Zone/relation state persists across repository/service recreation.

AC-027
Core Snapshot Query remains canonical and non-projective.

AC-028
SC-C remains free of Projection / Effective View behavior.

AC-029
SC-C remains free of Session, Identity, Authority, Policy, Surface and Hub memory dependencies.

AC-030
No SC-B runtime, NATS, JetStream, Vert.x or real SC-D adapter is required.

AC-031
MU-011 persistence boundary hardening remains intact.

AC-032
MU-012 materialization port boundary remains intact.

AC-033
Existing SC-C kernel tests continue passing.

AC-034
New tests cover room materialization, zone materialization if implemented, LOCATED_IN relation materialization, primary placement resolution, topologyVersion behavior and boundary exclusions.

AC-035
Build/test command succeeds.
```

---

# 12. Failure Signals

```text
FS-001
RoomNode is treated as Projection or Surface layout.

FS-002
ZoneNode is treated as Projection or Surface layout.

FS-003
TopologySpatialRelation is treated as metadata instead of relation state.

FS-004
Provider room/area/zone ref becomes canonical roomId or zoneId.

FS-005
Provider room/area/zone ref becomes canonical relationId.

FS-006
SC-D assigns final roomId, zoneId or relationId.

FS-007
SC-D emits final TopologyChanged.

FS-008
SC-B decides placement or materializes Room/Zone topology.

FS-009
LOCATED_IN is not implemented as seed relation.

FS-010
Semantic relations beyond LOCATED_IN enter the first seed.

FS-011
DeviceNode.roomId becomes independent source of truth over relation graph.

FS-012
EndpointNode.roomId becomes independent source of truth over relation graph.

FS-013
RoomNode.deviceIds or endpointIds become source of truth over relation graph.

FS-014
ZoneNode.deviceIds or endpointIds become source of truth over relation graph.

FS-015
Accepted spatial structural mutation fails to advance topologyVersion.

FS-016
Duplicate/noop/rejected spatial fact advances topologyVersion.

FS-017
Projection change advances topologyVersion.

FS-018
Query filters rooms/zones/devices/endpoints by user, role, session, authority or policy.

FS-019
SC-B runtime, NATS, JetStream or Vert.x becomes required.

FS-020
Real SC-D adapter implementation becomes required.

FS-021
Spatial relation persistence reintroduces health/state persistence coupling closed by MU-011.

FS-022
Materializer depends directly on H2BaseTopologyRepository or another concrete persistence adapter, reintroducing MU-012 debt.

FS-023
Room or zone removal silently leaves active LOCATED_IN relation pointing to missing target.

FS-024
Execution package ignores QUARANTINE_PENDING_REVIEW vs existing MaterializationDecisionKind audit issue.

FS-025
Execution package ignores room-before-device materialization ordering dependency if present.

FS-026
Execution package assumes compatibility fields exist without verifying them in code.
```

---

# 13. Execution Package Requirements

The execution package MUST contain:

```text
docs/mir/mir-013/MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001.md
docs/mir/mir-013/code-surface-audit.md
docs/mir/mir-013/acceptance-map.md
docs/mir/mir-013/context.md
docs/mir/mir-013/codex-prompt.md
docs/mir/mir-013/implementation-report.md
```

Required order:

```text
1. MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 v0.1.0-draft
2. code-surface-audit.md
3. architect review of audit
4. acceptance-map.md
5. context.md
6. codex-prompt.md
7. implementation
8. implementation-report.md
9. INDEX/SYNC/Production Readiness patch after validation
```

The MIR itself MUST NOT contain Codex implementation prompt text.

---

# 14. Open Questions

## OQ-MU013-001 — Exact relationId generation

Should relationId be deterministic from:

```text
habitatId + kind + subject + target
```

or generated as opaque stable ID?

Default seed recommendation:

```text
Use deterministic seed relationId if this matches current canonical ID seed discipline,
but do not promote it as final production ID strategy.
```

Final production strategy remains downstream:

```text
ADR-SOV-SC-C-ID-STRATEGY-001
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001
```

## OQ-MU013-002 — ZoneDiscoveryFact in first seed

Should explicit `ZoneDiscoveryFact` be mandatory in the first seed?

Default recommendation:

```text
Include ZoneNode as first-class model.
Implement ZoneDiscoveryFact if code surface cost is low.
If not, defer explicit zone discovery but preserve room-level LOCATED_IN seed and record deferral.
```

## OQ-MU013-003 — Secondary indices materialized or derived on query

Should RoomNode/ZoneNode secondary indices be physically updated on write, or derived from relation graph during query?

Default recommendation:

```text
Prefer derivation from relation graph if current code permits.
Allow denormalized update only if existing tests and model shape require it.
```

## OQ-MU013-004 — QUARANTINE_PENDING_REVIEW handling

Should the seed add `QUARANTINE_PENDING_REVIEW` to `MaterializationDecisionKind`?

Default recommendation:

```text
Audit first.
If enum change is low risk, add it.
Otherwise use REJECT_AMBIGUOUS_BINDING as seed approximation.
```

## OQ-MU013-005 — Relation persistence mechanism

Should H2 seed persistence use a dedicated relation table, embed relations in topology JSON, or both?

Default recommendation:

```text
Use the minimal seed mechanism that proves durability and queryability without finalizing production schema.
Do not pre-empt SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001.
```

---

# 15. Accepted Basis

```text
A-MU013-001
PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001 v0.1.1-draft is approved.

A-MU013-002
RoomNode, ZoneNode and TopologySpatialRelation are first-class SC-C Base Topology entities.

A-MU013-003
LOCATED_IN is the first and only seed spatial relation.

A-MU013-004
Provider-side spatial refs are metadata, not canonical identity.

A-MU013-005
Base Topology remains separate from Projection / Effective View.

A-MU013-006
EndpointNode remains a first-class operational locus.

A-MU013-007
MU-011 closed structural topology vs health persistence coupling.

A-MU013-008
MU-012 closed direct materializer dependency on concrete persistence adapters.

A-MU013-009
The seed is Non-Greenfield and requires Code Surface Audit.
```

---

# 16. Version Disposition

This draft is ready for architect review.

Recommended next action after approval:

```text
Produce docs/mir/mir-013/code-surface-audit.md from the current repository.
```

Implementation prompt is not authorized yet.

---

# 17. Suggested Branch and Commits

For adding the MIR:

```text
Branch:
  feat/sc-c-room-zone-topology-seed

Commit:
  docs(sc-c): open room zone topology seed mir
```

For the later implementation, after execution package approval:

```text
Commit:
  feat(sc-c): add room zone topology seed
```
