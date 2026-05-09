# Codex Prompt — MIR-002 — SC-C topologyVersion Seed

```text
MIR: MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001
MU: MU-SOV-SC-C-TOPOLOGY-VERSION-SEED-001
Execution Status: Not authorized until MIR v1.0.0-accepted
```

---

## Read First

Read `docs/mir/mir-002/context.md`. It contains the existing MU-001 API,
new reference domain shapes, architectural placement rules and boundary constraints.

Use `docs/mir/mir-002/acceptance-map.md` for AC numbering when reporting results.

---

## Goal

Harden the topologyVersion seed from MU-001 so it can serve as a reliable
foundation for durable persistence, snapshot query and command targeting in later MUs.

---

## What to build

Build on the existing MU-001 codebase. Do not rewrite what already works.

### 1. TopologyMutationResult

Add `TopologyMutationResult` per context §3.1.

Preserve the existing MU-001 `BaseTopologyService.addEndpoint` behavior unless
the repository already makes that impossible.

Preferred:
  add a new method such as `addEndpointWithResult`, `mutateTopology`, or
  equivalent, returning `TopologyMutationResult`.

Existing MU-001 tests must continue to pass.
If any existing public method signature changes, report it explicitly.

Existing `TopologyChanged` already has `fromVersion`/`toVersion` —
ensure both are populated and tested.

### 2. Rejected mutation non-advancement

Add a test that attempts an invalid structural mutation (e.g., add endpoint
to missing device, or add duplicate endpointId) and proves topologyVersion
does not advance. Use existing service validation — no new rejection logic needed
if the service already throws on invalid input.

If the existing service does not reject any invalid structural mutation yet,
add the minimal validation required to prove AC-005.
Do not generalize into a full topology validation framework.

### 3. Non-structural non-advancement

Add `updateEndpointHealth` and `updateDeviceState` seed methods to
`BaseTopologyService` per context §3.4. These methods update in-memory
state without advancing topologyVersion. Add tests proving non-advancement
for each. Do NOT build full state/health materialization.

### 4. Projection / Session / Authority / Policy exclusion

Add explicit tests proving these categories do not advance topologyVersion.
Use simple no-op test helpers or assertions that confirm the service has no
code path that accepts Projection/Session/Authority/Policy inputs as
topologyVersion advancement triggers. Do not introduce actual Projection,
Session, Authority or Policy modules.

### 5. Provider-native version exclusion

Add a test proving that provider-native revision metadata (e.g., a field in
`ProviderEndpointRef.nativeCoordinates`) does not become canonical topologyVersion
and does not advance it.

### 6. Snapshot consistency

Add tests proving:
- retrieved snapshot includes topologyVersion
- after mutation, retrieved snapshot has new version AND new topology
- no snapshot exposes new topology with old version or vice versa

These may extend existing MU-001 tests. In-memory atomicity is sufficient.

### 7. Stale target validator

Add `TopologyTargetRef`, `TargetValidationResult` and `validateTarget` method
per context §3.2. Add tests for the three required cases:
1. same version + target exists → VALID
2. stale version + target still valid → VALID_AFTER_REVALIDATION
3. stale version + target gone/changed → TARGET_NOT_FOUND or CAPABILITY_MISMATCH

### 8. Idempotency identity exclusion

Add `IdempotencyIdentity` per context §3.3. Add a test proving:
same operationKind + same target + same params + different topologyVersion
→ equal IdempotencyIdentity.

### 9. Tests

Implement tests covering AC-001 through AC-025 as defined in
docs/mir/mir-002/acceptance-map.md.

Do not invent new AC numbering.
Map every test/result to the acceptance-map.md entries.

---

## What NOT to build

Everything under "Negative Scope" in context §6.
If any negative-scope item becomes necessary, STOP and report it.

---

## Build

```bash
mvn test
```

---

## Output

Create `docs/mir/mir-002/implementation-report.md` with:

```text
1.  Summary (2-3 sentences)
2.  Files changed
3.  Domain types added or modified
4.  Services/validators added or modified
5.  Persistence strategy used
6.  Tests added (one-line each)
7.  Acceptance results: AC-001 through AC-025 — pass/fail with evidence
8.  Invariants preserved:
      SC-C owns topologyVersion — preserved/broken
      topologyVersion belongs to Base Topology — preserved/broken
      topologyVersion is Habitat-scoped — preserved/broken
      structural mutation advances version — preserved/broken
      rejected mutation does not advance — preserved/broken
      state/health does not advance — preserved/broken
      Projection does not advance — preserved/broken
      provider-native is not topologyVersion — preserved/broken
      snapshot/version consistent — preserved/broken
      stale target revalidation works — preserved/broken
      topologyVersion excluded from idempotency — preserved/broken
9.  Deviations from scope
10. Failure signals encountered
11. Assumptions made
12. Corpus issues discovered
13. Recommended upstream patches
14. Recommended next MU
```

---

## Stop conditions

Stop and report if:
- topologyVersion cannot be defined without Projection
- topologyVersion requires session/user/authority/policy
- provider-native version must become canonical topologyVersion
- SC-D must assign/advance topologyVersion
- SC-B must decide topologyVersion semantics
- state/health forces topologyVersion advancement
- stale detection can't distinguish version mismatch from target invalidity
- topologyVersion must participate in idempotency identity
- durable persistence becomes necessary
- SC-B transport becomes necessary
- real SC-D adapter becomes necessary
