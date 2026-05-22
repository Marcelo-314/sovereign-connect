# Codex Prompt — MU-017 Patch 001 Review Gaps

**Package:** `execution-package-MU-017-review-gaps-patch-001`  
**Version:** `v0.2.1`  
**Target branch:** `fix/sc-c-mir-017-normalized-topology-sqlite-persistence`  
**Commit:** `fix(sc-c): close MIR-017 topology SQLite persistence gaps`  

---

## Mission

Patch the existing MU-017 normalized SQLite topology implementation. Do **not** reimplement MU-017 from scratch.

Read `context.md` completely before editing code.

Close exactly:

```text
B-01 Restore located query fallback semantics.
B-02 Preserve initial endpoint health roundtrip without overwriting durable health.
B-03 Validate habitat-global capability_id uniqueness before persistence.
H-04 Inject Clock into SQLiteMaterializationDecisionReplayRepository.
G-01 Clean working tree / evidence artifacts.
```

---

## Hard constraints

Do not introduce:

```text
- graph database
- northbound facade
- Effective Interaction Boundary
- View Composer
- SC-B runtime
- SC-D adapter runtime
- command dispatch
- ActionTemporalPayload
- outbox dispatcher
- provider_bindings table
- topology_json as authority
```

Do not change normalized-table authority.
Do not reimport ledger/outbox persistence into topology repositories.
Do not make H2 or InMemory the production topology path.

---

## Phase 1 — B-01 located query fallback

Patch `SQLiteBaseTopologyRepository` so:

```text
findLocatedDevices = LOCATED_IN relation matches ∪ devices.room_id/zone_id matches.
findLocatedEndpoints = LOCATED_IN relation matches ∪ endpoints.room_id/zone_id matches.
```

Deduplicate by canonical id.
Do not read `topology_json` for these queries.

Required tests:

```text
findLocatedDevicesFallsBackToDeviceRoomIdWhenRelationMissing
findLocatedDevicesFallsBackToDeviceZoneIdWhenRelationMissing
findLocatedEndpointsFallsBackToEndpointRoomIdWhenRelationMissing
findLocatedEndpointsFallsBackToEndpointZoneIdWhenRelationMissing
locatedQueryDeduplicatesRelationAndColumnFallbackMatches
```

---

## Phase 2 — B-02 endpoint health seed-if-absent

Patch `SQLiteBaseTopologyRepository.save(...)` so structural save seeds `endpoint_health` for every endpoint if absent.

Rules:

```text
- INSERT OR IGNORE only.
- Never overwrite existing durable endpoint_health rows.
- Use endpoint.health() when present.
- Seed UNKNOWN if a future shape ever allows null/incomplete aggregate health.
```

Required tests:

```text
initialEndpointHealthRoundTripsThroughSQLiteWithoutExplicitHealthWrite
structuralSaveDoesNotOverwriteExistingEndpointHealthInSQLite
everyPersistedEndpointGetsEndpointHealthRowIfAbsent
```

---

## Phase 3 — B-03 habitat-global capability_id validation

Patch `BaseTopologyService.validateTopology(...)` so `capabilityId` is unique across:

```text
DeviceNode.deviceCapabilities()
EndpointNode.capabilities()
```

The validation must fail before SQLite PK violation.

Required test:

```text
duplicateCapabilityIdAcrossOwnersRejectedBeforePersistence
```

The test must validate a full topology containing both the device-level duplicate and endpoint-level duplicate in the same `createInitialTopology(...)` call.

---

## Phase 4 — H-04 injected Clock

Patch `SQLiteMaterializationDecisionReplayRepository` to accept `Clock` and use `Instant.now(clock)`.

Patch `TopologyPersistenceConfiguration` to pass the existing Clock bean.

Required test:

```text
materializationDecisionReplayUsesInjectedClock
```

---

## Phase 5 — governance cleanup

Before final report:

```text
- Remove .idea/* from submitted changes.
- Remove unrelated .gitignore changes unless explicitly justified.
- Remove docs/mir/mir-001 and docs/mir/mir-002 noise.
- Remove #U2014 duplicate artifacts.
- Remove target/*.sqlite runtime artifacts.
- Ensure docs/mir/mir-017/patch-001-review-gaps/ is the only new docs package for this patch.
```

---

## Test fixture warning

Use current constructors/enums:

```java
new CapabilityTraits(true, true, false)
new DeviceTraits(false, true, true)
new EndpointTraits(true, true, true, true, false, false)
EndpointMetadata.empty()
EndpointKind.SWITCH_CHANNEL
```

Do not use no-arg traits constructors or `EndpointKind.SWITCH`.

---

## Final verification

Run:

```bash
mvn test
git status --short
```

Update `implementation-report.md` using the template and include exact test summary.
