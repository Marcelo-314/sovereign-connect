# MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001

## SC-C Kernel Hardening Seed Materialization

```text
Document ID: MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001
Title: SC-C Kernel Hardening Seed Materialization
Version: v1.0.0-accepted
Status: Accepted
Date: 2026-05-10
Corpus: Sovereign Connect
Type: MIR — Materialization Increment Record
Plane: SC-C
Materialization Unit: MU-SOV-SC-C-KERNEL-HARDENING-SEED-001
Operational Slot: MU-010

Supersedes:
  - MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 v0.2.0-candidate
  - MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 v0.1.1-draft
  - MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 v0.1.0-draft

Depends on:
  - NT-SOV-SC-MATERIALIZATION-UNITS-001 v0.1.0-draft
  - PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.3-draft
  - INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.4-draft
  - SYNC-SOV-SC-MU-BACKLOG-001 v0.1.5-draft
  - RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
  - RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft
  - PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft
  - PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.6-draft
  - PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft
  - PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft
  - PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 v0.1.0-draft
  - MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001 v1.0.0-accepted

Execution package:
  - docs/mir/mir-010/MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001.md
  - docs/mir/mir-010/context.md
  - docs/mir/mir-010/codex-prompt.md
  - docs/mir/mir-010/acceptance-map.md
  - docs/mir/mir-010/implementation-report.md

Execution package status:
  - context.md: final / accepted
  - codex-prompt.md: final / accepted
  - acceptance-map.md: final / accepted
  - implementation-report.md: pending post-execution template

Target repository:
  - sovereign-connect

Suggested target branch:
  - test/sc-c-kernel-hardening-010

Suggested commit:
  - test(sc-c): harden core kernel invariants
```

---

## 0. Status Notice

This MIR is **Accepted**.

Accepted status means:

```text
- the Materialization Unit is scoped;
- dependencies are listed and pinned;
- hardening scope is bounded;
- coverage classification rules are explicit;
- durable cross-MU testing substrate is defined;
- negative scope is explicit;
- acceptance criteria are testable;
- failure signals are defined;
- context.md has been finalized;
- codex-prompt.md has been finalized;
- acceptance-map.md has been finalized;
- implementation-report.md remains a post-execution artifact;
- implementation is authorized.
```

This MIR is a **hardening MIR**, not a feature MIR.

It MUST NOT add new SC-C product behavior unless an invariant test exposes a genuine missing kernel guard.

Implementation is authorized under this MIR.

---

## 1. Purpose

This MIR governs the descent of:

```text
MU-SOV-SC-C-KERNEL-HARDENING-SEED-001
```

The purpose of this Materialization Unit is to harden the SC-C kernel after the following validated MUs:

```text
MU-001 — SC-C Base Topology Seed — Validated L4
MU-002 — SC-C topologyVersion Seed — Validated L4
MU-004 — SC-C Persistence/Memory Seed — Validated L4
MU-006 — SC-C Core Snapshot Query Seed — Validated L4
```

This MIR validates that those independently materialized increments compose into a stable SC-C kernel without regression or boundary drift.

It focuses on:

```text
- cross-MU invariant composition;
- durable recovery path validation;
- read/write separation;
- topologyVersion stability;
- canonical ID lookup integrity;
- provider-ref metadata discipline;
- SC-C / SC-B / SC-D boundary preservation;
- Projection / Effective View exclusion;
- Session / Identity / Authority / Policy exclusion.
```

This MIR does **not** implement Topology Materialization.

---

## 2. Materialization Unit

```text
MU ID: MU-SOV-SC-C-KERNEL-HARDENING-SEED-001
Operational Slot: MU-010
Title: SC-C Kernel Hardening Seed
Type: Kernel / Hardening MU
Plane: SC-C
Status: Accepted

Primary invariant validated:
  - SC-C kernel invariants remain stable across MU-001, MU-002, MU-004 and MU-006.
  - Base Topology morphology remains stable.
  - topologyVersion advancement/non-advancement rules remain stable.
  - persistence/recovery remains coherent.
  - Core Snapshot Query reads persisted state, not mutation-service memory.
  - canonical IDs remain distinct from provider refs.
  - SC-C kernel does not absorb SC-B, SC-D, Projection, Hub, Session, Identity, Authority or Policy.
```

### 2.1 Slot decision

`MU-010` is used intentionally.

Rationale:

```text
MU-008 is already assigned to MU-SOV-SC-B-ENVELOPE-CONTRACT-001.
The old v0.1.0 “MU-010” reference for Command-to-Simulated-Adapter is historical only.
Kernel Hardening is therefore assigned to the active v0.2.x slot MU-010.
```

---

## 3. Normative Thesis

```text
A materialized SC-C kernel is not validated only by feature tests.
It must also be protected by composed regression and boundary tests.
```

More concretely:

```text
SC-C must continue to own Base Topology, topologyVersion, persistence,
recovery and canonical query semantics without absorbing SC-B transport,
SC-D adapter semantics, Projection, Session, Identity, Authority or Policy.
```

This MIR exists because the next major SC-C step — Topology Materialization — will touch:

```text
facts / proposals → canonical topology mutation → TopologyChanged → topologyVersion
```

That path is high risk.

Kernel hardening should run before that.

---

## 4. Design Decisions

### MIR-010-D-001 — Hardening coverage classification

Kernel Hardening ACs MUST be classified as one of:

```text
New cross-MU validation:
  ACs that prove composition across independently validated MUs.

Regression consolidation:
  ACs already covered by accepted MIR tests, but rechecked to prevent drift.

Boundary guard:
  ACs that assert absence of forbidden dependencies or responsibility leakage.
```

The implementation prompt MUST NOT ask the agent to duplicate existing tests blindly.

It MUST ask the agent to:

```text
1. inspect existing tests;
2. classify inherited coverage;
3. add only missing composed hardening tests;
4. add boundary guards where needed;
5. report which ACs were inherited vs newly validated.
```

### MIR-010-D-002 — Durable cross-MU test substrate

Kernel Hardening MUST use the durable H2 path for cross-MU validation.

Rationale:

```text
MU-001 and MU-002 validated several semantics over InMemory.
MU-004 and MU-006 validated persistence and query over H2 durable storage.
Kernel Hardening must validate the composed kernel, therefore it must exercise
the durable path where topologyVersion, persistence, recovery and query meet.
```

Rule:

```text
New cross-MU tests SHOULD use H2BaseTopologyRepository or equivalent durable adapter.
InMemory MAY remain as inherited regression evidence.
```

### MIR-010-D-003 — No redundant implementation work

If an AC is already covered by:

```text
BaseTopologyServiceTest
TopologyVersionHardeningTest
PersistenceMemorySeedTest
CoreSnapshotQuerySeedTest
```

then Kernel Hardening SHOULD either:

```text
- cite it as inherited coverage in implementation-report.md; or
- add one composed regression test that crosses MU boundaries.
```

It SHOULD NOT duplicate equivalent unit tests without adding new cross-MU value.

### MIR-010-D-004 — Test-first hardening

This MIR prefers tests before production code.

Allowed production changes:

```text
- only if a hardening test exposes a genuine missing invariant;
- only if the change preserves existing public contracts;
- only if the implementation report explains the gap.
```

Disallowed:

```text
- adding new SC-C feature behavior;
- adding topology materialization logic;
- adding transport/API layer;
- adding SC-B, SC-D, Projection or Authority dependencies.
```

---

## 5. Implementation Scope

This MIR authorizes:

```text
- regression tests;
- cross-MU integration tests;
- durable recovery tests;
- read/write separation tests;
- provider identity negative tests;
- topologyVersion stability tests;
- boundary/dependency tests;
- implementation-report classification of inherited vs new coverage.
```

This MIR MAY authorize low-cost test-only architecture guards:

```text
- package dependency inspection;
- reflection-based dependency checks;
- ArchUnit-style checks, if dependency is already available or low-cost.
```

This MIR does **not** authorize:

```text
- Topology Materialization;
- TemporalActs;
- Adapter Lifecycle;
- Adapter Admission;
- SC-B request/reply;
- SC-D adapter facts;
- REST/gRPC/WebSocket APIs;
- MCP facade;
- Projection / Effective View;
- Authority / Policy logic;
- action terminal result persistence;
- production storage schema;
- storage ADR.
```

---

## 6. Hardening Targets

### H-001 — Base Topology morphology

Validate that the SC-C topology still preserves the canonical layers:

```text
HabitatBaseTopology
  rooms
  zones
  devices
  endpoints
  capabilities
  topologyVersion
  metadata
```

Required guarantees:

```text
DeviceNode remains stable topological container.
EndpointNode remains addressable operational locus.
CapabilityNode remains canonical affordance.
Provider refs remain metadata.
```

### H-002 — topologyVersion rules

Validate or inherit validated coverage for:

```text
accepted structural mutation advances topologyVersion;
rejected mutation does not advance topologyVersion;
state update does not advance topologyVersion;
health update does not advance topologyVersion;
snapshot query does not advance topologyVersion;
device lookup does not advance topologyVersion;
endpoint lookup does not advance topologyVersion.
```

### H-003 — Persistence/recovery consistency

Validate over durable path:

```text
Base Topology survives recovery;
topologyVersion survives recovery;
canonical deviceId survives recovery;
canonical endpointId survives recovery;
provider refs survive as metadata;
device state survives if persisted;
endpoint health survives if persisted;
query layer can read recovered state.
```

### H-004 — Read/write separation

Validate:

```text
BaseTopologyService owns mutation paths.
CoreSnapshotQueryService owns read paths.
CoreSnapshotQueryService does not read BaseTopologyService internal state.
CoreSnapshotQueryService does not depend on BaseTopologyService object lifetime.
```

### H-005 — Boundary exclusion

Validate that SC-C kernel has no dependency on:

```text
SC-B replay;
SC-D rediscovery;
Hub memory;
Projection;
Effective View;
Session;
Identity;
Authority;
Policy;
Surface layout.
```

### H-006 — Provider identity protection

Validate:

```text
providerDeviceId is not canonical deviceId;
providerEndpointId is not canonical endpointId;
provider refs are returned only as metadata;
provider IDs do not satisfy canonical lookup;
provider IDs do not silently fallback to canonical lookup.
```

### H-007 — Target validation continuity

Validate:

```text
validateTarget(...) remains usable after persistence, recovery and query.
```

Do not introduce:

```text
TargetResolutionSnapshot
```

as a mandatory path.

---

## 7. Coverage Classification

### 7.1 High-value cross-MU ACs

These ACs are the highest value of this MIR.

```text
AC-011
Core Snapshot Query does not advance topologyVersion.

Value:
  crosses MU-002 topologyVersion rules with MU-006 query semantics.

AC-017
CoreSnapshotQueryService reads persisted state, not BaseTopologyService internal state.

Value:
  crosses MU-004 persistence/memory with MU-006 read boundary.

AC-018
CoreSnapshotQueryService does not depend on retaining the same BaseTopologyService instance.

Value:
  crosses MU-004 recovery with MU-006 read/write separation.

AC-023
validateTarget(...) remains usable after persistence/recovery/query.

Value:
  crosses MU-002 target validation, MU-004 recovery and MU-006 query.
```

### 7.2 Regression consolidation ACs

These ACs likely have existing coverage but must remain protected.

```text
AC-007
Structural mutation advances topologyVersion.

AC-008
Rejected mutation does not advance topologyVersion.

AC-009
State update does not advance topologyVersion.

AC-010
Health update does not advance topologyVersion.

AC-012
Recovered topologyVersion equals recovered snapshot/aggregate version.

AC-013
Recovery preserves canonical deviceId.

AC-014
Recovery preserves canonical endpointId.

AC-015
Provider refs survive recovery as metadata.

AC-016
Provider refs are not accepted as canonical IDs.
```

Rule:

```text
These ACs MAY be satisfied by inherited test coverage plus one composed hardening test.
They do not require duplicating existing unit tests unless current coverage is insufficient.
```

### 7.3 Boundary guard ACs

```text
AC-019
SC-C kernel tests require no SC-B replay.

AC-020
SC-C kernel tests require no SC-D rediscovery.

AC-021
SC-C kernel tests require no Projection or Effective View.

AC-022
SC-C kernel tests require no Session, Identity, Authority or Policy.

AC-024
No new feature scope is introduced.

AC-025
Build/test command succeeds.
```

---

## 8. Acceptance Criteria

### 8.1 Kernel baseline

```text
AC-001
All existing MU-001, MU-002, MU-004 and MU-006 tests still pass.

AC-002
A dedicated SC-C kernel hardening test suite exists.

AC-003
The implementation report classifies each AC as:
  inherited coverage,
  regression consolidation,
  new cross-MU validation,
  or boundary guard.
```

### 8.2 Base Topology morphology

```text
AC-004
Base Topology morphology is regression-tested.

AC-005
DeviceNode remains stable topological container.

AC-006
EndpointNode remains addressable operational locus.

AC-007
CapabilityNode remains canonical affordance.
```

### 8.3 topologyVersion behavior

```text
AC-008
Structural mutation advances topologyVersion.

AC-009
Rejected mutation does not advance topologyVersion.

AC-010
State update does not advance topologyVersion.

AC-011
Core Snapshot Query does not advance topologyVersion.
```

### 8.4 Durable recovery and identity

```text
AC-012
Recovered topologyVersion equals recovered snapshot/aggregate version.

AC-013
Recovery preserves canonical deviceId.

AC-014
Recovery preserves canonical endpointId.

AC-015
Provider refs survive recovery as metadata.

AC-016
Provider refs are not accepted as canonical IDs.
```

### 8.5 Read/write separation

```text
AC-017
CoreSnapshotQueryService reads persisted state, not BaseTopologyService internal state.

AC-018
CoreSnapshotQueryService does not depend on retaining the same BaseTopologyService instance.
```

### 8.6 Boundary exclusion

```text
AC-019
SC-C kernel tests require no SC-B replay.

AC-020
SC-C kernel tests require no SC-D rediscovery.

AC-021
SC-C kernel tests require no Projection or Effective View.

AC-022
SC-C kernel tests require no Session, Identity, Authority or Policy.
```

### 8.7 Target validation and scope control

```text
AC-023
validateTarget(...) remains usable after persistence/recovery/query.

AC-024
No new feature scope is introduced.

AC-025
Build/test command succeeds.
```

---

## 9. Durable Hardening Test Pattern

The primary new composed hardening test SHOULD use the durable H2 path.

Suggested test class:

```text
ScCoreKernelHardeningTest
```

Suggested flow:

```text
1. create H2BaseTopologyRepository using file-backed temp storage;
2. create BaseTopologyService over durable repository;
3. create CoreSnapshotQueryService over CoreSnapshotReadPort;
4. create initial topology;
5. perform accepted structural mutation;
6. persist device state;
7. persist endpoint health;
8. capture topologyVersion;
9. discard repository/service/query instances;
10. recreate repository from same durable store;
11. recreate CoreSnapshotQueryService;
12. query current snapshot;
13. assert query does not advance topologyVersion;
14. assert recovered topologyVersion matches recovered topology/snapshot;
15. assert canonical deviceId survives recovery;
16. assert canonical endpointId survives recovery;
17. assert provider IDs fail canonical lookup;
18. assert provider refs remain metadata;
19. assert query reads durable state;
20. create new BaseTopologyService only to call validateTarget(...);
21. assert validateTarget(...) remains usable with recovered topologyVersion;
22. assert no SC-B / SC-D / Projection / Authority dependencies are required.
```

The test MUST NOT:

```text
reuse old BaseTopologyService instance;
reuse old CoreSnapshotQueryService instance;
reuse old H2BaseTopologyRepository instance;
depend on service-local ConcurrentMap;
call SC-B;
call SC-D;
perform Projection;
dispatch actions.
```

---

## 10. Failure Signals

```text
FS-001
Hardening requires implementing new SC-C feature behavior.

FS-002
Hardening requires SC-B replay.

FS-003
Hardening requires SC-D rediscovery.

FS-004
Hardening requires Projection or Effective View.

FS-005
Hardening requires Session, Identity, Authority or Policy.

FS-006
EndpointNode becomes provider-specific detail.

FS-007
Provider ID can satisfy canonical lookup.

FS-008
State or health read advances topologyVersion.

FS-009
Query depends on BaseTopologyService internal memory.

FS-010
Recovery correctness depends on retaining old Java object instances.

FS-011
TopologyVersion mismatches recovered topology.

FS-012
Test suite requires external devices or real adapters.

FS-013
Test suite introduces production storage doctrine.

FS-014
Hardening creates REST/gRPC/SC-B/MCP transport.

FS-015
Hardening introduces TargetResolutionSnapshot as mandatory path.

FS-016
Hardening introduces Projection-facing DTOs.

FS-017
Existing accepted MIR tests are broken.

FS-018
Hardening duplicates existing tests without adding inherited-coverage classification or cross-MU value.
```

---

## 11. Execution Package Requirements

The execution package includes:

```text
docs/mir/mir-010/context.md
docs/mir/mir-010/codex-prompt.md
docs/mir/mir-010/acceptance-map.md
docs/mir/mir-010/implementation-report.md
```

`context.md` lists the validated code surface.

`context.md` defines the hardening wiring pattern.

`codex-prompt.md` instructs:

```text
Prefer adding composed regression and boundary tests.
Do not duplicate existing tests unless duplication adds cross-MU value.
Classify inherited coverage explicitly.
Only add production code if a hardening test exposes a genuine missing invariant.
```

`acceptance-map.md` preserves AC-001 through AC-025.

`implementation-report.md` includes:

```text
- AC result table;
- coverage classification table;
- inherited coverage references;
- new test evidence;
- boundary guard evidence;
- deviations from scope;
- failure signals encountered;
- recommended next MU.
```

---

## 12. Negative Scope

This MIR explicitly excludes:

```text
Topology Materialization
TemporalActs
Adapter Lifecycle
Adapter Admission
SC-B request/reply
SC-B NATS/JetStream binding
SC-D adapter facts
SC-D protocol implementation
REST API
gRPC API
WebSocket API
MCP facade
Projection
Effective View
Session
Identity
Authority
Policy
Surface layout
action terminal result persistence
production persistence schema
storage ADR
TargetResolutionSnapshot as mandatory path
```

---

## 13. Suggested Branch

```text
test/sc-c-kernel-hardening-010
```

---

## 14. Suggested Commit

Preferred:

```text
test(sc-c): harden core kernel invariants
```

Alternative if production code is minimally adjusted due to an exposed invariant gap:

```text
test(sc-c): add kernel hardening coverage
```

or:

```text
fix(sc-c): preserve kernel invariant under recovery
```

---

## 15. Accepted Basis

Accepted basis:

```text
A-001
Final reviewer approval for execution scope: satisfied.

A-002
implementation-report.md remains post-execution artifact: satisfied.

A-003
Implementation may proceed under:
  docs/mir/mir-010/context.md
  docs/mir/mir-010/codex-prompt.md
  docs/mir/mir-010/acceptance-map.md

A-004
User-provided context.md and codex-prompt.md replace prior draft versions: satisfied.

A-005
Stale prompt reference to context §9 / 22-step flow patched: satisfied.
```

Implementation authorization:

```text
Authorized.
```

---

## 16. Changelog

```text
v1.0.0-accepted
- Promotes MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 from Candidate to Accepted.
- Authorizes implementation under docs/mir/mir-010/context.md, codex-prompt.md and acceptance-map.md.
- Uses the finalized context.md with exact API surface, inherited coverage map, gap analysis and durable H2 wiring pattern.
- Uses the finalized codex-prompt.md with reduced three-step execution strategy.
- Applies patch replacing stale reference to context §9 / 22-step flow with durable wiring pattern from context §5 and semantic assertion groups.
- Confirms implementation-report.md remains pending post-execution artifact.
- Confirms target branch test/sc-c-kernel-hardening-010.
- Confirms suggested commit test(sc-c): harden core kernel invariants.
- Preserves target validation level L4.

v0.2.0-candidate
- Promotes MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001 from Draft to Candidate.
- Confirms docs/mir/mir-010/context.md has been authored.
- Confirms docs/mir/mir-010/codex-prompt.md has been authored.
- Confirms docs/mir/mir-010/acceptance-map.md has been authored.
- Keeps implementation-report.md as pending post-execution artifact.
- Preserves the hardening coverage classification model.
- Preserves H2 durable adapter as required cross-MU hardening substrate.
- Preserves no-duplication rule for inherited coverage.
- Confirms target branch test/sc-c-kernel-hardening-010.
- Confirms suggested commit test(sc-c): harden core kernel invariants.
- Preserves target validation level L4.

v0.1.1-draft
- Incorporates hardening coverage classification.
- Adds MIR-010-D-001: classify ACs as inherited coverage, regression consolidation, new cross-MU validation or boundary guard.
- Adds MIR-010-D-002: durable cross-MU tests should use H2BaseTopologyRepository or equivalent durable adapter.
- Adds MIR-010-D-003: do not duplicate existing tests without cross-MU value.
- Adds MIR-010-D-004: test-first hardening.
- Clarifies that AC-011, AC-017, AC-018 and AC-023 are highest-value cross-MU validations.
- Clarifies that AC-007 to AC-010 and AC-012 to AC-016 may be satisfied by inherited coverage plus composed hardening.
- Strengthens execution package requirements with H2 durable wiring.
- Adds FS-018 against redundant test duplication.

v0.1.0-draft
- Opens SC-C Kernel Hardening Seed MIR.
- Defines hardening as regression/invariant/boundary validation, not feature work.
- Establishes hardening targets:
  Base Topology morphology,
  topologyVersion rules,
  persistence/recovery consistency,
  read/write separation,
  boundary exclusion,
  provider identity protection,
  target validation continuity.
- Defines AC-001 through AC-025.
- Defines failure signals.
- Reserves execution package under docs/mir/mir-010/.
```

---

# Dictamen de cierre

```text
MIR-SOV-SC-C-KERNEL-HARDENING-SEED-001
Version: v1.0.0-accepted
Status: Accepted
Target MU: MU-SOV-SC-C-KERNEL-HARDENING-SEED-001
Operational Slot: MU-010
Target validation level: L4
Implementation authorization: yes
Open blockers: none known
```
