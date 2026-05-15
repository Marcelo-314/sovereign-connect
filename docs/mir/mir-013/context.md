# Context — MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001

```text
Artifact: docs/mir/mir-013/context.md
Version: v0.1.2
Status: Execution-ready / patched
MIR: MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 v0.1.0-draft
CSA: CSA-MU-013 v0.1.1-merged
Target repository: sovereign-connect
Expected branch: feat/sc-c-room-zone-topology-seed
```

---

## 0. Objective

Implement MU-013: first-class `TopologySpatialRelation(LOCATED_IN)` seed.

The seed materializes and queries `RoomNode`, `ZoneNode` and
`TopologySpatialRelation(LOCATED_IN)` while preserving:
- all 28 existing tests passing
- MU-011 persistence separation
- MU-012 materialization port boundary

---

## 1. Confirmed decisions

```text
DEC-013-001: Add spatialRelations to HabitatBaseTopology.
DEC-013-002: Add REJECT_AMBIGUOUS_SPATIAL_BINDING; reserve QUARANTINE_PENDING_REVIEW.
DEC-013-003: Extend TopologyChanged additively with affectedRoomIds / affectedZoneIds.
DEC-013-004: Add ROOM_ADDED and ZONE_ADDED to TopologyChangeKind.
DEC-013-005: Room/zone removal out of scope; orphan relation targets are rejected.
DEC-013-006: All structural mutations go through BaseTopologyService.
DEC-013-007: Final persistence schema/migrations deferred; topology_json CLOB only.
DEC-013-008: No unplaced device/endpoint support in MU-013.
DEC-013-009: DeviceDiscoveryFact roomHint/zoneHint → input evidence;
             LOCATED_IN relation → persistent canonical output.
```

---

## 2. Current types that must be modified — exact code

### 2.1 HabitatBaseTopology — current shape (must add spatialRelations)

Current record:

```java
public record HabitatBaseTopology(
    String habitatId,
    TopologyVersion topologyVersion,
    List<RoomNode> rooms,
    List<ZoneNode> zones,
    List<DeviceNode> devices,
    List<EndpointNode> endpoints,
    TopologyMetadata metadata    // ← currently the last field
)
```

After MU-013:

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
)
```

**Jackson backward compatibility rule — critical:**
Old `topology_json` snapshots do not have a `spatialRelations` field. When
Jackson deserializes them, it will pass `null` for `spatialRelations`. The compact
constructor MUST NOT use `Objects.requireNonNull` for this field. Instead:

```java
// In compact constructor:
spatialRelations = List.copyOf(spatialRelations != null ? spatialRelations : List.of());
```

This differs from all other fields which use `requireNonNull`. The null-to-empty
conversion is the only Jackson backward compatibility hook needed.

### 2.2 HabitatBaseTopology construction sites — all 6 must be updated

All 6 construction sites are in `BaseTopologyService.java`:

| Line | Context | Action |
|-----:|---------|--------|
| 75 | `createInitialTopology` — initial creation | Pass `List.of()` as `spatialRelations` |
| 118 | `addEndpoint` internal helper | Pass `current.spatialRelations()` |
| 164 | `addDeviceWithResult` — `withVersionAndMetadata` | Pass `current.spatialRelations()` |
| 227 | `addEndpointWithResult` — `withVersionAndMetadata` | Pass `current.spatialRelations()` |
| 268 | `updateEndpointHealth` — `repository.save(...)` | Pass `current.spatialRelations()` |
| 331 | `withVersionAndMetadata` private method | Pass `topology.spatialRelations()` |

**Rule:** any new method that creates a `HabitatBaseTopology` with relation changes
must build on `current.spatialRelations()` and add/remove from that list.

### 2.3 TopologyChanged — current shape (must add room/zone fields)

Current record:

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
    String reason
)
```

After MU-013:

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
    List<String> affectedRoomIds,       // ← NEW
    List<String> affectedZoneIds,       // ← NEW
    String reason
)
```

**Backward compatibility:** Add a convenience factory or constructor overload
so existing call sites that don't pass room/zone IDs continue to compile
by forwarding to `List.of()`:

```java
// Compatibility factory for existing sites:
public static TopologyChanged forDevicesEndpoints(
    UUID eventId, Instant timestamp, String habitatId,
    String fromVersion, String toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds, List<String> affectedEndpointIds,
    String reason
) {
    return new TopologyChanged(eventId, timestamp, habitatId, fromVersion, toVersion,
        changeKinds, affectedDeviceIds, affectedEndpointIds, List.of(), List.of(), reason);
}
```

**Current construction sites of TopologyChanged** (in `BaseTopologyService.emit()`):

```java
private void emit(
    HabitatBaseTopology from,
    HabitatBaseTopology to,
    Set<TopologyChangeKind> changeKinds,
    List<String> deviceIds,
    List<String> endpointIds,
    String reason
) {
    emittedEvents.add(new TopologyChanged(
        UUID.randomUUID(), Instant.now(clock), to.habitatId(),
        from.topologyVersion().value(), to.topologyVersion().value(),
        changeKinds, deviceIds, endpointIds, reason
    ));
}
```

This `emit()` signature must be kept or overloaded. Add a parallel `emit()` overload
for room/zone events:

```java
private void emit(
    HabitatBaseTopology from, HabitatBaseTopology to,
    Set<TopologyChangeKind> changeKinds,
    List<String> roomIds, List<String> zoneIds,
    List<String> deviceIds, List<String> endpointIds,
    String reason
)
```

### 2.4 TopologyChangeKind — current values (must add room/zone)

```java
public enum TopologyChangeKind {
    DEVICE_ADDED,
    DEVICE_REMOVED,
    ENDPOINT_ADDED,
    ENDPOINT_REMOVED,
    ENDPOINT_CHANGED,
    CAPABILITY_ADDED,
    CAPABILITY_REMOVED,
    TRAITS_CHANGED,
    SPATIAL_ASSIGNMENT_CHANGED    // ← already exists
}
```

Add:

```java
ROOM_ADDED,    // ← new
ZONE_ADDED,    // ← new
```

`SPATIAL_ASSIGNMENT_CHANGED` is already present and must be used when a
`LOCATED_IN` relation is created or changed.

### 2.5 TopologyMutationResult — current shape (extend if room/zone helpers use it)

```java
public record TopologyMutationResult(
    String habitatId,
    TopologyVersion fromVersion,
    TopologyVersion toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds
)
```

If `addRoomWithResult` and `addZoneWithResult` return `TopologyMutationResult`,
extend it additively:

```java
List<String> affectedRoomIds,   // ← new
List<String> affectedZoneIds,   // ← new
```

Add a factory method for backward compatibility with existing call sites.

### 2.6 TopologyFact sealed interface — current permits (must add new facts)

```java
public sealed interface TopologyFact permits
    DeviceDiscoveryFact,
    EndpointDiscoveryFact,
    CapabilityDiscoveryFact,
    DeviceStateFact,
    HealthFact
```

Add to permits:

```java
    RoomDiscoveryFact,
    ZoneDiscoveryFact,
    SpatialAssignmentFact       // optional, depending on MIR scope decision
```

### 2.7 MaterializationDecisionKind — add REJECT_AMBIGUOUS_SPATIAL_BINDING

```java
// Current:
ACCEPT_STRUCTURAL_MUTATION, ACCEPT_NON_STRUCTURAL_STATE, ACCEPT_HEALTH_UPDATE,
REJECT_DUPLICATE, REJECT_INVALID_FACT, REJECT_UNAUTHORIZED_ADAPTER, NOOP

// Add:
REJECT_AMBIGUOUS_SPATIAL_BINDING   // for conflicting/ambiguous provider spatial metadata
```

---

## 3. BaseTopologyService — model for new methods

### 3.1 Pattern to follow: addDeviceWithResult

New room/zone/relation service methods follow the same pattern. The current pattern is:

```java
public TopologyMutationResult addDeviceWithResult(String habitatId, DeviceNode device) {
    Objects.requireNonNull(device, "device is required");
    HabitatBaseTopology current = repository.findByHabitatId(habitatId)
        .orElseThrow(() -> new IllegalArgumentException("base topology does not exist for habitatId " + habitatId));

    // 1. Reject duplicate
    if (current.devices().stream().anyMatch(existing -> existing.deviceId().equals(device.deviceId()))) {
        throw new IllegalArgumentException("deviceId already exists in habitat topology: " + device.deviceId());
    }

    // 2. Update secondary indices in rooms/zones
    List<RoomNode> rooms = current.rooms().stream()
        .map(room -> room.roomId().equals(device.roomId()) ? appendDevice(room, device.deviceId()) : room)
        .toList();
    List<ZoneNode> zones = current.zones().stream()
        .map(zone -> zone.zoneId().equals(device.zoneId()) ? appendDevice(zone, device.deviceId()) : zone)
        .toList();

    // 3. Add device to list
    List<DeviceNode> devices = new ArrayList<>(current.devices());
    devices.add(device);

    // 4. Build mutated topology with advanced version
    HabitatBaseTopology mutated = withVersionAndMetadata(new HabitatBaseTopology(
        current.habitatId(), current.topologyVersion(),
        rooms, zones, devices, current.endpoints(),
        current.spatialRelations(),   // ← preserve existing relations
        current.metadata()
    ));
    validateTopology(mutated);
    repository.save(mutated);

    // 5. Emit event and return result
    emit(current, mutated, Set.of(DEVICE_ADDED), List.of(device.deviceId()), List.of(), "device added");
    return new TopologyMutationResult(...);
}
```

### 3.2 addRoomWithResult — derived pattern

```java
public TopologyMutationResult addRoomWithResult(String habitatId, RoomNode room) {
    // 1. Find current topology
    // 2. Reject if roomId already exists in topology.rooms()
    // 3. Build mutated topology:
    //    - rooms = current.rooms() + new room
    //    - zones, devices, endpoints, spatialRelations = current.X() (unchanged)
    // 4. withVersionAndMetadata → advances topologyVersion
    // 5. repository.save(mutated)
    // 6. emit with ROOM_ADDED, affectedRoomIds=[room.roomId()]
    // 7. return TopologyMutationResult with ROOM_ADDED, affectedRoomIds=[room.roomId()]
}
```

Note: rooms have empty secondary indices when created
(`zoneIds=[]`, `deviceIds=[]`, `endpointIds=[]`).

### 3.3 addZoneWithResult — derived pattern

```java
public TopologyMutationResult addZoneWithResult(String habitatId, ZoneNode zone) {
    // 1. Find current topology
    // 2. Reject if zone.roomId() does not exist in topology.rooms()
    //    → "zone roomId must refer to an existing RoomNode"
    // 3. Reject if zoneId already exists in topology.zones()
    // 4. Update secondary index: update the matching room to append zone.zoneId() to RoomNode.zoneIds
    // 5. Build mutated topology:
    //    - rooms = updated rooms (with new zoneId in matching room)
    //    - zones = current.zones() + new zone
    //    - devices, endpoints, spatialRelations = current.X() (unchanged)
    // 6. withVersionAndMetadata → advances topologyVersion
    // 7. repository.save(mutated)
    // 8. emit with ZONE_ADDED, affectedZoneIds=[zone.zoneId()]
    // 9. return TopologyMutationResult with ZONE_ADDED, affectedZoneIds=[zone.zoneId()]
}
```

### 3.4 addSpatialRelationWithResult — derived pattern

```java
public TopologyMutationResult addSpatialRelationWithResult(
    String habitatId, TopologySpatialRelation relation
) {
    // 1. Find current topology
    // 2. Validate subject exists (DEVICE → devices, ENDPOINT → endpoints, ZONE → zones)
    // 3. Validate target exists (ROOM → rooms, ZONE → zones)
    //    → reject orphan target: "LOCATED_IN relation target does not exist"
    // 4. Validate no conflicting primary relation for same subject+kind+targetEntityType
    //    → if primary=true and one already exists, reject or update primary flag
    // 5. Build mutated topology:
    //    - spatialRelations = current.spatialRelations() + new relation
    //    - all other fields = current.X() (unchanged)
    //    NOTE: do NOT update secondary indices here (already maintained by addDeviceWithResult etc.)
    // 6. withVersionAndMetadata → advances topologyVersion
    // 7. repository.save(mutated)
    // 8. emit with SPATIAL_ASSIGNMENT_CHANGED, affected subject and target IDs
    // 9. return TopologyMutationResult
}
```

**Key rule:** `addSpatialRelationWithResult` does NOT maintain `RoomNode.deviceIds`
or `ZoneNode.deviceIds` secondary indices. Those are already maintained by
`addDeviceWithResult` and `addEndpointWithResult` at device/endpoint creation time.
The relation is additive canonical state only.

**Consistency guard:** because MU-013 does not implement standalone relocation,
`addSpatialRelationWithResult` MUST reject a DEVICE/ENDPOINT LOCATED_IN relation that
disagrees with the subject node's current `roomId`/`zoneId` compatibility fields or with
secondary indices. Standalone spatial reassignment remains out of scope unless a later MIR
extends the service to update relation graph + compatibility fields + secondary indices in
one accepted structural mutation.

### 3.5 validateTopology must be extended

Current `validateTopology` checks:
- unique roomId, zoneId, deviceId, endpointId
- room.zoneIds → valid zoneId references
- room.deviceIds → valid deviceId references
- room.endpointIds → valid endpointId references
- zone.roomId → valid roomId reference
- zone.deviceIds → valid deviceId references

After MU-013, extend to check:
- for each `TopologySpatialRelation`:
  - subject entity type+id must exist in the topology
  - target entity type+id must exist in the topology
  - no two primary relations of the same kind+subject+targetEntityType

---

## 4. Materialization flow — existing device path + relation output

### 4.1 Current materializeDevice (existing code — read-only reference)

```java
private MaterializationDecision materializeDevice(String habitatId, DeviceDiscoveryFact fact) {
    Optional<HabitatBaseTopology> topology = admittedTopology(habitatId, fact);
    if (topology.isEmpty()) return rejectedByPrecondition(habitatId, fact);

    String deviceId = canonicalDeviceId(fact.providerId(), fact.providerDeviceId());
    if (topology.get().devices().stream().anyMatch(device -> device.deviceId().equals(deviceId))) {
        return rejectDuplicate(habitatId, fact);
    }

    String roomId = chooseRoomId(topology.get(), fact.roomHint());   // ← hint = input evidence
    String zoneId = chooseZoneId(topology.get(), fact.zoneHint());   // ← hint = input evidence
    DeviceNode device = new DeviceNode(deviceId, ..., roomId, zoneId, ...);

    int before = baseTopologyService.emittedEvents().size();
    TopologyMutationResult result = baseTopologyService.addDeviceWithResult(habitatId, device);
    return acceptStructural(habitatId, fact, result, eventDelta(before), "device materialized");
}
```

### 4.2 materializeDevice after MU-013 — add LOCATED_IN relations

After `addDeviceWithResult` returns ACCEPT_STRUCTURAL_MUTATION, create
`TopologySpatialRelation(LOCATED_IN)` relations as canonical persistent output:

```java
// After addDeviceWithResult succeeds:
TopologySpatialRelation roomRelation = new TopologySpatialRelation(
    canonicalRelationId(habitatId, deviceId, "LOCATED_IN", roomId),  // deterministic seed ID
    TopologySpatialRelationKind.LOCATED_IN,
    new TopologySpatialSubject(TopologySpatialEntityType.DEVICE, deviceId),
    new TopologySpatialTarget(TopologySpatialEntityType.ROOM, roomId),
    true,   // primary
    relationConfidenceFor(fact, roomId),   // PROVIDER_REPORTED if selected from provider hint; INFERRED if fallback
    relationSourceFor(fact, roomId),       // PROVIDER if selected from provider hint; SYSTEM/INFERENCE if fallback
    null,   // no providerRef for device→room in the seed
    fact.observedAt(),
    Map.of()
);
baseTopologyService.addSpatialRelationWithResult(habitatId, roomRelation);
// similarly for DEVICE → ZONE if zoneId is different from "any zone in that room"
```

Allowed enum values are the ones defined in §1 / Phase 1: use
`RelationConfidence.PROVIDER_REPORTED` for accepted provider hints, `INFERRED` for fallback
selection, and `UNKNOWN` only when neither evidence nor inference quality is known. Use
`SpatialRelationSource.PROVIDER` for provider hints, `SYSTEM` for deterministic seed defaults,
and `INFERENCE` for inferred placement. Do not introduce `PROVIDER_HINT` or `MATERIALIZATION`
unless the enum is explicitly expanded.

**Ordering invariant (DEC-013-009):**
roomHint/zoneHint are input evidence. The LOCATED_IN relation is the persistent
canonical output. Both must be created in one logical materialization sequence:
1. `addDeviceWithResult` (creates DeviceNode, maintains secondary indices, advances version)
2. `addSpatialRelationWithResult` (creates LOCATED_IN, advances version again)

Each is a separate structural mutation with a separate topologyVersion advance in the seed path.

**Decision result rule — critical:** if the implementation uses this sequential seed path,
`MaterializationDecision.resultingTopologyVersion` and `emittedChanges` MUST reflect the
final topology after all LOCATED_IN relation writes, not only the first `addDeviceWithResult`
mutation. The final exposed topology MUST NOT leave a device/endpoint with compatibility
`roomId`/`zoneId` but without the corresponding LOCATED_IN relation.

This is accepted as seed-level sequencing only. Production atomicity for compound structural
mutations remains deferred to transaction semantics / persistence SDD work.

### 4.3 chooseRoomId / chooseZoneId — do not change these methods

These methods already exist and are correct. Do not modify their behavior.
They are the bridge from hint → chosen canonical ID.

```java
private String chooseRoomId(HabitatBaseTopology topology, String roomHint) {
    if (roomHint != null && topology.rooms().stream().anyMatch(r -> r.roomId().equals(roomHint))) {
        return roomHint;
    }
    return topology.rooms().stream().findFirst().map(RoomNode::roomId)
        .orElseThrow(() -> new IllegalArgumentException("habitat must contain at least one room"));
}
```

---

## 5. New fact types

### 5.1 RoomDiscoveryFact

Minimum required fields:

```java
public record RoomDiscoveryFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,              // optional, nullable
    String roomNameHint,            // name hint for the room
    RoomTraits traitsHint,          // may default to new RoomTraits(false, false)
    Instant observedAt,
    double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact
```

Note: `RoomDiscoveryFact` produces a `RoomNode` via `addRoomWithResult`.
SC-C assigns the canonical `roomId` — it is not provided by the fact.

### 5.2 ZoneDiscoveryFact

```java
public record ZoneDiscoveryFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,              // optional, nullable
    String targetRoomId,            // canonical roomId for Zone LOCATED_IN Room
    String zoneNameHint,
    ZoneTraits traitsHint,          // may default to new ZoneTraits(false)
    Instant observedAt,
    double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact
```

`targetRoomId` must reference an existing canonical `roomId`. If it does not exist,
materialize as `REJECT_INVALID_FACT`.

### 5.3 Canonical ID generation for rooms, zones, relations

Follow the same deterministic pattern used for devices/endpoints:

```java
// Room: deterministic from provider-derived name hint + adapter
"room." + providerId + "." + slugify(roomNameHint)

// Zone: deterministic from providerId + roomId + name hint
"zone." + providerId + "." + canonicalRoomId + "." + slugify(zoneNameHint)

// Relation: deterministic from subject + kind + target
"relation." + subjectId + ".LOCATED_IN." + targetId
```

If `providerId` is null, use a system prefix: `"room.system." + ...`.

---

## 6. Query additions

### 6.1 CoreSnapshotReadPort — new methods

Add to `CoreSnapshotReadPort`:

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

### 6.2 H2 implementation strategy

All methods read from the deserialized `HabitatBaseTopology` snapshot.
No new H2 table is introduced. The H2 pattern is:

```java
@Override
public Optional<RoomNode> findRoom(String habitatId, String roomId) {
    return findByHabitatId(habitatId)
        .flatMap(topology -> topology.rooms().stream()
            .filter(r -> r.roomId().equals(roomId))
            .findFirst());
}
```

Similarly for `findZone` (from `topology.zones()`),
`findSpatialRelation` (from `topology.spatialRelations()`), etc.

`findLocatedDevices` for a roomId: find all devices where primary LOCATED_IN
target is that room (via `topology.spatialRelations()`) or where
`device.roomId().equals(roomId)` (compatibility field fallback for devices
created before relations existed).

---

## 7. Critical invariants from MU-011 and MU-012

Do not break:

```text
MU-011: H2BaseTopologyRepository.save(HabitatBaseTopology) is structural-only.
        Adding spatialRelations to HabitatBaseTopology is structural — correct.
        No health/state side effects may be reintroduced.

MU-012: DefaultTopologyMaterializationService has no import, field or constructor
        parameter typed as H2BaseTopologyRepository.
        All materializer structural mutations go through BaseTopologyService.
        All materialization state reads go through TopologyMaterializationStatePort.
```

PersistenceMemorySeedTest boundary assertions must still pass after MU-013.

---

## 8. Stop conditions

Stop and report `BLOCKED` if:

```text
- MaterializationDecision cannot reflect final topologyVersion after all relation writes
- Adding spatialRelations requires more than 6 construction-site updates +
  ObjectMapper backward compat fix (broader rewrites indicate wrong approach)
- Old topology_json cannot be deserialized gracefully with the null→List.of() fix
- A new H2 table is required for relations
- Room/zone removal or unplaced device support becomes necessary
- Semantic relations beyond LOCATED_IN are needed
- DefaultTopologyMaterializationService needs H2BaseTopologyRepository import
- H2BaseTopologyRepository.save() gains health/state side effects
- Projection, Session, Identity, Authority, Policy, Hub, Surface needed
- NATS, JetStream, SC-B runtime, real SC-D adapter needed
```
