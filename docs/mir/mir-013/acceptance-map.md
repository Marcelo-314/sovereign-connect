# Acceptance Map — MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001

```text
Artifact: docs/mir/mir-013/acceptance-map.md
Version: v0.1.0
Status: Draft / execution-ready
MIR: MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 v0.1.0-draft
CSA: CSA-MU-013 v0.1.1-merged
Acceptance target: Validated L4
```

## 0. Purpose

This acceptance map binds MIR AC-001 through AC-035 to expected implementation evidence for MU-013.

The implementation is acceptable only if the evidence below is produced in `docs/mir/mir-013/implementation-report.md` and local tests pass.

---

## 1. Critical acceptance cluster

The following ACs are critical and block closure if missing:

```text
AC-005  TopologySpatialRelation exists as first-class SC-C relation state.
AC-006  LOCATED_IN is the only implemented seed relation.
AC-014  DeviceNode.roomId / zoneId remain consistent with primary LOCATED_IN.
AC-015  EndpointNode.roomId / zoneId remain consistent with primary LOCATED_IN.
AC-018  Mismatch between relation graph and compatibility fields/indices is detected.
AC-019  No silent orphan LOCATED_IN relation is created.
AC-020  Accepted room/zone/relation structural mutation advances topologyVersion.
AC-025  Relation-aware query or read boundary resolves primary placement.
AC-026  Room/Zone/relation state persists across restart/recreation.
AC-031  MU-011 persistence boundary remains intact.
AC-032  MU-012 materialization port boundary remains intact.
AC-033  Existing SC-C kernel tests continue passing.
AC-035  Build/test command succeeds.
```

---

## 2. AC mapping

| AC | Expected evidence | Required status |
|---|---|---|
| AC-001 | MIR-013 present under `docs/mir/mir-013/` and references PDR v0.1.1-draft. | PASS |
| AC-002 | `code-surface-audit.md` present and approved; package derives from CSA-MU-013 v0.1.1-merged. | PASS |
| AC-003 | Existing `RoomNode` remains first-class in Base Topology root `rooms`. | PASS |
| AC-004 | Existing `ZoneNode` remains first-class in Base Topology root `zones`. | PASS |
| AC-005 | `TopologySpatialRelation` or semantically equivalent SC-C relation state exists; preferred: model record plus `HabitatBaseTopology.spatialRelations`. | PASS |
| AC-006 | `TopologySpatialRelationKind.LOCATED_IN` implemented; reserved relation kinds may exist as enum constants but no behavior/tests for semantic relations. | PASS |
| AC-007 | No implementation behavior for `AFFECTS`, `OBSERVES`, `CONTROLS`, `ILLUMINATES`, `CONDITIONS`, `PROTECTS`. | PASS |
| AC-008 | `ProviderSpatialRef` or equivalent is metadata only; tests assert provider refs are not canonical IDs. | PASS |
| AC-009 | `RoomDiscoveryFact` or equivalent materializes `RoomNode` and advances topologyVersion. | PASS |
| AC-010 | `ZoneDiscoveryFact` or equivalent materializes `ZoneNode` and validates parent room. | PASS unless explicitly deferred by architect-approved report |
| AC-011 | Device materialization creates or updates primary `LOCATED_IN` relation(s) consistent with `DeviceNode.roomId/zoneId`. | PASS |
| AC-012 | Endpoint materialization creates or updates primary `LOCATED_IN` relation(s) consistent with `EndpointNode.roomId/zoneId`, or explicitly documents inherited placement semantics if no endpoint-level relation is created. | PASS |
| AC-013 | Primary endpoint placement resolution prefers endpoint-level placement over inherited device placement. | PASS |
| AC-014 | Device direct placement fields remain consistent with relation graph. | PASS |
| AC-015 | Endpoint direct placement fields remain consistent with relation graph. | PASS |
| AC-016 | `RoomNode.deviceIds/endpointIds` remain secondary indices and consistent where present. | PASS |
| AC-017 | `ZoneNode.deviceIds/endpointIds` remain secondary indices and consistent where present. | PASS |
| AC-018 | Consistency mismatch test exists and fails/rejects invalid relation or reports consistency error. | PASS |
| AC-019 | Relation target validation rejects missing room/zone targets; room/zone removal remains out of scope. | PASS |
| AC-020 | Room, zone and spatial relation structural mutation increments topologyVersion. | PASS |
| AC-021 | Duplicate/noop/rejected spatial facts preserve topologyVersion. | PASS |
| AC-022 | Existing state/health non-structural behavior remains unchanged. | PASS |
| AC-023 | Accepted structural room/zone/relation mutation emits or records compatible `TopologyChanged`. | PASS |
| AC-024 | Rejected/noop/duplicate facts do not emit `TopologyChanged`. | PASS |
| AC-025 | Query/read boundary can resolve primary placement and relation lookups. | PASS |
| AC-026 | `spatialRelations` persist/recover through seed persistence path. | PASS |
| AC-027 | Core Snapshot Query remains canonical; no projection fields are introduced. | PASS |
| AC-028 | No Effective View / Projection logic in SC-C. | PASS |
| AC-029 | No Session, Identity, Authority, Policy, Surface or Hub memory dependency. | PASS |
| AC-030 | No SC-B runtime, NATS, JetStream, Vert.x or real SC-D adapter required. | PASS |
| AC-031 | `H2BaseTopologyRepository.save(...)` remains structural-only and does not write health/state. | PASS |
| AC-032 | `DefaultTopologyMaterializationService` does not import or type against `H2BaseTopologyRepository`; it preserves MU-012 port boundary. | PASS |
| AC-033 | All pre-existing tests pass. Baseline before MU-013: 28 tests, 0 failures. | PASS |
| AC-034 | New tests cover room, zone, LOCATED_IN, primary placement, topologyVersion, recovery and boundary exclusions. | PASS |
| AC-035 | `mvn test` succeeds locally after implementation. | PASS |

---

## 3. Minimum expected test evidence

Expected new or extended tests:

```text
RoomZoneTopologySeedTest
  - roomDiscoveryFactMaterializesRoomAndAdvancesTopologyVersion
  - zoneDiscoveryFactMaterializesZoneUnderExistingRoom
  - zoneDiscoveryFactWithUnknownRoomIsRejected
  - deviceMaterializationCreatesLocatedInRelationsForResolvedRoomAndZone
  - endpointMaterializationCreatesLocatedInRelationsConsistentWithEndpointFields
  - resolvePrimaryPlacementReturnsCanonicalPlacement
  - duplicateRoomDiscoveryFactDoesNotAdvanceTopologyVersion
  - spatialRelationsSurviveRepositoryRecreation
  - relationTargetMissingIsRejectedWithoutSilentOrphan
  - relationGraphAndSecondaryIndicesRemainConsistent

ScCoreKernelHardeningTest or equivalent
  - noProjectionSessionAuthorityPolicyLeakageAfterRoomZoneSeed

PersistenceBoundaryHardeningTest or equivalent
  - structuralSpatialRelationPersistenceDoesNotWriteHealthOrState

MaterializationStatePortBoundaryTest or equivalent
  - materializerDoesNotDependOnH2AfterSpatialMaterialization
```

The exact class names may differ, but the evidence coverage must be present.

---

## 4. Implementation-report checklist

The implementation report MUST record:

```text
- branch and commit;
- baseline commit;
- files changed;
- whether all DEC-013-001 through DEC-013-009 were followed;
- test command and result;
- number of tests run/failures/errors/skipped;
- AC-001 through AC-035 status;
- whether any stop condition was triggered;
- whether any deferred scope was accidentally touched;
- whether INDEX/SYNC/PDR governance update is required after closure.
```
