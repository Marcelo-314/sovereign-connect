

# MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001

## SC-C Base Topology Seed Materialization


Document ID: MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001
Title: SC-C Base Topology Seed Materialization
Version: v1.0.0-accepted
Status: Accepted
Date: 2026-05-09
Corpus: Sovereign Connect
Type: MIR — Materialization Increment Record
Plane: SC-C
Materialization Unit: MU-SOV-SC-C-BASE-TOPOLOGY-SEED-001

Supersedes:
  - MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v0.3.0-candidate
  - MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v0.2.0-draft
  - MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v0.1.0-draft

Depends on:
  - NT-SOV-SC-MATERIALIZATION-UNITS-001 v0.1.0-draft
  - PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.2-draft
  - SYNC-SOV-SC-MU-BACKLOG-001 v0.1.1-draft
  - INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.0-draft
  - RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
  - RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft
  - PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft
  - PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.6-draft
  - PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.1.0-draft

Execution package:
  - docs/mir/mir-001/context.md
  - docs/mir/mir-001/codex-prompt.md
  - docs/mir/mir-001/implementation-report.md
  - docs/mir/mir-001/acceptance-map.md

Target repository:
  - sovereign-connect

Suggested target branch:
  - feat/sc-c-mir-base-topology-001

Suggested commit:
  - feat(sc-c): materialize base topology seed


---

# 0. Status Notice

This MIR is **Accepted**.

Accepted status means:


- the Materialization Unit is scoped;
- normative dependencies are pinned;
- acceptance criteria are explicit;
- failure signals are defined;
- execution artifacts are externalized;
- implementation may be attempted under the referenced operational package;
- post-implementation evidence must be returned through implementation-report.md.


This MIR follows the `PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.2-draft` convention:


MIR governance document:
  defines scope, invariants, acceptance criteria, failure signals and reporting protocol.

context.md:
  defines reference implementation context.

codex-prompt.md:
  defines executable implementation prompt.

implementation-report.md:
  records implementation evidence.

acceptance-map.md:
  records AC-001 through AC-025 mapping.


This MIR MUST NOT inline implementation context or executable prompt content.

---

# 1. Purpose

This MIR governs the descent of:


MU-SOV-SC-C-BASE-TOPOLOGY-SEED-001


The purpose of this Materialization Unit is to validate that SC-C can materialize the minimal Base Topology kernel while preserving the core architectural invariants of Sovereign Connect.

This MIR tests whether SC-C can represent, store/retrieve and test a minimal Base Topology containing:


HabitatBaseTopology
RoomNode
ZoneNode
DeviceNode
EndpointNode
CapabilityNode
ProviderEndpointRef
TopologyMetadata
TopologyVersion
BaseTopologySnapshot or equivalent aggregate


The goal is not to implement full SC-C.

The goal is to validate the first structural hypothesis:


SC-C can own Base Topology as a canonical, endpoint-aware, provider-neutral structure without depending on Projection, Session, Identity, Authority, Policy, Hub, Surfaces, SC-B transport or real SC-D adapters.


---

# 2. Materialization Unit


MU ID: MU-SOV-SC-C-BASE-TOPOLOGY-SEED-001
Title: SC-C Base Topology Seed
Type: Kernel MU
Plane: SC-C
Status: Accepted for Implementation / L4 validation path
Primary invariant validated:
  - SC-C owns Base Topology.
  - EndpointNode is a Base Topology invariant.
  - ProviderEndpointRef is metadata, not canonical identity.
  - Base Topology excludes Projection / Effective View.
  - Base Topology can exist without Session, Identity, Authority or Surface context.


## 2.1 MU thesis


Base Topology must be materializable before SC can safely compose adapters, bus routing, command targeting, Projection or device execution.


## 2.2 MU expected output

The implementation attempt SHOULD produce:


- minimal Base Topology domain model;
- DeviceNode / EndpointNode / CapabilityNode separation;
- ProviderEndpointRef as provider-binding metadata;
- topologyVersion seed behavior;
- snapshot creation and retrieval;
- tests proving multi-endpoint device representation;
- tests proving no user/session/projection dependency;
- implementation report with acceptance table.


## 2.3 MU failure value

This MU is valuable even if it fails.

A failure would reveal early that one of the following is underspecified or wrong:


- EndpointNode morphology;
- canonical identity boundary;
- ProviderEndpointRef semantics;
- topologyVersion ownership;
- Base Topology persistence boundary;
- separation between Base Topology and Projection;
- feasibility of SC-C as canonical materialization core.


---

# 3. Artifact Bundle

## 3.1 Normative artifacts


- NT-SOV-SC-MATERIALIZATION-UNITS-001 v0.1.0-draft
  Role: MU doctrine
  Status: Normative for MU/MIR interpretation

- PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.2-draft
  Role: MIR governance
  Status: Normative for MIR structure, filesystem convention and execution artifact separation

- SYNC-SOV-SC-MU-BACKLOG-001 v0.1.1-draft
  Role: backlog synchronization
  Status: Normative for current operational backlog state

- INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.0-draft
  Role: MU graph placement
  Status: Normative for MU dependency ordering

- RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
  Role: SC-C boundary gate
  Status: Normative

- RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft
  Role: Base Topology architectural doctrine
  Status: Normative

- PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft
  Role: operational topology contract
  Status: Normative

- PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.6-draft
  Role: canonical type and identifier alignment
  Status: Normative

- PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.1.0-draft
  Role: topologyVersion seed contract
  Status: Normative seed subset


## 3.2 Operational execution artifacts


- docs/mir/mir-001/context.md
  Role: implementation context
  Status: Operational artifact
  Inline in MIR: forbidden

- docs/mir/mir-001/codex-prompt.md
  Role: executable implementation prompt
  Status: Operational artifact
  Inline in MIR: forbidden

- docs/mir/mir-001/implementation-report.md
  Role: post-implementation evidence
  Status: Evidence artifact

- docs/mir/mir-001/acceptance-map.md
  Role: AC mapping
  Status: Evidence/support artifact


## 3.3 Conditionally normative artifacts


- SDD-SOV-SC-ENDPOINT-MATERIALIZATION-001 version TBD
  Status: Conditionally normative
  Required only if accepted before implementation.

- TCK-SOV-SC-BASE-TOPOLOGY-001 version TBD
  Status: Conditionally normative
  Required only if promoted before this MIR is executed.

- ADR-SOV-SC-C-ID-STRATEGY-001 version TBD
  Status: Conditionally normative
  Required only if seed implementation cannot proceed with existing canonical ID rules.

- ADR-SOV-SC-C-STORAGE-TECH-001 version TBD
  Status: Conditionally normative
  Required only if seed implementation requires durable storage decision.


---

# 4. Readiness Assessment


Ownership clear: yes
Primary plane clear: yes — SC-C
Types defined: yes for seed scope
Flows defined: partial / low-flow seed
Persistence impact known: partial, acceptable for seed
sc-contract impact known: yes
topologyVersion impact known: yes, seed subset
SC-C / SC-B / SC-D boundary clear: yes
Acceptance criteria available: yes
Failure signals available: yes
ADR dependency resolved: not required for seed
SDD dependency resolved: not required for seed
TCK dependency available: partial / not required for L4
Execution artifacts available: yes


## 4.1 Readiness decision


Current MIR status: Accepted
Implementation authorization: Authorized under referenced execution package
Target validation level: L4


## 4.2 Acceptance scope

This MIR authorizes only the implementation attempt described by:


docs/mir/mir-001/context.md
docs/mir/mir-001/codex-prompt.md


Any material deviation from those files MUST be reported in:


docs/mir/mir-001/implementation-report.md


---

# 5. Implementation Scope

This MIR allows implementation of a **minimal SC-C Base Topology seed**.

It does not authorize full topology management.

It authorizes only the structures and behaviors required to prove the first Base Topology kernel.

## 5.1 Domain model

Implementation MAY introduce, adapt or align domain types equivalent to:


HabitatBaseTopology
RoomNode
ZoneNode
DeviceNode
EndpointNode
CapabilityNode
ProviderEndpointRef
TopologyMetadata
TopologyVersion
BaseTopologySnapshot


Names may follow existing repository conventions, but the semantic distinctions MUST be preserved.

The implementation MUST NOT collapse:


DeviceNode into EndpointNode
EndpointNode into CapabilityNode
ProviderEndpointRef into EndpointNode
BaseTopologySnapshot into Effective View


## 5.2 Canonical identity boundary

Implementation MUST distinguish:


deviceId
endpointId
capabilityId
providerId
providerDeviceId
providerEndpointId


Minimum semantics:


deviceId:
  canonical SC-C identity for DeviceNode.

endpointId:
  canonical SC-C identity for EndpointNode.

capabilityId:
  canonical SC-C identity or stable key for CapabilityNode.

providerId:
  provider/protocol/vendor/integration source identifier.

providerDeviceId:
  provider-native device identifier.

providerEndpointId:
  provider-native endpoint/resource/channel identifier.


`ProviderEndpointRef` MUST be stored as metadata or binding reference.

`ProviderEndpointRef` MUST NOT become canonical `endpointId`.

## 5.3 Base Topology hierarchy

The seed implementation MUST support at least:


HabitatBaseTopology
  -> RoomNode
  -> ZoneNode
  -> DeviceNode
  -> EndpointNode
  -> CapabilityNode


The implementation MAY allow optional hierarchy shortcuts if the repository already permits them, but the seed tests MUST cover the full canonical chain.

## 5.4 Endpoint addressability

Implementation MUST support all of the following:


one DeviceNode with one EndpointNode
one DeviceNode with multiple EndpointNodes
one EndpointNode with one CapabilityNode
one EndpointNode with multiple CapabilityNodes


The multi-endpoint case is mandatory.

Reason:


The MU is invalid if EndpointNode is merely decorative.


## 5.5 Provider binding metadata

Implementation MUST support provider-binding metadata sufficient to express:


providerId
providerDeviceId
providerEndpointId
providerCapabilityKey, if applicable
rawProviderKind, if needed as metadata


Provider metadata MUST NOT become public canonical topology semantics.

Provider metadata MUST NOT force SC-C to mirror provider-native topology.

## 5.6 Base Topology snapshot

Implementation MUST support creation and retrieval of a `BaseTopologySnapshot` or equivalent aggregate.

A valid seed snapshot MUST include:


topologyVersion
topology metadata
at least one habitat/room/zone chain
at least one device
at least one endpoint
at least one capability
provider refs where applicable


The snapshot MUST NOT require:


userId
sessionId
threadId
speechSegmentId
role
visibility rule
authority context
policy context
conversation context
surface context


## 5.7 topologyVersion seed subset

This MIR uses the seed subset of:


PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.1.0-draft


Mandatory subset for MU-001:


- topologyVersion is SC-C-owned;
- topologyVersion is associated with Base Topology;
- topologyVersion is scoped at least to Habitat;
- topologyVersion advances on accepted structural topology mutation;
- topologyVersion does not derive from Projection, Session, Identity, Authority, Policy or provider-native version.


This MIR does not require the final topologyVersion design.

Allowed seed implementations:


monotonic integer
monotonic long
opaque version object with monotonic ordering
database sequence-backed value
in-memory monotonic value for seed validation


Disallowed seed implementations:


session-derived topologyVersion
user-derived topologyVersion
Projection-derived topologyVersion
provider-native version as canonical topologyVersion
wall-clock-only topologyVersion without structural mutation semantics


## 5.8 Domain event object

Implementation MAY define an internal domain event object equivalent to:


TopologyChanged


But this MIR does not require SC-B publication.

If implemented:


TopologyChanged MUST be emitted/created by SC-C domain logic.
TopologyChanged MUST refer to Base Topology mutation.
TopologyChanged MUST NOT be emitted by SC-D.
TopologyChanged MUST NOT include Projection payload.


## 5.9 Repository / persistence

This MIR requires repository behavior.

Acceptable seed modes:


in-memory repository
ephemeral repository
H2-backed repository
JPA-backed repository
existing repository convention in sovereign-connect


A purely transient object construction without repository retrieval is insufficient.

Minimum repository capability:


create Base Topology seed
retrieve Base Topology snapshot
mutate Base Topology structure
observe topologyVersion advancement


Durable production-grade persistence is not required by this MIR.

If durable DB persistence is deferred, the implementation report MUST state:


Persistence mode: in-memory or ephemeral seed
Durability: not implemented
Reason: seed limitation
Required follow-up MU or SDD


## 5.10 Tests

Implementation MUST include tests for:


Base Topology creation
Base Topology retrieval
DeviceNode / EndpointNode distinction
multi-endpoint DeviceNode
CapabilityNode attachment to EndpointNode
ProviderEndpointRef non-equivalence to endpointId
topologyVersion initialization
topologyVersion advancement on structural mutation
absence of user/session/projection dependency


---

# 6. Negative Scope

This MIR explicitly excludes:


Effective View
Projection Layer
VisibilityRule
Authority
Identity
Session
Policy
Hub
Surface
NLU
conversation metadata
MCP exposure
real SC-D adapters
adapter hot onboarding
provider discovery
vendor-specific payload parsing
SC-B transport
NATS
JetStream
Vert.x
gRPC
MQTT
WebSocket binding
production-grade persistence hardening
distributed topology synchronization
real device execution
command dispatch
command result verification
adapter admission
adapter quarantine behavior
TemporalActs implementation
snapshot query API beyond seed repository retrieval
full topology materialization pipeline


This MIR also excludes:


permission filtering
guest-specific topology
owner-specific topology
role-based topology
policy-derived topology
session-derived topology
surface-specific topology


Any implementation that adds these concerns MUST mark them as deviations.

---

# 7. Required Invariants

## 7.1 SC-C invariants


SC-C owns canonical materialization.
SC-C owns Base Topology.
SC-C owns topologyVersion.
SC-C emits or creates TopologyChanged when domain event is used.
SC-C owns canonical topology memory for this seed.


## 7.2 Base Topology invariants


Base Topology represents existence and structure.
Base Topology is not Effective View.
Base Topology is not Projection.
Base Topology does not encode user/session visibility.
Base Topology does not encode policy-derived operability.


## 7.3 Endpoint invariants


DeviceNode is the stable topological container.
EndpointNode is the addressable operational locus.
CapabilityNode is the canonical affordance.
ProviderEndpointRef is provider-specific metadata.
Endpoint awareness is a Base Topology invariant of SC-Core.
Endpoint awareness is not an adapter-only detail.


## 7.4 Boundary invariants


SC-B transports and correlates; it does not own topology.
SC-D emits facts; it does not emit final topology.
Projection lives outside SC.
Authority lives outside SC.
Session lives outside SC.
Identity lives outside SC.
Policy lives outside SC.


---

# 8. Minimal Acceptance Criteria

The implementation passes this MIR only if all mandatory acceptance criteria pass.

## 8.1 Domain criteria


AC-001
Given a Base Topology seed,
the system can represent:
HabitatBaseTopology -> RoomNode -> ZoneNode -> DeviceNode -> EndpointNode -> CapabilityNode.

AC-002
Given a DeviceNode with multiple EndpointNodes,
each EndpointNode has its own canonical endpointId.

AC-003
Given an EndpointNode with multiple CapabilityNodes,
each capability remains attached to the endpoint and not directly to the provider.

AC-004
Given a ProviderEndpointRef,
the system stores it as provider binding metadata,
not as canonical endpoint identity.

AC-005
Given no user/session/authority/projection context,
the system can create and retrieve Base Topology.

AC-006
Given provider-native identifiers,
the system does not expose them as canonical SC-C topology identity.


## 8.2 topologyVersion criteria


AC-007
Given an initial Base Topology snapshot,
the system assigns an initial topologyVersion.

AC-008
Given a structural topology mutation,
the system advances topologyVersion.

AC-009
Given a Projection-like concern,
the system does not include it in topologyVersion calculation.

AC-010
Given provider-native version metadata,
the system does not treat it as canonical topologyVersion.


## 8.3 Repository criteria


AC-011
Given a valid Base Topology seed,
the system can store it through the selected repository strategy.

AC-012
Given a stored Base Topology seed,
the system can retrieve a semantically equivalent BaseTopologySnapshot.

AC-013
Given a structural mutation after retrieval,
the system preserves canonical identity boundaries and advances topologyVersion.


## 8.4 Boundary criteria


AC-014
No SC-D adapter assigns final canonical deviceId.

AC-015
No SC-D adapter assigns final canonical endpointId.

AC-016
No SC-B component decides topology semantics.

AC-017
No VisibilityRule, Authority, Session, Identity or Policy field is required to persist Base Topology.

AC-018
No Effective View or Projection type is required to create Base Topology.


## 8.5 Test criteria


AC-019
Unit tests exist for DeviceNode / EndpointNode separation.

AC-020
Unit tests exist for multi-endpoint device topology.

AC-021
Unit tests exist for EndpointNode / CapabilityNode relation.

AC-022
Unit tests exist for ProviderEndpointRef non-equivalence to endpointId.

AC-023
Unit or integration tests exist for topologyVersion seed behavior.

AC-024
Unit or integration tests prove Base Topology can be created without user/session context.

AC-025
Build/test command succeeds in the target repository.


---

# 9. Failure Signals

Implementation MUST stop and report upstream if any of the following occurs.


FS-001
Implementation requires adding VisibilityRule to Base Topology.

FS-002
Implementation requires userId, sessionId, threadId or speechSegmentId to create Base Topology.

FS-003
Implementation requires authority or policy context to create Base Topology.

FS-004
Implementation cannot represent multiple EndpointNodes under one DeviceNode.

FS-005
Implementation treats providerEndpointId as canonical endpointId.

FS-006
Implementation treats providerDeviceId as canonical deviceId.

FS-007
Implementation requires SC-B to decide topology semantics.

FS-008
Implementation requires SC-D to emit final TopologyChanged.

FS-009
Implementation requires SC-D to assign final canonical endpointId.

FS-010
Implementation cannot define topologyVersion without Projection.

FS-011
Implementation treats provider-native version as canonical topologyVersion.

FS-012
Implementation requires Java records to be treated as the only possible wire contract.

FS-013
Implementation cannot write meaningful tests without inventing missing semantics.

FS-014
Implementation discovers contradiction between Base Topology and EndpointNode documents.

FS-015
Implementation discovers that CapabilityNode cannot be attached to EndpointNode without collapsing into provider-native capability models.

FS-016
Implementation requires TemporalActs, adapter lifecycle, command dispatch or SC-B transport to complete the seed.


---

# 10. Upstream Patch Protocol

The implementation report MUST classify every issue as one of:


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


If Base Topology ownership is unclear:
  patch RFC-SOV-SC-BASE-TOPOLOGY-001.

If DeviceNode / EndpointNode / CapabilityNode morphology is unclear:
  patch PDR-SOV-SC-ENDPOINT-NODE-001.

If canonical identifiers are unclear:
  patch PDR-SOV-SC-CANONICAL-CONTRACT-001.

If topologyVersion behavior is unclear:
  patch PDR-SOV-SC-TOPOLOGY-VERSION-001.

If SC-C boundary is unclear:
  patch RFC-SOV-SC-C-CORE-BOUNDARY-001.

If repository behavior is unclear:
  open or patch PDR-SOV-SC-C-PERSISTENCE-MEMORY-001.

If durable storage decision becomes necessary:
  open ADR-SOV-SC-C-STORAGE-TECH-001.

If acceptance cannot be tested:
  open or patch TCK-SOV-SC-BASE-TOPOLOGY-001.

If implementation requires Projection:
  patch RFC-SOV-SC-BASE-TOPOLOGY-001
  and review Projection boundary documents.

If provider refs become canonical identity:
  patch PDR-SOV-SC-ENDPOINT-NODE-001
  and PDR-SOV-SC-CANONICAL-CONTRACT-001.


---

# 11. Codex Prompt Reference

This MIR does not inline the executable implementation prompt or the implementation context.

Canonical execution package:


Context:
  docs/mir/mir-001/context.md

Codex Prompt:
  docs/mir/mir-001/codex-prompt.md

Implementation Report:
  docs/mir/mir-001/implementation-report.md

Acceptance Map:
  docs/mir/mir-001/acceptance-map.md


Rules:


- context.md MUST be read before codex-prompt.md is executed.
- codex-prompt.md MUST reference context.md.
- implementation-report.md MUST reference this MIR version.
- acceptance-map.md MUST preserve AC-001 through AC-025 numbering.
- context.md and codex-prompt.md MAY be refined without advancing this MIR version.
- Any refinement that changes scope, acceptance criteria, failure signals or required invariants MUST advance this MIR or produce a patch.


---

# 12. Suggested Branch


feat/sc-c-mir-base-topology-001


---

# 13. Suggested Commit

Preferred:


feat(sc-c): materialize base topology seed


If persistence is in-memory only:


feat(sc-c): add base topology domain seed


If repository alignment dominates:


feat(sc-c): introduce base topology repository seed


For post-implementation evidence normalization:


chore(mir-001): normalize materialization package


---

# 14. Post-Implementation Report Requirements

The implementation project MUST return:


1. Summary
2. Files changed
3. Domain types added or modified
4. Repository / persistence strategy used
5. Tests added
6. Acceptance criteria result table
7. Required invariants preserved
8. Deviations from MIR
9. Failure signals encountered
10. Corpus issues discovered
11. Recommended upstream patches
12. Recommended next MU


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


SC-C owns Base Topology: preserved/broken/unclear
SC-C owns topologyVersion: preserved/broken/unclear
EndpointNode is first-class: preserved/broken/unclear
ProviderEndpointRef is metadata: preserved/broken/unclear
Base Topology excludes Projection: preserved/broken/unclear
Base Topology excludes Session: preserved/broken/unclear
Base Topology excludes Authority: preserved/broken/unclear
Base Topology excludes Policy: preserved/broken/unclear
SC-B does not own topology: preserved/broken/not applicable
SC-D does not assign canonical endpointId: preserved/broken/not applicable


---

# 15. Non-blocking Open Questions

These questions do not block Accepted status.


OQ-001
Should TopologyChanged be implemented as a pure domain event now,
or deferred until SC-B envelope materialization?

Default for this MIR:
  Optional pure domain object only. No SC-B publication.

OQ-002
Should TCK-SOV-SC-BASE-TOPOLOGY-001 be opened before implementation?

Default for this MIR:
  No. Local tests are sufficient for L4 seed validation.

OQ-003
Should the first repository be in-memory, H2 or JPA?

Default for this MIR:
  Use the project’s existing convention. If absent, use in-memory and report limitation.

OQ-004
Should ADR-SOV-SC-C-ID-STRATEGY-001 precede this implementation?

Default for this MIR:
  No, unless the repository lacks any viable canonical ID strategy and implementation would otherwise invent one.


---

# 16. Versioning and Acceptance Level

Target level for this MIR:


L4


Meaning:


L4 = implementation attempted and local tests pass.


This MIR does not require L5 TCK validation yet.

Rationale:


This is a kernel seed MU. Local domain and repository tests are sufficient for first materialization feedback.


Future promotion to L5 may require:


TCK-SOV-SC-BASE-TOPOLOGY-001


---

# 17. Accepted Closure

Accepted blockers resolved:


B-001
RFC-SOV-SC-BASE-TOPOLOGY-001 version pinned:
  v0.2.1-draft

B-002
PDR-SOV-SC-ENDPOINT-NODE-001 version pinned:
  v0.2.1-draft

B-003
PDR-SOV-SC-CANONICAL-CONTRACT-001 version pinned:
  v0.2.6-draft

B-004
PDR-SOV-SC-TOPOLOGY-VERSION-001 pinned as seed dependency:
  v0.1.0-draft

B-005
RFC-SOV-SC-C-CORE-BOUNDARY-001 promoted:
  v0.3.0-candidate

B-006
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 aligned:
  v0.2.0-draft

B-007
MIR execution artifacts externalized:
  context.md
  codex-prompt.md
  implementation-report.md
  acceptance-map.md


Accepted status:


MIR status:
  Accepted

Implementation authorization:
  Authorized under docs/mir/mir-001/codex-prompt.md

Target validation level:
  L4

Post-implementation evidence:
  required in docs/mir/mir-001/implementation-report.md


---

# 18. Changelog


v1.0.0-accepted
- Promotes MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 from Candidate to Accepted.
- Adopts PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.2-draft filesystem convention.
- Removes inline Codex prompt content from the MIR.
- Removes inline implementation context content from the MIR.
- Replaces section 11 with Codex Prompt Reference.
- Adds canonical execution package paths:
  docs/mir/mir-001/context.md
  docs/mir/mir-001/codex-prompt.md
  docs/mir/mir-001/implementation-report.md
  docs/mir/mir-001/acceptance-map.md
- Clarifies that context.md and codex-prompt.md may be refined independently unless they change MIR scope, invariants, acceptance criteria or failure signals.
- Authorizes implementation only under the referenced execution package.
- Preserves target validation level L4.

v0.3.0-candidate
- Promotes MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 from Draft to Candidate.
- Pins RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate.
- Pins RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft.
- Pins PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft.
- Pins PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.6-draft.
- Pins PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.1.0-draft.
- Pins SYNC-SOV-SC-MU-BACKLOG-001 v0.1.1-draft.
- Pins INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.0-draft.
- Resolves prior Candidate blockers.
- Updates topologyVersion §5.7 to use the seed subset of PDR-SOV-SC-TOPOLOGY-VERSION-001.
- Clarifies repository/persistence seed behavior.
- Clarifies that Candidate status does not authorize implementation.
- Prepares Candidate Codex prompt for later Accepted execution.

v0.2.0-draft
- Reframes document under MU-first hierarchy.
- Declares MU-SOV-SC-C-BASE-TOPOLOGY-SEED-001 as primary unit.
- Updates dependencies to NT-SOV-SC-MATERIALIZATION-UNITS-001,
  PDR-SOV-SC-MU-MIR-GOVERNANCE-001 and INDEX-SOV-SC-MATERIALIZATION-UNITS-001.
- Keeps MIR as descent document rather than primary planning unit.
- Expands acceptance criteria from 17 to 25 checks.
- Adds explicit MU thesis, expected output and failure value.
- Strengthens ProviderEndpointRef boundary.
- Strengthens topologyVersion seed constraints.
- Adds repository/persistence minimum.
- Adds implementation report invariant table.
- Adds blockers for promotion to Candidate/Accepted.

v0.1.0-draft
- Defines first SC-C Base Topology materialization seed.
- Establishes DeviceNode / EndpointNode / CapabilityNode implementation scope.
- Defines ProviderEndpointRef identity boundary.
- Defines topologyVersion seed behavior.
- Excludes Projection, Authority, Session, SC-B and real adapters.
- Adds acceptance criteria, failure signals and draft Codex prompt.


---

# Dictamen de cierre


MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001
Version: v1.0.0-accepted
Status: Accepted
Target MU: MU-SOV-SC-C-BASE-TOPOLOGY-SEED-001
Target validation level: L4
Open MIR blockers: none known


Este MIR queda listo para el paquete:


docs/mir/mir-001/
  MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001-v1.0.0-accepted.md
  context.md
  codex-prompt.md
  implementation-report.md
  acceptance-map.md



