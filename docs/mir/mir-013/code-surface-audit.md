# Code Surface Audit — MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001

```text
Audit ID:            CSA-MU-013
Version:             v0.1.1-merged
Status:              Draft / ready for architect review
MIR:                 MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 v0.1.0-draft
MU:                  MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001
Operational Slot:    MU-013
PDR:                 PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001 v0.1.1-draft
Repository:          sovereign-connect
Repository state:    feat/sc-c-materialization-state-port, post-MU-012
Inspected artifact:  sovereign-connect-012.zip
Date:                2026-05-15
Surface category:    Non-Greenfield — full audit required
Target path:         docs/mir/mir-013/code-surface-audit.md
```

---

## 0. Merge note

This audit merges two CSA-MU-013 drafts:

1. the first audit produced from `sovereign-connect-012.zip`; and
2. the architect-provided audit with a more precise field inventory, constructor constraints and implementation strategy.

The architect-provided audit is used as the primary base.

This merged version adds:

```text
- repository/HEAD/test baseline and working-tree hygiene notes;
- explicit validation/mutation surface analysis;
- TopologyChanged / TopologyChangeKind compatibility analysis;
- port-boundary and persistence-boundary assertions;
- removal/orphan relation validation surface;
- stop conditions for the Codex prompt;
- acceptance mapping preview;
- final decision register for architect approval.
```

---

## 1. Purpose

This audit answers three questions before MU-013 implementation begins:

1. What already exists in the codebase that MU-013 builds on?
2. What implicit contracts and constraints must be preserved?
3. What is the correct minimal implementation strategy for the Room/Zone/LOCATED_IN seed?

The audit binds:

```text
PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001 v0.1.1-draft
MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 v0.1.0-draft
```

to the real repository surface after MU-011 and MU-012.

---

## 2. Repository state

Observed repository state from `sovereign-connect-012.zip`:

```text
Branch: feat/sc-c-materialization-state-port
HEAD:   7bac85e refactor(sc-c): extract topology materialization state port.
```

Baseline test evidence included in the ZIP:

```text
Surefire reports: 28 tests
Failures: 0
Errors: 0
Skipped: 0
```

Local execution note:

```text
mvn test could not be re-run in this environment because Maven is not installed.
The implementation package MUST require local mvn test execution after MU-013.
```

Working-tree hygiene note:

```text
The ZIP contains unrelated local/editor noise and historical MIR filename drift.
Before committing MU-013, git status --short MUST be clean except for MU-013 files.
The MU-013 implementation commit MUST NOT include .idea/*, unrelated .gitignore edits,
or MIR-001/MIR-002/MIR-012 filename-noise unless explicitly part of a separate docs commit.
```

---

## 3. Audit verdict

```text
Surface classification: Non-Greenfield
Refactor gate:         OPTION A — Refactor in scope
Decision:              D-MU013-A with explicit compatibility patches
Expected acceptance:   Validated L4
```

MU-013 is substantial but in-scope.

It introduces:

```text
- first-class TopologySpatialRelation model;
- a new structural collection inside HabitatBaseTopology;
- RoomDiscoveryFact and ZoneDiscoveryFact materialization;
- spatial assignment materialization for LOCATED_IN;
- relation-aware validation;
- relation-aware query support;
- compatibility changes to topology event/change metadata.
```

The codebase is extendable. The audit does not find a corpus contradiction requiring PDR revision.

---

## 4. Field inventory — PDR-assumed fields verified

All direct fields referenced by the PDR §4 reconciliation exist in the current codebase.

### 4.1 RoomNode

```java
public record RoomNode(
    String roomId,
    String roomName,
    List<String> zoneIds,
    List<String> deviceIds,
    List<String> endpointIds,
    RoomTraits traits
) implements TopologyNode
```

Findings:

```text
PASS: roomId exists and is required.
PASS: roomName exists and is required.
PASS: zoneIds exists and is required; it may be empty.
PASS: deviceIds exists and is required; it may be empty.
PASS: endpointIds exists and is required; it may be empty.
PASS: RoomTraits exists and is required.
GAP:  RoomSpatialDescriptor does not exist in current code.
```

Current `RoomTraits` shape:

```text
privateRoom
guestDefaultRoom
```

These are declarative hints only. They MUST NOT be interpreted as policy grants, Projection rules or visibility filters.

---

### 4.2 ZoneNode

```java
public record ZoneNode(
    String zoneId,
    String zoneName,
    String roomId,
    List<String> deviceIds,
    List<String> endpointIds,
    ZoneTraits traits
) implements TopologyNode
```

Findings:

```text
PASS: zoneId exists and is required.
PASS: zoneName exists and is required.
PASS: roomId exists and is required.
PASS: deviceIds exists and is required; it may be empty.
PASS: endpointIds exists and is required; it may be empty.
PASS: ZoneTraits exists and is required.
GAP:  ZoneSpatialDescriptor does not exist in current code.
```

Critical constraint:

```text
ZoneNode.roomId is NON-NULL required.
The Zone LOCATED_IN Room relationship is already enforced through a direct field,
not through TopologySpatialRelation.
```

MU-013 MUST preserve this constraint.

---

### 4.3 DeviceNode spatial fields

Current `DeviceNode` includes:

```java
String roomId
String zoneId
List<String> endpointIds
```

Constructor constraints:

```text
roomId != null
zoneId != null
```

Findings:

```text
PASS: DeviceNode.roomId exists.
PASS: DeviceNode.zoneId exists.
PASS: DeviceNode.endpointIds exists.
IMPORTANT: roomId and zoneId are mandatory, not nullable.
```

Decision for MU-013:

```text
Do not introduce unplaced device support in the seed.
The PDR's "MAY be unplaced" language is permissive, not mandatory.
Keeping roomId/zoneId required preserves existing tests and avoids migration complexity.
```

---

### 4.4 EndpointNode spatial fields

Current `EndpointNode` includes:

```java
String roomId
String zoneId
```

Constructor constraints:

```text
roomId != null
zoneId != null
```

Current materialization behavior:

```text
Endpoint placement inherits parent DeviceNode.roomId / zoneId at creation time.
```

Findings:

```text
PASS: EndpointNode.roomId exists.
PASS: EndpointNode.zoneId exists.
IMPORTANT: endpoint roomId and zoneId are mandatory, not nullable.
IMPORTANT: endpoint-level override requires a new explicit spatial assignment path.
```

Decision for MU-013:

```text
Do not introduce unplaced endpoint support in the seed.
Endpoint placement may inherit device placement unless an explicit endpoint LOCATED_IN
assignment is materialized.
```

---

### 4.5 HabitatBaseTopology

Current record:

```java
public record HabitatBaseTopology(
    String habitatId,
    TopologyVersion topologyVersion,
    List<RoomNode> rooms,
    List<ZoneNode> zones,
    List<DeviceNode> devices,
    List<EndpointNode> endpoints,
    TopologyMetadata metadata
)
```

Findings:

```text
PASS: rooms exists as a root collection.
PASS: zones exists as a root collection.
PASS: devices exists as a root collection.
PASS: endpoints exists as a root collection.
GAP:  no List<TopologySpatialRelation> spatialRelations field exists.
```

Primary MU-013 model addition:

```text
Add List<TopologySpatialRelation> spatialRelations to HabitatBaseTopology.
```

---

## 5. New types to create

### 5.1 Domain types

```text
TopologySpatialRelation
TopologySpatialRelationKind
TopologySpatialSubject
TopologySpatialTarget
TopologySpatialEntityType
RelationConfidence
SpatialRelationSource
ProviderSpatialRef
```

Minimum seed requirements:

```text
TopologySpatialRelationKind MUST include LOCATED_IN.
Reserved relation kinds MUST remain non-implemented:
  AFFECTS
  OBSERVES
  CONTROLS
  ILLUMINATES
  CONDITIONS
  PROTECTS
```

`TopologySpatialEntityType` should include at least:

```text
ROOM
ZONE
DEVICE
ENDPOINT
```

### 5.2 Fact types

Create:

```text
RoomDiscoveryFact
ZoneDiscoveryFact
SpatialAssignmentFact
```

`SpatialAssignmentFact` may be replaced by narrower variants if implementation clarity improves:

```text
DeviceRoomAssignmentFact
DeviceZoneAssignmentFact
EndpointRoomAssignmentFact
EndpointZoneAssignmentFact
```

Preferred seed strategy:

```text
Use a single SpatialAssignmentFact with subjectType / subjectId and targetType / targetId.
```

Rationale:

```text
A single relation-oriented fact minimizes sealed-interface expansion and aligns with
TopologySpatialRelation as the canonical structural output.
```

### 5.3 Decision kind

Current `MaterializationDecisionKind` lacks both quarantine and ambiguous binding values.

Recommended seed addition:

```text
REJECT_AMBIGUOUS_SPATIAL_BINDING
```

Do not add in MU-013:

```text
QUARANTINE_PENDING_REVIEW
```

Rationale:

```text
REJECT_AMBIGUOUS_SPATIAL_BINDING is precise enough to reject ambiguous provider
spatial metadata without introducing quarantine persistence/query semantics.
Full QUARANTINE_PENDING_REVIEW is deferred to a future materialization governance MU.
```

If a broader enum is preferred later, `REJECT_AMBIGUOUS_SPATIAL_BINDING` may be mapped or renamed by a downstream contract patch. For MU-013, the explicit spatial variant minimizes scope bleed.

---

## 6. Critical constraints from existing code

### CONSTRAINT-013-001 — DeviceNode roomId / zoneId are non-null

Any `DeviceNode` construction must provide valid `roomId` and `zoneId`.

Impact:

```text
Rooms and zones MUST exist before device materialization can succeed in the current seed model.
```

### CONSTRAINT-013-002 — chooseRoomId / chooseZoneId throw on empty collections

Current behavior:

```text
If roomHint matches an existing room, use it.
Otherwise use the first existing room.
If no room exists, throw IllegalArgumentException.

If zoneHint matches an existing zone, use it.
Otherwise use the first existing zone.
If no zone exists, throw IllegalArgumentException.
```

Ordering invariant:

```text
RoomDiscoveryFact and ZoneDiscoveryFact must be materialized before DeviceDiscoveryFact
when the topology starts empty or lacks placement targets.
```

Stop condition:

```text
If implementation requires DeviceDiscoveryFact to materialize before any room/zone exists,
stop and request a separate design decision. That would require changing mandatory
DeviceNode.roomId / zoneId semantics.
```

### CONSTRAINT-013-003 — addDeviceWithResult and addEndpointWithResult maintain secondary indices

Current `BaseTopologyService.addDeviceWithResult(...)` updates:

```text
RoomNode.deviceIds
ZoneNode.deviceIds
```

Current `BaseTopologyService.addEndpointWithResult(...)` updates:

```text
RoomNode.endpointIds
ZoneNode.endpointIds
DeviceNode.endpointIds
```

Seed decision:

```text
Maintain both compatibility fields/indices and LOCATED_IN relations in sync.
```

This is the safest seed profile because the current code and tests already rely on direct fields and secondary indices.

### CONSTRAINT-013-004 — HabitatBaseTopology construction sites

`HabitatBaseTopology` is constructed directly in multiple service paths, including the current `BaseTopologyService` mutation helpers.

Known construction sites in `BaseTopologyService.java`:

```text
createInitialTopology(...)
addDeviceWithResult(...)
addEndpointWithResult(...)
addCapabilityWithResult(...)
updateEndpointHealth(...)
updateDeviceState(...)
withVersionAndMetadata(...)
```

Line-number snapshot observed in the audited artifact:

```text
75, 118, 164, 227, 268, 331
```

Adding `spatialRelations` requires updating these construction paths.

Required compatibility strategy:

```text
- old/compatibility constructor or factory must initialize spatialRelations = List.of();
- mutation helpers must carry current.spatialRelations();
- new relation helpers must append/update spatialRelations explicitly.
```

ObjectMapper note:

```text
Existing topology_json snapshots do not contain spatialRelations.
The implementation must handle missing/null spatialRelations as empty list.
A compact constructor default or @JsonCreator/@JsonSetter strategy is acceptable.
```

### CONSTRAINT-013-005 — DeviceDiscoveryFact already carries roomHint / zoneHint

Current `DeviceDiscoveryFact` already has:

```text
roomHint
zoneHint
```

Current materializer already calls:

```text
chooseRoomId(topology, fact.roomHint())
chooseZoneId(topology, fact.zoneHint())
```

Seed decision:

```text
Keep the hint mechanism intact for compatibility.
After successful device materialization, create LOCATED_IN relation(s) consistent
with the resolved roomId/zoneId.
```

Important semantic distinction:

```text
roomHint / zoneHint are input evidence.
TopologySpatialRelation(LOCATED_IN) is the persistent canonical structural output.
```

### CONSTRAINT-013-006 — ZoneNode.roomId is required

When adding `addZoneWithResult(...)`, the service must validate:

```text
zone.roomId() resolves to an existing RoomNode.
```

This preserves the existing direct-field invariant while introducing the relation graph.

### CONSTRAINT-013-007 — invalid hint fallback is legacy/default placement

Current behavior silently falls back to the first room/zone if a hint is absent or invalid.

Risk:

```text
If interpreted as accepted provider placement, fallback could violate the relation-source-of-truth doctrine.
```

Seed rule:

```text
Preserve fallback for existing DeviceDiscoveryFact behavior, but label it as default/legacy placement.
New explicit spatial assignment semantics must use SpatialAssignmentFact or equivalent.
```

Implementation-report requirement:

```text
Record whether DeviceDiscoveryFact creates implicit default LOCATED_IN relation(s) from chosen room/zone.
```

---

## 7. Existing validation and mutation surface

### 7.1 Current validation

`BaseTopologyService.validateTopology(...)` already validates direct-field and secondary-index consistency, including:

```text
- unique roomId, zoneId, deviceId, endpointId;
- RoomNode.zoneIds refer to existing zones;
- RoomNode.deviceIds refer to existing devices;
- RoomNode.endpointIds refer to existing endpoints;
- ZoneNode.roomId refers to existing room;
- ZoneNode.deviceIds refer to existing devices;
- ZoneNode.endpointIds refer to existing endpoints;
- DeviceNode.roomId refers to existing room;
- DeviceNode.zoneId refers to existing zone;
- DeviceNode.endpointIds refer to existing endpoints;
- EndpointNode.deviceId refers to existing device;
- EndpointNode.roomId refers to existing room;
- EndpointNode.zoneId refers to existing zone.
```

Required MU-013 extension:

```text
validateTopology(...) must validate spatialRelations.
```

Minimum relation validation:

```text
- relationId non-blank and unique;
- kind == LOCATED_IN for implemented seed paths;
- reserved semantic relation kinds are rejected or unavailable;
- subjectType/subjectId refer to existing RoomNode, ZoneNode, DeviceNode or EndpointNode as allowed;
- targetType/targetId refer to existing RoomNode or ZoneNode;
- Zone LOCATED_IN target must be Room;
- Device LOCATED_IN target may be Room or Zone;
- Endpoint LOCATED_IN target may be Room or Zone;
- Zone target belongs to a valid Room;
- compatibility fields and secondary indices remain consistent with primary relations;
- no relation may point to a missing RoomNode or ZoneNode target;
- no silent orphan relation target is allowed.
```

Primary uniqueness warning:

```text
If both Room and Zone placement are represented as LOCATED_IN, primary uniqueness
must be scoped by:

  subjectType + subjectId + relationKind + targetType

not merely by:

  subjectType + subjectId + relationKind

Otherwise a device or endpoint could not simultaneously have a primary room placement
and a primary zone placement.
```

### 7.2 Structural helpers to add

Current `BaseTopologyService` exposes:

```text
createInitialTopology(...)
addDeviceWithResult(...)
addEndpointWithResult(...)
addCapabilityWithResult(...)
updateEndpointHealth(...)
updateDeviceState(...)
```

Gaps:

```text
No addRoomWithResult(...).
No addZoneWithResult(...).
No addSpatialRelationWithResult(...).
No remove room/zone helper.
```

Required additions:

```java
addRoomWithResult(String habitatId, RoomNode room) -> TopologyMutationResult
addZoneWithResult(String habitatId, ZoneNode zone) -> TopologyMutationResult
addSpatialRelationWithResult(String habitatId, TopologySpatialRelation relation) -> TopologyMutationResult
```

Alternative accepted name:

```java
upsertPrimaryLocatedInWithResult(String habitatId, TopologySpatialRelation relation)
```

Rules:

```text
- Helpers must mutate HabitatBaseTopology through BaseTopologyService.
- Helpers must call validateTopology(...).
- Helpers must call repository.save(mutated).
- Helpers must advance topologyVersion on accepted structural mutation.
- Helpers must emit compatible TopologyChanged.
- Helpers must not write endpoint_health or device_states.
```

---

## 8. Materialization surface

### 8.1 Current fact family

Current sealed `TopologyFact` permits only:

```text
DeviceDiscoveryFact
EndpointDiscoveryFact
CapabilityDiscoveryFact
DeviceStateFact
HealthFact
```

Required extension:

```text
RoomDiscoveryFact
ZoneDiscoveryFact
SpatialAssignmentFact
```

### 8.2 Materializer switch

`DefaultTopologyMaterializationService.materialize(...)` must add switch cases for the new fact types.

Rules:

```text
RoomDiscoveryFact -> BaseTopologyService.addRoomWithResult(...)
ZoneDiscoveryFact -> BaseTopologyService.addZoneWithResult(...)
SpatialAssignmentFact -> BaseTopologyService.addSpatialRelationWithResult(...) or upsertPrimaryLocatedInWithResult(...)
```

### 8.3 Preserve MU-012 materialization port boundary

Current materializer state reads/writes go through:

```text
TopologyMaterializationStatePort
```

MU-013 MUST NOT add structural relation writes to `TopologyMaterializationStatePort`.

Allowed:

```text
statePort.findByHabitatId(...)
statePort.findCurrentVersion(...)
```

Forbidden:

```text
statePort.saveRoom(...)
statePort.saveZone(...)
statePort.saveLocatedInRelation(...)
statePort.save(HabitatBaseTopology)
```

Structural topology mutation belongs through `BaseTopologyService`, not the state port.

---

## 9. TopologyChanged and TopologyChangeKind surface

### 9.1 Current TopologyChangeKind

Current enum:

```text
DEVICE_ADDED
DEVICE_REMOVED
ENDPOINT_ADDED
ENDPOINT_REMOVED
ENDPOINT_CHANGED
CAPABILITY_ADDED
CAPABILITY_REMOVED
TRAITS_CHANGED
SPATIAL_ASSIGNMENT_CHANGED
```

Gaps:

```text
ROOM_ADDED missing.
ZONE_ADDED missing.
ROOM_CHANGED missing.
ZONE_CHANGED missing.
ROOM_REMOVED missing.
ZONE_REMOVED missing.
```

Required MU-013 minimum:

```text
Add ROOM_ADDED.
Add ZONE_ADDED.
Use SPATIAL_ASSIGNMENT_CHANGED for LOCATED_IN assignment changes.
```

Optional:

```text
Add ROOM_CHANGED / ZONE_CHANGED only if update semantics are implemented.
Do not add ROOM_REMOVED / ZONE_REMOVED unless removal is in seed scope.
```

### 9.2 Current TopologyChanged

Current event shape:

```java
TopologyChanged(
    UUID eventId,
    Instant timestamp,
    String habitatId,
    String fromVersion,
    String toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds,
    String reason
)
```

Gaps:

```text
No affectedRoomIds.
No affectedZoneIds.
```

Preferred MU-013 decision:

```text
Extend TopologyChanged additively with affectedRoomIds and affectedZoneIds.
Preserve the existing constructor as a compatibility constructor that defaults
room/zone affected IDs to empty lists.
```

Rationale:

```text
Room/Zone topology seed should not emit room or zone additions through an event
shape that has no room/zone affected fields. Additive extension aligns implementation
with current canonical direction while preserving existing call sites.
```

Allowed weaker fallback:

```text
Keep current TopologyChanged shape and report room/zone identity only through
changeKinds + reason. If chosen, implementation-report.md MUST record this as a
seed limitation.
```

Related surface:

```text
TopologyMutationResult and TopologyMutationRecord may also lack affectedRoomIds / affectedZoneIds.
If room/zone helpers return TopologyMutationResult, either extend it additively or
record why room/zone affected IDs remain event-only for seed.
```

Do not expand the `mutation_records` H2 schema in MU-013 unless tests require durable mutation-record readback for room/zone affected IDs.

---

## 10. Persistence surface

### 10.1 Seed persistence strategy

Current H2 structural persistence:

```text
topology_snapshots(habitat_id, topology_version, topology_json, captured_at)
```

Current non-structural persistence:

```text
device_states
endpoint_health
```

Recommended seed strategy:

```text
Persist TopologySpatialRelation inside topology_json as part of HabitatBaseTopology.
Do not introduce a dedicated topology_spatial_relations H2 table in MU-013.
```

Rationale:

```text
LOCATED_IN is structural topology state.
Persisting it inside topology_json preserves MU-011 structural-only save semantics
and avoids premature production schema design.
```

Deferred:

```text
Normalized topology_spatial_relations table belongs to SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001.
Flyway/Liquibase migrations are out of scope.
```

### 10.2 H2BaseTopologyRepository

Rules:

```text
H2BaseTopologyRepository.save(...) remains structural-only.
No health/state write side effects may be introduced.
No relation persistence may use endpoint_health or device_states.
```

### 10.3 InMemoryBaseTopologyRepository

If `spatialRelations` is part of `HabitatBaseTopology`, the in-memory repository should not need a relation-specific API.

---

## 11. Query surface

Current `CoreSnapshotQueryService` exposes:

```text
findCurrentSnapshot
findCurrentTopologyVersion
findDevice
findEndpoint
findDeviceState
findEndpointHealth
```

Gaps:

```text
No findRoom.
No findZone.
No findSpatialRelation.
No findSpatialRelationsBySubject.
No findLocatedDevices.
No findLocatedEndpoints.
No resolvePrimaryPlacement.
```

Recommended MU-013 seed query methods:

```java
Optional<RoomNode> findRoom(String habitatId, String roomId)
Optional<ZoneNode> findZone(String habitatId, String zoneId)
Optional<TopologySpatialRelation> findSpatialRelation(String habitatId, String relationId)
List<TopologySpatialRelation> findSpatialRelationsBySubject(String habitatId, TopologySpatialEntityType type, String id)
List<DeviceNode> findLocatedDevices(String habitatId, String roomOrZoneId)
List<EndpointNode> findLocatedEndpoints(String habitatId, String roomOrZoneId)
Optional<TopologySpatialRelation> resolvePrimaryPlacement(String habitatId, TopologySpatialEntityType type, String id)
```

Implementation note:

```text
H2 implementations can read from the current deserialized topology snapshot
rather than from a new table.
```

Narrower API allowed only if tests still prove:

```text
- Room lookup;
- Zone lookup;
- LOCATED_IN persistence and recovery;
- device primary placement resolution;
- endpoint primary placement resolution;
- no Projection filtering.
```

---

## 12. Removal surface

Current code has no room/zone removal helpers.

PDR v0.1.1 requires:

```text
A RoomNode or ZoneNode MUST NOT be removed while active LOCATED_IN relations point
to it unless the accepted structural mutation explicitly handles those relations.
Silent orphan creation is a consistency error.
```

MU-013 scope:

```text
Do not implement room or zone removal.
```

Required invariant:

```text
validateTopology(...) must reject any LOCATED_IN relation whose target RoomNode or ZoneNode is missing.
```

---

## 13. Existing invariants to preserve

```text
INV-013-001: RoomNode and ZoneNode remain first-class Base Topology entities.
INV-013-002: TopologySpatialRelation is structural SC-C topology state, not metadata.
INV-013-003: LOCATED_IN is the only implemented seed relation kind.
INV-013-004: Semantic spatial relations remain reserved.
INV-013-005: Provider room/area/zone refs remain metadata and materialization input only.
INV-013-006: DeviceNode.roomId / zoneId remain compatibility/denormalized fields, not independent authority.
INV-013-007: EndpointNode.roomId / zoneId remain compatibility/denormalized fields, not independent authority.
INV-013-008: RoomNode.deviceIds / endpointIds remain secondary indices.
INV-013-009: ZoneNode.deviceIds / endpointIds remain secondary indices.
INV-013-010: Relation graph, compatibility fields and secondary indices remain mutually consistent.
INV-013-011: Room/Zone/LOCATED_IN structural mutation advances topologyVersion.
INV-013-012: Duplicate/rejected/ambiguous spatial facts do not advance topologyVersion.
INV-013-013: State/health behavior from MU-011 remains non-structural.
INV-013-014: H2BaseTopologyRepository.save(...) remains structural-only.
INV-013-015: DefaultTopologyMaterializationService must not depend on H2BaseTopologyRepository.
INV-013-016: Structural relation mutation must go through BaseTopologyService.
INV-013-017: Room/zone must exist before current DeviceDiscoveryFact can materialize a device.
INV-013-018: Endpoint placement inherits device placement unless an explicit endpoint LOCATED_IN override is materialized.
INV-013-019: No silent orphan relation target is allowed.
INV-013-020: Query methods are read-only and non-projective.
INV-013-021: No SC-B, NATS, JetStream, SC-D adapter, Projection, Hub, Session, Identity, Authority or Policy dependency enters the implementation.
INV-013-022: All current tests reported by the repository test suite must continue to pass.
```

---

## 14. Tests that must keep passing

Existing tests are compatibility contracts for MU-013.

```text
BaseTopologyServiceTest
CoreSnapshotQuerySeedTest
PersistenceBoundaryHardeningTest
PersistenceMemorySeedTest
ScCoreKernelHardeningTest
TopologyMaterializationSeedTest
TopologyVersionHardeningTest
```

Specific preservation points:

```text
TopologyMaterializationSeedTest:
  existing materialization paths must still work after spatialRelations is added.

PersistenceBoundaryHardeningTest:
  structural topology persistence remains separate from endpoint health.

ScCoreKernelHardeningTest:
  cross-MU regression must pass with the new spatial topology types present.

PersistenceMemorySeedTest:
  boundary assertions should be extended to verify materializer and any new spatial
  path do not import or depend on H2BaseTopologyRepository directly.

Test helper methods:
  room(), zone(), createInitialTopology(...) and direct HabitatBaseTopology construction
  must be updated to initialize spatialRelations consistently.
```

---

## 15. Required new tests

Minimum new test class:

```text
RoomZoneTopologySeedTest
```

Required test cases:

```text
1. roomDiscoveryFactMaterializesRoomNodeAndAdvancesTopologyVersion
2. duplicateRoomDiscoveryDoesNotAdvanceTopologyVersion
3. zoneDiscoveryFactMaterializesZoneNodeAndAdvancesTopologyVersion
4. duplicateZoneDiscoveryDoesNotAdvanceTopologyVersion
5. zoneDiscoveryRejectsUnknownRoom
6. deviceMaterializationCreatesLocatedInRelationToChosenRoomAndZone
7. deviceLocatedInRelationIsConsistentWithDeviceNodeRoomIdAndZoneId
8. roomNodeDeviceIdsSecondaryIndexIsConsistentWithLocatedInGraph
9. endpointSpatialAssignmentOverridesDeviceInheritedPlacement
10. endpointLocatedInRelationIsConsistentWithEndpointNodeRoomIdAndZoneId
11. primaryPlacementResolutionWorksForDeviceAndEndpoint
12. locatedInRelationPersistsAcrossRepositoryServiceRecreation
13. orphanLocatedInRelationTargetIsRejectedByValidation
14. ambiguousSpatialAssignmentUsesRejectAmbiguousSpatialBindingNotQuarantine
15. semanticRelationsBeyondLocatedInAreNotImplemented
16. materializerStillHasNoH2RepositoryFieldOrConstructor
17. healthAndStatePersistenceSemanticsFromMU011RemainUnchanged
18. allExistingScCoreTestsRemainPassing
```

Optional tests if event shape is extended:

```text
19. topologyChangedForRoomAdditionCarriesAffectedRoomId
20. topologyChangedForZoneAdditionCarriesAffectedZoneId
21. topologyChangedForSpatialAssignmentCarriesAffectedDeviceOrEndpointAndRoomOrZone
```

---

## 16. Operational non-goals

```text
- TopologySpatialRelation for AFFECTS, OBSERVES, CONTROLS, ILLUMINATES, CONDITIONS, PROTECTS.
- QUARANTINE_PENDING_REVIEW with persisted quarantine lifecycle.
- Unplaced device or unplaced endpoint support.
- Room removal or zone removal lifecycle policy.
- Final production schema.
- topology_spatial_relations production table.
- Flyway / Liquibase migrations.
- TemporalActs.
- terminal request state.
- outbox/ledger.
- SC-B runtime.
- SC-D adapter implementation.
- NATS / JetStream.
- Vert.x.
- Projection / Effective View.
- Hub, Session, Identity, Authority, Policy or Surface logic.
- 3D geometry.
- visual maps.
- presence engine.
- semantic spatial relations such as ILLUMINATES or OBSERVES.
- Provider ecosystem adapter implementation.
- RoomTraits / ZoneTraits cleanup or renaming unless required by compilation.
```

---

## 17. Implementation strategy

### Phase 1 — Add relation model types

Create the new domain types listed in §5.

Rules:

```text
- LOCATED_IN is the only implemented relation kind.
- Reserved relation kinds must not become executable materialization paths.
- ProviderSpatialRef is metadata only.
```

### Phase 2 — Add spatialRelations to HabitatBaseTopology

Target shape:

```java
public record HabitatBaseTopology(
    String habitatId,
    TopologyVersion topologyVersion,
    List<RoomNode> rooms,
    List<ZoneNode> zones,
    List<DeviceNode> devices,
    List<EndpointNode> endpoints,
    List<TopologySpatialRelation> spatialRelations,
    TopologyMetadata metadata
) { ... }
```

Compatibility requirements:

```text
- old constructor/factory initializes spatialRelations = List.of();
- compact constructor copies null to List.of() only for backward JSON compatibility;
- current mutation paths preserve current.spatialRelations();
- createInitialTopology initializes an empty relation list.
```

### Phase 3 — Extend validation and mutation helpers

Add:

```java
addRoomWithResult(...)
addZoneWithResult(...)
addSpatialRelationWithResult(...)
```

Validation must include direct fields, indices and relation graph consistency.

### Phase 4 — Extend fact family and materializer

Add:

```text
RoomDiscoveryFact
ZoneDiscoveryFact
SpatialAssignmentFact
```

Materializer rules:

```text
- RoomDiscoveryFact must be handled before dependent device materialization.
- ZoneDiscoveryFact must validate parent RoomNode.
- SpatialAssignmentFact creates LOCATED_IN relation and updates compatibility fields/indices as needed.
- DeviceDiscoveryFact should create implicit LOCATED_IN relation(s) after successful device materialization, consistent with resolved roomId/zoneId.
- EndpointDiscoveryFact may inherit device placement and may create inherited relation(s) if the seed chooses to record endpoint placement explicitly.
```

### Phase 5 — Extend query/read surface

Add relation-aware query methods as defined in §11.

H2 may derive query responses from deserialized `topology_json`.

### Phase 6 — Add tests

Implement tests listed in §15.

### Phase 7 — Run full validation

Required local command:

```bash
mvn test
```

Expected result:

```text
All previous 28 tests pass.
All new MU-013 tests pass.
0 failures, 0 errors, 0 skipped unless explicitly justified.
```

---

## 18. Stop conditions for Codex prompt

Codex must stop and report if any of these occur:

```text
STOP-013-001: Implementation requires changing DeviceNode.roomId or zoneId to nullable.
STOP-013-002: Implementation requires changing EndpointNode.roomId or zoneId to nullable.
STOP-013-003: Implementation requires making SC-D assign canonical roomId, zoneId or relationId.
STOP-013-004: Implementation requires adding NATS, JetStream, Vert.x, SC-B runtime, REST/gRPC API or a real adapter.
STOP-013-005: Implementation requires Projection, Session, Identity, Authority, Policy, Hub or Surface state.
STOP-013-006: Implementation requires DefaultTopologyMaterializationService to depend on H2BaseTopologyRepository.
STOP-013-007: Implementation requires relation writes through TopologyMaterializationStatePort instead of BaseTopologyService.
STOP-013-008: Implementation requires choosing final production persistence schema or migrations.
STOP-013-009: Implementation requires adding semantic relations beyond LOCATED_IN.
STOP-013-010: Implementation requires implementing Room/Zone removal policy.
STOP-013-011: Implementation cannot preserve MU-011 structural-only save semantics.
STOP-013-012: Implementation cannot preserve MU-012 materialization port boundary.
STOP-013-013: Implementation cannot preserve all existing tests.
STOP-013-014: Implementation requires a new H2 table for relations before architect review.
STOP-013-015: Implementation requires full QUARANTINE_PENDING_REVIEW semantics.
```

---

## 19. Executive package recommendations

### 19.1 context.md must include concrete code excerpts

Include excerpts for:

```text
RoomNode.java
ZoneNode.java
DeviceNode.java
EndpointNode.java
HabitatBaseTopology.java
BaseTopologyService.validateTopology(...)
BaseTopologyService.createInitialTopology(...)
BaseTopologyService.addDeviceWithResult(...)
BaseTopologyService.addEndpointWithResult(...)
DefaultTopologyMaterializationService.materialize(...)
DefaultTopologyMaterializationService.materializeDevice(...)
DefaultTopologyMaterializationService.materializeEndpoint(...)
DefaultTopologyMaterializationService.chooseRoomId(...)
DefaultTopologyMaterializationService.chooseZoneId(...)
MaterializationDecisionKind.java
TopologyFact.java
TopologyChanged.java
TopologyChangeKind.java
TopologyMaterializationStatePort.java
CoreSnapshotQueryService.java
CoreSnapshotReadPort.java
H2BaseTopologyRepository.save(...)
```

### 19.2 codex-prompt.md should implement in controlled phases

Recommended phases:

```text
Phase 1: Add relation model types.
Phase 2: Add spatialRelations to HabitatBaseTopology with compatibility constructor/defaulting.
Phase 3: Extend BaseTopologyService validation and mutation helpers.
Phase 4: Extend fact family and materializer switch.
Phase 5: Add minimal query methods.
Phase 6: Add tests.
Phase 7: Run mvn test and produce implementation report.
```

### 19.3 Branch and commit

Suggested branch:

```text
feat/sc-c-room-zone-topology-seed
```

Suggested implementation commit:

```text
feat(sc-c): add room zone located-in topology seed
```

---

## 20. Acceptance mapping preview

```text
AC-003 / AC-004:
  Already partially satisfied by existing RoomNode / ZoneNode model.

AC-005 / AC-006:
  Requires TopologySpatialRelation and LOCATED_IN relation kind.

AC-008:
  Requires provider spatial refs to remain metadata in facts/relation metadata.

AC-009 / AC-010:
  Requires RoomDiscoveryFact and ZoneDiscoveryFact or approved deferral.

AC-011 / AC-012 / AC-013:
  Requires SpatialAssignmentFact or equivalent and primary placement resolution.

AC-014 through AC-018:
  Requires relation graph validation against compatibility fields and indices.

AC-019:
  Requires relation target validation for missing RoomNode/ZoneNode.

AC-020 through AC-024:
  Requires topologyVersion and TopologyChanged behavior for accepted/rejected facts.

AC-025:
  Requires relation-aware query or equivalent read boundary.

AC-026:
  Requires relation persistence and recovery.

AC-031:
  Requires preserving H2BaseTopologyRepository.save(...) structural-only semantics.

AC-032:
  Requires preserving DefaultTopologyMaterializationService -> TopologyMaterializationStatePort boundary.

AC-033 through AC-035:
  Requires all existing and new tests to pass.
```

---

## 21. Decision register for architect approval

Proceed to execution package only after these decisions are accepted:

```text
DEC-013-001:
  Add spatialRelations to HabitatBaseTopology as seed structural relation storage.

DEC-013-002:
  Add REJECT_AMBIGUOUS_SPATIAL_BINDING and reserve QUARANTINE_PENDING_REVIEW.

DEC-013-003:
  Extend TopologyChanged additively with affectedRoomIds / affectedZoneIds,
  or explicitly accept the weaker compatibility strategy.

DEC-013-004:
  Add ROOM_ADDED and ZONE_ADDED to TopologyChangeKind.

DEC-013-005:
  Keep room/zone removal out of MU-013; validate against orphan relation targets.

DEC-013-006:
  Keep all structural mutations through BaseTopologyService.

DEC-013-007:
  Keep final persistence schema and migrations deferred.

DEC-013-008:
  Do not introduce unplaced device/endpoint support in MU-013.

DEC-013-009:
  Treat DeviceDiscoveryFact roomHint/zoneHint as input evidence and create
  LOCATED_IN relation(s) as persistent canonical output after accepted materialization.
```

Recommended acceptance:

```text
Accept DEC-013-001 through DEC-013-009.
```

---

## 22. Final audit verdict

```text
CSA-MU-013 v0.1.1-merged:
  Approvable with architect decision confirmation.

Recommended next step:
  Produce docs/mir/mir-013/acceptance-map.md, context.md,
  codex-prompt.md and implementation-report.md based on this audit.
```

This audit is a living artifact. If implementation reveals that `HabitatBaseTopology` modification is more disruptive than anticipated, or that a dedicated H2 relation table is required, the audit MUST be updated and architect-reviewed before the implementation continues.
