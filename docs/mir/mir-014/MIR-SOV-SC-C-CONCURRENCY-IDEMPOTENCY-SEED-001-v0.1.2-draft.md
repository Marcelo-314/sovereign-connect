# MIR-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001

## SC-C Concurrency / Idempotency Seed

```text
Document ID: MIR-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001
Title:       SC-C Concurrency / Idempotency Seed
Version:     v0.1.2-draft
Status:      Draft / Pending decision confirmation approval
Date:        2026-05-15
Corpus:      Sovereign Connect
Type:        MIR
Plane:       SC-C
Materialization Unit: MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001
Suggested operational slot: MU-014
Acceptance level target: L4
Scope:       materialization fact replay / duplicate semantic-effect prevention / stale topology revalidation coverage / seed idempotency guardrails
```

---

## Changelog v0.1.2-draft

Patch release after architect note on `DEC-014-006`.

This version:

1. Makes H2-backed materialization decision replay storage the preferred seed implementation.
2. Removes the earlier preference for in-memory replay storage.
3. Brings recovery-visible replay after repository/service recreation into MU-014 scope.
4. Clarifies that the H2 adapter should follow the existing small JdbcTemplate persistence pattern used by seed tables such as `endpoint_health`.
5. Preserves the boundary that MU-014 does not implement generic request-state, production `sc_c_terminal_responses`, TemporalAct fire idempotency or outbox/ledger runtime.

---

## Changelog v0.1.1-draft

Patch release after Code Surface Audit.

This version:

1. Reclassifies the MU as `Non-Greenfield — bounded scope`.
2. Narrows the implementation target from broad request-state / terminal-response idempotency to materialization fact replay and duplicate semantic-effect prevention.
3. Declares `factId` as the seed replay key and canonical entity identity as the seed semantic duplicate key.
4. Removes mandatory generic in-flight request detection from the seed scope.
5. Defers generic command/request idempotency, terminal response persistence, ActionRequest dispatch idempotency, TemporalAct fire idempotency and outbox/ledger implementation.
6. Preserves stale topology revalidation through existing `BaseTopologyService.validateTarget(...)` behavior.
7. Requires `CSA-MU-014 v0.1.1-merged` and `decision-confirmation.md` before context, acceptance-map or Codex prompt authoring.
8. Adds an explicit deferred/out-of-scope ledger for future planning.

---

## Changelog v0.1.0-draft

Initial draft.

Opened `MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001` as the next SC-C Production Readiness implementation descent after Outbox/Ledger SDD baseline.

Disposition: superseded by v0.1.1-draft scope correction.

---

## Depends on

- `Sovereign Connect — Curator & Descent Instructions`
- `PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.5-draft`
- `INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.12-draft`
- `SYNC-SOV-SC-MU-BACKLOG-001 v0.1.13-draft`
- `PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.6-draft`
- `PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001 v0.1.1-draft`
- `PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001 v0.1.1-draft`
- `PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 v0.1.0-draft`
- `PDR-SOV-SC-C-TEMPORAL-ACTS-001 v0.1.1-draft`
- `PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft`
- `PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.7-draft`
- `SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001 v0.1.1-draft`
- `SDD-SOV-SC-C-RECOVERY-001 v0.1.1-draft`
- `SDD-SOV-SC-C-OUTBOX-LEDGER-001 v0.1.1-draft`
- `MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001 — Validated L4`
- `MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001 — Validated L4`
- `MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001 — Validated L4`
- `CSA-MU-014 v0.1.2-merged`

---

## Related

- `SDD-SOV-SC-C-TEMPORAL-ENGINE-001`, downstream
- `MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001`, downstream
- `SDD-SOV-SC-C-OUTBOX-LEDGER-001`, future implementation descent
- `SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001`, outside SC-C
- `MU-SOV-SC-D-ADAPTER-MANIFEST-SEED-001`, deferred SC-D work

---

# 0. Purpose

This MIR authorizes a bounded seed implementation for SC-C materialization idempotency and duplicate semantic-effect prevention.

Its purpose is to validate that repeated topology materialization facts and semantic duplicates do not cause repeated structural effects, do not advance `topologyVersion` twice and do not bypass existing topology validation surfaces.

This MIR does **not** authorize a generic request-state framework, command idempotency framework, TemporalAct fire engine or production outbox/ledger implementation.

---

# 1. Normative thesis

SC-C seed idempotency must first be validated where executable code already exists: topology materialization.

Canonical seed formulation:

```text
factId = replay key for the same materialization fact.
canonical entity identity = semantic duplicate key for seed materialization.
MaterializationDecision replay prevents duplicate effects for repeated facts.
Existing duplicate detection prevents duplicate effects for distinct facts targeting the same canonical entity.
```

This is a seed-level materialization idempotency unit.

It is not yet full product-grade command/request idempotency.

---

# 2. Code Surface Audit requirement

This MIR requires a completed Code Surface Audit before approval.

Required audit:

```text
CSA-MU-014 v0.1.1-merged
```

Audit disposition:

```text
Surface category: Non-Greenfield — bounded scope.
Primary implementation surface: DefaultTopologyMaterializationService.materialize(...).
Primary behavioral target: materialization fact replay and duplicate semantic-effect prevention.
```

The execution package MUST use the audit as normative input.

---

# 3. In scope

MU-014 includes:

```text
1. A seed replay/idempotency port for topology materialization facts.
2. A H2-backed seed replay adapter following the existing H2/JdbcTemplate persistence pattern.
3. Replay of previously recorded MaterializationDecision by habitatId + factId.
4. Recording accepted and rejected MaterializationDecision outcomes.
5. Verification that replay does not re-execute mutation logic.
6. Verification that replay does not advance topologyVersion.
7. Verification that semantic duplicates with different factId are still rejected.
8. Verification that duplicate rejection does not advance topologyVersion.
9. Additional stale topology validation coverage using existing validateTarget(...).
10. Documentation that true production concurrent write safety is provided by SQLite/WAL single-writer semantics, not proven by H2/InMemory tests.
11. Recovery-visible replay after repository/service recreation.
```

---

# 4. Out of scope

MU-014 explicitly excludes:

```text
- generic command/request idempotency;
- full request-state lifecycle;
- terminal response persistence;
- sc_c_terminal_responses implementation;
- in-flight command registry;
- ActionRequest runtime implementation;
- ActionTargetFailure response pipeline implementation;
- ScResponseEnvelope implementation;
- TemporalAct fire idempotency;
- Temporal Engine / scheduler;
- outbox/ledger tables or dispatcher;
- SC-B runtime;
- NATS / JetStream;
- real adapter command execution;
- production SQLite migrations;
- provider binding fingerprint migration;
- Hub / Projection / Session / Identity / Authority / Policy;
- Room/Zone removal lifecycle unless already supported by existing code.
```

---

# 5. Seed decisions

## D-MU014-001 — Replay key

```text
factId is the seed replay key.
```

The same materialization fact submitted twice must return the stored `MaterializationDecision` without repeating materialization logic.

## D-MU014-002 — Semantic duplicate key

```text
canonical entity identity is the seed semantic duplicate key.
```

Two different facts with different `factId` values but targeting the same canonical entity must be rejected as duplicates through existing duplicate detection.

Future production migration:

```text
Provider binding fingerprints replace canonical ID equality for provider-fact semantic duplicate detection after production ID migration.
```

## D-MU014-003 — Replay port

The seed SHOULD introduce a narrow fact-scoped port.

Preferred name:

```text
MaterializationDecisionReplayPort
```

Reference shape:

```java
public interface MaterializationDecisionReplayPort {
    Optional<MaterializationDecision> findDecision(String habitatId, UUID factId);
    void recordDecision(String habitatId, UUID factId, MaterializationDecision decision);
}
```

Acceptable alternate names:

```text
TopologyFactReplayPort
TopologyFactIdempotencyPort
IdempotencyKeyPort, only if documented as fact-scoped and seed-local
```

The port MUST NOT be named or shaped as a generic terminal request-state port.

## D-MU014-004 — Seed storage

H2-backed replay storage is preferred for MU-014 L4.

The execution package SHOULD add a small H2/JdbcTemplate replay adapter, following the same implementation style already used by existing SC-C seed persistence surfaces such as `endpoint_health`.

The replay table is seed-local and fact-scoped. It MUST NOT be shaped as production `sc_c_terminal_responses`.

In-memory replay storage MAY be used only as a fallback if implementation discovers a concrete blocker and the Code Surface Audit / implementation report records the reason.

Recovery-visible replay across repository/service recreation is in scope for MU-014 when H2-backed storage is implemented.

## D-MU014-005 — Materializer integration

`DefaultTopologyMaterializationService` may use the replay port as follows:

```text
1. Check habitatId + factId at materialize(...) entry.
2. If a prior decision exists, return it without mutation.
3. If no prior decision exists, evaluate existing materialization logic.
4. Record the resulting decision, whether accepted or rejected.
```

This MUST preserve MU-012: the materializer must not depend directly on concrete persistence adapters.

## D-MU014-006 — Rejection behavior

All rejection paths, including replay, duplicate and invalid facts, MUST NOT advance `topologyVersion`.

## D-MU014-007 — Stale topology revalidation

MU-014 MUST preserve and may add coverage for `BaseTopologyService.validateTarget(...)`.

A mismatched `topologyVersion` with a still-valid target remains `VALID_AFTER_REVALIDATION`, not rejection.

## D-MU014-008 — Concurrency interpretation

MU-014 tests may validate serialized/idempotent behavior but MUST NOT claim to prove production-grade concurrent structural write safety under H2 or in-memory repositories.

Production write serialization belongs to the SQLite/WAL storage profile and downstream implementation/recovery tests.

---

# 6. Required behavior

## 6.1 Replay lookup before materialization

A replay lookup MUST occur before structural mutation logic is executed.

If a decision exists for `(habitatId, factId)`, the materializer returns the recorded decision.

The replay path MUST NOT:

- call `BaseTopologyService.addDeviceWithResult(...)`;
- call `BaseTopologyService.addEndpointWithResult(...)`;
- call `BaseTopologyService.addRoomWithResult(...)`;
- call `BaseTopologyService.addZoneWithResult(...)`;
- call `BaseTopologyService.addSpatialRelationWithResult(...)`;
- emit duplicate topology/materialization events;
- advance topologyVersion.

## 6.2 Decision recording

Every materialization decision in the MU-014 target surface SHOULD be recorded:

```text
ACCEPT_* decisions
REJECT_DUPLICATE
REJECT_INVALID_FACT
REJECT_AMBIGUOUS_SPATIAL_BINDING
NOOP, if present
```

If some decision kind cannot be serialized/stored safely, the execution package MUST document the limitation and restrict the replay test accordingly.

## 6.3 Semantic duplicate handling

A new fact with a different `factId` but same canonical target must still be evaluated and rejected as semantic duplicate.

It MUST NOT be treated as replay.

It MUST NOT advance `topologyVersion`.

## 6.4 Existing validation preservation

MU-014 must not weaken:

- provider refs as metadata;
- canonical IDs as opaque;
- `topologyVersion` advancement rules;
- state/health non-advancement rules;
- Room/Zone/LOCATED_IN spatial coherence;
- `TopologyMaterializationStatePort` boundary;
- `CoreSnapshotReadPort` read behavior.

---

# 7. Deferred / explicitly out-of-scope ledger

The following aspects are intentionally left outside MU-014 and must remain visible for future work.

| Deferred item | Reason | Future owner / candidate artifact |
|---|---|---|
| Generic command/request idempotency | `ActionRequest` and command dispatch surface are not implemented. | Future command execution MU / production idempotency hardening |
| Terminal response persistence | `sc_c_terminal_responses` exists only in SDD design, not seed code. | Production persistence schema implementation MU |
| In-flight request registry | No current concurrent command execution surface. | Future request execution / command dispatcher MU |
| TemporalAct fire idempotency | TemporalActs are contract-first only; no engine exists. | `SDD-SOV-SC-C-TEMPORAL-ENGINE-001` + TemporalActs seed MU |
| Outbox/ledger implementation | SDD exists; code surface does not. | Future Outbox/Ledger implementation MU |
| SC-B delivery idempotency | SC-B is transport/correlation, not SC-C semantic state. | SC-B binding / TCK artifacts |
| NATS / JetStream dispatch | Physical broker binding is outside SC-C. | `SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001` |
| Provider binding fingerprint migration | Seed uses deterministic canonical IDs; ADR-ID changes production identity. | Production ID migration / persistence implementation MU |
| Full concurrent structural write correctness under H2/InMemory | Current repositories do not offer CAS across read-modify-save. | Production SQLite implementation + recovery/concurrency tests |
| Room/Zone removal lifecycle | Removal APIs are not stable in current seed surface. | Future topology lifecycle/removal MIR |

---

# 8. Minimal acceptance criteria

## Replay/idempotency seed

AC-001: A fact-scoped replay/idempotency port exists or an equivalent boundary is implemented.

AC-002: The replay key is `habitatId + factId` or an equivalent fact-scoped key.

AC-003: Replaying the same factId returns the recorded `MaterializationDecision`.

AC-004: Replaying the same factId does not execute structural mutation a second time.

AC-005: Replaying the same factId does not advance `topologyVersion`.

AC-006: Accepted materialization decisions can be recorded for replay.

AC-007: Rejected materialization decisions can be recorded for replay, where decision shape permits it.

AC-007A: The replay store is H2-backed unless implementation discovers a concrete blocker recorded in the implementation report.

AC-007B: Replay survives repository/service recreation when the H2-backed replay store is implemented.

## Semantic duplicate behavior

AC-008: A different factId targeting an already materialized canonical device/endpoint/room/zone/capability/relation is rejected as duplicate or invalid according to existing materialization semantics.

AC-009: Semantic duplicate rejection does not advance `topologyVersion`.

AC-010: `factId` replay and semantic duplicate detection are not conflated.

## topologyVersion and validation

AC-011: Duplicate/replay paths preserve previous/resulting topologyVersion equality.

AC-012: `VALID_AFTER_REVALIDATION` remains a valid target-resolution result, not a rejection.

AC-013: `TOPOLOGY_VERSION_CONFLICT` remains covered or is added to stale target validation tests.

AC-014: No MU-014 code path bypasses `BaseTopologyService.validateTopology(...)` for structural topology mutation.

## Boundaries

AC-015: `DefaultTopologyMaterializationService` does not depend directly on `H2BaseTopologyRepository`, JDBC, SQL, `DataSource` or concrete persistence adapter types.

AC-016: MU-011 structural persistence vs health/state separation remains intact.

AC-017: MU-012 `TopologyMaterializationStatePort` boundary remains intact.

AC-018: MU-013 Room/Zone/LOCATED_IN coherence checks remain intact.

AC-019: No SC-B, SC-D, NATS, JetStream, Projection, Hub, Session, Identity, Authority or Policy dependency is introduced.

## Concurrency interpretation

AC-020: The implementation report states that H2/InMemory tests validate seed idempotency/replay behavior, not production-grade concurrent write safety.

AC-021: Any concurrent/interleaved test includes a comment documenting the storage-layer nature of production write serialization.

## Regression

AC-022: Existing tests continue to pass.

AC-023: New tests cover same-fact replay, semantic duplicate with different factId, stale topology revalidation and duplicate non-advancement.

AC-024: Build/test command succeeds.

---

# 9. Failure signals

Implementation or execution package MUST stop and report upstream if:

FS-001: `factId` is not present or not accessible on the target `TopologyFact` subtypes.

FS-002: Replay requires broad changes to the sealed `TopologyFact` hierarchy.

FS-003: H2-backed replay storage cannot be added without disproportionate schema or repository changes.

FS-004: Replay storage cannot be added without violating MU-012 port boundaries.

FS-005: Replay or duplicate rejection advances `topologyVersion`.

FS-006: Replay emits duplicate `TopologyChanged` or materialization events.

FS-007: A same canonical entity with a different factId is treated as replay rather than semantic duplicate.

FS-008: `VALID_AFTER_REVALIDATION` is converted into rejection.

FS-009: Target revalidation requires `ActionRequest`, SC-B or adapter dispatch code.

FS-010: Tests require topology removal APIs that do not exist.

FS-011: Implementation requires TemporalActs, outbox/ledger or SC-B runtime.

FS-012: Production SQL/JDBC leaks into domain services or materialization services.

FS-013: SC-B delivery success becomes source of SC-C semantic idempotency.

FS-014: Session, user identity, authority, policy, projection, conversation or surface metadata enters replay/idempotency identity.

FS-015: Existing validated MU behavior regresses.

---

# 10. Required evidence package

The final execution package for this MIR must include:

```text
docs/mir/mir-014/MIR-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001.md
docs/mir/mir-014/code-surface-audit.md
docs/mir/mir-014/decision-confirmation.md
docs/mir/mir-014/context.md
docs/mir/mir-014/codex-prompt.md
docs/mir/mir-014/acceptance-map.md
docs/mir/mir-014/implementation-report.md
```

MIR must not embed implementation prompt or operational context.

---

# 11. Decision confirmation requirements

Before authoring `context.md`, `acceptance-map.md` or `codex-prompt.md`, confirm:

```text
DEC-014-001: MU-014 is Non-Greenfield — bounded scope.
DEC-014-002: factId is the seed replay key.
DEC-014-003: canonical entity identity is the seed semantic duplicate key.
DEC-014-004: MaterializationDecisionReplayPort or equivalent is the target port.
DEC-014-005: H2-backed replay adapter is preferred for L4; in-memory is fallback only with recorded blocker.
DEC-014-006: generic request-state and terminal response persistence are deferred.
DEC-014-007: TemporalAct fire idempotency is deferred.
DEC-014-008: outbox/ledger implementation is deferred.
DEC-014-009: production concurrent write safety is not claimed by H2/InMemory tests.
DEC-014-010: all deferred items in §7 remain tracked.
```

---

# 12. Branch and commit suggestions

Suggested branch:

```text
feat/sc-c-concurrency-idempotency-seed
```

Suggested documentation commit:

```text
docs(sc-c): open concurrency idempotency seed mir
```

Suggested implementation commit, after execution:

```text
feat(sc-c): add materialization decision replay seed
```

---

# 13. Approval status

```text
MIR v0.1.1-draft is approvable after architect review.
Execution package is not authorized until decision-confirmation.md is accepted.
```
