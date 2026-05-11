# MIR-007 — SC-C Topology Materialization Seed — Implementation Context

```text
MIR: MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001
MIR Version: v1.0.0-accepted
MU: MU-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001
Operational Slot: MU-007
Status: Accepted execution artifact
Date: 2026-05-10
```

---

## 0. Purpose

Read this before executing `docs/mir/mir-007/codex-prompt.md`.

This context defines the exact existing API surface, the critical implementation decisions for MU-007, and the patterns the agent must follow.

All critical decisions must be understood before writing a single line of code.

This context is the Code Surface Audit for MIR-007.

---

## 1. Existing Codebase — Exact API Surface

### 1.1 TopologyChanged record — exact Java

```java
package com.sovereign.connect.core.topology.event;

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
) {}
```

`fromVersion` and `toVersion` are `String`, not `TopologyVersion`.

`TopologyChangeKind` already has:

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

Do NOT create a new `TopologyChanged` variant.

Do NOT change these fields.

### 1.2 TopologyMutationResult record — exact Java

```java
public record TopologyMutationResult(
    String habitatId,
    TopologyVersion fromVersion,
    TopologyVersion toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds
) {}
```

### 1.3 BaseTopologyService — exact relevant methods

```java
public class BaseTopologyService {

    // Accumulates ALL TopologyChanged events since service construction.
    // CRITICAL: does not clear between calls.
    private final List<TopologyChanged> emittedEvents = new ArrayList<>();

    // Creates topology if none exists. Throws if already exists.
    public HabitatBaseTopology createInitialTopology(
        String habitatId,
        List<RoomNode> rooms, List<ZoneNode> zones,
        List<DeviceNode> devices, List<EndpointNode> endpoints
    );

    // Adds endpoint. Throws if habitatId not found. Throws if endpointId already exists.
    // Throws if deviceId, roomId, zoneId do not already exist in topology.
    public HabitatBaseTopology addEndpoint(String habitatId, EndpointNode endpoint);

    // Returns TopologyMutationResult with fromVersion/toVersion.
    // Calls addEndpoint internally, so same throw conditions apply.
    public TopologyMutationResult addEndpointWithResult(String habitatId, EndpointNode endpoint);

    // Returns a snapshot of ALL events emitted since service was created.
    // Does NOT clear after reading. Grows with each structural mutation.
    public List<TopologyChanged> emittedEvents();

    // Other existing methods:
    // updateEndpointHealth
    // updateDeviceState (ConcurrentMap, not durable)
    // validateTarget
    // findCurrentVersion
}
```

### 1.4 DeviceNode record — exact fields

```java
public record DeviceNode(
    String deviceId,
    String alias,
    String displayName,
    String roomId,
    String zoneId,
    DeviceKind kind,
    DeviceProvider provider,
    List<String> endpointIds,
    List<CapabilityNode> deviceCapabilities,
    DeviceTraits traits,
    DeviceHealth health,
    ProviderDeviceRef providerRef
) {}
```

`roomId` is required and must reference an existing room in topology.

`zoneId` is required and must reference an existing zone in topology.

### 1.5 H2BaseTopologyRepository — durable persistence methods

```java
public class H2BaseTopologyRepository implements BaseTopologyRepository, CoreSnapshotReadPort {

    // Port methods — structural
    void save(HabitatBaseTopology topology);
    Optional<HabitatBaseTopology> findByHabitatId(String habitatId);
    Optional<TopologyVersion> findCurrentVersion(String habitatId);

    // Durable state/health — non-structural
    void saveDeviceState(String habitatId, String deviceId, Map<String, Object> state);
    Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);
    void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health);
    Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);
}
```

### 1.6 Existing test baseline

```text
BaseTopologyServiceTest             — 8 tests  — InMemory
TopologyVersionHardeningTest        — 12 tests — InMemory
PersistenceMemorySeedTest           — 1 test   — H2 durable
CoreSnapshotQuerySeedTest           — 1 test   — H2 durable
ScCoreKernelHardeningTest           — 1 test   — H2 durable

Total:
  23 tests

All must continue passing.
```

---

## 2. Critical Decision 1 — emittedEvents() Delta Collection

`BaseTopologyService.emittedEvents()` accumulates ALL events since construction.

It does NOT clear between calls.

After processing N structural facts, the list has N entries.

The materializer MUST use a delta pattern to associate events with decisions:

```java
int snapshotBefore = baseTopologyService.emittedEvents().size();

TopologyMutationResult result =
    baseTopologyService.addEndpointWithResult(habitatId, endpoint);

List<TopologyChanged> emittedByThisDecision = baseTopologyService
    .emittedEvents()
    .subList(snapshotBefore, baseTopologyService.emittedEvents().size());
```

Place these events in:

```text
MaterializationDecision.emittedChanges
```

This is mandatory.

Without it, each decision accumulates all previous events.

---

## 3. Critical Decision 2 — Duplicate Detection Before Service Call

`BaseTopologyService.addEndpoint(...)` throws `IllegalArgumentException` if the endpoint already exists.

The materializer MUST NOT let duplicate exceptions propagate.

It must detect duplicates BEFORE calling the service and return `REJECT_DUPLICATE`.

Endpoint duplicate detection:

```java
Optional<HabitatBaseTopology> current = repository.findByHabitatId(habitatId);

if (current.isEmpty()) {
    return rejectInvalid(fact, "habitat not initialized");
}

String canonicalEndpointId = deriveEndpointId(fact);

boolean alreadyExists = current.get().endpoints().stream()
    .anyMatch(e -> e.endpointId().equals(canonicalEndpointId));

if (alreadyExists) {
    return rejectDuplicate(fact);
}
```

Device duplicate detection:

```java
String canonicalDeviceId = deriveDeviceId(fact);

boolean alreadyExists = current.get().devices().stream()
    .anyMatch(d -> d.deviceId().equals(canonicalDeviceId));

if (alreadyExists) {
    return rejectDuplicate(fact);
}
```

Repeated same fact:

```text
REJECT_DUPLICATE
no version advance
no event
```

---

## 4. Critical Decision 3 — Habitat Pre-Condition

`BaseTopologyService.addEndpointWithResult(...)` throws `IllegalArgumentException` if the habitat topology does not exist.

The same applies to any structural mutation helper.

The materializer handles missing habitat by returning `REJECT_INVALID_FACT`:

```java
if (repository.findByHabitatId(habitatId).isEmpty()) {
    return MaterializationDecision.reject(
        fact,
        "habitat not initialized",
        MaterializationDecisionKind.REJECT_INVALID_FACT
    );
}
```

Do NOT auto-initialize habitat inside the materializer.

Habitat initialization is outside materialization scope.

---

## 5. Critical Decision 4 — addDeviceWithResult Must Be Added to BaseTopologyService

The existing service has `addEndpointWithResult(...)` but not `addDeviceWithResult(...)`.

The materializer MUST NOT implement device mutation directly.

It must delegate.

Add this method to `BaseTopologyService` following the existing mutation pattern:

```java
public TopologyMutationResult addDeviceWithResult(String habitatId, DeviceNode device) {
    Objects.requireNonNull(device, "device is required");

    HabitatBaseTopology current = repository.findByHabitatId(habitatId)
        .orElseThrow(() -> new IllegalArgumentException(
            "base topology does not exist for habitatId " + habitatId));

    if (current.devices().stream()
            .anyMatch(d -> d.deviceId().equals(device.deviceId()))) {
        throw new IllegalArgumentException(
            "deviceId already exists in habitat topology: " + device.deviceId());
    }

    List<RoomNode> rooms = current.rooms().stream()
        .map(room -> room.roomId().equals(device.roomId())
            ? appendDevice(room, device.deviceId()) : room)
        .toList();

    List<ZoneNode> zones = current.zones().stream()
        .map(zone -> zone.zoneId().equals(device.zoneId())
            ? appendDevice(zone, device.deviceId()) : zone)
        .toList();

    List<DeviceNode> devices = new ArrayList<>(current.devices());
    devices.add(device);

    HabitatBaseTopology mutated = withVersionAndMetadata(new HabitatBaseTopology(
        current.habitatId(),
        current.topologyVersion(),
        rooms,
        zones,
        devices,
        current.endpoints(),
        current.metadata()
    ));

    repository.save(mutated);

    emit(
        current,
        mutated,
        Set.of(TopologyChangeKind.DEVICE_ADDED),
        List.of(device.deviceId()),
        List.of(),
        "device added via materialization"
    );

    TopologyVersion fromVersion = current.topologyVersion();

    return new TopologyMutationResult(
        habitatId,
        fromVersion,
        mutated.topologyVersion(),
        Set.of(TopologyChangeKind.DEVICE_ADDED),
        List.of(device.deviceId()),
        List.of()
    );
}
```

`appendDevice(RoomNode room, String deviceId)` and `appendDevice(ZoneNode zone, String deviceId)` follow the same pattern as existing private `appendEndpoint(...)` helper methods.

`DeviceNode` requires `roomId` and `zoneId`.

For the seed test:

```text
create habitat with at least one room and one zone before materializing facts.
```

The `DeviceDiscoveryFact` may include `roomHint` and `zoneHint`.

If not provided:

```text
default to first room and first zone in the habitat.
```

---

## 6. Critical Decision 5 — State/Health Persistence Path

State facts go to the durable repository, NOT to `BaseTopologyService`.

Correct:

```java
repository.saveDeviceState(habitatId, canonicalDeviceId, fact.statePayload());
```

Wrong:

```java
baseTopologyService.updateDeviceState(habitatId, canonicalDeviceId, state);
```

Reason:

```text
BaseTopologyService.updateDeviceState(...) stores in an in-process ConcurrentMap.
It does NOT survive repository/service recreation.
```

Health facts go to the durable endpoint health path.

Correct:

```java
repository.saveEndpointHealth(
    habitatId,
    canonicalEndpointId,
    new EndpointHealth(fact.healthStatus(), Instant.now(clock), fact.healthReason())
);
```

For MIR-007 seed, `HealthFact` is endpoint-level only.

If `providerEndpointId` is null:

```text
return REJECT_INVALID_FACT
or NOOP with reason "device-level health persistence not available in seed".
```

Do not add device-level health persistence in MIR-007 unless an existing durable repository method already supports it.

State/health calls:

```text
do not advance topologyVersion;
do not emit TopologyChanged;
MaterializationDecision.emittedChanges is empty.
```

---

## 7. Critical Decision 6 — Capability Materialization Must Not Re-Add Endpoint

`addEndpointWithResult(...)` cannot update an existing endpoint.

It calls `addEndpoint(...)`, and `addEndpoint(...)` throws if endpointId already exists.

Therefore:

```text
CapabilityDiscoveryFact MUST NOT update an existing endpoint by calling
addEndpointWithResult(...) with the same endpointId.
```

Capability materialization requires a helper such as:

```java
TopologyMutationResult addCapabilityWithResult(
    String habitatId,
    String endpointId,
    CapabilityNode capability
)
```

The helper must:

```text
find existing endpoint;
reject duplicate capability before mutation;
replace/update the endpoint with the additional capability;
advance topologyVersion;
persist via repository.save(...);
emit existing transitional TopologyChanged with CAPABILITY_ADDED;
return TopologyMutationResult.
```

If this helper cannot be safely implemented, stop and report.

AC-004 requires capability materialization, so the default is to implement the helper.

---

## 8. Deterministic Seed Canonical ID Strategy

```java
String deviceId =
    "device." + providerId + "." + providerDeviceId;

String endpointId =
    "endpoint." + providerId + "." + providerDeviceId + "." + providerEndpointId;

String capabilityId =
    "capability." + providerId + "." + providerDeviceId + "."
    + providerEndpointId + "." + providerCapabilityKey;
```

These must be stable and repeatable.

Do not use random UUIDs.

Required assertions:

```text
deviceId != providerDeviceId
endpointId != providerEndpointId
capabilityId != providerCapabilityKey
```

---

## 9. New Types to Add

Package:

```text
com.sovereign.connect.core.topology.materialization
```

### 9.1 TopologyFact

```java
public sealed interface TopologyFact permits
    DeviceDiscoveryFact,
    EndpointDiscoveryFact,
    CapabilityDiscoveryFact,
    DeviceStateFact,
    HealthFact {

    UUID factId();
    String adapterInstanceId();
    String providerId();
    Instant observedAt();
}
```

### 9.2 DeviceDiscoveryFact

```java
public record DeviceDiscoveryFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String providerDeviceId,
    String providerDeviceKind,
    String displayNameHint,
    String manufacturerHint,
    String roomHint,
    String zoneHint,
    Instant observedAt,
    double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact {}
```

### 9.3 EndpointDiscoveryFact

```java
public record EndpointDiscoveryFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String providerDeviceId,
    String providerEndpointId,
    String providerEndpointKind,
    List<String> capabilityHints,
    Instant observedAt,
    double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact {}
```

### 9.4 CapabilityDiscoveryFact

```java
public record CapabilityDiscoveryFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String providerDeviceId,
    String providerEndpointId,
    String providerCapabilityKey,
    String capabilityKindHint,
    Instant observedAt,
    double confidence,
    Map<String, Object> rawProviderMetadata
) implements TopologyFact {}
```

### 9.5 DeviceStateFact

```java
public record DeviceStateFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String providerDeviceId,
    Map<String, Object> statePayload,
    Instant observedAt,
    double confidence
) implements TopologyFact {}
```

### 9.6 HealthFact

```java
public record HealthFact(
    UUID factId,
    String adapterInstanceId,
    String providerId,
    String providerDeviceId,
    String providerEndpointId,
    HealthStatus healthStatus,
    String healthReason,
    Instant observedAt,
    double confidence
) implements TopologyFact {}
```

For seed:

```text
providerEndpointId must be present for durable endpoint health.
```

### 9.7 MaterializationDecisionKind

```java
public enum MaterializationDecisionKind {
    ACCEPT_STRUCTURAL_MUTATION,
    ACCEPT_NON_STRUCTURAL_STATE,
    ACCEPT_HEALTH_UPDATE,
    REJECT_DUPLICATE,
    REJECT_INVALID_FACT,
    REJECT_UNAUTHORIZED_ADAPTER,
    NOOP
}
```

### 9.8 MaterializationDecision

```java
public record MaterializationDecision(
    UUID decisionId,
    UUID causationFactId,
    String habitatId,
    MaterializationDecisionKind kind,
    Optional<TopologyVersion> previousTopologyVersion,
    Optional<TopologyVersion> resultingTopologyVersion,
    List<TopologyChanged> emittedChanges,
    String reason
) {}
```

For rejected/noop decisions:

```text
emittedChanges = List.of()
```

For structural decisions:

```text
emittedChanges = delta from emittedEvents() as defined in §2
```

### 9.9 TopologyMaterializationService

```java
public interface TopologyMaterializationService {
    MaterializationDecision materialize(String habitatId, TopologyFact fact);
}
```

---

## 10. Materialization Service Constructor

```java
public class DefaultTopologyMaterializationService implements TopologyMaterializationService {

    public DefaultTopologyMaterializationService(
        BaseTopologyService baseTopologyService,
        H2BaseTopologyRepository repository,
        Predicate<String> admittedAdapterPredicate,
        Clock clock
    ) { ... }
}
```

`baseTopologyService` is used for structural mutations only.

`repository` is used for:

```text
state/health durable persistence;
duplicate detection;
habitat pre-condition checks.
```

Do NOT use `baseTopologyService.updateDeviceState(...)` from the materializer.

---

## 11. Materialization Flow — Per Fact Type

### 11.1 DeviceDiscoveryFact

```text
1. Check admission:
   if !admitted(fact.adapterInstanceId()) → REJECT_UNAUTHORIZED_ADAPTER.

2. Check habitat:
   repository.findByHabitatId(habitatId) → if empty → REJECT_INVALID_FACT.

3. Derive canonicalDeviceId:
   "device." + providerId + "." + providerDeviceId.

4. Check duplicate:
   if topology has device with that deviceId → REJECT_DUPLICATE.

5. Build DeviceNode:
   roomId = fact.roomHint() if valid, else first room in topology.
   zoneId = fact.zoneHint() if valid, else first zone in topology.
   providerRef = ProviderDeviceRef with providerDeviceId, providerId, metadata.

6. int before = baseTopologyService.emittedEvents().size().

7. result = baseTopologyService.addDeviceWithResult(habitatId, deviceNode).

8. emitted = delta from emittedEvents() using before.

9. Return ACCEPT_STRUCTURAL_MUTATION with previousVersion/resultingVersion/emittedChanges.
```

### 11.2 EndpointDiscoveryFact

```text
1. Check admission.
2. Check habitat.
3. Derive canonicalEndpointId.
4. Derive canonical parentDeviceId.
5. If topology has no device with parentDeviceId → REJECT_INVALID_FACT("parent device not found").
6. If topology has endpoint with canonicalEndpointId → REJECT_DUPLICATE.
7. Build EndpointNode with capabilities from fact.capabilityHints().
8. Delta pattern → addEndpointWithResult(habitatId, endpointNode).
9. Return ACCEPT_STRUCTURAL_MUTATION.
```

### 11.3 CapabilityDiscoveryFact

```text
1. Check admission.
2. Check habitat.
3. Derive canonicalEndpointId and canonicalCapabilityId.
4. If topology has no endpoint with canonicalEndpointId → REJECT_INVALID_FACT.
5. If endpoint already has capability with canonicalCapabilityId → REJECT_DUPLICATE.
6. Use delta pattern.
7. Call BaseTopologyService.addCapabilityWithResult(habitatId, endpointId, capability).
8. Return ACCEPT_STRUCTURAL_MUTATION.
```

Do not call `addEndpointWithResult(...)` to update an existing endpoint.

### 11.4 DeviceStateFact

```text
1. Check admission.
2. Derive canonicalDeviceId.
3. Check topology has that device; else REJECT_INVALID_FACT.
4. repository.saveDeviceState(habitatId, canonicalDeviceId, fact.statePayload()).
5. Return ACCEPT_NON_STRUCTURAL_STATE.
6. No topologyVersion change.
7. emittedChanges empty.
```

### 11.5 HealthFact

```text
1. Check admission.
2. Require providerEndpointId for seed.
3. Derive canonicalEndpointId.
4. Check topology has that endpoint; else REJECT_INVALID_FACT.
5. repository.saveEndpointHealth(...).
6. Return ACCEPT_HEALTH_UPDATE.
7. No topologyVersion change.
8. emittedChanges empty.
```

### 11.6 Duplicate / Invalid / Non-admitted

```text
Return typed decision.
No topology mutation.
No version advance.
emittedChanges empty.
```

---

## 12. Durable Test Wiring

```java
DataSource dataSource = new DriverManagerDataSource(h2FileUrl, "sa", "");

H2BaseTopologyRepository repository =
    new H2BaseTopologyRepository(dataSource, mapper, clock);

BaseTopologyService mutationService =
    new BaseTopologyService(repository, clock);

CoreSnapshotQueryService queryService =
    new CoreSnapshotQueryService(repository, clock);

Predicate<String> admittedAll = adapterInstanceId -> true;

DefaultTopologyMaterializationService materializationService =
    new DefaultTopologyMaterializationService(
        mutationService,
        repository,
        admittedAll,
        clock
    );

mutationService.createInitialTopology(
    "habitat-007",
    List.of(room),
    List.of(zone),
    List.of(),
    List.of()
);
```

After recreation:

```text
discard all instances;
create new DataSource;
create new H2BaseTopologyRepository;
create new BaseTopologyService;
create new CoreSnapshotQueryService.
```

---

## 13. Test Expectations

New test class:

```text
TopologyMaterializationSeedTest
```

Required test scenarios:

```text
1. DeviceDiscoveryFact → canonical DeviceNode created → topologyVersion advanced.
2. EndpointDiscoveryFact → canonical EndpointNode created → version advanced.
3. CapabilityDiscoveryFact → capability attached → version advanced.
4. providerDeviceId != canonical deviceId.
5. providerEndpointId != canonical endpointId.
6. providerCapabilityKey != canonical capabilityId.
7. TopologyChanged has fromVersion/toVersion and DEVICE_ADDED / ENDPOINT_ADDED / CAPABILITY_ADDED.
8. Duplicate DeviceDiscoveryFact → REJECT_DUPLICATE → version unchanged.
9. Duplicate EndpointDiscoveryFact → REJECT_DUPLICATE → version unchanged.
10. Duplicate CapabilityDiscoveryFact → REJECT_DUPLICATE → version unchanged.
11. REJECT_DUPLICATE → emittedChanges empty.
12. DeviceStateFact → version unchanged → state recoverable from repository.
13. HealthFact endpoint-level → version unchanged → health recoverable.
14. HealthFact without providerEndpointId → REJECT_INVALID_FACT or NOOP.
15. Non-admitted adapter → REJECT_UNAUTHORIZED_ADAPTER → version unchanged.
16. After recreation: CoreSnapshotQueryService observes materialized device/endpoint/capability.
17. Query after materialization does not advance version.
18. validateTarget works with post-materialization topology version.
19. No SC-B / SC-D / Projection / Session / Authority dependency.
```

Existing 23 tests must pass.

Expected total:

```text
at least 24 tests passing
```

---

## 14. AC Numbering

The MIR defines AC-001 through AC-025.

Use `acceptance-map.md`.

Do not report AC-026, AC-027 or AC-028.

Delegation, no-bypass, deterministic IDs, capability helper and endpoint-level health are mandatory rules and stop conditions, not extra AC numbers.

---

## 15. Negative Scope

Do NOT implement:

```text
real SC-D adapter;
SC-B transport;
NATS / JetStream;
REST / gRPC / WebSocket API;
MCP facade;
Projection;
Effective View;
Session;
Identity;
Authority;
Policy;
full Adapter Lifecycle;
production database schema;
external wire ABI;
full RelationFact;
deletion/removal materialization;
independent materializer mutation engine;
device-level health persistence unless already supported.
```

Stop and report if any becomes necessary.

---

## 16. Execution Status

Implementation is authorized under:

```text
MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001 v1.0.0-accepted
```
