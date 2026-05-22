# Context — MU-017 Patch 001 Review Gaps

```text
Package:          execution-package-MU-017-review-gaps-patch-001
Version:          v0.2.1
Baseline ZIP:     sovereign-connect-017.zip
Baseline commit:  ccd2221 feat(sc-c): add normalized SQLite persistence for canonical topology
Baseline tests:   152 / 0 failures
```

---

## 0. How to use this file

Read completely before modifying any file. This is a focused patch over the existing MU-017 normalized SQLite implementation.

This document provides **mandatory implementation patterns**, not fully literal Java bodies for every test. DDL, authority rules, transaction boundary, FK behavior and required semantics are normative. Test scenarios may be adapted to the existing test fixtures, constructors, helper names and row mapper classes.

Do not re-implement MU-017 from scratch.

---

## 1. Scope

Close exactly these items:

```text
B-01  Restore located-query fallback semantics in SQLiteBaseTopologyRepository.
B-02  Seed initial endpoint health in SQLiteBaseTopologyRepository.save() if absent.
B-03  Add habitat-global capability_id validation in BaseTopologyService.validateTopology().
H-04  Inject Clock into SQLiteMaterializationDecisionReplayRepository.
G-01  Clean working tree / evidence package.
```

Do **not** touch:

```text
V4 migration, except if a compile/runtime defect directly requires a tiny compatible fix.
TopologyPersistenceConfiguration, except to wire H-04 Clock if needed.
TemporalEngineConfiguration.
H2BaseTopologyRepository, except tests may keep using it as legacy seed fixture.
InMemoryBaseTopologyRepository, already no production @Repository; leave as-is.
Existing normalized-table authority rules.
Existing topology_json compatibility/read-model rule.
Existing 152 tests, which must continue to pass.
```

Hard non-goals:

```text
No graph database.
No northbound facade.
No Effective Interaction Boundary.
No View Composer.
No SC-B runtime.
No SC-D runtime.
No command dispatch.
No ActionTemporalPayload.
No outbox dispatcher.
No provider_bindings table.
```

---

## 2. B-01 — Located-query fallback semantics

### 2.1 Problem

The first MU-017 SQLite implementation resolves `findLocatedDevices(...)` and `findLocatedEndpoints(...)` only through explicit `TopologySpatialRelation(LOCATED_IN)` rows.

The previous H2-backed behavior also treated denormalized placement fields as valid transitional placement evidence:

```text
DeviceNode.roomId / DeviceNode.zoneId
EndpointNode.roomId / EndpointNode.zoneId
```

That fallback is still required because `roomId`/`zoneId` compatibility fields exist and are expected to remain consistent with `LOCATED_IN` during the transition.

### 2.2 Required semantics

`SQLiteBaseTopologyRepository.findLocatedDevices(habitatId, roomOrZoneId)` MUST return the union of:

```text
A. devices with explicit LOCATED_IN relation to roomOrZoneId;
B. devices whose normalized devices.room_id == roomOrZoneId;
C. devices whose normalized devices.zone_id == roomOrZoneId.
```

`SQLiteBaseTopologyRepository.findLocatedEndpoints(habitatId, roomOrZoneId)` MUST return the union of:

```text
A. endpoints with explicit LOCATED_IN relation to roomOrZoneId;
B. endpoints whose normalized endpoints.room_id == roomOrZoneId;
C. endpoints whose normalized endpoints.zone_id == roomOrZoneId.
```

Results MUST be deduplicated by canonical id.

### 2.3 Implementation pattern

Use a set/ordered-set union before loading nodes:

```java
Set<String> ids = new LinkedHashSet<>();
ids.addAll(locatedSubjectIds(habitatId, TopologySpatialEntityType.DEVICE, roomOrZoneId));
ids.addAll(directlyLocatedDeviceIds(habitatId, roomOrZoneId));
```

For endpoints:

```java
Set<String> ids = new LinkedHashSet<>();
ids.addAll(locatedSubjectIds(habitatId, TopologySpatialEntityType.ENDPOINT, roomOrZoneId));
ids.addAll(directlyLocatedEndpointIds(habitatId, roomOrZoneId));
```

Required SQL shape:

```sql
SELECT device_id
FROM devices
WHERE habitat_id = ?
  AND (room_id = ? OR zone_id = ?)
```

```sql
SELECT endpoint_id
FROM endpoints
WHERE habitat_id = ?
  AND (room_id = ? OR zone_id = ?)
```

Do not deserialize `topology_json` for these entity-level queries.

---

## 3. B-02 — Initial endpoint health seed-if-absent

### 3.1 Problem

The normalized SQLite implementation correctly avoids overwriting `endpoint_health` during structural saves. However, if a topology is initially saved with endpoint health embedded in the aggregate and there is no durable row yet, hydration may return fallback `UNKNOWN` instead of the initial health.

### 3.2 Decision

Use **Option A — seed endpoint health for every endpoint if absent**.

Rule:

```text
Structural save MUST ensure every persisted endpoint has an endpoint_health row.
If endpoint_health row already exists, structural save MUST NOT overwrite it.
If aggregate endpoint health is present, seed that value.
If aggregate endpoint health is null/incomplete in a future shape, seed UNKNOWN.
```

In the current code, `EndpointNode.health()` is non-null by record contract, so use that value.

### 3.3 Required implementation pattern

During `SQLiteBaseTopologyRepository.save(HabitatBaseTopology topology)`, after inserting endpoint rows and before transaction commit, call a helper equivalent to:

```java
private void seedEndpointHealthIfAbsent(String habitatId, EndpointNode endpoint) {
    EndpointHealth health = endpoint.health();
    if (health == null) {
        health = new EndpointHealth(HealthStatus.UNKNOWN, null, "seeded default endpoint health");
    }
    jdbcTemplate.update("""
        INSERT OR IGNORE INTO endpoint_health
          (habitat_id, endpoint_id, status, last_seen_at_ms, details)
        VALUES (?, ?, ?, ?, ?)
        """,
        habitatId,
        endpoint.endpointId(),
        health.status().name(),
        health.lastSeenAt() == null ? null : health.lastSeenAt().toEpochMilli(),
        health.details()
    );
}
```

Important:

```text
INSERT OR IGNORE is required.
Do not use INSERT OR REPLACE.
Do not UPDATE existing rows.
Existing durable health must win over stale aggregate health.
```

---

## 4. B-03 — Habitat-global capability_id validation

### 4.1 Problem

The normalized SQLite schema treats `capability_id` as globally unique within a habitat:

```sql
PRIMARY KEY (habitat_id, capability_id)
```

This is the accepted identity rule for MU-017. `owner_kind` and `owner_id` are ownership metadata, not part of identity.

The domain validation must reject duplicate `capability_id` values before persistence reaches a SQLite PK violation.

### 4.2 Required validation

Patch `BaseTopologyService.validateTopology(HabitatBaseTopology topology)` to validate uniqueness across both:

```text
DeviceNode.deviceCapabilities()
EndpointNode.capabilities()
```

Required pattern:

```java
List<String> allCapabilityIds = new ArrayList<>();
for (DeviceNode device : topology.devices()) {
    for (CapabilityNode cap : device.deviceCapabilities()) {
        allCapabilityIds.add(cap.capabilityId());
    }
}
for (EndpointNode endpoint : topology.endpoints()) {
    for (CapabilityNode cap : endpoint.capabilities()) {
        allCapabilityIds.add(cap.capabilityId());
    }
}
validateUnique("capabilityId", allCapabilityIds);
```

If `validateUnique(...)` already exists and is used for other canonical ids, reuse it. Do not create a divergent duplicate-validation style.

Expected failure message should contain:

```text
capabilityId must be unique
```

---

## 5. H-04 — Inject Clock into SQLiteMaterializationDecisionReplayRepository

H-04 is required in this patch, not optional.

Patch `SQLiteMaterializationDecisionReplayRepository` so recorded timestamps use injected `Clock`, not `Instant.now()`.

Required pattern:

```java
private final Clock clock;

public SQLiteMaterializationDecisionReplayRepository(
    DataSource dataSource,
    ObjectMapper objectMapper,
    Clock clock
) {
    this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
}
```

Replace:

```java
Instant.now().toEpochMilli()
```

with:

```java
Instant.now(clock).toEpochMilli()
```

Update `TopologyPersistenceConfiguration` to pass the existing `Clock` bean.

---

## 6. Required test scenarios

Use existing test infrastructure and helper style. Do not copy stale constructor calls from older packages.

### 6.1 Real constructor values to use in fixtures

Use current constructors/enums:

```java
new CapabilityTraits(true, true, false)
new DeviceTraits(false, true, true)
new EndpointTraits(true, true, true, true, false, false)
EndpointMetadata.empty()
EndpointKind.SWITCH_CHANNEL
```

Avoid:

```java
new CapabilityTraits()
new DeviceTraits()
new EndpointTraits()
new EndpointMetadata()
EndpointKind.SWITCH
```

When creating capability fixtures for normal roundtrip tests, do not reuse the same `capabilityId` across device-level and endpoint-level capabilities unless the test is explicitly testing rejection.

### 6.2 B-01 tests

Add or adapt tests covering:

```text
findLocatedDevicesFallsBackToDeviceRoomIdWhenRelationMissing
findLocatedDevicesFallsBackToDeviceZoneIdWhenRelationMissing
findLocatedEndpointsFallsBackToEndpointRoomIdWhenRelationMissing
findLocatedEndpointsFallsBackToEndpointZoneIdWhenRelationMissing
locatedQueryDeduplicatesRelationAndColumnFallbackMatches
```

Each test must persist normalized SQLite topology with no explicit `LOCATED_IN` relation for the target subject, then assert fallback via `room_id` or `zone_id` works.

### 6.3 B-02 tests

Add or adapt tests covering:

```text
initialEndpointHealthRoundTripsThroughSQLiteWithoutExplicitHealthWrite
structuralSaveDoesNotOverwriteExistingEndpointHealthInSQLite
everyPersistedEndpointGetsEndpointHealthRowIfAbsent
```

Required assertions:

```text
- initial endpoint HEALTHY/DEGRADED value survives save + hydration;
- durable endpoint_health row wins over stale aggregate UNKNOWN after structural re-save;
- endpoint_health row exists for each endpoint after save().
```

### 6.4 B-03 test

Add or adapt:

```text
duplicateCapabilityIdAcrossOwnersRejectedBeforePersistence
```

Correct scenario:

```text
1. Build RoomNode and ZoneNode required by validation.
2. Build DeviceNode with deviceCapabilities containing capabilityId = shared.
3. Build EndpointNode for that device with endpoint.capabilities containing the same capabilityId = shared.
4. Call BaseTopologyService.createInitialTopology(habitatId, rooms, zones, List.of(device), List.of(endpoint)).
5. Assert IllegalArgumentException with message containing "capabilityId must be unique".
6. Assert SQLite did not persist partial capability rows.
```

Do not use a flow that omits the endpoint from the validated topology.

### 6.5 H-04 test

Add or adapt:

```text
materializationDecisionReplayUsesInjectedClock
```

Use a fixed `Clock`. Persist a decision. Assert `recorded_at_ms == fixedClock.instant().toEpochMilli()`.

---

## 7. Stop conditions

Stop and report if any of these happens:

```text
1. The fallback query fix requires topology_json deserialization.
2. B-02 requires overwriting existing endpoint_health rows.
3. B-03 can only be implemented by changing the V4 primary key.
4. H-04 requires creating a second Clock bean or bypassing the existing one.
5. Any existing MU-017 normalized persistence tests fail.
6. Any existing temporal runtime / outbox / topology regression tests fail.
7. Working tree still contains .idea, mir-001/mir-002 noise, #U2014 artifacts, or target/*.sqlite.
```

---

## 8. Implementation report requirements

The implementation report must truthfully state:

```text
- B-01 status and tests.
- B-02 status and tests.
- B-03 status and tests.
- H-04 status and tests.
- Full mvn test summary.
- git status --short.
- Any remaining H-01/H-02/H-03 debt.
- Confirmation that no V4 redesign, northbound facade, EIB, VC, SC-B, SC-D, graph DB, ActionTemporalPayload, command dispatch or outbox dispatcher was introduced.
```
