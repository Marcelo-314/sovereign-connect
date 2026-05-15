# Context — MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001

```text
Document ID:        CTX-SOV-SC-C-MATERIALIZATION-STATE-PORT-001
MIR:                MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001 v0.1.0-draft
MU:                 MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001
Operational Slot:   MU-012
Evidence Path:      docs/mir/mir-012/
Audit:              CSA-MU-012 v0.1.1-merged
Status:             Execution context
```

---

## 1. Objective

Close `DEBT-007-002` by extracting `TopologyMaterializationStatePort` so that
`DefaultTopologyMaterializationService` depends on an SC-C port, not on the
concrete H2 persistence adapter.

Canonical objective:

```text
DefaultTopologyMaterializationService MUST depend on SC-C ports,
not on H2BaseTopologyRepository.
```

---

## 2. Baseline state

```text
Branch:        feat/sc-c-persistence-boundary-hardening
Baseline HEAD: 7e73e28 fix(sc-c): separate structural topology and health persistence
Baseline tests: 28 tests, 0 failures, 0 errors, 0 skipped
```

MU-011 is already validated. Its MU-011 persistence separation invariants MUST
remain fully intact after MU-012.

---

## 3. Current problem — exact code to change

### 3.1 Import to remove (line 3)

```java
import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
```

### 3.2 Field to change (line 40)

Current:

```java
private final H2BaseTopologyRepository repository;
```

After MU-012:

```java
private final TopologyMaterializationStatePort statePort;
```

Note: renaming the field from `repository` to `statePort` (or `materializationState`)
is recommended. A port is not a repository. All call sites inside the class that
reference the old field name must be updated to the new name.

### 3.3 Constructor to change (lines 44–53)

Current:

```java
public DefaultTopologyMaterializationService(
    BaseTopologyService baseTopologyService,
    H2BaseTopologyRepository repository,          // ← change type
    Predicate<String> admittedAdapterPredicate,
    Clock clock
) {
    this.baseTopologyService = Objects.requireNonNull(baseTopologyService, "baseTopologyService is required");
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.admittedAdapterPredicate = Objects.requireNonNull(admittedAdapterPredicate, "admittedAdapterPredicate is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
}
```

After MU-012:

```java
public DefaultTopologyMaterializationService(
    BaseTopologyService baseTopologyService,
    TopologyMaterializationStatePort statePort,   // ← port type
    Predicate<String> admittedAdapterPredicate,
    Clock clock
) {
    this.baseTopologyService = Objects.requireNonNull(baseTopologyService, "baseTopologyService is required");
    this.statePort = Objects.requireNonNull(statePort, "statePort is required");
    this.admittedAdapterPredicate = Objects.requireNonNull(admittedAdapterPredicate, "admittedAdapterPredicate is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
}
```

---

## 4. Complete repository. call site inventory

All eight `repository.` call sites must reference the renamed field `statePort.`
after the field rename. The method signatures do not change — only the receiver name.

| Line | Call | Method context |
|-----:|------|----------------|
| 158 | `repository.saveEndpointHealth(habitatId, endpointId, initialHealth)` | `materializeEndpoint()` — initial UNKNOWN health (MU-011) |
| 205 | `repository.saveDeviceState(habitatId, deviceId, fact.statePayload())` | `materializeDeviceState()` |
| 230 | `repository.saveEndpointHealth(habitatId, endpointId, new EndpointHealth(...))` | `materializeHealth()` |
| 250 | `return repository.findByHabitatId(habitatId)` | `admittedTopology()` |
| 259 | `repository.findCurrentVersion(habitatId)` | `rejectedByPrecondition()` |
| 260 | `repository.findCurrentVersion(habitatId)` | `rejectedByPrecondition()` |
| 269 | `repository.findCurrentVersion(habitatId)` | `rejectDuplicate()` |
| 282 | `repository.findCurrentVersion(habitatId)` | `rejectInvalid()` |

No method body logic changes. Only the field name (`repository` → `statePort`)
and its declared type change.

---

## 5. Required new port

Create in `com.sovereign.connect.core.topology.port`:

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

The port MUST NOT include:

```text
save(HabitatBaseTopology)        — structural mutation; belongs to BaseTopologyRepository
appendMutationRecord(...)         — mutation records; separate concern
findSnapshot(...)                 — CoreSnapshotReadPort concern
any JDBC / SQL / DataSource / ObjectMapper import
Room/Zone / SC-D / SC-B methods
```

---

## 6. H2 adapter integration — implements only

Add `TopologyMaterializationStatePort` to H2BaseTopologyRepository's implements
clause. Do not change any method bodies.

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
`saveDeviceState`, `saveEndpointHealth`) already exist on H2BaseTopologyRepository.
Compilation should succeed without any body changes.

---

## 7. The two parallel health write paths — do NOT merge

After MU-011, health writes flow through two separate, independent paths.
MU-012 must preserve both without merging them.

```text
Path A — BaseTopologyService.updateEndpointHealth():
  → uses EndpointHealthWritePort.saveEndpointHealth()
  → H2BaseTopologyRepository (as EndpointHealthWritePort)
  → writes to endpoint_health table
  → used for service-level health updates

Path B — DefaultTopologyMaterializationService:
  → will use TopologyMaterializationStatePort.saveEndpointHealth()
  → H2BaseTopologyRepository (as TopologyMaterializationStatePort)
  → writes to endpoint_health table
  → used for HealthFact materialization AND initial UNKNOWN health on endpoint creation
```

Both paths call the same H2 method body (`saveEndpointHealth`), but through
different port interfaces. This is intentional and correct. Do not collapse them
into a single port. `EndpointHealthWritePort` remains for `BaseTopologyService`.
`TopologyMaterializationStatePort` is for the materializer.

---

## 8. Test call sites

These four sites construct `DefaultTopologyMaterializationService` with an
`H2BaseTopologyRepository` instance. After MU-012, the second constructor
parameter is `TopologyMaterializationStatePort`. Because `H2BaseTopologyRepository`
now implements that port, the existing call sites compile without change.

```text
TopologyMaterializationSeedTest.java:58    new DefaultTopologyMaterializationService(mutationService, repository, ...)
TopologyMaterializationSeedTest.java:191   new DefaultTopologyMaterializationService(mutationService, repository, ...)
TopologyMaterializationSeedTest.java:266   new DefaultTopologyMaterializationService(mutationService, repository, ...)
PersistenceBoundaryHardeningTest.java:105  new DefaultTopologyMaterializationService(mutationService, repository, ...)
```

These call sites pass H2 instances. Java accepts this because H2 now implements
the port. The boundary assertion verifies that the CONSTRUCTOR DECLARATION no
longer uses H2 as the parameter type — it now declares `TopologyMaterializationStatePort`.

---

## 9. Boundary regression assertion to add

`PersistenceMemorySeedTest` already has the `constructorSurface(...)` helper:

```java
private String constructorSurface(Constructor<?> constructor) {
    return Arrays.stream(constructor.getParameterTypes())
        .map(Class::getName)
        .reduce("", (left, right) -> left + " " + right);
}
```

And it already has a boundary assertion for `BaseTopologyService`:

```java
assertThat(Arrays.stream(BaseTopologyService.class.getConstructors())
    .map(this::constructorSurface).toList())
    .noneMatch(surface -> surface.contains("H2BaseTopologyRepository"));
```

Extend this to also assert for `DefaultTopologyMaterializationService`:

```java
// AC-001 / AC-002 enforcement
assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getDeclaredFields())
    .map(field -> field.getType().getName())
    .toList())
    .noneMatch(type -> type.contains("H2BaseTopologyRepository"));

assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getConstructors())
    .map(this::constructorSurface).toList())
    .noneMatch(surface -> surface.contains("H2BaseTopologyRepository"));
```

Also add:

```java
// AC-003 / AC-004 enforcement
assertThat(H2BaseTopologyRepository.class.getInterfaces())
    .extracting(Class::getSimpleName)
    .contains("TopologyMaterializationStatePort");
```

The `constructorSurface` helper is already present in `PersistenceMemorySeedTest`
and does not need to be added.

---

## 10. Existing ports not to reuse in the materializer

```text
BaseTopologyRepository:
  Reason: exposes save(HabitatBaseTopology) — the structural mutation boundary.
  The materializer must not own structural mutation persistence directly.

CoreSnapshotReadPort:
  Reason: query/read boundary for snapshots. Does not include write operations.
  The materializer needs cohesive reads + writes in one port.

EndpointHealthWritePort:
  Reason: serves BaseTopologyService (Path A above). Do not reuse for materializer.
  TopologyMaterializationStatePort serves the materializer (Path B above).
```

---

## 11. InMemory repository

`InMemoryBaseTopologyRepository` does NOT need to implement
`TopologyMaterializationStatePort` in MU-012.

All materializer tests use `H2BaseTopologyRepository`. The in-memory adapter
is used by `BaseTopologyServiceTest` and `TopologyVersionHardeningTest`, which
do not construct `DefaultTopologyMaterializationService`.

---

## 12. Critical invariants

```text
BaseTopologyService remains owner of structural mutation semantics.
Accepted structural mutations still advance topologyVersion exactly as before.
State/health facts do not advance topologyVersion.
Rejected facts do not advance topologyVersion.
H2BaseTopologyRepository.save(HabitatBaseTopology) remains structural-only (MU-011 invariant).
Endpoint health persistence remains explicit — not a side effect of structural save.
CoreSnapshotQuery behavior remains unchanged.
SC-C materialization does not import H2, JDBC, SQL, DataSource or ObjectMapper.
```

---

## 13. Strict negative scope

Do not implement:

```text
RoomNode / ZoneNode topology extension
TopologySpatialRelation
RoomDiscoveryFact / ZoneDiscoveryFact
Production persistence schema / Flyway / Liquibase
TemporalActs / terminal request state / outbox / ledger
SC-B runtime / NATS / JetStream / Vert.x
SC-D adapter manifest / discovery protocol
Projection / Effective View / Hub / Session / Identity / Authority / Policy / Surface
```

---

## 14. Stop conditions

Report `BLOCKED` if:

```text
The refactor requires changing materialization semantics.
The materializer needs save(HabitatBaseTopology) directly.
H2 method bodies need semantic changes beyond signature.
A broad generic repository abstraction seems necessary.
Room/Zone or SC-D scope appears necessary.
Technology-specific leakage enters the materializer.
```

If a stop condition occurs, update `docs/mir/mir-012/code-surface-audit.md`
and request architect review before proceeding.
