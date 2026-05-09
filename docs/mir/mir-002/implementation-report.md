# MIR-002 Implementation Report

## 1. Summary

MU-002 hardens the SC-C `topologyVersion` seed without rewriting the MU-001 topology model or changing the existing `BaseTopologyService.addEndpoint` API. It adds mutation result reporting, non-structural update paths that do not advance version, stale target validation, idempotency identity exclusion, and AC-001 through AC-025 test evidence.

## 2. Files Changed

- `src/main/java/com/sovereign/connect/core/topology/model/TopologyMutationResult.java`
- `src/main/java/com/sovereign/connect/core/topology/model/TopologyTargetRef.java`
- `src/main/java/com/sovereign/connect/core/topology/model/TargetValidationResult.java`
- `src/main/java/com/sovereign/connect/core/topology/model/IdempotencyIdentity.java`
- `src/main/java/com/sovereign/connect/core/topology/service/BaseTopologyService.java`
- `src/test/java/com/sovereign/connect/core/topology/TopologyVersionHardeningTest.java`
- `docs/mir/mir-002/implementation-report.md`

## 3. Domain Types Added or Modified

- Added `TopologyMutationResult`.
- Added `TopologyTargetRef`.
- Added `TargetValidationResult`.
- Added `IdempotencyIdentity`.
- Existing `TopologyVersion`, `TopologyChanged`, `HabitatBaseTopology`, `DeviceNode`, `EndpointNode`, and `CapabilityNode` were reused without semantic rewrite.

## 4. Services/Validators Added or Modified

- Modified `BaseTopologyService`.
- Preserved existing MU-001 `addEndpoint(String, EndpointNode) -> HabitatBaseTopology` behavior and signature.
- Added `addEndpointWithResult(String, EndpointNode) -> TopologyMutationResult`.
- Added `updateEndpointHealth(String, String, HealthStatus)`.
- Added `updateDeviceState(String, String, Map<String, Object>)`.
- Added `findDeviceState(String, String)` for seed-level state verification.
- Added `validateTarget(String, TopologyTargetRef, TopologyVersion) -> TargetValidationResult`.

## 5. Persistence Strategy Used

The existing in-memory repository strategy is preserved through `BaseTopologyRepository` and `InMemoryBaseTopologyRepository`.
Seed-only device operational state is stored in an in-memory map inside `BaseTopologyService`; it does not affect structural topology snapshots or `topologyVersion`.

## 6. Tests Added

- `ac001ToAc004TopologyVersionIsTypedHabitatScopedInitialAndAdvancesOnlyForAcceptedStructuralMutation` - proves typed Habitat-scoped version, initial version, and structural advancement.
- `ac005RejectedStructuralMutationDoesNotAdvanceTopologyVersion` - proves duplicate endpoint rejection leaves version unchanged.
- `ac006DeviceStateUpdateDoesNotAdvanceTopologyVersion` - proves state update stores seed state without version advancement.
- `ac007EndpointHealthUpdateDoesNotAdvanceTopologyVersion` - proves health update changes health without version advancement.
- `ac008ToAc010ProjectionSessionAuthorityAndPolicyHaveNoVersionAdvancementPath` - proves no service path accepts these inputs as version triggers.
- `ac011AndAc012ProviderNativeRevisionIsMetadataAndDoesNotAdvanceTopologyVersion` - proves provider revision metadata is not canonical version and does not advance it.
- `ac013AndAc014SnapshotIncludesVersionAndStaysConsistentAfterMutation` - proves retrieved snapshot version and topology stay internally consistent.
- `ac015AndAc016MutationResultAndEventExposeFromAndToVersions` - proves mutation result and event expose from/to versions and version changes.
- `ac017ToAc019StaleTargetValidationRevalidatesOrFailsBeforeDispatch` - proves valid, stale-valid, missing target, and capability mismatch cases.
- `ac020TopologyVersionIsExcludedFromIdempotencyIdentity` - proves idempotency identity excludes topologyVersion.
- `ac021ToAc023NoScbScdOrUserSessionAuthorityPolicyDependenciesAreRequired` - proves no SC-B, SC-D, user/session/identity/authority/policy dependency on service/aggregate surfaces.
- `ac024StructuralAndNonStructuralBehaviorAreCoveredTogether` - proves structural and non-structural behavior are covered in the same suite.

Existing MU-001 tests in `BaseTopologyServiceTest` continue to pass.

## 7. Acceptance Results

- AC-001 - pass. Evidence: `TopologyVersion` domain type exists and is asserted in `ac001ToAc004...`.
- AC-002 - pass. Evidence: `TopologyVersionScopeType.HABITAT` and `scope.id == habitatId` asserted in `ac001ToAc004...`.
- AC-003 - pass. Evidence: initial version value `1` asserted in `ac001ToAc004...`.
- AC-004 - pass. Evidence: accepted `addEndpoint` advances version to `2` in `ac001ToAc004...`.
- AC-005 - pass. Evidence: duplicate endpoint mutation throws and current version remains unchanged in `ac005...`.
- AC-006 - pass. Evidence: `updateDeviceState` leaves version unchanged in `ac006...`.
- AC-007 - pass. Evidence: `updateEndpointHealth` leaves version unchanged in `ac007...`.
- AC-008 - pass. Evidence: service reflection proves no Projection advancement path in `ac008ToAc010...`.
- AC-009 - pass. Evidence: service reflection proves no Session advancement path in `ac008ToAc010...`.
- AC-010 - pass. Evidence: service reflection proves no Authority/Policy advancement path in `ac008ToAc010...`.
- AC-011 - pass. Evidence: provider revision metadata differs from canonical topologyVersion in `ac011AndAc012...`.
- AC-012 - pass. Evidence: provider revision-like state update does not advance topologyVersion in `ac011AndAc012...`.
- AC-013 - pass. Evidence: retrieved snapshot includes `topologyVersion` in `ac013AndAc014...`.
- AC-014 - pass. Evidence: retrieved post-mutation snapshot has version `2` and new endpoint/device links in `ac013AndAc014...`.
- AC-015 - pass. Evidence: `TopologyMutationResult` and `TopologyChanged` expose `fromVersion`/`toVersion` in `ac015AndAc016...`.
- AC-016 - pass. Evidence: `toVersion` differs from `fromVersion` in `ac015AndAc016...`.
- AC-017 - pass. Evidence: stale version triggers revalidation in `ac017ToAc019...`.
- AC-018 - pass. Evidence: stale version with still-valid target returns `VALID_AFTER_REVALIDATION` in `ac017ToAc019...`.
- AC-019 - pass. Evidence: stale version with missing target returns `TARGET_NOT_FOUND`; changed capability returns `CAPABILITY_MISMATCH` in `ac017ToAc019...`.
- AC-020 - pass. Evidence: equal `IdempotencyIdentity` values with different implied topology versions; record has no `topologyVersion` field in `ac020...`.
- AC-021 - pass. Evidence: service/aggregate reflection has no SC-B dependency in `ac021ToAc023...`.
- AC-022 - pass. Evidence: service/aggregate reflection has no SC-D dependency in `ac021ToAc023...`.
- AC-023 - pass. Evidence: service/aggregate reflection has no user/session/identity/authority/policy dependency in `ac021ToAc023...`.
- AC-024 - pass. Evidence: structural and non-structural behavior covered by `TopologyVersionHardeningTest` plus existing MU-001 tests.
- AC-025 - pass. Evidence: `mvn test` succeeds with 20 tests, 0 failures, 0 errors, 0 skipped.

## 8. Invariants Preserved

- SC-C owns topologyVersion - preserved.
- topologyVersion belongs to Base Topology - preserved.
- topologyVersion is Habitat-scoped - preserved.
- structural mutation advances version - preserved.
- rejected mutation does not advance - preserved.
- state/health does not advance - preserved.
- Projection does not advance - preserved.
- provider-native is not topologyVersion - preserved.
- snapshot/version consistent - preserved.
- stale target revalidation works - preserved.
- topologyVersion excluded from idempotency - preserved.

## 9. Deviations From Scope

None.

No durable persistence, DB schema, distributed versioning, event sourcing, outbox, SC-B transport, real SC-D adapter, provider discovery, command dispatch, device execution, historical snapshots, Projection, Session, Identity, Authority, Policy, Hub, Surface, MCP, or UI module was introduced.

## 10. Failure Signals Encountered

None.

No stop condition was reached.

## 11. Assumptions Made

- `addEndpoint` remains the MU-001 structural mutation API and must stay backward-compatible.
- A new `addEndpointWithResult` method is acceptable for returning `TopologyMutationResult`.
- Seed-level `updateDeviceState` may store state in memory without adding state fields to the Base Topology aggregate.
- Health updates may update endpoint health in the stored in-memory snapshot without changing structural topology or advancing `topologyVersion`.
- Stale target validation can use current in-memory snapshot state; historical snapshots are not required for MU-002.

## 12. Corpus Issues Discovered

None blocking.

The MIR-002 context and acceptance map were present and used for AC numbering.

## 13. Recommended Upstream Patches

- Clarify whether seed-level health updates should update `TopologyMetadata.lastModified` or leave aggregate metadata unchanged when `topologyVersion` is unchanged.
- Clarify whether device operational state should remain service-local for the seed or move behind a separate state repository port in a later MU.
- Clarify whether `TARGET_NOT_FOUND` or `CAPABILITY_MISMATCH` is preferred when an endpoint exists but is no longer linked by its device.

## 14. Recommended Next MU

Introduce a durable persistence adapter behind the existing `BaseTopologyRepository` port and add snapshot query semantics while preserving the version advancement and stale-target behavior validated in MU-002.

## Build Result

Command:

```bash
mvn test
```

Result:

```text
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
