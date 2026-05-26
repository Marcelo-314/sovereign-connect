# MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001

## SC-C Northbound HTTP/SSE Binding Seed

**Document ID:** MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001  
**Title:** SC-C Northbound HTTP/SSE Binding Seed  
**Version:** v0.2.0-candidate  
**Status:** Candidate / SDD accepted / CSA-approved / execution package enabled  
**Date:** 2026-05-26  
**Corpus:** Sovereign Connect  
**Type:** MIR  
**Plane:** SC-C / Northbound exposure binding  
**Materialization Unit:** MU-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001  
**Operational Slot:** MU-021  
**Scope:** First HTTP/OpenAPI exposure binding over the hardened in-process `ScCoreNorthboundFacade`, implemented as an adapter outside `core.northbound`. SSE remains `SSE-P0` / deferred in this seed. This MIR does not implement EIB, View Composer, Projection, Effective View, Session, Identity, Authority, Policy, Hub/SApp/Surface product API, SC-B runtime, SC-D adapters, gRPC/ConnectRPC, MCP, GraphQL or WebSocket.

---

## Changelog v0.2.0-candidate

Candidate promotion.

This version:

1. Promotes the MIR after review approval.
2. Moves production substrate SDDs (`SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001`, `SDD-SOV-SC-C-RECOVERY-001`, `SDD-SOV-SC-C-TEMPORAL-ENGINE-001`) from `Depends on` to `Related`, because this HTTP binding depends on the hardened Northbound Facade that already encapsulates those concerns.
3. Tightens `AC-021-025` so any deviation from the `@Primary` ObjectMapper decision requires implementation-report evidence and a post-execution governance patch.
4. Enables external execution-package preparation for `docs/mir/mir-021/`.

---

## Changelog v0.1.0-draft

Initial MIR draft.

This version:

1. Opens `MU-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001` as `MU-021` after acceptance of `ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-BINDING-001`, `SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001` and the post-SDD CSA.
2. Materializes the first HTTP/OpenAPI exposure binding over the hardened in-process SC-C Northbound Facade.
3. Adopts `CSA-SOV-SC-C-NORTHBOUND-HTTP-SSE-POST-SDD-001 v0.1.1-merged` as the controlling code-surface audit for this MIR.
4. Authorizes adding `spring-boot-starter-web` and SpringDoc WebMVC API support.
5. Authorizes a new HTTP adapter package outside `core.northbound`.
6. Requires controllers to delegate only to `ScCoreNorthboundFacade`.
7. Requires `ScNorthboundResponse<T>` to remain visible as the HTTP response body.
8. Requires canonical `ScNorthboundStatus -> HTTP status` mapping.
9. Keeps SSE at `SSE-P0` / deferred: no SSE controller, no `SseEmitter`, no WebFlux, no fabricated event stream.
10. Authorizes ObjectMapper serialization hardening with `@Primary` and disabled timestamp output, validated by context and serialization tests.
11. Requires route/body adaptation for `cancelTemporalAct` to avoid path/body `temporalActId` conflict.
12. Requires `listTemporalActs` to use `mode=ACTIVE&maxResults=50` query semantics, with `mode` values aligned to `NorthboundTemporalActFilter.Mode`.
13. Requires stale no-external-exposure tests to be replaced by boundary-scoped HTTP adapter tests.
14. Externalizes implementation context, Codex prompt, acceptance map and implementation report according to MU/MIR governance.

---

## 0. Governance note

This MIR does not include Codex prompts, operational context or acceptance-map details inline.

Execution assets MUST be produced separately under:

```text
docs/mir/mir-021/
  MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001.md
  code-surface-audit.md
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report-template.md
  implementation-report.md
```

Operational rule:

```text
MIR defines the materialization scope.
CSA constrains the actual code surface.
context.md and codex-prompt.md operationalize implementation.
acceptance-map.md maps MIR/SDD/CSA criteria to tests and evidence.
implementation-report.md returns post-execution evidence.
```

This MIR is candidate. Execution package preparation is enabled; implementation remains gated by acceptance of the external execution package.

---

## 1. Disposition

```text
MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001 v0.2.0-candidate:
  Candidate MIR for MU-021.

Execution readiness:
  ADR accepted.
  SDD accepted.
  Post-SDD CSA merged and approvable.
  Candidate promotion completed.
  Execution package required before implementation.

Implementation authorization:
  Direct implementation is authorized only after acceptance of context.md,
  codex-prompt.md, acceptance-map.md and implementation-report-template.md
  as the external execution package.
```

Candidate constraints:

```text
- MU-021 identity and operational slot remain bound to INDEX/SYNC governance.
- CSA decisions are binding for the execution package.
- Execution package MUST NOT broaden scope beyond this MIR.
- This MIR does not authorize EIB implementation.
- This MIR does not authorize gRPC/ConnectRPC, MCP, GraphQL or WebSocket.
- This MIR does not authorize SSE implementation beyond explicit SSE-P0 deferred disposition.
- This MIR exposes Northbound over HTTP/OpenAPI only through an adapter over ScCoreNorthboundFacade.
```

---

## 2. Background

MU-019 created the in-process canonical SC-C Northbound Facade.

MU-020 hardened that facade by stabilizing:

```text
ScNorthboundResponse<T>
ScNorthboundStatus
ScNorthboundError(code, message, source)
ScNorthboundWarning(code, message, source)
DeviceHealth DERIVED_FROM_ENDPOINTS
VALIDATION_ERROR
NorthboundDiagnosticsView
explicit unsupported/deferred dispositions
```

Post-MU-020 state:

```text
SC-C Northbound Facade:
  hardened in-process canonical facade;
  exposure-binding-ready;
  not externally exposed;
  not consumable by non-co-located EIB across a process boundary.
```

The accepted exposure-binding decision is:

```text
EIB is not co-located with SC-C.
Therefore, ScCoreNorthboundFacade is necessary but insufficient.
A technology exposure binding is required before EIB implementation can consume SC-C.
```

This MIR opens the first exposure-binding seed: HTTP/OpenAPI over `ScCoreNorthboundFacade`.

The intended chain is:

```text
SC-C canonical state
  -> ScCoreNorthboundFacade
  -> HTTP/OpenAPI adapter
  -> non-co-located EIB or authorized technical consumer
```

Forbidden chain:

```text
HTTP controller
  -> repositories / SQLite / JdbcTemplate / Flyway / CoreSnapshotQueryService
  -> direct topology/temporal/state authority bypassing ScCoreNorthboundFacade
```

---

## 3. Depends on

```text
Sovereign Connect — Curator & Descent Instructions
RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.14-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.20-draft
SYNC-SOV-SC-MU-BACKLOG-001 v0.1.21-draft
ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-BINDING-001 v0.1.1-draft, accepted
SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001 v0.2.0-candidate, accepted by final dictum
CSA-SOV-SC-C-NORTHBOUND-HTTP-SSE-PRE-SDD-001 v0.1.1-merged
CSA-SOV-SC-C-NORTHBOUND-HTTP-SSE-POST-SDD-001 v0.1.1-merged
PDR-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft
SDD-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft
MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001 v1.0.0-accepted
PDR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.2.0-candidate
SDD-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.1.1-draft
MIR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v1.0.0-accepted
MIR-SOV-SC-C-TEMPORAL-RUNTIME-INDUSTRIALIZATION-001 v1.0.0-accepted
MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001 v1.0.0-accepted
MIR-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001 v1.0.0-accepted
```

If governance documents use newer versions in the repository at execution time, the execution package MUST reference the newer canonical versions while preserving the same MU identity and decisions.

---

## 4. Related

```text
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001 v0.1.1-draft, production substrate encapsulated by Northbound Facade / not direct dependency
SDD-SOV-SC-C-RECOVERY-001 v0.1.2-draft, production substrate encapsulated by Northbound Facade / not direct dependency
SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.1.2-draft, production substrate encapsulated by Northbound Facade / not direct dependency
SDD-SOV-SC-C-NORTHBOUND-GRPC-CONNECTRPC-001, future / not authorized
SDD-SOV-SC-C-NORTHBOUND-MCP-ADAPTER-001, future / not authorized by this MIR
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001, contract-preparation / implementation-deferred
PDR-SOV-SC-VIEW-COMPOSER-BOUNDARY-001, future / EIB-framed
PDR-SOV-SC-DIAGNOSTIC-ADMIN-BOUNDARY-001, future
PDR-SOV-SC-MCP-FACADE-001, separate AI/tool/admin exposure track
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001, outside SC-C HTTP binding
```

---

## 5. Materialization thesis

MU-021 creates the first real HTTP/OpenAPI exposure binding for SC-C Northbound.

Canonical statement:

```text
SC-C owns canonical truth.
ScCoreNorthboundFacade exposes canonical truth inside the SC-C runtime.
HTTP/OpenAPI adapter exposes that facade across a process boundary.
EIB may later consume the exposed canonical truth.
Hub, SApp and Surfaces still do not call SC-C directly.
```

Implementation statement:

```text
MU-021 adds an adapter package.
It does not change the semantic ownership of Northbound.
It does not make controllers application services.
It does not let controllers bypass ScCoreNorthboundFacade.
```

Exposure statement:

```text
HTTP/OpenAPI is in scope.
SSE is taxonomically reserved but remains SSE-P0 / deferred.
gRPC/ConnectRPC, MCP, GraphQL and WebSocket remain downstream.
```

---

## 6. Goals

### G-021-001 — Add Spring MVC HTTP exposure capability

Add `spring-boot-starter-web` as the first HTTP exposure stack.

The seed MUST NOT add `spring-boot-starter-webflux`.

---

### G-021-002 — Add OpenAPI runtime endpoint

Add SpringDoc WebMVC API support so that `/v3/api-docs` is available and tested.

The seed commits to a runtime OpenAPI endpoint only.

It does not commit to Swagger UI or static YAML generation.

---

### G-021-003 — Create HTTP adapter outside `core.northbound`

Create package:

```text
com.sovereign.connect.adapter.northbound.http
```

The adapter package MAY import Spring Web.

The `core.northbound` package MUST remain HTTP-free.

---

### G-021-004 — Delegate exclusively to `ScCoreNorthboundFacade`

HTTP controllers MUST inject only `ScCoreNorthboundFacade` as their SC-C business dependency.

Controllers MUST NOT inject:

```text
CoreSnapshotQueryService
BaseTopologyService
TemporalActApplicationPort
TemporalActObservationPort
SQLite repositories
JdbcTemplate
DataSource
Flyway
SC-B clients
SC-D adapters
```

---

### G-021-005 — Preserve `ScNorthboundResponse<T>` as HTTP body

Controllers MUST return `ScNorthboundResponse<T>` as the response body, wrapped in `ResponseEntity` only for HTTP status mapping.

The body MUST retain:

```text
status
payload
warnings
error
```

The adapter MUST NOT unwrap successful responses into flat DTO bodies.

---

### G-021-006 — Implement canonical status mapping

Implement `ScNorthboundStatus -> HTTP status` mapping through an adapter-local mapper.

The mapping MUST preserve the canonical Northbound status in the response body.

---

### G-021-007 — Bind all 21 facade methods through HTTP routes

The HTTP adapter MUST expose route coverage for the 21 current `ScCoreNorthboundFacade` methods unless the execution package explicitly records a safe route-level deferral for a method.

The SDD/CSA expectation is full route coverage for:

```text
14 Profile A observation methods
7 Profile B temporal/diagnostics methods
```

---

### G-021-008 — Keep SSE deferred

SSE remains:

```text
SSE-P0 — deferred
```

The implementation MUST NOT add:

```text
ScNorthboundSseController
SseEmitter
WebFlux
Flux
Mono
polling heartbeat pretending to be a canonical event stream
```

---

### G-021-009 — Harden ObjectMapper serialization behavior

The existing `TemporalEngineConfiguration.objectMapper()` SHOULD become the explicit canonical serialization bean by adding:

```text
@Primary
SerializationFeature.WRITE_DATES_AS_TIMESTAMPS disabled
findAndRegisterModules() preserved
```

This is required as a serialization-stability hardening step and MUST be validated by tests.

---

### G-021-010 — Preserve route/body correctness for TemporalAct operations

`createSignalTemporalAct` MUST use the existing core northbound request record.

`cancelTemporalAct` MUST avoid path/body `temporalActId` conflict by using an adapter-local HTTP body record.

`listTemporalActs` MUST use `mode` and `maxResults` query parameters.

---

### G-021-011 — Replace stale architecture tests

Tests that globally prohibit HTTP exposure MUST be replaced by boundary-scoped tests.

The new rule is:

```text
Spring Web is allowed only under adapter.northbound.http.
Spring Web remains forbidden in core.northbound and core/domain packages.
```

---

### G-021-012 — Preserve non-SC-C boundaries

The HTTP binding MUST NOT introduce:

```text
EIB
View Composer
Projection / Effective View
Session / Identity / Authority / Policy
Hub / SApp / Surface product API
SC-B runtime
SC-D adapter runtime
gRPC / ConnectRPC
MCP
GraphQL
WebSocket
NATS / JetStream
```

---

## 7. Non-goals

This MIR does not implement or authorize:

```text
EIB implementation
View Composer implementation
Projection / Effective View
Session / Identity / Authority / Policy
Hub / SApp / Surface product-facing API
SC-B runtime
SC-D adapters
command dispatch
provider discovery execution
gRPC / ConnectRPC implementation
MCP adapter implementation
GraphQL
WebSocket
SSE implementation beyond deferred taxonomy
Swagger UI
static OpenAPI YAML generation
public internet-facing security profile
authentication / authorization layer
Northbound TCK / conformance harness
```

This MIR also does not close the following retained Northbound debts:

```text
EndpointRuntimeState authority absent.
RecoveryStatus read model absent.
MigrationReadiness authority absent.
DeviceHealth normalized authority absent; DERIVED_FROM_ENDPOINTS is used.
Northbound TCK / conformance harness absent.
Full diagnostics/readiness surface deferred.
```

The HTTP binding MUST expose unsupported/deferred states explicitly. It MUST NOT hide or fabricate authority for them.

---

## 8. MIR decisions

### D-MIR-021-001 — Spring MVC is the seed HTTP stack

Decision:

```text
Add spring-boot-starter-web.
Do not add spring-boot-starter-webflux.
```

Rationale:

```text
The accepted SDD selects Spring MVC as the first HTTP stack.
The codebase is currently servlet/standard Spring Boot, not reactive.
SSE remains deferred, so WebFlux is unnecessary.
```

---

### D-MIR-021-002 — SpringDoc WebMVC API is in scope

Decision:

```text
Add springdoc-openapi-starter-webmvc-api.
Expose /v3/api-docs.
Test that the endpoint returns non-empty JSON.
```

Version policy:

```text
The execution package SHOULD pin a SpringDoc 2.x version compatible with Spring Boot 3.3.5.
CSA suggests 2.5.0 as conservative initial version.
A newer 2.x version is permitted only if dependency resolution and /v3/api-docs tests pass.
```

No Swagger UI commitment is made in this seed.

---

### D-MIR-021-003 — Adapter package is the only HTTP-permitted package

Decision:

```text
Package: com.sovereign.connect.adapter.northbound.http
```

Only this package may import Spring Web annotations/classes.

`core.northbound` must remain HTTP-free.

---

### D-MIR-021-004 — Controller split

Decision:

```text
ScNorthboundTopologyHttpController
ScNorthboundTemporalHttpController
ScNorthboundHttpResponseMapper
ScNorthboundHttpConfiguration
ScNorthboundHttpProperties
ScNorthboundCancelTemporalActHttpBody
```

Optional/deferred:

```text
ScNorthboundHttpExceptionHandler — optional; may be deferred.
ScNorthboundSseController — forbidden in this seed because SSE-P0.
```

---

### D-MIR-021-005 — HTTP response body preserves canonical Northbound envelope

Decision:

```text
ScNorthboundResponse<T> remains the HTTP body for all statuses.
```

The adapter maps HTTP status independently but does not remove the canonical `status` field from the body.

---

### D-MIR-021-006 — Status mapping

The HTTP adapter MUST implement:

```text
OK                            -> 200
CREATED                       -> 201
ACCEPTED                      -> 202
CANCELLED                     -> 200
NOT_FOUND                     -> 404
INVALID_REQUEST               -> 400
INVALID_CANONICAL_ID          -> 400
VALIDATION_ERROR              -> 422
UNSUPPORTED_PROFILE           -> 501
DEFERRED_SC_B_REQUIRED        -> 503
UNKNOWN_PENDING_NORMALIZATION -> 200
INTERNAL_ERROR                -> 500
```

Rules:

```text
UNSUPPORTED_PROFILE and DEFERRED_SC_B_REQUIRED MUST NOT map to 200.
UNKNOWN_PENDING_NORMALIZATION MAY map to 200 only if body.status remains visible.
```

---

### D-MIR-021-007 — TemporalAct HTTP request shapes

Decision:

```text
POST /sc/v1/habitats/{habitatId}/temporal-acts
  body: NorthboundCreateSignalTemporalActRequest
  interpretation: signal-only seed route

POST /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
  body: ScNorthboundCancelTemporalActHttpBody(requestedByRef, idempotencyKey, reason)
  adapter constructs NorthboundCancelTemporalActRequest from path + body

GET /sc/v1/habitats/{habitatId}/temporal-acts?mode=ACTIVE&maxResults=50
  query param: mode, not filter
  separator: &, not |
  values: ACTIVE, TERMINAL, MISFIRED
```

Future `ActionTemporalAct` route shape remains deferred.

---

### D-MIR-021-008 — SSE-P0

Decision:

```text
SSE is deferred in this seed.
```

Rationale:

```text
No current SC-C source provides a canonical live event stream suitable for SSE.
Outbox storage is not a northbound event stream authority.
No topology-change event read model exists in the baseline.
```

---

### D-MIR-021-009 — ObjectMapper serialization hardening

Decision:

```text
Patch TemporalEngineConfiguration.objectMapper() to:
  - add @Primary;
  - preserve findAndRegisterModules();
  - disable SerializationFeature.WRITE_DATES_AS_TIMESTAMPS.
```

This is adopted as serialization stability, not because duplicate `ObjectMapper` failure is assumed.

---

### D-MIR-021-010 — HTTP configuration properties

Decision:

```java
@ConfigurationProperties(prefix = "sc.northbound.http")
public record ScNorthboundHttpProperties(
    boolean enabled,
    String basePath,
    String exposureProfile
) {}
```

Default `application.yml`:

```yaml
sc:
  northbound:
    http:
      enabled: true
      base-path: /sc/v1
      exposure-profile: local-trusted
```

`server.port` remains standard Spring Boot configuration.

---

## 9. Expected implementation surface

### 9.1 Dependencies

Expected `pom.xml` additions:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
    <version>${springdoc.version-or-literal}</version>
</dependency>
```

`spring-boot-starter-webflux` MUST NOT be added.

---

### 9.2 Main source files

Expected new files:

```text
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTopologyHttpController.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTemporalHttpController.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpResponseMapper.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpConfiguration.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpProperties.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundCancelTemporalActHttpBody.java
```

Expected modified files:

```text
pom.xml
src/main/resources/application.yml
src/main/java/com/sovereign/connect/config/TemporalEngineConfiguration.java
```

Optional only if execution package explicitly scopes it:

```text
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpExceptionHandler.java
```

Forbidden in this seed:

```text
ScNorthboundSseController
```

---

### 9.3 Test files

Expected new test files:

```text
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTopologyHttpControllerTest.java
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTemporalHttpControllerTest.java
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpSerializationTest.java
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpArchitectureTest.java
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpSpringContextTest.java
```

Expected modified tests:

```text
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeNegativeBoundaryTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeSpringContextTest.java
```

Expected unchanged:

```text
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeArchitectureTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeBehavioralTest.java
```

---

## 10. HTTP route surface

The execution package MUST provide exact route mapping. The route base path is:

```text
/sc/v1/habitats/{habitatId}
```

Minimum route obligations:

```text
GET  /sc/v1/habitats/{habitatId}/topology
GET  /sc/v1/habitats/{habitatId}/topology/version
GET  /sc/v1/habitats/{habitatId}/rooms
GET  /sc/v1/habitats/{habitatId}/zones
GET  /sc/v1/habitats/{habitatId}/devices
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/health
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/endpoints
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/health
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/locations/{roomOrZoneId}/devices
GET  /sc/v1/habitats/{habitatId}/locations/{roomOrZoneId}/endpoints
GET  /sc/v1/habitats/{habitatId}/temporal/runtime-status
GET  /sc/v1/habitats/{habitatId}/recovery/status
GET  /sc/v1/habitats/{habitatId}/diagnostics
GET  /sc/v1/habitats/{habitatId}/temporal-acts
GET  /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}
POST /sc/v1/habitats/{habitatId}/temporal-acts
POST /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
```

Route notes:

```text
getDeviceRuntimeState is functional: OK or NOT_FOUND.
getEndpointRuntimeState is UNSUPPORTED_PROFILE until endpoint runtime-state authority exists.
getRecoveryStatus is UNSUPPORTED_PROFILE until recovery/readiness authority exists.
getNorthboundDiagnostics returns OK with migrationReadiness possibly UNKNOWN_PENDING_NORMALIZATION inside payload.
```

---

## 11. Test obligations

### 11.1 Topology HTTP controller tests

Required coverage:

```text
GET /topology -> 200 when facade returns OK.
GET /devices/{deviceId} -> 404 when facade returns NOT_FOUND.
GET /devices/{deviceId}/health -> 200 with device health payload.
GET /devices/{deviceId}/runtime-state -> 404 when facade returns NOT_FOUND.
GET /endpoints/{endpointId}/runtime-state -> 501 when facade returns UNSUPPORTED_PROFILE.
GET /locations/{roomOrZoneId}/devices -> 200.
```

Strategy:

```text
@WebMvcTest(ScNorthboundTopologyHttpController.class)
@MockBean ScCoreNorthboundFacade
MockMvc
```

---

### 11.2 Temporal HTTP controller tests

Required coverage:

```text
POST /temporal-acts valid body -> 202 when facade returns ACCEPTED.
POST /temporal-acts validation error -> 422 when facade returns VALIDATION_ERROR.
POST /temporal-acts/{temporalActId}/cancel -> 200 when facade returns CANCELLED.
Cancel controller constructs NorthboundCancelTemporalActRequest from path + body.
GET /temporal-acts?mode=ACTIVE -> 200.
GET /temporal-acts?mode=INVALID -> 400 and facade not invoked.
GET /recovery/status -> 501 when facade returns UNSUPPORTED_PROFILE.
GET /diagnostics -> 200 with canonical response body preserved.
```

Strategy:

```text
@WebMvcTest(ScNorthboundTemporalHttpController.class)
@MockBean ScCoreNorthboundFacade
MockMvc
```

---

### 11.3 Serialization tests

Required coverage:

```text
Instant serializes as ISO-8601 string.
NorthboundCreateSignalTemporalActRequest dueAt deserializes from ISO-8601.
ScNorthboundResponse<T> serializes status/payload/warnings/error.
ScNorthboundError(code, message, source) serializes all three fields.
ScNorthboundWarning(code, message, source) serializes all three fields.
UNKNOWN_PENDING_NORMALIZATION remains visible in response body.
```

---

### 11.4 Architecture tests

Required coverage:

```text
webImportsForbiddenOutsideHttpAdapterPackage.
forbiddenTechnologiesAbsentFromAllMainSource.
httpAdapterDelegatesOnlyToFacade.
httpAdapterDoesNotImportRepositoriesOrDomainServices.
httpAdapterDoesNotLeakEibProjectionOrAuthorityConcepts.
```

Stale tests MUST be replaced or updated:

```text
NorthboundFacadeNegativeBoundaryTest.noExternalExposureLayerIsIntroducedByMu019
NorthboundFacadeSpringContextTest.noHttpControllerBeanIsIntroduced
```

---

### 11.5 Spring context / OpenAPI tests

Required coverage:

```text
contextLoadsWithHttpControllers.
topologyControllerBeanExistsAndIsProperlyTyped.
temporalControllerBeanExistsAndIsProperlyTyped.
httpControllerBeansAreOnlyInHttpAdapterPackage.
openApiEndpointResolvesToNonEmptyDocument.
```

---

## 12. Acceptance criteria

### Functional HTTP route criteria

**AC-021-001**  
`spring-boot-starter-web` is added and the application context starts.

**AC-021-002**  
`spring-boot-starter-webflux` is not added.

**AC-021-003**  
SpringDoc WebMVC API dependency is added and `/v3/api-docs` returns non-empty JSON.

**AC-021-004**  
HTTP adapter package exists outside `core.northbound`.

**AC-021-005**  
Topology HTTP controller delegates only to `ScCoreNorthboundFacade`.

**AC-021-006**  
Temporal HTTP controller delegates only to `ScCoreNorthboundFacade`.

**AC-021-007**  
No HTTP controller injects repositories, SQLite adapters, JDBC primitives, Flyway, core query services, temporal ports or topology services directly.

**AC-021-008**  
`ScNorthboundResponse<T>` remains the HTTP response body for success, not-found, invalid, unsupported and internal-error cases.

**AC-021-009**  
HTTP status mapping matches D-MIR-021-006.

**AC-021-010**  
`UNSUPPORTED_PROFILE` maps to HTTP 501 and remains visible as body status.

**AC-021-011**  
`DEFERRED_SC_B_REQUIRED` maps to HTTP 503 and remains visible as body status.

**AC-021-012**  
`VALIDATION_ERROR` maps to HTTP 422 and is exercised by a controller test.

**AC-021-013**  
`UNKNOWN_PENDING_NORMALIZATION` may map to HTTP 200 only with body status visible.

**AC-021-014**  
`getDeviceRuntimeState` route is tested as functional `OK` or `NOT_FOUND`, not as unsupported.

**AC-021-015**  
`getEndpointRuntimeState` route returns HTTP 501 when facade returns `UNSUPPORTED_PROFILE`.

**AC-021-016**  
`getRecoveryStatus` route returns HTTP 501 when facade returns `UNSUPPORTED_PROFILE`.

**AC-021-017**  
`getNorthboundDiagnostics` route returns HTTP 200 with canonical response body preserved.

### Temporal request criteria

**AC-021-018**  
`POST /temporal-acts` binds `NorthboundCreateSignalTemporalActRequest` directly and delegates to `createSignalTemporalAct`.

**AC-021-019**  
`POST /temporal-acts/{temporalActId}/cancel` uses adapter-local body without `temporalActId` and constructs `NorthboundCancelTemporalActRequest` from path + body.

**AC-021-020**  
`GET /temporal-acts?mode=ACTIVE&maxResults=50` uses `mode`, not `filter`, and `&`, not `|`.

**AC-021-021**  
Unknown temporal act `mode` returns `INVALID_REQUEST` / HTTP 400 without invoking the facade.

**AC-021-022**  
Supported `mode` values match `NorthboundTemporalActFilter.Mode`: `ACTIVE`, `TERMINAL`, `MISFIRED`.

**AC-021-023**  
The implementation report records that the generic `POST /temporal-acts` path is signal-only in this seed and that future `ActionTemporalAct` route/discriminator decision remains deferred.

### Serialization / configuration criteria

**AC-021-024**  
`TemporalEngineConfiguration.objectMapper()` preserves `findAndRegisterModules()`.

**AC-021-025**  
`TemporalEngineConfiguration.objectMapper()` is annotated with `@Primary`, unless execution discovers a concrete incompatibility; in that case the implementation report MUST record the deviation and the approved alternative, and a post-execution governance patch to this MIR is required.

**AC-021-026**  
ObjectMapper disables `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` or otherwise proves ISO-8601 `Instant` serialization by test.

**AC-021-027**  
`Instant` fields serialize as ISO-8601 strings.

**AC-021-028**  
`NorthboundCreateSignalTemporalActRequest.dueAt` deserializes from ISO-8601 string.

**AC-021-029**  
`ScNorthboundError` and `ScNorthboundWarning` serialize `source`.

**AC-021-030**  
`ScNorthboundHttpProperties` exists with prefix `sc.northbound.http` and defaults are present in `application.yml`.

### Boundary / negative criteria

**AC-021-031**  
`core.northbound` remains free of Spring Web imports and annotations.

**AC-021-032**  
Spring Web imports are allowed only under `adapter.northbound.http`.

**AC-021-033**  
Forbidden technologies remain absent from main source: gRPC, ConnectRPC, MCP, GraphQL, WebSocket, NATS, JetStream.

**AC-021-034**  
HTTP adapter does not import repositories, SQLite adapters, `JdbcTemplate`, `DataSource`, `Flyway`, `CoreSnapshotQueryService`, `BaseTopologyService`, `TemporalActApplicationPort` or `TemporalActObservationPort`.

**AC-021-035**  
HTTP adapter does not introduce EIB, Projection, Effective View, Session, Identity, Authority, Policy or Surface concepts.

**AC-021-036**  
No SSE controller, `SseEmitter`, `Flux`, `Mono` or WebFlux dependency is introduced.

**AC-021-037**  
Existing Northbound facade behavioral tests continue to pass.

**AC-021-038**  
Existing `NorthboundFacadeArchitectureTest` remains scoped to `core.northbound` and continues to pass unchanged.

**AC-021-039**  
Stale global no-exposure test is replaced with boundary-scoped tests.

**AC-021-040**  
Stale no-controller Spring context assertion is replaced with controller-package assertion.

### Evidence criteria

**AC-021-041**  
Implementation report records dependency changes, new files, modified tests, validation command and complete test results.

**AC-021-042**  
Implementation report records retained debts after MU-021.

**AC-021-043**  
Implementation report records whether SpringDoc version differed from CSA recommendation and why.

**AC-021-044**  
Implementation report records that SSE remains `SSE-P0` / deferred.

**AC-021-045**  
Implementation report records that EIB remains unimplemented and that HTTP binding only exposes SC-C Northbound.

---

## 13. Retained debt after MU-021

Expected retained debt:

```text
DEBT-HTTP-001 — SSE remains SSE-P0 / deferred.
DEBT-HTTP-002 — No authentication/authorization layer in HTTP seed; local-trusted only.
DEBT-HTTP-003 — Swagger UI/static OpenAPI artifact deferred.
DEBT-HTTP-004 — EIB runtime consumption still requires EIB-side contract/SDD.
DEBT-HTTP-005 — gRPC/ConnectRPC binding remains downstream.
DEBT-HTTP-006 — MCP adapter remains separate AI/tool/admin exposure track.
DEBT-HTTP-007 — ACTIVE mode maxResults behavior remains inherited from facade.
DEBT-HTTP-008 — HTTP binding TCK/conformance harness absent.
```

These debts are non-blocking for MU-021 seed validation if they are explicit and tested where applicable.

---

## 14. Risks

### RISK-021-001 — HTTP adapter bypasses facade

Mitigation:

```text
Architecture tests require controllers to delegate only to ScCoreNorthboundFacade.
```

### RISK-021-002 — Spring Web leaks into core

Mitigation:

```text
Boundary-scoped tests permit Spring Web only in adapter.northbound.http.
```

### RISK-021-003 — Canonical response body gets unwrapped

Mitigation:

```text
Controller tests and serialization tests require ScNorthboundResponse<T> as body.
```

### RISK-021-004 — ObjectMapper serialization drift

Mitigation:

```text
@Primary + WRITE_DATES_AS_TIMESTAMPS disabled + context/serialization tests.
```

### RISK-021-005 — SSE accidentally becomes fake event stream

Mitigation:

```text
SSE-P0 forbids SSE controller, SseEmitter, WebFlux and polling heartbeat.
```

### RISK-021-006 — HTTP binding mistaken for product-facing API

Mitigation:

```text
Security profile remains local-trusted technical API; EIB/Product API remains downstream.
```

---

## 15. Branch and commit suggestion

Suggested branch:

```text
feat/sc-c-mir-021-northbound-http-sse-binding
```

Suggested commit:

```text
feat(sc-c): add northbound http sse binding seed
```

---

## 16. Final dictum

```text
MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001 v0.2.0-candidate
is accepted as Candidate.

Execution package preparation is enabled.
Implementation remains gated by acceptance of:
  - context.md;
  - codex-prompt.md;
  - acceptance-map.md;
  - implementation-report-template.md.

SSE remains SSE-P0 / deferred.
This MIR authorizes HTTP/OpenAPI binding seed only after execution-package approval.
```
