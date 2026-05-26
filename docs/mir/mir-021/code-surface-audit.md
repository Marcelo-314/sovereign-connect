# CSA-SOV-SC-C-NORTHBOUND-HTTP-SSE-POST-SDD-001

## Post-SDD Code Surface Audit — SC-C Northbound HTTP/SSE Binding

```text
Document ID:  CSA-SOV-SC-C-NORTHBOUND-HTTP-SSE-POST-SDD-001
Title:        Post-SDD Code Surface Audit — SC-C Northbound HTTP/SSE Binding
Version:      v0.1.1-merged
Status:       Merged Draft / Post-SDD / Pre-MIR
Date:         2026-05-26
Corpus:       Sovereign Connect
Plane:        SC-C
Scope:        Code surface reconnaissance after SDD acceptance, before MIR authorization
Baseline:     sovereign-connect-pre-csa-21.zip / sovereign-connect-020.zip
Branch:       feat/sc-c-mir-020-northbound-facade-hardening
Input ADR:    ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-BINDING-001 v0.1.1-draft, accepted
Input SDD:    SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001 v0.2.0-candidate, accepted
Result:       Approvable to open MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001
```

Supersedes / merges:

```text
CSA-SOV-SC-C-NORTHBOUND-HTTP-SSE-001 v0.1.0-draft
CSA-SOV-SC-C-NORTHBOUND-HTTP-SSE-POST-SDD-001 v0.1.0-draft
```

---

## 0. Purpose

This is the required post-SDD / pre-MIR code surface audit for
`SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001 v0.2.0-candidate`.

It resolves the ten CSA obligations declared in the SDD and makes the next MIR
bounded enough to authorize implementation.

This CSA does **not** authorize implementation. That gate remains with:

```text
MIR -> execution package -> implementation -> review -> governance closure
```

---

## 1. Executive verdict

```text
Verdict: Approvable to open MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001.
```

The codebase is ready for an HTTP binding seed if the MIR and execution package
apply the decisions below.

Mandatory implementation actions:

```text
ACTION-HTTP-001 — Add spring-boot-starter-web.
ACTION-HTTP-002 — Add SpringDoc WebMVC API dependency and /v3/api-docs test.
ACTION-HTTP-003 — Create adapter package outside core.northbound.
ACTION-HTTP-004 — Controllers delegate only to ScCoreNorthboundFacade.
ACTION-HTTP-005 — Preserve ScNorthboundResponse<T> as HTTP body.
ACTION-HTTP-006 — Replace stale no-external-exposure tests with boundary-scoped tests.
ACTION-HTTP-007 — Update stale controller-absence Spring context test.
ACTION-HTTP-008 — Add HTTP request/body adapter for cancelTemporalAct path/body split.
ACTION-HTTP-009 — Keep SSE at SSE-P0 / deferred.
ACTION-HTTP-010 — Validate ObjectMapper/Jackson behavior with context and serialization tests.
```

Important correction in this merged version:

```text
The prior CSA draft treated @Primary on ObjectMapper as mandatory because it
assumed Spring Boot would definitely create a second ObjectMapper. Spring Boot
Jackson auto-configuration backs off when an ObjectMapper is already configured.
Therefore, @Primary is not justified as a duplicate-bean fix by itself.

However, the execution package SHOULD still add @Primary and disable
WRITE_DATES_AS_TIMESTAMPS as a low-risk serialization-stability hardening step,
provided tests confirm context startup and ISO-8601 behavior.
```

---

## 2. Completeness envelope

### 2.1 Completeness level

```text
CSA-C3 — Change-impact-complete
```

Rationale:

```text
The audit identifies impacted packages, dependency changes, controller surface,
request body adaptation, architecture-test changes, ObjectMapper/Jackson risk,
OpenAPI scope and SSE disposition.
```

### 2.2 Inspected baseline

```text
ZIP:       sovereign-connect-pre-csa-21.zip / sovereign-connect-020.zip
Branch:    feat/sc-c-mir-020-northbound-facade-hardening
Evidence:  included Surefire reports
Tests:     196 / 0 failures / 0 errors / 0 skipped
```

The audit environment did not have Maven or a Maven wrapper available, so fresh
local test execution was not performed.

### 2.3 Inspected source paths

```text
pom.xml
src/main/resources/application.yml
src/main/java/com/sovereign/connect/SovereignConnectApplication.java
src/main/java/com/sovereign/connect/config/TemporalEngineConfiguration.java
src/main/java/com/sovereign/connect/config/TopologyPersistenceConfiguration.java
src/main/java/com/sovereign/connect/core/northbound/ScCoreNorthboundFacade.java
src/main/java/com/sovereign/connect/core/northbound/DefaultScCoreNorthboundFacade.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundResponse.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundStatus.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundError.java
src/main/java/com/sovereign/connect/core/northbound/ScNorthboundWarning.java
src/main/java/com/sovereign/connect/core/northbound/NorthboundMapper.java
src/main/java/com/sovereign/connect/core/northbound/runtime/**
src/main/java/com/sovereign/connect/core/northbound/temporal/**
src/main/java/com/sovereign/connect/core/northbound/topology/**
src/main/java/com/sovereign/connect/core/temporal/engine/TemporalEngineProperties.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeBehavioralTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeArchitectureTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeNegativeBoundaryTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeSpringContextTest.java
src/test/java/com/sovereign/connect/architecture/StorageLegacyCleanupArchitectureTest.java
src/test/java/com/sovereign/connect/testing/SQLiteTestSupport.java
```

### 2.4 Audit limitations

```text
mvn is unavailable in the audit environment.
Fresh test execution was not performed here.
Spring MVC, SpringDoc and Jackson behavior must be validated by implementation tests.
Dependency resolution must be confirmed in the implementation environment.
```

---

## 3. Evidence snapshot

### 3.1 Current dependency surface

Confirmed in `pom.xml`:

```text
PRESENT:
  spring-boot-starter
  spring-boot-starter-jdbc
  spring-boot-starter-test (test scope)
  jackson-datatype-jsr310
  jackson-annotations
  sqlite-jdbc
  flyway-core
  flyway-database-nc-sqlite
  h2 (test scope)

ABSENT:
  spring-boot-starter-web
  spring-boot-starter-webflux
  springdoc-openapi-starter-webmvc-api
  spring-security
  grpc / connectrpc / mcp / graphql / websocket / nats
```

Repository properties:

```text
java.version = 21
spring-boot.version = 3.3.5
```

### 3.2 Spring configuration surface

```text
@SpringBootApplication — SovereignConnectApplication
@Configuration + @EnableConfigurationProperties(TemporalEngineProperties.class)
    — TemporalEngineConfiguration
@Configuration
    — TopologyPersistenceConfiguration
@Service
    — DefaultScCoreNorthboundFacade
```

There is no explicit `@SpringBootApplication(exclude = ...)` and no
`JacksonAutoConfiguration` exclusion.

### 3.3 ObjectMapper bean surface

Current `TemporalEngineConfiguration.objectMapper()`:

```java
@Bean
public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
}
```

Current annotations:

```text
@Bean only.
No @Primary.
No SerializationFeature.WRITE_DATES_AS_TIMESTAMPS disable.
```

Known production injection points include temporal repositories/services and
topology persistence configuration. HTTP serialization will add Spring MVC HTTP
message-converter usage.

Merged disposition:

```text
Adding spring-boot-starter-web does not, by itself, prove a duplicate
ObjectMapper bean will exist, because Spring Boot's JacksonAutoConfiguration
creates an ObjectMapper only when none is already configured.

Still, the HTTP binding seed SHOULD make the existing ObjectMapper the explicit
canonical serialization bean by adding @Primary and disabling timestamp output.
The correctness of this decision MUST be validated by context and serialization tests.
```

Recommended patch:

```java
@Bean
@Primary
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
}
```

### 3.4 Northbound facade — confirmed method count

`ScCoreNorthboundFacade`: **21 methods**.

Profile A — topology / runtime observation:

```text
getTopologySnapshot
getTopologyVersion
getDevice
getEndpoint
listRooms
listZones
listDevices
listEndpoints
listDevicesLocatedIn
listEndpointsLocatedIn
getEndpointHealth
getDeviceHealth
getDeviceRuntimeState
getEndpointRuntimeState
```

Profile B — temporal / diagnostics:

```text
getTemporalRuntimeStatus
getRecoveryStatus
createSignalTemporalAct
cancelTemporalAct
getTemporalAct
listTemporalActs
getNorthboundDiagnostics
```

### 3.5 Unsupported/deferred method inventory

```text
getDeviceRuntimeState
  functional: OK with device state, or NOT_FOUND if no state is recorded.

getEndpointRuntimeState
  UNSUPPORTED_PROFILE.

getRecoveryStatus
  UNSUPPORTED_PROFILE.

getNorthboundDiagnostics
  OK response; payload contains migrationReadiness = UNKNOWN_PENDING_NORMALIZATION.
```

### 3.6 Instant-bearing Northbound DTOs

Records with `Instant` fields include:

```text
NorthboundTopologySnapshot.readAt
NorthboundEndpointHealthView.lastSeenAt
NorthboundDeviceHealthView.readAt
NorthboundRuntimeStateView.readAt
NorthboundTemporalRuntimeStatusView.lastPollAt
NorthboundTemporalRuntimeStatusView.lastSuccessfulPollAt
NorthboundRecoveryStatusView.observedAt
NorthboundDiagnosticsView.readAt
NorthboundTemporalActView.dueAt
NorthboundTemporalActView.createdAt
NorthboundTemporalActView.updatedAt
NorthboundTemporalActView.firedAt
NorthboundTemporalActView.terminalAt
NorthboundCreateSignalTemporalActRequest.dueAt
```

`jackson-datatype-jsr310` is already on the classpath and the current
`ObjectMapper` uses `findAndRegisterModules()`.

---

## 4. CSA obligation resolutions

### CSA-HTTP-001 — Exact Spring Web dependency patch

Add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Do not add `spring-boot-starter-webflux` in this seed.

Rationale:

```text
The SDD selects Spring MVC as the first HTTP binding stack.
SSE remains SSE-P0 / deferred.
```

---

### CSA-HTTP-002 — Exact OpenAPI dependency and artifact strategy

Add SpringDoc WebMVC API support:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
    <version>2.5.0</version>
</dependency>
```

Disposition:

```text
The MIR may pin 2.5.0 as the conservative initial version for Spring Boot 3.3.5.
The execution package MAY allow a newer 2.x version only if dependency resolution
and /v3/api-docs tests pass in the implementation environment.
```

Selected artifact strategy:

```text
Runtime OpenAPI endpoint only: /v3/api-docs.
No Swagger UI commitment in this seed.
No static YAML commitment in this seed.
```

Required test:

```text
/v3/api-docs returns 200 with non-empty JSON.
```

---

### CSA-HTTP-003 — ObjectMapper / Jackson behavior after adding Spring Web

Merged disposition:

```text
ObjectMapper treatment is mandatory as a serialization-stability concern,
not as a proven duplicate-bean failure.
```

Required execution-package instruction:

```text
1. Add @Primary to TemporalEngineConfiguration.objectMapper().
2. Disable SerializationFeature.WRITE_DATES_AS_TIMESTAMPS.
3. Keep findAndRegisterModules().
4. Add context and serialization tests.
```

Required tests:

```text
- Spring context starts with spring-boot-starter-web present.
- HTTP serialization of Instant fields is ISO-8601 string.
- ScNorthboundResponse<T> body serializes status/payload/warnings/error.
- ScNorthboundError and ScNorthboundWarning serialize source.
```

---

### CSA-HTTP-004 — Existing architecture tests to patch

#### Test 1 — NorthboundFacadeNegativeBoundaryTest

Current stale behavior:

```text
noExternalExposureLayerIsIntroducedByMu019 scans all main source and forbids
Spring Web globally.
```

Required replacement:

```text
webImportsForbiddenOutsideHttpAdapterPackage
forbiddenTechnologiesAbsentFromAllMainSource
```

Required intent:

```text
Spring Web imports are allowed only under adapter.northbound.http.
Other technologies remain globally forbidden: GraphQL, WebSocket, gRPC,
ConnectRPC, NATS/JetStream and MCP direct binding unless separately authorized.
```

Representative implementation:

```java
@Test
void webImportsForbiddenOutsideHttpAdapterPackage() throws IOException {
    List<String> forbiddenWeb = List.of(
        "org.springframework.web",
        "@RestController",
        "@Controller",
        "@RequestMapping",
        "@GetMapping",
        "@PostMapping",
        "SseEmitter",
        "ResponseEntity"
    );
    List<String> allowedPaths = List.of("adapter/northbound/http");
    try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/sovereign/connect"))) {
        List<String> violations = paths
            .filter(p -> p.toString().endsWith(".java"))
            .filter(p -> allowedPaths.stream().noneMatch(allowed -> p.toString().contains(allowed)))
            .filter(p -> containsAny(p, forbiddenWeb))
            .map(Path::toString)
            .toList();
        assertThat(violations)
            .as("Spring Web imports are only allowed under adapter.northbound.http")
            .isEmpty();
    }
}
```

#### Test 2 — NorthboundFacadeSpringContextTest.noHttpControllerBeanIsIntroduced

Current stale behavior:

```text
Asserts no controller bean exists.
```

Required replacement:

```text
httpControllerBeansAreOnlyInHttpAdapterPackage
```

Representative implementation:

```java
@Test
void httpControllerBeansAreOnlyInHttpAdapterPackage() {
    Map<String, Object> controllerBeans =
        ((ListableBeanFactory) context).getBeansWithAnnotation(
            org.springframework.stereotype.Controller.class);
    for (Object bean : controllerBeans.values()) {
        assertThat(bean.getClass().getPackageName())
            .as("Controller bean %s must be in adapter.northbound.http",
                bean.getClass().getSimpleName())
            .startsWith("com.sovereign.connect.adapter.northbound.http");
    }
}
```

#### Test 3 — NorthboundFacadeArchitectureTest

No change required.

Rationale:

```text
It is already scoped to src/main/java/com/sovereign/connect/core/northbound.
It should continue to prove that core.northbound remains HTTP-free.
```

---

### CSA-HTTP-005 — Exact controller package/class split

Package:

```text
com.sovereign.connect.adapter.northbound.http
```

Classes:

```text
ScNorthboundTopologyHttpController
  - handles Profile A routes.
  - injects only ScCoreNorthboundFacade.

ScNorthboundTemporalHttpController
  - handles Profile B temporal/diagnostics routes.
  - injects only ScCoreNorthboundFacade.

ScNorthboundHttpResponseMapper
  - maps ScNorthboundStatus to HTTP status.
  - wraps ScNorthboundResponse<T> in ResponseEntity<ScNorthboundResponse<T>>.
  - no Spring stereotype required.

ScNorthboundHttpConfiguration
  - @Configuration.
  - @EnableConfigurationProperties(ScNorthboundHttpProperties.class).
  - owns HTTP-specific configuration only.

ScNorthboundHttpProperties
  - @ConfigurationProperties(prefix = "sc.northbound.http").

ScNorthboundCancelTemporalActHttpBody
  - adapter-local record for cancel request body.
```

Optional / deferred:

```text
ScNorthboundHttpExceptionHandler
  - may be deferred unless MIR requires canonical body for deserialization errors.

ScNorthboundSseController
  - not created in this seed because SSE remains SSE-P0 / deferred.
```

---

### CSA-HTTP-006 — Request record shapes for TemporalAct create/cancel/list

#### createSignalTemporalAct

Use existing core northbound request record directly:

```java
public record NorthboundCreateSignalTemporalActRequest(
    Instant dueAt,
    String label,
    String signalKind,
    String notificationTargetRef,
    String createdByRef,
    String idempotencyKey
) {}
```

Controller route:

```text
POST /sc/v1/habitats/{habitatId}/temporal-acts
```

Seed interpretation:

```text
The route is signal-only because the facade exposes createSignalTemporalAct only.
The generic path is accepted for this seed.
A future ActionTemporalAct descent must explicitly decide between:
  - /temporal-acts/signal and /temporal-acts/action;
  - a body discriminator;
  - or a versioned route split.
```

#### cancelTemporalAct

Do not require `temporalActId` in both path and body.

Create adapter-local request body:

```java
record ScNorthboundCancelTemporalActHttpBody(
    String requestedByRef,
    String idempotencyKey,
    String reason
) {}
```

Controller route:

```text
POST /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
```

Controller constructs:

```java
new NorthboundCancelTemporalActRequest(
    temporalActId,
    body.requestedByRef(),
    body.idempotencyKey(),
    body.reason()
)
```

#### listTemporalActs

Canonical query shape:

```text
GET /sc/v1/habitats/{habitatId}/temporal-acts?mode=ACTIVE&maxResults=50
```

Rules:

```text
- Query param is mode, not filter.
- Separator is &, not |.
- Supported values match NorthboundTemporalActFilter.Mode:
  ACTIVE, TERMINAL, MISFIRED.
- Matching is case-insensitive by normalizing to upper case.
- Unknown mode maps to ScNorthboundResponse.invalidRequest(...), HTTP 400.
- ACTIVE mode currently ignores maxResults inside the facade; controller must not compensate.
```

---

### CSA-HTTP-007 — SSE disposition

Confirmed:

```text
SSE-P0 — deferred.
```

Rationale:

```text
No current SC-C source provides a canonical live event stream suitable for SSE.
TemporalEngineHealth is not a publication mechanism.
Outbox storage is not a northbound event stream authority.
No topology-change event read model exists in the baseline.
```

Therefore:

```text
No ScNorthboundSseController in the first HTTP binding seed.
No SseEmitter in the first HTTP binding seed.
No WebFlux.
No polling heartbeat pretending to be a canonical event stream.
```

---

### CSA-HTTP-008 — Exact test classes and fixture strategy

New tests:

```text
src/test/java/com/sovereign/connect/adapter/northbound/http/
  ScNorthboundTopologyHttpControllerTest.java
  ScNorthboundTemporalHttpControllerTest.java
  ScNorthboundHttpSerializationTest.java
  ScNorthboundHttpArchitectureTest.java
  ScNorthboundHttpSpringContextTest.java
```

#### ScNorthboundTopologyHttpControllerTest

Strategy:

```text
@WebMvcTest(ScNorthboundTopologyHttpController.class)
@MockBean ScCoreNorthboundFacade facade
MockMvc assertions only.
No SQLite.
No Flyway.
```

Required coverage:

```text
GET /topology -> 200 OK body status OK.
GET /devices/{deviceId} -> 404 when facade returns NOT_FOUND.
GET /devices/{deviceId}/health -> 200 with device health payload.
GET /devices/{deviceId}/runtime-state -> 404 when facade returns NOT_FOUND.
GET /endpoints/{endpointId}/runtime-state -> 501 when facade returns UNSUPPORTED_PROFILE.
GET /locations/{roomOrZoneId}/devices -> 200.
```

#### ScNorthboundTemporalHttpControllerTest

Strategy:

```text
@WebMvcTest(ScNorthboundTemporalHttpController.class)
@MockBean ScCoreNorthboundFacade facade
```

Required coverage:

```text
POST /temporal-acts valid body -> 202 when facade returns ACCEPTED.
POST /temporal-acts validation error -> 422 when facade returns VALIDATION_ERROR.
POST /temporal-acts/{temporalActId}/cancel -> 200 when facade returns CANCELLED.
GET /temporal-acts?mode=ACTIVE -> 200.
GET /temporal-acts?mode=INVALID -> 400 and facade not invoked.
GET /recovery/status -> 501 when facade returns UNSUPPORTED_PROFILE.
GET /diagnostics -> 200 with canonical response body preserved.
```

#### ScNorthboundHttpSerializationTest

Strategy:

```text
Standalone ObjectMapper unit test using the same construction as configuration.
```

Required coverage:

```text
Instant serializes as ISO-8601 string.
NorthboundCreateSignalTemporalActRequest dueAt deserializes from ISO-8601.
ScNorthboundResponse<T> serializes status/payload/warnings/error.
ScNorthboundError(code, message, source) serializes all three fields.
ScNorthboundWarning(code, message, source) serializes all three fields.
UNKNOWN_PENDING_NORMALIZATION remains visible in response body.
```

#### ScNorthboundHttpArchitectureTest

Required coverage:

```text
webImportsForbiddenOutsideHttpAdapterPackage.
forbiddenTechnologiesAbsentFromAllMainSource.
httpAdapterDelegatesOnlyToFacade.
httpAdapterDoesNotImportRepositoriesOrDomainServices.
httpAdapterDoesNotLeakEibProjectionOrAuthorityConcepts.
```

#### ScNorthboundHttpSpringContextTest

Strategy:

```text
@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite:target/http-context.sqlite"})
```

Required coverage:

```text
contextLoadsWithHttpControllers.
topologyControllerBeanExistsAndIsProperlyTyped.
temporalControllerBeanExistsAndIsProperlyTyped.
httpControllerBeansAreOnlyInHttpAdapterPackage.
openApiEndpointResolvesToNonEmptyDocument.
```

Existing tests:

```text
NorthboundFacadeBehavioralTest       — no web dependency; no change required.
NorthboundFacadeArchitectureTest     — core/northbound scoped; no change required.
NorthboundFacadeSpringContextTest    — stale controller-absence assertion must change.
NorthboundFacadeNegativeBoundaryTest — global no-web assertion must be replaced.
```

---

### CSA-HTTP-009 — Server config / @ConfigurationProperties

Create:

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

Notes:

```text
server.port remains standard Spring Boot config.
Default 8080 applies unless overridden.
No custom port property required in this seed.
```

---

### CSA-HTTP-010 — Call-site and route-surface risks

#### Risk 1 — ObjectMapper ambiguity / serialization drift

Disposition:

```text
Not proven as duplicate-bean failure because Boot backs off when ObjectMapper exists.
Still requires explicit context and serialization tests.
@Primary + disable timestamps is recommended and allowed.
```

#### Risk 2 — NorthboundFacadeNegativeBoundaryTest breaks

Disposition:

```text
Definitive. Replace global no-web assertion with boundary-scoped assertions.
```

#### Risk 3 — NorthboundFacadeSpringContextTest.noHttpControllerBeanIsIntroduced fails

Disposition:

```text
Definitive. Replace with controller package assertion.
```

#### Risk 4 — cancelTemporalAct path/body conflict

Disposition:

```text
Resolved by adapter-local ScNorthboundCancelTemporalActHttpBody.
```

#### Risk 5 — listTemporalActs unknown mode

Disposition:

```text
Controller maps invalid mode to INVALID_REQUEST / HTTP 400 before invoking facade.
```

#### Risk 6 — Instant serialization/deserialization

Disposition:

```text
Use JavaTimeModule via findAndRegisterModules and disable WRITE_DATES_AS_TIMESTAMPS.
Test ISO-8601 serialization/deserialization.
```

#### Risk 7 — ACTIVE mode ignores maxResults

Disposition:

```text
Pre-existing facade behavior. Controller must pass the filter as-is and not compensate.
Document as retained behavior.
```

#### Risk 8 — @WebMvcTest isolation

Disposition:

```text
Controllers must inject only ScCoreNorthboundFacade. @WebMvcTest + @MockBean facade
acts as guardrail.
```

#### Risk 9 — OpenAPI endpoint drift

Disposition:

```text
Add /v3/api-docs test. No UI commitment.
```

---

## 5. HTTP status mapping

The execution package must implement the SDD mapping through
`ScNorthboundHttpResponseMapper`.

Canonical mapping:

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
- ScNorthboundResponse<T> remains the response body for all statuses.
- HTTP status communicates transport-level outcome.
- Body status communicates canonical northbound status.
- UNSUPPORTED_PROFILE and DEFERRED_SC_B_REQUIRED must not map to 200.
- UNKNOWN_PENDING_NORMALIZATION may map to 200 only if the body status remains visible.
```

---

## 6. Surface inventory matrix

| Artifact | Expected | Found | Disposition |
|---|---:|---:|---|
| `ScCoreNorthboundFacade` | yes | yes, 21 methods | Inject into controllers only |
| `ScNorthboundResponse<T>` + status/error/warning | yes | yes | Serialize as HTTP body |
| `spring-boot-starter-web` | yes | no | Add to `pom.xml` |
| `spring-boot-starter-webflux` | no | no | Do not add |
| `springdoc-openapi-starter-webmvc-api` | yes | no | Add, version pinned in MIR/package |
| `ObjectMapper` serialization hardening | yes | partial | Add `@Primary` + disable timestamps if tests pass |
| `adapter.northbound.http` package | yes | no | Create new |
| `ScNorthboundHttpResponseMapper` | yes | no | Create new |
| `ScNorthboundHttpProperties` | yes | no | Create new |
| `ScNorthboundCancelTemporalActHttpBody` | yes | no | Create new |
| `NorthboundFacadeNegativeBoundaryTest` patched | yes | no | Replace stale global scan |
| `NorthboundFacadeSpringContextTest` patched | yes | no | Replace stale no-controller assertion |
| `NorthboundFacadeArchitectureTest` | no change | correct scope | No action |
| SSE controller | no | absent | Confirmed deferred |
| SC-B / SC-D / EIB / Projection imports | no | absent | Preserve absence |

---

## 7. Requirement coverage matrix

| SDD obligation | Resolution | Section |
|---|---|---|
| CSA-HTTP-001 exact Spring Web dependency | `spring-boot-starter-web` | §4.1 |
| CSA-HTTP-002 exact OpenAPI strategy | SpringDoc WebMVC API + `/v3/api-docs` test | §4.2 |
| CSA-HTTP-003 ObjectMapper behavior | serialization-stability patch + tests | §4.3 |
| CSA-HTTP-004 architecture tests to patch | two stale tests patched; one unchanged | §4.4 |
| CSA-HTTP-005 controller class split | 2 controllers + mapper + config + properties | §4.5 |
| CSA-HTTP-006 request record shapes | create direct; cancel body adapter; list mode query | §4.6 |
| CSA-HTTP-007 SSE disposition | SSE-P0 confirmed | §4.7 |
| CSA-HTTP-008 tests and fixtures | 5 new test classes | §4.8 |
| CSA-HTTP-009 config/properties | `ScNorthboundHttpProperties` | §4.9 |
| CSA-HTTP-010 call-site/route risks | 9 risks dispositioned | §4.10 |

---

## 8. Search ledger

```text
pom.xml
  rg "spring-boot-starter-web|webflux|springdoc|spring-security"
    -> none found

src/main/java
  rg "@RestController|@Controller|@RequestMapping|@GetMapping|@PostMapping|ResponseEntity|SseEmitter"
    -> none found in main source

config/**
  rg "ObjectMapper|@Primary|@ConditionalOnMissingBean"
    -> objectMapper() found in TemporalEngineConfiguration, no @Primary

src/main/**
  rg "@ConfigurationProperties"
    -> TemporalEngineProperties only

application.yml + src/main/**
  rg "sc.northbound"
    -> not present

core/northbound/**
  ScCoreNorthboundFacade methods confirmed: 21
  getDeviceRuntimeState -> functional OK/NOT_FOUND
  getEndpointRuntimeState -> UNSUPPORTED_PROFILE
  getRecoveryStatus -> UNSUPPORTED_PROFILE
  listTemporalActs -> ACTIVE ignores maxResults in current facade behavior

src/test/java/**
  NorthboundFacadeNegativeBoundaryTest -> global no-web scan, stale
  NorthboundFacadeSpringContextTest -> asserts no controllers, stale
  NorthboundFacadeArchitectureTest -> core/northbound scoped, still valid
  @WebMvcTest / MockMvc -> not yet present
```

---

## 9. Expected MIR scope

Candidate MIR:

```text
MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001
```

Suggested operational slot:

```text
MU-021
```

Suggested branch:

```text
feat/sc-c-mir-021-northbound-http-sse-binding
```

Suggested commit:

```text
feat(sc-c): add northbound http sse binding seed
```

MIR must authorize:

```text
- dependency patch: spring-boot-starter-web;
- dependency patch: springdoc-openapi-starter-webmvc-api;
- adapter package under com.sovereign.connect.adapter.northbound.http;
- HTTP controllers delegating only to ScCoreNorthboundFacade;
- ScNorthboundHttpResponseMapper;
- ScNorthboundHttpProperties;
- ObjectMapper serialization hardening and tests;
- boundary-scoped architecture tests;
- MockMvc/WebMvc tests;
- /v3/api-docs test;
- SSE-P0 deferred disposition;
- no EIB, View Composer, SC-B runtime, SC-D adapter runtime, MCP, gRPC or GraphQL.
```

---

## 10. Retained debts after this CSA

```text
DEBT-HTTP-001 — SSE remains SSE-P0 / deferred.
DEBT-HTTP-002 — No authentication/authorization layer in HTTP seed; local-trusted only.
DEBT-HTTP-003 — Swagger UI/static OpenAPI artifact deferred.
DEBT-HTTP-004 — EIB runtime consumption still requires EIB-side contract/SDD.
DEBT-HTTP-005 — gRPC/ConnectRPC binding remains undecided/downstream.
DEBT-HTTP-006 — MCP adapter remains separate AI/tool/admin exposure track.
DEBT-HTTP-007 — Unknown ACTIVE maxResults behavior remains inherited from facade.
```

---

## 11. Final dictum

```text
Post-SDD CSA verdict: Approvable.
```

The codebase is ready for a MIR authorizing the first HTTP binding seed.

The MIR and execution package must preserve the following non-negotiable boundary:

```text
HTTP adapts ScCoreNorthboundFacade.
HTTP does not become SC-C semantic authority.
HTTP does not bypass Northbound.
HTTP does not implement EIB.
HTTP does not implement Projection / Effective View.
HTTP does not implement SC-B or SC-D.
```

