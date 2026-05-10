# MIR-010 — SC-C Kernel Hardening Seed — Implementation Context

```text
MIR: MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001
MIR Version: v1.0.0-accepted
MU: MU-SOV-SC-C-KERNEL-HARDENING-SEED-001
Operational Slot: MU-010
Status: Accepted execution artifact
Date: 2026-05-10
```

---

## 0. Purpose

Read this file before executing `docs/mir/mir-010/codex-prompt.md`.

This context defines the existing validated API surface, the inherited coverage map,
the gap analysis for new cross-MU tests, and the durable wiring pattern for MIR-010.

MIR-010 is a hardening MIR. The goal is to add ONE composed durable test and produce a
classification table — not to rewrite existing tests.

---

## 1. Existing Test Surface — Before MIR-010

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
```

Existing test classes:

```text
BaseTopologyServiceTest        — 8 tests  (InMemory)
TopologyVersionHardeningTest   — 12 tests (InMemory)
PersistenceMemorySeedTest      — 1 test   (H2 durable)
CoreSnapshotQuerySeedTest      — 1 test   (H2 durable)
```

Do not break any of these.

---

## 2. Existing Validated API Surface

### 2.1 BaseTopologyService (mutation service)

```java
public class BaseTopologyService {
    public BaseTopologyService(BaseTopologyRepository repository);
    public BaseTopologyService(BaseTopologyRepository repository, Clock clock);

    public HabitatBaseTopology createInitialTopology(
        String habitatId,
        List<RoomNode> rooms, List<ZoneNode> zones,
        List<DeviceNode> devices, List<EndpointNode> endpoints
    );
    public HabitatBaseTopology addEndpoint(String habitatId, EndpointNode endpoint);
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

### 2.2 H2BaseTopologyRepository (durable adapter — implements BaseTopologyRepository AND CoreSnapshotReadPort)

```java
public class H2BaseTopologyRepository implements BaseTopologyRepository, CoreSnapshotReadPort {

    public H2BaseTopologyRepository(DataSource dataSource);
    public H2BaseTopologyRepository(DataSource dataSource, ObjectMapper objectMapper, Clock clock);

    // BaseTopologyRepository
    void save(HabitatBaseTopology topology);
    Optional<HabitatBaseTopology> findByHabitatId(String habitatId);
    Optional<TopologyVersion> findCurrentVersion(String habitatId);

    // CoreSnapshotReadPort
    Optional<BaseTopologySnapshot> findSnapshot(String habitatId);
    Optional<HabitatBaseTopology> findTopology(String habitatId);

    // Persistence
    void appendMutationRecord(TopologyMutationRecord record);
    List<TopologyMutationRecord> findMutationRecords(String habitatId);
    void saveDeviceState(String habitatId, String deviceId, Map<String, Object> state);
    Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);
    void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health);
    Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);
}
```

### 2.3 CoreSnapshotQueryService (query service — depends on CoreSnapshotReadPort)

```java
public class CoreSnapshotQueryService {
    public CoreSnapshotQueryService(CoreSnapshotReadPort readPort, Clock clock);

    Optional<CoreSnapshot> findCurrentSnapshot(String habitatId);
    Optional<TopologyVersion> findCurrentTopologyVersion(String habitatId);
    Optional<DeviceSnapshot> findDevice(String habitatId, String deviceId);
    Optional<EndpointSnapshot> findEndpoint(String habitatId, String endpointId);
    Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);
    Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);
}
```

---

## 3. Inherited Coverage Map

This map allows you to classify ACs WITHOUT reading all four test classes.

Use it to identify inherited coverage vs. gaps.

```text
AC-001  Inherited — all 22 existing tests are the evidence
AC-002  New — ScCoreKernelHardeningTest is created by this MIR
AC-003  New — coverage classification table in implementation-report.md

AC-004  Inherited — BaseTopologyServiceTest#createsAndRetrievesFullBaseTopologyHierarchy
AC-005  Inherited — BaseTopologyServiceTest#supportsOneDeviceWithMultipleFirstClassEndpoints
AC-006  Inherited — BaseTopologyServiceTest#supportsOneEndpointWithMultipleCapabilities
AC-007  Inherited — TopologyVersionHardeningTest (structural mutation advances version)

AC-008  Inherited — TopologyVersionHardeningTest (rejected mutation does not advance)
AC-009  Inherited — TopologyVersionHardeningTest (state update does not advance)
AC-010  Inherited — TopologyVersionHardeningTest (health update does not advance)
AC-011  Inherited — CoreSnapshotQuerySeedTest (afterQueries == beforeQueries assertion)

AC-012  Inherited — PersistenceMemorySeedTest + CoreSnapshotQuerySeedTest
AC-013  Inherited — PersistenceMemorySeedTest + CoreSnapshotQuerySeedTest
AC-014  Inherited — PersistenceMemorySeedTest + CoreSnapshotQuerySeedTest
AC-015  Inherited — PersistenceMemorySeedTest
AC-016  Inherited — CoreSnapshotQuerySeedTest (provider lookup returns empty for tuya.device.abc)

AC-017  Inherited — CoreSnapshotQuerySeedTest (reflective field check on CoreSnapshotQueryService)
AC-018  Inherited — CoreSnapshotQuerySeedTest (full instance recreation pattern)

AC-019  Inherited — PersistenceMemorySeedTest + CoreSnapshotQuerySeedTest
AC-020  Inherited — PersistenceMemorySeedTest + CoreSnapshotQuerySeedTest
AC-021  Inherited — TopologyVersionHardeningTest + CoreSnapshotQuerySeedTest
AC-022  Inherited — TopologyVersionHardeningTest + CoreSnapshotQuerySeedTest
AC-023  Inherited — CoreSnapshotQuerySeedTest (validateTarget after recreation)

AC-024  New — confirmed by implementation report (no feature scope introduced)
AC-025  New — mvn test must pass with all prior tests plus new hardening test
```

---

## 4. Gap Analysis — What MIR-010 Actually Adds

Based on the inherited coverage map, the actual new work is:

```text
New cross-MU composed test (ScCoreKernelHardeningTest):
  Provides a single test that explicitly wires all four MUs together over the
  durable H2 path. This does NOT duplicate existing tests — it provides a
  regression net that explicitly exercises the composition:
    MU-001 topology creation
    + MU-002 topologyVersion semantics
    + MU-004 durable persistence and recovery
    + MU-006 query layer reads durable state

  The key differences from existing tests:
  - PersistenceMemorySeedTest does NOT use CoreSnapshotQueryService
  - CoreSnapshotQuerySeedTest exercises MU-006 but is not labeled as kernel hardening
  - The new composed test exercises the FULL stack in a single labeled test

Classification table:
  Produces the required AC-001 through AC-025 classification table in the report.
  This is the primary deliverable of MIR-010.
```

---

## 5. Durable Wiring Pattern

For the composed hardening test, use this setup:

```java
// Setup phase
DataSource dataSource = new DriverManagerDataSource(h2FileUrl, "sa", "");
H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource, mapper, clock);
BaseTopologyService mutationService = new BaseTopologyService(repository, clock);
CoreSnapshotQueryService queryService = new CoreSnapshotQueryService(repository, clock);

// After discard
repository = null;
mutationService = null;
queryService = null;

// Recreation phase
DataSource newDataSource = new DriverManagerDataSource(sameH2FileUrl, "sa", "");
H2BaseTopologyRepository newRepository = new H2BaseTopologyRepository(newDataSource, mapper, clock);
CoreSnapshotQueryService newQueryService = new CoreSnapshotQueryService(newRepository, clock);
// Create new mutation service only for validateTarget
BaseTopologyService newMutationService = new BaseTopologyService(newRepository, clock);
```

Use `@TempDir` for file path. Use `DB_CLOSE_DELAY=0` in JDBC URL.

---

## 6. Boundary Guard Options

For AC-019 to AC-022, acceptable boundary evidence:

```text
- no SC-B/SC-D/Hub/Projection/Authority imports in query service class
- no such parameters in constructors of tested services
- reflection check on service fields (as done in CoreSnapshotQuerySeedTest)
- absence of such dependencies in test setup
- implementation report statement
```

No ArchUnit required unless already present.

---

## 7. Negative Scope

Do not implement: Topology Materialization, TemporalActs, Adapter Lifecycle,
SC-B transport, SC-D protocol, REST/gRPC/WebSocket API, MCP facade, Projection,
Effective View, Session/Identity/Authority/Policy, TargetResolutionSnapshot,
production schema, storage ADR.

If any negative-scope item becomes necessary, stop and report.

---

## 8. Execution Status

Implementation is authorized under:
```text
MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 v1.0.0-accepted
```
