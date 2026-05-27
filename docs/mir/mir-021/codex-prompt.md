# Codex Prompt — MU-021 Northbound HTTP/SSE Binding Seed

You are implementing `MU-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001` on branch:

```text
feat/sc-c-mir-021-northbound-http-sse-binding
```

Read the execution package in this order before writing any code:

```text
docs/mir/mir-021/context.md          ← authoritative; contains exact signatures and paste-ready code
docs/mir/mir-021/acceptance-map.md   ← maps each AC to the required evidence
docs/mir/mir-021/MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001.md
```

---

## Objective

Add an HTTP/OpenAPI adapter over `ScCoreNorthboundFacade`. This is a routing adapter.
It must not alter any Northbound semantic, must not bypass the facade, and must not
modify production `core.northbound` classes. New exposure code must live only under
`adapter.northbound.http`; the only existing production code change is the ObjectMapper
patch in `TemporalEngineConfiguration`.

---

## Implementation order

Follow this order exactly. Run `mvn test` at every STOP before proceeding.

### Step 1 — Dependencies

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
    <version>2.5.0</version>
</dependency>
```

Do not add webflux, springdoc-ui, gRPC, MCP, GraphQL, WebSocket, NATS.

### Step 2 — ObjectMapper patch

In `src/main/java/com/sovereign/connect/config/TemporalEngineConfiguration.java`,
replace the `objectMapper()` bean (currently `@Bean` only, no `@Primary`) with:

```java
@Bean
@Primary
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
}
```

Add if not already present:
```java
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Primary;
```

Check whether `import org.springframework.context.annotation.Primary` already exists
in the file (it does for other beans) — do not duplicate it.

**STOP-2: `mvn test` — all 196 tests must pass before Step 3.**

### Step 3 — Patch stale tests

These two tests will fail as soon as controller files exist. Patch them now,
before creating any controller files.

#### NorthboundFacadeNegativeBoundaryTest

File: `src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeNegativeBoundaryTest.java`

Replace only the method `noExternalExposureLayerIsIntroducedByMu019`.
Do NOT touch `defaultFacadeConstructorUsesOnlyApprovedCollaborators`.

New method body (rename to `webImportsForbiddenOutsideHttpAdapterPackage` or keep old name):

```java
@Test
void webImportsForbiddenOutsideHttpAdapterPackage() throws IOException {
    List<String> webTokens = List.of(
        "@RestController", "@Controller",
        "@MessageMapping", "org.springframework.web",
        "graphql", "io.grpc", "connectrpc", "io.nats", "JetStream"
    );
    List<String> allowedPaths = List.of("adapter/northbound/http");
    try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/sovereign/connect"))) {
        List<String> violations = paths
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> allowedPaths.stream()
                .noneMatch(allowed -> path.toString().contains(allowed)))
            .filter(path -> containsAny(path, webTokens))
            .map(Path::toString)
            .toList();
        assertThat(violations)
            .as("Spring Web imports are only allowed under adapter.northbound.http")
            .isEmpty();
    }
}
```

#### NorthboundFacadeSpringContextTest

File: `src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeSpringContextTest.java`

Replace only the method `noHttpControllerBeanIsIntroduced`.
Do NOT touch the other three methods in this class.

New method body:

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

**STOP-3: `mvn test` — all 196 tests must pass before Step 4.**

### Step 4 — Create adapter package and support classes

Create all in `src/main/java/com/sovereign/connect/adapter/northbound/http/`:

#### ScNorthboundHttpResponseMapper.java (package-private, stateless)

```java
package com.sovereign.connect.adapter.northbound.http;

import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.ScNorthboundStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ScNorthboundHttpResponseMapper {

    private ScNorthboundHttpResponseMapper() {}

    static <T> ResponseEntity<ScNorthboundResponse<T>> toResponseEntity(
            ScNorthboundResponse<T> response) {
        return ResponseEntity.status(toHttpStatus(response.status())).body(response);
    }

    private static HttpStatus toHttpStatus(ScNorthboundStatus status) {
        return switch (status) {
            case OK                            -> HttpStatus.OK;
            case CREATED                       -> HttpStatus.CREATED;
            case ACCEPTED                      -> HttpStatus.ACCEPTED;
            case CANCELLED                     -> HttpStatus.OK;
            case NOT_FOUND                     -> HttpStatus.NOT_FOUND;
            case INVALID_REQUEST               -> HttpStatus.BAD_REQUEST;
            case INVALID_CANONICAL_ID          -> HttpStatus.BAD_REQUEST;
            case VALIDATION_ERROR              -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNSUPPORTED_PROFILE           -> HttpStatus.NOT_IMPLEMENTED;
            case DEFERRED_SC_B_REQUIRED        -> HttpStatus.SERVICE_UNAVAILABLE;
            case UNKNOWN_PENDING_NORMALIZATION -> HttpStatus.OK;
            case INTERNAL_ERROR                -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
```

#### ScNorthboundCancelTemporalActHttpBody.java (package-private record)

```java
package com.sovereign.connect.adapter.northbound.http;

record ScNorthboundCancelTemporalActHttpBody(
    String requestedByRef,
    String idempotencyKey,
    String reason
) {}
```

#### ScNorthboundHttpProperties.java

```java
package com.sovereign.connect.adapter.northbound.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sc.northbound.http")
public record ScNorthboundHttpProperties(
    boolean enabled,
    String basePath,
    String exposureProfile
) {}

// Note: `/sc/v1` remains a static seed route prefix in MU-021.
// `basePath` records the chosen seed prefix; runtime-remappable base paths are deferred.
// `enabled` is enforced by @ConditionalOnProperty on both controller classes.
```

#### ScNorthboundHttpConfiguration.java

```java
package com.sovereign.connect.adapter.northbound.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScNorthboundHttpProperties.class)
public class ScNorthboundHttpConfiguration {}
```

Add to `src/main/resources/application.yml`:
```yaml
sc:
  northbound:
    http:
      enabled: true
      base-path: /sc/v1
      exposure-profile: local-trusted
```

### Step 5 — Create controllers

Both controllers use `@RestController`, `@ConditionalOnProperty(prefix = "sc.northbound.http", name = "enabled", havingValue = "true", matchIfMissing = true)`, and `@RequestMapping("/sc/v1/habitats/{habitatId}")`.
Both inject only `ScCoreNorthboundFacade` through a constructor. Import `org.springframework.boot.autoconfigure.condition.ConditionalOnProperty`.
Every method body is a single `return ScNorthboundHttpResponseMapper.toResponseEntity(facade.xxx(...))`.

#### ScNorthboundTopologyHttpController.java — all 14 Profile A routes

Route table (path relative to `/sc/v1/habitats/{habitatId}`):

```text
GET /topology                             → facade.getTopologySnapshot(habitatId)
GET /topology/version                     → facade.getTopologyVersion(habitatId)
GET /rooms                                → facade.listRooms(habitatId)
GET /zones                                → facade.listZones(habitatId)
GET /devices                              → facade.listDevices(habitatId)
GET /devices/{deviceId}                   → facade.getDevice(habitatId, deviceId)
GET /devices/{deviceId}/health            → facade.getDeviceHealth(habitatId, deviceId)
GET /devices/{deviceId}/runtime-state     → facade.getDeviceRuntimeState(habitatId, deviceId)
GET /endpoints                            → facade.listEndpoints(habitatId)
GET /endpoints/{endpointId}               → facade.getEndpoint(habitatId, endpointId)
GET /endpoints/{endpointId}/health        → facade.getEndpointHealth(habitatId, endpointId)
GET /endpoints/{endpointId}/runtime-state → facade.getEndpointRuntimeState(habitatId, endpointId)
GET /locations/{roomOrZoneId}/devices     → facade.listDevicesLocatedIn(habitatId, roomOrZoneId)
GET /locations/{roomOrZoneId}/endpoints   → facade.listEndpointsLocatedIn(habitatId, roomOrZoneId)
```

Note: `getDeviceRuntimeState` is functional and returns OK or NOT_FOUND.
`getEndpointRuntimeState` returns UNSUPPORTED_PROFILE → HTTP 501. Both are plain delegates.

#### ScNorthboundTemporalHttpController.java — all 7 Profile B routes

```text
GET  /temporal/runtime-status             → facade.getTemporalRuntimeStatus(habitatId)
GET  /recovery/status                     → facade.getRecoveryStatus(habitatId)
GET  /diagnostics                         → facade.getNorthboundDiagnostics(habitatId)
GET  /temporal-acts/{temporalActId}       → facade.getTemporalAct(habitatId, temporalActId)
POST /temporal-acts                       → facade.createSignalTemporalAct(habitatId, request)
POST /temporal-acts/{temporalActId}/cancel → assembled from path + body (see below)
GET  /temporal-acts                       → with mode/maxResults query params (see below)
```

For `listTemporalActs` — use this exact method body:

```java
@GetMapping("/temporal-acts")
public ResponseEntity<ScNorthboundResponse<List<NorthboundTemporalActView>>> listTemporalActs(
    @PathVariable String habitatId,
    @RequestParam(required = false) String mode,
    @RequestParam(required = false) Integer maxResults
) {
    NorthboundTemporalActFilter.Mode resolvedMode = null;
    if (mode != null) {
        try {
            resolvedMode = NorthboundTemporalActFilter.Mode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ScNorthboundHttpResponseMapper.toResponseEntity(
                ScNorthboundResponse.invalidRequest("INVALID_MODE",
                    "unsupported mode value: " + mode));
        }
    }
    return ScNorthboundHttpResponseMapper.toResponseEntity(
        facade.listTemporalActs(habitatId,
            new NorthboundTemporalActFilter(resolvedMode, maxResults)));
}
```

For `cancelTemporalAct` — use this exact method body:

```java
@PostMapping("/temporal-acts/{temporalActId}/cancel")
public ResponseEntity<ScNorthboundResponse<NorthboundTemporalActView>> cancelTemporalAct(
    @PathVariable String habitatId,
    @PathVariable String temporalActId,
    @RequestBody ScNorthboundCancelTemporalActHttpBody body
) {
    return ScNorthboundHttpResponseMapper.toResponseEntity(
        facade.cancelTemporalAct(habitatId,
            new NorthboundCancelTemporalActRequest(
                temporalActId,
                body.requestedByRef(),
                body.idempotencyKey(),
                body.reason()
            )));
}
```

**STOP-4: `mvn test` — all tests must pass before Step 6.**

### Step 6 — Create test classes

Create all in `src/test/java/com/sovereign/connect/adapter/northbound/http/`.

Use the exact skeletons from `context.md` §10. Key points:

- `ScNorthboundTopologyHttpControllerTest`: `@WebMvcTest(ScNorthboundTopologyHttpController.class)` + `@MockBean ScCoreNorthboundFacade`
- `ScNorthboundTemporalHttpControllerTest`: `@WebMvcTest(ScNorthboundTemporalHttpController.class)` + `@MockBean ScCoreNorthboundFacade`; include `cancelTemporalAct` ArgumentCaptor test and `mode=BOGUS → 400 without facade call` test
- `ScNorthboundHttpSerializationTest`: standalone `ObjectMapper` unit test; use the exact mapper construction from `context.md` §10.3
- `ScNorthboundHttpArchitectureTest`: file scan tests; use exact helper from `context.md` §10.4
- `ScNorthboundHttpSpringContextTest`: `@SpringBootTest` + `@AutoConfigureMockMvc`; include `/v3/api-docs` test

**STOP-FINAL: `mvn test` — expected baseline 196 + at least 30 new tests, 0 failures, 0 errors.**

---

## Hard boundaries

Do not implement or create:

```text
ScNorthboundSseController    — SSE is SSE-P0/deferred
SseEmitter                   — same
Flux / Mono                  — no WebFlux
EIB                          — downstream
View Composer / Projection   — downstream
Session / Identity / Auth    — downstream
SC-B runtime / SC-D adapters — downstream
gRPC / MCP / GraphQL         — downstream
```

Do not modify:

```text
src/main/java/com/sovereign/connect/core/northbound/**
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeArchitectureTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeBehavioralTest.java
```

---

## After implementation

Update `docs/mir/mir-021/implementation-report.md` with:

```text
- branch and commit hash
- complete file list (created / modified)
- dependency changes
- mvn test output summary (exact test count and result)
- acceptance criteria status (AC-021-001 through AC-021-045)
- SpringDoc version used (if different from 2.5.0, explain why)
- confirmation: SSE remains SSE-P0/deferred
- confirmation: EIB remains unimplemented
- confirmation: HTTP binding only exposes SC-C Northbound
- retained debts after MU-021
```
