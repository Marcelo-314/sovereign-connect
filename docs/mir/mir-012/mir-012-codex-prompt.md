# Codex Prompt — MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001

```text
MIR:              MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001 v0.1.0-draft
MU:               MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001
Operational Slot: MU-012
Audit:            CSA-MU-012 v0.1.1-merged
Branch:           feat/sc-c-materialization-state-port
Expected commit:  refactor(sc-c): extract topology materialization state port
```

---

## 0. Mandatory reading order

Before changing code, read:

```text
docs/mir/mir-012/MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001.md
docs/mir/mir-012/code-surface-audit.md
docs/mir/mir-012/acceptance-map.md
docs/mir/mir-012/context.md
```

Treat those files as authoritative. Do not implement outside MU-012 scope.

---

## 1. Objective

Remove the direct dependency of `DefaultTopologyMaterializationService` on
`H2BaseTopologyRepository` by introducing `TopologyMaterializationStatePort`.

After your changes:

```text
DefaultTopologyMaterializationService imports TopologyMaterializationStatePort.
DefaultTopologyMaterializationService does NOT import H2BaseTopologyRepository.
DefaultTopologyMaterializationService has no field typed as H2BaseTopologyRepository.
DefaultTopologyMaterializationService has no constructor parameter typed as H2BaseTopologyRepository.
```

This closes DEBT-007-002. No materialization semantics change.

---

## 2. Required changes

### 2.1 Create TopologyMaterializationStatePort

Create:

```text
src/main/java/com/sovereign/connect/core/topology/port/TopologyMaterializationStatePort.java
```

Exact content:

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

Do NOT add `save(HabitatBaseTopology)`, `appendMutationRecord`, `findSnapshot`,
JDBC/SQL imports, or any Room/Zone/SC-D/SC-B method to this interface.

---

### 2.2 Add interface to H2BaseTopologyRepository

Update the `implements` clause only. Do not change any method bodies.

Before:

```java
public class H2BaseTopologyRepository
    implements BaseTopologyRepository, CoreSnapshotReadPort, EndpointHealthWritePort
```

After:

```java
public class H2BaseTopologyRepository
    implements BaseTopologyRepository, CoreSnapshotReadPort,
               EndpointHealthWritePort, TopologyMaterializationStatePort
```

All four required methods (`findByHabitatId`, `findCurrentVersion`,
`saveDeviceState`, `saveEndpointHealth`) already exist on `H2BaseTopologyRepository`
with correct signatures. No body changes should be necessary.

---

### 2.3 Refactor DefaultTopologyMaterializationService

This is the primary change. Make exactly these modifications:

**Remove import (line 3):**

```java
// REMOVE:
import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;

// ADD:
import com.sovereign.connect.core.topology.port.TopologyMaterializationStatePort;
```

**Change field declaration (line 40):**

Before:

```java
private final H2BaseTopologyRepository repository;
```

After (rename from `repository` to `statePort`):

```java
private final TopologyMaterializationStatePort statePort;
```

**Change constructor (lines 44–53):**

Before:

```java
public DefaultTopologyMaterializationService(
    BaseTopologyService baseTopologyService,
    H2BaseTopologyRepository repository,
    Predicate<String> admittedAdapterPredicate,
    Clock clock
) {
    this.baseTopologyService = Objects.requireNonNull(baseTopologyService, "baseTopologyService is required");
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.admittedAdapterPredicate = Objects.requireNonNull(admittedAdapterPredicate, "admittedAdapterPredicate is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
}
```

After:

```java
public DefaultTopologyMaterializationService(
    BaseTopologyService baseTopologyService,
    TopologyMaterializationStatePort statePort,
    Predicate<String> admittedAdapterPredicate,
    Clock clock
) {
    this.baseTopologyService = Objects.requireNonNull(baseTopologyService, "baseTopologyService is required");
    this.statePort = Objects.requireNonNull(statePort, "statePort is required");
    this.admittedAdapterPredicate = Objects.requireNonNull(admittedAdapterPredicate, "admittedAdapterPredicate is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
}
```

**Update all eight `repository.` call sites inside the class to `statePort.`:**

The eight call sites (see context.md §4 for line numbers) use the old field name
`repository`. After renaming the field to `statePort`, each `repository.X()` call
becomes `statePort.X()`. The method names are identical — only the receiver name
changes:

```text
repository.saveEndpointHealth(...)   →   statePort.saveEndpointHealth(...)
repository.saveDeviceState(...)      →   statePort.saveDeviceState(...)
repository.findByHabitatId(...)      →   statePort.findByHabitatId(...)
repository.findCurrentVersion(...)   →   statePort.findCurrentVersion(...)
```

No other changes to method bodies.

---

### 2.4 Test call sites — compilation note

Tests that construct `DefaultTopologyMaterializationService` pass an
`H2BaseTopologyRepository` instance as the second argument. Because H2 now
implements `TopologyMaterializationStatePort`, Java accepts these call sites
without changes.

Known call sites from audit (context.md §8):

```text
TopologyMaterializationSeedTest.java:58
TopologyMaterializationSeedTest.java:191
TopologyMaterializationSeedTest.java:266
PersistenceBoundaryHardeningTest.java:105
```

These compile without change. Do not add new constructors or overloads.
The boundary assertion (§2.5 below) verifies the constructor DECLARATION, not
what gets passed at call sites.

---

### 2.5 Add boundary regression assertions

`PersistenceMemorySeedTest` already contains `constructorSurface(...)` helper
and boundary assertions for `BaseTopologyService`. Extend the existing assertion
block to also cover `DefaultTopologyMaterializationService`.

Add these assertions in the existing boundary assertion section of
`PersistenceMemorySeedTest` (currently around lines 149–158):

```java
// AC-001 / AC-002: materializer field and constructor must not expose H2
assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getDeclaredFields())
    .map(field -> field.getType().getName())
    .toList())
    .noneMatch(type -> type.contains("H2BaseTopologyRepository"));

assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getConstructors())
    .map(this::constructorSurface).toList())
    .noneMatch(surface -> surface.contains("H2BaseTopologyRepository"));

// AC-003 / AC-004: H2 implements the new port
assertThat(H2BaseTopologyRepository.class.getInterfaces())
    .extracting(Class::getSimpleName)
    .contains("TopologyMaterializationStatePort");
```

Add the required import at the top of `PersistenceMemorySeedTest`:

```java
import com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService;
import com.sovereign.connect.core.topology.port.TopologyMaterializationStatePort;
```

The `constructorSurface` helper is already present. Do not add it again.

---

### 2.6 Health write paths — do NOT merge

MU-011 established two independent health write paths. Preserve both:

```text
Path A (BaseTopologyService → EndpointHealthWritePort):
  BaseTopologyService.updateEndpointHealth()
  → healthWritePort.saveEndpointHealth()
  → H2BaseTopologyRepository (as EndpointHealthWritePort)
  DO NOT CHANGE THIS PATH.

Path B (DefaultTopologyMaterializationService → TopologyMaterializationStatePort):
  materializeEndpoint() → statePort.saveEndpointHealth()   [initial UNKNOWN health]
  materializeHealth()   → statePort.saveEndpointHealth()   [HealthFact health]
  → H2BaseTopologyRepository (as TopologyMaterializationStatePort)
  THIS IS WHAT CHANGES (receiver name only: repository → statePort).
```

Both paths call the same underlying H2 method. They are parallel and independent.
Do not unify `EndpointHealthWritePort` and `TopologyMaterializationStatePort`.
Do not change `BaseTopologyService` in this MU.

---

## 3. Prohibited changes

Do not:

```text
- add save(HabitatBaseTopology) to TopologyMaterializationStatePort
- add any JDBC / SQL / DataSource / ObjectMapper imports to the materializer
- change H2BaseTopologyRepository method bodies
- modify BaseTopologyService constructors or fields
- implement InMemoryBaseTopologyRepository as TopologyMaterializationStatePort
- add new constructors or overloads to DefaultTopologyMaterializationService
- implement RoomDiscoveryFact, ZoneDiscoveryFact or TopologySpatialRelation
- introduce SC-B, SC-D, NATS, JetStream, Vert.x, Projection, Hub, Session,
  Identity, Authority, Policy or Surface dependencies
- weaken existing tests
```

---

## 4. Summary of files to change

```text
NEW:
  src/main/java/com/sovereign/connect/core/topology/port/TopologyMaterializationStatePort.java

MODIFY:
  src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
    — implements clause only

  src/main/java/com/sovereign/connect/core/topology/materialization/DefaultTopologyMaterializationService.java
    — remove H2 import
    — add port import
    — change field type and name
    — change constructor param type and name
    — update 8 repository. → statePort. call sites

  src/test/java/com/sovereign/connect/core/topology/PersistenceMemorySeedTest.java
    — add imports for DefaultTopologyMaterializationService and TopologyMaterializationStatePort
    — add 3 boundary assertions in the existing assertion block
```

---

## 5. Acceptance criteria

Satisfy AC-001 through AC-023 from `docs/mir/mir-012/acceptance-map.md`.

Critical ACs:

```text
AC-001: DefaultTopologyMaterializationService does not import H2BaseTopologyRepository.
AC-002: DefaultTopologyMaterializationService has no H2BaseTopologyRepository field.
AC-003: TopologyMaterializationStatePort exists in the SC-C port package.
AC-004: H2BaseTopologyRepository implements TopologyMaterializationStatePort.
AC-010: Initial UNKNOWN endpoint health (MU-011) remains explicitly persisted via statePort.
AC-016: PersistenceBoundaryHardeningTest remains passing.
AC-017: Full mvn test passes.
```

---

## 6. Verification command

```bash
mvn test
```

Baseline: 28 tests, 0 failures, 0 errors, 0 skipped.
New test total after §2.5 additions: same count or higher if assertions split
into a new test method.

---

## 7. Implementation report

After implementation, fill `docs/mir/mir-012/implementation-report.md`.

Required content:

```text
- PASS / FAIL / BLOCKED
- branch and commit hash
- test command and result
- total tests / failures / errors / skipped
- production files changed
- test files changed
- final TopologyMaterializationStatePort shape (4 methods)
- confirmation DefaultTopologyMaterializationService has no H2 import / field / constructor
- confirmation MU-011 persistence boundary remains intact
- confirmation both health write paths (EndpointHealthWritePort and TopologyMaterializationStatePort) are separate
- AC-001 through AC-023 result table
- remaining deferred debt
```

---

## 8. Stop conditions

Stop and report `BLOCKED` if:

```text
The materializer needs save(HabitatBaseTopology) directly.
H2 method bodies require semantic changes beyond the implements clause.
A broad generic repository abstraction seems necessary.
Room/Zone or SC-D scope becomes necessary for tests to pass.
New code introduces H2/JDBC/DataSource/ObjectMapper into the materializer.
The health write paths need to be merged to make tests pass.
```

Update `docs/mir/mir-012/code-surface-audit.md` and request architect review.
Do not continue automatically.
