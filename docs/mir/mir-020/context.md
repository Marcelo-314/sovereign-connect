# Context — MIR-020 Northbound Facade Hardening

```text
Document ID: CTX-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Version:     v0.3.1-reviewed
MU:          MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Slot:        MU-020
Baseline:    sovereign-connect-020-pre-csa.zip (188 tests / 0 failures)
Branch:      feat/sc-c-mir-020-northbound-facade-hardening
```

---

## 0. How to use this file

Read completely before writing any code. Section 2 gives the exact current shapes
of all types that change. Section 3 gives the complete call-site replacements for
the source field addition. Section 4 gives the DeviceHealth derivation body. Section 5
gives the diagnostics method body. Section 6 gives the safe incremental validation patch. Section 7 gives
optional mapper helpers. Section 8 gives test intent templates aligned with the existing Mockito-based tests. Section 9 gives
stop conditions.

---

## 1. Scope — eight bounded changes

```text
1. Add VALIDATION_ERROR to ScNorthboundStatus
2. Add String source to ScNorthboundError and ScNorthboundWarning
3. Update all 9 call sites (6 in ScNorthboundResponse.java, 3 in DefaultScCoreNorthboundFacade.java)
4. Add validationError(...) factory to ScNorthboundResponse
5. Make VALIDATION_ERROR reachable via dueAt-in-past guard in createSignalTemporalAct
6. Replace getDeviceHealth with DERIVED_FROM_ENDPOINTS implementation
7. Add getNorthboundDiagnostics to interface + implementation
8. Harden NorthboundDeviceHealthView to add source, endpointCount, warnings
```

Do NOT implement: EIB, View Composer, HTTP/SSE/gRPC/MCP/GraphQL/WebSocket,
SC-B runtime, SC-D invocation, discovery, command dispatch, ActionTemporalPayload,
Flyway migrations, SQLite schema changes.

---

## 2. Exact current shapes — what changes

### 2.1 ScNorthboundStatus (current 11 values → add 1)

```java
public enum ScNorthboundStatus {
    OK, CREATED, ACCEPTED, CANCELLED,
    NOT_FOUND, INVALID_REQUEST, INVALID_CANONICAL_ID,
    UNSUPPORTED_PROFILE, DEFERRED_SC_B_REQUIRED,
    UNKNOWN_PENDING_NORMALIZATION, INTERNAL_ERROR
    // ADD: VALIDATION_ERROR
}
```

### 2.2 ScNorthboundError (2 fields → 3 fields)

```java
// CURRENT:
public record ScNorthboundError(String code, String message) {}

// TARGET:
public record ScNorthboundError(String code, String message, String source) {}
```

### 2.3 ScNorthboundWarning (2 fields → 3 fields)

```java
// CURRENT:
public record ScNorthboundWarning(String code, String message) {}

// TARGET:
public record ScNorthboundWarning(String code, String message, String source) {}
```

### 2.4 NorthboundDeviceHealthView (4 fields → 6 fields)

```java
// CURRENT:
public record NorthboundDeviceHealthView(
    String deviceId, String status, Instant lastSeenAt, String details) {}

// TARGET:
public record NorthboundDeviceHealthView(
    String deviceId,
    String status,           // HEALTHY, DEGRADED, OFFLINE — from HealthStatus.name()
    String source,           // DERIVED_FROM_ENDPOINTS | UNKNOWN_PENDING_NORMALIZATION
    int endpointCount,       // deviceSnapshot.device().endpointIds().size()
    Instant readAt,          // Instant.now(clock)
    List<ScNorthboundWarning> warnings
) {}
```

Remove `lastSeenAt` and `details` — those fields belong to `NorthboundEndpointHealthView`,
not to the derived device health view. The device health is derived, not a single
endpoint's status.

---

## 3. Exact call-site replacements — all 9 sites

### 3.1 ScNorthboundResponse.java — 6 ScNorthboundError sites + 1 new factory

Replace each factory method in `ScNorthboundResponse.java`:

```java
// notFound — add source "northbound.query"
public static <T> ScNorthboundResponse<T> notFound(String code, String message) {
    return of(ScNorthboundStatus.NOT_FOUND, null, List.of(),
        new ScNorthboundError(code, message, "northbound.query"));
}

// invalidRequest — add source "northbound.validation"
public static <T> ScNorthboundResponse<T> invalidRequest(String code, String message) {
    return of(ScNorthboundStatus.INVALID_REQUEST, null, List.of(),
        new ScNorthboundError(code, message, "northbound.validation"));
}

// invalidCanonicalId — add source "northbound.validation"
public static <T> ScNorthboundResponse<T> invalidCanonicalId(String code, String message) {
    return of(ScNorthboundStatus.INVALID_CANONICAL_ID, null, List.of(),
        new ScNorthboundError(code, message, "northbound.validation"));
}

// unsupportedProfile — add source "northbound.unsupported_profile"
public static <T> ScNorthboundResponse<T> unsupportedProfile(String code, String message) {
    return of(ScNorthboundStatus.UNSUPPORTED_PROFILE, null, List.of(),
        new ScNorthboundError(code, message, "northbound.unsupported_profile"));
}

// unknownPendingNormalization — add source "northbound.pending_normalization"
public static <T> ScNorthboundResponse<T> unknownPendingNormalization(String code, String message) {
    return of(ScNorthboundStatus.UNKNOWN_PENDING_NORMALIZATION, null, List.of(),
        new ScNorthboundError(code, message, "northbound.pending_normalization"));
}

// internalError — add source "northbound.internal"
public static <T> ScNorthboundResponse<T> internalError(String code, String message) {
    return of(ScNorthboundStatus.INTERNAL_ERROR, null, List.of(),
        new ScNorthboundError(code, message, "northbound.internal"));
}

// NEW factory — validationError
public static <T> ScNorthboundResponse<T> validationError(String code, String message) {
    return of(ScNorthboundStatus.VALIDATION_ERROR, null, List.of(),
        new ScNorthboundError(code, message, "northbound.validation"));
}
```

One-String overloads (e.g. `notFound(String message)`) delegate to the two-arg
version — they are unchanged in behavior.

### 3.2 DefaultScCoreNorthboundFacade.java — 3 ScNorthboundWarning sites

```java
// Line 302 (createSignalTemporalAct IdempotentReplay):
new ScNorthboundWarning("IDEMPOTENT_REPLAY", "Request already processed", "temporal.application")

// Line 324 (cancelTemporalAct AlreadyTerminal):
new ScNorthboundWarning("ALREADY_TERMINAL", "Act was already in terminal state", "temporal.application")

// Line 334 (cancelTemporalAct IdempotentReplay):
new ScNorthboundWarning("IDEMPOTENT_REPLAY", "Cancel already recorded", "temporal.application")
```

---

## 4. getDeviceHealth — exact implementation body

```java
@Override
public ScNorthboundResponse<NorthboundDeviceHealthView> getDeviceHealth(
    String habitatId, String deviceId) {

    // Step 1: resolve device
    Optional<DeviceSnapshot> deviceSnapshotOpt = queryService.findDevice(habitatId, deviceId);
    if (deviceSnapshotOpt.isEmpty()) {
        return ScNorthboundResponse.notFound("DEVICE_NOT_FOUND",
            "Device not found: " + deviceId);
    }
    DeviceSnapshot deviceSnapshot = deviceSnapshotOpt.get();
    List<String> endpointIds = deviceSnapshot.device().endpointIds();
    int endpointCount = endpointIds.size();

    // Step 2: empty endpoint set
    if (endpointIds.isEmpty()) {
        return ScNorthboundResponse.of(
            ScNorthboundStatus.UNKNOWN_PENDING_NORMALIZATION,
            new NorthboundDeviceHealthView(deviceId, "UNKNOWN_PENDING_NORMALIZATION",
                "UNKNOWN_PENDING_NORMALIZATION", 0, Instant.now(clock), List.of()),
            List.of(), null);
    }

    // Step 3: derive health from endpoint health rows
    // CRITICAL: use findEndpointHealth directly — NOT findEndpoint which uses aggregate fallback
    HealthStatus derived = HealthStatus.HEALTHY;
    for (String endpointId : endpointIds) {
        Optional<EndpointHealth> healthOpt =
            queryService.findEndpointHealth(habitatId, endpointId);

        HealthStatus endpointStatus = healthOpt
            .map(EndpointHealth::status)
            .orElse(HealthStatus.UNKNOWN);  // missing row = UNKNOWN evidence

        if (endpointStatus == HealthStatus.OFFLINE) {
            derived = HealthStatus.OFFLINE;
            break;  // OFFLINE is definitive — no need to continue
        }
        if (endpointStatus == HealthStatus.DEGRADED || endpointStatus == HealthStatus.UNKNOWN) {
            derived = HealthStatus.DEGRADED;
            // continue — OFFLINE can still be found
        }
    }

    return ScNorthboundResponse.ok(new NorthboundDeviceHealthView(
        deviceId,
        derived.name(),
        "DERIVED_FROM_ENDPOINTS",
        endpointCount,
        Instant.now(clock),
        List.of()
    ));
}
```

**Forbidden:** Do NOT call `deviceSnapshot.device().health()`,
`endpointSnapshot.health()`, or any aggregate health accessor.
The loop above is the only authorized health source.

---

## 5. getNorthboundDiagnostics — exact implementation

### 5.1 Add to ScCoreNorthboundFacade interface

```java
ScNorthboundResponse<NorthboundDiagnosticsView> getNorthboundDiagnostics(String habitatId);
```

### 5.2 New DTOs in runtime package

```java
public record NorthboundDiagnosticsView(
    String habitatId,
    String topologyVersion,                              // value or "UNKNOWN"
    NorthboundTemporalRuntimeStatusView temporalEngineStatus,
    NorthboundMigrationReadinessView migrationReadiness,
    Instant readAt,
    List<ScNorthboundWarning> warnings
) {}

public record NorthboundMigrationReadinessView(
    String status,   // UNKNOWN_PENDING_NORMALIZATION
    String source,   // "migration.readiness"
    String message   // explanation
) {}
```

### 5.3 Implementation body

```java
@Override
public ScNorthboundResponse<NorthboundDiagnosticsView> getNorthboundDiagnostics(
    String habitatId) {

    // topologyVersion from canonical query service
    String topologyVersion = queryService.findCurrentTopologyVersion(habitatId)
        .map(TopologyVersion::value)
        .orElse("UNKNOWN");

    // temporalEngineStatus from TemporalEngineHealth — reuse existing mapper
    NorthboundTemporalRuntimeStatusView temporalStatus =
        NorthboundMapper.toTemporalRuntimeStatusView(habitatId, engineHealth);

    // migrationReadiness — no read authority exists
    NorthboundMigrationReadinessView migrationReadiness = new NorthboundMigrationReadinessView(
        "UNKNOWN_PENDING_NORMALIZATION",
        "migration.readiness",
        "No migration readiness read port exists; deferred to future MU."
    );

    ScNorthboundWarning migrationWarning = new ScNorthboundWarning(
        "MIGRATION_READINESS_PENDING_NORMALIZATION",
        "Migration readiness is not yet exposed through a Northbound-safe port.",
        "migration.readiness"
    );

    return ScNorthboundResponse.ok(new NorthboundDiagnosticsView(
        habitatId,
        topologyVersion,
        temporalStatus,
        migrationReadiness,
        Instant.now(clock),
        List.of(migrationWarning)
    ));
}
```

**Note:** `NorthboundMapper.toTemporalRuntimeStatusView(habitatId, engineHealth)` already
exists with the exact signature — confirmed from the codebase. Do NOT reimplement it.

---

## 6. VALIDATION_ERROR reachable path — incremental patch only

MU-019 already has this method shape and call site:

```java
private ScNorthboundResponse<NorthboundTemporalActView> validateCreateRequest(
    String habitatId,
    NorthboundCreateSignalTemporalActRequest request
) { ... }
```

and `createSignalTemporalAct(...)` already calls:

```java
ScNorthboundResponse<NorthboundTemporalActView> validation = validateCreateRequest(habitatId, request);
if (validation != null) {
    return validation;
}
```

Do **not** rewrite this method into an `Optional`-returning helper and do **not** remove the existing `habitatId`, `request == null`, `signalKind`, `createdByRef` or `idempotencyKey` guards.

Add only this semantic validation after the existing `request.dueAt() == null` guard:

```java
if (!request.dueAt().isAfter(Instant.now(clock))) {
    return ScNorthboundResponse.validationError(
        "INVALID_DUE_AT",
        "dueAt must be a future Instant"
    );
}
```

Current syntactic/missing-field checks remain `INVALID_REQUEST`. `VALIDATION_ERROR` is required for the dueAt-in-the-past semantic validation path.

---

## 7. Add to NorthboundMapper

```java
static NorthboundDeviceHealthView toDeviceHealthView(
    String deviceId, String status, String source,
    int endpointCount, Instant readAt, List<ScNorthboundWarning> warnings) {
    return new NorthboundDeviceHealthView(deviceId, status, source, endpointCount, readAt, warnings);
}

static NorthboundDiagnosticsView toDiagnosticsView(
    String habitatId, String topologyVersion,
    NorthboundTemporalRuntimeStatusView temporalStatus,
    NorthboundMigrationReadinessView migrationReadiness,
    Instant readAt, List<ScNorthboundWarning> warnings) {
    return new NorthboundDiagnosticsView(habitatId, topologyVersion,
        temporalStatus, migrationReadiness, readAt, warnings);
}
```

These are optional — the facade can construct DTOs directly. Add mapper methods only
if it reduces duplication across tests.

---

## 8. Required test intents

The existing `NorthboundFacadeBehavioralTest` is Mockito-based and already provides helper methods such as `device()`, `deviceSnapshot()`, `endpoint()`, `validCreateRequest()` and a fixed `clock`. Prefer extending that style.

Do not introduce SQLite/Flyway fixtures into the Northbound behavioral tests unless the implementation proves Mockito cannot express the required authority check. The required evidence is method behavior and delegation to `CoreSnapshotQueryService`, not a new persistence integration test.

### T-1: deviceHealthDerivedHealthyWhenAllEndpointsHealthy

```java
@Test
void deviceHealthDerivedHealthyWhenAllEndpointsHealthy() {
    when(queryService.findDevice(HABITAT, DEVICE_ID)).thenReturn(Optional.of(deviceSnapshot()));
    when(queryService.findEndpointHealth(HABITAT, ENDPOINT_ID))
        .thenReturn(Optional.of(new EndpointHealth(HealthStatus.HEALTHY, NOW, "ok")));

    var response = facade.getDeviceHealth(HABITAT, DEVICE_ID);

    assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
    assertThat(response.payload().status()).isEqualTo("HEALTHY");
    assertThat(response.payload().source()).isEqualTo("DERIVED_FROM_ENDPOINTS");
    assertThat(response.payload().endpointCount()).isEqualTo(1);
    verify(queryService).findEndpointHealth(HABITAT, ENDPOINT_ID);
}
```

### T-2: deviceHealthDerivedOfflineWhenAnyEndpointOffline

Use a `DeviceSnapshot` whose `DeviceNode.endpointIds()` contains at least two endpoint IDs. Configure one endpoint as `HEALTHY` and another as `OFFLINE`. Assert payload status `OFFLINE`, source `DERIVED_FROM_ENDPOINTS`, and verify `findEndpointHealth(...)` calls. Do not use aggregate `EndpointSnapshot.health()`.

### T-3: deviceHealthDerivedDegradedWhenEndpointDegradedUnknownOrMissing

Cover at least one of:

```text
- endpoint health = DEGRADED -> device DEGRADED
- endpoint health = UNKNOWN -> device DEGRADED
- endpoint health row missing -> device DEGRADED
```

### T-4: deviceHealthEmptyEndpointSetReturnsUnknownPendingNormalization

Use a `DeviceSnapshot` whose `DeviceNode.endpointIds()` is empty. Assert:

```text
response.status() == UNKNOWN_PENDING_NORMALIZATION
payload.source() == UNKNOWN_PENDING_NORMALIZATION
payload.endpointCount() == 0
```

### T-5: createSignalTemporalActWithPastDueAtReturnsValidationError

Use the fixed `clock` from the existing test. Create a `NorthboundCreateSignalTemporalActRequest` with `dueAt = NOW.minusSeconds(3600)` and otherwise valid fields. Assert:

```text
status = VALIDATION_ERROR
error.code = INVALID_DUE_AT
error.source = northbound.validation
payload = null
```

### T-6: diagnosticsContainsMandatoryFields

Mock `queryService.findCurrentTopologyVersion(HABITAT)` and use the existing `engineHealth`. Assert:

```text
status = OK
topologyVersion is present or UNKNOWN according to mock setup
temporalEngineStatus is non-null
migrationReadiness.status = UNKNOWN_PENDING_NORMALIZATION
migrationReadiness.source = migration.readiness
readAt = NOW
warnings contains MIGRATION_READINESS_PENDING_NORMALIZATION with source migration.readiness
```

### T-7: errorAndWarningSourcesArePopulated

Exercise at least:

```text
getDevice(... missing canonical device ...) -> error.source = northbound.query
getEndpointRuntimeState(...) -> error.source = northbound.unsupported_profile
createSignalTemporalAct idempotent replay -> warning.source = temporal.application
```

### T-8: endpointRuntimeStateAndRecoveryRemainUnsupported

Preserve or add assertions that:

```text
getEndpointRuntimeState(...) -> UNSUPPORTED_PROFILE
getRecoveryStatus(...) -> UNSUPPORTED_PROFILE
```

---

## 9. Stop conditions

```text
1. NorthboundDeviceHealthView constructor call in getDeviceHealth uses
   DeviceNode.health() or EndpointSnapshot.health() — stop and revert.

2. getDiagnostics infers migrationReadiness = READY from Flyway bean or
   Spring context startup — stop and revert.

3. HealthStatus.UNHEALTHY reference appears in getDeviceHealth —
   stop: the enum has no UNHEALTHY value. Use OFFLINE as the unhealthy-equivalent.

4. toTemporalRuntimeStatusView is reimplemented instead of reusing NorthboundMapper —
   stop and use the existing mapper method (confirmed to exist at line 133).

5. getEndpointHealth body changes to call findEndpoint() instead of findEndpointHealth() —
   stop and revert. The invariant from MU-017 B-02 must be preserved.

6. Any of the 188 existing tests fail — stop and report before continuing.

7. VALIDATION_ERROR is added to the enum but no test exercises it —
   stop and add the dueAt-in-past test before declaring the implementation complete.
```
