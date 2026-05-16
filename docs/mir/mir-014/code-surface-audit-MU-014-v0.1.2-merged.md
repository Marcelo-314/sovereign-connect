# Code Surface Audit — MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001

```text
Audit ID:            CSA-MU-014
Version:             v0.1.2-merged
MU:                  MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001
Operational Slot:    MU-014
Repository state:    sovereign-connect-013-hardening / post-MU-013 hardening
Surface category:    Non-Greenfield — bounded scope / full audit required
Date:                2026-05-15
Tests baseline:      54 tests / 0 failures / 0 errors / 0 skipped, per included Surefire reports
Status:              Merged audit / DEC-014-006 storage preference patched
```

---

## Changelog v0.1.2-merged

Patch release after architect note on `DEC-014-006`.

This version:

1. Changes replay storage guidance from “in-memory sufficient unless H2 is low-risk” to “H2-backed replay storage preferred.”
2. Notes that the existing H2/JdbcTemplate seed persistence pattern makes recovery-visible replay low-risk and valuable for MU-014.
3. Moves repository/service recreation replay from deferred scope into expected MU-014 coverage.
4. Keeps generic request-state, terminal responses, TemporalAct fire idempotency and outbox/ledger implementation out of scope.

---

## 0. Disposition

This merged audit supersedes the initial broad request-state audit and adopts the architect audit as the primary baseline.

The original MIR scope was too broad for the current repository surface. The codebase does not yet contain the runtime substrate required for generic request idempotency, terminal response reconstruction, command dispatch idempotency, TemporalAct fire idempotency or outbox/ledger dispatch.

Correct MU-014 scope:

```text
Introduce a bounded SC-C materialization fact replay / idempotency seed.

Validate that repeated facts or duplicate materialization attempts do not produce duplicate semantic effects, do not advance topologyVersion twice and do not bypass stale target or topology coherence invariants.
```

This MU is **not** a full production concurrency implementation.

---

## 1. Purpose

This audit maps the existing concurrency and idempotency surface in SC-C before MU-014 implementation begins.

It identifies:

- what exists and may be safely extended;
- what is absent and must not be assumed;
- what invariants from MU-011, MU-012 and MU-013 must remain intact;
- what tests should be added;
- what decisions must be confirmed before authoring the execution package.

---

## 2. Critical finding — current concurrency model

SC-C mutation methods in `BaseTopologyService` follow a read-modify-save cycle:

```java
HabitatBaseTopology current = repository.findByHabitatId(habitatId);
// compute mutated topology
repository.save(mutated);
```

There is no application-level synchronization across the full cycle.

Current repository implications:

```text
InMemoryBaseTopologyRepository:
  ConcurrentHashMap operations are thread-safe individually,
  but read -> modify -> put is not atomic.
  Lost update is possible under concurrent structural writes.

H2BaseTopologyRepository:
  MERGE INTO topology_snapshots is row-level atomic,
  but the read -> modify -> MERGE cycle is not a single application-level CAS.
  Lost update remains possible under concurrent callers unless serialized externally.

Production SQLite/WAL profile:
  Single-writer semantics serialize writes at the storage layer.
  This is the intended production concurrency primitive for structural writes.
```

Implication:

```text
MU-014 may validate idempotency, replay and duplicate semantic-effect prevention.
MU-014 must not claim to prove full concurrent structural write safety using H2 or in-memory repositories.
```

Any concurrent or interleaved test in MU-014 must explicitly state that it validates seed idempotency behavior under serialized mutation assumptions, not production-grade concurrent write correctness.

---

## 3. Existing surfaces confirmed present

### 3.1 Topology materialization service

`DefaultTopologyMaterializationService` exists and is the correct implementation surface for this seed.

It already performs duplicate detection for materialization facts before structural service mutation.

### 3.2 TopologyMaterializationStatePort boundary

MU-012 introduced and validated `TopologyMaterializationStatePort`.

`DefaultTopologyMaterializationService` depends on this port rather than on `H2BaseTopologyRepository` directly.

MU-014 must preserve that boundary.

### 3.3 Duplicate structural entity handling

Existing and/or MU-013-validated duplicate handling includes:

```text
Device duplicate canonical ID    -> REJECT_DUPLICATE
Endpoint duplicate canonical ID  -> REJECT_DUPLICATE
Capability duplicate under owner -> REJECT_DUPLICATE
Room duplicate canonical ID      -> REJECT_DUPLICATE
Zone duplicate canonical ID      -> REJECT_DUPLICATE
Spatial relation duplicate ID    -> rejected by BaseTopologyService validation path
```

### 3.4 Stale topology target detection

`BaseTopologyService.validateTarget(...)` exists and supports stale-target revalidation semantics.

Relevant result vocabulary includes:

```text
VALID
VALID_AFTER_REVALIDATION
TARGET_NOT_FOUND
CAPABILITY_MISMATCH
TOPOLOGY_VERSION_CONFLICT
```

MU-014 must reuse or preserve this surface. It must not reimplement target validation in a parallel path.

### 3.5 topologyVersion behavior

Existing behavior to preserve:

```text
Accepted structural mutations advance topologyVersion.
Rejected / duplicate / invalid materialization facts do not advance topologyVersion.
State and health writes do not advance topologyVersion.
```

---

## 4. Missing surfaces that must not be assumed

The current repository does **not** implement:

```text
ActionRequest runtime surface
ActionTargetFailure concrete terminal response flow
ScResponseEnvelope concrete response carrier
terminal response records
request-state table
idempotency store
in-flight request registry
outbox / ledger tables or dispatcher
TemporalAct aggregate
TemporalAct engine
command dispatch surface
transactional request boundary
production SQLite schema/migrations
```

Therefore, MU-014 must not require those surfaces to pass L4.

---

## 5. Idempotency model for this seed

MU-014 must distinguish two different concepts.

### 5.1 Replay key

```text
factId is the seed replay key.
```

Meaning:

```text
The exact same fact/event submitted again should return the previously recorded MaterializationDecision without re-executing materialization logic.
```

### 5.2 Semantic duplicate key

```text
canonical entity identity is the seed semantic duplicate key.
```

Meaning:

```text
Two different facts, with different factId values, that attempt to materialize the same canonical device / endpoint / room / zone / capability / relation must be rejected as duplicate and must not advance topologyVersion twice.
```

Future note:

```text
After ADR-SOV-SC-C-ID-STRATEGY-001 migrates production identities to SC-C-generated UUIDv7-compatible IDs, provider binding fingerprints replace canonical ID equality as the principal provider-fact semantic duplicate mechanism.
```

This future migration point is out of scope for MU-014 implementation.

---

## 6. Recommended implementation surface

### 6.1 New fact-scoped replay port

Preferred naming:

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

Naming rule:

```text
Do not name this as generic RequestStatePort or TerminalResponsePort.
```

Rationale:

```text
The current implementation surface is topology materialization facts, not general commands/requests.
```

### 6.2 Seed adapter

H2-backed replay storage is preferred for MU-014 L4.

The current repository already uses small H2/JdbcTemplate persistence surfaces for seed state such as `endpoint_health`; a fact-decision replay table follows the same implementation pattern and provides recovery-visible replay after repository/service recreation.

In-memory replay storage is acceptable only as a fallback if implementation discovers a concrete blocker and the audit is updated before the Codex prompt is authorized.

### 6.3 Materialization service integration

At the beginning of `DefaultTopologyMaterializationService.materialize(...)` or equivalent:

```java
Optional<MaterializationDecision> cached = replayPort.findDecision(habitatId, fact.factId());
if (cached.isPresent()) {
    return cached.get();
}
```

After any terminal materialization decision is produced, whether accepted or rejected:

```java
replayPort.recordDecision(habitatId, fact.factId(), decision);
```

Rules:

- replay must not advance topologyVersion;
- replay must not call `BaseTopologyService` mutation methods again;
- replay must not emit duplicate materialization events;
- replay must not bypass existing duplicate detection for different facts with the same semantic target;
- replay must not introduce direct repository coupling in `DefaultTopologyMaterializationService`.

---

## 7. Invariants MU-014 must preserve

### INV-014-001 — Rejections do not advance topologyVersion

Any rejection path, including duplicate facts and replayed decisions, must preserve previous and resulting topologyVersion equality.

### INV-014-002 — `VALID_AFTER_REVALIDATION` is not a rejection

A stale topologyVersion with a still-valid target must remain valid after revalidation.

### INV-014-003 — MU-011 persistence boundary

Structural topology persistence must remain separated from state/health and future terminal records.

### INV-014-004 — MU-012 materialization port boundary

The materializer must not depend directly on `H2BaseTopologyRepository`, JDBC, SQL, `DataSource` or concrete persistence adapter types.

### INV-014-005 — MU-013 spatial topology coherence

No MU-014 path may bypass `BaseTopologyService.validateTopology(...)` or equivalent topology coherence checks.

### INV-014-006 — No generic request-state claims

This MU may implement fact replay and duplicate semantic-effect prevention. It must not claim production command request idempotency.

### INV-014-007 — No bus/broker semantic authority

SC-B, NATS, JetStream or any delivery layer must not become source of semantic idempotency or terminal decision state.

---

## 8. Required tests / coverage targets

The execution package should include or verify coverage for:

```text
T-014-001 duplicateRoomFactRejectedWithoutVersionAdvance
T-014-002 duplicateZoneFactRejectedWithoutVersionAdvance
T-014-003 duplicateSpatialRelationRejectedWithoutVersionAdvance
T-014-004 staleTopologyVersionRevalidatesValidTarget
T-014-005 staleTopologyVersionFailsOnRemovedTarget, only if removal exists in code surface
T-014-006 topologyVersionScopeConflictRejected
T-014-007 sameFactIdReplaysStoredMaterializationDecision
T-014-008 secondMaterializationOfSameProviderBindingRejectedWithoutVersionAdvance
T-014-009 serializedMutationDocumentationTest
T-014-010 sameCanonicalEntityDifferentFactIdRejectedAsDuplicateWithoutVersionAdvance
```

Adjustments:

- If removal is not implemented, T-014-005 must be deferred explicitly rather than simulated through non-existent API.
- T-014-009 must not claim to prove production concurrent write safety.
- Existing tests in `RoomZoneTopologySeedTest`, `TopologyMaterializationSeedTest` and `TopologyVersionHardeningTest` may be extended rather than duplicated.

---

## 9. Files likely to modify

Expected new files:

```text
src/main/java/.../port/MaterializationDecisionReplayPort.java
src/main/java/.../adapter/persistence/InMemoryMaterializationDecisionReplayRepository.java
src/test/java/.../ConcurrencyIdempotencySeedTest.java
```

Expected modified files:

```text
src/main/java/.../materialization/DefaultTopologyMaterializationService.java
src/test/java/.../TopologyMaterializationSeedTest.java
src/test/java/.../RoomZoneTopologySeedTest.java
src/test/java/.../TopologyVersionHardeningTest.java
```

Possible modified test fixture files:

```text
any test constructors/builders that instantiate DefaultTopologyMaterializationService
```

Do not modify unless explicitly required:

```text
H2BaseTopologyRepository schema
SC-B envelope code
SC-D protocol code
TemporalActs code
outbox/ledger code
Projection/Hub/Authority/Policy code
```

---

## 10. Explicitly deferred / out-of-scope ledger

The following are intentionally left out of MU-014 and must remain visible for future planning.

| Deferred item | Reason | Future owner / candidate artifact |
|---|---|---|
| Generic command/request idempotency | `ActionRequest` / command dispatch surface is not implemented. | Future command execution MU / Concurrency-Idempotency production hardening |
| Terminal response persistence | `sc_c_terminal_responses` is SDD-level design, not seed code. | Production persistence schema implementation MU |
| In-flight request registry | No current concurrent command execution surface. SQLite single-writer is the production structural-write primitive. | Future request execution / command dispatcher MU |
| TemporalAct fire idempotency | TemporalActs are contract-first only; no engine exists. | `SDD-SOV-SC-C-TEMPORAL-ENGINE-001` and TemporalActs seed MU |
| Outbox/ledger implementation | SDD is specified but no code surface exists. | Future Outbox/Ledger implementation MU |
| SC-B delivery idempotency | SC-B remains transport/correlation, not SC-C semantic state. | SC-B binding / TCK artifacts |
| NATS / JetStream dispatch | Physical broker binding is outside SC-C. | `SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001` |
| Provider binding fingerprint migration | Seed uses deterministic canonical IDs; ADR-ID production strategy changes this. | Production ID migration / persistence implementation MU |
| Full concurrent structural write correctness under H2/InMemory | Current repositories do not offer application-level CAS across read-modify-save. | Production SQLite implementation and recovery/concurrency tests |
| Room/Zone removal lifecycle | Removal APIs are not present or not stable in current seed. | Future topology lifecycle/removal MIR |

This ledger must be copied into `decision-confirmation.md` or the MIR before approval.

---

## 11. Stop conditions

Stop and report upstream if:

```text
- factId is not available on all TopologyFact subtypes targeted by MU-014;
- replay requires changing the sealed TopologyFact hierarchy beyond small accessor exposure;
- H2-backed replay storage cannot be added without disproportionate schema or repository changes;
- replay storage cannot be added without violating MU-012 port boundaries;
- duplicate replay causes topologyVersion advancement;
- duplicate replay emits duplicate TopologyChanged/materialization events;
- target revalidation requires ActionRequest, SC-B or adapter dispatch code;
- any required test depends on non-existent removal APIs;
- implementation requires TemporalActs, outbox/ledger or SC-B runtime;
- production SQL/JDBC leaks into domain services or materialization services.
```

---

## 12. Required MIR patch

Before MIR approval, patch `MIR-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001` to:

```text
- classify MU-014 as Non-Greenfield — bounded scope;
- replace generic request-state / terminal response language with fact replay / materialization idempotency language;
- remove mandatory in-flight request behavior from acceptance criteria;
- defer terminal response persistence and ActionRequest command idempotency;
- add the out-of-scope ledger from §10;
- require this audit as normative Code Surface Audit input;
- require decision-confirmation.md before context/prompt authoring.
```

---

## 13. Disposition

```text
Recommended decision:
  Proceed after MIR patch.

Surface category:
  Non-Greenfield — bounded scope.

Expected acceptance:
  Validated L4 if tests pass.

Primary implementation risk:
  Wiring a new replay port into DefaultTopologyMaterializationService without breaking test constructors or MU-012 port boundaries.

Key constraint:
  factId is the seed replay key;
  canonical ID is the seed semantic duplicate key;
  provider binding fingerprints replace canonical ID equality after the production ID migration.
```
