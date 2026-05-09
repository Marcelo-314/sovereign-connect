# MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 — Acceptance Map

```text
MIR: MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001
MIR Version: v0.1.0-draft
MU: MU-SOV-SC-C-TOPOLOGY-VERSION-SEED-001
Validation Target: L4
Date: 2026-05-09
```

---

## 0. Purpose

Official AC-001 through AC-025 mapping for MIR-002.
Use this file when reporting acceptance results.
Do not invent new AC numbering.

---

## 1. Acceptance Criteria Map

| AC | Criterion | Required Evidence |
|---|---|---|
| AC-001 | `TopologyVersion` exists as typed value or equivalent | Domain type present |
| AC-002 | `TopologyVersion` is scoped at least to Habitat | Scope type = HABITAT, scope id = habitatId |
| AC-003 | Initial snapshot has initial `topologyVersion` | Initial snapshot test |
| AC-004 | Accepted structural mutation advances `topologyVersion` | Structural mutation test with version check |
| AC-005 | Rejected mutation does not advance `topologyVersion` | Rejected mutation test with version unchanged |
| AC-006 | Operational state update does not advance `topologyVersion` | `updateDeviceState` test with version unchanged |
| AC-007 | Health update does not advance `topologyVersion` | `updateEndpointHealth` test with version unchanged |
| AC-008 | Projection-like change does not advance `topologyVersion` | Test proving no Projection advancement path |
| AC-009 | Session-like change does not advance `topologyVersion` | Test proving no Session advancement path |
| AC-010 | Authority/policy-like change does not advance `topologyVersion` | Test proving no Authority/Policy advancement path |
| AC-011 | Provider-native version is not canonical `topologyVersion` | Provider metadata exclusion test |
| AC-012 | Provider-native revision stored as metadata without advancing version | Provider revision metadata test |
| AC-013 | Snapshot includes `topologyVersion` | Snapshot structure assertion |
| AC-014 | Snapshot nodes and `topologyVersion` are internally consistent | Post-mutation snapshot consistency test |
| AC-015 | `TopologyChanged` or mutation result contains `fromVersion`/`toVersion` | Mutation result field assertions |
| AC-016 | `toVersion` differs from `fromVersion` after structural mutation | Version inequality assertion |
| AC-017 | Stale `topologyVersion` triggers revalidation | `validateTarget` with stale version test |
| AC-018 | Stale version does not auto-fail if target remains valid | `VALID_AFTER_REVALIDATION` case test |
| AC-019 | Stale version fails before dispatch if target gone/changed | `TARGET_NOT_FOUND` or `CAPABILITY_MISMATCH` case test |
| AC-020 | `topologyVersion` excluded from idempotency identity | `IdempotencyIdentity` equality test with different versions |
| AC-021 | SC-B not required for `topologyVersion` semantics | No SC-B dependency in codebase |
| AC-022 | SC-D not allowed to assign/advance `topologyVersion` | No SC-D component in codebase |
| AC-023 | No user/session/identity/authority/policy required | Constructor/service inspection |
| AC-024 | Tests cover structural and non-structural behavior | Test suite coverage |
| AC-025 | Build/test command succeeds | `mvn test` green |

---

## 2. Satisfaction Notes

AC-006, AC-007: Satisfied via minimal seed methods (`updateEndpointHealth`,
`updateDeviceState`) added to `BaseTopologyService`. These are seed-only.

AC-008, AC-009, AC-010: Satisfied via explicit test assertions that confirm
no code path accepts Projection/Session/Authority/Policy inputs as version
advancement triggers. No actual modules needed.

AC-021, AC-022: Satisfied negatively — no SC-B or SC-D components exist in
the seed. Valid if the implementation remains entirely within SC-C scope.

AC-017, AC-018, AC-019: Satisfied via `BaseTopologyService.validateTarget`
method with `TopologyTargetRef` and three test cases.

AC-020: Satisfied via `IdempotencyIdentity` record that excludes
`topologyVersion` from its fields.

---

## 3. Validation Level

```text
Target: L4 — implementation attempted and local tests pass.
L5 TCK validation not required for this MU.
```
