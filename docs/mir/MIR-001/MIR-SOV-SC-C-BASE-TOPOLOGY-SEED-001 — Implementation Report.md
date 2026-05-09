

# MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 — Implementation Report

MIR: MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001
MIR Version: v1.0.0-accepted
Materialization Unit: MU-SOV-SC-C-BASE-TOPOLOGY-SEED-001
Repository: sovereign-connect
Validation Target: L4
Report Status: Normalized
Date: 2026-05-09

---

## 1. Summary

This increment implements the first SC-C Base Topology seed for Sovereign Connect.

It adds a minimal Java 21 / Spring Boot 3 / Maven project with hexagonal topology domain boundaries, an in-memory repository adapter, a domain service, typed habitat-scoped topology versioning, internal topology change signaling, and tests for the mandatory MIR behaviors.

Result:

L4 local validation passed.

---

## 2. Files Changed

The increment added or modified:

pom.xml
.gitignore

src/main/java/.../SovereignConnectApplication.java

src/main/java/.../core/topology/model/*
src/main/java/.../core/topology/port/BaseTopologyRepository.java
src/main/java/.../core/topology/service/BaseTopologyService.java
src/main/java/.../core/topology/event/TopologyChanged.java
src/main/java/.../core/topology/event/TopologyChangeKind.java

src/main/java/.../adapter/persistence/InMemoryBaseTopologyRepository.java

src/test/java/.../core/topology/*

Exact package paths may follow the repository implementation.

3. Domain Types Added or Materialized

The increment adds or materializes the following domain concepts:

HabitatBaseTopology
RoomNode
ZoneNode
DeviceNode
EndpointNode
CapabilityNode
TopologyNode
TopologyMetadata
TopologyVersion
TopologyVersionScope
TopologyVersionScopeType
ProviderDeviceRef
ProviderEndpointRef
DeviceHealth / EndpointHealth or equivalent seed health structures
DeviceTraits / EndpointTraits / CapabilityTraits or equivalent seed traits
TopologyChanged
TopologyChangeKind
4. Repository / Persistence Strategy

Persistence mode:

In-memory repository adapter

Repository port:

BaseTopologyRepository

Adapter:

InMemoryBaseTopologyRepository

Repository capabilities implemented:

save(HabitatBaseTopology)
findByHabitatId(String)
findCurrentVersion(String habitatId)

Durability:

Not implemented.

Reason:

This is a seed implementation. Durable production-grade persistence belongs to a later Persistence/Memory MU, ADR or SDD.
5. Domain Behavior Added

The implementation supports:

- initial HabitatBaseTopology creation;
- initial topologyVersion assignment;
- habitat-scoped TopologyVersion;
- Base Topology storage/retrieval by habitatId;
- current topologyVersion retrieval through repository port;
- structural mutation through endpoint addition;
- topologyVersion advancement after accepted structural mutation;
- parent room, zone and device endpoint reference updates during endpoint addition;
- optional internal TopologyChanged domain object;
- unique canonical room, zone, device and endpoint identifiers;
- unique capability identifiers within their containing node;
- provider endpoint IDs rejected as canonical endpoint IDs;
- provider device IDs rejected as canonical device IDs;
- provider-native IDs preserved as metadata only.
6. Tests Added
createsAndRetrievesFullBaseTopologyHierarchy

Verifies:

HabitatBaseTopology -> RoomNode -> ZoneNode -> DeviceNode -> EndpointNode -> CapabilityNode

and repository retrieval.

supportsOneDeviceWithMultipleFirstClassEndpoints

Verifies one device can contain multiple endpoint IDs and multiple endpoint nodes.

supportsOneEndpointWithMultipleCapabilities

Verifies one endpoint can contain multiple capability nodes.

storesProviderEndpointRefAsMetadataAndNeverAsCanonicalEndpointId

Verifies ProviderEndpointRef.providerEndpointId is metadata and cannot become endpointId.

keepsProviderNativeIdsOutOfCanonicalTopologyIdentity

Verifies provider-native IDs are not canonical topology IDs.

topologyContainsNoUserSessionAuthorityOrProjectionFields

Verifies topology records do not expose user, session, authority, projection or policy fields.

topologyVersionExistsOnInitialSnapshotAndIsHabitatScoped

Verifies initial topology version exists and is Habitat-scoped.

topologyVersionAdvancesAfterAcceptedStructuralMutation

Verifies version advancement after endpoint addition.

7. Validation Command
mvn test

Reported result:

Tests run: 8
Failures: 0
Errors: 0
Skipped: 0
Build: success
8. Acceptance Criteria Result Table
AC	Result	Evidence
AC-001	PASS	createsAndRetrievesFullBaseTopologyHierarchy
AC-002	PASS	supportsOneDeviceWithMultipleFirstClassEndpoints
AC-003	PASS	supportsOneEndpointWithMultipleCapabilities
AC-004	PASS	storesProviderEndpointRefAsMetadataAndNeverAsCanonicalEndpointId
AC-005	PASS	topologyContainsNoUserSessionAuthorityOrProjectionFields; creation/retrieval tests
AC-006	PASS	keepsProviderNativeIdsOutOfCanonicalTopologyIdentity
AC-007	PASS	topologyVersionExistsOnInitialSnapshotAndIsHabitatScoped
AC-008	PASS	topologyVersionAdvancesAfterAcceptedStructuralMutation
AC-009	PASS	No Projection model participates in versioning
AC-010	PASS	Provider-native versioning is not used as topologyVersion
AC-011	PASS	BaseTopologyRepository.save
AC-012	PASS	BaseTopologyRepository.findByHabitatId
AC-013	PASS	endpoint addition mutation + version advancement
AC-014	PASS / N.A.	No SC-D adapter implemented
AC-015	PASS / N.A.	No SC-D adapter implemented
AC-016	PASS / N.A.	No SC-B component implemented
AC-017	PASS	no VisibilityRule / Authority / Session / Identity / Policy required
AC-018	PASS	no Effective View / Projection required
AC-019	PASS	DeviceNode / EndpointNode tests
AC-020	PASS	multi-endpoint device test
AC-021	PASS	endpoint/capability relation test
AC-022	PASS	ProviderEndpointRef metadata test
AC-023	PASS	topologyVersion tests
AC-024	PASS	no user/session/projection context test
AC-025	PASS	mvn test success

Summary:

PASS: 25
FAIL: 0
BLOCKED: 0
9. Invariants Preserved
Invariant	Status
SC-C owns Base Topology	preserved
SC-C owns topologyVersion	preserved
EndpointNode is first-class	preserved
ProviderEndpointRef is metadata	preserved
Base Topology excludes Projection	preserved
Base Topology excludes Session	preserved
Base Topology excludes Authority	preserved
Base Topology excludes Policy	preserved
Provider-native IDs are not canonical topology identity	preserved
SC-B does not own topology	not applicable / preserved by absence
SC-D does not assign canonical endpointId	not applicable / preserved by absence
10. Deviations from MIR

None.

11. Failure Signals Encountered

None.

No negative-scope component was required to satisfy the tests.

Specifically, the implementation did not require:

Projection
Effective View
VisibilityRule
Authority
Identity
Session
Policy
Hub
Surfaces
NLU
MCP
SC-B transport
real SC-D adapters
provider discovery
vendor payload parsing
command dispatch
device execution
verification
adapter lifecycle
TemporalActs
full snapshot query API
production-grade durable persistence
distributed topology synchronization
12. Assumptions
A-001
The repository did not contain an existing Maven/Spring Boot project, so the increment scaffolded the minimal project structure.

A-002
The implementation was initially executed before the final docs/mir/mir-001/ package layout was normalized.

A-003
The implementation validated against the available MIR context and prompt, then this report was normalized against MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted and acceptance-map.md.

A-004
In-memory persistence is sufficient for L4 seed validation.
13. Corpus Issues Discovered

Original issues discovered during execution:

C-001
The official AC-001 through AC-025 definitions were not available inside the repository at execution time.

C-002
The MIR context path differed from the final normalized package path.

Resolution:

C-001 resolved by:
  docs/mir/mir-001/acceptance-map.md

C-002 resolved by:
  docs/mir/mir-001/ package convention

Remaining corpus issues:

None blocking.
14. Recommended Upstream Patches
P-001
Patch INDEX-SOV-SC-MATERIALIZATION-UNITS-001 to mark:
  MU-SOV-SC-C-BASE-TOPOLOGY-SEED-001
  Status: Validated
  Acceptance level: L4
  Evidence package: docs/mir/mir-001/

P-002
Patch SYNC-SOV-SC-MU-BACKLOG-001 to record MU-001 closure and unlock MU-002.

P-003
Open or promote PDR-SOV-SC-TOPOLOGY-VERSION-001 v0.2.0-draft.
15. Recommended Next MU

Recommended next MU:

MU-SOV-SC-C-TOPOLOGY-VERSION-SEED-001

Rationale:

The implementation already introduced a habitat-scoped topologyVersion seed. Before introducing durable persistence, the topologyVersion semantics should be hardened into its own MU so later persistence does not prematurely freeze an underspecified versioning model.

Alternative later MU:

MU-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001

But this should follow topologyVersion hardening.

16. Final Decision
MIR: MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted
MU: MU-SOV-SC-C-BASE-TOPOLOGY-SEED-001
Validation level: L4
Result: PASS
Status recommendation: Validated