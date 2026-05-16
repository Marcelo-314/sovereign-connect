# Decision Confirmation — MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001

```text
Document:            decision-confirmation.md
Decision Set:        DEC-014
Version:             v0.1.1-draft
MU:                  MU-SOV-SC-C-CONCURRENCY-IDEMPOTENCY-SEED-001
Operational Slot:    MU-014
Depends on:          CSA-MU-014 v0.1.2-merged
Status:              Draft / for architect confirmation
```

---

## Changelog v0.1.1-draft

Patch release after architect note on `DEC-014-006`.

This version:

1. Makes H2-backed replay storage the preferred seed implementation rather than an optional low-risk enhancement.
2. Aligns the replay store with the existing H2/JdbcTemplate seed persistence pattern used by `endpoint_health` and related seed tables.
3. Moves recovery-visible replay after repository/service recreation into MU-014 scope.
4. Removes “recovery-visible replay after restart” from the deferred ledger.
5. Preserves that generic request-state, terminal responses, TemporalAct fire idempotency and outbox/ledger implementation remain out of scope.

---

## 0. Purpose

This document records the decisions required before authoring the MU-014 execution package.

It exists because the Code Surface Audit found that the current repository does not contain the runtime substrate for broad request-state, terminal response, outbox/ledger, TemporalAct or command-dispatch idempotency.

---

## 1. Decisions

### DEC-014-001 — Surface category

```text
Decision:
  MU-014 is Non-Greenfield — bounded scope.
```

Consequence:

```text
A full Code Surface Audit is required and must govern the execution package.
```

---

### DEC-014-002 — Implementation target

```text
Decision:
  The implementation target is materialization fact replay and duplicate semantic-effect prevention.
```

Consequence:

```text
Do not implement generic request-state, command idempotency or terminal response persistence in this MU.
```

---

### DEC-014-003 — Replay key

```text
Decision:
  factId is the seed replay key.
```

Consequence:

```text
The same factId submitted again must return the previously recorded MaterializationDecision without reexecuting structural mutation logic.
```

---

### DEC-014-004 — Semantic duplicate key

```text
Decision:
  canonical entity identity is the seed semantic duplicate key.
```

Consequence:

```text
Different factIds targeting the same canonical entity are not replay; they must be rejected by semantic duplicate detection.
```

---

### DEC-014-005 — Replay port

```text
Decision:
  Introduce a narrow fact-scoped replay port.
```

Preferred name:

```text
MaterializationDecisionReplayPort
```

Acceptable aliases:

```text
TopologyFactReplayPort
TopologyFactIdempotencyPort
IdempotencyKeyPort, only if documented as seed-local and fact-scoped
```

---

### DEC-014-006 — Seed storage

```text
Decision:
  H2-backed replay storage is preferred for MU-014 L4.
```

Consequence:

```text
The execution package SHOULD implement the replay store using the same H2/JdbcTemplate seed persistence pattern already used by existing SC-C persistence adapters, especially endpoint_health.

In-memory replay storage MAY be used only as a fallback if the Code Surface Audit is updated with a concrete blocker.

Recovery-visible replay after repository/service recreation is in scope for MU-014 and MUST be tested if H2-backed replay storage is implemented.
```

Boundary:

```text
This does not implement production sc_c_terminal_responses.
This does not implement generic command/request idempotency.
This implements only fact-scoped materialization decision replay.
```

---

### DEC-014-007 — Concurrency interpretation

```text
Decision:
  MU-014 tests do not prove production-grade concurrent structural write safety.
```

Consequence:

```text
H2/InMemory tests validate replay/idempotency behavior under serialized assumptions.
Production write serialization belongs to the SQLite/WAL profile and later implementation tests.
```

---

### DEC-014-008 — Existing invariants remain binding

```text
Decision:
  MU-014 must preserve MU-011, MU-012 and MU-013 invariants.
```

Required preservation:

```text
MU-011 structural persistence vs health/state separation
MU-012 TopologyMaterializationStatePort boundary
MU-013 Room/Zone/LOCATED_IN spatial coherence validation
```

---

### DEC-014-009 — Future ID migration note

```text
Decision:
  Seed semantic duplicate detection may rely on canonical entity identity.
  Production provider-fact duplicate detection will later use binding fingerprints.
```

Consequence:

```text
Do not encode deterministic seed IDs as the permanent idempotency strategy.
```

---

### DEC-014-010 — Deferred items remain tracked

```text
Decision:
  The deferred ledger below is part of MU-014 governance state.
```

Consequence:

```text
These items must not be silently lost or implemented by accident in MU-014.
```

---

## 2. Deferred / out-of-scope ledger

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

## 3. Approval checklist

```text
[ ] DEC-014-001 accepted
[ ] DEC-014-002 accepted
[ ] DEC-014-003 accepted
[ ] DEC-014-004 accepted
[ ] DEC-014-005 accepted
[ ] DEC-014-006 accepted
[ ] DEC-014-007 accepted
[ ] DEC-014-008 accepted
[ ] DEC-014-009 accepted
[ ] DEC-014-010 accepted
```

Once confirmed, the execution package may proceed.
