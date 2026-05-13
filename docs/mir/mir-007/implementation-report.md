# MIR-007 Implementation Report

## 1. Summary

MIR-007 adds the SC-C topology materialization seed: local `TopologyFact` inputs are converted into typed materialization decisions, delegated structural mutations, durable state/health writes, and recovered Core Snapshot query results. The implementation preserves the existing `TopologyChanged` transitional shape, uses deterministic canonical IDs, rejects duplicates before service calls, and keeps SC-B, SC-D, Projection, Authority, Session, Identity and Policy outside the seed.

## 2. Files Changed

- `src/main/java/com/sovereign/connect/core/topology/service/BaseTopologyService.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/TopologyFact.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/DeviceDiscoveryFact.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/EndpointDiscoveryFact.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/CapabilityDiscoveryFact.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/DeviceStateFact.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/HealthFact.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/MaterializationDecisionKind.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/MaterializationDecision.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/TopologyMaterializationService.java`
- `src/main/java/com/sovereign/connect/core/topology/materialization/DefaultTopologyMaterializationService.java`
- `src/test/java/com/sovereign/connect/core/topology/TopologyMaterializationSeedTest.java`
- `docs/mir/mir-007/implementation-report.md`

## 3. BaseTopologyService Methods Added

- `addDeviceWithResult(String, DeviceNode)`
- `addCapabilityWithResult(String, String, CapabilityNode)`

Private helpers added:

- `appendDevice(RoomNode, String)`
- `appendDevice(ZoneNode, String)`

## 4. Materialization Types Added

- `TopologyFact`
- `DeviceDiscoveryFact`
- `EndpointDiscoveryFact`
- `CapabilityDiscoveryFact`
- `DeviceStateFact`
- `HealthFact`
- `MaterializationDecisionKind`
- `MaterializationDecision`
- `TopologyMaterializationService`
- `DefaultTopologyMaterializationService`

## 5. Materialization Service Behavior

`DefaultTopologyMaterializationService` handles:

- `DeviceDiscoveryFact` -> canonical `DeviceNode` via `BaseTopologyService.addDeviceWithResult`.
- `EndpointDiscoveryFact` -> canonical `EndpointNode` via `BaseTopologyService.addEndpointWithResult`.
- `CapabilityDiscoveryFact` -> canonical `CapabilityNode` via `BaseTopologyService.addCapabilityWithResult`.
- `DeviceStateFact` -> durable `H2BaseTopologyRepository.saveDeviceState`.
- `HealthFact` -> endpoint-level durable `H2BaseTopologyRepository.saveEndpointHealth`.

Structural branches use the `emittedEvents()` delta pattern. Duplicate and habitat pre-condition checks occur before service mutation calls.

## 6. Tests Added

- `TopologyMaterializationSeedTest#materializesTopologyFactsThroughBaseTopologyServiceAndDurableRepository` - validates device, endpoint, capability, duplicate rejection, invalid health, non-admitted adapter rejection, durable state/health, recovery, Core Snapshot query visibility and `validateTarget`.

## 7. AC-001 Through AC-025

| AC | Result | Evidence |
|---|---|---|
| AC-001 | PASS | `TopologyMaterializationService` and `DefaultTopologyMaterializationService` added. |
| AC-002 | PASS | `DeviceDiscoveryFact` materializes canonical `DeviceNode` in `TopologyMaterializationSeedTest`. |
| AC-003 | PASS | `EndpointDiscoveryFact` materializes canonical `EndpointNode` in test. |
| AC-004 | PASS | `CapabilityDiscoveryFact` attaches canonical capability through `addCapabilityWithResult`. |
| AC-005 | PASS | Recovered topology contains provider refs on device/endpoint as metadata. |
| AC-006 | PASS | Test asserts canonical `device.tuya.tuya-device-abc` differs from `tuya-device-abc`. |
| AC-007 | PASS | Test asserts canonical endpoint ID differs from provider endpoint ID `dp-1`. |
| AC-008 | PASS | Structural device/endpoint/capability decisions assert resulting version differs from previous version. |
| AC-009 | PASS | Duplicate device, endpoint and capability facts assert unchanged version. |
| AC-010 | PASS | HealthFact without endpoint ID returns `REJECT_INVALID_FACT` and version remains unchanged. |
| AC-011 | PASS | DeviceStateFact returns `ACCEPT_NON_STRUCTURAL_STATE` and version remains unchanged. |
| AC-012 | PASS | Endpoint HealthFact returns `ACCEPT_HEALTH_UPDATE` and version remains unchanged. |
| AC-013 | PASS | Structural decisions assert `TopologyChanged` has from/to versions and change kinds. |
| AC-014 | PASS | Duplicate, invalid and unauthorized decisions assert empty emitted changes. |
| AC-015 | PASS | H2 recreation test recovers materialized device, endpoint and capability. |
| AC-016 | PASS | `CoreSnapshotQueryService` observes materialized topology after recovery. |
| AC-017 | PASS | Repeated same facts return `REJECT_DUPLICATE`, no duplicate nodes and no version advance. |
| AC-018 | PASS | Conflicting provider binding with same provider/native device key and different metadata is rejected as duplicate, not silently accepted. |
| AC-019 | PASS | Non-admitted adapter returns `REJECT_UNAUTHORIZED_ADAPTER` and version remains unchanged. |
| AC-020 | PASS | No SC-B dependency or setup exists; facts are local inputs. |
| AC-021 | PASS | No real SC-D implementation is introduced; provider facts are local records. |
| AC-022 | PASS | No Projection or Effective View dependency exists. |
| AC-023 | PASS | No Session, Identity, Authority or Policy dependency exists. |
| AC-024 | PASS | Full suite includes existing MU-001, MU-002, MU-004, MU-006 and MU-010 tests and remains green. |
| AC-025 | PASS | `mvn test` succeeds with 24 tests, 0 failures, 0 errors, 0 skipped. |

## 8. topologyVersion Evidence Per Fact Type

- `DeviceDiscoveryFact`: accepted structural mutation advances version.
- `EndpointDiscoveryFact`: accepted structural mutation advances version.
- `CapabilityDiscoveryFact`: accepted structural mutation advances version.
- Duplicate device/endpoint/capability facts: version unchanged.
- `DeviceStateFact`: version unchanged.
- endpoint-level `HealthFact`: version unchanged.
- invalid `HealthFact` without endpoint ID: version unchanged.
- non-admitted adapter fact: version unchanged.

## 9. TopologyChanged Transitional Shape Evidence

The implementation keeps the existing `TopologyChanged` record with:

- `fromVersion` as `String`
- `toVersion` as `String`
- `changeKinds` as `Set<TopologyChangeKind>`
- `affectedDeviceIds`
- `affectedEndpointIds`

The test asserts:

- `DEVICE_ADDED`
- `ENDPOINT_ADDED`
- `CAPABILITY_ADDED`
- `fromVersion != toVersion`
- affected canonical IDs are included.

## 10. Provider ID vs Canonical ID Evidence

Deterministic canonical IDs are:

- `device.<providerId>.<providerDeviceId>`
- `endpoint.<providerId>.<providerDeviceId>.<providerEndpointId>`
- `capability.<providerId>.<providerDeviceId>.<providerEndpointId>.<providerCapabilityKey>`

Test evidence:

- canonical device ID differs from `providerDeviceId`.
- canonical endpoint ID differs from `providerEndpointId`.
- canonical capability ID differs from `providerCapabilityKey`.

## 11. Duplicate Detection Evidence

Duplicate detection occurs before service mutation calls in `DefaultTopologyMaterializationService`.

The test repeats:

- same `DeviceDiscoveryFact`
- same `EndpointDiscoveryFact`
- same `CapabilityDiscoveryFact`

Each returns `REJECT_DUPLICATE`, emits no changes and leaves topologyVersion unchanged.

## 12. State/Health Persistence Path

- `DeviceStateFact` uses `H2BaseTopologyRepository.saveDeviceState`.
- `HealthFact` uses `H2BaseTopologyRepository.saveEndpointHealth`.
- `BaseTopologyService.updateDeviceState` is not used by the materializer.
- Health is endpoint-level only for this seed.

## 13. Admission Predicate Evidence

`DefaultTopologyMaterializationService` accepts an admission predicate.

The test creates a denied materializer and verifies a fact from a non-admitted adapter returns:

- `REJECT_UNAUTHORIZED_ADAPTER`
- no emitted changes
- no topologyVersion advance

## 14. Capability Materialization Evidence

`CapabilityDiscoveryFact` uses:

```text
BaseTopologyService.addCapabilityWithResult(...)
```

It does not call `addEndpointWithResult(...)` to update an existing endpoint.

The test verifies:

- `ACCEPT_STRUCTURAL_MUTATION`
- `CAPABILITY_ADDED`
- recovered endpoint contains the canonical capability ID.

## 15. Recovery Evidence

The test discards repository, mutation service, query service and materialization service instances, then recreates H2 repository, mutation service and query service from the same H2 file path.

Recovered `CoreSnapshot` contains:

- materialized canonical device
- materialized canonical endpoint
- materialized canonical capability
- durable device state
- durable endpoint health
- current topologyVersion

`validateTarget(...)` succeeds with recovered topologyVersion.

## 16. Boundary Evidence

No implementation dependency was introduced for:

- SC-B
- SC-D
- Projection
- Effective View
- Session
- Identity
- Authority
- Policy
- RelationFact
- device-level health persistence
- independent materializer mutation engine

## 17. Required Statements

Structural mutation path: `BaseTopologyService.addDeviceWithResult / addEndpointWithResult / addCapabilityWithResult`.

Independent materializer mutation engine: no.

emittedEvents delta pattern used: yes.

Duplicate detection before service call: yes.

Habitat pre-condition check: yes.

State/health path: `H2BaseTopologyRepository` direct, not service `ConcurrentMap`.

Capability path: `addCapabilityWithResult`, not `addEndpointWithResult` on existing endpoint.

HealthFact scope: endpoint-level only for seed.

Deterministic canonical IDs: `device.<providerId>.<providerDeviceId>` etc.

TopologyChanged shape: existing transitional shape preserved.

SC-B required: no.

SC-D required: no.

Projection/Authority/Session required: no.

## 18. Deviations From Scope

None.

No real SC-D adapter, SC-B transport, REST/gRPC/WebSocket API, MCP facade, Projection, Effective View, Session, Identity, Authority, Policy, full Adapter Lifecycle, production database schema, external wire ABI, full RelationFact, deletion/removal materialization, independent materializer mutation engine or device-level health persistence was introduced.

## 19. Failure Signals Encountered

None.

No stop condition was reached.

## 20. Assumptions Made

- Deterministic seed IDs can safely include provider-native tokens as suffix material while remaining distinct canonical IDs.
- Conflicting provider binding for the same provider/native key is rejected as `REJECT_DUPLICATE` in this seed.
- Device-level health persistence remains unsupported because durable endpoint health already exists and MIR-007 scopes `HealthFact` to endpoint-level only.

## 21. Recommended Next MU

Add explicit conflict/quarantine semantics for provider binding changes if future materialization needs to distinguish duplicate observations from conflicting provider metadata updates.

## Build Result

Command:

```bash
mvn test
```

Result:

```text
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Post-validation hardening

MU-007 remains VALIDATED L4.

Post-validation hardening applied:
- providerRef field assertions added to `TopologyMaterializationSeedTest`
- `endpointHealthSurvivesSubsequentStructuralMutation` regression test added
- `EndpointHealth.lastSeenAt` is now asserted to preserve `HealthFact.observedAt()`
- `DefaultTopologyMaterializationService` now materializes `EndpointHealth.lastSeenAt`
  from `fact.observedAt()`, preserving provider observation time
- `H2BaseTopologyRepository.save()` patched with transitional merge-semantics for
  endpoint health: durable health written by `HealthFact` is preserved when
  subsequent structural saves carry stale `UNKNOWN` aggregate health

Corpus issues remaining:
- Full separation of structural topology persistence from endpoint health persistence
  remains open.
- `TopologyMaterializationStatePort` extraction remains deferred to the next
  health/materialization-related descent.

Scope unchanged:
- no SC-B introduced
- no SC-D runtime introduced
- no Projection / Effective View introduced
- no Session / Identity / Authority / Policy introduced
- no `TopologyChanged` shape change
- no MIR-007 acceptance criteria change
