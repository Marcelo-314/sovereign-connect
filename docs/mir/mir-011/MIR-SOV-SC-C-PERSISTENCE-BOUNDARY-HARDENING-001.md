# MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001

## Persistence Boundary Hardening

Document ID: MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001  
Title: Persistence Boundary Hardening  
Version: v0.1.0-draft  
Status: Draft  
Date: 2026-05-14  
Corpus: Sovereign Connect  
Type: MIR  
Plane: SC-C  
Materialization Unit: MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001  
Operational Slot: MU-011  
Expected Acceptance Level: L4  
Implementation Surface: Non-Greenfield  
Evidence Path: `docs/mir/mir-011/`

---

## Depends on

- `PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.2-draft`
- `PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.5-draft`
- `INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.8-draft`
- `SYNC-SOV-SC-MU-BACKLOG-001 v0.1.9-draft`
- `PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft`
- `PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 v0.1.0-draft`
- `PDR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001 v0.1.0-draft`
- `MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 v1.0.0-accepted`
- `MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 v1.0.0-accepted`
- `MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 v1.0.0-accepted`
- `MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001 v1.0.0-accepted`
- `CSA-MU-011 v0.1.1-merged`

---

## Related

- `MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001`, downstream / out of scope
- `PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001`, downstream / out of scope
- `MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001`, downstream / out of scope
- `PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001`, downstream / out of scope
- `SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001`, downstream / out of scope
- `SDD-SOV-SC-C-RECOVERY-001`, downstream / out of scope
- `SDD-SOV-SC-C-TEMPORAL-ENGINE-001`, downstream / out of scope

---

# 0. Purpose

This MIR opens the implementation descent for `MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001`.

Its purpose is to close `DEBT-007-001` by separating structural topology persistence from device/endpoint health and state persistence in the existing seed repository implementation.

This MU is a hardening MU. It does not introduce a production persistence schema, a final storage technology decision, a migration framework, TemporalActs storage, terminal request records, SC-B runtime, SC-D runtime, Projection, Hub, Authority, Policy or Surface concerns.

The immediate target is the existing H2/JDBC seed implementation, especially the current coupling in `H2BaseTopologyRepository.save(HabitatBaseTopology)`.

---

# 1. Materialization Unit

```text
MU ID:             MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
Operational Slot:  MU-011
Title:             Persistence Boundary Hardening
Plane:             SC-C
Type:              Kernel / Persistence Hardening MU
Status:            MIR Drafted
Expected Level:    L4
```

## 1.1 Primary debt closed

```text
DEBT-007-001:
  Full separation of structural topology persistence from endpoint health persistence remains open.
```

## 1.2 Criteria advanced

This MU advances the following Production Readiness criteria:

- `C-001 — Durable correctness`
- `C-002 — Persistence separation`
- `C-004 — Transactional semantics`, seed-level only
- `C-009 — Regression harness`

It does not close production schema, recovery SDD, storage technology ADR, idempotency/terminal request state, TemporalActs, outbox/ledger or migration policy.

---

# 2. Artifact Bundle

## 2.1 Normative artifacts

- `PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.2-draft`
- `PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.5-draft`
- `PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft`
- `PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 v0.1.0-draft`
- `PDR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001 v0.1.0-draft`

## 2.2 Execution artifacts

Canonical package path:

```text
docs/mir/mir-011/
  MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001.md
  code-surface-audit.md
  acceptance-map.md
  context.md
  codex-prompt.md
  implementation-report.md
```

## 2.3 Required Code Surface Audit

Required and already prepared for review:

```text
docs/mir/mir-011/code-surface-audit.md
```

Audit identity:

```text
Audit ID: CSA-MU-011
Version:  v0.1.1-merged
Status:   Draft / ready for architect review
Surface:  Non-Greenfield
```

The audit is binding for this MIR. If implementation discovers new persistence coupling, hidden side effects, missing tests, or a corpus contradiction, the audit MUST be updated before implementation continues.

---

# 3. Readiness Assessment

## 3.1 Readiness decision

This MU is ready for MIR because:

1. SC-C seed persistence is already implemented and validated at L4.
2. Core Snapshot Query reads health/state from persistence/read ports.
3. MU-007 exposed and patched an endpoint-health overwrite defect.
4. The remaining problem is now a bounded persistence responsibility split.
5. Code Surface Audit has identified the affected surface and implementation strategy.

## 3.2 Surface category

```text
Non-Greenfield — full Code Surface Audit required.
```

Reason:

This MU modifies existing persistence behavior and must preserve regression semantics across MU-004, MU-006, MU-007 and MU-010.

## 3.3 Main implementation thesis

`H2BaseTopologyRepository.save(HabitatBaseTopology)` MUST become a structural topology save only.

Endpoint/device health and state persistence MUST be explicit persistence operations, not hidden side effects of structural topology save.

---

# 4. Implementation Scope

This MU may implement the following changes.

## 4.1 Structural save separation

Remove endpoint health persistence side effects from:

```text
H2BaseTopologyRepository.save(HabitatBaseTopology)
```

After this MU, `save(HabitatBaseTopology)` MUST write only the structural topology snapshot and associated structural snapshot metadata.

## 4.2 Explicit initial endpoint health persistence

When a new endpoint is materialized, the initial `UNKNOWN` endpoint health MUST be persisted explicitly through the existing concrete health persistence path.

Expected call site:

```text
DefaultTopologyMaterializationService.materializeEndpoint(...)
```

Rationale:

Today, the initial `UNKNOWN` health entry is persisted because `save(HabitatBaseTopology)` also writes endpoint health when no durable row exists. Once that side effect is removed, the initial health write must be explicit.

## 4.3 Preserve explicit HealthFact persistence

`DefaultTopologyMaterializationService.materializeHealth(...)` already writes endpoint health explicitly. This behavior MUST be preserved.

## 4.4 Preserve explicit DeviceState persistence

`DefaultTopologyMaterializationService.materializeDeviceState(...)` already writes device state explicitly. This behavior MUST be preserved.

## 4.5 Preserve mutation record separation

`appendMutationRecord(...)`, if present, MUST remain separate from structural `save(...)`.

## 4.6 Preserve service-level endpoint health durability

If `BaseTopologyService.updateEndpointHealth(...)` currently relies on repository `save(...)` to durably persist endpoint health, the implementation MUST preserve durable behavior through a minimal explicit persistence path.

This preservation MUST NOT become the final `TopologyMaterializationStatePort`. Port extraction remains MU-012.

## 4.7 Regression tests

The implementation MUST add or update regression tests demonstrating that:

1. structural saves do not touch `endpoint_health`;
2. initial endpoint `UNKNOWN` health is persisted explicitly on endpoint materialization;
3. endpoint health written by a `HealthFact` survives subsequent structural saves;
4. service-level endpoint health update remains durable, if that behavior exists in the current seed surface;
5. health/state writes do not advance `topologyVersion`;
6. recovery and Core Snapshot Query still read correct durable health/state.

---

# 5. Negative Scope

This MU MUST NOT implement:

- `TopologyMaterializationStatePort` extraction;
- new SC-C port interfaces, unless the Code Surface Audit is updated and architecturally approved;
- production schema finalization;
- Flyway, Liquibase or migration policy;
- storage technology selection;
- TemporalActs schema;
- terminal request state schema;
- outbox/ledger semantics;
- RoomDiscoveryFact;
- ZoneDiscoveryFact;
- DeviceRoomAssignmentFact;
- `TopologySpatialRelation`;
- Room/Zone materialization from adapter facts;
- Canonical Topology Interface;
- SC-B runtime;
- NATS / JetStream;
- SC-D adapter integration;
- Projection / Effective View;
- Hub memory;
- Authority / Policy;
- Session / Identity;
- Surface UX.

This MU MUST NOT alter canonical topology ownership.

This MU MUST NOT change the meaning of `topologyVersion`.

This MU MUST NOT treat provider-native identifiers as canonical lookup keys.

---

# 6. Required Invariants

## INV-011-001 — Structural save is structural only

`H2BaseTopologyRepository.save(HabitatBaseTopology)` MUST NOT persist endpoint health, device health, endpoint state or device state as side effects.

## INV-011-002 — HealthFact health survives structural mutation

Endpoint health written by a `HealthFact` MUST survive later structural topology saves.

This must pass without relying on merge semantics inside `save(HabitatBaseTopology)`.

## INV-011-003 — Initial endpoint health exists durably

A newly materialized endpoint MUST have durable initial `UNKNOWN` endpoint health if the seed contract currently exposes health immediately through `CoreSnapshotReadPort`.

If the implementation proposes otherwise, it MUST trigger architect review and MIR/audit update.

## INV-011-004 — Health writes do not advance topologyVersion

Endpoint/device health writes MUST NOT advance `topologyVersion`.

## INV-011-005 — State writes do not advance topologyVersion

Endpoint/device state writes MUST NOT advance `topologyVersion`.

## INV-011-006 — Structural mutations continue to advance topologyVersion

Accepted structural mutations MUST continue to advance `topologyVersion` according to existing MU-002 / MU-007 semantics.

## INV-011-007 — Recovery reads durable health/state

`CoreSnapshotQueryService` and `CoreSnapshotReadPort` MUST continue to read durable health/state after repository/service recreation.

## INV-011-008 — BaseTopologyService.updateEndpointHealth durability path

If `BaseTopologyService.updateEndpointHealth(...)` currently updates aggregate health and persists it by calling repository `save(...)`, MU-011 MUST preserve the durable health update behavior through an explicit persistence path.

This MUST NOT be generalized into the final materialization state port. That remains MU-012.

## INV-011-009 — DeviceState and mutation records remain separate

Device state and mutation records, already separate from structural `save(...)`, MUST remain separate.

## INV-011-010 — No Room/Zone scope absorption

RoomNode and ZoneNode already exist partially in the model and may remain part of the structural topology snapshot. MU-011 MUST NOT implement Room/Zone topology seed semantics.

---

# 7. Minimal Acceptance Criteria

- AC-001: The Code Surface Audit is present at `docs/mir/mir-011/code-surface-audit.md` and accepted before implementation.
- AC-002: Existing SC-C kernel tests remain PASS.
- AC-003: `H2BaseTopologyRepository.save(HabitatBaseTopology)` no longer writes endpoint health as a side effect.
- AC-004: Structural topology saves do not overwrite durable endpoint health.
- AC-005: Structural topology saves do not recreate missing endpoint health rows unless the caller explicitly writes health.
- AC-006: New endpoint materialization explicitly persists initial `UNKNOWN` endpoint health, or the MIR/audit is updated with an accepted alternate decision.
- AC-007: `HealthFact` materialization persists endpoint health explicitly.
- AC-008: `DeviceStateFact` materialization persists device state explicitly.
- AC-009: Endpoint health written by `HealthFact` survives later capability materialization / structural save.
- AC-010: Endpoint health survives repository/service recreation after a later structural save.
- AC-011: Service-level endpoint health update remains durable if it was durable before MU-011.
- AC-012: Health writes do not advance `topologyVersion`.
- AC-013: State writes do not advance `topologyVersion`.
- AC-014: Structural accepted mutations still advance `topologyVersion`.
- AC-015: Core Snapshot Query reads endpoint health and device state from durable/read boundaries after recovery.
- AC-016: No new SC-B, SC-D, NATS, JetStream, Projection, Hub, Session, Identity, Authority, Policy or Surface dependency is introduced.
- AC-017: `TopologyMaterializationStatePort` is not introduced in this MU.
- AC-018: No production schema or migration framework is introduced.
- AC-019: Implementation Report lists files changed, tests added, tests run and whether any residual persistence debt remains.
- AC-020: Any new hidden coupling discovered during implementation updates the Code Surface Audit before continuation.

---

# 8. Failure Signals

Critical failure signals:

1. `H2BaseTopologyRepository.save(...)` still writes endpoint health after MU-011.
2. Endpoint health written by `HealthFact` can be overwritten by later structural save.
3. Initial endpoint health disappears from durable read path without accepted MIR/audit decision.
4. `BaseTopologyService.updateEndpointHealth(...)` loses durable behavior without accepted MIR/audit decision.
5. Health/state writes advance `topologyVersion`.
6. Structural mutations stop advancing `topologyVersion`.
7. Core Snapshot Query reads health/state from aggregate JSON instead of durable health/state boundaries.
8. Implementation extracts `TopologyMaterializationStatePort` in this MU.
9. Implementation changes `BaseTopologyRepository` into a production persistence boundary without SDD.
10. Implementation introduces production schema/migration concerns.
11. Implementation alters Room/Zone topology semantics.
12. Implementation introduces SC-B, SC-D, NATS, JetStream, Projection, Hub, Authority, Policy, Session, Identity or Surface dependencies.
13. Tests pass only by weakening existing assertions.
14. Code Surface Audit is not updated after discovering a new implicit contract.

---

# 9. Upstream Patch Protocol

If implementation discovers that the current corpus cannot express a safe persistence boundary, the implementation MUST stop and report the issue.

Valid upstream actions:

- update `docs/mir/mir-011/code-surface-audit.md`;
- patch this MIR;
- register a SYNC note;
- open a new MU;
- defer a discovered issue to `MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001`;
- defer a schema issue to `SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001`.

The implementation MUST NOT silently resolve corpus contradiction in favor of repository convenience.

Known corpus feedback already captured by the audit:

```text
RoomNode and ZoneNode already exist partially in the model.
Future Room/Zone artifacts should treat this as partial model-level implementation,
not as MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 completion.
```

---

# 10. Codex Prompt Reference

The implementation prompt is not inlined in this MIR.

Required execution package files:

```text
docs/mir/mir-011/code-surface-audit.md
docs/mir/mir-011/acceptance-map.md
docs/mir/mir-011/context.md
docs/mir/mir-011/codex-prompt.md
docs/mir/mir-011/implementation-report.md
```

Rules:

- `codex-prompt.md` MUST derive from this MIR and the approved Code Surface Audit.
- `codex-prompt.md` MUST NOT be authored before this MIR and `code-surface-audit.md` are accepted.
- `context.md` MUST include the persistence invariants and negative scope from this MIR.
- `acceptance-map.md` MUST preserve AC-001 through AC-020 numbering.

---

# 11. Suggested Branch

```text
feat/sc-c-persistence-boundary-hardening
```

---

# 12. Suggested Commit

Opening MIR / execution package:

```text
docs(sc-c): open persistence boundary hardening mir
```

Implementation commit, later:

```text
fix(sc-c): separate structural topology and health persistence
```

---

# 13. Post-Implementation Report Requirements

The implementation report MUST include:

1. summary;
2. branch and commit hash;
3. files changed;
4. tests added;
5. tests modified;
6. tests run;
7. result of AC-001 through AC-020;
8. invariants preserved;
9. Code Surface Audit version used;
10. Code Surface Audit updates, if any;
11. persistence behavior before/after summary;
12. evidence that `save(HabitatBaseTopology)` no longer writes health;
13. evidence that initial endpoint health remains durable or accepted alternate decision;
14. evidence that HealthFact health survives structural save and recovery;
15. evidence that service-level endpoint health durability remains correct or accepted alternate decision;
16. deviations;
17. corpus issues discovered;
18. recommended follow-up for MU-012.

---

# 14. Changelog

## v0.1.0-draft

- Opens MIR for `MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001`.
- Declares `DEBT-007-001` as primary closure target.
- Classifies implementation surface as Non-Greenfield.
- Requires `CSA-MU-011` before implementation.
- Defines structural save separation, explicit initial endpoint health persistence and durable health/state preservation as scope.
- Excludes `TopologyMaterializationStatePort`, production schema, TemporalActs, Room/Zone seed, SC-B, SC-D and Projection concerns.
- Defines AC-001 through AC-020.

---

# 15. Dictamen

`MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001 v0.1.0-draft` is ready for review.

It authorizes preparation of the execution package only after the Code Surface Audit is accepted.

It does not yet authorize Codex implementation.

Next required artifacts:

```text
docs/mir/mir-011/acceptance-map.md
docs/mir/mir-011/context.md
docs/mir/mir-011/codex-prompt.md
```
