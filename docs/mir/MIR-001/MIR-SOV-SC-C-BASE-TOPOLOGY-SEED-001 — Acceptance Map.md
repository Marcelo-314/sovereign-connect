

# MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 — Acceptance Map

Document ID: MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001-ACCEPTANCE-MAP
MIR: MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001
MIR Version: v1.0.0-accepted
Materialization Unit: MU-SOV-SC-C-BASE-TOPOLOGY-SEED-001
Status: Evidence Support Artifact
Validation Target: L4
Date: 2026-05-09

---

## 0. Purpose

This file preserves the official AC-001 through AC-025 mapping for:

MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 v1.0.0-accepted

It is part of the canonical execution package:

docs/mir/mir-001/
  MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001-v1.0.0-accepted.md
  context.md
  codex-prompt.md
  implementation-report.md
  acceptance-map.md

This file exists because context.md and codex-prompt.md are operational artifacts, while the MIR governs scope, invariants, acceptance criteria and failure signals.

---

## 1. Acceptance Criteria Map

| AC | Criterion | Evidence | Result |
|---|---|---|---|
| AC-001 | Given a Base Topology seed, the system can represent `HabitatBaseTopology -> RoomNode -> ZoneNode -> DeviceNode -> EndpointNode -> CapabilityNode`. | Test: `createsAndRetrievesFullBaseTopologyHierarchy` | PASS |
| AC-002 | Given a `DeviceNode` with multiple `EndpointNodes`, each `EndpointNode` has its own canonical `endpointId`. | Test: `supportsOneDeviceWithMultipleFirstClassEndpoints` | PASS |
| AC-003 | Given an `EndpointNode` with multiple `CapabilityNodes`, each capability remains attached to the endpoint and not directly to the provider. | Test: `supportsOneEndpointWithMultipleCapabilities` | PASS |
| AC-004 | Given a `ProviderEndpointRef`, the system stores it as provider binding metadata, not as canonical endpoint identity. | Test: `storesProviderEndpointRefAsMetadataAndNeverAsCanonicalEndpointId` | PASS |
| AC-005 | Given no user/session/authority/projection context, the system can create and retrieve Base Topology. | Test: `topologyContainsNoUserSessionAuthorityOrProjectionFields`; domain construction/retrieval tests | PASS |
| AC-006 | Given provider-native identifiers, the system does not expose them as canonical SC-C topology identity. | Test: `keepsProviderNativeIdsOutOfCanonicalTopologyIdentity` | PASS |
| AC-007 | Given an initial Base Topology snapshot, the system assigns an initial `topologyVersion`. | Test: `topologyVersionExistsOnInitialSnapshotAndIsHabitatScoped` | PASS |
| AC-008 | Given a structural topology mutation, the system advances `topologyVersion`. | Test: `topologyVersionAdvancesAfterAcceptedStructuralMutation` | PASS |
| AC-009 | Given a Projection-like concern, the system does not include it in `topologyVersion` calculation. | No Projection model exists in implementation; topology version is habitat-scoped and service-controlled. | PASS |
| AC-010 | Given provider-native version metadata, the system does not treat it as canonical `topologyVersion`. | Provider refs are metadata only; `TopologyVersion` is typed and habitat-scoped. | PASS |
| AC-011 | Given a valid Base Topology seed, the system can store it through the selected repository strategy. | `BaseTopologyRepository.save`; `InMemoryBaseTopologyRepository` | PASS |
| AC-012 | Given a stored Base Topology seed, the system can retrieve a semantically equivalent `BaseTopologySnapshot` or equivalent aggregate. | `BaseTopologyRepository.findByHabitatId`; retrieval tests | PASS |
| AC-013 | Given a structural mutation after retrieval, the system preserves canonical identity boundaries and advances `topologyVersion`. | Endpoint addition mutation through `BaseTopologyService`; version advancement test | PASS |
| AC-014 | No SC-D adapter assigns final canonical `deviceId`. | No SC-D adapter implemented; canonical IDs are created in SC-C topology model/service. | PASS / NOT APPLICABLE |
| AC-015 | No SC-D adapter assigns final canonical `endpointId`. | No SC-D adapter implemented; endpoint IDs are canonical SC-C fields. | PASS / NOT APPLICABLE |
| AC-016 | No SC-B component decides topology semantics. | No SC-B transport or bus component implemented. | PASS / NOT APPLICABLE |
| AC-017 | No `VisibilityRule`, Authority, Session, Identity or Policy field is required to persist Base Topology. | `topologyContainsNoUserSessionAuthorityOrProjectionFields`; model inspection | PASS |
| AC-018 | No Effective View or Projection type is required to create Base Topology. | No Effective View / Projection model exists in seed implementation. | PASS |
| AC-019 | Unit tests exist for `DeviceNode` / `EndpointNode` separation. | `supportsOneDeviceWithMultipleFirstClassEndpoints`; hierarchy tests | PASS |
| AC-020 | Unit tests exist for multi-endpoint device topology. | `supportsOneDeviceWithMultipleFirstClassEndpoints` | PASS |
| AC-021 | Unit tests exist for `EndpointNode` / `CapabilityNode` relation. | `supportsOneEndpointWithMultipleCapabilities` | PASS |
| AC-022 | Unit tests exist for `ProviderEndpointRef` non-equivalence to `endpointId`. | `storesProviderEndpointRefAsMetadataAndNeverAsCanonicalEndpointId` | PASS |
| AC-023 | Unit or integration tests exist for `topologyVersion` seed behavior. | `topologyVersionExistsOnInitialSnapshotAndIsHabitatScoped`; `topologyVersionAdvancesAfterAcceptedStructuralMutation` | PASS |
| AC-024 | Unit or integration tests prove Base Topology can be created without user/session context. | `topologyContainsNoUserSessionAuthorityOrProjectionFields` | PASS |
| AC-025 | Build/test command succeeds in the target repository. | `mvn test`: 8 tests, 0 failures, 0 errors, 0 skipped | PASS |

---

## 2. Summary

Result:

PASS: 25
FAIL: 0
BLOCKED: 0

Validation level:

L4 — implementation attempted and local tests pass.

---

## 3. Notes

AC-014, AC-015 and AC-016 are marked PASS / NOT APPLICABLE because SC-D and SC-B were intentionally excluded from the seed scope.

Their pass condition is satisfied negatively:

- no SC-D adapter exists in this increment;
- no SC-B transport component exists in this increment;
- therefore neither plane can own or mutate Base Topology semantics.

This is valid for MU-001 because the MIR explicitly excludes real SC-D adapters and SC-B transport.