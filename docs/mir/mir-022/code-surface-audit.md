# CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001

## Post-SDD Code Surface Audit — EIB Initial Implementation Slice

```text
Document ID:  CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001
Title:        Post-SDD Code Surface Audit — EIB Initial Implementation Slice
Version:      v0.1.1-merged
Status:       Draft / Post-SDD / Pre-MIR / Merged Audit
Date:         2026-05-26
Corpus:       Sovereign Connect
Plane:        SC-X / EIB
Scope:        Code surface reconnaissance for the EIB initial implementation slice,
              before MIR authorization
Baseline:     sovereign-connect-021.zip / sovereign-connect-csa-pre-022.zip
Branch:       feat/sc-c-mir-021-northbound-http-sse-binding
Input PDR:    PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.3.0-candidate
Input SDD:    SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.2.0-candidate
Result:       Approvable to open MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001,
              provided the MIR preserves non-co-located runtime semantics
Merge basis:  CSA v0.1.0 generated audit + reviewer CSA report
```

---

## 0. Purpose

This is the required post-SDD / pre-MIR Code Surface Audit for
`SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.2.0-candidate`.

It resolves the fourteen CSA obligations declared by the SDD and merges two audit
inputs:

1. the initial CSA generated for EIB after SDD acceptance; and
2. the reviewer CSA report that inspected the post-MU-021 baseline and contributed
   concrete route, DTO, test and implementation-risk details.

This CSA does **not** authorize implementation. The implementation gate remains:

```text
MIR -> execution package -> implementation report -> validation evidence
```

---

## 1. Executive verdict

```text
Verdict: Approvable to open MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001.
```

The baseline is ready for MIR descent because:

```text
- SC-C Northbound HTTP/OpenAPI binding exists and is L4-validated.
- SC-C exposes the HTTP routes required by the EIB initial implementation slice.
- EIB is greenfield: no existing EIB package, DTOs, services, controllers or tests exist.
- No legacy EIB code must be migrated or preserved.
- RestClient, MockRestServiceServer, Jackson JavaTime support and Spring MVC are already available.
- The implementation risk is mostly runtime placement, mirror DTO correctness, envelope preservation,
  provider-native ID suppression and architecture-test updates.
```

### 1.1 Normative merge resolution — runtime placement

The two CSA inputs disagree on placement:

```text
Input A:
  EIB MUST be non-co-located with SC-C and should not enter the existing SC-C Spring application context.

Input B:
  EIB may be implemented under com.sovereign.connect.adapter.eib in the same process/application context.
```

Normative resolution:

```text
Same repository is allowed.
Same SC-C runtime application context is not allowed.
```

Rationale:

```text
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.3.0-candidate and
SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.2.0-candidate define EIB
as a non-co-located consumer of SC-C Northbound HTTP/OpenAPI.

Therefore, EIB MUST NOT be materialized as ordinary controllers/services inside
`SovereignConnectApplication` or the existing SC-C Spring component scan.
```

Accepted placement forms:

```text
A. top-level `eib/` standalone Maven project in the same repository; or
B. separate Maven module with its own Spring Boot application; or
C. separate Spring Boot application package outside the SC-C component scan,
   with tests proving that `SovereignConnectApplication` does not instantiate EIB beans.
```

CSA recommendation:

```text
Option A — top-level eib/ standalone Maven project.
```

Rejected for this MIR unless governance explicitly overrides it:

```text
com.sovereign.connect.adapter.eib inside the existing SC-C application context.
```

The reviewer CSA's detailed technical findings are adopted below, but its same-process
placement recommendation is not adopted because it conflicts with the accepted PDR/SDD
boundary.

---

## 2. Completeness envelope

### 2.1 Completeness level

```text
CSA-C3 — Change-impact-complete
```

The CSA is complete relative to the inspected code surface and resolves the SDD CSA
obligations sufficiently for MIR opening.

### 2.2 Inspected baseline

```text
ZIP:         sovereign-connect-021.zip / sovereign-connect-csa-pre-022.zip
Branch:      feat/sc-c-mir-021-northbound-http-sse-binding
HEAD:        10325cd docs(mir-021): record evidence commit
Impl commit: 59828db feat(sc-c): add northbound http sse binding seed
Evidence:    docs/mir/mir-021/
Tests:       240 / 0 failures / 0 errors / 0 skipped from included Surefire reports
```

### 2.3 Inspected source paths

```text
pom.xml
src/main/resources/application.yml
src/main/java/com/sovereign/connect/SovereignConnectApplication.java
src/main/java/com/sovereign/connect/adapter/northbound/http/**
src/main/java/com/sovereign/connect/config/TemporalEngineConfiguration.java
src/main/java/com/sovereign/connect/core/northbound/**
src/main/java/com/sovereign/connect/core/northbound/topology/**
src/main/java/com/sovereign/connect/core/northbound/runtime/**
src/main/java/com/sovereign/connect/core/northbound/temporal/**
src/main/java/com/sovereign/connect/core/temporal/engine/TemporalEngineProperties.java
src/main/java/com/sovereign/connect/core/temporal/application/TemporalActApplicationService.java
src/test/java/com/sovereign/connect/adapter/northbound/http/**
src/test/java/com/sovereign/connect/core/northbound/**
target/surefire-reports/**
```

### 2.4 Explicitly searched but absent

```text
src/main/java/**/eib/**
src/test/java/**/eib/**
EffectiveInteractionBoundary
EffectiveHabitatView
EffectiveRoomView
EffectiveZoneView
EffectiveDeviceView
EffectiveEndpointView
EffectiveTemporalActView
EibResponse
EibScNorthboundClient
InteractionAdmissionDecision
CanonicalSubmissionTrace
EffectiveActionHandle
EffectiveRefCodec
```

### 2.5 Audit limitations

```text
mvn is unavailable in the audit environment.
No fresh test execution was performed by this CSA.
Included Surefire reports are used as validation evidence for the baseline.
Runtime behavior of RestClient / MockRestServiceServer is inferred from Spring Boot 3.3.x / Spring 6.1 source-level availability and must be verified by implementation tests.
```

---

## 3. Evidence snapshot

### 3.1 Current dependency surface

Confirmed in the post-MU-021 baseline:

```text
Java version:              21
Spring Boot version:       3.3.5
Spring Framework:          6.1.x via Spring Boot BOM

PRESENT:
  spring-boot-starter
  spring-boot-starter-jdbc
  spring-boot-starter-web
  springdoc-openapi-starter-webmvc-api 2.5.0
  spring-boot-starter-test
  jackson-datatype-jsr310
  jackson-annotations
  sqlite-jdbc
  flyway-core
  flyway-database-nc-sqlite
  h2 test-scope

AVAILABLE THROUGH EXISTING DEPENDENCIES:
  RestClient via spring-web 6.1+
  MockRestServiceServer via spring-test

ABSENT:
  WireMock
  spring-cloud-contract-wiremock
  spring-boot-starter-webflux
  spring-security
  gRPC / ConnectRPC
  MCP
  GraphQL
  WebSocket
  NATS / JetStream
```

### 3.2 Existing EIB code surface

```text
Result: GREENFIELD.
```

No EIB production package, test package, DTO, service, controller, client, mapper,
configuration or properties class exists in the supplied baseline.

### 3.3 Existing SC-C HTTP exposure

SC-C HTTP adapter package exists:

```text
com.sovereign.connect.adapter.northbound.http
```

Production classes:

```text
ScNorthboundTopologyHttpController
ScNorthboundTemporalHttpController
ScNorthboundHttpResponseMapper
ScNorthboundHttpConfiguration
ScNorthboundHttpProperties
ScNorthboundCancelTemporalActHttpBody
```

Configuration:

```yaml
sc:
  northbound:
    http:
      enabled: true
      base-path: /sc/v1
      exposure-profile: local-trusted
```

Note:

```text
base-path is recorded but routes are statically mapped in MU-021.
```

### 3.4 SC-C HTTP routes confirmed from MU-021

Base path:

```text
/sc/v1/habitats/{habitatId}
```

Profile A — topology and observation routes:

```text
GET /topology                             -> getTopologySnapshot(habitatId)
GET /topology/version                     -> getTopologyVersion(habitatId)
GET /rooms                                -> listRooms(habitatId)
GET /zones                                -> listZones(habitatId)
GET /devices                              -> listDevices(habitatId)
GET /devices/{deviceId}                   -> getDevice(habitatId, deviceId)
GET /devices/{deviceId}/health            -> getDeviceHealth(habitatId, deviceId)
GET /devices/{deviceId}/runtime-state     -> getDeviceRuntimeState(habitatId, deviceId)
GET /endpoints                            -> listEndpoints(habitatId)
GET /endpoints/{endpointId}               -> getEndpoint(habitatId, endpointId)
GET /endpoints/{endpointId}/health        -> getEndpointHealth(habitatId, endpointId)
GET /endpoints/{endpointId}/runtime-state -> getEndpointRuntimeState(habitatId, endpointId)
GET /locations/{roomOrZoneId}/devices     -> listDevicesLocatedIn(habitatId, roomOrZoneId)
GET /locations/{roomOrZoneId}/endpoints   -> listEndpointsLocatedIn(habitatId, roomOrZoneId)
```

Profile B — temporal and diagnostics routes:

```text
GET  /temporal/runtime-status              -> getTemporalRuntimeStatus(habitatId)
GET  /recovery/status                      -> getRecoveryStatus(habitatId)
GET  /diagnostics                          -> getNorthboundDiagnostics(habitatId)
GET  /temporal-acts/{temporalActId}        -> getTemporalAct(habitatId, temporalActId)
POST /temporal-acts                        -> createSignalTemporalAct(habitatId, request)
POST /temporal-acts/{temporalActId}/cancel -> cancelTemporalAct(habitatId, constructed request)
GET  /temporal-acts?mode=&maxResults=      -> listTemporalActs(habitatId, filter)
```

### 3.5 Routes consumed by the EIB initial implementation slice

Required:

```text
GET  /sc/v1/habitats/{habitatId}/topology
GET  /sc/v1/habitats/{habitatId}/topology/version
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/health
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/health
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/diagnostics
GET  /sc/v1/habitats/{habitatId}/temporal-acts?mode=ACTIVE&maxResults=50
GET  /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}
POST /sc/v1/habitats/{habitatId}/temporal-acts
POST /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
```

Available but not required for the first implementation slice:

```text
GET /rooms
GET /zones
GET /locations/{roomOrZoneId}/devices
GET /locations/{roomOrZoneId}/endpoints
GET /temporal/runtime-status
GET /recovery/status
```

Reason:

```text
EffectiveHabitatView derives rooms/zones/devices/endpoints from GET /topology.
Diagnostics covers temporal engine status and migration readiness for initial diagnostics.
```

---

## 4. Northbound wire shapes — field inventory

The EIB initial implementation slice must use EIB-owned mirror DTOs for these upstream wire shapes.

### 4.1 ScEnvelope

SC-C returns `ScNorthboundResponse<T>` as the HTTP body. EIB mirrors it as:

```java
record ScEnvelope<T>(
    String status,
    T payload,
    List<ScWarningDto> warnings,
    ScErrorDto error
) {}
```

EIB MUST preserve all 12 `ScNorthboundStatus` values internally.

### 4.2 Shared error/warning DTOs

```java
record ScErrorDto(String code, String message, String source) {}
record ScWarningDto(String code, String message, String source) {}
```

### 4.3 Topology DTOs

```java
record NorthboundTopologySnapshotDto(
    String habitatId,
    String topologyVersionValue,
    String topologyVersionScope,
    List<NorthboundRoomViewDto> rooms,
    List<NorthboundZoneViewDto> zones,
    List<NorthboundDeviceViewDto> devices,
    List<NorthboundEndpointViewDto> endpoints,
    Instant readAt
) {}

record NorthboundTopologyVersionViewDto(
    String habitatId,
    String value,
    String scopeType,
    String scopeId
) {}

record NorthboundRoomViewDto(
    String roomId,
    String roomName,
    List<String> zoneIds,
    List<String> deviceIds,
    List<String> endpointIds
) {}

record NorthboundZoneViewDto(
    String zoneId,
    String zoneName,
    String roomId,
    List<String> deviceIds,
    List<String> endpointIds
) {}
```

### 4.4 Device / endpoint DTOs

```java
record NorthboundDeviceViewDto(
    String deviceId,
    String alias,
    String displayName,
    String roomId,
    String zoneId,
    String kind,
    String provider,
    List<String> endpointIds,
    List<NorthboundCapabilityViewDto> capabilities,
    String providerDeviceId
) {}

record NorthboundEndpointViewDto(
    String endpointId,
    String deviceId,
    String alias,
    String displayName,
    String kind,
    String roomId,
    String zoneId,
    List<NorthboundCapabilityViewDto> capabilities,
    String providerEndpointId
) {}

record NorthboundCapabilityViewDto(
    String capabilityId,
    String name,
    String kind
) {}
```

Critical rule:

```text
providerDeviceId and providerEndpointId MUST be deserializable but MUST NOT be exposed in ordinary EffectiveDeviceView / EffectiveEndpointView responses.
```

### 4.5 Health / runtime DTOs

```java
record NorthboundDeviceHealthViewDto(
    String deviceId,
    String status,
    String source,
    int endpointCount,
    Instant readAt,
    List<ScWarningDto> warnings
) {}

record NorthboundEndpointHealthViewDto(
    String endpointId,
    String status,
    Instant lastSeenAt,
    String details
) {}

record NorthboundRuntimeStateViewDto(
    String habitatId,
    String subjectId,
    String subjectType,
    Map<String, Object> state,
    Instant readAt
) {}
```

### 4.6 Temporal DTOs

```java
record NorthboundTemporalActViewDto(
    String temporalActId,
    String habitatId,
    String status,
    Instant dueAt,
    String payloadKind,
    String label,
    String signalKind,
    String notificationTargetRef,
    String createdByRef,
    Instant createdAt,
    Instant updatedAt,
    Instant firedAt,
    Instant terminalAt,
    String terminalReason
) {}

record NorthboundCreateSignalTemporalActRequestDto(
    Instant dueAt,
    String label,
    String signalKind,
    String notificationTargetRef,
    String createdByRef,
    String idempotencyKey
) {}

record NorthboundCancelTemporalActRequestDto(
    String requestedByRef,
    String idempotencyKey,
    String reason
) {}
```

Note:

```text
For cancel, temporalActId belongs to the HTTP path, not the request body.
```

### 4.7 Diagnostics DTOs

```java
record NorthboundDiagnosticsViewDto(
    String habitatId,
    String topologyVersion,
    NorthboundTemporalRuntimeStatusViewDto temporalEngineStatus,
    NorthboundMigrationReadinessViewDto migrationReadiness,
    Instant readAt,
    List<ScWarningDto> warnings
) {}

record NorthboundTemporalRuntimeStatusViewDto(
    String habitatId,
    String engineStatus,
    boolean isReady,
    Instant lastPollAt,
    Instant lastSuccessfulPollAt,
    long firedTotal,
    long misfiredTotal,
    long cancelledTotal,
    long failedTotal,
    long skippedTotal
) {}

record NorthboundMigrationReadinessViewDto(
    String status,
    String source,
    String message
) {}
```

---

## 5. CSA obligation resolutions

### CSA-EIB-001 — Concrete codebase placement

Decision:

```text
EIB MUST be placed as a separate runtime boundary.
```

Preferred concrete placement:

```text
eib/
  pom.xml
  src/main/java/com/sovereign/eib/EibApplication.java
  src/main/java/com/sovereign/eib/**
  src/test/java/com/sovereign/eib/**
```

Required architectural properties:

```text
- EIB has its own Spring Boot application.
- EIB is not scanned by SovereignConnectApplication.
- EIB consumes SC-C only through configured HTTP base URL.
- EIB does not import com.sovereign.connect.core.* or com.sovereign.connect.adapter.northbound.http.* production classes.
```

Allowed repository topology:

```text
Monorepo with separate runtime/application boundary.
```

Disallowed:

```text
Adding EIB controllers directly under src/main/java/com/sovereign/connect/adapter/eib
if those beans are loaded by the existing SovereignConnectApplication context.
```

### CSA-EIB-002 — Existing EIB code surface

```text
No EIB code exists in the baseline. Implementation is greenfield.
```

No migration, no rename and no compatibility preservation are required.

### CSA-EIB-003 — Exact dependency patch

If the MIR adopts the preferred `eib/` standalone Maven project, the EIB module/project must declare its own minimal dependencies:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
  <groupId>com.fasterxml.jackson.datatype</groupId>
  <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>

<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

No runtime dependency is required for:

```text
WireMock
WebFlux
Spring Security
gRPC / ConnectRPC
MCP
GraphQL
WebSocket
NATS / JetStream
```

If the implementation instead uses a separate Maven module under a parent build,
the same dependency set applies within that module.

Required EIB properties:

```yaml
sc:
  eib:
    northbound:
      base-url: http://localhost:8080/sc/v1
      timeout-ms: 2000
    ref-codec:
      secret: change-me-in-production
    diagnostic:
      admin-enabled: false
```

For tests:

```yaml
sc:
  eib:
    northbound:
      base-url: http://sc-c-mock/sc/v1
      timeout-ms: 500
    ref-codec:
      secret: test-secret
    diagnostic:
      admin-enabled: true
```

### CSA-EIB-004 — Exact upstream SC-C HTTP routes

Resolved in §3.4 and §3.5.

The MIR/execution package MUST instruct Codex to consume these routes through
`EibScNorthboundClient` only.

### CSA-EIB-005 — Exact DTO strategy

Decision:

```text
EIB-owned mirror DTOs.
```

Rules:

```text
- EIB MUST NOT import SC-C Java DTOs.
- EIB MUST mirror the HTTP wire shape it consumes.
- Mirror DTOs may share field names with SC-C DTOs, but are distinct Java types.
- EIB internal/effective DTOs are separate from upstream mirror DTOs.
- providerDeviceId/providerEndpointId are deserialized but suppressed from ordinary product views.
```

Mandatory test:

```text
ordinary EffectiveDeviceView / EffectiveEndpointView serialization MUST NOT contain providerDeviceId/providerEndpointId values.
```

### CSA-EIB-006 — Exact EibScNorthboundClient strategy

Decision:

```text
RestClient-based synchronous upstream client.
```

Required interface:

```java
interface EibScNorthboundClient {
    ScEnvelope<NorthboundTopologySnapshotDto> getTopologySnapshot(String habitatId);
    ScEnvelope<NorthboundTopologyVersionViewDto> getTopologyVersion(String habitatId);
    ScEnvelope<NorthboundDeviceHealthViewDto> getDeviceHealth(String habitatId, String deviceId);
    ScEnvelope<NorthboundEndpointHealthViewDto> getEndpointHealth(String habitatId, String endpointId);
    ScEnvelope<NorthboundRuntimeStateViewDto> getDeviceRuntimeState(String habitatId, String deviceId);
    ScEnvelope<NorthboundRuntimeStateViewDto> getEndpointRuntimeState(String habitatId, String endpointId);
    ScEnvelope<NorthboundDiagnosticsViewDto> getDiagnostics(String habitatId);
    ScEnvelope<List<NorthboundTemporalActViewDto>> listTemporalActs(String habitatId, String mode, Integer maxResults);
    ScEnvelope<NorthboundTemporalActViewDto> getTemporalAct(String habitatId, String temporalActId);
    ScEnvelope<NorthboundTemporalActViewDto> createSignalTemporalAct(String habitatId, NorthboundCreateSignalTemporalActRequestDto request);
    ScEnvelope<NorthboundTemporalActViewDto> cancelTemporalAct(String habitatId, String temporalActId, NorthboundCancelTemporalActRequestDto request);
}
```

`getTopologyVersion` MUST return `ScEnvelope<NorthboundTopologyVersionViewDto>`, not `ScEnvelope<String>`.

### CSA-EIB-007 — Exact effective ref codec strategy

Decision:

```text
HMAC-SHA256 with configurable secret.
```

Reference algorithm:

```java
public final class EibEffectiveRefCodec {
    private final byte[] secret;

    public EibEffectiveRefCodec(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateRef(String type, String habitatId, String canonicalId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] hash = mac.doFinal((habitatId + ":" + type + ":" + canonicalId)
                .getBytes(StandardCharsets.UTF_8));
            return type + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hash)
                .substring(0, 22);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("EIB effective ref codec failure", e);
        }
    }
}
```

Ref families:

```text
eib.room.<token>
eib.zone.<token>
eib.device.<token>
eib.endpoint.<token>
eib.temporal.<token>
```

Because HMAC refs are not reversible, `getEffectiveTemporalAct(effectiveRef)` and
`cancelTemporalAct(effectiveRef)` require list+match. This is accepted for the
initial implementation slice and MUST be documented in the implementation report.

Retained debt:

```text
DEBT-EIB-012 — Persistent effective ref registry deferred.
```

### CSA-EIB-008 — Exact context/authorization profile

Decision:

```text
Permissive context stub with diagnostic/admin defaulting to false.
```

Seed request headers:

```text
X-SC-Context-Ref
X-SC-Actor-Ref
X-SC-Surface-Ref
X-SC-Locale
X-SC-Diagnostic-Mode
X-SC-Client-Request-Ref
```

Diagnostic/admin exposure requires both:

```text
X-SC-Diagnostic-Mode: true
sc.eib.diagnostic.admin-enabled=true
```

Rules:

```text
ordinary product views MUST NOT expose canonicalDeviceId, canonicalEndpointId or canonicalTemporalActId.
ordinary product views MUST NOT expose providerDeviceId or providerEndpointId.
diagnostic/admin mode MAY expose canonical IDs, never provider-native IDs as effective identifiers.
```

### CSA-EIB-009 — Architecture tests preventing direct SC-C imports

If EIB is implemented as preferred top-level `eib/` project:

```text
The SC-C architecture tests do not need to allow adapter/eib because no adapter/eib package is introduced in SC-C src/main/java.
```

Required EIB architecture tests:

```text
EibArchitectureTest.eibDoesNotImportScCoreInternals
EibArchitectureTest.eibDoesNotImportScCNorthboundHttpAdapterClasses
EibArchitectureTest.eibDoesNotIntroduceForbiddenTechnologies
EibArchitectureTest.eibDoesNotUseJdbcFlywayOrPersistenceAdapters
```

Forbidden tokens include:

```text
com.sovereign.connect.core.
com.sovereign.connect.adapter.northbound.http.
ScCoreNorthboundFacade
DefaultScCoreNorthboundFacade
JdbcTemplate
javax.sql.DataSource
Flyway
io.grpc
connectrpc
graphql
io.nats
JetStream
SseEmitter
Flux<
Mono<
```

If governance overrides placement and allows EIB under `com.sovereign.connect.adapter.eib`, then the following SC-C tests would require patching:

```text
ScNorthboundHttpArchitectureTest.webImportsForbiddenOutsideHttpAdapterPackage
NorthboundFacadeNegativeBoundaryTest.webImportsForbiddenOutsideHttpAdapterPackage
NorthboundFacadeSpringContextTest.httpControllerBeansAreOnlyInHttpAdapterPackage
```

That placement is not recommended by this merged CSA.

### CSA-EIB-010 — TemporalAct observation/admission route coverage

Confirmed SC-C routes:

```text
GET  /temporal-acts?mode=ACTIVE&maxResults=50
GET  /temporal-acts/{temporalActId}
POST /temporal-acts
POST /temporal-acts/{temporalActId}/cancel
```

EIB cancel flow:

```text
1. Receive effectiveTemporalActRef from EIB route path.
2. Resolve to canonical temporalActId by list+match.
3. POST to SC-C /temporal-acts/{canonicalTemporalActId}/cancel.
4. Body contains requestedByRef, idempotencyKey, reason.
5. temporalActId is never sent in the body.
```

EIB create flow:

```text
1. Receive admitTemporalSignalRequest.
2. Generate/forward idempotencyKey according to SDD.
3. POST to SC-C /temporal-acts.
4. Preserve ScEnvelope and status.
5. If SC-C returns ACCEPTED with NorthboundTemporalActViewDto payload, generate effectiveTemporalActRef from temporalActId.
6. Set InteractionAdmissionDecision.effectiveRef to that generated ref.
```

Inherited behavior:

```text
DEBT-HTTP-007 — ACTIVE mode ignores maxResults in SC-C facade.
EIB must not compensate in the initial implementation slice.
```

### CSA-EIB-011 — Canonical ID and provider-native ID gating tests

Mandatory tests:

```text
ordinaryDeviceViewDoesNotContainCanonicalDeviceId
ordinaryEndpointViewDoesNotContainCanonicalEndpointId
ordinaryTemporalActViewDoesNotContainCanonicalTemporalActId
diagnosticDeviceViewContainsCanonicalDeviceId
diagnosticEndpointViewContainsCanonicalEndpointId
diagnosticTemporalActViewContainsCanonicalTemporalActId
ordinaryDeviceViewDoesNotContainProviderDeviceId
ordinaryEndpointViewDoesNotContainProviderEndpointId
```

### CSA-EIB-012 — Response-envelope preservation tests

Mandatory tests:

```text
preservesAllTwelveScNorthboundStatusValues
preservesErrorSource
preservesWarningSources
unsupportedProfileMapsToDisabledNotSuccess
unknownPendingNormalizationMapsToPartialNotSuccess
internalErrorDoesNotLeakInternalsToProductClient
notFoundRemainsTraceableInternally
canonicalSubmissionTracePreservesScStatus
```

All 12 statuses:

```text
OK
CREATED
ACCEPTED
CANCELLED
NOT_FOUND
INVALID_REQUEST
INVALID_CANONICAL_ID
VALIDATION_ERROR
UNSUPPORTED_PROFILE
DEFERRED_SC_B_REQUIRED
UNKNOWN_PENDING_NORMALIZATION
INTERNAL_ERROR
```

### CSA-EIB-013 — Network failure / upstream unavailable test strategy

Decision:

```text
MockRestServiceServer.
```

Required cases:

```text
connection refused / IOException -> EibUpstreamUnavailableException or UPSTREAM_UNAVAILABLE equivalent
non-JSON body -> UPSTREAM_UNAVAILABLE equivalent
HTTP 503 with valid ScEnvelope(status=DEFERRED_SC_B_REQUIRED) -> semantic SC-C response, not transport failure
HTTP 503 with no valid ScEnvelope body -> transport/upstream unavailable
malformed JSON -> UPSTREAM_UNAVAILABLE equivalent
```

Important disambiguation:

```text
SC-C uses HTTP 503 for DEFERRED_SC_B_REQUIRED.
EIB MUST distinguish semantic 503 with valid ScEnvelope from transport failure 503.
```

### CSA-EIB-014 — Retained debts and MIR scope

The MIR is bounded to the EIB initial implementation slice:

```text
- EIB separate runtime/application boundary.
- EibScNorthboundClient using RestClient.
- EIB-owned mirror DTOs.
- EibEffectiveRefCodec using HMAC-SHA256.
- EibEffectiveViewService and topology/health/runtime/diagnostics projection.
- EibTemporalActProjectionService.
- EibInteractionAdmissionService / EibTemporalAdmissionService.
- EIB HTTP API returning EibResponse<T>.
- Permissive context/policy stub.
- Canonical ID diagnostic/admin gating.
- Provider-native ID suppression.
- Envelope preservation.
- Network failure disambiguation.
- Architecture tests for runtime and code boundary.
```

Out of scope:

```text
- Device/endpoint action admission.
- Discovery admission.
- Live updates.
- SSE.
- GraphQL.
- MCP.
- gRPC/ConnectRPC.
- WebSocket.
- Authority/Policy as real system of record.
- Persistent effective ref registry.
- EIB conformance/TCK.
- Durable EIB audit/admission ledger.
```

---

## 6. Surface inventory matrix

| Artifact | Expected | Found | Disposition |
|---|---:|---:|---|
| EIB package / runtime | yes | no | Create new, separate runtime boundary |
| `EibApplication` | yes | no | Create in preferred `eib/` project |
| `EibScNorthboundClient` | yes | no | Create RestClient client |
| `EibEffectiveRefCodec` | yes | no | Create HMAC-SHA256 codec |
| EIB-owned mirror DTOs | yes | no | Create DTO records |
| `EibScNorthboundClientProperties` | yes | no | Create |
| `EibRefCodecProperties` | yes | no | Create |
| `EibDiagnosticProperties` | yes | no | Create |
| `sc.eib.northbound.base-url` | yes | no | Add in EIB config |
| `providerDeviceId` / `providerEndpointId` suppression | yes | no | Implement and test |
| Envelope preservation mapper | yes | no | Implement and test |
| Effective temporal create `effectiveRef` | yes | no | Implement and test |
| WireMock dependency | no | no | Not needed |
| WebFlux | no | absent | Keep absent |
| Direct SC-C Java imports | no | absent | Keep absent by architecture test |
| SSE controller | no | absent | Keep deferred |

---

## 7. Requirement coverage matrix

| SDD obligation | Resolution |
|---|---|
| CSA-EIB-001 — concrete codebase placement | Separate runtime boundary; preferred `eib/` project |
| CSA-EIB-002 — existing EIB code surface | Greenfield |
| CSA-EIB-003 — dependency patch | EIB module/project dependencies; no WireMock/WebFlux required |
| CSA-EIB-004 — upstream routes | Confirmed from MU-021 controllers |
| CSA-EIB-005 — DTO strategy | EIB-owned mirror DTOs; provider IDs suppressed |
| CSA-EIB-006 — client strategy | RestClient + ScEnvelope<T> |
| CSA-EIB-007 — effective ref codec | HMAC-SHA256 with configurable secret |
| CSA-EIB-008 — context/auth profile | Permissive stub; diagnostic/admin gated |
| CSA-EIB-009 — architecture tests | EIB-specific tests; SC-C test patch only if forbidden placement chosen |
| CSA-EIB-010 — TemporalAct coverage | list/get/create/cancel covered |
| CSA-EIB-011 — canonical ID gating | Mandatory gating tests |
| CSA-EIB-012 — envelope preservation | Mandatory 12-status/source tests |
| CSA-EIB-013 — network failure | MockRestServiceServer + 503 disambiguation |
| CSA-EIB-014 — retained debts | Explicit DEBT-HTTP and DEBT-EIB registry |

---

## 8. Mandatory implementation actions

```text
ACTION-EIB-001 — Preserve non-co-located runtime placement.
  Implement EIB as separate runtime/application boundary, preferably top-level `eib/` project.

ACTION-EIB-002 — Add EIB upstream configuration.
  Add sc.eib.northbound.base-url and timeout-ms in EIB configuration.

ACTION-EIB-003 — Add EIB ref-codec and diagnostic configuration.
  Add sc.eib.ref-codec.secret and sc.eib.diagnostic.admin-enabled.

ACTION-EIB-004 — Implement EIB-owned mirror DTOs.
  Include providerDeviceId/providerEndpointId for deserialization only.

ACTION-EIB-005 — Suppress provider-native IDs from ordinary product views.
  providerDeviceId/providerEndpointId MUST NOT appear in ordinary EffectiveDeviceView/EffectiveEndpointView.

ACTION-EIB-006 — Preserve canonical envelope semantics.
  Preserve ScEnvelope, all 12 statuses, error.source and warning.source internally.

ACTION-EIB-007 — Generate effectiveTemporalActRef after accepted create.
  If createSignalTemporalAct returns ACCEPTED with payload, set InteractionAdmissionDecision.effectiveRef.

ACTION-EIB-008 — Implement network failure vs semantic failure disambiguation.
  HTTP 503 with valid ScEnvelope(DEFERRED_SC_B_REQUIRED) is semantic; HTTP 503 without valid envelope is upstream failure.

ACTION-EIB-009 — Add architecture tests.
  Prove EIB does not import SC-C internals or northbound HTTP adapter classes.

ACTION-EIB-010 — Document list+match effective ref resolution.
  get/cancel EffectiveTemporalAct uses list+match in the initial implementation slice.
```

---

## 9. Retained debt registry

### 9.1 Inherited HTTP debts

```text
DEBT-HTTP-001 — SSE remains SSE-P0 / deferred.
DEBT-HTTP-002 — local-trusted only; no auth/authz.
DEBT-HTTP-003 — Swagger UI / static YAML deferred.
DEBT-HTTP-004 — EIB runtime: this CSA advances it but does not close it; closure requires MIR implementation and validation.
DEBT-HTTP-005 — gRPC/ConnectRPC downstream.
DEBT-HTTP-006 — MCP adapter separate track.
DEBT-HTTP-007 — ACTIVE mode ignores maxResults in SC-C facade.
DEBT-HTTP-008 — HTTP TCK absent.
```

### 9.2 New / carried EIB debts

```text
DEBT-EIB-001 — EIB MIR not yet opened.
DEBT-EIB-002 — EIB implementation not yet attempted.
DEBT-EIB-003 — Authority/Policy/Identity/Session integration remains context-port stub only.
DEBT-EIB-004 — Device/endpoint action admission is SC-B-gated and deferred.
DEBT-EIB-005 — Discovery admission is SC-B-gated and deferred.
DEBT-EIB-006 — Live updates deferred.
DEBT-EIB-007 — GraphQL facade deferred.
DEBT-EIB-008 — MCP AI/tool/admin facade remains separate track.
DEBT-EIB-009 — gRPC/ConnectRPC downstream.
DEBT-EIB-010 — EIB conformance/TCK absent.
DEBT-EIB-011 — Durable audit/admission ledger deferred.
DEBT-EIB-012 — Persistent effective ref registry deferred; list+match accepted for the initial implementation slice.
DEBT-EIB-013 — EIB product security/authz remains stubbed; diagnostic/admin behavior is config/header gated only.
```

---

## 10. Search ledger

```text
pom.xml
  rg "wiremock|WebClient|RestClient|WireMock" -> no explicit dependency found
  spring-boot-starter-web present -> RestClient available
  spring-boot-starter-test present -> MockRestServiceServer available

src/main/java
  rg "adapter/eib|adapter.eib|EibScNorthbound" -> none found
  rg "providerDeviceId|providerEndpointId" -> found in northbound wire DTOs
  rg "MessageDigest|SHA-256" -> existing SHA-256 pattern found in temporal application code

src/main/java/com/sovereign/connect/adapter/northbound/http
  routes confirmed from MU-021 controllers
  @ConditionalOnProperty pattern confirmed on both northbound HTTP controllers

src/test/java
  SC-C northbound HTTP architecture tests confirm boundary pattern
  if EIB is incorrectly placed inside SC-C src/main/java, three existing tests require patching

SC-C routes
  21 northbound facade routes confirmed via HTTP adapter controllers
```

---

## 11. Final verdict

```text
Post-SDD CSA verdict: Approvable.
```

This CSA authorizes opening:

```text
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
```

Candidate branch:

```text
feat/sc-eib-mir-022-effective-interaction-boundary-seed
```

Candidate commit:

```text
feat(eib): add effective interaction boundary seed
```

MIR guardrail:

```text
The MIR MUST preserve EIB as a non-co-located runtime/application boundary.
It MUST NOT silently implement EIB inside the existing SC-C Spring application context.
```

