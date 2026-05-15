# MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001

## Materialization State Port Hardening

**Document ID:** MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001  
**Title:** Materialization State Port Hardening  
**Version:** v0.1.0-draft  
**Status:** Draft  
**Date:** 2026-05-14  
**Corpus:** Sovereign Connect  
**Type:** MIR  
**Plane:** SC-C  
**MU:** MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001  
**Operational Slot:** MU-012  
**Evidence Path:** `docs/mir/mir-012/`  
**Acceptance Target:** Validated L4  
**Surface Category:** Non-Greenfield — full Code Surface Audit required  
**Closes:** DEBT-007-002 — `TopologyMaterializationStatePort` extraction

---

## Changelog v0.1.0-draft

Initial draft.

This version:

1. Opens MU-012 as the next SC-C Production Readiness implementation descent after MU-011 closure.
2. Defines the purpose of extracting `TopologyMaterializationStatePort` or an equivalent explicitly named materialization state boundary.
3. Declares that `DefaultTopologyMaterializationService` MUST NOT depend directly on `H2BaseTopologyRepository` after this MU.
4. Preserves MU-011 persistence separation and `EndpointHealthWritePort` behavior.
5. Defers Room/Zone Topology, `TopologySpatialRelation`, Canonical Topology Interface, production persistence schema, NATS/JetStream, SC-D and Projection work.
6. Requires a Code Surface Audit before `context.md`, `codex-prompt.md` or implementation authorization.

---

## Depends on

- `PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.3-draft`
- `INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.9-draft`
- `SYNC-SOV-SC-MU-BACKLOG-001 v0.1.10-draft`
- `PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.5-draft`
- `MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001 v1.0.0-accepted`
- `MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 v1.0.0-accepted`
- `MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001 v0.1.1-draft`
- `PDR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001 v0.1.0-draft`
- `PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.7-draft`

---

## Related

- `MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001` — validated L4; closes DEBT-007-001.
- `PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001` — planned after MU-012.
- `MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001` — planned after Room/Zone PDR.
- `PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001` — planned after Room/Zone topology seed.
- `SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001` — downstream production schema; not in this MU.
- `SDD-SOV-SC-C-RECOVERY-001` — downstream recovery semantics; not in this MU.

---

# 0. Purpose

This MIR authorizes the scoping and eventual implementation of `MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001`.

The purpose of this MU is to remove the direct dependency of SC-C topology materialization logic on the concrete H2 persistence adapter.

Canonical objective:

```text
DefaultTopologyMaterializationService MUST depend on SC-C ports,
not on H2BaseTopologyRepository.
```

This MU closes `DEBT-007-002` without expanding into Room/Zone topology, SC-D adapter implementation, SC-B runtime, NATS/JetStream binding, production schema design, or Projection.

---

# 1. Background

MU-007 validated SC-C-owned topology materialization from discovery/state/health facts, but left a known follow-up debt: the materializer still depended directly on concrete persistence.

MU-011 then separated structural topology persistence from endpoint health persistence and closed `DEBT-007-001`. MU-012 is the next hardening step and addresses the remaining boundary issue:

```text
DEBT-007-002:
  TopologyMaterializationStatePort extraction remains deferred.
```

The production-readiness rationale is that SC-C materialization may later serve as a stable SC-D-facing integration boundary. That boundary cannot be considered stable while the materializer imports or depends directly on `H2BaseTopologyRepository`.

---

# 2. Problem statement

Current seed materialization logic uses a core service plus a concrete persistence adapter.

The problematic pattern is:

```text
DefaultTopologyMaterializationService
  depends on BaseTopologyService             acceptable core service dependency
  depends on H2BaseTopologyRepository        concrete adapter dependency; must be removed
```

The materializer currently needs persistence-facing operations for:

- reading the current topology for precondition checks;
- reading current topologyVersion for rejected decisions;
- writing device state facts;
- writing endpoint health facts;
- preserving seed behavior for initial endpoint health introduced by MU-011.

Those operations are legitimate materialization state needs. The problem is not that the materializer needs them. The problem is that it currently obtains them through a concrete H2 adapter rather than an SC-C port.

---

# 3. Implementation surface classification

```text
Surface category:
  Non-Greenfield — full Code Surface Audit required.
```

Reason:

This MU touches already implemented and tested SC-C behavior:

- topology materialization;
- BaseTopologyService integration;
- H2 persistence adapter boundaries;
- Core Snapshot read behavior;
- health/state write paths;
- topologyVersion mutation/non-mutation invariants;
- tests from MU-004, MU-006, MU-007, MU-010 and MU-011.

A `docs/mir/mir-012/code-surface-audit.md` artifact MUST be produced and reviewed before `context.md`, `codex-prompt.md` or implementation authorization.

---

# 4. Scope

## 4.1 Positive scope

This MU MAY include:

1. Defining `TopologyMaterializationStatePort` or a functionally equivalent materialization state boundary in `com.sovereign.connect.core.topology.port`.
2. Moving materializer read/write state dependencies behind that port.
3. Updating `DefaultTopologyMaterializationService` so it no longer imports or stores `H2BaseTopologyRepository`.
4. Having `H2BaseTopologyRepository` implement the new materialization state port if the Code Surface Audit confirms this is the minimal safe adapter path.
5. Preserving existing materialization behavior for:
   - `DeviceDiscoveryFact`;
   - `EndpointDiscoveryFact`;
   - `CapabilityDiscoveryFact`;
   - `DeviceStateFact`;
   - `HealthFact`.
6. Preserving current topologyVersion behavior:
   - accepted structural mutations advance topologyVersion;
   - health/state facts do not advance topologyVersion;
   - rejected facts do not advance topologyVersion.
7. Preserving MU-011 behavior:
   - structural saves remain structural-only;
   - endpoint health persistence remains explicit;
   - initial endpoint UNKNOWN health remains explicitly persisted during endpoint materialization;
   - `endpoint_health` / `CoreSnapshotReadPort` remains health query source of truth.
8. Adding regression tests proving the materializer is no longer coupled to `H2BaseTopologyRepository` directly.
9. Preserving all current SC-C tests.

## 4.2 Negative scope

This MU MUST NOT include:

- Room/Zone Topology contract or implementation.
- `TopologySpatialRelation`.
- `RoomDiscoveryFact` or `ZoneDiscoveryFact`.
- Provider-side room/area canonicalization.
- Canonical Topology Interface PDR.
- Production persistence schema design.
- Flyway/Liquibase or migration policy.
- TemporalActs implementation.
- Terminal request state.
- Outbox/ledger implementation.
- SC-B runtime.
- NATS Core.
- JetStream.
- Vert.x.
- SC-D adapter manifest implementation.
- Adapter lifecycle implementation.
- Projection / Effective View.
- Hub, Session, Identity, Authority, Policy or Surface concerns.

---

# 5. Decisions

## MIR-012-D-001 — Materializer depends on ports, not concrete adapters

`DefaultTopologyMaterializationService` MUST NOT depend directly on `H2BaseTopologyRepository` after MU-012.

The implementation MUST remove concrete adapter imports from the materializer.

## MIR-012-D-002 — Introduce materialization state boundary

A materialization state boundary MUST be introduced.

Preferred name:

```text
TopologyMaterializationStatePort
```

The Code Surface Audit MAY recommend an equivalent split or naming only if it proves that the alternative better preserves existing boundaries and does not broaden scope.

## MIR-012-D-003 — Keep structural mutation ownership unchanged

This MU MUST NOT replace `BaseTopologyService` as the service responsible for structural topology mutation semantics.

The materializer may continue to delegate structural mutations to `BaseTopologyService`.

## MIR-012-D-004 — Keep state/health persistence explicit

Device state and endpoint health persistence MUST remain explicit operations.

They MUST NOT re-enter `H2BaseTopologyRepository.save(HabitatBaseTopology)` or any structural topology save side effect.

## MIR-012-D-005 — No production schema decision

This MU MUST NOT finalize production storage technology, schema layout, migration tooling or recovery policy.

Those concerns remain downstream ADR/SDD work.

## MIR-012-D-006 — No SC-D protocol expansion

This MU prepares a cleaner SC-C materialization boundary but MUST NOT implement SC-D adapter manifest, SC-D discovery protocol or cross-plane fact ingestion.

SC-D remains protocol-conformance work outside this MU.

---

# 6. Required Code Surface Audit focus

The Code Surface Audit MUST inspect and report at least:

1. All imports and fields in `DefaultTopologyMaterializationService` that point to concrete adapters.
2. Every method call from materialization logic into `H2BaseTopologyRepository`.
3. The exact minimal operation set needed by the materializer.
4. Whether existing ports (`BaseTopologyRepository`, `CoreSnapshotReadPort`, `EndpointHealthWritePort`) can be reused or whether a new cohesive `TopologyMaterializationStatePort` is required.
5. Whether introducing a new write port for device state is necessary or whether the new materialization state port should own that operation.
6. Whether `H2BaseTopologyRepository` should implement the new port directly for seed purposes.
7. Whether `InMemoryBaseTopologyRepository` needs changes or whether tests should use a fake/materialization-state test double.
8. Which tests currently depend on H2-backed materialization.
9. Which tests currently depend on in-memory topology mutation behavior.
10. Which regression tests must be added to prove the materializer no longer imports `H2BaseTopologyRepository`.
11. Whether any implementation discovery risks absorbing MU-013 Room/Zone scope.
12. Whether any implementation discovery risks reintroducing the MU-011 persistence coupling.

If the audit discovers that the required refactor is broader than this MIR permits, implementation MUST NOT proceed until the MIR is updated and reviewed.

---

# 7. Invariants

## INV-012-001 — SC-C owns materialization

SC-C remains the owner of canonical topology materialization.

This MU changes the materializer's persistence boundary, not its ownership semantics.

## INV-012-002 — SC-D emits facts; SC-C materializes

This MU MUST NOT allow SC-D or any adapter-facing protocol to emit final topology.

## INV-012-003 — Structural mutation semantics remain in BaseTopologyService

Accepted structural materialization may continue to use `BaseTopologyService` for topology mutation and topologyVersion advancement.

## INV-012-004 — State/health facts do not advance topologyVersion

Device state and endpoint health materialization MUST preserve existing non-structural semantics.

## INV-012-005 — Rejected facts do not advance topologyVersion

Duplicate, invalid and unauthorized facts MUST preserve previous/resulting topologyVersion behavior.

## INV-012-006 — MU-011 persistence separation remains intact

Structural topology saves MUST NOT write endpoint health as a side effect.

## INV-012-007 — Query source-of-truth remains unchanged

Core Snapshot Query behavior MUST remain compatible with existing read ports and persistence semantics.

## INV-012-008 — No technology leakage

This MU MUST NOT introduce SC-B, NATS, JetStream, Vert.x, SC-D runtime, Projection, Hub, Session, Identity, Authority, Policy or Surface dependencies into SC-C materialization.

---

# 8. Expected implementation strategy

The exact implementation strategy is determined by the Code Surface Audit.

The expected minimal strategy is:

1. Add a core port such as:

```text
TopologyMaterializationStatePort
```

2. Include only operations the materializer actually needs, likely in the family:

```text
findByHabitatId / findTopologyForMaterialization
findCurrentVersion
saveDeviceState
saveEndpointHealth
```

3. Make `H2BaseTopologyRepository` implement this port for the seed.
4. Update `DefaultTopologyMaterializationService` constructor and field type to depend on the port, not H2.
5. Preserve existing constructor call sites or update tests according to the audit.
6. Add regression coverage proving no direct materializer dependency on `H2BaseTopologyRepository` remains.
7. Keep all existing materialization behavior stable.

The implementation MUST NOT create a general persistence architecture beyond this materialization-state boundary.

---

# 9. Acceptance criteria

This MU is acceptable if all criteria below pass.

- AC-001: `DefaultTopologyMaterializationService` no longer imports `H2BaseTopologyRepository`.
- AC-002: `DefaultTopologyMaterializationService` no longer has a field typed as `H2BaseTopologyRepository`.
- AC-003: A materialization state port exists in the SC-C core port layer, or the Code Surface Audit justifies an equivalent boundary.
- AC-004: H2 persistence supports the new materialization state boundary without moving H2-specific concerns into core domain logic.
- AC-005: `DeviceDiscoveryFact` materialization behavior remains unchanged.
- AC-006: `EndpointDiscoveryFact` materialization behavior remains unchanged.
- AC-007: `CapabilityDiscoveryFact` materialization behavior remains unchanged.
- AC-008: `DeviceStateFact` materialization persists device state explicitly and does not advance topologyVersion.
- AC-009: `HealthFact` materialization persists endpoint health explicitly and does not advance topologyVersion.
- AC-010: initial endpoint UNKNOWN health introduced by MU-011 remains explicitly persisted during endpoint materialization.
- AC-011: accepted structural mutations still advance topologyVersion exactly as before.
- AC-012: duplicate facts remain rejected without topologyVersion advancement.
- AC-013: invalid facts remain rejected without topologyVersion advancement.
- AC-014: unauthorized adapter facts remain rejected without topologyVersion advancement.
- AC-015: Core Snapshot Query can still observe materialized topology, device state and endpoint health after repository/service recreation.
- AC-016: MU-011 regression tests remain passing.
- AC-017: all current SC-C tests remain passing.
- AC-018: no production schema, migration mechanism or storage technology decision is introduced.
- AC-019: no Room/Zone Topology, `TopologySpatialRelation`, RoomDiscoveryFact or ZoneDiscoveryFact implementation is introduced.
- AC-020: no SC-B, SC-D, NATS, JetStream, Vert.x, Projection, Hub, Session, Identity, Authority, Policy or Surface dependency is introduced.
- AC-021: the implementation report records the final port shape and justifies why it is sufficient for materialization state access.
- AC-022: the implementation report explicitly states whether any materialization/persistence boundary debt remains deferred.
- AC-023: if implementation reveals new coupling, the Code Surface Audit MUST be updated and architect-reviewed before continuing.

---

# 10. Required evidence artifacts

The evidence package MUST be located at:

```text
docs/mir/mir-012/
```

Required artifacts:

```text
MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001.md
code-surface-audit.md
acceptance-map.md
context.md
codex-prompt.md
implementation-report.md
```

`code-surface-audit.md` MUST precede `context.md` and `codex-prompt.md`.

`codex-prompt.md` MUST NOT be authorized until the audit is accepted.

---

# 11. Failure signals

The following are critical failure signals:

1. `DefaultTopologyMaterializationService` still imports `H2BaseTopologyRepository` after implementation.
2. `DefaultTopologyMaterializationService` still has a field typed as `H2BaseTopologyRepository` after implementation.
3. The materializer depends on JDBC, H2, SQL, DataSource, ObjectMapper or adapter-specific persistence details.
4. `BaseTopologyService` is bypassed for structural mutation semantics without explicit MIR update.
5. topologyVersion advancement rules change unintentionally.
6. health/state facts start advancing topologyVersion.
7. rejected facts start advancing topologyVersion.
8. structural topology saves regain health/state write side effects.
9. MU-011 health persistence regressions reappear.
10. Room/Zone Topology or `TopologySpatialRelation` is implemented in this MU.
11. SC-D adapter manifest or discovery protocol is implemented in this MU.
12. SC-B, NATS, JetStream or Vert.x enters SC-C materialization code.
13. Projection, Hub, Session, Identity, Authority, Policy or Surface concepts enter SC-C materialization code.
14. The new port becomes a broad generic repository abstraction that absorbs production schema concerns.
15. The implementation report fails to identify the final port method set.

---

# 12. Open questions for Code Surface Audit

## OQ-012-001 — Port granularity

Should MU-012 introduce one cohesive `TopologyMaterializationStatePort`, or reuse existing ports plus one new write port?

Current recommendation:

```text
Prefer a cohesive TopologyMaterializationStatePort for the materializer.
```

The audit may override this only with explicit rationale.

## OQ-012-002 — H2 implementation of the port

Should `H2BaseTopologyRepository` implement the new port directly for seed purposes?

Current recommendation:

```text
Yes, unless the audit identifies a lower-risk adapter wrapper.
```

## OQ-012-003 — In-memory support

Should `InMemoryBaseTopologyRepository` implement the materialization state port?

Current recommendation:

```text
Do not assume. Let the Code Surface Audit determine whether tests need in-memory support or whether H2-backed tests are sufficient.
```

## OQ-012-004 — Device state write boundary

Should `saveDeviceState(...)` be placed in `TopologyMaterializationStatePort`, or should a narrower `DeviceStateWritePort` be introduced?

Current recommendation:

```text
Prefer placing it in TopologyMaterializationStatePort for this seed, unless the audit proves a narrower split is safer.
```

## OQ-012-005 — Read method naming

Should the read method be named `findByHabitatId`, `findTopology`, or `findTopologyForMaterialization`?

Current recommendation:

```text
Use the name that minimizes call-site disruption while keeping materialization intent clear.
```

---

# 13. Implementation report requirements

The implementation report MUST include:

1. final status: PASS / FAIL / BLOCKED;
2. branch name;
3. commit hash;
4. test command and result;
5. total tests run / failures / errors / skipped;
6. list of production files changed;
7. list of test files changed;
8. final port shape;
9. whether `DefaultTopologyMaterializationService` imports `H2BaseTopologyRepository` after implementation;
10. whether any concrete adapter type remains in materializer fields/constructors;
11. confirmation that MU-011 behavior remains preserved;
12. confirmation that no Room/Zone / SC-B / SC-D / Projection scope was introduced;
13. acceptance map result AC-001 through AC-023;
14. any remaining deferred debt.

---

# 14. Descenso siguiente

After this MIR is accepted:

```text
1. Produce docs/mir/mir-012/code-surface-audit.md.
2. Review and accept the audit.
3. Produce acceptance-map.md.
4. Produce context.md.
5. Produce codex-prompt.md.
6. Authorize implementation only after MIR + audit + execution package are accepted.
```

Next downstream artifact after MU-012 validation:

```text
PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001
```

---

# 15. Suggested branch and commit

Suggested branch for MIR/execution package:

```text
docs/sc-c-materialization-state-port-mir
```

Suggested implementation branch after package approval:

```text
feat/sc-c-materialization-state-port
```

Suggested implementation commit:

```text
refactor(sc-c): extract topology materialization state port
```

---

# 16. Dictamen

`MIR-SOV-SC-C-MATERIALIZATION-STATE-PORT-001 v0.1.0-draft` opens MU-012 as the next SC-C Production Readiness descent.

This MIR authorizes scoping and audit for extracting the materialization state boundary needed to close `DEBT-007-002`.

It does not authorize immediate implementation.

Implementation requires an accepted Code Surface Audit and execution package.
