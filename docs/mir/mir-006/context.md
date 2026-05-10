# MIR-006 — SC-C Core Snapshot Query Seed — Implementation Context

```text
MIR: MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
MIR Version: v1.0.0-accepted
MU: MU-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
Status: Accepted execution artifact
Date: 2026-05-10
```

---

## 0. Purpose

Read this file before executing `docs/mir/mir-006/codex-prompt.md`.

This context defines the existing codebase surface, the query service wiring pattern, new query models, the read/write separation rule, and the recovery test strategy for MU-006.

This file is operational. It does not override MIR, PDR, RFC, INDEX or SYNC documents.

---

## 1. Technology Stack

```text
Java 21+, Spring Boot 3.x, Maven, hexagonal architecture.
H2/JDBC seed persistence already introduced by MU-004.
MU-006 does NOT add new persistence technology.
```

---

## 2. Existing Codebase — Exact API Surface

### 2.1 Repository port — unchanged since MU-001

```java
public interface BaseTopologyRepository {
    void save(HabitatBaseTopology topology);
    Optional<HabitatBaseTopology> findByHabitatId(String habitatId);
    Optional<TopologyVersion> findCurrentVersion(String habitatId);
}
```

### 2.2 In-memory adapter — unchanged

```text
com.sovereign.connect.adapter.persistence.InMemoryBaseTopologyRepository
```

This adapter is still used by MU-001/MU-002 tests.

Do not modify it for MU-006 unless required by compile compatibility.

### 2.3 H2 durable adapter — added by MU-004

Expected class:

```java
public class H2BaseTopologyRepository implements BaseTopologyRepository {

    public H2BaseTopologyRepository(DataSource dataSource);
    public H2BaseTopologyRepository(DataSource dataSource, ObjectMapper objectMapper, Clock clock);

    // BaseTopologyRepository port methods
    void save(HabitatBaseTopology topology);
    Optional<HabitatBaseTopology> findByHabitatId(String habitatId);
    Optional<TopologyVersion> findCurrentVersion(String habitatId);

    // Snapshot envelope
    Optional<BaseTopologySnapshot> findSnapshot(String habitatId);

    // Mutation ledger
    void appendMutationRecord(TopologyMutationRecord record);
    List<TopologyMutationRecord> findMutationRecords(String habitatId);

    // Device state — durable
    void saveDeviceState(String habitatId, String deviceId, Map<String, Object> state);
    Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);

    // Endpoint health — durable
    void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health);
    Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);
}
```

These are the methods the query service can delegate to for reads.

The query service needs:

```text
findSnapshot
findCurrentVersion
findByHabitatId
findDeviceState
findEndpointHealth
```

### 2.4 Mutation service — BaseTopologyService

```java
public class BaseTopologyService {

    public BaseTopologyService(BaseTopologyRepository repository);
    public BaseTopologyService(BaseTopologyRepository repository, Clock clock);

    // MU-001
    public HabitatBaseTopology createInitialTopology(
        String habitatId,
        List<RoomNode> rooms, List<ZoneNode> zones,
        List<DeviceNode> devices, List<EndpointNode> endpoints
    );

    public HabitatBaseTopology addEndpoint(String habitatId, EndpointNode endpoint);

    // MU-002
    public TopologyMutationResult addEndpointWithResult(String habitatId, EndpointNode endpoint);
    public void updateEndpointHealth(String habitatId, String endpointId, HealthStatus status);
    public void updateDeviceState(String habitatId, String deviceId, Map<String, Object> state);
    public Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);

    public TargetValidationResult validateTarget(
        String habitatId, TopologyTargetRef target, TopologyVersion requestTopologyVersion
    );

    public Optional<TopologyVersion> findCurrentVersion(String habitatId);
    public List<TopologyChanged> emittedEvents();
}
```

Critical warning:

```text
BaseTopologyService.findDeviceState(...) reads from an internal ConcurrentMap
that does NOT survive service recreation.
```

Therefore:

```text
CoreSnapshotQueryService MUST NOT use BaseTopologyService.findDeviceState(...).
```

It must read device state from the durable adapter or from a read port backed by the durable adapter.

### 2.5 Existing test classes

```text
BaseTopologyServiceTest             — 8 tests  — InMemory
TopologyVersionHardeningTest        — 12 tests — InMemory
PersistenceMemorySeedTest           — 1 test   — H2 durable

Total:
  21 tests

All must continue passing.
```

---

## 3. Critical Rule — Read/Write Separation

MIR-006-D-003:

```text
CoreSnapshotQueryService reads from persistence/repository ports.
BaseTopologyService owns mutations.
```

The query service MUST NOT:

```text
- call BaseTopologyService.findDeviceState(...);
- depend on BaseTopologyService being alive;
- read from any service-local ConcurrentMap;
- make query correctness depend on mutation service object lifetime.
```

The query service reads from:

```text
- CoreSnapshotReadPort, recommended;
- or directly from H2BaseTopologyRepository methods, acceptable for seed only.
```

---

## 3.1 MU-006 Incremental Value

MU-006 is not a persistence retest.

MU-004 already proved save/retrieve/recovery.

MU-006 validates:

```text
1. Composition:
   CoreSnapshot composes topology, topologyVersion, device state and endpoint health
   into one canonical read model.

2. Canonical lookup:
   device and endpoint queries use canonical IDs and return not-found without
   provider-native fallback.

3. Read/write separation:
   CoreSnapshotQueryService reads persisted state while BaseTopologyService
   remains the mutation service.
```

---

## 4. Wiring Pattern — How the Pieces Connect

### 4.1 Introduce CoreSnapshotReadPort

```java
public interface CoreSnapshotReadPort {
    Optional<BaseTopologySnapshot> findSnapshot(String habitatId);
    Optional<TopologyVersion> findCurrentVersion(String habitatId);
    Optional<HabitatBaseTopology> findTopology(String habitatId);
    Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);
    Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);
}
```

Place in:

```text
com.sovereign.connect.core.topology.port
```

### 4.2 H2BaseTopologyRepository implements CoreSnapshotReadPort

Add:

```java
implements CoreSnapshotReadPort
```

to the existing class declaration.

All required methods already exist on the adapter except possibly:

```java
Optional<HabitatBaseTopology> findTopology(String habitatId)
```

That method may delegate to:

```java
findByHabitatId(habitatId)
```

No new adapter class is needed.

No breaking change is allowed.

Do not modify `BaseTopologyRepository`.

Do not modify `InMemoryBaseTopologyRepository`.

### 4.3 Query service constructor

```java
public CoreSnapshotQueryService(CoreSnapshotReadPort readPort, Clock clock) { ... }
```

### 4.4 Test wiring — setup phase

```java
DataSource ds = dataSource(jdbcUrl);
H2BaseTopologyRepository repository = new H2BaseTopologyRepository(ds, mapper, clock);

// repository implements both BaseTopologyRepository and CoreSnapshotReadPort
BaseTopologyService mutationService = new BaseTopologyService(repository, clock);
CoreSnapshotQueryService queryService = new CoreSnapshotQueryService(repository, clock);
```

### 4.5 Test wiring — after recreation

```java
DataSource newDs = dataSource(sameJdbcUrl);
H2BaseTopologyRepository newRepository = new H2BaseTopologyRepository(newDs, mapper, clock);

CoreSnapshotQueryService newQueryService = new CoreSnapshotQueryService(newRepository, clock);
```

After recreation:

```text
- NO old mutation service;
- NO old query service;
- NO old adapter;
- NO reliance on in-process maps.
```

---

## 5. New Query Models — Reference Shapes

Place all in:

```text
com.sovereign.connect.core.topology.query
```

### 5.1 CoreSnapshot

```java
public record CoreSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    HabitatBaseTopology topology,
    Map<String, Map<String, Object>> deviceStates,
    Map<String, EndpointHealth> endpointHealth,
    Instant readAt
) {}
```

The `deviceStates` and `endpointHealth` maps aggregate known state/health for the habitat.

They may be empty if no state/health was persisted.

### 5.2 DeviceSnapshot

```java
public record DeviceSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    DeviceNode device,
    Map<String, Object> state,
    Instant readAt
) {}
```

### 5.3 EndpointSnapshot

```java
public record EndpointSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    EndpointNode endpoint,
    EndpointHealth health,
    Instant readAt
) {}
```

---

## 6. Mandatory Query Surface

```java
public class CoreSnapshotQueryService {

    // Current canonical snapshot with state/health
    Optional<CoreSnapshot> findCurrentSnapshot(String habitatId);

    // Fast version read
    Optional<TopologyVersion> findCurrentTopologyVersion(String habitatId);

    // Canonical device lookup — NOT providerDeviceId
    Optional<DeviceSnapshot> findDevice(String habitatId, String deviceId);

    // Canonical endpoint lookup — NOT providerEndpointId
    Optional<EndpointSnapshot> findEndpoint(String habitatId, String endpointId);

    // Device state from persistence — NOT from BaseTopologyService
    Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);

    // Endpoint health from persistence
    Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);
}
```

Capability lookup:

```text
derive from findEndpoint(...) + endpoint.capabilities().
No dedicated method is required.
```

Target validation:

```text
reuse BaseTopologyService.validateTarget(...) in tests.
No TargetResolutionSnapshot is required.
```

---

## 7. Query Method Implementation Guide

### 7.1 findCurrentSnapshot

```text
1. readPort.findSnapshot(habitatId) -> BaseTopologySnapshot.
2. For each device in topology: readPort.findDeviceState(habitatId, deviceId).
3. For each endpoint in topology: readPort.findEndpointHealth(habitatId, endpointId).
4. Compose CoreSnapshot with topology + version + states + health + readAt.
5. Verify: result.topologyVersion == result.topology.topologyVersion.
```

### 7.2 findCurrentTopologyVersion

```text
readPort.findCurrentVersion(habitatId)
```

### 7.3 findDevice

```text
1. readPort.findTopology(habitatId) -> find device by canonical deviceId.
2. If not found -> Optional.empty() without provider fallback.
3. readPort.findDeviceState(habitatId, deviceId) -> state.
4. readPort.findCurrentVersion(habitatId) -> version.
5. Compose DeviceSnapshot.
```

### 7.4 findEndpoint

```text
1. readPort.findTopology(habitatId) -> find endpoint by canonical endpointId.
2. If not found -> Optional.empty() without provider fallback.
3. readPort.findEndpointHealth(habitatId, endpointId) -> health.
4. readPort.findCurrentVersion(habitatId) -> version.
5. Compose EndpointSnapshot.
```

### 7.5 findDeviceState / findEndpointHealth

```text
Delegate directly to readPort.
MUST NOT call BaseTopologyService.findDeviceState(...).
```

---

## 8. Endpoint Health Source Rule

Endpoint health must be persisted through the same read boundary that `CoreSnapshotQueryService` will use.

Preferred:

```text
repository.saveEndpointHealth(habitatId, endpointId, endpointHealth)
```

Allowed:

```text
service.updateEndpointHealth(...), only if H2BaseTopologyRepository.save(topology)
persists endpoint health into the same store used by findEndpointHealth(...),
or if findEndpointHealth(...) reconstructs health from the persisted aggregate.
```

The test MUST prove:

```text
queryService.findEndpointHealth(...) succeeds after repository/service/query recreation.
```

---

## 9. Recovery Test Pattern

```text
Phase 1 — Setup and persist
  1. Create DataSource(tempDir) -> H2BaseTopologyRepository.
  2. Create BaseTopologyService(repository) for mutations.
  3. Create CoreSnapshotQueryService(repository) for queries.
  4. service.createInitialTopology(...).
  5. service.addEndpointWithResult(...) — structural mutation.
  6. repository.saveDeviceState(...) — via durable adapter/read boundary.
  7. Persist endpoint health through the same read boundary used by query service.
  8. Capture topologyVersion via queryService.findCurrentTopologyVersion(...).

Phase 2 — Destroy everything
  9. Discard service, repository, queryService and dataSource.

Phase 3 — Recreate and query
  10. Create NEW DataSource(same tempDir) -> NEW H2BaseTopologyRepository.
  11. Create NEW CoreSnapshotQueryService(newRepository).
  12. queryService.findCurrentSnapshot(habitatId) — full snapshot.
  13. queryService.findCurrentTopologyVersion(habitatId) — version consistency.
  14. queryService.findDevice(habitatId, deviceId) — canonical lookup.
  15. queryService.findEndpoint(habitatId, endpointId) — canonical lookup.
  16. queryService.findDeviceState(habitatId, deviceId) — from persistence.
  17. queryService.findEndpointHealth(habitatId, endpointId) — from persistence.
  18. queryService.findDevice(habitatId, "nonexistent-id") — not-found.
  19. queryService.findDevice(habitatId, providerDeviceId) — not-found, no fallback.
  20. Verify provider refs are metadata, not canonical IDs.

Phase 4 — Target validation integration
  21. Create NEW BaseTopologyService(newRepository) only for validateTarget(...).
  22. validateTarget(habitatId, target, recoveredVersion) — prove it works.
```

Do not reuse the old service instance.

Do not reuse the old query service.

Do not reuse the old repository.

Do not rely on any in-process map surviving recovery.

The only state that may survive is state persisted through the durable repository/read boundary.

Use `@TempDir` for H2 file path.

---

## 10. Boundary Constraints

Query MUST NOT:

```text
filter by user/session/role/authority/policy
return Effective View
dispatch commands
call SC-D
require SC-B
advance topologyVersion
mutate topology
update state/health
emit events
```

Query uses canonical IDs only.

Provider IDs are metadata, never lookup keys.

---

## 11. Negative Scope

Do NOT implement:

```text
Projection
Effective View
authority/session filtering
Surface layout
Hub context
SC-B transport
REST/gRPC/WebSocket API
MCP facade
historical snapshots
snapshot diff/pagination
command dispatch
action execution
real SC-D adapters
provider rediscovery
production query DTO
external wire ABI
TargetResolutionSnapshot as required mechanism
```

---

## 12. Architectural Placement

```text
CoreSnapshotReadPort           -> com.sovereign.connect.core.topology.port
CoreSnapshotQueryService       -> com.sovereign.connect.core.topology.query
CoreSnapshot                   -> com.sovereign.connect.core.topology.query
DeviceSnapshot                 -> com.sovereign.connect.core.topology.query
EndpointSnapshot               -> com.sovereign.connect.core.topology.query
CoreSnapshotQuerySeedTest      -> com.sovereign.connect.core.topology
```

`H2BaseTopologyRepository` adds `implements CoreSnapshotReadPort`.

No new adapter class is required.

No breaking changes to existing code are allowed.
