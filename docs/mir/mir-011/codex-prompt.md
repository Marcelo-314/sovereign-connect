# Codex Prompt — MIR-011 Persistence Boundary Hardening

```text
Artifact:      codex-prompt.md
MIR:           MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
MIR version:   v0.1.1-draft, architect-approved
MU:            MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
Slot:          MU-011
Audit:         CSA-MU-011 v0.1.1-merged
Target:        Validated L4
Branch:        feat/sc-c-persistence-boundary-hardening
Commit:        fix(sc-c): separate structural topology and health persistence
```

---

## 0. Mandatory reading order

Before changing code, read:

```text
docs/mir/mir-011/MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001.md
docs/mir/mir-011/code-surface-audit.md
docs/mir/mir-011/acceptance-map.md
docs/mir/mir-011/context.md
```

Treat those files as authoritative for this task.

Do not implement outside the scope of MU-011.

---

## 1. Objective

Implement `MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001`.

Close `DEBT-007-001` by separating structural topology persistence from endpoint health/state persistence in the current H2/JDBC seed implementation.

Primary target:

```text
H2BaseTopologyRepository.save(HabitatBaseTopology)
```

After your changes, this method must be structural-only and must not write endpoint health as a hidden side effect.

---

## 2. Required changes

### 2.1 Make structural save structural-only

Modify `H2BaseTopologyRepository.save(HabitatBaseTopology)` so it only writes the structural topology snapshot.

It must not:

```text
- create endpoint_health rows;
- update endpoint_health rows;
- restore missing endpoint_health rows;
- overwrite endpoint_health rows;
- write device state;
- write future temporal/terminal records.
```

Remove the endpoint iteration / conditional merge health logic from `save(...)`.

---

### 2.2 Explicitly persist initial UNKNOWN endpoint health

When `DefaultTopologyMaterializationService.materializeEndpoint(...)` creates a new endpoint, persist the initial `UNKNOWN` endpoint health explicitly after the structural endpoint mutation is accepted.

Rules:

```text
- Do this only if endpoint materialization is accepted.
- Do not write initial health for rejected/duplicate endpoint materialization.
- Preserve observedAt / lastSeenAt semantics from the discovery fact.
- Do not advance topologyVersion for this health write.
```

---

### 2.3 Preserve HealthFact and DeviceStateFact explicit persistence

Preserve existing explicit persistence behavior for:

```text
DefaultTopologyMaterializationService.materializeHealth(...)
DefaultTopologyMaterializationService.materializeDeviceState(...)
```

Do not route these through structural save.

---

### 2.4 Preserve service-level endpoint health durability

`BaseTopologyService.updateEndpointHealth(...)` currently mutates the aggregate and calls `repository.save(...)`. In H2, health durability came from the side effect inside `save(...)`. After MU-011, that side effect is gone.

Required fix:

1. Introduce `EndpointHealthWritePort` in:

   ```text
   com.sovereign.connect.core.topology.port
   ```

   Use the recommended pattern in `context.md` §11.4, including the `noOp()` factory method.

2. Add `H2BaseTopologyRepository` to the `EndpointHealthWritePort` implements clause.

   The method already exists as `saveEndpointHealth(...)`; only the interface declaration should be required unless compilation reveals a small signature adjustment.

3. Add a third constructor to `BaseTopologyService`:

   ```text
   BaseTopologyService(BaseTopologyRepository, EndpointHealthWritePort, Clock)
   ```

   Existing constructors must delegate to it using `EndpointHealthWritePort.noOp()`.

   Existing tests using `InMemoryBaseTopologyRepository` must continue to work unchanged because they read health from the aggregate, not from `findEndpointHealth(...)`.

4. Inside `updateEndpointHealth(...)`, after `repository.save(updatedTopology)`, add explicit:

   ```text
   healthWritePort.saveEndpointHealth(...)
   ```

   for the updated health.

5. Add or update test:

   ```text
   serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect
   ```

   Use H2 and the three-argument constructor. Assert that after `updateEndpointHealth(...)`, `repository.findEndpointHealth(...)` returns the updated status.

If this requires broader rearchitecture, stop and report `BLOCKED`. Do not implement MU-012 inside MU-011.
---

### 2.5 Preserve read/query semantics

`CoreSnapshotQueryService` must continue to read endpoint health through:

```text
CoreSnapshotReadPort.findEndpointHealth(...)
```

Do not change query semantics to treat CLOB-embedded `EndpointHealth` inside `topology_json` as health source-of-truth.

`EndpointHealth` may remain embedded in `topology_json` as aggregate-shape residue, but the durable health source for queries/recovery is the `endpoint_health` table.

---

## 3. Prohibited changes

Do not:

```text
- extract TopologyMaterializationStatePort;
- implement MU-012;
- add saveEndpointHealth(...) or saveDeviceState(...) to BaseTopologyRepository;
- make BaseTopologyService depend on H2BaseTopologyRepository;
- introduce production schema/migrations/Flyway/Liquibase;
- introduce TemporalActs, terminal request state or outbox/ledger semantics;
- implement RoomDiscoveryFact, ZoneDiscoveryFact, DeviceRoomAssignmentFact or TopologySpatialRelation;
- change RoomNode or ZoneNode semantics;
- introduce SC-B, SC-D, NATS, JetStream, Vert.x, Projection, Hub, Session, Identity, Authority, Policy or Surface dependencies;
- weaken existing tests.
```

---

## 4. Required tests

Add or update tests equivalent to:

```text
structuralSaveDoesNotTouchEndpointHealthTable
initialEndpointHealthIsWrittenOnMaterialization
serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect
healthWriteDoesNotAdvanceTopologyVersion
```

Also ensure existing high-risk tests remain passing:

```text
TopologyMaterializationSeedTest
PersistenceMemorySeedTest
CoreSnapshotQuerySeedTest
ScCoreKernelHardeningTest
TopologyVersionHardeningTest
BaseTopologyServiceTest
```

Test guidance:

```text
- It is acceptable to use test-only JDBC/direct DB setup to delete or inspect endpoint_health rows.
- Do not add production delete methods solely for tests.
- Prefer assertions that prove structural save does not touch endpoint_health at all.
```

---

## 5. Acceptance criteria

Satisfy AC-001 through AC-021 from:

```text
docs/mir/mir-011/acceptance-map.md
```

Critical ACs:

```text
AC-003: save(HabitatBaseTopology) no longer writes endpoint health as side effect.
AC-004: structural saves do not overwrite durable endpoint health.
AC-005: structural saves do not recreate missing endpoint health rows.
AC-006: endpoint materialization explicitly persists initial UNKNOWN health.
AC-011: service-level endpoint health update remains durable if it was durable before MU-011.
AC-021: endpoint_health/CoreSnapshotReadPort, not topology_json CLOB, is health query source-of-truth.
```

---

## 6. Required verification command

Run the full test suite:

```bash
mvn test
```

If the project requires a wrapper, use the project wrapper instead:

```bash
./mvnw test
```

Report the exact command used and test summary.

---

## 7. Implementation report

After implementation, complete:

```text
docs/mir/mir-011/implementation-report.md
```

The report must include:

```text
- branch;
- commit hash if available;
- summary;
- files changed;
- tests added;
- tests modified;
- tests run;
- test result summary;
- AC-001 through AC-021 status;
- evidence that save(HabitatBaseTopology) is structural-only;
- evidence that initial UNKNOWN endpoint health is explicit;
- evidence that HealthFact health survives structural save and recovery;
- evidence that service-level updateEndpointHealth durability remains correct;
- explicit statement that endpoint_health via CoreSnapshotReadPort is health query source-of-truth, not topology_json;
- deviations;
- audit updates, if any;
- residual debt and MU-012 recommendations.
```

---

## 8. Stop conditions

Stop and do not continue automatically if you discover:

```text
- preserving updateEndpointHealth durability requires broad port rearchitecture;
- endpoint health cannot be separated from structural save without changing core domain semantics;
- a new hidden coupling not captured by code-surface-audit.md;
- a need to extract TopologyMaterializationStatePort;
- a need to alter BaseTopologyRepository into a mixed persistence port;
- a need to introduce schema/migration work.
```

If any stop condition occurs, update the implementation report and propose the smallest upstream patch.

---

## 9. Expected result

Expected successful outcome:

```text
- H2BaseTopologyRepository.save(...) is structural-only.
- Endpoint health persistence is explicit.
- Initial UNKNOWN endpoint health is preserved durably.
- Service-level endpoint health durability is preserved.
- Structural saves cannot overwrite or recreate endpoint health rows.
- Full test suite passes.
- MU-011 can be reported as Validated L4 after review.
```
