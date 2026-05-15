# Acceptance Map — MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001

```text
Document ID:        AM-SOV-SC-C-MATERIALIZATION-STATE-PORT-001
MIR:                MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001 v0.1.0-draft
MU:                 MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001
Operational Slot:   MU-012
Evidence Path:      docs/mir/mir-012/
Acceptance Target:  Validated L4
Audit:              CSA-MU-012 v0.1.1-merged
Status:             Draft / execution package
```

---

## 1. Purpose

This document maps the acceptance criteria declared in `MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001` to the concrete implementation evidence expected from MU-012.

MU-012 closes `DEBT-007-002` by extracting a materialization state boundary so that `DefaultTopologyMaterializationService` no longer depends directly on the H2 persistence adapter.

---

## 2. Validation command

Expected validation command:

```bash
mvn test
```

Expected result:

```text
All current repository tests pass.
No failures.
No errors.
No skipped tests unless already present in baseline.
```

Baseline from CSA-MU-012:

```text
28 tests
0 failures
0 errors
0 skipped
```

---

## 3. Acceptance criteria map

| AC | Criterion | Expected evidence | Status |
|---:|---|---|---|
| AC-001 | `DefaultTopologyMaterializationService` no longer imports `H2BaseTopologyRepository`. | Source inspection; boundary regression test if present. | Pending |
| AC-002 | `DefaultTopologyMaterializationService` no longer has a field typed as `H2BaseTopologyRepository`. | Source inspection; reflection/assertion over fields. | Pending |
| AC-003 | A materialization state port exists in SC-C core port layer, or an equivalent boundary is justified. | New `TopologyMaterializationStatePort` in `com.sovereign.connect.core.topology.port`. | Pending |
| AC-004 | H2 persistence supports the new materialization boundary without moving H2-specific concerns into core domain logic. | `H2BaseTopologyRepository implements TopologyMaterializationStatePort`; no H2/JDBC imports in materializer. | Pending |
| AC-005 | `DeviceDiscoveryFact` materialization behavior remains unchanged. | Existing materialization tests pass. | Pending |
| AC-006 | `EndpointDiscoveryFact` materialization behavior remains unchanged. | Existing materialization tests pass; initial health still explicit. | Pending |
| AC-007 | `CapabilityDiscoveryFact` materialization behavior remains unchanged. | Existing materialization tests pass. | Pending |
| AC-008 | `DeviceStateFact` materialization persists device state explicitly and does not advance topologyVersion. | Existing tests plus unchanged state-write path through port. | Pending |
| AC-009 | `HealthFact` materialization persists endpoint health explicitly and does not advance topologyVersion. | Existing tests plus unchanged health-write path through port. | Pending |
| AC-010 | Initial endpoint `UNKNOWN` health introduced by MU-011 remains explicitly persisted during endpoint materialization. | `EndpointDiscoveryFact` path still calls `saveEndpointHealth(...)` via the new port. | Pending |
| AC-011 | Accepted structural mutations still advance topologyVersion exactly as before. | Existing topology materialization / topologyVersion tests pass. | Pending |
| AC-012 | Duplicate facts remain rejected without topologyVersion advancement. | Existing duplicate-fact tests pass. | Pending |
| AC-013 | Invalid facts remain rejected without topologyVersion advancement. | Existing invalid-fact tests pass. | Pending |
| AC-014 | Unauthorized adapter facts remain rejected without topologyVersion advancement. | Existing unauthorized-adapter tests pass. | Pending |
| AC-015 | Core Snapshot Query can still observe materialized topology, device state and endpoint health after repository/service recreation. | Existing snapshot/recovery tests pass. | Pending |
| AC-016 | MU-011 regression tests remain passing. | `PersistenceBoundaryHardeningTest` passes. | Pending |
| AC-017 | All current SC-C tests remain passing. | `mvn test` result. | Pending |
| AC-018 | No production schema, migration mechanism or storage technology decision is introduced. | Source/package diff; implementation report confirmation. | Pending |
| AC-019 | No Room/Zone Topology, `TopologySpatialRelation`, `RoomDiscoveryFact` or `ZoneDiscoveryFact` implementation is introduced. | Source/package diff; implementation report confirmation. | Pending |
| AC-020 | No SC-B, SC-D, NATS, JetStream, Vert.x, Projection, Hub, Session, Identity, Authority, Policy or Surface dependency is introduced. | Source/package diff; dependency/import scan; implementation report confirmation. | Pending |
| AC-021 | Implementation report records the final port shape and justifies sufficiency. | `implementation-report.md` final section. | Pending |
| AC-022 | Implementation report states whether any materialization/persistence boundary debt remains deferred. | `implementation-report.md` deferred-debt section. | Pending |
| AC-023 | If new coupling is discovered, the Code Surface Audit is updated and architect-reviewed before continuing. | Either “none discovered” or updated CSA with review note. | Pending |

---

## 4. Required architecture regression

The implementation SHOULD extend the existing boundary assertions to verify at least:

```text
DefaultTopologyMaterializationService exposes no field typed as H2BaseTopologyRepository.
DefaultTopologyMaterializationService exposes no public constructor parameter typed as H2BaseTopologyRepository.
DefaultTopologyMaterializationService does not import H2BaseTopologyRepository.
```

The assertion may use reflection over fields and constructors, or an equivalent stable source/architecture check already used in the repository.

---

## 5. Critical invariants to preserve

```text
INV-012-001: SC-C owns materialization.
INV-012-002: SC-D emits facts; SC-C materializes.
INV-012-003: Structural mutation semantics remain in BaseTopologyService.
INV-012-004: State/health facts do not advance topologyVersion.
INV-012-005: Rejected facts do not advance topologyVersion.
INV-012-006: MU-011 persistence separation remains intact.
INV-012-007: Core Snapshot Query source-of-truth remains unchanged.
INV-012-008: No technology leakage into materialization service.
```

---

## 6. Explicit non-acceptance signals

Implementation MUST be rejected if any of the following occur:

```text
DefaultTopologyMaterializationService still imports H2BaseTopologyRepository.
DefaultTopologyMaterializationService still exposes H2BaseTopologyRepository in fields or constructors.
TopologyMaterializationStatePort includes save(HabitatBaseTopology).
The new port includes JDBC, H2, SQL, DataSource, ObjectMapper or migration concerns.
H2BaseTopologyRepository.save(HabitatBaseTopology) regains health/state side effects.
Room/Zone Topology or TopologySpatialRelation is implemented in this MU.
SC-B, SC-D, NATS, JetStream, Projection, Hub, Session, Identity, Authority, Policy or Surface dependencies are introduced.
```

---

## 7. Evidence checklist for implementation report

The final `implementation-report.md` MUST include:

```text
- Branch name.
- Commit hash.
- Test command.
- Total tests / failures / errors / skipped.
- Production files changed.
- Test files changed.
- Final TopologyMaterializationStatePort shape.
- Confirmation that materializer no longer imports H2BaseTopologyRepository.
- Confirmation that materializer has no H2BaseTopologyRepository field/constructor surface.
- Confirmation that MU-011 persistence separation remains intact.
- AC-001 through AC-023 result table.
- Remaining deferred debt, if any.
```
