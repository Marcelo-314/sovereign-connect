# MU-013 Architect Decision Confirmation

```text
Artifact: docs/mir/mir-013/decision-confirmation.md
MIR: MIR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 v0.1.0-draft
CSA: CSA-MU-013 v0.1.1-merged
Status: Approved for execution package
Date: 2026-05-15
```

## Confirmed decisions

```text
DEC-013-001:
  Add spatialRelations to HabitatBaseTopology as seed structural relation storage.

DEC-013-002:
  Add REJECT_AMBIGUOUS_SPATIAL_BINDING and reserve QUARANTINE_PENDING_REVIEW.

DEC-013-003:
  Extend TopologyChanged additively with affectedRoomIds / affectedZoneIds.

DEC-013-004:
  Add ROOM_ADDED and ZONE_ADDED to TopologyChangeKind.

DEC-013-005:
  Keep room/zone removal out of MU-013; validate against orphan relation targets.

DEC-013-006:
  Keep all structural mutations through BaseTopologyService.

DEC-013-007:
  Keep final persistence schema and migrations deferred.

DEC-013-008:
  Do not introduce unplaced device/endpoint support in MU-013.

DEC-013-009:
  Treat DeviceDiscoveryFact roomHint/zoneHint as input evidence and create
  LOCATED_IN relation(s) as persistent canonical output after accepted materialization.
```

## Execution consequence

The execution package is authorized to encode these decisions as implementation instructions.

The implementation remains bounded to `LOCATED_IN` seed semantics only. Semantic relations such as `AFFECTS`, `OBSERVES`, `CONTROLS`, `ILLUMINATES`, `CONDITIONS` and `PROTECTS` remain reserved.
