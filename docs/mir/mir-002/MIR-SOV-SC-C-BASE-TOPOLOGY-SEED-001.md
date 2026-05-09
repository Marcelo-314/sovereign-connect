

# MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001

## SC-C topologyVersion Seed Materialization

Document ID: MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001
Title: SC-C topologyVersion Seed Materialization
Version: v1.0.0-accepted
Status: Accepted
Date: 2026-05-09
Corpus: Sovereign Connect
Type: MIR — Materialization Increment Record
Plane: SC-C
Materialization Unit: MU-SOV-SC-C-TOPOLOGY-VERSION-SEED-001

Supersedes:
  - MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 v0.2.0-candidate
  - MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 v0.1.0-draft

Depends on:
  - NT-SOV-SC-MATERIALIZATION-UNITS-001 v0.1.0-draft
  - PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.2-draft
  - INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.1-draft
  - SYNC-SOV-SC-MU-BACKLOG-001 v0.1.2-draft
  - RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
  - RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft
  - PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft
  - PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.6-draft
  - PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft
  - MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted

Execution package:
  - docs/mir/mir-002/context.md
  - docs/mir/mir-002/codex-prompt.md
  - docs/mir/mir-002/implementation-report.md
  - docs/mir/mir-002/acceptance-map.md

Execution package status:
  - context.md: authored / approved
  - codex-prompt.md: authored / approved
  - acceptance-map.md: authored / approved
  - implementation-report.md: pending post-execution

Target repository:
  - sovereign-connect

Suggested target branch:
  - feat/sc-c-mir-topology-version-002

Suggested commit:
  - feat(sc-c): harden topology version seed


---

# 0. Status Notice

This MIR is **Accepted**.

Accepted status means:

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


Implementation is authorized under:

docs/mir/mir-002/context.md
docs/mir/mir-002/codex-prompt.md
docs/mir/mir-002/acceptance-map.md


The implementation report MUST be produced after execution at:

docs/mir/mir-002/implementation-report.md


This MIR MUST NOT inline implementation context or executable prompt content.

The implementation context and Codex prompt are external operational artifacts under:

docs/mir/mir-002/
  context.md
  codex-prompt.md
  implementation-report.md
  acceptance-map.md


This MIR follows:

PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.2-draft


---

# 1. Purpose

This MIR governs the descent of:

MU-SOV-SC-C-TOPOLOGY-VERSION-SEED-001


The purpose of this Materialization Unit is to harden the `topologyVersion` seed introduced during `MU-001`.

`MU-001` validated that SC-C can materialize Base Topology with a habitat-scoped version marker. `MU-002` must now make that version marker explicit, testable and operationally useful before durable persistence, snapshot query or broader topology materialization are introduced.

This MIR validates the following hypothesis:

SC-C can own topologyVersion as a Habitat-scoped, Base-Topology-bound version marker that advances only on accepted structural topology mutation, participates in stale target detection, remains excluded from idempotency identity and does not derive from Projection, Session, Identity, Authority, Policy or provider-native versioning.


---

# 2. Materialization Unit

MU ID: MU-SOV-SC-C-TOPOLOGY-VERSION-SEED-001
Title: SC-C topologyVersion and Snapshot Versioning Seed
Type: Kernel MU
Plane: SC-C
Status: Accepted for Implementation

Primary invariant validated:
  - SC-C owns topologyVersion.
  - topologyVersion belongs to Base Topology.
  - topologyVersion is scoped at least to Habitat.
  - topologyVersion advances only after accepted structural Base Topology mutation.
  - topologyVersion is included in Base Topology snapshots.
  - topologyVersion participates in stale target detection.
  - topologyVersion does not participate in idempotency identity.
  - topologyVersion does not derive from Projection, Session, Identity, Authority, Policy or provider-native versions.


## 2.1 MU thesis

topologyVersion must be hardened before durable persistence, snapshot query, topology materialization and endpoint-aware command targeting are allowed to depend on it.


## 2.2 MU expected output

The implementation attempt SHOULD produce:

- explicit TopologyVersion value object or equivalent domain value;
- explicit TopologyVersionScope with mandatory HABITAT scope;
- initial topologyVersion behavior;
- structural mutation advancement behavior;
- rejected mutation non-advancement behavior;
- non-structural state/health non-advancement behavior;
- snapshot consistency with topologyVersion;
- TopologyChanged or equivalent mutation result with fromVersion/toVersion;
- stale target revalidation behavior;
- idempotency identity exclusion behavior;
- tests proving all required acceptance criteria;
- implementation report with AC table.


## 2.3 MU failure value

This MU is valuable even if it fails.

Failure would reveal early that one of the following is underspecified or structurally wrong:

- topologyVersion scope;
- structural vs non-structural mutation boundary;
- version advancement semantics;
- snapshot consistency semantics;
- stale target detection semantics;
- TopologyChanged version transition semantics;
- idempotency boundary;
- provider-native version boundary;
- Projection exclusion boundary;
- readiness for durable persistence.


---

# 3. Artifact Bundle

## 3.1 Normative artifacts

- PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.2-draft
  Role: MIR governance and filesystem convention
  Status: Normative

- INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.1-draft
  Role: MU graph placement
  Status: Normative

- SYNC-SOV-SC-MU-BACKLOG-001 v0.1.2-draft
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
  Status: Normative for this MIR

- MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted
  Role: validated prior MU
  Status: Normative dependency for current implementation context


## 3.2 Operational execution artifacts

Execution package:

docs/mir/mir-002/context.md
docs/mir/mir-002/codex-prompt.md
docs/mir/mir-002/implementation-report.md
docs/mir/mir-002/acceptance-map.md


Status:

context.md:
  authored / approved

codex-prompt.md:
  authored / approved

acceptance-map.md:
  authored / approved

implementation-report.md:
  pending post-execution


Rules:

- context.md MUST define repository-local implementation context.
- codex-prompt.md MUST reference context.md.
- codex-prompt.md MUST use acceptance-map.md for AC numbering.
- implementation-report.md MUST reference this MIR version.
- acceptance-map.md MUST preserve AC-001 through AC-025.
- This MIR MUST NOT inline prompt or context content.


## 3.3 Conditionally normative artifacts

- TCK-SOV-SC-C-TOPOLOGY-VERSION-001 version TBD
  Required only if created before MIR acceptance.

- PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001 version TBD
  Not required for this MIR.
  Future downstream after topologyVersion hardening.

- PDR-SOV-SC-C-PERSISTENCE-MEMORY-001 version TBD
  Not required for this MIR.
  Future downstream after topologyVersion hardening.

- ADR-SOV-SC-C-STORAGE-TECH-001 version TBD
  Not required for this MIR.


---

# 4. Readiness Assessment

Ownership clear: yes
Primary plane clear: yes — SC-C
Prior MU validated: yes — MU-001 Validated L4
Primary PDR available: yes — PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft
Implementation surface known: yes — existing MU-001 codebase
Persistence impact known: yes — durable persistence excluded
SC-B dependency: none
SC-D dependency: none
Projection dependency: forbidden
Acceptance criteria available: yes
Failure signals available: yes
Execution package available: yes
Implementation authorization: yes


## 4.1 Readiness decision

Current MIR status:
  Accepted

Ready for implementation:
  yes

Implementation authorization:
  authorized under approved execution package.


## 4.2 Accepted basis

Accepted promotion is justified because:

- Candidate scope has been reviewed and approved;
- the 25 acceptance criteria are testable;
- implementation scope is incremental over MU-001;
- negative scope is exhaustive;
- implementation-report.md remains a post-execution artifact;
- execution may proceed under:
    docs/mir/mir-002/context.md
    docs/mir/mir-002/codex-prompt.md
    docs/mir/mir-002/acceptance-map.md
- no Accepted blockers remain open.


The approved execution artifacts are:

docs/mir/mir-002/context.md
docs/mir/mir-002/codex-prompt.md
docs/mir/mir-002/acceptance-map.md


The implementation report remains intentionally absent until implementation is attempted.

---

# 5. Implementation Scope

This MIR allows implementation of a **minimal SC-C topologyVersion hardening seed**.

It does not authorize durable persistence, full snapshot query, SC-B transport or real SC-D integration.

The implementation SHOULD build on the code produced by `MU-001`.

## 5.1 TopologyVersion value

Implementation MUST define or preserve a domain value equivalent to:

java
public record TopologyVersion(
    TopologyVersionScope scope,
    String value
) {}


Equivalent forms are allowed if they preserve:

scope
opaque value
equality
non-provider-native origin
non-projection origin


## 5.2 TopologyVersion scope

Implementation MUST define or preserve a scope equivalent to:

java
public record TopologyVersionScope(
    TopologyVersionScopeType type,
    String id
) {}


Minimum mandatory scope:

java
public enum TopologyVersionScopeType {
    HABITAT
}


The implementation MAY NOT introduce additional scopes unless already present and harmless.

If additional scopes exist, tests for this MIR MUST still prove `HABITAT` behavior.

## 5.3 Initial version behavior

Implementation MUST ensure that an initial Base Topology snapshot has an initial `topologyVersion`.

The initial version MUST be associated with the Habitat scope.

Allowed examples:

HABITAT:<habitatId>:1
1
v1
opaque-string-backed-by-monotonic-counter


The external shape may be opaque, but tests MUST prove it exists and is scoped.

## 5.4 Structural mutation advancement

Implementation MUST advance `topologyVersion` after accepted structural Base Topology mutation.

At minimum, tests MUST cover one structural mutation.

Recommended seed mutation:

add EndpointNode to existing DeviceNode


Alternative allowed structural mutations:

add DeviceNode
add CapabilityNode
move EndpointNode
change ProviderEndpointRef when it affects canonical binding


The implementation MUST record or expose:

fromVersion
toVersion


for the mutation result or internal `TopologyChanged` equivalent.

Implementation SHOULD preserve backward compatibility with existing MU-001 service behavior.

If a new mutation result is required, the preferred strategy is to add a new method returning a mutation result rather than changing existing public service behavior.

## 5.5 Rejected mutation non-advancement

Implementation MUST prove that a rejected mutation does not advance `topologyVersion`.

A rejected mutation MAY be:

add EndpointNode to missing DeviceNode
add EndpointNode with duplicate endpointId
add CapabilityNode to missing EndpointNode
providerEndpointId used as canonical endpointId
invalid structural mutation request


The exact rejected case may follow repository-local constraints.

If the existing service does not reject any invalid structural mutation yet, implementation MAY add minimal validation required to prove this criterion.

Implementation MUST NOT generalize this into a full topology validation framework.

## 5.6 Non-structural changes

Implementation MUST prove that non-structural changes do not advance `topologyVersion`.

Minimum required non-structural categories:

operational state update
health update


If the current codebase does not yet implement state/health update services, the implementation MAY introduce minimal seed methods or test doubles to prove the negative boundary.

The implementation MUST NOT add full state/health materialization scope.

Clarification:

topologyVersion versions structural Base Topology.

Seed state/health updates MAY change operational metadata without advancing topologyVersion.

This is not snapshot inconsistency, provided structural topology remains unchanged.


## 5.7 Snapshot consistency

Implementation MUST ensure that retrieved Base Topology snapshots are internally consistent with their `topologyVersion`.

At minimum:

a snapshot contains its topologyVersion;
after accepted structural mutation, retrieved snapshot has the new version;
no retrieved snapshot exposes new topology with old version;
no retrieved snapshot exposes old topology with new version.


Seed-level in-memory atomicity is sufficient.

## 5.8 Stale target detection seed

Implementation MUST add minimal stale target revalidation behavior.

This does not require full command dispatch.

At minimum, implement a domain service or validator that can answer:

given ActionTarget-like reference and requestTopologyVersion,
is target valid under current Base Topology?


Required cases:

same version + target exists => valid
stale version + target still exists with same capability => valid after revalidation
stale version + target removed/missing => invalid before dispatch


This MUST NOT invoke SC-B or SC-D.

## 5.9 Idempotency exclusion seed

Implementation MUST prove that `topologyVersion` is not part of idempotency identity.

It may implement a minimal function or test-only domain value equivalent to:

idempotencyIdentity = operationKind + ActionTarget + canonical params


Required test:

same operationKind + same target + same canonical params + different topologyVersion
=> same idempotency identity


This MIR does not require full idempotency persistence.

## 5.10 TopologyChanged or mutation result

Implementation MUST provide at least one of:

TopologyChanged domain event
TopologyMutationResult domain object
equivalent mutation result


It MUST expose:

fromVersion
toVersion
changeKinds


for accepted structural mutation.

It MUST prove:

toVersion != fromVersion


after mutation.

---

# 6. Negative Scope

This MIR explicitly excludes:

durable persistence
database schema
JPA/H2 migration unless already existing and trivial
distributed versioning
cluster coordination
event sourcing
transactional outbox
SC-B transport
NATS / JetStream
Vert.x
gRPC
MQTT
WebSocket binding
real SC-D adapters
provider discovery
provider payload parsing
provider-native version reconciliation beyond metadata exclusion
full ActionTarget PDR implementation
full command dispatch
real device execution
adapter lifecycle
adapter admission
TemporalActs
full snapshot query API
historical snapshot retrieval
Projection
Effective View
VisibilityRule
Session
Identity
Authority
Policy
Hub
Surface
MCP exposure
UI cache invalidation


This MIR also excludes choosing final external version ordering semantics.

External consumers MAY compare equality/staleness only unless future contracts define ordering.

---

# 7. Required Invariants

Implementation MUST preserve the following invariants.

## 7.1 Ownership invariants

SC-C owns topologyVersion.
SC-B does not own topologyVersion semantics.
SC-D does not assign or advance topologyVersion.
Projection does not assign or advance topologyVersion.


## 7.2 Base Topology invariants

topologyVersion belongs to Base Topology.
topologyVersion is included in Base Topology snapshot or equivalent aggregate.
Base Topology snapshots are internally consistent with topologyVersion.


## 7.3 Advancement invariants

accepted structural mutation advances topologyVersion.
rejected structural mutation does not advance topologyVersion.
state update does not advance topologyVersion.
health update does not advance topologyVersion.
Projection-like changes do not advance topologyVersion.
Session/Identity/Authority/Policy-like changes do not advance topologyVersion.
provider-native version changes do not directly advance topologyVersion.


## 7.4 Stale target invariants

version mismatch triggers revalidation.
version mismatch does not automatically fail.
version mismatch does not automatically dispatch.
invalid current target fails before dispatch.


## 7.5 Idempotency invariant

topologyVersion is validation context, not idempotency identity.


## 7.6 Event/result invariant

accepted structural mutation produces a fromVersion/toVersion transition.


---

# 8. Minimal Acceptance Criteria

The implementation passes this MIR only if all mandatory acceptance criteria pass.

## 8.1 Domain criteria

AC-001
TopologyVersion exists as a typed value or equivalent domain value object.

AC-002
TopologyVersion is scoped at least to Habitat.

AC-003
An initial Base Topology snapshot has an initial topologyVersion.


## 8.2 Advancement criteria

AC-004
Accepted structural mutation advances topologyVersion.

AC-005
Rejected mutation does not advance topologyVersion.

AC-006
Operational state update does not advance topologyVersion.

AC-007
Health update does not advance topologyVersion.

AC-008
Projection-like change does not advance topologyVersion.

AC-009
Session-like change does not advance topologyVersion.

AC-010
Authority/policy-like change does not advance topologyVersion.


## 8.3 Provider boundary criteria

AC-011
Provider-native version is not used as canonical topologyVersion.

AC-012
Provider-native revision may be stored as metadata without advancing topologyVersion by itself.


## 8.4 Snapshot criteria

AC-013
Snapshot includes topologyVersion.

AC-014
Snapshot nodes and topologyVersion are internally consistent.


## 8.5 Event/result criteria

AC-015
TopologyChanged or equivalent domain result contains fromVersion and toVersion.

AC-016
toVersion differs from fromVersion after structural mutation.


## 8.6 Stale target criteria

AC-017
Stale request topologyVersion triggers revalidation.

AC-018
Stale request topologyVersion does not automatically fail if current target remains valid.

AC-019
Stale request topologyVersion fails before dispatch if target no longer exists or no longer matches current Base Topology.


## 8.7 Idempotency criteria

AC-020
topologyVersion is excluded from idempotency identity.


## 8.8 Boundary criteria

AC-021
SC-B is not required to decide topologyVersion semantics.

AC-022
SC-D is not allowed to assign or advance topologyVersion.

AC-023
No user/session/identity/authority/policy field is required to create or advance topologyVersion.


## 8.9 Test/build criteria

AC-024
Tests cover structural and non-structural mutation behavior.

AC-025
Build/test command succeeds.


---

# 9. Failure Signals

Implementation MUST stop and report upstream if any of the following occurs.

FS-001
topologyVersion cannot be defined without Projection.

FS-002
topologyVersion requires sessionId, userId, authority or policy context.

FS-003
provider-native version must be used as canonical topologyVersion.

FS-004
SC-D must assign or advance topologyVersion.

FS-005
SC-B must decide topologyVersion semantics.

FS-006
non-structural state/health updates force topologyVersion advancement.

FS-007
snapshot can be observed with mismatched topology and version.

FS-008
accepted mutation and version advancement cannot be made atomic even at seed semantic level.

FS-009
stale target detection cannot distinguish version mismatch from target invalidity.

FS-010
topologyVersion must participate in idempotency identity.

FS-011
TopologyChanged or equivalent mutation result cannot represent fromVersion/toVersion.

FS-012
Base Topology implementation collapses topologyVersion into schemaVersion, checksum or lastModified.

FS-013
implementation requires durable persistence before seed semantics can be tested.

FS-014
implementation requires full SC-B transport before seed semantics can be tested.

FS-015
implementation requires real SC-D adapter before seed semantics can be tested.


---

# 10. Upstream Patch Protocol

The implementation report MUST classify issues as one of:

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


## 10.1 Patch routing

If topologyVersion ownership is unclear:
  patch PDR-SOV-SC-TOPOLOGY-VERSION-001.

If structural/non-structural mutation boundary is unclear:
  patch PDR-SOV-SC-TOPOLOGY-VERSION-001.

If Base Topology snapshot shape is unclear:
  patch RFC-SOV-SC-BASE-TOPOLOGY-001
  or open PDR-SOV-SC-C-CORE-SNAPSHOT-QUERY-001.

If EndpointNode mutation semantics are unclear:
  patch PDR-SOV-SC-ENDPOINT-NODE-001.

If stale ActionTarget behavior is unclear:
  patch PDR-SOV-SC-ACTION-TARGET-001.

If topologyVersion/idempotency boundary is unclear:
  patch PDR-SOV-SC-CANONICAL-CONTRACT-001
  and/or ADR-SOV-SC-PERSISTENCE-IDEMPOTENCY-001.

If implementation requires durable persistence:
  open PDR-SOV-SC-C-PERSISTENCE-MEMORY-001
  or ADR-SOV-SC-C-STORAGE-TECH-001.

If SC-B transport becomes necessary:
  stop implementation and report out-of-scope request.

If real SC-D adapters become necessary:
  stop implementation and report out-of-scope request.

If Projection becomes necessary:
  patch RFC-SOV-SC-BASE-TOPOLOGY-001
  and review Projection boundary documents.


---

# 11. Codex Prompt Reference

This MIR does not inline the executable implementation prompt or the implementation context.

Canonical execution package:

Context:
  docs/mir/mir-002/context.md

Codex Prompt:
  docs/mir/mir-002/codex-prompt.md

Implementation Report:
  docs/mir/mir-002/implementation-report.md

Acceptance Map:
  docs/mir/mir-002/acceptance-map.md


Rules:

- context.md MUST be read before codex-prompt.md is executed.
- codex-prompt.md MUST reference context.md.
- codex-prompt.md MUST use acceptance-map.md for AC numbering.
- implementation-report.md MUST reference this MIR version.
- acceptance-map.md MUST preserve AC-001 through AC-025 numbering.
- context.md and codex-prompt.md MAY be refined without advancing this MIR version.
- Any refinement that changes scope, acceptance criteria, failure signals or required invariants MUST advance this MIR or produce a patch.


---

# 12. Suggested Branch

feat/sc-c-mir-topology-version-002


---

# 13. Suggested Commit

Preferred:

feat(sc-c): harden topology version seed


If the increment mainly adds validation services:

feat(sc-c): add topology version validation seed


If the increment mainly updates tests and domain behavior:

test(sc-c): validate topology version semantics


---

# 14. Post-Implementation Report Requirements

The implementation project MUST return:

1. Summary
2. Files changed
3. Domain types added or modified
4. Services/validators added or modified
5. Repository / persistence strategy used
6. Tests added
7. Acceptance criteria result table
8. Required invariants preserved
9. Deviations from MIR
10. Failure signals encountered
11. Assumptions made
12. Corpus issues discovered
13. Recommended upstream patches
14. Recommended next MU


## 14.1 Acceptance table format

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


## 14.2 Invariant table format

SC-C owns topologyVersion: preserved/broken/unclear
topologyVersion belongs to Base Topology: preserved/broken/unclear
topologyVersion is Habitat-scoped: preserved/broken/unclear
accepted structural mutation advances topologyVersion: preserved/broken/unclear
rejected mutation does not advance topologyVersion: preserved/broken/unclear
state/health updates do not advance topologyVersion: preserved/broken/unclear
Projection does not advance topologyVersion: preserved/broken/unclear
provider-native version does not become topologyVersion: preserved/broken/unclear
snapshot/version consistency preserved: preserved/broken/unclear
stale target revalidation implemented: preserved/broken/unclear
topologyVersion excluded from idempotency identity: preserved/broken/unclear
SC-B does not own topologyVersion semantics: preserved/broken/not applicable
SC-D does not assign or advance topologyVersion: preserved/broken/not applicable


---

# 15. Non-blocking Open Questions

These questions do not block Accepted status.

OQ-001
Should topologyVersion expose ordering semantics externally, or only equality/staleness?

Default:
  equality/staleness only.

OQ-002
Should snapshot by version be supported now?

Default:
  no. Current snapshot is sufficient for MU-002.

OQ-003
Should topologyVersion be represented internally as long, string, ULID or structured opaque value?

Default:
  use the existing MU-001 representation unless it violates this MIR.

OQ-004
Should state/health update services be introduced just to prove non-structural behavior?

Default:
  minimal seed/test-only service is acceptable.
  Do not introduce full state/health materialization.

OQ-005
Should stale target validation reuse ActionTarget types from Canonical Contract or define a local seed reference?

Default:
  use existing ActionTarget if available.
  otherwise define a minimal seed reference and report the limitation.

OQ-006
Should provider-native revision metadata be introduced now?

Default:
  only if useful for testing exclusion.
  Do not introduce provider reconciliation.


---

# 16. Versioning and Acceptance Level

Current MIR status:

Accepted


Target acceptance level after implementation:

L4


Meaning:

L4 = implementation attempted and local tests pass.


This MIR does not require L5 TCK validation yet.

Future L5 validation may require:

TCK-SOV-SC-C-TOPOLOGY-VERSION-001


---

# 17. Accepted Closure

Accepted blockers resolved:

A-001
Final reviewer approval for Candidate scope:
  closed

A-002
implementation-report.md remains post-execution artifact:
  closed

A-003
implementation may proceed under approved execution package:
  closed


Execution authorization:

Authorized.


Authorized execution package:

docs/mir/mir-002/context.md
docs/mir/mir-002/codex-prompt.md
docs/mir/mir-002/acceptance-map.md


Post-execution evidence:

docs/mir/mir-002/implementation-report.md


Execution branch:

feat/sc-c-mir-topology-version-002


Recommended first commit after implementation:

feat(sc-c): harden topology version seed


---

# 18. Changelog

v1.0.0-accepted
- Promotes MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 from Candidate to Accepted.
- Confirms Candidate scope has been reviewed and approved.
- Confirms all 25 ACs are testable.
- Confirms implementation scope is incremental over MU-001.
- Confirms negative scope is exhaustive.
- Confirms implementation-report.md remains a post-execution artifact.
- Authorizes implementation under:
  docs/mir/mir-002/context.md
  docs/mir/mir-002/codex-prompt.md
  docs/mir/mir-002/acceptance-map.md
- Closes Accepted blockers A-001, A-002 and A-003.
- Preserves target validation level L4.
- Preserves suggested branch:
  feat/sc-c-mir-topology-version-002
- Preserves suggested commit:
  feat(sc-c): harden topology version seed

v0.2.0-candidate
- Promotes MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001 from Draft to Candidate.
- Confirms docs/mir/mir-002/context.md has been authored and approved.
- Confirms docs/mir/mir-002/codex-prompt.md has been authored and approved.
- Confirms docs/mir/mir-002/acceptance-map.md has been authored and approved.
- Closes Draft blockers B-001, B-002 and B-003.
- Keeps implementation-report.md as pending post-execution artifact.
- Maintains implementation authorization as not yet granted.
- Clarifies that codex-prompt.md MUST use acceptance-map.md for AC-001 through AC-025 numbering.
- Preserves target validation level L4.

v0.1.0-draft
- Opens MIR for MU-SOV-SC-C-TOPOLOGY-VERSION-SEED-001.
- Targets topologyVersion hardening after MU-001 L4 validation.
- Depends on PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft.
- Defines scope for Habitat-scoped topologyVersion.
- Defines accepted structural mutation advancement behavior.
- Defines rejected mutation and non-structural non-advancement behavior.
- Defines snapshot consistency requirements.
- Defines stale target revalidation seed.
- Defines idempotency exclusion seed.
- Defines TopologyChanged / mutation result fromVersion-toVersion requirement.
- Externalizes context and Codex prompt under docs/mir/mir-002/.
- Sets target validation level L4.



