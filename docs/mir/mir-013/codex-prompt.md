# Codex Prompt — MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001

```text
MIR:              MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 v0.1.0-draft
MU:               MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001
Operational Slot: MU-013
Audit:            CSA-MU-013 v0.1.1-merged
Context:          docs/mir/mir-013/context.md v0.1.2
Branch:           feat/sc-c-room-zone-topology-seed
Expected commit:  feat(sc-c): add room zone located-in topology seed
```

---

## 0. Mandatory reading order

Read these before any code change:

```text
docs/mir/mir-013/MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001.md
docs/mir/mir-013/code-surface-audit.md
docs/mir/mir-013/acceptance-map.md
docs/mir/mir-013/context.md
```

---

## 1. Mission

Implement the SC-C Room/Zone topology seed with first-class
`TopologySpatialRelation(LOCATED_IN)` semantics.

After implementation:

```text
- RoomNode and ZoneNode are materialized from facts.
- TopologySpatialRelation(LOCATED_IN) is the source of truth for primary placement.
- LOCATED_IN relations persist in topology_json via HabitatBaseTopology.spatialRelations.
- DeviceDiscoveryFact creates LOCATED_IN relations as canonical persistent output.
- Query methods expose room/zone/spatial relation data from the snapshot.
- 28 existing tests still pass. New tests cover all above behaviors.
- No H2 import in DefaultTopologyMaterializationService.
- H2BaseTopologyRepository.save() remains structural-only.
```

---

## 2. Implementation phases

Work in strict order. Do not advance to the next phase until all compilation
errors from the current phase are resolved and the rationale for the change is clear.

---

### Phase 1 — Add new domain types

Create in `com.sovereign.connect.core.topology.model`:

**TopologySpatialRelationKind.java**

```java
public enum TopologySpatialRelationKind {
    LOCATED_IN,
    AFFECTS, OBSERVES, CONTROLS, ILLUMINATES, CONDITIONS, PROTECTS
}
```

Only `LOCATED_IN` has materialization behavior in MU-013.
Other values are reserved enum constants — no cases for them in the materializer switch.

**TopologySpatialEntityType.java**

```java
public enum TopologySpatialEntityType {
    ROOM, ZONE, DEVICE, ENDPOINT
}
```

**TopologySpatialSubject.java**

```java
public record TopologySpatialSubject(TopologySpatialEntityType type, String id) {}
```

**TopologySpatialTarget.java**

```java
public record TopologySpatialTarget(TopologySpatialEntityType type, String id) {}
```

**RelationConfidence.java**

```java
public enum RelationConfidence {
    CONFIGURED, PROVIDER_REPORTED, IMPORTED, INFERRED, UNKNOWN
}
```

**SpatialRelationSource.java**

```java
public enum SpatialRelationSource {
    MANUAL, PROVIDER, IMPORT, MIGRATION, INFERENCE, SYSTEM
}
```

**ProviderSpatialRef.java**

```java
public record ProviderSpatialRef(
    String provider,
    String providerRoomId,
    String providerAreaId,
    String providerZoneId,
    Map<String, String> nativeCoordinates
) {}
```

**TopologySpatialRelation.java**

```java
public record TopologySpatialRelation(
    String relationId,
    TopologySpatialRelationKind kind,
    TopologySpatialSubject subject,
    TopologySpatialTarget target,
    boolean primary,
    RelationConfidence confidence,
    SpatialRelationSource source,
    ProviderSpatialRef providerRef,
    Instant observedAt,
    Map<String, String> metadata
) {}
```

Rules:
- `relationId` is canonical — never a provider ID
- `providerRef` is optional (nullable) metadata
- `metadata` must not contain session/identity/authority/policy data

---

### Phase 2 — Extend TopologyChangeKind and MaterializationDecisionKind

**TopologyChangeKind.java** — add two values:

```java
ROOM_ADDED,   // ← new
ZONE_ADDED,   // ← new
// SPATIAL_ASSIGNMENT_CHANGED already exists — use it for LOCATED_IN relation events
```

**MaterializationDecisionKind.java** — add one value:

```java
REJECT_AMBIGUOUS_SPATIAL_BINDING   // ← new; for conflicting provider spatial metadata
```

---

### Phase 3 — Add spatialRelations to HabitatBaseTopology

Update `HabitatBaseTopology` to add the new field before `metadata`:

```java
public record HabitatBaseTopology(
    String habitatId,
    TopologyVersion topologyVersion,
    List<RoomNode> rooms,
    List<ZoneNode> zones,
    List<DeviceNode> devices,
    List<EndpointNode> endpoints,
    List<TopologySpatialRelation> spatialRelations,   // ← NEW, before metadata
    TopologyMetadata metadata
) {
    public HabitatBaseTopology {
        // ... existing requireNonNull for all other fields ...
        // Jackson backward compatibility: null → empty list (NOT requireNonNull)
        spatialRelations = List.copyOf(spatialRelations != null ? spatialRelations : List.of());
        // ...
    }
}
```

**Now fix all 6 construction sites in BaseTopologyService.**
Every `new HabitatBaseTopology(...)` must pass `spatialRelations` as a parameter.

At line 75 (`createInitialTopology`): pass `List.of()`.
At lines 118, 164, 227, 268, 331: pass `current.spatialRelations()` or
`topology.spatialRelations()` as appropriate.

After this phase: `mvn compile` must succeed.

`addSpatialRelationWithResult` MUST reject DEVICE/ENDPOINT LOCATED_IN relations that disagree
with current compatibility fields or secondary indices. Standalone relocation is out of scope.


---

### Phase 4 — Extend TopologyChanged and emit()

**TopologyChanged.java** — add room/zone fields:

```java
public record TopologyChanged(
    UUID eventId,
    Instant timestamp,
    String habitatId,
    String fromVersion,
    String toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds,
    List<String> affectedRoomIds,     // ← NEW
    List<String> affectedZoneIds,     // ← NEW
    String reason
) {
    public TopologyChanged {
        // existing requireNonNull + copyOf ...
        affectedRoomIds = List.copyOf(
            Objects.requireNonNull(affectedRoomIds, "affectedRoomIds is required"));
        affectedZoneIds = List.copyOf(
            Objects.requireNonNull(affectedZoneIds, "affectedZoneIds is required"));
    }
}
```

**Update BaseTopologyService.emit()** to add the new parameters:

Current signature (existing — do not delete):

```java
private void emit(
    HabitatBaseTopology from, HabitatBaseTopology to,
    Set<TopologyChangeKind> changeKinds,
    List<String> deviceIds, List<String> endpointIds,
    String reason
)
```

Add overload for room/zone events:

```java
private void emit(
    HabitatBaseTopology from, HabitatBaseTopology to,
    Set<TopologyChangeKind> changeKinds,
    List<String> roomIds, List<String> zoneIds,
    List<String> deviceIds, List<String> endpointIds,
    String reason
)
```

The existing `emit()` overload forwards to the new one with `List.of(), List.of()`:

```java
private void emit(..., List<String> deviceIds, List<String> endpointIds, String reason) {
    emit(from, to, changeKinds, List.of(), List.of(), deviceIds, endpointIds, reason);
}
```

**TopologyMutationResult** — extend additively with `affectedRoomIds` and
`affectedZoneIds`. Add a static factory or constructor overload so existing
call sites that create results with only deviceIds/endpointIds continue to compile.

After this phase: `mvn compile` must succeed. All 28 existing tests must pass.
Run `mvn test` to verify before proceeding.

---

### Phase 5 — Add BaseTopologyService mutation methods

Add three new public methods to `BaseTopologyService`. Follow the exact pattern
of `addDeviceWithResult` (see context.md §3).

**addRoomWithResult:**

```java
public TopologyMutationResult addRoomWithResult(String habitatId, RoomNode room) {
    Objects.requireNonNull(room, "room is required");
    HabitatBaseTopology current = repository.findByHabitatId(habitatId)
        .orElseThrow(() -> new IllegalArgumentException("..."));
    if (current.rooms().stream().anyMatch(r -> r.roomId().equals(room.roomId()))) {
        throw new IllegalArgumentException("roomId already exists: " + room.roomId());
    }
    List<RoomNode> rooms = new ArrayList<>(current.rooms());
    rooms.add(room);
    HabitatBaseTopology mutated = withVersionAndMetadata(new HabitatBaseTopology(
        current.habitatId(), current.topologyVersion(),
        rooms, current.zones(), current.devices(), current.endpoints(),
        current.spatialRelations(), current.metadata()
    ));
    validateTopology(mutated);
    repository.save(mutated);
    emit(mutated, mutated, Set.of(TopologyChangeKind.ROOM_ADDED),
         List.of(room.roomId()), List.of(), List.of(), List.of(), "room added");
    // Note: emit uses from=current (before version) and to=mutated (after version)
    return new TopologyMutationResult(..., Set.of(ROOM_ADDED),
        List.of(), List.of(), List.of(room.roomId()), List.of());
}
```

**addZoneWithResult:**

Validate that `zone.roomId()` exists in `current.rooms()`. If not:
throw `IllegalArgumentException("zone roomId must refer to an existing RoomNode")`.

Update the matching room to append `zone.zoneId()` to `RoomNode.zoneIds`
(secondary index maintenance — same pattern as device→room secondary index update).

**addSpatialRelationWithResult:**

Validate subject entity exists in topology (DEVICE → devices, ENDPOINT → endpoints,
ZONE → zones). Validate target entity exists (ROOM → rooms, ZONE → zones).
If either missing: throw with "LOCATED_IN relation subject/target does not exist".

Validate no conflicting primary relation for same kind+subject+targetEntityType.

Mutated topology: `spatialRelations = current.spatialRelations() + [new relation]`.

Do NOT update `RoomNode.deviceIds` or `ZoneNode.deviceIds` here. Those secondary
indices are maintained by `addDeviceWithResult` / `addEndpointWithResult` at device
creation time. The relation is additive canonical state only.

**Extend validateTopology** to check spatial relations:
- Each relation's subject entity exists
- Each relation's target entity exists
- No two primary relations of same kind+subject+targetEntityType

---

### Phase 6 — Add fact types and extend materialization

**Create fact types:**

`RoomDiscoveryFact.java` — implements `TopologyFact`:

```java
public record RoomDiscoveryFact(
    UUID factId, String adapterInstanceId, String providerId,
    String roomNameHint, RoomTraits traitsHint,
    Instant observedAt, double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact { ... }
```

`ZoneDiscoveryFact.java` — implements `TopologyFact`:

```java
public record ZoneDiscoveryFact(
    UUID factId, String adapterInstanceId, String providerId,
    String targetRoomId,   // ← canonical roomId (not a provider ref)
    String zoneNameHint, ZoneTraits traitsHint,
    Instant observedAt, double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact { ... }
```

**Update TopologyFact sealed interface** — add permits:

```java
public sealed interface TopologyFact permits
    DeviceDiscoveryFact, EndpointDiscoveryFact, CapabilityDiscoveryFact,
    DeviceStateFact, HealthFact,
    RoomDiscoveryFact, ZoneDiscoveryFact   // ← new
```

**Update DefaultTopologyMaterializationService.materialize():**

Add cases for `RoomDiscoveryFact` and `ZoneDiscoveryFact` in the switch/dispatch:

```java
// RoomDiscoveryFact path:
case RoomDiscoveryFact roomFact -> materializeRoom(habitatId, roomFact);

// ZoneDiscoveryFact path:
case ZoneDiscoveryFact zoneFact -> materializeZone(habitatId, zoneFact);
```

**materializeRoom:** calls `baseTopologyService.addRoomWithResult(habitatId, roomNode)`.
Generates `roomId` deterministically from fact fields.
Returns `ACCEPT_STRUCTURAL_MUTATION` on success, `REJECT_DUPLICATE` for duplicates.

**materializeZone:** calls `baseTopologyService.addZoneWithResult(habitatId, zoneNode)`.
If `fact.targetRoomId()` does not exist in topology: returns `REJECT_INVALID_FACT`.

**DEC-013-009 — extend materializeDevice to create LOCATED_IN relations:**

After `addDeviceWithResult` returns success:

```java
// Create LOCATED_IN (DEVICE → ROOM) relation
TopologySpatialRelation roomRelation = buildLocatedIn(deviceId, DEVICE, roomId, ROOM, fact);
TopologyMutationResult roomRelationResult =
    baseTopologyService.addSpatialRelationWithResult(habitatId, roomRelation);

// Create LOCATED_IN (DEVICE → ZONE) relation when a zone placement exists
TopologySpatialRelation zoneRelation = buildLocatedIn(deviceId, DEVICE, zoneId, ZONE, fact);
TopologyMutationResult zoneRelationResult =
    baseTopologyService.addSpatialRelationWithResult(habitatId, zoneRelation);
```

Similarly extend `materializeEndpoint` to create LOCATED_IN relations after
`addEndpointWithResult` succeeds.

`buildLocatedIn(...)` MUST use only enum constants that exist in Phase 1:

```java
RelationConfidence.PROVIDER_REPORTED  // selected from provider roomHint/zoneHint
RelationConfidence.INFERRED           // selected by fallback/default placement
SpatialRelationSource.PROVIDER        // selected from provider hint
SpatialRelationSource.SYSTEM          // deterministic seed default
SpatialRelationSource.INFERENCE       // inferred placement
```

Do not use non-existent constants such as `PROVIDER_HINT` or `MATERIALIZATION`.

**Decision result rule — critical:** if device/endpoint materialization performs multiple
structural writes (`addDeviceWithResult` or `addEndpointWithResult` followed by one or more
`addSpatialRelationWithResult` calls), the returned `MaterializationDecision` MUST reflect
the final resulting topologyVersion and the full event delta from all structural writes.
The final exposed topology MUST NOT contain a newly materialized device/endpoint without
the corresponding LOCATED_IN relation.

**Materializer must not import H2BaseTopologyRepository.** Verify this is unchanged.

---

### Phase 7 — Add query methods

**CoreSnapshotReadPort.java** — add method signatures:

```java
Optional<RoomNode> findRoom(String habitatId, String roomId);
Optional<ZoneNode> findZone(String habitatId, String zoneId);
Optional<TopologySpatialRelation> findSpatialRelation(String habitatId, String relationId);
List<TopologySpatialRelation> findSpatialRelationsBySubject(
    String habitatId, TopologySpatialEntityType type, String id);
List<DeviceNode> findLocatedDevices(String habitatId, String roomOrZoneId);
List<EndpointNode> findLocatedEndpoints(String habitatId, String roomOrZoneId);
Optional<TopologySpatialRelation> resolvePrimaryPlacement(
    String habitatId, TopologySpatialEntityType type, String id);
```

**H2BaseTopologyRepository.java** — implement each by reading from deserialized
snapshot. No new H2 table. Pattern (see context.md §6.2):

```java
@Override
public Optional<RoomNode> findRoom(String habitatId, String roomId) {
    return findByHabitatId(habitatId)
        .flatMap(t -> t.rooms().stream().filter(r -> r.roomId().equals(roomId)).findFirst());
}
```

`findLocatedDevices(roomOrZoneId)`: return devices where
`topology.spatialRelations()` has a primary LOCATED_IN with target.id equal to
roomOrZoneId, OR where `device.roomId()` / `device.zoneId()` equals roomOrZoneId
(compatibility fallback for devices with no relation yet).

**CoreSnapshotQueryService.java** — expose wrapper methods if tests require
service-level access.

---

### Phase 8 — Tests

Create `RoomZoneTopologySeedTest.java` covering:

```text
1. roomDiscoveryFactMaterializesRoomNodeAndAdvancesTopologyVersion
2. duplicateRoomDiscoveryDoesNotAdvanceTopologyVersion
3. zoneDiscoveryFactMaterializesZoneNodeInExistingRoom
4. zoneDiscoveryWithUnknownRoomRejectedWithoutVersionAdvance
5. deviceMaterializationCreatesLocatedInRelationsForChosenRoomAndZone
6. endpointMaterializationCreatesLocatedInRelationConsistentWithRoomZone
7. resolvePrimaryPlacementReturnsExpectedRelation
8. findLocatedDevicesReturnsDeviceInRoom
9. findLocatedEndpointsReturnsEndpointInZone
10. spatialRelationsPersistAcrossRepositoryRecreation (recovery test)
11. relationGraphAndCompatibilityFieldsRemainConsistent
12. missingRelationTargetRejectedNoSilentOrphan
13. duplicateSpatialRelationRejectedWithoutVersionAdvance
14. stateAndHealthPersistenceFromMU011Unchanged
15. materializerHasNoH2FieldOrConstructorOrImport (boundary assertion)
16. structuralSaveRemainsHealthStateFree (MU-011 invariant)
17. topologyChangedForRoomAdditionCarriesAffectedRoomId
18. topologyChangedForSpatialAssignmentCarriesAffectedIds
```

All 28 existing tests must continue to pass.

---

## 3. Critical non-goals

Do not implement:

```text
AFFECTS, OBSERVES, CONTROLS, ILLUMINATES, CONDITIONS, PROTECTS relations
QUARANTINE_PENDING_REVIEW with persistence
Room/zone removal or cascade
Unplaced device/endpoint support
Production H2 schema for relations (no new table)
Flyway/Liquibase migrations
TemporalActs, terminal request state, outbox/ledger
SC-B runtime, NATS, JetStream, Vert.x
Real SC-D adapter implementation
Projection, Session, Identity, Authority, Policy, Hub, Surface logic
```

---

## 4. Files to create or modify

**New files:**

```text
src/main/java/.../model/TopologySpatialRelation.java
src/main/java/.../model/TopologySpatialRelationKind.java
src/main/java/.../model/TopologySpatialSubject.java
src/main/java/.../model/TopologySpatialTarget.java
src/main/java/.../model/TopologySpatialEntityType.java
src/main/java/.../model/RelationConfidence.java
src/main/java/.../model/SpatialRelationSource.java
src/main/java/.../model/ProviderSpatialRef.java
src/main/java/.../materialization/RoomDiscoveryFact.java
src/main/java/.../materialization/ZoneDiscoveryFact.java
src/test/java/.../RoomZoneTopologySeedTest.java
```

**Modified files:**

```text
src/main/java/.../model/HabitatBaseTopology.java        (+spatialRelations, Jackson compat)
src/main/java/.../event/TopologyChanged.java             (+affectedRoomIds, +affectedZoneIds)
src/main/java/.../event/TopologyChangeKind.java          (+ROOM_ADDED, +ZONE_ADDED)
src/main/java/.../materialization/MaterializationDecisionKind.java (+REJECT_AMBIGUOUS_SPATIAL_BINDING)
src/main/java/.../materialization/TopologyFact.java      (+RoomDiscoveryFact, +ZoneDiscoveryFact permits)
src/main/java/.../materialization/DefaultTopologyMaterializationService.java
src/main/java/.../service/BaseTopologyService.java       (+addRoomWithResult, +addZoneWithResult,
                                                          +addSpatialRelationWithResult,
                                                          +validateTopology extension,
                                                          +6 HabitatBaseTopology construction sites,
                                                          +emit() overload)
src/main/java/.../model/TopologyMutationResult.java      (+affectedRoomIds, +affectedZoneIds)
src/main/java/.../port/CoreSnapshotReadPort.java          (+7 query methods)
src/main/java/.../adapter/persistence/H2BaseTopologyRepository.java (+7 query methods)
src/main/java/.../query/CoreSnapshotQueryService.java    (if wrapper methods needed)
src/test/java/.../PersistenceMemorySeedTest.java         (boundary assertion extension)
```

---

## 5. Critical ACs

From `docs/mir/mir-013/acceptance-map.md`, the highest-risk ACs:

```text
AC-002: HabitatBaseTopology has spatialRelations. Old snapshots deserialize correctly.
AC-005: TopologySpatialRelation and LOCATED_IN exist.
AC-006: Only LOCATED_IN is implemented; reserved kinds are enum constants only.
AC-009/010: RoomDiscoveryFact and ZoneDiscoveryFact materialize correctly.
AC-012: DeviceDiscoveryFact creates LOCATED_IN relation(s) as persistent output.
AC-022: topologyVersion advances on room/zone/relation structural mutations.
AC-023: Duplicate/rejected facts do not advance topologyVersion.
AC-026: Relations persist and survive repository recreation.
AC-031: H2BaseTopologyRepository.save() structural-only — no health/state side effects.
AC-032: DefaultTopologyMaterializationService has no H2BaseTopologyRepository dependency.
AC-034: All 28 existing tests still pass.
```

---

## 6. Final commands

```bash
mvn test
```

Then fill `docs/mir/mir-013/implementation-report.md` with:

```text
- PASS / FAIL / BLOCKED
- branch and commit hash
- test command and result
- total tests / failures / errors / skipped
- production files changed
- test files changed
- final TopologySpatialRelation shape
- confirmation Jackson backward compatibility for old snapshots
- confirmation MU-011 persistence separation preserved
- confirmation MU-012 materializer port boundary preserved
- AC-001 through AC-035 result table
- remaining deferred debt
```

---

## 7. Stop conditions

Stop and report `BLOCKED` if:

```text
- MaterializationDecision cannot reflect final topologyVersion after all relation writes
- Adding spatialRelations requires more than context.md §2.2 describes
- Old topology_json cannot be deserialized with the null→List.of() fix
- A new H2 table becomes necessary
- Room/zone removal or unplaced device support becomes necessary
- Semantic relations beyond LOCATED_IN become necessary
- DefaultTopologyMaterializationService needs H2BaseTopologyRepository
- H2BaseTopologyRepository.save() gains health/state side effects
- Projection, Session, Identity, Authority, Policy, Hub, Surface needed
- NATS, JetStream, SC-B runtime, or real SC-D adapter needed
```
