# MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 - Implementation Report

## Summary

This increment implements the first SC-C Base Topology seed for Sovereign Connect.
It adds a minimal Java 21 / Spring Boot 3 / Maven project with hexagonal topology domain boundaries, an in-memory repository adapter, a domain service, typed habitat-scoped topology versioning, and tests for the mandatory MIR behaviors.

## Problem Solved

Before this increment:

- The repository had no Maven/Spring Boot source tree for the SC-C topology seed.
- The Base Topology domain model did not exist.
- `DeviceNode`, `EndpointNode`, and `CapabilityNode` were not represented as distinct canonical topology concepts.
- There was no `BaseTopologyRepository` port.
- There was no in-memory adapter for storing and retrieving topology snapshots.
- There was no domain service for creating the initial Base Topology seed.
- There was no topology version owned by SC-C and scoped to a Habitat.
- Provider-native device and endpoint identifiers had no enforced separation from canonical topology identity.
- The mandatory MIR tests were not present.

## Main Changes

- Added Maven project configuration in `pom.xml`.
- Added minimal Spring Boot application entry point.
- Added the SC-C Base Topology domain model under:
  - `com.sovereign.connect.core.topology.model`
- Added the topology aggregate:
  - `HabitatBaseTopology`
- Added first-class topology nodes:
  - `RoomNode`
  - `ZoneNode`
  - `DeviceNode`
  - `EndpointNode`
  - `CapabilityNode`
- Added typed topology versioning:
  - `TopologyVersion`
  - `TopologyVersionScope`
  - `TopologyVersionScopeType`
- Added provider metadata records:
  - `ProviderDeviceRef`
  - `ProviderEndpointRef`
- Added health, traits, metadata, and enum types required by the seed.
- Added sealed `TopologyNode` for canonical topology node identity.
- Added repository port:
  - `BaseTopologyRepository`
- Added in-memory persistence adapter:
  - `InMemoryBaseTopologyRepository`
- Added minimal domain service:
  - `BaseTopologyService`
- Added optional internal domain event object:
  - `TopologyChanged`
  - `TopologyChangeKind`
- Added tests covering the mandatory MIR scenarios.
- Updated `.gitignore` to exclude Maven `target/` output.

## Domain Behavior Added

- Creates an initial `HabitatBaseTopology` snapshot.
- Assigns initial topology version `1`.
- Scopes `TopologyVersion` to `TopologyVersionScopeType.HABITAT`.
- Stores and retrieves topologies by `habitatId`.
- Exposes current topology version through the repository port.
- Supports accepted structural mutation through endpoint addition.
- Advances topology version after accepted structural mutation.
- Updates parent room, zone, and device endpoint references during endpoint addition.
- Emits an internal `TopologyChanged` domain object without bus publication.
- Validates unique canonical room, zone, device, and endpoint identifiers.
- Validates unique capability identifiers within their containing node.
- Prevents provider endpoint IDs from being used as canonical endpoint IDs.
- Prevents provider device IDs from being used as canonical device IDs.
- Keeps provider-native IDs as metadata only.

## Impact

- SC-C now owns the Base Topology aggregate for this seed.
- SC-C now owns topology version assignment and advancement.
- Endpoint awareness is modeled inside SC-Core as a Base Topology invariant.
- `EndpointNode` is first-class and not an adapter detail.
- Provider-native identifiers are retained only as provider metadata.
- The implementation does not introduce projection, session, authority, policy, transport, provider discovery, command dispatch, or durable persistence.
- The implementation is intentionally in-memory and seed-scoped.

## Tests Added

- `createsAndRetrievesFullBaseTopologyHierarchy`
  - Verifies Habitat -> Room -> Zone -> Device -> Endpoint -> Capability structure and repository retrieval.
- `supportsOneDeviceWithMultipleFirstClassEndpoints`
  - Verifies one device can contain multiple endpoint IDs and multiple endpoint nodes.
- `supportsOneEndpointWithMultipleCapabilities`
  - Verifies one endpoint can contain multiple capability nodes.
- `storesProviderEndpointRefAsMetadataAndNeverAsCanonicalEndpointId`
  - Verifies `ProviderEndpointRef.providerEndpointId` is metadata and cannot become `endpointId`.
- `keepsProviderNativeIdsOutOfCanonicalTopologyIdentity`
  - Verifies provider-native IDs are not canonical topology IDs.
- `topologyContainsNoUserSessionAuthorityOrProjectionFields`
  - Verifies the topology records do not expose user, session, authority, projection, or policy fields.
- `topologyVersionExistsOnInitialSnapshotAndIsHabitatScoped`
  - Verifies initial topology version exists and is Habitat-scoped.
- `topologyVersionAdvancesAfterAcceptedStructuralMutation`
  - Verifies version advancement after endpoint addition.

## Validations

- `mvn test`

Result:

- Tests run: 8
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: success

## Invariants Preserved

- SC-C owns Base Topology: preserved.
- SC-C owns topologyVersion: preserved.
- EndpointNode is first-class: preserved.
- ProviderEndpointRef is metadata: preserved.
- Base Topology excludes Projection: preserved.
- Base Topology excludes Session: preserved.
- Provider-native IDs are not canonical topology identity: preserved.

## Deviations From Scope

None.

The increment does not implement:

- Effective View / Projection / VisibilityRule
- Authority / Identity / Session / Policy
- Hub / Surfaces / NLU / MCP
- SC-B transport
- Real SC-D adapters
- Provider discovery
- Vendor payload parsing
- Command dispatch
- Device execution
- Verification
- Adapter lifecycle
- TemporalActs
- Full snapshot query API
- Production-grade durable persistence
- Distributed topology synchronization

## Failure Signals Encountered

None.

No negative-scope component was required to satisfy the tests.

## Assumptions

- The repository did not contain an existing Maven/Spring Boot project, so this increment scaffolded the minimal project structure.
- The requested `docs/mir-001-context.md` was not present at the time of implementation; the available MIR context was used from the repository corpus.
- The official `MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v0.3.0-candidate` AC-001 through AC-025 mapping was not present in the repository, so acceptance was validated against the prompt and available MIR context.

## Corpus Issues

- The official AC-001 through AC-025 definitions were not available in the repository.
- The MIR context path differed from the prompt path during implementation.

## Recommended Next MU

Add the official MIR acceptance criteria package to the repository, then introduce a durable persistence adapter behind the existing `BaseTopologyRepository` port while keeping the domain model and service boundaries unchanged.
