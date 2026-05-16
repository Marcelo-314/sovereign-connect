# Codex Prompt — MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001

```text
MU:               MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001
Operational Slot: MU-014
Branch:           feat/sc-c-concurrency-idempotency-seed
Commit:           feat(sc-c): add materialization decision replay seed
Baseline tests:   54 / 0 failures / 0 errors
Target tests:     ≥ 62
```

---

## 0. Mandatory reading

Before any code change:

```text
docs/mir/mir-014/MIR-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001.md
docs/mir/mir-014/code-surface-audit.md
docs/mir/mir-014/decision-confirmation.md
docs/mir/mir-014/context.md
docs/mir/mir-014/acceptance-map.md
```

---

## 1. Mission

Add fact-scoped replay to `DefaultTopologyMaterializationService`:

```text
Same factId    → return stored MaterializationDecision. No re-execution.
Different factId, same canonical entity → existing REJECT_DUPLICATE path.
```

Neither path advances `topologyVersion`. Neither emits duplicate newly-emitted events.
Replay survives repository and service recreation (H2-backed).

---

## 2. Phase 1 — Create MaterializationDecisionReplayPort

Create:

```text
src/main/java/com/sovereign/connect/core/topology/port/MaterializationDecisionReplayPort.java
```

Exact content:

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

After this phase: `mvn compile` must succeed.

---

## 3. Phase 2 — Add H2 replay table and implement port

### 3.1 Add table to createSchema()

In `H2BaseTopologyRepository.createSchema()`, add after existing table creates:

```java
jdbcTemplate.execute("""
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
    """);
```

`previous_version_value` and `resulting_version_value` are nullable because some
rejection paths may occur before a current `topologyVersion` exists, for example
missing habitat or uninitialized topology. Rejected decisions MAY still carry the
current version when available.

`Optional.empty()` → NULL. `Optional.of(v)` → `v.value()` string.

Do NOT add `sc_c_terminal_responses`, `sc_c_outbox_entries` or any other SDD tables.

### 3.2 Add to implements clause

```java
// Before:
public class H2BaseTopologyRepository
    implements BaseTopologyRepository, CoreSnapshotReadPort,
               EndpointHealthWritePort, TopologyMaterializationStatePort

// After:
public class H2BaseTopologyRepository
    implements BaseTopologyRepository, CoreSnapshotReadPort,
               EndpointHealthWritePort, TopologyMaterializationStatePort,
               MaterializationDecisionReplayPort
```

### 3.3 Implement recordDecision

Follow the `saveEndpointHealth` MERGE pattern exactly:

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

`writeJson()` already exists in H2BaseTopologyRepository for ObjectMapper serialization.
Use it for `decision.emittedChanges()` — `List<TopologyChanged>` serializes cleanly.

### 3.4 Implement findDecision

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
        (rs, rowNum) -> {
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
            List<TopologyChanged> changes = readJson(
                rs.getString("emitted_changes_json"),
                new TypeReference<List<TopologyChanged>>() {}
            );
            return new MaterializationDecision(
                decisionId, factId, habitatId, kind,
                prev, res, changes, rs.getString("reason")
            );
        },
        habitatId,
        factId.toString()
    );
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
}
```

For `readJson`, use the same `objectMapper.readValue(json, typeRef)` pattern used
elsewhere in the repository. If a `readJson` helper does not exist, introduce one
consistent with the existing `writeJson` helper.

After Phase 2: `mvn compile` must succeed.
Run `mvn test` — all 54 existing tests must pass before proceeding.

---

## 4. Phase 3 — Update DefaultTopologyMaterializationService

### 4.1 Add replayPort field and update constructor

Add field:

```java
private final MaterializationDecisionReplayPort replayPort;
```

Update constructor — add `replayPort` before `clock`:

```java
public DefaultTopologyMaterializationService(
    BaseTopologyService baseTopologyService,
    TopologyMaterializationStatePort statePort,
    Predicate<String> admittedAdapterPredicate,
    MaterializationDecisionReplayPort replayPort,   // ← new, before clock
    Clock clock
) {
    this.baseTopologyService = Objects.requireNonNull(baseTopologyService, "baseTopologyService is required");
    this.statePort = Objects.requireNonNull(statePort, "statePort is required");
    this.admittedAdapterPredicate = Objects.requireNonNull(admittedAdapterPredicate, "admittedAdapterPredicate is required");
    this.replayPort = Objects.requireNonNull(replayPort, "replayPort is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
}
```

Add import:

```java
import com.sovereign.connect.core.topology.port.MaterializationDecisionReplayPort;
```

`DefaultTopologyMaterializationService` MUST NOT import H2BaseTopologyRepository,
JdbcTemplate, DataSource, SQL strings or ObjectMapper. The port hides all of that.

### 4.2 Update materialize()

Replace the current `materialize()` body with:

```java
@Override
public MaterializationDecision materialize(String habitatId, TopologyFact fact) {
    Objects.requireNonNull(fact, "fact is required");
    // Replay path: same (habitatId, factId) → return stored decision, no re-execution
    Optional<MaterializationDecision> replayed =
        replayPort.findDecision(habitatId, fact.factId());
    if (replayed.isPresent()) {
        return replayed.get();
    }
    // First-time path: execute and record
    MaterializationDecision decision = switch (fact) {
        case DeviceDiscoveryFact f    -> materializeDevice(habitatId, f);
        case EndpointDiscoveryFact f  -> materializeEndpoint(habitatId, f);
        case CapabilityDiscoveryFact f -> materializeCapability(habitatId, f);
        case DeviceStateFact f        -> materializeDeviceState(habitatId, f);
        case HealthFact f             -> materializeHealth(habitatId, f);
        case RoomDiscoveryFact f      -> materializeRoom(habitatId, f);
        case ZoneDiscoveryFact f      -> materializeZone(habitatId, f);
    };
    replayPort.recordDecision(habitatId, fact.factId(), decision);
    return decision;
}
```

The replay return is before the switch. The switch body is unchanged.
All structural mutation helpers (`addDeviceWithResult`, `addRoomWithResult`, etc.)
remain in the switch branches — they are NOT called on the replay path.

Replay may return the original `emittedChanges` stored with the original decision as
historical decision evidence. Replay MUST NOT append new events to
`BaseTopologyService.emittedEvents()` and downstream code MUST NOT treat replayed
`emittedChanges` as newly emitted events.

### 4.3 Update all constructor call sites

Update every `DefaultTopologyMaterializationService` constructor call site. Do not
assume there are only four. At the audited baseline there are six known test call
sites:

```text
TopologyMaterializationSeedTest.java x3
PersistenceBoundaryHardeningTest.java x1
RoomZoneTopologySeedTest.java x2
```

Pattern:

```java
// Before (4 args):
new DefaultTopologyMaterializationService(mutationService, repository, predicate, clock)

// After (5 args):
new DefaultTopologyMaterializationService(mutationService, repository, predicate, repository, clock)
```

`repository` appears twice: position 2 as `TopologyMaterializationStatePort`,
position 4 as `MaterializationDecisionReplayPort`. Java resolves by declared parameter
type. This is correct — `H2BaseTopologyRepository` implements both.

Use grep/search to find and update any additional call site not listed above.

After Phase 3: `mvn compile` must succeed.
Run `mvn test` — all 54 existing tests must still pass.

---

## 5. Phase 4 — Tests

Create:

```text
src/test/java/com/sovereign/connect/core/topology/ConcurrencyIdempotencySeedTest.java
```

The test class uses H2-backed repository so replay survives service recreation.
Follow the setup pattern of `PersistenceBoundaryHardeningTest`.

### Test A — sameFactIdReplaysStoredDecisionWithoutVersionAdvance

```text
1. Create initial topology (with room + zone).
2. Build a DeviceDiscoveryFact with a fixed factId.
3. Materialize → capture resulting topologyVersion and emittedChanges.
4. Recreate H2BaseTopologyRepository and DefaultTopologyMaterializationService
   against the same DataSource (same H2 database, new Java objects).
5. Materialize the same fact again (same factId).
6. Assert: returned decision.kind == ACCEPT_STRUCTURAL_MUTATION.
7. Assert: returned decision.causationFactId == original factId.
8. Assert: topologyVersion did NOT advance (same version as after step 3).
9. Assert: no duplicate TopologyChanged events emitted by the new service instance.
10. Assert: `BaseTopologyService.emittedEvents()` does not grow on replay.
11. If the replayed decision carries stored `emittedChanges`, treat them as historical
    decision evidence only, not newly emitted events.
```

### Test B — replaySurvivesRepositoryAndServiceRecreation

May be combined with Test A if steps 4-9 explicitly verify H2 persistence.
The separate test name is acceptable if the combined test is long.

### Test C — sameCanonicalDeviceDifferentFactIdRejectedAsDuplicate

```text
1. Materialize DeviceDiscoveryFact-1 (factId=UUID-1, providerId+providerDeviceId=X).
2. Capture topologyVersion after step 1.
3. Build DeviceDiscoveryFact-2 (factId=UUID-2, same providerId+providerDeviceId=X).
   UUID-2 != UUID-1 — this is a new fact, not replay.
4. Materialize fact-2.
5. Assert: kind == REJECT_DUPLICATE.
6. Assert: topologyVersion did NOT advance.
7. Assert: fact-2 was NOT treated as replay (different factId).
```

Add variants: `sameCanonicalRoomDifferentFactIdRejectedAsDuplicate`,
`sameCanonicalZoneDifferentFactIdRejectedAsDuplicate`.

### Test D — rejectedDecisionIsReplayable

```text
1. Use a predicate that rejects all adapters.
2. Materialize a fact → kind == REJECT_UNAUTHORIZED_ADAPTER.
3. Recreate services against same H2.
4. Materialize the same factId again.
5. Assert: kind == REJECT_UNAUTHORIZED_ADAPTER (replayed, not re-executed).
6. Assert: topologyVersion unchanged.
```

### Test E — validAfterRevalidationRemainsNonRejection

```text
1. Create topology with device at topologyVersion V.
2. Advance topologyVersion (materialize another structural fact).
3. Call validateTarget with TopologyTargetRef to the original device and version V.
4. Assert: result == VALID_AFTER_REVALIDATION (not rejection).
```

### Test F — wrongHabitatScopeReturnsConflict

```text
1. Create topology for habitat-A.
2. Create a TopologyVersion scoped to habitat-B.
3. Call validateTarget with the habitat-B version in habitat-A context.
4. Assert: result == TOPOLOGY_VERSION_CONFLICT.
```

### Test G — boundary assertion extension (PersistenceMemorySeedTest)

Add to the existing boundary assertion block:

```java
// AC-015: replay port must not expose H2 in materializer constructor
assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getConstructors())
    .map(this::constructorSurface).toList())
    .anyMatch(surface -> surface.contains("MaterializationDecisionReplayPort"));

// H2 implements replay port
assertThat(H2BaseTopologyRepository.class.getInterfaces())
    .extracting(Class::getSimpleName)
    .contains("MaterializationDecisionReplayPort");
```

Add required imports.

---

## 6. Strict non-goals — stop if these appear

Do NOT implement:

```text
ActionRequest runtime or dispatch
ActionTargetFailure concrete response
ScResponseEnvelope
sc_c_terminal_responses table
request-state lifecycle (IN_FLIGHT, TERMINAL states)
in-flight request registry
TemporalAct aggregate or engine
outbox / ledger tables or dispatcher
SC-B runtime
NATS / JetStream
Projection / Hub / Session / Identity / Authority / Policy
provider binding fingerprint migration
Room/Zone removal
production SQLite migrations or Flyway
```

---

## 7. Stop conditions

Report BLOCKED and do not continue if:

```text
- fact.factId() not available on any TopologyFact subtype
- replay requires changes to the sealed TopologyFact hierarchy
- H2-backed replay cannot be added without disproportionate schema changes
- DefaultTopologyMaterializationService must import H2/JDBC/SQL/DataSource/ObjectMapper
- replay advances topologyVersion
- replay emits duplicate TopologyChanged events
- different factId targeting same canonical entity is treated as replay
- VALID_AFTER_REVALIDATION becomes rejection
- any of the 4 prior MU invariants (MU-011, MU-012, MU-013) regress
- existing 54 tests fail after constructor update
```

---

## 8. Validation

```bash
mvn test
```

Expected:
```text
All 54 existing tests pass.
New ConcurrencyIdempotencySeedTest tests pass.
PersistenceMemorySeedTest boundary assertions pass.
Total: ≥ 62 tests, 0 failures, 0 errors.
```

Then fill `docs/mir/mir-014/implementation-report.md`.
