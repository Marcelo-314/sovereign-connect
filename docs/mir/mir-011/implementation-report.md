# Implementation Report - MIR-011 Persistence Boundary Hardening

```text
Artifact:      implementation-report.md
MIR:           MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
MIR version:   v0.1.1-draft, architect-approved
MU:            MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
Slot:          MU-011
Audit:         CSA-MU-011 v0.1.1-merged
Status:        Implementation complete
Target level:  L4
```

---

## 0. Implementation metadata

```text
Repository:          sovereign-connect
Branch:              feat/sc-c-persistence-boundary-hardening
Commit:              0e36ebf (pre-implementation HEAD; implementation not committed)
Implemented by:      Codex
Date:                2026-05-14
Test command:        mvn test
Test result summary: Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
```

---

## 1. Summary

MU-011 separates structural topology persistence from endpoint health persistence in the H2/JDBC seed implementation.

`H2BaseTopologyRepository.save(HabitatBaseTopology)` is now structural-only: it writes `topology_snapshots` and no longer creates, updates, restores or overwrites `endpoint_health` rows.

Endpoint health writes are now explicit:

- endpoint materialization writes initial `UNKNOWN` health after the structural endpoint mutation is accepted;
- `HealthFact` materialization continues to write endpoint health explicitly;
- `BaseTopologyService.updateEndpointHealth(...)` preserves H2 durability through the narrow `EndpointHealthWritePort`.

`DEBT-007-001` is closed for the current H2/JDBC seed boundary. The broader materialization state port remains deferred to MU-012.

---

## 2. Files changed

```text
src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
src/main/java/com/sovereign/connect/core/topology/materialization/DefaultTopologyMaterializationService.java
src/main/java/com/sovereign/connect/core/topology/port/EndpointHealthWritePort.java
src/main/java/com/sovereign/connect/core/topology/service/BaseTopologyService.java
src/test/java/com/sovereign/connect/core/topology/PersistenceBoundaryHardeningTest.java
src/test/java/com/sovereign/connect/core/topology/PersistenceMemorySeedTest.java
docs/mir/mir-011/implementation-report.md
```

---

## 3. Tests added

```text
PersistenceBoundaryHardeningTest#structuralSaveDoesNotTouchEndpointHealthTable
PersistenceBoundaryHardeningTest#initialEndpointHealthIsWrittenOnMaterialization
PersistenceBoundaryHardeningTest#serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect
```

---

## 4. Tests modified

```text
PersistenceMemorySeedTest#ac001ToAc025TopologyMemorySurvivesAdapterAndServiceRecreation
```

The H2-backed service in this test now uses the three-argument `BaseTopologyService` constructor with `H2BaseTopologyRepository` as `EndpointHealthWritePort`, preserving the durable service-level health contract after structural save became structural-only.

---

## 5. Tests run

Command:

```bash
mvn test
```

Result:

```text
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 6. Acceptance Criteria Result

| AC | Result | Evidence |
|---:|---|---|
| AC-001 | PASS | `docs/mir/mir-011/code-surface-audit.md` is present and implementation follows `CSA-MU-011 v0.1.1-merged`. |
| AC-002 | PASS | Full `mvn test` passes with 28 tests, 0 failures. |
| AC-003 | PASS | `H2BaseTopologyRepository.save(...)` ends after the `MERGE INTO topology_snapshots` write and no longer iterates endpoints or calls `saveEndpointHealth(...)`. |
| AC-004 | PASS | `PersistenceBoundaryHardeningTest#structuralSaveDoesNotTouchEndpointHealthTable` writes durable `DEGRADED` health, invokes structural `save(...)`, and verifies health is not overwritten. |
| AC-005 | PASS | Same test deletes the `endpoint_health` row through test-only JDBC, invokes structural `save(...)`, and verifies the row is not recreated. |
| AC-006 | PASS | `DefaultTopologyMaterializationService.materializeEndpoint(...)` explicitly calls `repository.saveEndpointHealth(...)` after accepted endpoint mutation; `initialEndpointHealthIsWrittenOnMaterialization` verifies durable `UNKNOWN` health. |
| AC-007 | PASS | `DefaultTopologyMaterializationService.materializeHealth(...)` continues to call `repository.saveEndpointHealth(...)` explicitly; existing materialization tests verify durable `HealthFact` health. |
| AC-008 | PASS | `DefaultTopologyMaterializationService.materializeDeviceState(...)` continues to call `repository.saveDeviceState(...)` explicitly; existing materialization tests verify durable device state. |
| AC-009 | PASS | `TopologyMaterializationSeedTest#endpointHealthSurvivesSubsequentStructuralMutation` passes without merge semantics inside `save(...)`. |
| AC-010 | PASS | The same regression test recreates the repository and verifies recovered `DEGRADED` health and original observed timestamp. |
| AC-011 | PASS | `EndpointHealthWritePort` added; `H2BaseTopologyRepository` implements it; `serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect` verifies `updateEndpointHealth(...)` persists durable health. |
| AC-012 | PASS | `serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect` and existing topology-version tests assert health updates do not advance `topologyVersion`. |
| AC-013 | PASS | Existing materialization and topology-version tests assert state writes do not advance `topologyVersion`. |
| AC-014 | PASS | Existing structural mutation tests still assert accepted structural mutations advance `topologyVersion`. |
| AC-015 | PASS | Existing Core Snapshot and materialization recovery tests continue to read durable device state and endpoint health after recovery. |
| AC-016 | PASS | No SC-B, SC-D, NATS, JetStream, Projection, Hub, Session, Identity, Authority, Policy or Surface dependency was introduced. |
| AC-017 | PASS | `TopologyMaterializationStatePort` was not introduced. Only the narrow `EndpointHealthWritePort` authorized by the prompt was added. |
| AC-018 | PASS | No production schema, migration framework, Flyway or Liquibase files were introduced. |
| AC-019 | PASS | This report lists files changed, tests added/modified, tests run and residual debt. |
| AC-020 | PASS | No new hidden coupling outside `CSA-MU-011 v0.1.1-merged` was discovered. The audit was not changed. |
| AC-021 | PASS | This report states that `endpoint_health` through `CoreSnapshotReadPort` is the durable health query source-of-truth, not embedded `topology_json`. |

---

## 7. Invariants preserved

```text
INV-011-001: H2 structural save is structural-only.
INV-011-002: HealthFact health survives later structural mutation.
INV-011-003: Newly materialized endpoints get explicit durable UNKNOWN health.
INV-011-004: Health writes do not advance topologyVersion.
INV-011-005: State writes do not advance topologyVersion.
INV-011-006: Accepted structural mutations still advance topologyVersion.
INV-011-007: Recovery reads durable health/state through read boundaries.
INV-011-008: BaseTopologyService.updateEndpointHealth durability is preserved through EndpointHealthWritePort.
INV-011-009: Device state and mutation records remain separate from structural save.
INV-011-010: RoomNode and ZoneNode behavior was not changed.
```

---

## 8. Persistence behavior before / after

### Before

```text
H2BaseTopologyRepository.save(HabitatBaseTopology) wrote topology_snapshots and also maintained endpoint_health.
MU-007 merge semantics prevented stale UNKNOWN aggregate health from overwriting durable non-UNKNOWN health, but the structural save still had mixed responsibility.
```

### After

```text
H2BaseTopologyRepository.save(HabitatBaseTopology) writes only topology_snapshots.
Endpoint health is written only through explicit calls:
- DefaultTopologyMaterializationService.materializeEndpoint(...)
- DefaultTopologyMaterializationService.materializeHealth(...)
- BaseTopologyService.updateEndpointHealth(...) via EndpointHealthWritePort
Device state remains explicitly written through H2BaseTopologyRepository.saveDeviceState(...).
```

---

## 9. Evidence: structural save is structural-only

`H2BaseTopologyRepository.save(...)` now performs:

```text
Objects.requireNonNull(...)
serialize topology
capture timestamp
MERGE INTO topology_snapshots (...)
```

It does not call:

```text
findEndpointHealth(...)
saveEndpointHealth(...)
saveDeviceState(...)
```

`PersistenceBoundaryHardeningTest#structuralSaveDoesNotTouchEndpointHealthTable` verifies that structural save:

```text
- does not create endpoint_health rows;
- does not overwrite existing endpoint_health rows;
- does not recreate deleted endpoint_health rows.
```

---

## 10. Evidence: initial endpoint health remains durable

`DefaultTopologyMaterializationService.materializeEndpoint(...)` now creates one `EndpointHealth initialHealth` and uses it both in the `EndpointNode` and the explicit durable write:

```text
repository.saveEndpointHealth(habitatId, endpointId, initialHealth)
```

The write happens after `baseTopologyService.addEndpointWithResult(...)` returns, so rejected or duplicate endpoint materialization does not write initial health.

`PersistenceBoundaryHardeningTest#initialEndpointHealthIsWrittenOnMaterialization` verifies:

```text
status:      UNKNOWN
lastSeenAt:  HealthFact/EndpointDiscoveryFact observedAt semantics from the discovery fact
details:     materialized from discovery fact
```

---

## 11. Evidence: HealthFact health survives structural save and recovery

`TopologyMaterializationSeedTest#endpointHealthSurvivesSubsequentStructuralMutation` verifies:

```text
1. device + endpoint are materialized;
2. HealthFact writes DEGRADED durable endpoint health;
3. capability materialization triggers a later structural save;
4. durable endpoint health remains DEGRADED;
5. repository recreation still reads DEGRADED and the original observedAt timestamp.
```

This now passes because structural save does not touch `endpoint_health`.

---

## 12. Evidence: service-level endpoint health durability

`EndpointHealthWritePort` was introduced as the narrow explicit write boundary:

```text
com.sovereign.connect.core.topology.port.EndpointHealthWritePort
```

`H2BaseTopologyRepository` implements it using the existing `saveEndpointHealth(...)` method.

`BaseTopologyService` now has:

```text
BaseTopologyService(BaseTopologyRepository, EndpointHealthWritePort, Clock)
```

Existing constructors delegate to `EndpointHealthWritePort.noOp()` for compatibility.

`BaseTopologyService.updateEndpointHealth(...)` still updates aggregate health without advancing `topologyVersion`, then explicitly writes durable endpoint health through the port.

`PersistenceBoundaryHardeningTest#serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect` verifies durability and recovery.

---

## 13. Health source-of-truth statement

```text
EndpointHealth may remain embedded in topology_json as part of the current aggregate shape.
For health queries and recovery, endpoint_health through CoreSnapshotReadPort is the durable operational-health source of truth.
The topology_json CLOB is not the durable health authority.
```

---

## 14. Code Surface Audit updates

```text
None.
```

No new hidden coupling outside `CSA-MU-011 v0.1.1-merged` was discovered.

---

## 15. Deviations

```text
None.
```

The implementation follows the prompt-recommended `EndpointHealthWritePort` pattern.

---

## 16. Failure signals encountered

```text
None.
```

No stop condition was reached.

---

## 17. Corpus issues discovered

```text
None.
```

The known RoomNode/ZoneNode partial model-level implementation remains unchanged and out of scope.

---

## 18. Residual debt and MU-012 recommendations

Remaining downstream work:

```text
MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001 should extract the broader materialization state boundary for health/state persistence.
Future persistence SDD work should define production schema ownership, migration strategy and recovery semantics.
The current aggregate may continue embedding EndpointHealth as shape residue, but endpoint_health remains query/recovery source-of-truth.
```

---

## 19. Final disposition

```text
Recommended MU status: Validated L4 after review
Acceptance level:      L4
Rationale:             Full test suite passes; structural save is structural-only; health/state persistence is explicit; prohibited scope was not introduced.
```
