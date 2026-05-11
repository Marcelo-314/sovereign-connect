# MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001 — Acceptance Map

```text
Document ID: MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001-ACCEPTANCE-MAP
MIR: MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001
MIR Version: v1.0.0-accepted
Materialization Unit: MU-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001
Operational Slot: MU-007
Status: Evidence Support Artifact
Validation Target: L4
Date: 2026-05-10
```

---

## 0. Purpose

This file preserves AC-001 through AC-025 for:

```text
MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001 v1.0.0-accepted
```

Use this file when producing `implementation-report.md`.

Do not invent new AC numbering.

Delegation to `BaseTopologyService`, deterministic IDs, the emitted-events delta pattern, capability helper usage, and endpoint-level health are mandatory rules and stop conditions, not additional AC numbers.

---

## 1. Acceptance Criteria Map

| AC | Criterion | Required Evidence | Result |
|---|---|---|---|
| AC-001 | A topology materialization service or equivalent boundary exists. | Service/interface/class in materialization package. | Pending |
| AC-002 | DeviceDiscoveryFact can materialize a canonical DeviceNode. | Test creating DeviceNode from fact. | Pending |
| AC-003 | EndpointDiscoveryFact can materialize a canonical EndpointNode. | Test creating EndpointNode from fact. | Pending |
| AC-004 | CapabilityDiscoveryFact can materialize canonical capability under EndpointNode. | Test attaching capability through addCapabilityWithResult or equivalent helper. | Pending |
| AC-005 | Provider refs are stored as metadata. | Snapshot/topology assertions. | Pending |
| AC-006 | providerDeviceId is not used as canonical deviceId. | Assert canonical deviceId differs from providerDeviceId. | Pending |
| AC-007 | providerEndpointId is not used as canonical endpointId. | Assert canonical endpointId differs from providerEndpointId. | Pending |
| AC-008 | Accepted structural materialization advances topologyVersion. | Before/after version assertion. | Pending |
| AC-009 | Duplicate fact does not advance topologyVersion. | Repeat fact assertion. | Pending |
| AC-010 | Rejected invalid fact does not advance topologyVersion. | Invalid fact assertion. | Pending |
| AC-011 | State-only fact does not advance topologyVersion. | State fact assertion. | Pending |
| AC-012 | Health-only fact does not advance topologyVersion. | Endpoint-level health fact assertion. | Pending |
| AC-013 | Accepted structural materialization emits TopologyChanged using existing transitional shape. | TopologyChanged fromVersion/toVersion/changeKinds assertion. | Pending |
| AC-014 | Duplicate/noop/rejected fact does not emit TopologyChanged. | emittedChanges empty assertion. | Pending |
| AC-015 | Materialized topology persists across repository/service recreation. | H2 recreation test. | Pending |
| AC-016 | Core Snapshot Query observes materialized topology after recovery. | CoreSnapshotQueryService recovery assertion. | Pending |
| AC-017 | Repeated same fact is idempotent. | Same fact repeated; no duplicate node/version advance. | Pending |
| AC-018 | Conflicting provider binding is rejected or quarantined, not silently accepted. | Conflict test or documented rejection behavior. | Pending |
| AC-019 | Facts from non-admitted adapter do not mutate topology. | Admission predicate test. | Pending |
| AC-020 | Materialization requires no SC-B implementation. | No SC-B dependency / test setup. | Pending |
| AC-021 | Materialization requires no SC-D implementation beyond local fact input shapes. | No adapter implementation. | Pending |
| AC-022 | Materialization requires no Projection / Effective View. | No Projection dependency. | Pending |
| AC-023 | Materialization requires no Session / Identity / Authority / Policy. | No such dependency. | Pending |
| AC-024 | Existing MU-001, MU-002, MU-004, MU-006 and MU-010 tests continue passing. | Full test suite result. | Pending |
| AC-025 | Build/test command succeeds. | `mvn test` success; at least 24 tests passing. | Pending |

---

## 2. Mandatory Rule Evidence

The implementation report must also include evidence for these mandatory rules:

```text
MR-001
Structural mutation path delegates to BaseTopologyService or additive BaseTopologyService methods.

MR-002
TopologyMaterializationService does not directly mutate HabitatBaseTopology and persist it through repository save as an independent mutation engine.

MR-003
emittedEvents() delta pattern is used for MaterializationDecision.emittedChanges.

MR-004
Duplicate detection happens before service calls that throw on duplicate.

MR-005
Habitat pre-condition is checked before structural mutation.

MR-006
State/health facts use durable repository paths, not BaseTopologyService in-process state.

MR-007
CapabilityDiscoveryFact uses addCapabilityWithResult or equivalent helper, not addEndpointWithResult on an existing endpoint.

MR-008
Seed canonical IDs are deterministic and not equal to provider-native IDs.

MR-009
HealthFact is endpoint-level only for seed unless durable device health already exists.
```

---

## 3. Expected Result Table

Implementation report must include:

| AC | Result | Evidence |
|---|---|---|
| AC-001 | PASS/FAIL | ... |
| AC-002 | PASS/FAIL | ... |
| AC-003 | PASS/FAIL | ... |
| AC-004 | PASS/FAIL | ... |
| AC-005 | PASS/FAIL | ... |
| AC-006 | PASS/FAIL | ... |
| AC-007 | PASS/FAIL | ... |
| AC-008 | PASS/FAIL | ... |
| AC-009 | PASS/FAIL | ... |
| AC-010 | PASS/FAIL | ... |
| AC-011 | PASS/FAIL | ... |
| AC-012 | PASS/FAIL | ... |
| AC-013 | PASS/FAIL | ... |
| AC-014 | PASS/FAIL | ... |
| AC-015 | PASS/FAIL | ... |
| AC-016 | PASS/FAIL | ... |
| AC-017 | PASS/FAIL | ... |
| AC-018 | PASS/FAIL | ... |
| AC-019 | PASS/FAIL | ... |
| AC-020 | PASS/FAIL | ... |
| AC-021 | PASS/FAIL | ... |
| AC-022 | PASS/FAIL | ... |
| AC-023 | PASS/FAIL | ... |
| AC-024 | PASS/FAIL | ... |
| AC-025 | PASS/FAIL | ... |

---

## 4. Validation Level

```text
L4 — implementation attempted and local tests pass.
```

This acceptance map does not require L5 TCK validation.
