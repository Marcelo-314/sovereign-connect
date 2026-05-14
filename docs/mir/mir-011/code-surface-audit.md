# Code Surface Audit — MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001

```text
Audit ID:            CSA-MU-011
Version:             v0.1.1-merged
MIR:                 MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001 v0.1.0-draft
MU:                  MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
Operational Slot:    MU-011
Closes:              DEBT-007-001
Repository package:  sovereign-connect-007-hardening.zip
Repository state:    fix/sc-c-mir-007-health-persistence-hardening (2026-05-14)
Inspected HEAD:      626fdd7 test(sc-c): harden topology materialization health persistence
Audited by:          Architect review + assistant repository inspection
Date:                2026-05-14
Surface category:    Non-Greenfield — full audit required
Audit status:        Draft / merged audit ready for architect approval
```

---

## 0. Audit verdict

MU-011 is correctly classified as **Non-Greenfield**.

The implementation touches the persistence boundary already used by MU-004, MU-006, MU-007 and MU-010. The central defect class is real: `H2BaseTopologyRepository.save(HabitatBaseTopology)` persists the structural topology snapshot and still performs endpoint-health writes as a side effect.

MU-007 added transitional merge semantics to prevent stale aggregate `UNKNOWN` endpoint health from overwriting durable endpoint health written by `HealthFact`. That patch is functionally correct, but it is not a clean persistence boundary.

This audit recommends proceeding to implementation only after the MIR accepts the following strategy:

```text
1. H2BaseTopologyRepository.save(HabitatBaseTopology) becomes structural-only.
2. Endpoint health writes become explicit write operations, not hidden save() side effects.
3. Initial UNKNOWN endpoint health created during endpoint materialization is written explicitly.
4. Existing service-level endpoint-health durability is preserved through an explicit health persistence boundary or equivalent narrow mechanism.
5. DEBT-007-002 — TopologyMaterializationStatePort extraction — remains out of scope for MU-011.
```

The implementation package MUST NOT proceed until this audit is approved and reflected in `context.md`, `acceptance-map.md` and `codex-prompt.md`.

---

## 1. Purpose

This audit determines the safe implementation strategy for separating structural topology persistence from health/state persistence in the current H2 seed repository, closing `DEBT-007-001` without:

- breaking current SC-C seed tests;
- changing canonical topology semantics;
- introducing SC-B, SC-D, Projection, Hub, Session, Identity, Authority or Policy concerns;
- prematurely implementing `DEBT-007-002` / `TopologyMaterializationStatePort`.

The audit is a repository-reality binding artifact for `docs/mir/mir-011/code-surface-audit.md`. It is not an independent corpus artifact.

---

## 2. Affected code surface

```text
Package: com.sovereign.connect.adapter.persistence
  H2BaseTopologyRepository.java        — PRIMARY surface
    save(HabitatBaseTopology)           — structural save with embedded endpoint-health side effect
    saveEndpointHealth(...)             — endpoint health persistence, concrete seed method
    saveDeviceState(...)                — device state persistence, concrete seed method
    findEndpointHealth(...)             — endpoint health read, in CoreSnapshotReadPort implementation
    findDeviceState(...)                — device state read, in CoreSnapshotReadPort implementation
    findSnapshot(...)                   — structural snapshot read
    findByHabitatId(...)                — structural topology read
    findCurrentVersion(...)             — topologyVersion read
    appendMutationRecord(...)           — mutation record persistence, already separate
    findMutationRecords(...)            — mutation record read, already separate
    createSchema()                      — topology_snapshots, mutation_records, device_states, endpoint_health

  InMemoryBaseTopologyRepository.java  — in-memory BaseTopologyRepository implementation
    save(HabitatBaseTopology)           — stores topology only; no health/state table side effect

Package: com.sovereign.connect.core.topology.port
  BaseTopologyRepository.java           — structural repository port:
                                            save, findByHabitatId, findCurrentVersion
                                          MUST NOT become mixed structural/health/state port

  CoreSnapshotReadPort.java             — read port:
                                            findSnapshot, findTopology, findCurrentVersion,
                                            findDeviceState, findEndpointHealth

  Candidate narrow addition, only if implementation requires it:
    EndpointHealthWritePort or equivalent explicit health persistence boundary.
    This MUST remain narrower than TopologyMaterializationStatePort and MUST NOT
    absorb MU-012 scope.

Package: com.sovereign.connect.core.topology.service
  BaseTopologyService.java
    createInitialTopology(...)          — requires rooms/zones lists supplied by caller/tests
    addDeviceWithResult(...)            — structural mutation; calls repository.save(...)
    addEndpointWithResult(...)          — structural mutation; calls repository.save(...)
    addCapabilityWithResult(...)        — structural mutation; calls repository.save(...)
    updateEndpointHealth(...)           — currently mutates aggregate health and calls repository.save(...)
    updateDeviceState(...)              — in-process deviceStates map; not durable H2 state
    validateTarget(...)                 — must remain recovery-compatible

Package: com.sovereign.connect.core.topology.materialization
  DefaultTopologyMaterializationService.java
    materializeDevice(...)              — calls BaseTopologyService; structural path
    materializeEndpoint(...)            — calls BaseTopologyService; structural path
    materializeCapability(...)          — calls BaseTopologyService; structural path
    materializeDeviceState(...)         — calls H2 repository saveDeviceState(...) directly
    materializeHealth(...)              — calls H2 repository saveEndpointHealth(...) directly
    chooseRoomId()                      — requires at least one RoomNode
    chooseZoneId()                      — requires at least one ZoneNode

Tests / regression surfaces:
  BaseTopologyServiceTest.java
  TopologyVersionHardeningTest.java
  PersistenceMemorySeedTest.java
  CoreSnapshotQuerySeedTest.java
  ScCoreKernelHardeningTest.java
  TopologyMaterializationSeedTest.java
```

All current tests reported by the repository test suite MUST remain passing. The ZIP includes Surefire reports showing the current seed suite passing; tests were inspected but not re-run in this audit environment.

---

## 3. Critical discovery — RoomNode and ZoneNode already exist in the model

`RoomNode` and `ZoneNode` are already present as first-class model types in the codebase:

```java
// HabitatBaseTopology already has:
List<RoomNode> rooms
List<ZoneNode> zones

// DefaultTopologyMaterializationService already requires:
chooseRoomId() -> throws if topology.rooms() is empty
chooseZoneId() -> throws if topology.zones() is empty
```

Current tests initialize topology with rooms and zones using calls equivalent to:

```java
createInitialTopology(habitatId, List.of(room()), List.of(zone()), ...)
```

This is **not a gap in MU-011**. It confirms that `D-ROOM-ZONE-001` has partial model-level seed implementation already.

It does **not** close the future Room/Zone topology work. `MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001` remains required for:

```text
- RoomDiscoveryFact;
- ZoneDiscoveryFact;
- DeviceRoomAssignmentFact or equivalent;
- materialization of rooms/zones from adapter/provider facts;
- TopologySpatialRelation(LOCATED_IN);
- query behavior for room/zone placement;
- formal consistency between DeviceNode.roomId and LOCATED_IN;
- contract reconciliation with RFC-SOV-TOPOLOGY-BASE-PROJECTION-SPLIT-001.
```

**Implication for MU-011:** rooms and zones are part of the structural topology snapshot serialized into `topology_snapshots.topology_json`. MU-011 MUST NOT change room/zone handling. Its scope is persistence-boundary hardening for structural topology vs health/state.

**Corpus feedback trigger:** `SYNC` / `INDEX` / future `PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001` should record that partial Room/Zone model implementation already exists and is the starting point for MU-013, not evidence that MU-013 is complete.

---

## 4. Root cause of DEBT-007-001

Current `H2BaseTopologyRepository.save()` has dual responsibility:

```java
@Override
public void save(HabitatBaseTopology topology) {
    // Responsibility 1: structural topology snapshot
    jdbcTemplate.update("MERGE INTO topology_snapshots ... VALUES (?, ?, ?, ?)", ...);

    // Responsibility 2: endpoint health maintenance (embedded, conditional)
    topology.endpoints().forEach(endpoint -> {
        Optional<EndpointHealth> durable = findEndpointHealth(topology.habitatId(), endpoint.endpointId());

        if (durable.isEmpty() || endpoint.health().status() != HealthStatus.UNKNOWN) {
            saveEndpointHealth(topology.habitatId(), endpoint.endpointId(), endpoint.health());
        }
    });
}
```

The merge-semantics patch prevents structural saves carrying stale `UNKNOWN` aggregate health from overwriting durable non-`UNKNOWN` health. It works, but it preserves coupling.

```text
The problem is not current functional correctness.
The problem is mixed ownership inside save().
```

A structural `save(HabitatBaseTopology)` should write only structural topology state. Health/state writes must be explicit write operations through dedicated call sites or a narrow persistence boundary.

---

## 5. Existing invariants to preserve

### INV-011-001 — Structural topology owns topologyVersion advancement

Structural mutations through `BaseTopologyService` advance `topologyVersion`. Health/state writes MUST NOT advance `topologyVersion`.

Preserved by current tests around:

```text
- structural mutation result versions;
- device state update non-advancement;
- endpoint health update non-advancement;
- materialization state/health decision kinds.
```

---

### INV-011-002 — `BaseTopologyRepository` remains structural at the port level

`BaseTopologyRepository` currently exposes only:

```text
save(HabitatBaseTopology)
findByHabitatId(String)
findCurrentVersion(String)
```

MU-011 MUST NOT turn this into a mixed structural + health/state port.

Adding `saveEndpointHealth(...)` directly to `BaseTopologyRepository` is prohibited unless the MIR is revised. If a write boundary is needed, use a separate narrow interface such as `EndpointHealthWritePort` or equivalent.

---

### INV-011-003 — Core Snapshot Query reads through read ports

`CoreSnapshotQueryService` depends on `CoreSnapshotReadPort`. It reads device state and endpoint health from the read boundary. It MUST NOT be changed to depend on `BaseTopologyService`, in-process maps or repository internals.

---

### INV-011-004 — Provider refs remain metadata

Provider IDs and provider refs may be persisted and exposed as metadata. They MUST NOT become canonical lookup keys, canonical identity or routing authority.

---

### INV-011-005 — SC-C boundary purity remains intact

MU-011 MUST NOT introduce dependencies on:

```text
SC-B
SC-D
NATS / JetStream
Vert.x
Projection / Effective View
Hub
Session
Identity
Authority
Policy
Surface UX
```

---

### INV-011-006 — HealthFact durable health survives later structural mutation

The regression from MU-007 remains normative:

```text
1. materialize device + endpoint;
2. write DEGRADED health via HealthFact;
3. materialize capability, causing structural save;
4. assert durable endpoint health remains DEGRADED;
5. assert recovery still sees DEGRADED health.
```

After MU-011, this must pass because structural saves do not touch `endpoint_health`, not because `save()` contains conditional merge semantics.

---

### INV-011-007 — Snapshot consistency remains strict

`BaseTopologySnapshot` enforces:

```text
snapshot.topologyVersion == snapshot.topology.topologyVersion
```

MU-011 MUST preserve this rule.

---

### INV-011-008 — Recovery path remains durable and queryable

After repository/service recreation, topology, topologyVersion, device state and endpoint health must remain queryable through repository/read-port paths.

---

### INV-011-009 — Initial endpoint health is explicit after materialization

When `materializeEndpoint()` creates a new `EndpointNode`, it currently embeds:

```java
new EndpointHealth(HealthStatus.UNKNOWN, fact.observedAt(), "materialized from discovery fact")
```

Today that initial `UNKNOWN` health reaches `endpoint_health` only because `H2BaseTopologyRepository.save()` writes endpoint health when no durable row exists.

After `save()` becomes structural-only, `materializeEndpoint()` must explicitly write the initial endpoint health, or the durable health row will be absent until the first `HealthFact` arrives.

Recommended rule:

```text
Every newly materialized endpoint SHOULD have an explicit endpoint_health row
with UNKNOWN status and observedAt-derived lastSeenAt.
```

---

### INV-011-010 — Service-level endpoint-health durability must not regress silently

`BaseTopologyService.updateEndpointHealth(...)` currently mutates aggregate endpoint health and calls `repository.save(...)`. With the current H2 adapter, that persists to `endpoint_health` only because `save()` has a health side effect.

If MU-011 removes that side effect without replacing the durable write path, existing durability behavior can regress.

Required disposition:

```text
MU-011 MUST either:
  A. preserve service-level endpoint-health durability through an explicit narrow
     health persistence boundary; or
  B. deliberately reclassify BaseTopologyService.updateEndpointHealth(...) as
     aggregate-only seed behavior and update tests/acceptance criteria accordingly.

Recommended: A.
```

This does not require extracting `TopologyMaterializationStatePort`. It requires only the minimal explicit health persistence boundary needed to close `DEBT-007-001` safely.

---

### INV-011-011 — DeviceState is already separate

`saveDeviceState()` is called directly from `materializeDeviceState()` and from tests. It does not go through `save(HabitatBaseTopology)`.

No structural-save side effect currently writes device state.

MU-011 does not need to redesign device-state persistence, but it must preserve existing behavior.

---

### INV-011-012 — Mutation records are already separate

`appendMutationRecord()` and `findMutationRecords()` are distinct methods. They are not called from structural `save()`.

No change is required for mutation records in MU-011.

---

## 6. Implicit contracts in tests

### TEST-CONTRACT-011-001 — topology initialization requires rooms and zones

Current tests initialize base topology with non-empty room and zone lists. This is not incidental: `DefaultTopologyMaterializationService.chooseRoomId()` and `chooseZoneId()` throw if no room/zone exists.

MU-011 MUST NOT change this precondition.

---

### TEST-CONTRACT-011-002 — health survival test uses capability materialization as structural trigger

`TopologyMaterializationSeedTest.endpointHealthSurvivesSubsequentStructuralMutation` uses capability materialization as the later structural write that must not overwrite durable endpoint health.

After MU-011, this test must pass because structural save does not write endpoint health.

---

### TEST-CONTRACT-011-003 — in-memory repository has no health side effect

`InMemoryBaseTopologyRepository.save()` stores only the topology. It has no `endpoint_health` table and no durable health/state side effect.

MU-011 should not infer durable health semantics from the in-memory adapter.

---

### TEST-CONTRACT-011-004 — PersistenceMemorySeedTest currently relies on service-level endpoint health durability

`PersistenceMemorySeedTest` calls `BaseTopologyService.updateEndpointHealth(...)` and later asserts recovered durable endpoint health through the repository.

This is the critical hidden contract not fully captured in the first audit.

If `save()` becomes structural-only, this test will fail unless service-level health persistence is preserved explicitly or the test contract is revised by MIR decision.

Recommended preservation path:

```text
BaseTopologyService.updateEndpointHealth(...) should trigger explicit endpoint-health persistence
without relying on H2BaseTopologyRepository.save(...) side effects.
```

---

### TEST-CONTRACT-011-005 — TopologyVersionHardeningTest expects aggregate health update semantics

`TopologyVersionHardeningTest` expects `updateEndpointHealth(...)` to mutate endpoint health without advancing `topologyVersion`.

MU-011 MUST preserve:

```text
health update changes health observably;
topologyVersion remains unchanged.
```

If the implementation introduces explicit durable health persistence, it must not turn health updates into structural mutations.

---

### TEST-CONTRACT-011-006 — CoreSnapshotQueryService must continue to see durable health after recovery

Snapshot query behavior must continue to compose structural topology and durable operational state/health through `CoreSnapshotReadPort`.

---

## 7. Integration risks

### RISK-011-001 — Missing initial UNKNOWN health row

Removing health writes from `save()` means newly materialized endpoints will not automatically get an `endpoint_health` row.

Decision:

```text
Use explicit initial health write from materializeEndpoint().
```

---

### RISK-011-002 — Loss of service-level health durability

`BaseTopologyService.updateEndpointHealth(...)` currently relies on `save()` for durable H2 health persistence. Removing the side effect can break `PersistenceMemorySeedTest` and any caller using service-level health update as durable behavior.

Decision required in MIR:

```text
Recommended: preserve service-level durability through a narrow explicit health persistence boundary.
```

---

### RISK-011-003 — Scope creep into DEBT-007-002

MU-011 may be tempted to extract `TopologyMaterializationStatePort` while separating persistence concerns.

Rule:

```text
MU-011 MUST NOT extract TopologyMaterializationStatePort.
MU-011 MAY introduce only the minimal explicit health persistence boundary required
for DEBT-007-001, if accepted by MIR.
```

---

### RISK-011-004 — BaseTopologyRepository contamination

Adding health/state write methods to `BaseTopologyRepository` would convert the structural port into a mixed persistence port.

Rule:

```text
Do not add saveEndpointHealth(...) or saveDeviceState(...) to BaseTopologyRepository.
```

---

### RISK-011-005 — Stale embedded health in topology_json

Even after the boundary is separated, `topology_json` will still contain `EndpointNode.health` because health is part of the current aggregate shape.

This creates a representational asymmetry:

```text
topology_json endpoint health may be stale;
endpoint_health table is the durable operational-health source for query/recovery.
```

MU-011 should not redesign aggregate shape. The MIR/context must state that the durable operational-health source is `endpoint_health`, not embedded JSON health, for query/recovery semantics.

---

### RISK-011-006 — DeviceState confusion

`BaseTopologyService.updateDeviceState(...)` is in-process only. Durable device state is written through `H2BaseTopologyRepository.saveDeviceState(...)`.

MU-011 must not accidentally promote the in-process service map into production durable state.

---

## 8. Refactor gate

**Decision: Refactor in scope, bounded.**

The refactor is in scope if it remains limited to:

```text
1. Make H2BaseTopologyRepository.save(...) structural-only.
2. Move initial endpoint-health persistence to explicit endpoint materialization path.
3. Preserve service-level endpoint-health durability explicitly, either by a narrow
   health write port or an equivalent bounded mechanism accepted by MIR.
4. Add/update regression tests.
```

The following are out of scope and require separate MU or downstream artifact:

```text
- TopologyMaterializationStatePort extraction;
- final production persistence schema;
- migration framework;
- TemporalActs persistence;
- terminal request state persistence;
- Room/Zone materialization facts;
- TopologySpatialRelation;
- SC-B/NATS/SC-D integration.
```

No separate MU is required for the bounded persistence-boundary refactor. If implementation reveals that preserving service-level health durability requires broader port rearchitecture, implementation MUST stop and activate a corpus feedback trigger.

---

## 9. Recommended implementation strategy

### Step 1 — Make `H2BaseTopologyRepository.save(...)` structural-only

Remove endpoint-health iteration and conditional merge semantics from `save()`.

```java
@Override
public void save(HabitatBaseTopology topology) {
    Objects.requireNonNull(topology, "topology is required");
    String topologyJson = writeJson(topology);
    Instant capturedAt = Instant.now(clock);
    jdbcTemplate.update(
        """
            MERGE INTO topology_snapshots
            (habitat_id, topology_version, topology_json, captured_at)
            KEY (habitat_id)
            VALUES (?, ?, ?, ?)
            """,
        topology.habitatId(),
        topology.topologyVersion().value(),
        topologyJson,
        Timestamp.from(capturedAt)
    );
}
```

Acceptance condition:

```text
A structural save must not create, update, restore or overwrite endpoint_health rows.
```

---

### Step 2 — Explicit initial health write in `materializeEndpoint(...)`

After successful endpoint structural materialization, explicitly persist initial endpoint health.

Recommended shape:

```java
EndpointHealth initialHealth = new EndpointHealth(
    HealthStatus.UNKNOWN,
    fact.observedAt(),
    "materialized from discovery fact"
);

EndpointNode endpoint = new EndpointNode(
    ...,
    initialHealth,
    ...
);

TopologyMutationResult result = baseTopologyService.addEndpointWithResult(habitatId, endpoint);
repository.saveEndpointHealth(habitatId, endpointId, initialHealth);
```

Notes:

```text
- The structural mutation still advances topologyVersion.
- The initial health write must not advance topologyVersion.
- If structural mutation is rejected, initial health must not be written.
```

---

### Step 3 — Preserve service-level `updateEndpointHealth(...)` durability explicitly

Recommended strategy:

```text
Introduce a narrow explicit endpoint-health write boundary, e.g. EndpointHealthWritePort,
implemented by H2BaseTopologyRepository.
```

`BaseTopologyService.updateEndpointHealth(...)` should no longer rely on `repository.save(...)` side effects to persist durable endpoint health.

Possible implementation pattern:

```text
BaseTopologyService keeps BaseTopologyRepository for structural topology.
A second optional/narrow dependency writes endpoint health.
In production seed tests with H2, H2BaseTopologyRepository implements both.
In pure in-memory tests, the health-write dependency may be absent or in-memory.
```

Constraints:

```text
- Do not add health methods to BaseTopologyRepository.
- Do not introduce TopologyMaterializationStatePort in MU-011.
- Do not make BaseTopologyService depend on H2BaseTopologyRepository concrete type.
- Do not advance topologyVersion when health is written.
```

If this cannot be implemented cleanly inside MU-011, the MIR must decide whether `updateEndpointHealth(...)` becomes aggregate-only seed behavior and update tests accordingly. That is not the recommended path.

---

### Step 4 — Preserve device state and mutation records as already separated

No change required for:

```text
saveDeviceState(...)
findDeviceState(...)
appendMutationRecord(...)
findMutationRecords(...)
```

Only update them if tests reveal accidental coupling.

---

### Step 5 — Add regression tests

Required tests or equivalent coverage:

```text
TEST-011-A — structuralSaveDoesNotTouchEndpointHealthTable
  1. materialize device + endpoint;
  2. ensure endpoint_health row exists or write one explicitly;
  3. mutate/remove endpoint_health row in controlled test setup OR write DEGRADED health;
  4. trigger structural save through capability materialization;
  5. assert structural save did not create/overwrite endpoint_health.

TEST-011-B — initialEndpointHealthIsWrittenOnMaterialization
  1. materialize endpoint;
  2. before any HealthFact, assert findEndpointHealth(...) returns UNKNOWN;
  3. assert lastSeenAt equals fact.observedAt().

TEST-011-C — serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect
  1. create topology with endpoint;
  2. call BaseTopologyService.updateEndpointHealth(..., DEGRADED);
  3. assert topologyVersion unchanged;
  4. assert repository.findEndpointHealth(...) returns DEGRADED;
  5. recreate repository/service;
  6. assert recovered query still sees DEGRADED.

TEST-011-D — healthWriteDoesNotAdvanceTopologyVersion
  1. write HealthFact or service-level health update;
  2. assert topologyVersion unchanged.
```

All current repository tests must remain passing.

---

## 10. Tests that must remain passing

All current tests reported by the repository test suite MUST remain passing.

Highest-risk tests for MU-011:

```text
TopologyMaterializationSeedTest
  - materializesTopologyFactsThroughBaseTopologyServiceAndDurableRepository
  - endpointHealthSurvivesSubsequentStructuralMutation

PersistenceMemorySeedTest
  - ac001ToAc025TopologyMemorySurvivesAdapterAndServiceRecreation

CoreSnapshotQuerySeedTest
  - query visibility and durable health/state reads after recovery

ScCoreKernelHardeningTest
  - composed durable topology/state/health recovery path

TopologyVersionHardeningTest
  - endpoint health update does not advance topologyVersion
  - structural and non-structural behavior are covered together

BaseTopologyServiceTest
  - existing structural topology behavior and boundary purity
```

---

## 11. Non-goals for MU-011

```text
- Extracting TopologyMaterializationStatePort.
- Implementing MU-012.
- Adding saveEndpointHealth(...) or saveDeviceState(...) to BaseTopologyRepository.
- Production storage selection.
- Final persistence schema SDD.
- Flyway/Liquibase migrations.
- TemporalActs schema.
- Terminal request state schema.
- RoomDiscoveryFact / ZoneDiscoveryFact.
- DeviceRoomAssignmentFact.
- TopologySpatialRelation(LOCATED_IN).
- Semantic spatial relations.
- Room/Zone persistence separation from topology snapshot beyond current JSON snapshot behavior.
- NATS / JetStream.
- SC-B runtime.
- SC-D adapter integration.
- Projection / Effective View.
- Hub, Session, Identity, Authority or Policy concerns.
```

---

## 12. Compatibility constraints

```text
COMPAT-011-001:
  Existing public behavior required by tests must remain valid unless the MIR explicitly revises it.

COMPAT-011-002:
  BaseTopologyRepository constructor usage in tests and services must remain manageable. Any new constructor dependency must be reflected in context.md and codex-prompt.md.

COMPAT-011-003:
  H2BaseTopologyRepository may implement additional narrow interfaces, but SC-C domain services must not depend on the concrete H2 class.

COMPAT-011-004:
  CoreSnapshotQueryService must continue to use CoreSnapshotReadPort.

COMPAT-011-005:
  The current H2 schema may remain unchanged for MU-011. New tables are not required.

COMPAT-011-006:
  topology_snapshots.topology_json may still embed endpoint health as part of the current aggregate shape, but endpoint_health is the durable operational-health source for query/recovery.
```

---

## 13. Operational non-goals

```text
OP-NONGOAL-011-001:
  Do not solve materializer-to-repository concrete coupling in this MU.

OP-NONGOAL-011-002:
  Do not generalize H2 persistence into production storage doctrine.

OP-NONGOAL-011-003:
  Do not introduce migration tooling.

OP-NONGOAL-011-004:
  Do not change RoomNode / ZoneNode behavior.

OP-NONGOAL-011-005:
  Do not alter canonical ID strategy.

OP-NONGOAL-011-006:
  Do not introduce broker, adapter or projection concerns.
```

---

## 14. Corpus feedback triggers

### CFT-011-001 — Room/Zone partial implementation exists

Finding:

```text
RoomNode, ZoneNode, HabitatBaseTopology.rooms and HabitatBaseTopology.zones already exist.
DefaultTopologyMaterializationService already requires non-empty rooms/zones.
```

Action:

```text
Record in SYNC/INDEX or future PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001 that Room/Zone model-level seed exists.
This does not close MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001.
```

---

### CFT-011-002 — Possible need for a narrow endpoint-health write port

Finding:

```text
Strict structural-only save conflicts with existing service-level endpoint-health durability unless explicit health persistence is introduced elsewhere.
```

Action:

```text
MIR-011 must either authorize a narrow EndpointHealthWritePort/equivalent or explicitly revise the service-level durability contract.
```

Recommended resolution:

```text
Authorize the narrow health persistence boundary inside MU-011 as part of DEBT-007-001 closure.
Keep TopologyMaterializationStatePort extraction deferred to MU-012.
```

---

## 15. Disposition

```text
Recommended decision: Proceed after architect approval.
Implementation mode:  Code change required — target Validated L4.
Surface category:     Non-Greenfield — full audit completed.
```

The core implementation is safe if the MIR accepts these decisions:

```text
D-CSA-011-001:
  H2BaseTopologyRepository.save(...) becomes structural-only.

D-CSA-011-002:
  Initial endpoint health is written explicitly by endpoint materialization.

D-CSA-011-003:
  Service-level updateEndpointHealth durability is preserved through a narrow explicit health persistence boundary or equivalent accepted mechanism.

D-CSA-011-004:
  TopologyMaterializationStatePort extraction remains out of scope for MU-011.

D-CSA-011-005:
  endpoint_health is the durable operational-health source for query/recovery; embedded aggregate health in topology_json is not the durable operational-health authority.
```

---

## 16. Required propagation into execution package

`context.md` MUST include:

```text
- structural save must be structural-only;
- endpoint health must not be persisted as a hidden side effect of structural save;
- initial UNKNOWN health must be written explicitly on endpoint materialization;
- service-level updateEndpointHealth durability path must be preserved;
- BaseTopologyRepository must remain structural;
- CoreSnapshotReadPort remains the query/read boundary;
- TopologyMaterializationStatePort remains MU-012 scope;
- RoomNode/ZoneNode are existing model-level types but not MU-013 completion.
```

`acceptance-map.md` MUST include explicit ACs for:

```text
- structural saves do not touch endpoint_health;
- endpointHealthSurvivesSubsequentStructuralMutation;
- initialEndpointHealthIsWrittenOnMaterialization;
- serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect;
- health writes do not advance topologyVersion;
- all existing tests remain passing;
- no SC-B/SC-D/Projection/Authority/Policy dependency introduced.
```

`codex-prompt.md` MUST NOT be authored until this audit is approved.

---

## 17. Living artifact status

This audit is a living artifact during MU-011 implementation.

If implementation reveals additional coupling between structural save and health/state writes not captured here, implementation MUST stop, this audit MUST be updated, and the Executive Package MUST be regenerated or patched before continuing.

```text
Audit status after merge:
  v0.1.1-merged — ready for architect review.
```

