# Codex Prompt — MIR-004 — SC-C Persistence and Memory Seed

```text
MIR: MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
MIR Version: v1.0.0-accepted
MU: MU-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
Execution Status: Authorized under MIR v1.0.0-accepted
```

---

## Read First

Read:

```text
docs/mir/mir-004/context.md
```

It contains:

```text
- existing API surface;
- new domain shapes;
- H2/JDBC infrastructure guidance;
- adapter recreation pattern;
- repository compatibility rules;
- mutation ledger rule;
- device state persistence rule;
- boundary constraints.
```

Use:

```text
docs/mir/mir-004/acceptance-map.md
```

for AC numbering when reporting.

---

## Goal

Prove that SC-C canonical state survives repository/service recreation.

This is NOT a production database design task.

It is a seed durability proof:

```text
save → destroy service layer → recreate → retrieve → verify invariants
```

---

## What to build

Build on the existing MU-001/MU-002 codebase.

Do not rewrite what already works.

Implement in dependency order.

---

### 1. Inspect existing code

Inspect the existing MU-001/MU-002 codebase.

Identify:

```text
BaseTopologyRepository
InMemoryBaseTopologyRepository
BaseTopologyService
HabitatBaseTopology
TopologyVersion
TopologyMutationResult
TopologyTargetRef
TargetValidationResult
EndpointHealth
ProviderDeviceRef
ProviderEndpointRef
existing tests
```

Preserve all existing tests.

Do not duplicate existing types.

---

### 2. H2 file-backed infrastructure

Add H2/JDBC seed infrastructure per `context.md §4` and `§5`.

Preferred dependency model:

```text
H2 + Spring JDBC
```

Do not add JPA unless the repository already uses JPA.

Create:

```text
H2BaseTopologyRepository
```

or equivalent durable adapter implementing:

```text
BaseTopologyRepository
```

It must implement the same three methods as `InMemoryBaseTopologyRepository`:

```java
save(...)
findByHabitatId(...)
findCurrentVersion(...)
```

Use the seed schema from `context.md §4.3`.

Serialize `HabitatBaseTopology` as JSON CLOB using Jackson.

Use an `ObjectMapper` with Java Time support, for example:

```java
new ObjectMapper().findAndRegisterModules()
```

Create tables programmatically on adapter construction or through test-local setup.

Do not rely on Spring auto-config for this MIR.

Keep `InMemoryBaseTopologyRepository` untouched.

Existing tests must continue using `InMemoryBaseTopologyRepository`.

---

### 3. BaseTopologySnapshot envelope

Add:

```text
BaseTopologySnapshot
```

per `context.md §3.1`.

When the durable adapter saves, it creates the envelope internally:

```text
- extract topologyVersion from the aggregate;
- store the aggregate as JSON;
- store topologyVersion as indexed/derived envelope value;
- record capturedAt.
```

When retrieving, reconstruct or expose the envelope and verify:

```text
snapshot.topologyVersion == snapshot.topology.topologyVersion
```

`HabitatBaseTopology.topologyVersion` remains the source of truth.

---

### 4. Preserve BaseTopologyRepository compatibility

Do not break:

```text
BaseTopologyRepository
```

Do not silently change public signatures.

Do not remove:

```text
save(...)
findByHabitatId(...)
findCurrentVersion(...)
```

If additional persistence behavior is needed, prefer:

```text
- durable adapter methods;
- extension interface;
- wrapper;
- optional port;
- test-local adapter methods.
```

Existing MU-001 and MU-002 tests must still pass.

If a public API break is unavoidable, stop and report it.

---

### 5. Mutation ledger

Add:

```text
TopologyMutationRecord
```

per `context.md §3.2`.

Add mutation recording through an explicit seed ledger method.

Preferred approach:

```text
1. call addEndpointWithResult(...);
2. convert the returned TopologyMutationResult into a TopologyMutationRecord;
3. persist it through a durable adapter method such as appendMutationRecord(...);
4. expose findMutationRecords(String habitatId) for readback tests.
```

Do not require `save(HabitatBaseTopology)` to infer mutation semantics from the aggregate alone.

Allowed fallback:

```text
If repository-local structure makes explicit append awkward, the durable adapter MAY derive a minimal mutation record by comparing previous and new topologyVersion.
```

If fallback is used, report it in `implementation-report.md` as a seed limitation.

Do not implement full event sourcing.

Do not implement transactional outbox.

---

### 6. Durable device state

Persist device state through an explicit persistence boundary.

Preferred approach:

```text
Introduce a small optional port/interface for device state memory and inject it without breaking existing BaseTopologyService constructors.
```

Acceptable seed alternative:

```text
Use durable-adapter methods directly in MIR-004 tests to prove recovery,
while leaving MU-002 service-local behavior unchanged.
```

Do not make `BaseTopologyService` depend on `H2BaseTopologyRepository` directly.

Do not break existing MU-001/MU-002 tests.

Required behavior:

```text
save device state
retrieve device state after adapter/service recreation
prove topologyVersion did not advance
```

---

### 7. Durable endpoint health

Endpoint health already persists inside `HabitatBaseTopology` if `updateEndpointHealth(...)` modifies the aggregate and the aggregate is saved.

For MU-004, also prove endpoint health recovery.

Allowed approaches:

```text
- recover endpoint health from persisted aggregate;
- additionally populate endpoint_health table for direct health lookup.
```

If implementing a direct `endpoint_health` table, keep it seed-level.

Do not implement full health policy.

Required behavior:

```text
save endpoint health
recover endpoint health after adapter/service recreation
prove topologyVersion did not advance
```

---

### 8. Recovery test

Implement the adapter recreation pattern from `context.md §5.3`.

The test must:

```text
1. Create DataSource → durable adapter → service.
2. Create topology.
3. Add endpoint through addEndpointWithResult(...).
4. Persist topology snapshot.
5. Append mutation record.
6. Persist device state.
7. Persist endpoint health.
8. Discard service, adapter and DataSource.
9. Create NEW DataSource → NEW adapter → NEW service using the same file path.
10. Retrieve topology.
11. Retrieve current topologyVersion.
12. Retrieve mutation records.
13. Retrieve device state.
14. Retrieve endpoint health.
15. Verify canonical IDs unchanged.
16. Verify provider refs remain metadata.
17. Verify snapshot.topologyVersion == snapshot.topology.topologyVersion.
18. Verify recovered topologyVersion works with validateTarget(...).
```

Use `@TempDir` or equivalent for the H2/file path so tests clean up automatically.

Do not reuse adapter or service instances across the recreation boundary.

---

### 9. Stale target validation with recovered version

After recovery, call the existing `validateTarget(...)` method with:

```text
recovered topologyVersion
existing target
```

Prove the recovered version is usable for stale target detection without:

```text
SC-B
SC-D
command dispatch
```

---

### 10. Tests

Implement tests covering AC-001 through AC-025 per:

```text
docs/mir/mir-004/acceptance-map.md
```

Do not invent new AC numbering.

Organize as a new test class, for example:

```text
PersistenceMemorySeedTest
```

or equivalent.

Existing test classes keep using `InMemoryBaseTopologyRepository`.

All 20 existing tests must still pass alongside the new persistence tests.

---

## What NOT to build

Do not implement anything under `Negative Scope` in `context.md §12`.

Especially do not implement:

```text
JPA entities unless already used
production schema
migration framework
event sourcing framework
transactional outbox
SC-B replay
SC-D rediscovery
full snapshot query API
TemporalActs persistence
action terminal result persistence
idempotency persistence
Projection/Effective View cache
Session/Identity/Authority/Policy persistence
```

If any negative-scope item becomes necessary, STOP and report it.

---

## Build

Run:

```bash
mvn test
```

If the repository uses a different build command, use the repository's actual command and report the difference.

---

## Output

Create:

```text
docs/mir/mir-004/implementation-report.md
```

The report must include:

```text
1.  Summary (2-3 sentences)
2.  Files changed
3.  Domain types added or modified
4.  Repository/persistence adapters added or modified
5.  Services modified (if any)
6.  Seed storage strategy used
7.  Tests added (one-line each)
8.  Acceptance results: AC-001 through AC-025 — pass/fail with evidence
9.  Invariants preserved:
      SC-C owns persistence — preserved/broken
      Base Topology persists and recovers — preserved/broken
      topologyVersion persists and recovers — preserved/broken
      snapshot.topologyVersion == aggregate topologyVersion — preserved/broken
      canonical deviceId survives recovery — preserved/broken
      canonical endpointId survives recovery — preserved/broken
      provider refs remain metadata — preserved/broken
      state persistence does not advance version — preserved/broken
      health persistence does not advance version — preserved/broken
      recovery independent of SC-B/SC-D — preserved/broken
      recovery independent of Projection/Session/Authority/Policy — preserved/broken
      BaseTopologyRepository compatibility — preserved/broken
      seed storage not declared production doctrine — preserved/broken
10. Deviations from scope
11. Failure signals encountered
12. Assumptions made
13. Corpus issues discovered
14. Storage technology statement:
      Seed storage: <technology>
      Production storage: not decided
      ADR required: ADR-SOV-SC-C-STORAGE-TECH-001
15. Recommended next MU
```

---

## Stop conditions

Stop and report if:

```text
topology cannot survive repository/service recreation
recovery requires SC-B replay or SC-D rediscovery
recovery requires Hub/Projection/Session/Identity/Authority/Policy
recovered topology loses canonical deviceId or endpointId
provider refs become canonical identity after recovery
recovered topologyVersion mismatches recovered topology
snapshot.topologyVersion differs from snapshot.topology.topologyVersion
state or health persistence forces topologyVersion advancement
existing BaseTopologyRepository behavior must be broken
BaseTopologyService must depend on H2BaseTopologyRepository directly
pure in-memory storage is the only viable approach
final storage ADR is required before seed can work
SC-B transport becomes necessary
real SC-D adapter becomes necessary
```
