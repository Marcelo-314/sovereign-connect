# MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001

## SC-C Persistence and Memory Seed Materialization

```text
Document ID: MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
Title: SC-C Persistence and Memory Seed Materialization
Version: v1.0.0-accepted
Status: Accepted
Date: 2026-05-10
Corpus: Sovereign Connect
Type: MIR — Materialization Increment Record
Plane: SC-C
Materialization Unit: MU-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001

Supersedes:
  - MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 v0.2.0-candidate
  - MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 v0.1.0-draft

Depends on:
  - NT-SOV-SC-MATERIALIZATION-UNITS-001 v0.1.0-draft
  - PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.3-draft
  - INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.2-draft
  - SYNC-SOV-SC-MU-BACKLOG-001 v0.1.3-draft
  - RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
  - RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft
  - PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft
  - PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.6-draft
  - PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft
  - PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft
  - MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted
  - MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 v1.0.0-accepted

Execution package:
  - docs/mir/mir-004/MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001.md
  - docs/mir/mir-004/context.md
  - docs/mir/mir-004/codex-prompt.md
  - docs/mir/mir-004/implementation-report.md
  - docs/mir/mir-004/acceptance-map.md

Execution package status:
  - context.md: authored / approved
  - codex-prompt.md: authored / approved
  - acceptance-map.md: authored / approved
  - implementation-report.md: pending post-execution

Target repository:
  - sovereign-connect

Suggested target branch:
  - feat/sc-c-mir-persistence-memory-004

Suggested commit:
  - feat(sc-c): add persistence memory seed
```

---

# 0. Status Notice

This MIR is **Accepted**.

Accepted status means:

```text
- the Materialization Unit is scoped;
- normative dependencies are pinned;
- implementation scope is bounded;
- negative scope is explicit;
- acceptance criteria are testable;
- failure signals are defined;
- context.md has been authored and approved;
- codex-prompt.md has been authored and approved;
- acceptance-map.md has been authored and approved;
- implementation-report.md is reserved as post-execution evidence;
- execution is authorized under the approved package.
```

Implementation is authorized under:

```text
docs/mir/mir-004/context.md
docs/mir/mir-004/codex-prompt.md
docs/mir/mir-004/acceptance-map.md
```

The implementation report MUST be produced after execution at:

```text
docs/mir/mir-004/implementation-report.md
```

This MIR MUST NOT inline implementation context or executable prompt content.

The implementation context and Codex prompt are external operational artifacts under:

```text
docs/mir/mir-004/
  MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001.md
  context.md
  codex-prompt.md
  implementation-report.md
  acceptance-map.md
```

Filenames are stable and MUST NOT include version suffixes.

Document version MUST be declared inside each document metadata header.

This MIR follows:

```text
PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.3-draft
```

---

# 1. Purpose

This MIR governs the descent of:

```text
MU-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
```

The purpose of this Materialization Unit is to validate that SC-C can persist and recover SC-C-owned canonical state across a repository/service recreation boundary.

`MU-001` validated that SC-C can materialize Base Topology in memory.

`MU-002` validated that SC-C can own and harden `topologyVersion` semantics.

`MU-004` must now validate that SC-C can retain and recover:

```text
- Base Topology;
- topologyVersion;
- canonical node identities;
- provider binding metadata;
- topology mutation memory;
- operational device state;
- endpoint health;
- enough current state for future snapshot query and stale target validation.
```

This MIR validates the following hypothesis:

```text
SC-C can own persistence and operational memory for SC-C-owned state without requiring SC-B replay, SC-D rediscovery, Hub memory, Projection, Session, Identity, Authority or Policy.
```

---

# 2. Materialization Unit

```text
MU ID: MU-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
Title: SC-C Persistence and Memory Seed
Type: Kernel MU
Plane: SC-C
Status: Accepted for Implementation

Primary invariant validated:
  - SC-C owns persistence for SC-C-owned state.
  - SC-C persists Base Topology.
  - SC-C persists topologyVersion.
  - persisted topology and topologyVersion are internally consistent.
  - recovery does not require SC-B replay.
  - recovery does not require SC-D rediscovery.
  - recovery does not require Hub, Projection, Session, Identity, Authority or Policy.
  - provider refs persist as metadata, not canonical identity.
  - operational state and health persistence do not advance topologyVersion by themselves.
```

## 2.1 MU thesis

```text
Persistence/Memory must be validated before Core Snapshot Query can be implemented.
```

Reason:

```text
Queryable consistent snapshots require a persistence/memory substrate.
```

## 2.2 MU expected output

The implementation attempt SHOULD produce:

```text
- durable or file-backed seed persistence adapter;
- compatibility with existing MU-001/MU-002 repository contract;
- persisted Base Topology snapshot;
- persisted current topologyVersion;
- recovery after repository/service recreation;
- canonical ID preservation after recovery;
- provider refs preserved as metadata after recovery;
- minimal mutation memory/ledger;
- operational device state memory/persistence;
- endpoint health memory/persistence;
- tests proving topologyVersion/snapshot consistency;
- tests proving recovery without SC-B or SC-D;
- tests proving no Projection/Session/Authority/Policy dependency;
- implementation report with AC table.
```

## 2.3 MU failure value

This MU is valuable even if it fails.

Failure would reveal early that one of the following is underspecified or structurally wrong:

```text
- SC-C persistence ownership;
- Base Topology serialization/recovery boundary;
- topologyVersion/snapshot consistency;
- repository compatibility with prior seeds;
- provider binding persistence semantics;
- operational state memory boundary;
- health memory boundary;
- recovery dependency boundary;
- readiness for Core Snapshot Query.
```

---

# 3. Artifact Bundle

## 3.1 Normative artifacts

```text
- PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.3-draft
  Role: MIR governance, filesystem convention and filename convention
  Status: Normative

- INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.2-draft
  Role: MU graph placement
  Status: Normative

- SYNC-SOV-SC-MU-BACKLOG-001 v0.1.3-draft
  Role: operational backlog synchronization
  Status: Normative

- RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
  Role: SC-C ownership boundary
  Status: Normative

- RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft
  Role: Base Topology doctrine
  Status: Normative

- PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft
  Role: EndpointNode / DeviceNode / CapabilityNode morphology
  Status: Normative

- PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.6-draft
  Role: canonical type shape and cross-plane contract
  Status: Normative

- PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft
  Role: topologyVersion semantic contract
  Status: Normative

- PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft
  Role: persistence and memory semantic contract
  Status: Normative for this MIR

- MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted
  Role: validated prior MU
  Status: Normative dependency

- MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 v1.0.0-accepted
  Role: validated prior MU
  Status: Normative dependency
```

## 3.2 Operational execution artifacts

Execution package:

```text
docs/mir/mir-004/MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001.md
docs/mir/mir-004/context.md
docs/mir/mir-004/codex-prompt.md
docs/mir/mir-004/implementation-report.md
docs/mir/mir-004/acceptance-map.md
```

Status:

```text
context.md:
  authored / approved

codex-prompt.md:
  authored / approved

acceptance-map.md:
  authored / approved

implementation-report.md:
  pending post-execution
```

Rules:

```text
- context.md MUST define repository-local implementation context.
- codex-prompt.md MUST reference context.md.
- codex-prompt.md MUST use acceptance-map.md for AC numbering.
- implementation-report.md MUST reference this MIR version.
- acceptance-map.md MUST preserve AC-001 through AC-025.
- This MIR MUST NOT inline prompt or context content.
```

## 3.3 Conditionally normative artifacts

```text
- ADR-SOV-SC-C-STORAGE-TECH-001 version TBD
  Not required before this MIR unless seed cannot proceed without a storage technology decision.

- SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001 version TBD
  Not required before this MIR.

- SDD-SOV-SC-C-STATE-MATERIALIZATION-001 version TBD
  Not required before this MIR.

- SDD-SOV-SC-C-RECOVERY-001 version TBD
  Not required before this MIR.

- TCK-SOV-SC-C-PERSISTENCE-MEMORY-001 version TBD
  Not required before L4 seed validation.
```

---

# 4. Readiness Assessment

```text
Ownership clear: yes
Primary plane clear: yes — SC-C
Prior MU validated: yes — MU-001 Validated L4
Prior MU validated: yes — MU-002 Validated L4
Primary PDR available: yes — PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft
Implementation surface known: yes — existing MU-001/MU-002 codebase documented in context.md
Persistence impact known: yes — seed must go beyond pure in-memory
SC-B dependency: none
SC-D dependency: none
Projection dependency: forbidden
Acceptance criteria available: yes
Failure signals available: yes
Execution package available: yes
Implementation authorization: yes
```

## 4.1 Readiness decision

```text
Current MIR status:
  Accepted

Ready for implementation:
  yes

Implementation authorization:
  authorized under approved execution package.
```

## 4.2 Accepted basis

Accepted promotion is justified because:

```text
- Candidate scope has been reviewed and approved;
- the three execution artifacts are authored and approved;
- implementation-report.md remains a post-execution artifact;
- the 25 acceptance criteria are testable;
- implementation scope is incremental over MU-001/MU-002;
- negative scope is exhaustive;
- package explicitly avoids JPA by default and prefers JDBC/H2 seed infrastructure;
- package explicitly prevents mutation ledger semantics from being inferred opaquely from save(...);
- package explicitly prevents BaseTopologyService from depending on H2BaseTopologyRepository directly;
- execution may proceed under:
    docs/mir/mir-004/context.md
    docs/mir/mir-004/codex-prompt.md
    docs/mir/mir-004/acceptance-map.md
- no Accepted blockers remain open.
```

The approved execution artifacts are:

```text
docs/mir/mir-004/context.md
docs/mir/mir-004/codex-prompt.md
docs/mir/mir-004/acceptance-map.md
```

The implementation report remains intentionally absent until implementation is attempted.

---

# 5. Implementation Scope

This MIR allows implementation of a **minimal SC-C persistence/memory seed**.

It does not authorize final production storage design.

It does not authorize durable persistence doctrine.

It does not authorize full snapshot query API.

It does not authorize event sourcing, transactional outbox, distributed persistence, SC-B replay or SC-D rediscovery.

## 5.1 Seed durability target

The implementation MUST validate persistence stronger than pure in-memory storage.

Minimum target:

```text
SC-C can write canonical state,
recreate repository/service layer,
and recover equivalent canonical state.
```

Acceptable seed strategies:

```text
file-backed H2
embedded local database
simple file-backed repository
serialized aggregate store
repository-local durable test fixture
existing repository persistence convention
```

Pure in-memory storage alone is insufficient for this MIR.

Seed storage technology MUST NOT be declared final production storage technology.

## 5.2 Existing repository compatibility

This MIR MUST preserve compatibility with the existing MU-001/MU-002 repository contract.

Existing repository concept:

```text
BaseTopologyRepository
```

Known existing behavior from prior MUs:

```text
save(...)
findByHabitatId(...)
findCurrentVersion(...)
```

MIR-004-D-001:

```text
Do not replace BaseTopologyRepository as a breaking change.
```

Preferred implementation strategy:

```text
- keep the existing BaseTopologyRepository behavior unchanged;
- add a durable adapter implementing the existing port where possible;
- extend the existing port only if required;
- add snapshot-specific methods through a wrapper, extension interface or adapter if needed.
```

Allowed approaches:

```text
Approach A:
  Durable adapter implements existing BaseTopologyRepository.

Approach B:
  Extended interface adds snapshot methods while preserving existing methods.

Approach C:
  Persistence-specific wrapper delegates to existing repository semantics.

Approach D:
  Adapter exposes saveSnapshot/findCurrentSnapshot internally while preserving public BaseTopologyRepository compatibility.
```

Disallowed:

```text
- silently removing existing BaseTopologyRepository methods;
- changing existing public method signatures without reporting;
- forcing MU-001/MU-002 tests to be rewritten unnecessarily;
- replacing repository semantics with a new incompatible port;
- coupling BaseTopologyService to H2BaseTopologyRepository as a concrete class.
```

Any public API break MUST be reported as a deviation in `implementation-report.md`.

## 5.3 BaseTopologySnapshot envelope rule

This MIR chooses the envelope strategy.

MIR-004-D-002:

```text
BaseTopologySnapshot is a persistence/query envelope.
```

Allowed seed shape:

```java
public record BaseTopologySnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    HabitatBaseTopology topology,
    Instant capturedAt
) {}
```

Rule:

```text
BaseTopologySnapshot.topologyVersion is a derived/indexed copy of
BaseTopologySnapshot.topology.topologyVersion.
```

Source of truth:

```text
HabitatBaseTopology.topologyVersion remains the source of truth.
```

The envelope-level `topologyVersion` exists to support:

```text
- faster current version reads;
- future snapshot query;
- stale target validation;
- persistence indexing;
- recovery checks without requiring full aggregate traversal.
```

Implementation MUST prove:

```text
snapshot.topologyVersion == snapshot.topology.topologyVersion
```

A mismatch between envelope version and aggregate version is a critical failure.

## 5.4 Base Topology snapshot persistence

Implementation MUST support saving and retrieving the current Base Topology snapshot.

Minimum semantics:

```text
save current topology snapshot
retrieve current topology snapshot by habitatId
retrieve current topologyVersion by habitatId
```

The persisted snapshot MUST include:

```text
habitatId
topologyVersion
HabitatBaseTopology or equivalent serialized aggregate
capturedAt or equivalent timestamp metadata
```

The implementation MAY serialize the aggregate as JSON/blob for seed purposes.

The seed MUST NOT claim that the serialized shape is the final production schema.

## 5.5 topologyVersion persistence consistency

Persisted topology and persisted topologyVersion MUST be internally consistent.

The implementation MUST NOT allow recovery of:

```text
new topology with old topologyVersion
```

or:

```text
old topology with new topologyVersion
```

Seed-level atomicity MAY be achieved through:

```text
single aggregate write
single file replacement
single embedded DB transaction
single repository commit operation
```

## 5.6 Recovery

The implementation MUST prove that after repository/service recreation, SC-C can recover:

```text
current Base Topology snapshot
current topologyVersion
canonical deviceId
canonical endpointId
provider binding metadata
```

Recovery MUST NOT require:

```text
SC-B replay
SC-D rediscovery
Hub conversation memory
Projection cache
Session
Identity
Authority
Policy
Surface state
```

## 5.7 Provider binding persistence

Provider refs MUST persist as metadata.

Provider refs MUST NOT become canonical identity after recovery.

The implementation MUST prove:

```text
providerDeviceId != deviceId
providerEndpointId != endpointId
```

after recovery.

## 5.8 Mutation memory / ledger seed

The implementation MUST persist or durably remember at least one accepted structural mutation record.

Preferred source:

```text
TopologyMutationResult returned by addEndpointWithResult(...)
```

Minimum mutation record semantics:

```text
habitatId
fromVersion
toVersion
changeKinds
affectedDeviceIds
affectedEndpointIds
acceptedAt
```

A full event-sourcing design is not required.

A full transactional outbox is not required.

The mutation memory may be:

```text
file-backed
database-backed
serialized list
embedded table
repository-local ledger
```

The implementation MUST prove that mutation memory can be read back for the Habitat.

## 5.9 Operational state memory

The implementation MUST support storing device operational state without advancing `topologyVersion`.

Minimum seed behavior:

```text
save device state
retrieve device state
prove topologyVersion unchanged
```

Operational state MAY be seed-level key/value state.

Full state materialization policy is out of scope.

`BaseTopologyService` MUST NOT depend on `H2BaseTopologyRepository` as a concrete class.

Allowed approaches:

```text
Approach A:
  introduce a small optional DeviceStateMemoryPort and inject it through an additional BaseTopologyService constructor while preserving existing constructors.

Approach B:
  introduce an interface such as PersistentTopologyMemoryPort implemented by the durable adapter, while keeping BaseTopologyRepository methods intact.

Approach C:
  use durable-adapter methods directly in MIR-004 tests to prove recovery, while preserving the existing service-local behavior for InMemoryBaseTopologyRepository.
```

## 5.10 Health memory

The implementation MUST support storing endpoint health without advancing `topologyVersion`.

Minimum seed behavior:

```text
save endpoint health
retrieve endpoint health
prove topologyVersion unchanged
```

Full health policy is out of scope.

Endpoint health MAY be recovered from the persisted aggregate.

A separate `endpoint_health` table MAY be used for direct health lookup, but MUST remain seed-level.

## 5.11 Recovery and stale target validation

The implementation MUST prove that recovered `topologyVersion` can be used by existing stale target validation semantics from MU-002.

Minimum proof:

```text
persist topology
recover topologyVersion
validate target using recovered topologyVersion/current topology
```

This MUST NOT require command dispatch.

This MUST NOT require SC-B or SC-D.

## 5.12 Storage technology non-finality

If the seed uses H2, file storage or another local mechanism, the implementation report MUST state:

```text
Seed storage technology:
  <technology>

Production storage decision:
  not decided

ADR required before production:
  ADR-SOV-SC-C-STORAGE-TECH-001
```

The implementation MUST NOT hard-code seed technology as architecture doctrine.

---

# 6. Negative Scope

This MIR explicitly excludes:

```text
final production database decision
final database vendor
final ORM decision
final schema migration strategy
final normalized persistence schema
distributed persistence
multi-node consensus
cloud synchronization
event sourcing as mandatory architecture
transactional outbox implementation
SC-B durable message storage
SC-B replay as recovery source
SC-D rediscovery as recovery source
SC-D provider-native cache as canonical store
Hub conversation memory
Projection cache
Effective View cache
Session persistence
Identity persistence
Authority persistence
Policy persistence
Surface preferences
TemporalActs scheduling
action terminal result persistence
idempotency persistence
full snapshot query API
historical snapshot retrieval
MCP exposure
TL retention/audit ledger policy
production backup/restore strategy
production encryption/key management
JPA entity model unless already present in the repository
```

This MIR also excludes making H2, file storage, JSON blob storage or any other seed mechanism final production doctrine.

---

# 7. Required Invariants

## 7.1 Ownership invariants

```text
SC-C owns persistence for SC-C-owned state.
SC-B does not become authoritative persistence.
SC-D does not become authoritative topology persistence.
Projection does not persist canonical Base Topology.
Hub memory does not become SC-C persistence.
Session/Identity/Authority/Policy do not participate in SC-C recovery.
```

## 7.2 Topology persistence invariants

```text
Base Topology can be persisted.
Base Topology can be recovered.
topologyVersion can be persisted.
topologyVersion can be recovered.
canonical deviceId survives recovery.
canonical endpointId survives recovery.
provider refs survive recovery as metadata.
provider refs do not become canonical identity.
```

## 7.3 Snapshot invariants

```text
BaseTopologySnapshot is an envelope.
HabitatBaseTopology.topologyVersion is source of truth.
BaseTopologySnapshot.topologyVersion is derived/indexed copy.
snapshot.topologyVersion equals snapshot.topology.topologyVersion.
recovered topology and recovered topologyVersion are consistent.
```

## 7.4 State/health invariants

```text
device state persistence does not advance topologyVersion.
endpoint health persistence does not advance topologyVersion.
operational memory does not become structural mutation unless explicitly materialized.
```

## 7.5 Recovery invariants

```text
recovery does not require SC-B replay.
recovery does not require SC-D rediscovery.
recovery does not require Projection.
recovery does not require Hub.
recovery does not require Session, Identity, Authority or Policy.
```

## 7.6 Technology boundary invariants

```text
seed storage technology is not final production doctrine.
ADR-SOV-SC-C-STORAGE-TECH-001 remains future decision point if production storage must be selected.
```

---

# 8. Minimal Acceptance Criteria

The implementation passes this MIR only if all mandatory acceptance criteria pass.

## 8.1 Persistence port / repository criteria

```text
AC-001
A persistence-capable repository, adapter or port exists for Base Topology snapshots.

AC-002
Existing BaseTopologyRepository behavior is preserved or explicitly backward-compatible.

AC-003
A current Base Topology snapshot can be saved.

AC-004
A current Base Topology snapshot can be retrieved.
```

## 8.2 Snapshot/version criteria

```text
AC-005
The retrieved snapshot includes topologyVersion.

AC-006
snapshot.topologyVersion equals snapshot.topology.topologyVersion.

AC-007
The retrieved topology and topologyVersion are internally consistent.

AC-008
findCurrentVersion or equivalent returns the recovered current topologyVersion.
```

## 8.3 Recovery criteria

```text
AC-009
Persisted topology survives repository/service recreation or equivalent process-boundary simulation.

AC-010
Recovery does not require SC-B replay.

AC-011
Recovery does not require SC-D rediscovery.

AC-012
Recovery does not require Hub, Projection, Session, Identity, Authority or Policy.
```

## 8.4 Identity and provider binding criteria

```text
AC-013
Canonical deviceId survives recovery unchanged.

AC-014
Canonical endpointId survives recovery unchanged.

AC-015
Provider refs survive recovery as metadata.

AC-016
Provider refs do not become canonical identity after recovery.
```

## 8.5 Mutation memory criteria

```text
AC-017
A structural mutation can be recorded with fromVersion and toVersion.

AC-018
Mutation memory/ledger can be read back for the Habitat.
```

## 8.6 Operational memory criteria

```text
AC-019
Operational device state can be stored without advancing topologyVersion.

AC-020
Operational device state can be recovered if included in seed scope.

AC-021
Endpoint health can be stored without advancing topologyVersion.

AC-022
Endpoint health can be recovered if included in seed scope.
```

## 8.7 Boundary and technology criteria

```text
AC-023
Pure in-memory-only storage is not claimed as persistence validation.

AC-024
Seed storage technology is not declared final production storage technology.

AC-025
Build/test command succeeds.
```

---

# 9. Failure Signals

Implementation MUST stop and report upstream if any of the following occurs.

```text
FS-001
Persistence requires Hub conversation memory.

FS-002
Persistence requires Projection or Effective View.

FS-003
Persistence requires Session, Identity, Authority or Policy.

FS-004
SC-B must become authoritative persistence for Base Topology.

FS-005
SC-D must become authoritative persistence for canonical topology.

FS-006
Canonical topology cannot survive repository/service recreation.

FS-007
Recovered topology loses canonical deviceId or endpointId.

FS-008
Provider refs become canonical identity after recovery.

FS-009
Recovered topologyVersion does not match recovered topology.

FS-010
snapshot.topologyVersion differs from snapshot.topology.topologyVersion.

FS-011
State or health persistence forces topologyVersion advancement.

FS-012
Persistent topology cannot be recovered without SC-D rediscovery.

FS-013
Implementation requires final storage ADR before seed can be tested.

FS-014
Implementation claims pure in-memory repository validates persistence.

FS-015
Storage technology choice becomes hard-coded as doctrine.

FS-016
TemporalActs persistence becomes necessary for this MU.

FS-017
Snapshot query API becomes necessary for this MU.

FS-018
SC-B transport becomes necessary for this MU.

FS-019
Real SC-D adapter becomes necessary for this MU.

FS-020
Existing BaseTopologyRepository behavior must be broken to implement the seed.

FS-021
BaseTopologyService must depend on H2BaseTopologyRepository directly.
```

---

# 10. Upstream Patch Protocol

The implementation report MUST classify issues as one of:

```text
documentation ambiguity
missing type
missing flow
missing invariant
contradiction
wrong ownership
unresolved ADR
SDD gap
TCK gap
implementation bug
out-of-scope request
repository mismatch
storage seed limitation
backward compatibility risk
infrastructure-domain coupling risk
```

## 10.1 Patch routing

```text
If persistence ownership is unclear:
  patch PDR-SOV-SC-C-PERSISTENCE-MEMORY-001.

If BaseTopologyRepository compatibility is unclear:
  patch this MIR and/or PDR-SOV-SC-C-PERSISTENCE-MEMORY-001.

If BaseTopologySnapshot envelope semantics are unclear:
  patch this MIR and/or PDR-SOV-SC-C-PERSISTENCE-MEMORY-001.

If topologyVersion consistency is unclear:
  patch PDR-SOV-SC-TOPOLOGY-VERSION-001.

If Base Topology aggregate shape is unclear:
  patch RFC-SOV-SC-BASE-TOPOLOGY-001
  or PDR-SOV-SC-ENDPOINT-NODE-001.

If durable storage choice blocks seed implementation:
  open ADR-SOV-SC-C-STORAGE-TECH-001.

If final persistence schema becomes necessary:
  open SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001.

If recovery behavior is underspecified:
  open SDD-SOV-SC-C-RECOVERY-001.

If SC-B transport becomes necessary:
  stop implementation and report out-of-scope request.

If SC-D rediscovery becomes necessary:
  stop implementation and report out-of-scope request.

If Projection becomes necessary:
  patch RFC-SOV-SC-BASE-TOPOLOGY-001
  and review Projection boundary documents.
```

---

# 11. Codex Prompt Reference

This MIR does not inline the executable implementation prompt or the implementation context.

Canonical execution package:

```text
Context:
  docs/mir/mir-004/context.md

Codex Prompt:
  docs/mir/mir-004/codex-prompt.md

Implementation Report:
  docs/mir/mir-004/implementation-report.md

Acceptance Map:
  docs/mir/mir-004/acceptance-map.md
```

Rules:

```text
- context.md MUST be read before codex-prompt.md is executed.
- codex-prompt.md MUST reference context.md.
- codex-prompt.md MUST use acceptance-map.md for AC numbering.
- implementation-report.md MUST reference this MIR version.
- acceptance-map.md MUST preserve AC-001 through AC-025 numbering.
- context.md and codex-prompt.md MAY be refined without advancing this MIR version.
- Any refinement that changes scope, acceptance criteria, failure signals or required invariants MUST advance this MIR or produce a patch.
```

---

# 12. Suggested Branch

```text
feat/sc-c-mir-persistence-memory-004
```

---

# 13. Suggested Commit

Preferred:

```text
feat(sc-c): add persistence memory seed
```

If the increment mainly adds a file-backed adapter:

```text
feat(sc-c): add file-backed topology persistence
```

If the increment mainly adds H2-backed persistence:

```text
feat(sc-c): add h2 topology persistence seed
```

If the increment mainly adds tests around persistence semantics:

```text
test(sc-c): validate persistence memory semantics
```

---

# 14. Post-Implementation Report Requirements

The implementation project MUST return:

```text
1. Summary
2. Files changed
3. Domain types added or modified
4. Repository / persistence adapters added or modified
5. Services added or modified
6. Storage seed strategy used
7. Tests added
8. Acceptance criteria result table
9. Required invariants preserved
10. Deviations from MIR
11. Failure signals encountered
12. Assumptions made
13. Corpus issues discovered
14. Recommended upstream patches
15. Recommended next MU
```

## 14.1 Acceptance table format

```text
AC-001: pass/fail
AC-002: pass/fail
AC-003: pass/fail
AC-004: pass/fail
AC-005: pass/fail
AC-006: pass/fail
AC-007: pass/fail
AC-008: pass/fail
AC-009: pass/fail
AC-010: pass/fail
AC-011: pass/fail
AC-012: pass/fail
AC-013: pass/fail
AC-014: pass/fail
AC-015: pass/fail
AC-016: pass/fail
AC-017: pass/fail
AC-018: pass/fail
AC-019: pass/fail
AC-020: pass/fail
AC-021: pass/fail
AC-022: pass/fail
AC-023: pass/fail
AC-024: pass/fail
AC-025: pass/fail
```

## 14.2 Invariant table format

```text
SC-C owns persistence for SC-C-owned state: preserved/broken/unclear
Base Topology persists and recovers: preserved/broken/unclear
topologyVersion persists and recovers: preserved/broken/unclear
snapshot.topologyVersion equals aggregate topologyVersion: preserved/broken/unclear
canonical deviceId survives recovery: preserved/broken/unclear
canonical endpointId survives recovery: preserved/broken/unclear
provider refs remain metadata: preserved/broken/unclear
state persistence does not advance topologyVersion: preserved/broken/unclear
health persistence does not advance topologyVersion: preserved/broken/unclear
recovery does not require SC-B: preserved/broken/unclear
recovery does not require SC-D: preserved/broken/unclear
recovery does not require Projection/Session/Authority/Policy: preserved/broken/unclear
seed storage is not declared final production storage: preserved/broken/unclear
BaseTopologyRepository compatibility preserved: preserved/broken/unclear
BaseTopologyService does not depend on H2BaseTopologyRepository directly: preserved/broken/unclear
```

---

# 15. Non-blocking Open Questions

These questions do not block Accepted status.

```text
OQ-001
Should MU-004 use H2 file-backed persistence or a simpler file-backed repository?

Default:
  choose the smallest seed strategy compatible with process-boundary survival.

OQ-002
Should mutation memory be stored in the same file/database as topology snapshots?

Default:
  yes for seed simplicity, unless repository structure makes separation easier.

OQ-003
Should BaseTopologySnapshot be introduced as a new domain record or adapter-local persistence envelope?

Default:
  introduce as a domain/persistence boundary record if useful,
  but preserve HabitatBaseTopology as aggregate source of truth.

OQ-004
Should OperationalStateMemoryPort and HealthMemoryPort be separate ports?

Default:
  only if separation is low-cost.
  A combined seed adapter is acceptable if semantics are explicit.

OQ-005
Should provider bindings be persisted inside the topology aggregate or separately?

Default:
  aggregate persistence is acceptable for seed.
  separate provider binding store belongs to later schema/SDD if needed.

OQ-006
Should this MU produce an ADR?

Default:
  no, unless implementation cannot proceed without a technology decision.

OQ-007
Should H2/JDBC be kept entirely test-local?

Default:
  yes, unless the repository needs a dev adapter to run the seed outside tests.

OQ-008
Should device state persistence be wired through BaseTopologyService now?

Default:
  only if it can be done without breaking existing constructors or coupling to H2.
  Adapter-direct seed tests are acceptable for MIR-004.
```

---

# 16. Versioning and Acceptance Level

Current MIR status:

```text
Accepted
```

Target acceptance level after implementation:

```text
L4
```

Meaning:

```text
L4 = implementation attempted and local tests pass.
```

This MIR does not require L5 TCK validation yet.

Future L5 validation may require:

```text
TCK-SOV-SC-C-PERSISTENCE-MEMORY-001
```

---

# 17. Accepted Closure

Accepted blockers resolved:

```text
A-001
Final reviewer approval for Candidate scope:
  closed

A-002
implementation-report.md remains post-execution artifact:
  closed

A-003
implementation may proceed under approved execution package:
  closed
```

Execution authorization:

```text
Authorized.
```

Authorized execution package:

```text
docs/mir/mir-004/context.md
docs/mir/mir-004/codex-prompt.md
docs/mir/mir-004/acceptance-map.md
```

Post-execution evidence:

```text
docs/mir/mir-004/implementation-report.md
```

Execution branch:

```text
feat/sc-c-mir-persistence-memory-004
```

Recommended first commit after implementation:

```text
feat(sc-c): add persistence memory seed
```

---

# 18. Changelog

```text
v1.0.0-accepted
- Promotes MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 from Candidate to Accepted.
- Confirms Candidate scope has been reviewed and approved.
- Confirms the three execution artifacts are authored and approved.
- Confirms implementation-report.md remains a post-execution artifact.
- Authorizes implementation under:
  docs/mir/mir-004/context.md
  docs/mir/mir-004/codex-prompt.md
  docs/mir/mir-004/acceptance-map.md
- Closes Accepted blockers A-001, A-002 and A-003.
- Preserves target validation level L4.
- Preserves suggested branch:
  feat/sc-c-mir-persistence-memory-004
- Preserves suggested commit:
  feat(sc-c): add persistence memory seed

v0.2.0-candidate
- Promotes MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001 from Draft to Candidate.
- Confirms docs/mir/mir-004/context.md has been authored and approved.
- Confirms docs/mir/mir-004/codex-prompt.md has been authored and approved.
- Confirms docs/mir/mir-004/acceptance-map.md has been authored and approved.
- Closes Draft blockers B-001, B-002 and B-003.
- Replaces JPA as default seed path with H2/JDBC seed guidance.
- Clarifies that MIR-004 validates durability/recovery semantics, not ORM mapping.
- Clarifies mutation ledger must preferably be sourced from TopologyMutationResult.
- Clarifies that save(HabitatBaseTopology) MUST NOT infer mutation semantics as primary strategy.
- Clarifies device state persistence must not couple BaseTopologyService to H2BaseTopologyRepository.
- Adds FS-021 for direct BaseTopologyService → H2BaseTopologyRepository coupling.
- Maintains implementation-report.md as pending post-execution artifact.
- Maintains implementation authorization as not yet granted.
- Preserves target validation level L4.

v0.1.0-draft
- Opens MIR for MU-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001.
- Targets Persistence/Memory validation after MU-001 and MU-002 L4 validation.
- Depends on PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 v0.1.0-draft.
- Defines seed durability target as repository/service recreation survival.
- Requires persistence stronger than pure in-memory storage.
- Preserves existing BaseTopologyRepository compatibility.
- Adds MIR-004-D-001: do not replace BaseTopologyRepository as breaking change.
- Adds MIR-004-D-002: BaseTopologySnapshot is a persistence/query envelope.
- Defines snapshot.topologyVersion as derived/indexed copy of aggregate topologyVersion.
- Requires snapshot.topologyVersion == snapshot.topology.topologyVersion.
- Defines Base Topology snapshot persistence scope.
- Defines topologyVersion persistence consistency.
- Defines recovery requirements without SC-B replay or SC-D rediscovery.
- Defines provider binding persistence as metadata.
- Defines mutation memory / ledger seed requirement.
- Defines operational device state memory and endpoint health memory.
- Excludes final storage ADR, final schema, SC-B transport, SC-D rediscovery, Projection, Session, Identity, Authority and Policy.
- Defines acceptance criteria, failure signals and upstream patch protocol.
- Reserves execution package under docs/mir/mir-004/.
- Sets target validation level L4.
```

---

# Dictamen de cierre

```text
MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
Version: v1.0.0-accepted
Status: Accepted
Target MU: MU-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
Target validation level: L4
Implementation authorization: yes
Open Accepted blockers: none known
```

Execution authorized against:

```text
Branch:
  feat/sc-c-mir-persistence-memory-004

Execution package:
  docs/mir/mir-004/context.md
  docs/mir/mir-004/codex-prompt.md
  docs/mir/mir-004/acceptance-map.md
```

Recommended commit:

```text
feat(sc-c): add persistence memory seed
```
