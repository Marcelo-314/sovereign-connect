# Acceptance Map — MIR-011 Persistence Boundary Hardening

```text
Artifact:      acceptance-map.md
MIR:           MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
MIR version:   v0.1.1-draft, architect-approved
MU:            MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
Slot:          MU-011
Audit:         CSA-MU-011 v0.1.1-merged
Status:        Ready for implementation package
Target level:  L4
```

---

## 0. Purpose

This acceptance map preserves the MIR acceptance criteria numbering and translates each criterion into implementation evidence expected from Codex.

The implementation report MUST mark every AC as `PASS`, `FAIL`, `N/A with accepted rationale`, or `BLOCKED`.

---

## 1. Acceptance Criteria Map

| AC | Requirement | Required evidence | Suggested verification |
|---:|---|---|---|
| AC-001 | The Code Surface Audit is present at `docs/mir/mir-011/code-surface-audit.md` and accepted before implementation. | Report references `CSA-MU-011 v0.1.1-merged` and confirms implementation derived from it. | File exists and implementation report cites it. |
| AC-002 | Existing SC-C kernel tests remain PASS. | `mvn test` output with all current tests passing. | Full test suite run. |
| AC-003 | `H2BaseTopologyRepository.save(HabitatBaseTopology)` no longer writes endpoint health as a side effect. | Diff showing endpoint-health loop/merge side effect removed from `save(...)`. | Code inspection + targeted test. |
| AC-004 | Structural topology saves do not overwrite durable endpoint health. | Regression test showing health written before structural save is preserved after structural save. | `TopologyMaterializationSeedTest` or new dedicated test. |
| AC-005 | Structural topology saves do not recreate missing endpoint health rows unless caller explicitly writes health. | Targeted test deleting or removing endpoint-health row, then invoking structural save; row remains absent unless explicit health write occurs. | Use test-only JDBC/direct setup if needed; do not add production delete method. |
| AC-006 | New endpoint materialization explicitly persists initial `UNKNOWN` endpoint health, or MIR/audit is updated with accepted alternate decision. | Test showing endpoint health is queryable as `UNKNOWN` immediately after endpoint materialization and before any `HealthFact`. | New `initialEndpointHealthIsWrittenOnMaterialization` test. |
| AC-007 | `HealthFact` materialization persists endpoint health explicitly. | Existing or updated test showing `HealthFact` writes durable endpoint health. | Materialize `HealthFact`; assert `findEndpointHealth(...)`. |
| AC-008 | `DeviceStateFact` materialization persists device state explicitly. | Existing or updated test showing `DeviceStateFact` writes durable device state. | Materialize `DeviceStateFact`; assert `findDeviceState(...)` after recovery if available. |
| AC-009 | Endpoint health written by `HealthFact` survives later capability materialization / structural save. | Regression test passing without health merge inside `save(...)`. | `endpointHealthSurvivesSubsequentStructuralMutation`. |
| AC-010 | Endpoint health survives repository/service recreation after a later structural save. | Test recreates repository/service and reads persisted health. | Existing MU-007/MU-010 path or new assertion. |
| AC-011 | Service-level endpoint health update remains durable if it was durable before MU-011. | New or updated test showing `BaseTopologyService.updateEndpointHealth(...)` writes durable health without relying on structural `save(...)` side effect. | `serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect`. |
| AC-012 | Health writes do not advance `topologyVersion`. | Test records version before/after health update and asserts equality. | Existing `TopologyVersionHardeningTest` or new targeted test. |
| AC-013 | State writes do not advance `topologyVersion`. | Test records version before/after state update and asserts equality. | Existing test or new targeted assertion. |
| AC-014 | Structural accepted mutations still advance `topologyVersion`. | Tests for add device/endpoint/capability still show version advance. | Existing topology version hardening tests. |
| AC-015 | Core Snapshot Query reads endpoint health and device state from durable/read boundaries after recovery. | Test demonstrates query after repository/service recreation sees durable health/state. | `CoreSnapshotQuerySeedTest` / `ScCoreKernelHardeningTest`. |
| AC-016 | No new SC-B, SC-D, NATS, JetStream, Projection, Hub, Session, Identity, Authority, Policy or Surface dependency is introduced. | Report states no forbidden imports/packages/classes were introduced. | Code inspection, package grep if useful. |
| AC-017 | `TopologyMaterializationStatePort` is not introduced in this MU. | Report states no such port was introduced and MU-012 remains responsible. | Code inspection. |
| AC-018 | No production schema or migration framework is introduced. | Report states no Flyway/Liquibase/final schema/migration files added. | Code inspection. |
| AC-019 | Implementation Report lists files changed, tests added, tests run and residual persistence debt. | Completed implementation report contains required fields. | Report review. |
| AC-020 | Any new hidden coupling discovered during implementation updates the Code Surface Audit and requires architect review/approval before continuation. | Report confirms no unreviewed audit-affecting coupling was discovered, or points to architect-approved audit update. | Report review. |
| AC-021 | MIR or Implementation Report explicitly states that `EndpointHealth` may remain embedded in `topology_json`, but health query source-of-truth is `endpoint_health` through `CoreSnapshotReadPort`, not the CLOB aggregate field. | Report includes explicit source-of-truth statement. | Report review + code path check for `CoreSnapshotReadPort.findEndpointHealth(...)`. |

---

## 2. Required test coverage

The implementation SHOULD add or update tests equivalent to:

```text
TEST-011-A — structuralSaveDoesNotTouchEndpointHealthTable
TEST-011-B — initialEndpointHealthIsWrittenOnMaterialization
TEST-011-C — serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect
TEST-011-D — healthWriteDoesNotAdvanceTopologyVersion
```

Existing tests that are especially relevant and MUST remain passing:

```text
TopologyMaterializationSeedTest
PersistenceMemorySeedTest
CoreSnapshotQuerySeedTest
ScCoreKernelHardeningTest
TopologyVersionHardeningTest
BaseTopologyServiceTest
```

---

## 3. Evidence rules

The implementation report MUST include:

```text
- branch and commit hash;
- files changed;
- tests added and modified;
- exact test command(s) run;
- test output summary;
- AC-001 through AC-021 status;
- confirmation that H2BaseTopologyRepository.save(...) is structural-only;
- confirmation that endpoint_health is the durable health source for query/recovery;
- remaining debt, especially any item deferred to MU-012.
```
