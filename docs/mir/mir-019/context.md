# context.md — MIR-019 Northbound Facade Seed

```text
Document ID: CTX-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Version:     v0.2.2-reviewed
MU:          MU-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Slot:        MU-019
Baseline:    sovereign-connect-develop-csa.zip (168 tests / 0 failures)
```

---

## 0. How to use this file

Read completely before touching any file. Sections 4–5 give exact existing APIs.
Section 6 gives exact DTO record shapes. Section 7 gives the exact result→status
mapping (with pattern-match exhaustion). Section 8 gives the delegation table.
Section 9 gives representative test bodies. Section 10 gives stop conditions.

---

## 1. Architectural thesis

```text
SC-C owns canonical state and canonical request lifecycle.
Northbound Facade exposes SC-C-owned canonical surfaces.
EIB consumes the facade. Hub/SApp/Surfaces consume EIB, not SC-C directly.
SC-B routes and correlates cross-plane operations.
SC-D observes, translates and executes provider-level behavior.
```

MU-019 creates a clean in-process Java facade over existing SC-C services. No network API.

---

## 2. Scope

Profile A (topology observation) + selected Profile B (TemporalAct runtime requests).
No Profile C (discovery), no Profile D (EIB/effective interaction), no external transport.

---

## 3. Non-goals — do not implement

```text
HTTP controllers, Spring Web, SSE, MCP, gRPC, ConnectRPC, WebSocket, GraphQL
NATS, JetStream, SC-B runtime, SC-D adapter runtime
EIB, View Composer, Hub/SApp/Surface direct integration
Discovery E2E, DiscoveryCandidate admission, requestDiscovery(...)
ActionTemporalPayload, device command dispatch, outbox dispatcher
Flyway schema changes, SQLite repository redesign
DeviceHealth normalized persistence, endpoint runtime-state persistence
SemanticPayloadCodec extraction
```

---

## 4. Exact existing APIs — confirmed from codebase

### 4.1 CoreSnapshotQueryService (Spring bean)

```java
Optional<CoreSnapshot>              findCurrentSnapshot(String habitatId)
Optional<TopologyVersion>           findCurrentTopologyVersion(String habitatId)
Optional<DeviceSnapshot>            findDevice(String habitatId, String deviceId)
Optional<EndpointSnapshot>          findEndpoint(String habitatId, String endpointId)
Optional<Map<String,Object>>        findDeviceState(String habitatId, String deviceId)
Optional<EndpointHealth>            findEndpointHealth(String habitatId, String endpointId)
Optional<RoomNode>                  findRoom(String habitatId, String roomId)
Optional<ZoneNode>                  findZone(String habitatId, String zoneId)
Optional<TopologySpatialRelation>   findSpatialRelation(String habitatId, String relationId)
List<TopologySpatialRelation>       findSpatialRelationsBySubject(String habitatId,
                                        TopologySpatialEntityType type, String id)
List<DeviceNode>                    findLocatedDevices(String habitatId, String roomOrZoneId)
List<EndpointNode>                  findLocatedEndpoints(String habitatId, String roomOrZoneId)
Optional<TopologySpatialRelation>   resolvePrimaryPlacement(String habitatId,
                                        TopologySpatialEntityType type, String id)
```

**WARNING: getEndpointHealth MUST call `queryService.findEndpointHealth(...)` directly.**
Do NOT call `queryService.findEndpoint(...)` and use `result.health()` — that method
does `readPort.findEndpointHealth(...).orElse(endpoint.get().health())` which falls back
to the aggregate and violates the durable-only contract.

### 4.2 Existing snapshot result types (exact record shapes)

```java
// HabitatBaseTopology — for list derivation
// topology.rooms(), topology.zones(), topology.devices(),
// topology.endpoints(), topology.spatialRelations() are immutable lists.

CoreSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,        // record(TopologyVersionScope scope, String value)
    HabitatBaseTopology topology,
    Map<String, Map<String, Object>> deviceStates,   // keyed by deviceId
    Map<String, EndpointHealth> endpointHealth,      // keyed by endpointId
    Instant readAt
)

DeviceSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    DeviceNode device,
    Map<String, Object> state,
    Instant readAt
)

EndpointSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    EndpointNode endpoint,
    EndpointHealth health,                           // may contain durable value or aggregate fallback
    Instant readAt
)

EndpointHealth(HealthStatus status, Instant lastSeenAt, String details)

// IMPORTANT: EndpointSnapshot.health() MUST NOT be used as durable health evidence.
// CoreSnapshotQueryService.findEndpoint(...) currently falls back to aggregate endpoint.health()
// when no durable endpoint_health row exists. getEndpointHealth MUST use findEndpointHealth(...) directly.

// HealthStatus enum — exact values (NOTE: NO "UNHEALTHY" value):
HEALTHY, DEGRADED, OFFLINE, UNKNOWN
```

**CRITICAL:** `HealthStatus` has no `UNHEALTHY` value. The SDD derivation rule mentions
`UNHEALTHY` — this is a vocabulary mismatch. For this reason, Strategy A (DERIVED_FROM_ENDPOINTS)
is blocked. Device health MUST return `UNKNOWN_PENDING_NORMALIZATION` only.

### 4.3 TemporalActApplicationPort (Spring bean)

```java
CreateSignalTemporalActResult createSignalTemporalAct(CreateSignalTemporalActRequest request)
CancelTemporalActResult cancelTemporalAct(CancelTemporalActRequest request)
```

**CreateSignalTemporalActRequest (exact fields):**
```java
String habitatId, Instant dueAt, String label, String signalKind,
String notificationTargetRef, String createdByRef,
String idempotencyKey, Instant requestedAt
```

**CancelTemporalActRequest (exact fields):**
```java
String habitatId, String temporalActId, String requestedByRef,
String idempotencyKey, String reason, Instant requestedAt
```

**CreateSignalTemporalActResult (sealed interface, all variants):**
```java
Accepted(TemporalActObservation temporalAct)
IdempotentReplay(TemporalActObservation act)
Rejected(TemporalRequestRejection rejection)
Failed(TemporalRuntimeFailure failure)
```

**CancelTemporalActResult (sealed interface, all variants):**
```java
Cancelled(TemporalActObservation temporalAct)
AlreadyTerminal(TemporalActObservation act)
NotFound(String habitatId, String id)
IdempotentReplay(TemporalActObservation act)
Rejected(TemporalRequestRejection rejection)
Failed(TemporalRuntimeFailure failure)
```

### 4.4 TemporalActObservationPort (Spring bean)

```java
List<TemporalActObservation> listActive(String habitatId)
Optional<TemporalActObservation> findById(String habitatId, String temporalActId)
List<TemporalActObservation> listTerminal(String habitatId, int maxResults)
List<TemporalActObservation> listMisfired(String habitatId, int maxResults)
```

**TemporalActObservation (exact 14 fields):**
```java
String temporalActId, String habitatId, TemporalActStatus status, Instant dueAt,
String payloadKind, String label, String signalKind, String notificationTargetRef,
String createdByRef, Instant createdAt, Instant updatedAt,
Instant firedAt, Instant terminalAt, String terminalReason
```

**TemporalActStatus enum (exact values):**
```
PENDING, ARMED, FIRED, CANCELLED, EXPIRED, MISFIRED, FAILED
```

### 4.5 TemporalEngineHealth (Spring bean)

```java
TemporalEngineStatus status()   // STOPPED, STARTING, RECOVERING, RUNNING, DEGRADED, FAILED, DISABLED
boolean isReady()
Instant lastPollAt()
Instant lastSuccessfulPollAt()
RuntimeException lastFailure()
long firedTotal(), misfiredTotal(), cancelledTotal(), failedTotal(), skippedTotal()
```

---

## 5. Delegation table

| Facade method | Delegates to |
|---|---|
| `getTopologySnapshot(habitatId)` | `queryService.findCurrentSnapshot(habitatId)` |
| `getTopologyVersion(habitatId)` | `queryService.findCurrentTopologyVersion(habitatId)` |
| `getDevice(habitatId, deviceId)` | `queryService.findDevice(habitatId, deviceId)` |
| `getEndpoint(habitatId, endpointId)` | `queryService.findEndpoint(habitatId, endpointId)` |
| `listRooms(habitatId)` | `queryService.findCurrentSnapshot(habitatId)` → `.topology().rooms()` |
| `listZones(habitatId)` | `queryService.findCurrentSnapshot(habitatId)` → `.topology().zones()` |
| `listDevices(habitatId)` | `queryService.findCurrentSnapshot(habitatId)` → `.topology().devices()` |
| `listEndpoints(habitatId)` | `queryService.findCurrentSnapshot(habitatId)` → `.topology().endpoints()` |
| `listDevicesLocatedIn(habitatId, id)` | `queryService.findLocatedDevices(habitatId, id)` |
| `listEndpointsLocatedIn(habitatId, id)` | `queryService.findLocatedEndpoints(habitatId, id)` |
| `getEndpointHealth(habitatId, endpointId)` | `queryService.findEndpointHealth(habitatId, endpointId)` ← direct call only |
| `getDeviceHealth(habitatId, deviceId)` | always `UNKNOWN_PENDING_NORMALIZATION` |
| `getDeviceRuntimeState(habitatId, deviceId)` | `queryService.findDeviceState(habitatId, deviceId)` |
| `getEndpointRuntimeState(habitatId, endpointId)` | `UNSUPPORTED_PROFILE` |
| `getTemporalRuntimeStatus(habitatId)` | `engineHealth.status()` + counters |
| `getRecoveryStatus(habitatId)` | `engineHealth.status()` proxy or `UNSUPPORTED_PROFILE` |
| `createSignalTemporalAct(habitatId, request)` | `temporalActApplicationPort.createSignalTemporalAct(...)` |
| `cancelTemporalAct(habitatId, request)` | `temporalActApplicationPort.cancelTemporalAct(...)` |
| `getTemporalAct(habitatId, temporalActId)` | `temporalActObservationPort.findById(...)` |
| `listTemporalActs(habitatId, filter)` | see §4.4 observation port methods |

---

## 6. Exact DTO record shapes

### 6.1 ScNorthboundResponse

```java
public record ScNorthboundResponse<T>(
    ScNorthboundStatus status,
    T payload,                       // null when status is error/deferred
    List<ScNorthboundWarning> warnings,
    ScNorthboundError error          // null when status is OK/ACCEPTED/etc.
) {
    // factory helpers:
    static <T> ScNorthboundResponse<T> ok(T payload) {
        return new ScNorthboundResponse<>(ScNorthboundStatus.OK, payload, List.of(), null);
    }
    static <T> ScNorthboundResponse<T> notFound(String message) {
        return new ScNorthboundResponse<>(ScNorthboundStatus.NOT_FOUND, null, List.of(),
            new ScNorthboundError("NOT_FOUND", message));
    }
    static <T> ScNorthboundResponse<T> unsupportedProfile(String message) {
        return new ScNorthboundResponse<>(ScNorthboundStatus.UNSUPPORTED_PROFILE, null, List.of(),
            new ScNorthboundError("UNSUPPORTED_PROFILE", message));
    }
    static <T> ScNorthboundResponse<T> unknownPendingNormalization(String message) {
        return new ScNorthboundResponse<>(ScNorthboundStatus.UNKNOWN_PENDING_NORMALIZATION, null,
            List.of(), new ScNorthboundError("UNKNOWN_PENDING_NORMALIZATION", message));
    }
}
```

### 6.2 NorthboundTopologySnapshot

```java
public record NorthboundTopologySnapshot(
    String habitatId,
    String topologyVersionValue,    // TopologyVersion.value()
    String topologyVersionScope,    // TopologyVersion.scope().habitatId() or similar
    List<NorthboundRoomView> rooms,
    List<NorthboundZoneView> zones,
    List<NorthboundDeviceView> devices,
    List<NorthboundEndpointView> endpoints,
    Instant readAt
) {}
```

### 6.3 NorthboundDeviceView

```java
public record NorthboundDeviceView(
    String deviceId,            // canonical
    String alias,
    String displayName,
    String roomId,
    String zoneId,
    String kind,                // DeviceKind.name()
    String provider,            // DeviceProvider.name()
    List<String> endpointIds,
    List<NorthboundCapabilityView> capabilities,
    String providerDeviceId     // ProviderDeviceRef.providerDeviceId() — metadata, not canonical key
    // NOTE: no health field in first seed — health is UNKNOWN_PENDING_NORMALIZATION
) {}
```

### 6.4 NorthboundEndpointView

```java
public record NorthboundEndpointView(
    String endpointId,          // canonical
    String deviceId,
    String alias,
    String displayName,
    String kind,                // EndpointKind.name()
    String roomId,
    String zoneId,
    List<NorthboundCapabilityView> capabilities,
    String providerEndpointId   // ProviderEndpointRef.providerEndpointId() — metadata only
    // NOTE: health is separate — fetched via getEndpointHealth(...), not embedded here
) {}
```

### 6.5 NorthboundEndpointHealthView

```java
public record NorthboundEndpointHealthView(
    String endpointId,
    String status,              // HealthStatus.name(): HEALTHY, DEGRADED, OFFLINE, UNKNOWN
    Instant lastSeenAt,         // nullable
    String details              // nullable
) {}
```

### 6.6 NorthboundTemporalRuntimeStatusView

```java
public record NorthboundTemporalRuntimeStatusView(
    String habitatId,
    String engineStatus,        // TemporalEngineStatus.name()
    boolean isReady,
    Instant lastPollAt,         // nullable
    Instant lastSuccessfulPollAt,  // nullable
    long firedTotal,
    long misfiredTotal,
    long cancelledTotal,
    long failedTotal,
    long skippedTotal
) {}
```

### 6.7 NorthboundTemporalActView

```java
public record NorthboundTemporalActView(
    String temporalActId,
    String habitatId,
    String status,              // TemporalActStatus.name()
    Instant dueAt,
    String payloadKind,
    String label,               // nullable
    String signalKind,          // nullable
    String notificationTargetRef, // nullable
    String createdByRef,
    Instant createdAt,
    Instant updatedAt,
    Instant firedAt,            // nullable
    Instant terminalAt,         // nullable
    String terminalReason       // nullable
) {}
```

### 6.8 NorthboundCreateSignalTemporalActRequest

```java
public record NorthboundCreateSignalTemporalActRequest(
    Instant dueAt,
    String label,
    String signalKind,
    String notificationTargetRef,   // nullable
    String createdByRef,
    String idempotencyKey           // required — facade rejects null with INVALID_REQUEST
) {}
```

Maps to `CreateSignalTemporalActRequest` as:
```java
new CreateSignalTemporalActRequest(
    habitatId,               // from facade method param
    request.dueAt(),
    request.label(),
    request.signalKind(),
    request.notificationTargetRef(),
    request.createdByRef(),
    request.idempotencyKey(),
    Instant.now(clock)       // requestedAt — requires Clock injection
)
```

---

## 7. Exact result-to-status mapping

### 7.1 createSignalTemporalAct

Use pattern matching with exhaustive switch:

```java
return switch (result) {
    case CreateSignalTemporalActResult.Accepted a ->
        ScNorthboundResponse.of(ScNorthboundStatus.ACCEPTED,
            NorthboundMapper.toTemporalActView(a.temporalAct()), List.of(), null);

    case CreateSignalTemporalActResult.IdempotentReplay r ->
        ScNorthboundResponse.of(ScNorthboundStatus.ACCEPTED,
            NorthboundMapper.toTemporalActView(r.act()),
            List.of(new ScNorthboundWarning("IDEMPOTENT_REPLAY", "Request already processed")),
            null);

    case CreateSignalTemporalActResult.Rejected rej ->
        ScNorthboundResponse.of(ScNorthboundStatus.INVALID_REQUEST, null, List.of(),
            new ScNorthboundError("REJECTED", rej.rejection().code().name()));

    case CreateSignalTemporalActResult.Failed f ->
        ScNorthboundResponse.of(ScNorthboundStatus.INTERNAL_ERROR, null, List.of(),
            new ScNorthboundError("TEMPORAL_ENGINE_FAILURE", f.failure().message()));
};
```

### 7.2 cancelTemporalAct

```java
return switch (result) {
    case CancelTemporalActResult.Cancelled c ->
        ScNorthboundResponse.of(ScNorthboundStatus.CANCELLED,
            NorthboundMapper.toTemporalActView(c.temporalAct()), List.of(), null);

    case CancelTemporalActResult.AlreadyTerminal t ->
        ScNorthboundResponse.of(ScNorthboundStatus.OK,
            NorthboundMapper.toTemporalActView(t.act()),
            List.of(new ScNorthboundWarning("ALREADY_TERMINAL", "Act was already in terminal state")),
            null);

    case CancelTemporalActResult.NotFound nf ->
        ScNorthboundResponse.notFound("temporal act not found: " + nf.id());

    case CancelTemporalActResult.IdempotentReplay r ->
        ScNorthboundResponse.of(ScNorthboundStatus.CANCELLED,
            NorthboundMapper.toTemporalActView(r.act()),
            List.of(new ScNorthboundWarning("IDEMPOTENT_REPLAY", "Cancel already recorded")),
            null);

    case CancelTemporalActResult.Rejected rej ->
        ScNorthboundResponse.of(ScNorthboundStatus.INVALID_REQUEST, null, List.of(),
            new ScNorthboundError("REJECTED", rej.rejection().code().name()));

    case CancelTemporalActResult.Failed f ->
        ScNorthboundResponse.of(ScNorthboundStatus.INTERNAL_ERROR, null, List.of(),
            new ScNorthboundError("TEMPORAL_ENGINE_FAILURE", f.failure().message()));
};
```

**Why exhaustive pattern matching:** Both result types are sealed interfaces. The Java compiler
enforces exhaustion — if a new variant is added in a future MU, this code fails to compile,
making the gap visible immediately.

---

## 8. Spring wiring

`DefaultScCoreNorthboundFacade` as `@Service`:

```java
@Service
public class DefaultScCoreNorthboundFacade implements ScCoreNorthboundFacade {

    private final CoreSnapshotQueryService queryService;
    private final TemporalActApplicationPort temporalActApplicationPort;
    private final TemporalActObservationPort temporalActObservationPort;
    private final TemporalEngineHealth engineHealth;
    private final Clock clock;

    public DefaultScCoreNorthboundFacade(
        CoreSnapshotQueryService queryService,
        TemporalActApplicationPort temporalActApplicationPort,
        TemporalActObservationPort temporalActObservationPort,
        TemporalEngineHealth engineHealth,
        Clock clock
    ) { ... }
}
```

Do NOT inject: `SQLiteBaseTopologyRepository`, `SQLiteEndpointHealthRepository`,
`BaseTopologyService`, `CoreSnapshotReadPort`, `JdbcTemplate`, `DataSource`,
`ObjectMapper`, `TransactionTemplate`.

---

## 9. Required test bodies

### T-1: endpointHealthReturnsFromDurableSourceNotAggregateHealth

```java
@Test
void endpointHealthReturnsFromDurableSourceNotAggregateHealth(@TempDir Path tmp) {
    DataSource ds = sqliteDataSource(tmp.resolve("facade-ep-health.sqlite").toString());
    // Build topology with endpoint health = HEALTHY in aggregate
    // ... save topology via SQLiteBaseTopologyRepository ...
    // Then overwrite endpoint_health with DEGRADED via SQLiteEndpointHealthRepository
    healthRepo.saveEndpointHealth("habitat-001", endpointId,
        new EndpointHealth(HealthStatus.DEGRADED, Instant.now(), "from direct write"));

    ScCoreNorthboundFacade facade = new DefaultScCoreNorthboundFacade(
        queryService, temporalPort, observationPort, engineHealth, clock);

    var response = facade.getEndpointHealth("habitat-001", endpointId);
    assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
    // Must return DEGRADED from durable table, NOT the HEALTHY from aggregate
    assertThat(response.payload().status()).isEqualTo("DEGRADED");
}
```

### T-2: deviceHealthAlwaysReturnsUnknownPendingNormalization

```java
@Test
void deviceHealthAlwaysReturnsUnknownPendingNormalization() {
    // Works with any mock or real facade — device health is unconditional
    ScCoreNorthboundFacade facade = new DefaultScCoreNorthboundFacade(
        queryService, temporalPort, observationPort, engineHealth, clock);

    var response = facade.getDeviceHealth("habitat-001", "device.tuya.light-1");
    assertThat(response.status()).isEqualTo(ScNorthboundStatus.UNKNOWN_PENDING_NORMALIZATION);
    assertThat(response.payload()).isNull();
}
```

### T-3: createSignalTemporalActResultMappingIsExhaustive

Operational note T-3 rename: previous package drafts used `createSignalTemporalActMapsCancelledVariantsCorrectly` due to copy-paste from cancelTemporalAct coverage. The implementation MUST use `createSignalTemporalActMapsAllResultVariantsCorrectly` because the body verifies CreateSignalTemporalActResult variants, not cancel variants. The implementation report MUST record this rename for traceability.

```java
@Test
void createSignalTemporalActMapsAllResultVariantsCorrectly() {
    // Verify all 4 CreateSignalTemporalActResult variants are handled:
    // Accepted → ACCEPTED with payload
    // IdempotentReplay → ACCEPTED with IDEMPOTENT_REPLAY warning
    // Rejected → INVALID_REQUEST with error
    // Failed → INTERNAL_ERROR with error

    // For Accepted:
    TemporalActObservation obs = buildObservation(TemporalActStatus.PENDING);
    when(temporalPort.createSignalTemporalAct(any()))
        .thenReturn(new CreateSignalTemporalActResult.Accepted(obs));
    var r = facade.createSignalTemporalAct("habitat-001", validRequest());
    assertThat(r.status()).isEqualTo(ScNorthboundStatus.ACCEPTED);
    assertThat(r.payload()).isNotNull();
    assertThat(r.warnings()).isEmpty();

    // For IdempotentReplay:
    when(temporalPort.createSignalTemporalAct(any()))
        .thenReturn(new CreateSignalTemporalActResult.IdempotentReplay(obs));
    var r2 = facade.createSignalTemporalAct("habitat-001", validRequest());
    assertThat(r2.status()).isEqualTo(ScNorthboundStatus.ACCEPTED);
    assertThat(r2.warnings()).anyMatch(w -> w.code().equals("IDEMPOTENT_REPLAY"));

    // For Rejected:
    when(temporalPort.createSignalTemporalAct(any()))
        .thenReturn(new CreateSignalTemporalActResult.Rejected(
            new TemporalRequestRejection(TemporalRequestRejectionCode.IDEMPOTENCY_CONFLICT, "conflict")));
    var r3 = facade.createSignalTemporalAct("habitat-001", validRequest());
    assertThat(r3.status()).isEqualTo(ScNorthboundStatus.INVALID_REQUEST);
}
```

### T-4: northboundPackageHasNoForbiddenImports (architecture test)

```java
@Test
void northboundPackageHasNoForbiddenImports() throws IOException {
    Path northbound = Path.of("src/main/java/com/sovereign/connect/core/northbound");
    List<String> forbidden = List.of(
        "springframework.web", "springframework.stereotype.Controller",
        "RestController", "RequestMapping", "GetMapping", "PostMapping",
        "graphql", "io.grpc", "nats", "jetstream", "websocket",
        "mcp", "javax.sql.DataSource", "JdbcTemplate",
        "adapter.persistence", "adapter.discovery",
        "H2", "SQLite", "Flyway",
        "MaterializationDecision",   // discovery fact classes
        "DeviceDiscoveryFact", "EndpointDiscoveryFact"
    );
    if (!Files.exists(northbound)) return;
    try (Stream<Path> paths = Files.walk(northbound)) {
        paths.filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> {
                try {
                    String content = Files.readString(p);
                    forbidden.forEach(banned ->
                        assertThat(content)
                            .as("northbound file %s must not contain %s", p.getFileName(), banned)
                            .doesNotContain(banned));
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
    }
}
```

---

## 10. Stop conditions

```text
1. HealthStatus.UNHEALTHY does not exist in the enum (confirmed: only HEALTHY, DEGRADED,
   OFFLINE, UNKNOWN). Do not invent it. Device health = UNKNOWN_PENDING_NORMALIZATION always.

2. getEndpointHealth calls findEndpoint(...) instead of findEndpointHealth(...) —
   stop and revert. findEndpoint uses aggregate fallback which violates the durable-only contract.

3. NorthboundMapper imports any class from:
   adapter.persistence, DeviceDiscoveryFact, EndpointDiscoveryFact, MaterializationDecision —
   stop and remove the import.

4. ScNorthboundResponse is confused with ScResponseEnvelope from SC-B (which does not exist
   yet but is declared in ADR-SC-BUS-TECH-001) — keep them separate, no import relationship.

5. mvn test fails after any phase — stop and report before continuing.

6. createSignalTemporalAct or cancelTemporalAct switch misses a variant and falls through
   to a default branch — remove the default and let the compiler enforce exhaustion.
```
