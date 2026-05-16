# Context — MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001

```text
MU:               MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001
Operational Slot: MU-014
Audit:            CSA-MU-014 v0.1.2-merged
Decisions:        decision-confirmation.md v0.1.1-draft
Branch:           feat/sc-c-concurrency-idempotency-seed
Baseline tests:   54 / 0 failures / 0 errors
```

---

## 0. Execution thesis

MU-014 adds fact-scoped replay to the materialization path.

```text
factId         = replay key for the exact same materialization fact
canonical ID   = semantic duplicate key for different facts on the same entity
```

The first time a fact is processed → execute materialization → record decision.
The same factId again → return recorded decision immediately, no re-execution.
A different factId for the same canonical entity → existing REJECT_DUPLICATE path.

Neither path advances `topologyVersion`. Neither emits duplicate `TopologyChanged` events.

This is NOT generic request idempotency, NOT command idempotency, NOT terminal response
persistence, NOT TemporalAct fire idempotency. Those are explicitly deferred.

---

## 1. Exact types involved — read before writing any code

### 1.1 MaterializationDecision — current shape

```java
public record MaterializationDecision(
    UUID decisionId,
    UUID causationFactId,      // = fact.factId() that caused this decision
    String habitatId,
    MaterializationDecisionKind kind,
    Optional<TopologyVersion> previousTopologyVersion,
    Optional<TopologyVersion> resultingTopologyVersion,
    List<TopologyChanged> emittedChanges,
    String reason
)
```

Key observations:
- `causationFactId` IS the factId. Use it as the lookup key in replay.
- `Optional<TopologyVersion>` is NOT Jackson-serializable by default without
  a custom serializer or explicit Optional unwrapping. Serialize version values
  as nullable columns, NOT as whole-object JSON, to avoid this risk.
- `List<TopologyChanged>` can be serialized as JSON using the existing ObjectMapper
  pattern in H2BaseTopologyRepository (see §4.2 below).
- On replay: reconstruct `Optional.of(TopologyVersion.habitatVersion(habitatId, Long.parseLong(v)))`
  when the version column is non-null, or `Optional.empty()` when null.

### 1.2 TopologyVersion — factory methods

```java
// Reconstruct from stored string value:
TopologyVersion.habitatVersion(habitatId, Long.parseLong(storedValue))

// Initial version:
TopologyVersion.initialForHabitat(habitatId)
```

Do NOT construct `new TopologyVersion(...)` directly — use the factory methods.

### 1.3 TopologyFact — factId is guaranteed on the interface

```java
public sealed interface TopologyFact permits
    DeviceDiscoveryFact, EndpointDiscoveryFact, CapabilityDiscoveryFact,
    DeviceStateFact, HealthFact, RoomDiscoveryFact, ZoneDiscoveryFact {
    UUID factId();
    // ...
}
```

`fact.factId()` is safe to call on any `TopologyFact` without pattern matching.
No sealed hierarchy changes are needed.

### 1.4 DefaultTopologyMaterializationService — current constructor and materialize()

Current constructor (4 params):

```java
public DefaultTopologyMaterializationService(
    BaseTopologyService baseTopologyService,
    TopologyMaterializationStatePort statePort,
    Predicate<String> admittedAdapterPredicate,
    Clock clock
)
```

Current `materialize()` body start:

```java
@Override
public MaterializationDecision materialize(String habitatId, TopologyFact fact) {
    Objects.requireNonNull(fact, "fact is required");
    return switch (fact) {
        case DeviceDiscoveryFact f    -> materializeDevice(habitatId, f);
        case EndpointDiscoveryFact f  -> materializeEndpoint(habitatId, f);
        case CapabilityDiscoveryFact f -> materializeCapability(habitatId, f);
        case DeviceStateFact f        -> materializeDeviceState(habitatId, f);
        case HealthFact f             -> materializeHealth(habitatId, f);
        case RoomDiscoveryFact f      -> materializeRoom(habitatId, f);
        case ZoneDiscoveryFact f      -> materializeZone(habitatId, f);
    };
}
```

The replay lookup must wrap this entire switch — before any branch executes.

### 1.5 H2BaseTopologyRepository — current state

```java
public class H2BaseTopologyRepository
    implements BaseTopologyRepository, CoreSnapshotReadPort,
               EndpointHealthWritePort, TopologyMaterializationStatePort
```

Constructor calls `createSchema()` at construction time.
H2 MERGE pattern used by `saveEndpointHealth` (the reference pattern):

```java
jdbcTemplate.update("""
    MERGE INTO endpoint_health
    (habitat_id, endpoint_id, status, last_seen_at, details)
    KEY (habitat_id, endpoint_id)
    VALUES (?, ?, ?, ?, ?)
    """,
    habitatId, endpointId, health.status().name(),
    Timestamp.from(health.lastSeenAt()), health.details()
);
```

`MaterializationDecisionReplayPort` implementation follows this exact pattern.

---

## 2. New artifacts to create

### 2.1 MaterializationDecisionReplayPort (new port)

```java
package com.sovereign.connect.core.topology.port;

import com.sovereign.connect.core.topology.materialization.MaterializationDecision;
import java.util.Optional;
import java.util.UUID;

public interface MaterializationDecisionReplayPort {
    Optional<MaterializationDecision> findDecision(String habitatId, UUID factId);
    void recordDecision(String habitatId, UUID factId, MaterializationDecision decision);
}
```

Do NOT name it `RequestStatePort`, `TerminalResponsePort`, `CommandIdempotencyPort`
or any name implying generic request idempotency.

### 2.2 H2 replay table — add to createSchema()

Add this table to `H2BaseTopologyRepository.createSchema()`, following the
same `CREATE TABLE IF NOT EXISTS` pattern as `endpoint_health`:

```sql
CREATE TABLE IF NOT EXISTS materialization_decision_replay (
    habitat_id              VARCHAR(255)  NOT NULL,
    fact_id                 VARCHAR(36)   NOT NULL,
    decision_id             VARCHAR(36)   NOT NULL,
    kind                    VARCHAR(128)  NOT NULL,
    previous_version_value  VARCHAR(255),
    resulting_version_value VARCHAR(255),
    emitted_changes_json    CLOB          NOT NULL,
    reason                  VARCHAR(2048) NOT NULL,
    recorded_at             TIMESTAMP     NOT NULL,
    PRIMARY KEY (habitat_id, fact_id)
)
```

Notes:
- `previous_version_value` and `resulting_version_value` are nullable because some
  rejection paths may occur before a current `topologyVersion` is available, for example
  missing habitat or uninitialized topology. Rejected decisions MAY still carry the
  current version when available.
- `emitted_changes_json` stores `List<TopologyChanged>` as JSON using the existing
  `writeJson()` / `objectMapper` pattern. An empty list serializes as `[]`.
- Do NOT add `sc_c_terminal_responses`, `sc_c_outbox_entries` or `sc_c_ledger_entries`.

### 2.3 H2BaseTopologyRepository — add implements clause and two methods

Update implements clause:

```java
public class H2BaseTopologyRepository
    implements BaseTopologyRepository, CoreSnapshotReadPort,
               EndpointHealthWritePort, TopologyMaterializationStatePort,
               MaterializationDecisionReplayPort                          // ← new
```

**recordDecision (write) — H2 MERGE pattern:**

```java
@Override
public void recordDecision(String habitatId, UUID factId, MaterializationDecision decision) {
    Objects.requireNonNull(habitatId, "habitatId is required");
    Objects.requireNonNull(factId, "factId is required");
    Objects.requireNonNull(decision, "decision is required");
    String emittedChangesJson = writeJson(decision.emittedChanges());
    jdbcTemplate.update("""
        MERGE INTO materialization_decision_replay
        (habitat_id, fact_id, decision_id, kind,
         previous_version_value, resulting_version_value,
         emitted_changes_json, reason, recorded_at)
        KEY (habitat_id, fact_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        habitatId,
        factId.toString(),
        decision.decisionId().toString(),
        decision.kind().name(),
        decision.previousTopologyVersion().map(TopologyVersion::value).orElse(null),
        decision.resultingTopologyVersion().map(TopologyVersion::value).orElse(null),
        emittedChangesJson,
        decision.reason(),
        Timestamp.from(Instant.now(clock))
    );
}
```

**findDecision (read) — reconstruct MaterializationDecision:**

```java
@Override
public Optional<MaterializationDecision> findDecision(String habitatId, UUID factId) {
    Objects.requireNonNull(habitatId, "habitatId is required");
    Objects.requireNonNull(factId, "factId is required");
    List<MaterializationDecision> results = jdbcTemplate.query(
        """
        SELECT decision_id, kind,
               previous_version_value, resulting_version_value,
               emitted_changes_json, reason
        FROM materialization_decision_replay
        WHERE habitat_id = ? AND fact_id = ?
        """,
        (rs, rowNum) -> toMaterializationDecision(habitatId, factId, rs),
        habitatId,
        factId.toString()
    );
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
}

private MaterializationDecision toMaterializationDecision(
    String habitatId, UUID factId, ResultSet rs
) throws SQLException {
    UUID decisionId = UUID.fromString(rs.getString("decision_id"));
    MaterializationDecisionKind kind =
        MaterializationDecisionKind.valueOf(rs.getString("kind"));
    String prevVal = rs.getString("previous_version_value");
    String resVal  = rs.getString("resulting_version_value");
    Optional<TopologyVersion> prev = prevVal != null
        ? Optional.of(TopologyVersion.habitatVersion(habitatId, Long.parseLong(prevVal)))
        : Optional.empty();
    Optional<TopologyVersion> res = resVal != null
        ? Optional.of(TopologyVersion.habitatVersion(habitatId, Long.parseLong(resVal)))
        : Optional.empty();
    List<TopologyChanged> emittedChanges =
        readTopologyChangedList(rs.getString("emitted_changes_json"));
    return new MaterializationDecision(
        decisionId, factId, habitatId, kind,
        prev, res, emittedChanges, rs.getString("reason")
    );
}
```

For `readTopologyChangedList`, use the same `objectMapper.readValue(json, new TypeReference<List<TopologyChanged>>(){})` pattern used elsewhere in the repository. If that pattern does not already exist, it is safe to introduce it here — `TopologyChanged` is a plain record with standard field types.

### 2.4 DefaultTopologyMaterializationService — constructor + materialize() change

Add field:

```java
private final MaterializationDecisionReplayPort replayPort;
```

Update constructor to 5 params:

```java
public DefaultTopologyMaterializationService(
    BaseTopologyService baseTopologyService,
    TopologyMaterializationStatePort statePort,
    Predicate<String> admittedAdapterPredicate,
    MaterializationDecisionReplayPort replayPort,   // ← new
    Clock clock
) {
    this.baseTopologyService = Objects.requireNonNull(baseTopologyService, ...);
    this.statePort = Objects.requireNonNull(statePort, ...);
    this.admittedAdapterPredicate = Objects.requireNonNull(admittedAdapterPredicate, ...);
    this.replayPort = Objects.requireNonNull(replayPort, "replayPort is required");
    this.clock = Objects.requireNonNull(clock, ...);
}
```

Update `materialize()`:

```java
@Override
public MaterializationDecision materialize(String habitatId, TopologyFact fact) {
    Objects.requireNonNull(fact, "fact is required");
    // Replay path: same factId → return stored decision immediately
    Optional<MaterializationDecision> replayed =
        replayPort.findDecision(habitatId, fact.factId());
    if (replayed.isPresent()) {
        return replayed.get();
    }
    // First-time path: execute materialization
    MaterializationDecision decision = switch (fact) {
        case DeviceDiscoveryFact f    -> materializeDevice(habitatId, f);
        case EndpointDiscoveryFact f  -> materializeEndpoint(habitatId, f);
        case CapabilityDiscoveryFact f -> materializeCapability(habitatId, f);
        case DeviceStateFact f        -> materializeDeviceState(habitatId, f);
        case HealthFact f             -> materializeHealth(habitatId, f);
        case RoomDiscoveryFact f      -> materializeRoom(habitatId, f);
        case ZoneDiscoveryFact f      -> materializeZone(habitatId, f);
    };
    // Record decision for future replay
    replayPort.recordDecision(habitatId, fact.factId(), decision);
    return decision;
}
```

The replay path returns before the switch. No structural mutation methods are
called. No topologyVersion advances. No new `TopologyChanged` events are emitted.

Replay may return the original `emittedChanges` stored with the original decision as
historical decision evidence. However, replay MUST NOT append new events to
`BaseTopologyService.emittedEvents()` and downstream code MUST NOT treat replayed
`emittedChanges` as newly emitted events.

---

## 3. Test call sites — update all constructor call sites, same pattern as MU-012

Update every `DefaultTopologyMaterializationService` constructor call site. Do not
assume there are only four.

At the audited baseline there are six known test call sites:

```text
TopologyMaterializationSeedTest.java x3
PersistenceBoundaryHardeningTest.java x1
RoomZoneTopologySeedTest.java x2
```

Each call site that passes `H2BaseTopologyRepository` as the `statePort` argument
must add `repository` (which now also implements `MaterializationDecisionReplayPort`)
as the 4th argument:

Pattern (same as how MU-012 updated call sites):

```java
// Before:
new DefaultTopologyMaterializationService(mutationService, repository, predicate, clock)

// After:
new DefaultTopologyMaterializationService(mutationService, repository, predicate, repository, clock)
```

`repository` is passed twice: once as `TopologyMaterializationStatePort` (2nd param)
and once as `MaterializationDecisionReplayPort` (4th param). This is correct because
`H2BaseTopologyRepository` now implements both interfaces. The declared parameter
types differ — Java accepts this.

---

## 4. Concurrency model — why tests don't prove production concurrent safety

The materializer's `findDecision → execute → recordDecision` cycle is NOT atomic
at the application layer. Two concurrent callers with the same factId can both
find no stored decision and both execute materialization.

SQLite WAL is the production storage profile for serialized writes, but MU-014 does
not prove full concurrent same-fact atomicity unless a future implementation wraps
`findDecision → execute → recordDecision` in a single transaction, lock, or equivalent
claim/insert-first protocol.

It is NOT safe to infer concurrent same-fact atomicity from InMemory or H2 tests.

MU-014 tests are sequential. They validate replay behavior and duplicate-effect
prevention under serialized assumptions, not production-grade concurrent write safety.
Every concurrent/interleaved documentation test MUST include this comment:

```java
// This test validates seed replay/idempotency behavior under serialized assumptions.
// Production concurrent write safety requires the production SQLite/WAL profile plus
// a transaction, lock, or equivalent claim protocol; it is not proven by H2/InMemory tests.
```

---

## 5. Invariants from prior MUs — must remain intact

```text
MU-011: H2BaseTopologyRepository.save() is structural-only — no health/state side effects
MU-012: DefaultTopologyMaterializationService imports no H2/JDBC/SQL/DataSource
MU-013: validateTopology() checks all spatial coherence — no bypass
MU-013: TopologySpatialRelation(LOCATED_IN) is source of truth for placement
```

The `PersistenceMemorySeedTest` boundary assertions must still pass.
`DefaultTopologyMaterializationService` must have no H2 field/import/constructor.

---

## 6. Files to create and modify

```text
NEW:
  src/main/java/com/sovereign/connect/core/topology/port/MaterializationDecisionReplayPort.java
  src/test/java/com/sovereign/connect/core/topology/ConcurrencyIdempotencySeedTest.java

MODIFY:
  src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
    — add MaterializationDecisionReplayPort to implements clause
    — add materialization_decision_replay table to createSchema()
    — add findDecision() + recordDecision() + toMaterializationDecision() methods

  src/main/java/com/sovereign/connect/core/topology/materialization/DefaultTopologyMaterializationService.java
    — add replayPort field
    — update constructor (add replayPort param before clock)
    — update materialize() with replay lookup + record

  src/test/java/com/sovereign/connect/core/topology/TopologyMaterializationSeedTest.java
    — update 3 known construction call sites (add repository as 4th arg)

  src/test/java/com/sovereign/connect/core/topology/PersistenceBoundaryHardeningTest.java
    — update 1 known construction call site (add repository as 4th arg)

  src/test/java/com/sovereign/connect/core/topology/RoomZoneTopologySeedTest.java
    — update 2 known construction call sites (add repository as 4th arg)

  Any additional `DefaultTopologyMaterializationService` call site found by grep
    — update with the same pattern
```
