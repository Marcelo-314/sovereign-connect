# MIR-003 / MIR-004 Implementation Report

## 1. Summary

This increment implements the SC-C Persistence and Memory Seed using a file-backed H2/JDBC durable adapter while preserving the existing MU-001/MU-002 in-memory tests and public repository port. It proves Base Topology snapshots, topologyVersion, mutation records, device state, and endpoint health survive adapter/service recreation without SC-B, SC-D, Projection, Session, Identity, Authority, or Policy.

## 2. Files Changed

- `pom.xml`
- `src/main/java/com/sovereign/connect/core/topology/model/BaseTopologySnapshot.java`
- `src/main/java/com/sovereign/connect/core/topology/model/TopologyMutationRecord.java`
- `src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java`
- `src/test/java/com/sovereign/connect/core/topology/PersistenceMemorySeedTest.java`
- `docs/mir/mir-003/implementation-report.md`

## 3. Domain Types Added or Modified

- Added `BaseTopologySnapshot`.
- Added `TopologyMutationRecord`.
- Existing `HabitatBaseTopology`, `TopologyVersion`, `TopologyMutationResult`, `TopologyTargetRef`, `TargetValidationResult`, `EndpointHealth`, `ProviderDeviceRef`, and `ProviderEndpointRef` were reused unchanged.

## 4. Repository/Persistence Adapters Added or Modified

- Added `H2BaseTopologyRepository`.
- It implements the existing `BaseTopologyRepository` port:
  - `save(HabitatBaseTopology)`
  - `findByHabitatId(String)`
  - `findCurrentVersion(String)`
- It also provides seed-only durable methods:
  - `findSnapshot(String)`
  - `appendMutationRecord(TopologyMutationRecord)`
  - `findMutationRecords(String)`
  - `saveDeviceState(String, String, Map<String, Object>)`
  - `findDeviceState(String, String)`
  - `saveEndpointHealth(String, String, EndpointHealth)`
  - `findEndpointHealth(String, String)`
- `InMemoryBaseTopologyRepository` was left untouched.

## 5. Services Modified

None.

`BaseTopologyService` remains decoupled from H2/JDBC and depends only on `BaseTopologyRepository`.

## 6. Seed Storage Strategy Used

- H2 file-backed storage.
- Spring JDBC / `JdbcTemplate`.
- Programmatic schema creation inside `H2BaseTopologyRepository`.
- `HabitatBaseTopology` is serialized as JSON CLOB using Jackson with Java Time module registration.
- `BaseTopologySnapshot.topologyVersion` is stored as an indexed/derived envelope value; `HabitatBaseTopology.topologyVersion` remains the source of truth.
- Device state is persisted via explicit adapter methods.
- Endpoint health is persisted from saved topology snapshots and exposed via direct seed readback.

## 7. Tests Added

- `PersistenceMemorySeedTest#ac001ToAc025TopologyMemorySurvivesAdapterAndServiceRecreation` - creates topology, mutates it, records mutation memory, persists state and health, recreates DataSource/adapter/service, and verifies AC-001 through AC-025 recovery invariants.

Existing tests preserved:

- `BaseTopologyServiceTest` - 8 tests still pass.
- `TopologyVersionHardeningTest` - 12 tests still pass.

## 8. Acceptance Results

| AC | Result | Evidence |
|---|---|---|
| AC-001 | PASS | `H2BaseTopologyRepository` exists and implements durable snapshot storage. |
| AC-002 | PASS | `BaseTopologyRepository` signatures unchanged; all existing MU-001/MU-002 tests pass. |
| AC-003 | PASS | `save(HabitatBaseTopology)` persists current snapshot as JSON CLOB. |
| AC-004 | PASS | `findByHabitatId("habitat-001")` retrieves recovered topology after recreation. |
| AC-005 | PASS | `findSnapshot("habitat-001")` returns envelope with `topologyVersion`. |
| AC-006 | PASS | Test asserts `snapshot.topologyVersion() == snapshot.topology().topologyVersion()`. |
| AC-007 | PASS | Test asserts recovered topology version, snapshot version, and current version all match. |
| AC-008 | PASS | Test asserts `findCurrentVersion("habitat-001")` returns recovered version `2`. |
| AC-009 | PASS | Test discards first service/adapter/DataSource and recreates new instances on same H2 file path. |
| AC-010 | PASS | No SC-B dependency or replay path exists in adapter/service/test. |
| AC-011 | PASS | No SC-D dependency or rediscovery path exists in adapter/service/test. |
| AC-012 | PASS | Test inspects adapter surface for no Hub/Projection/Session/Identity/Authority/Policy dependency. |
| AC-013 | PASS | Recovered `deviceId` remains `device.light.kitchen-main`. |
| AC-014 | PASS | Recovered endpoint IDs remain `endpoint.light.kitchen-main` and `endpoint.light.kitchen-dimmer`. |
| AC-015 | PASS | Recovered provider refs retain `tuya.device.abc` and `tuya.dp.1`. |
| AC-016 | PASS | Test asserts provider IDs differ from canonical `deviceId` and `endpointId` after recovery. |
| AC-017 | PASS | `TopologyMutationRecord` is appended with `fromVersion=1` and `toVersion=2`. |
| AC-018 | PASS | `findMutationRecords("habitat-001")` reads the record back after recreation. |
| AC-019 | PASS | Device state persisted after mutation; current `topologyVersion` remains unchanged. |
| AC-020 | PASS | Device state `power=on`, `level=75` recovers after adapter/service recreation. |
| AC-021 | PASS | Endpoint health update persists through aggregate save; `topologyVersion` remains unchanged. |
| AC-022 | PASS | Endpoint health recovers as `DEGRADED` after recreation. |
| AC-023 | PASS | This report states H2 file-backed storage, not pure in-memory storage, validates persistence. |
| AC-024 | PASS | This report states seed storage is not final production storage; ADR required. |
| AC-025 | PASS | `mvn test` succeeds with 21 tests, 0 failures, 0 errors, 0 skipped. |

## 9. Invariants Preserved

- SC-C owns persistence - preserved.
- Base Topology persists and recovers - preserved.
- topologyVersion persists and recovers - preserved.
- snapshot.topologyVersion == aggregate topologyVersion - preserved.
- canonical deviceId survives recovery - preserved.
- canonical endpointId survives recovery - preserved.
- provider refs remain metadata - preserved.
- state persistence does not advance version - preserved.
- health persistence does not advance version - preserved.
- recovery independent of SC-B/SC-D - preserved.
- recovery independent of Projection/Session/Authority/Policy - preserved.
- BaseTopologyRepository compatibility - preserved.
- seed storage not declared production doctrine - preserved.

## 10. Deviations From Scope

None.

No JPA entities, production schema, migration framework, event sourcing framework, transactional outbox, SC-B replay, SC-D rediscovery, full snapshot query API, TemporalActs persistence, action terminal result persistence, idempotency persistence, Projection/Effective View cache, or Session/Identity/Authority/Policy persistence were introduced.

## 11. Failure Signals Encountered

None.

No stop condition was reached.

## 12. Assumptions Made

- The directory is `docs/mir/mir-003`, but the package content is labeled MIR-004. The local `mir-003` files were treated as the authoritative execution package for this request.
- A single durable adapter with seed-only extra methods is the smallest implementation that preserves `BaseTopologyRepository` compatibility.
- Device state recovery can be proven through adapter-direct methods while leaving MU-002 service-local state behavior unchanged.
- Endpoint health recovery can be proven from persisted aggregate state and the seed `endpoint_health` table populated by adapter `save(...)`.

## 13. Corpus Issues Discovered

- `docs/mir/mir-003/codex-prompt.md` is titled MIR-004 and references `docs/mir/mir-004/context.md` and `docs/mir/mir-004/acceptance-map.md`.
- The available files are under `docs/mir/mir-003`, so this implementation report was written to `docs/mir/mir-003/implementation-report.md`.

## 14. Storage Technology Statement

Seed storage: H2 file-backed database with Spring JDBC and JSON CLOB snapshot storage.

Production storage: not decided.

ADR required: `ADR-SOV-SC-C-STORAGE-TECH-001`.

## 15. Recommended Next MU

Define the snapshot query boundary and durable repository contract refinements needed for production-facing reads, while keeping storage technology non-final until `ADR-SOV-SC-C-STORAGE-TECH-001` is completed.

## Build Result

Command:

```bash
mvn test
```

Result:

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
