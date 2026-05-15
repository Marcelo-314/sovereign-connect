# Code Surface Audit — MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001

```text
Audit ID:            CSA-MU-012
Version:             v0.1.1-merged
Status:              Draft / ready for architect review
MIR:                 MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001 v0.1.0-draft
MU:                  MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001
Operational Slot:    MU-012
Closes:              DEBT-007-002
Repository state:    feat/sc-c-persistence-boundary-hardening (post-MU-011, 2026-05-14)
Baseline HEAD:       7e73e28 fix(sc-c): separate structural topology and health persistence
Baseline tests:      28 tests, 0 failures, 0 errors, 0 skipped
Audited by:          Architect review + merged assistant review
Date:                2026-05-14
Surface category:    Non-Greenfield — full audit required
```

---

## 1. Purpose

This audit identifies all concrete adapter dependencies in
`DefaultTopologyMaterializationService`, determines the exact operation set
needed by the materializer, evaluates whether existing ports can be reused,
and recommends the minimal port shape for `TopologyMaterializationStatePort`.

The goal of MU-012 is to close `DEBT-007-002` by removing direct dependency from
SC-C materialization code to the concrete H2 persistence adapter, without changing
materialization semantics, persistence semantics, topologyVersion behavior, or
SC-C / SC-B / SC-D boundaries.

---

## 2. Affected surface

```text
Package: com.sovereign.connect.core.topology.materialization
  DefaultTopologyMaterializationService.java    — PRIMARY surface
    import H2BaseTopologyRepository             — to remove
    field: H2BaseTopologyRepository repository  — to change to port type
    constructor param: H2BaseTopologyRepository — to change to port type
    repository.findByHabitatId(habitatId)       — topology read
    repository.findCurrentVersion(habitatId)    — topologyVersion read (multiple call sites)
    repository.saveDeviceState(...)             — device state write
    repository.saveEndpointHealth(...)          — endpoint health write

Package: com.sovereign.connect.core.topology.port
  TopologyMaterializationStatePort.java         — to create

Package: com.sovereign.connect.adapter.persistence
  H2BaseTopologyRepository.java                 — to add interface to implements clause

Tests / regression surface:
  TopologyMaterializationSeedTest.java          — materializer construction and fact paths
  PersistenceBoundaryHardeningTest.java         — materializer construction after MU-011
  PersistenceMemorySeedTest.java                — boundary assertions to extend
  CoreSnapshotQuerySeedTest.java                — must remain passing
  ScCoreKernelHardeningTest.java                — must remain passing
  TopologyVersionHardeningTest.java             — must remain passing
  BaseTopologyServiceTest.java                  — must remain passing
```

---

## 3. Complete inventory of H2 dependencies in the materializer

### 3.1 Import

```java
import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
```

**Must be removed.**

After MU-012, no `com.sovereign.connect.adapter.persistence.*` type should be
imported by `DefaultTopologyMaterializationService`.

### 3.2 Field declaration

```java
private final H2BaseTopologyRepository repository;
```

**Must change to:**

```java
private final TopologyMaterializationStatePort statePort;
```

or an equivalent field name with type `TopologyMaterializationStatePort`.

### 3.3 Constructor parameter

```java
public DefaultTopologyMaterializationService(
    BaseTopologyService baseTopologyService,
    H2BaseTopologyRepository repository,
    Predicate<String> admittedAdapterPredicate,
    Clock clock
)
```

**Must change the second parameter type to:**

```java
TopologyMaterializationStatePort
```

The public constructor surface must not expose `H2BaseTopologyRepository` after MU-012.

### 3.4 All `repository.` call sites

The current materializer depends on the H2 repository for exactly four operation families:

| Call family | Operation category | Port inclusion |
|---|---|---|
| `findByHabitatId(habitatId)` | topology read | yes |
| `findCurrentVersion(habitatId)` | topologyVersion read | yes |
| `saveDeviceState(habitatId, deviceId, state)` | device state write | yes |
| `saveEndpointHealth(habitatId, endpointId, health)` | endpoint health write | yes |

These are the only operations the new port should expose.

---

## 4. Required port operation set

The materializer needs exactly four operations:

```java
Optional<HabitatBaseTopology> findByHabitatId(String habitatId);

Optional<TopologyVersion> findCurrentVersion(String habitatId);

void saveDeviceState(String habitatId, String deviceId, Map<String, Object> state);

void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health);
```

The port MUST NOT expose structural topology writes.

Specifically, `TopologyMaterializationStatePort` MUST NOT include:

```text
save(HabitatBaseTopology)
appendMutationRecord(...)
findSnapshot(...)
createSchema(...)
migration methods
JDBC/DataSource/ObjectMapper/SQL methods
Room/Zone materialization methods
SC-D adapter methods
SC-B bus methods
```

---

## 5. Existing port coverage — reuse analysis

### 5.1 `BaseTopologyRepository`

Existing shape:

```java
void save(HabitatBaseTopology topology)
Optional<HabitatBaseTopology> findByHabitatId(String habitatId)
Optional<TopologyVersion> findCurrentVersion(String habitatId)
```

`BaseTopologyRepository` covers two of the four needed operations. However, making the
materializer depend on `BaseTopologyRepository` would expose the structural mutation
boundary to the materializer. That is precisely what MU-012 is trying to avoid.

`BaseTopologyService` remains the owner of accepted structural mutation calls.

**Verdict:** do not reuse `BaseTopologyRepository` directly in the materializer.

### 5.2 `CoreSnapshotReadPort`

Existing shape:

```java
Optional<BaseTopologySnapshot> findSnapshot(String habitatId)
Optional<TopologyVersion> findCurrentVersion(String habitatId)
Optional<HabitatBaseTopology> findTopology(String habitatId)
Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId)
Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId)
```

`CoreSnapshotReadPort` covers the two read needs but does not include the write operations
required by materialization. It is also the query/read boundary, not the materialization
state boundary.

**Verdict:** do not split the materializer across `CoreSnapshotReadPort` plus separate
write ports in MU-012.

### 5.3 `EndpointHealthWritePort`

Existing from MU-011:

```java
void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health)
```

`EndpointHealthWritePort` covers one operation. It exists to let `BaseTopologyService`
preserve service-level endpoint health durability after MU-011 removed health writes
from structural `save(...)`.

**Verdict:** do not make the materializer depend on `EndpointHealthWritePort` separately.
Include `saveEndpointHealth(...)` in the cohesive materialization port. The two ports
may share a method signature, but they serve different consumers and boundaries.

---

## 6. Recommended port design

### 6.1 Decision — Single cohesive port

Use a single cohesive `TopologyMaterializationStatePort`.

Rationale:

```text
- The materializer is the sole consumer.
- The four operations are materialization-scoped.
- A single port avoids scattering materializer dependencies across multiple boundaries.
- It prevents exposure of structural save(HabitatBaseTopology) to the materializer.
- It is easy to mock if future tests need a non-H2 implementation.
```

Recommended interface:

```java
package com.sovereign.connect.core.topology.port;

import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.TopologyVersion;

import java.util.Map;
import java.util.Optional;

public interface TopologyMaterializationStatePort {

    Optional<HabitatBaseTopology> findByHabitatId(String habitatId);

    Optional<TopologyVersion> findCurrentVersion(String habitatId);

    void saveDeviceState(String habitatId, String deviceId, Map<String, Object> state);

    void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health);
}
```

### 6.2 H2 adapter implementation

`H2BaseTopologyRepository` should implement `TopologyMaterializationStatePort`.

All four required methods already exist on `H2BaseTopologyRepository`. MU-012 should
only add the interface to the implements clause; it should not change these method bodies.

```java
public class H2BaseTopologyRepository
    implements BaseTopologyRepository,
               CoreSnapshotReadPort,
               EndpointHealthWritePort,
               TopologyMaterializationStatePort
```

### 6.3 In-memory implementation

`InMemoryBaseTopologyRepository` does not need to implement `TopologyMaterializationStatePort`
in MU-012.

Current materializer tests use `H2BaseTopologyRepository`. Existing in-memory tests use
`BaseTopologyService` and do not construct `DefaultTopologyMaterializationService`.

If a future MU introduces in-memory materializer tests, a dedicated test adapter or
in-memory implementation may be added then. That is not required for MU-012.

### 6.4 Device state write location

Keep `saveDeviceState(...)` in `TopologyMaterializationStatePort`.

Do not introduce a separate `DeviceStateWritePort` in MU-012. The materializer is the
current caller, and splitting the dependency would add complexity without improving
ownership.

### 6.5 Read method naming

Use `findByHabitatId(...)`, matching current `BaseTopologyRepository` naming and current
materializer call sites.

Do not rename to `findTopologyForMaterialization(...)` in MU-012 unless implementation
reveals a hard reason. A rename would add churn without closing additional risk.

---

## 7. Constructor call sites to verify

After the constructor second parameter changes from `H2BaseTopologyRepository` to
`TopologyMaterializationStatePort`, existing calls that pass an `H2BaseTopologyRepository`
instance should continue to compile once H2 implements the new port.

All constructor call sites MUST be verified.

They may not require source edits if the H2 repository instance is already assignable to
`TopologyMaterializationStatePort`.

Known construction surfaces include:

```text
TopologyMaterializationSeedTest.java
PersistenceBoundaryHardeningTest.java
any application/service wiring that constructs DefaultTopologyMaterializationService
```

---

## 8. Existing invariants to preserve

### INV-012-001 — H2 method bodies unchanged

`H2BaseTopologyRepository` gains a new `implements` declaration only. The four methods
it contributes to `TopologyMaterializationStatePort` already exist and their bodies
must not change as part of MU-012.

### INV-012-002 — MU-011 persistence boundary remains intact

`H2BaseTopologyRepository.save(HabitatBaseTopology)` remains structural-only.

`TopologyMaterializationStatePort.saveEndpointHealth(...)` remains a distinct explicit
health write method.

MU-012 MUST NOT reintroduce health writes into structural topology save.

### INV-012-003 — `EndpointHealthWritePort` remains for `BaseTopologyService`

`BaseTopologyService` continues to use `EndpointHealthWritePort`.

`TopologyMaterializationStatePort` introduces `saveEndpointHealth(...)` for the materializer.
The method signature may be shared, but the ports serve different boundaries.

### INV-012-004 — topologyVersion advancement rules unchanged

MU-012 is a structural dependency refactor. It MUST NOT alter:

```text
- accepted structural mutation advances topologyVersion;
- rejected mutation does not advance topologyVersion;
- state writes do not advance topologyVersion;
- health writes do not advance topologyVersion;
- duplicate/invalid/unauthorized facts do not advance topologyVersion.
```

### INV-012-005 — BaseTopologyService remains structural mutation boundary

The materializer may call `BaseTopologyService` for accepted structural mutations.
It MUST NOT gain direct structural write access through the new port.

---

## 9. Boundary assertions required

`PersistenceMemorySeedTest` already contains an assertion preventing `BaseTopologyService`
from exposing `H2BaseTopologyRepository` in its constructor surface.

MU-012 should extend this boundary protection to `DefaultTopologyMaterializationService`.

Minimum assertion:

```java
assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getConstructors())
    .map(this::constructorSurface)
    .toList())
    .noneMatch(surface -> surface.contains("H2BaseTopologyRepository"));
```

Stronger recommended assertion:

```text
DefaultTopologyMaterializationService MUST expose no public constructor parameter
and no declared field typed as H2BaseTopologyRepository.
```

The regression should inspect:

```text
- public constructor parameter types;
- declared field types.
```

---

## 10. No technology leakage rule

After MU-012, `DefaultTopologyMaterializationService` MUST NOT reference:

```text
H2BaseTopologyRepository
JdbcTemplate
DataSource
ObjectMapper
SQL strings
H2-specific types
adapter.persistence package types
```

It may depend on:

```text
BaseTopologyService
TopologyMaterializationStatePort
Predicate<String>
Clock
SC-C topology/materialization model types
```

This prevents replacing one concrete dependency with another technology leak.

---

## 11. Refactor gate decision

**Decision: In-scope, no separate MU required.**

The refactor is bounded:

```text
1. Create TopologyMaterializationStatePort.
2. Add TopologyMaterializationStatePort to H2BaseTopologyRepository implements clause.
3. Change DefaultTopologyMaterializationService field and constructor param type.
4. Remove H2 import from materializer.
5. Verify constructor call sites.
6. Extend boundary assertion to cover DefaultTopologyMaterializationService.
7. Run full test suite.
```

No persistence behavior changes. No schema changes. No new tables. No model changes. No
Room/Zone expansion. No SC-D integration. No SC-B/NATS integration.

---

## 12. Non-goals for this MU

```text
- Changing H2BaseTopologyRepository method bodies.
- Changing H2BaseTopologyRepository.save(HabitatBaseTopology).
- Changing persistence semantics.
- Adding save(HabitatBaseTopology) to TopologyMaterializationStatePort.
- Reusing BaseTopologyRepository as materializer dependency.
- Splitting saveDeviceState into a separate DeviceStateWritePort.
- Implementing TopologySpatialRelation or LOCATED_IN.
- Implementing RoomDiscoveryFact / ZoneDiscoveryFact / DeviceRoomAssignmentFact.
- Production schema design.
- TemporalActs or terminal request state.
- SC-B, SC-D, NATS, JetStream, Projection, Hub, Session, Identity, Authority or Policy.
```

---

## 13. Required tests

Tests to add or extend:

```text
PersistenceMemorySeedTest:
  Extend existing boundary assertion to include DefaultTopologyMaterializationService.
  Assert no constructor parameter and no declared field uses H2BaseTopologyRepository.
```

All current repository tests must remain passing:

```text
BaseTopologyServiceTest
TopologyVersionHardeningTest
PersistenceMemorySeedTest
CoreSnapshotQuerySeedTest
ScCoreKernelHardeningTest
TopologyMaterializationSeedTest
PersistenceBoundaryHardeningTest
```

Baseline evidence after MU-011:

```text
Tests run: 28
Failures: 0
Errors: 0
Skipped: 0
```

---

## 14. Acceptance mapping preview

This audit supports the following expected MU-012 acceptance criteria:

```text
AC-001: All existing tests remain passing.
AC-002: DefaultTopologyMaterializationService no longer imports H2BaseTopologyRepository.
AC-003: DefaultTopologyMaterializationService has no field typed as H2BaseTopologyRepository.
AC-004: DefaultTopologyMaterializationService has no constructor parameter typed as H2BaseTopologyRepository.
AC-005: TopologyMaterializationStatePort exists in core topology port package.
AC-006: TopologyMaterializationStatePort exposes exactly the materializer-scoped operation set.
AC-007: TopologyMaterializationStatePort does not expose save(HabitatBaseTopology).
AC-008: H2BaseTopologyRepository implements TopologyMaterializationStatePort.
AC-009: H2BaseTopologyRepository method bodies are not changed for this MU except if required by compilation.
AC-010: MU-011 structural-only save invariant remains intact.
AC-011: Materialization behavior remains unchanged.
AC-012: topologyVersion advancement/non-advancement behavior remains unchanged.
AC-013: Boundary regression asserts no H2 repository exposure from the materializer.
AC-014: No SC-B / SC-D / Projection / Authority / Policy / Session / Hub dependency is introduced.
AC-015: Implementation report records any unexpected coupling discovered during implementation.
```

---

## 15. Implementation strategy

Step 1: Create `TopologyMaterializationStatePort` in:

```text
com.sovereign.connect.core.topology.port
```

Step 2: Add `TopologyMaterializationStatePort` to `H2BaseTopologyRepository` implements clause.

Step 3: Change `DefaultTopologyMaterializationService`:

```text
- remove import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
- change field type to TopologyMaterializationStatePort;
- change constructor second parameter type to TopologyMaterializationStatePort;
- keep all materialization logic unchanged;
- do not change BaseTopologyService calls;
- do not change H2 method bodies.
```

Step 4: Verify all construction sites still compile.

Step 5: Extend boundary assertion in `PersistenceMemorySeedTest` or equivalent hardening test.

Step 6: Run full test suite.

Expected result:

```text
At least baseline 28 tests remain passing.
0 failures.
0 errors.
```

---

## 16. Working tree hygiene

Before closing MU-012, the implementation report should confirm:

```text
- no unrelated IDE files are included;
- no unrelated docs/mir artifacts are modified;
- no MU-011 evidence files are accidentally rewritten;
- only MU-012 source/test/docs changes are committed.
```

If unrelated changes appear in `git status --short`, they must be removed from the
MU-012 commit or explicitly justified in the implementation report.

---

## 17. Disposition

```text
Recommended decision: Proceed to execution package.
Surface category:     Non-Greenfield.
Implementation mode:  Code change required — expected Validated L4.
Port shape:           TopologyMaterializationStatePort with 4 methods.
H2 implementation:    add implements clause, no method body changes.
Test impact:          boundary assertion extension + full regression.
Risk:                 Low — structural refactor, no behavior changes intended.
```

---

## 18. Living artifact status

This audit is a living artifact.

If implementation reveals an additional dependency on `H2BaseTopologyRepository`, H2/JDBC,
state/health persistence semantics, structural saves or materialization behavior not listed
here, this audit MUST be updated and architect-reviewed before the implementation prompt is
authorized or continued.
