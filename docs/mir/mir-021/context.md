# MU-021 Execution Context — SC-C Northbound HTTP/SSE Binding Seed

```text
Document ID:  CONTEXT-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001
Version:      v0.3.1-reviewed
Status:       Execution package context
MU:           MU-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001
Slot:         MU-021
Branch:       feat/sc-c-mir-021-northbound-http-sse-binding
```

---

## 0. Purpose

Implement the first HTTP/OpenAPI exposure binding over the already-hardened in-process
`ScCoreNorthboundFacade`. This is a pure adapter seed. It must not alter any semantic
owned by Northbound, must not modify production `core.northbound` classes, and must
only modify existing production code where explicitly listed: `pom.xml`,
`application.yml`, and `TemporalEngineConfiguration.objectMapper()`. It may create new
HTTP adapter classes only under `adapter.northbound.http` and may patch the two stale
Northbound tests listed below.

---

## 1. Exact baseline facts

### 1.1 Facade interface — full method signature table

Source: `src/main/java/com/sovereign/connect/core/northbound/ScCoreNorthboundFacade.java`

```java
// Profile A — 14 topology / runtime observation methods
ScNorthboundResponse<NorthboundTopologySnapshot>        getTopologySnapshot(String habitatId)
ScNorthboundResponse<NorthboundTopologyVersionView>     getTopologyVersion(String habitatId)
ScNorthboundResponse<NorthboundDeviceView>              getDevice(String habitatId, String deviceId)
ScNorthboundResponse<NorthboundEndpointView>            getEndpoint(String habitatId, String endpointId)
ScNorthboundResponse<List<NorthboundRoomView>>          listRooms(String habitatId)
ScNorthboundResponse<List<NorthboundZoneView>>          listZones(String habitatId)
ScNorthboundResponse<List<NorthboundDeviceView>>        listDevices(String habitatId)
ScNorthboundResponse<List<NorthboundEndpointView>>      listEndpoints(String habitatId)
ScNorthboundResponse<List<NorthboundDeviceView>>        listDevicesLocatedIn(String habitatId, String roomOrZoneId)
ScNorthboundResponse<List<NorthboundEndpointView>>      listEndpointsLocatedIn(String habitatId, String roomOrZoneId)
ScNorthboundResponse<NorthboundEndpointHealthView>      getEndpointHealth(String habitatId, String endpointId)
ScNorthboundResponse<NorthboundDeviceHealthView>        getDeviceHealth(String habitatId, String deviceId)
ScNorthboundResponse<NorthboundRuntimeStateView>        getDeviceRuntimeState(String habitatId, String deviceId)
ScNorthboundResponse<NorthboundRuntimeStateView>        getEndpointRuntimeState(String habitatId, String endpointId)

// Profile B — 7 temporal / diagnostics methods
ScNorthboundResponse<NorthboundTemporalRuntimeStatusView> getTemporalRuntimeStatus(String habitatId)
ScNorthboundResponse<NorthboundRecoveryStatusView>        getRecoveryStatus(String habitatId)
ScNorthboundResponse<NorthboundDiagnosticsView>           getNorthboundDiagnostics(String habitatId)
ScNorthboundResponse<NorthboundTemporalActView>           createSignalTemporalAct(String habitatId, NorthboundCreateSignalTemporalActRequest request)
ScNorthboundResponse<NorthboundTemporalActView>           cancelTemporalAct(String habitatId, NorthboundCancelTemporalActRequest request)
ScNorthboundResponse<NorthboundTemporalActView>           getTemporalAct(String habitatId, String temporalActId)
ScNorthboundResponse<List<NorthboundTemporalActView>>     listTemporalActs(String habitatId, NorthboundTemporalActFilter filter)
```

### 1.2 ScNorthboundResponse<T> — exact record shape and factory methods

Source: `src/main/java/com/sovereign/connect/core/northbound/ScNorthboundResponse.java`

```java
public record ScNorthboundResponse<T>(
    ScNorthboundStatus status,
    T payload,
    List<ScNorthboundWarning> warnings,
    ScNorthboundError error
) { ... }
```

Factory methods available (use these in controller tests and mapper implementation):
```java
ScNorthboundResponse.ok(payload)
ScNorthboundResponse.accepted(payload)
ScNorthboundResponse.cancelled(payload)
ScNorthboundResponse.notFound("NOT_FOUND", "message")
ScNorthboundResponse.invalidRequest("code", "message")
ScNorthboundResponse.validationError("code", "message")
ScNorthboundResponse.unsupportedProfile("message")
ScNorthboundResponse.unknownPendingNormalization("message")
ScNorthboundResponse.internalError("code", "message")
```

### 1.3 ScNorthboundError and ScNorthboundWarning — exact record shapes

```java
public record ScNorthboundError(String code, String message, String source) {}
public record ScNorthboundWarning(String code, String message, String source) {}
```

Both have exactly three fields. Serialization tests must verify `source` is present.

### 1.4 ScNorthboundStatus enum — complete ordered list

```java
public enum ScNorthboundStatus {
    OK, CREATED, ACCEPTED, CANCELLED, NOT_FOUND,
    INVALID_REQUEST, INVALID_CANONICAL_ID, UNSUPPORTED_PROFILE,
    DEFERRED_SC_B_REQUIRED, UNKNOWN_PENDING_NORMALIZATION,
    VALIDATION_ERROR, INTERNAL_ERROR
}
```

### 1.5 Temporal request records — exact fields

```java
// NorthboundCreateSignalTemporalActRequest — use directly as @RequestBody
public record NorthboundCreateSignalTemporalActRequest(
    Instant dueAt,
    String label,
    String signalKind,
    String notificationTargetRef,
    String createdByRef,
    String idempotencyKey
) {}

// NorthboundCancelTemporalActRequest — construct inside controller from path + adapter body
public record NorthboundCancelTemporalActRequest(
    String temporalActId,
    String requestedByRef,
    String idempotencyKey,
    String reason
) {}

// NorthboundTemporalActFilter
public record NorthboundTemporalActFilter(Mode mode, Integer maxResults) {
    public enum Mode { ACTIVE, TERMINAL, MISFIRED }
}
```

### 1.6 NorthboundTemporalActView — exact record shape (for serialization tests)

```java
public record NorthboundTemporalActView(
    String temporalActId, String habitatId, String status,
    Instant dueAt, String payloadKind, String label, String signalKind,
    String notificationTargetRef, String createdByRef,
    Instant createdAt, Instant updatedAt, Instant firedAt,
    Instant terminalAt, String terminalReason
) {}
```

### 1.7 NorthboundTopologySnapshot — readAt field (for serialization tests)

```java
public record NorthboundTopologySnapshot(
    String habitatId, String topologyVersionValue, String topologyVersionScope,
    List<NorthboundRoomView> rooms, List<NorthboundZoneView> zones,
    List<NorthboundDeviceView> devices, List<NorthboundEndpointView> endpoints,
    Instant readAt
) { ... }
```

### 1.8 TemporalEngineConfiguration.objectMapper() — current body (before patch)

Source: `src/main/java/com/sovereign/connect/config/TemporalEngineConfiguration.java` line ~53

```java
@Bean
public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
}
```

Current state: `@Bean` only. No `@Primary`. No timestamp disable.
Required state after patch:

```java
@Bean
@Primary
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
}
```

Required new import in `TemporalEngineConfiguration.java`:
```java
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Primary;
```

The four existing injection points (SQLiteTemporalActRepository, TemporalActService,
TemporalEngineService, TemporalActApplicationService) all receive ObjectMapper by type.
`@Primary` does not change what they receive — it changes resolution priority when
multiple candidates exist. No changes needed to those call sites.

---

## 2. Implicit invariants elevated to explicit rules

### INV-HTTP-001: Controllers are pure routing delegates

Controllers must contain zero business logic. Every method body must be a single
expression: one facade call wrapped in `ScNorthboundHttpResponseMapper.toResponseEntity(...)`.
The only exception is the `listTemporalActs` mode parsing and the cancel body assembly —
both are adapter-level concerns, not business logic.

Anti-pattern (never do this):
```java
// WRONG: business logic in controller
if (facade.getDevice(habitatId, deviceId).status() == ScNorthboundStatus.OK) {
    // ...
}
```

Correct pattern:
```java
// RIGHT: pure delegation
return ScNorthboundHttpResponseMapper.toResponseEntity(
    facade.getDevice(habitatId, deviceId));
```

### INV-HTTP-002: ScNorthboundResponse<T> is never unwrapped

The HTTP response body must always be `ScNorthboundResponse<T>` as the JSON root.
Never extract `payload` and return it directly as the body.
Never strip `status`, `warnings` or `error` from the response.

The controller return type for every route is:
```java
ResponseEntity<ScNorthboundResponse<T>>
```

where `T` is the exact type from the facade method signature.

### INV-HTTP-003: ScNorthboundHttpResponseMapper is stateless

`ScNorthboundHttpResponseMapper` must be a utility class (no instantiation, static methods
only, no Spring stereotype annotation). It must not inject any Spring bean.

Its only responsibility is: given a `ScNorthboundResponse<T>`, return a
`ResponseEntity<ScNorthboundResponse<T>>` with the correct HTTP status code.

Signature:
```java
static <T> ResponseEntity<ScNorthboundResponse<T>> toResponseEntity(ScNorthboundResponse<T> response)
```

### INV-HTTP-004: cancelTemporalAct path/body split is not optional

The facade method `cancelTemporalAct(String habitatId, NorthboundCancelTemporalActRequest request)`
takes a `NorthboundCancelTemporalActRequest` that contains `temporalActId` as its first field.
The HTTP route is `POST .../temporal-acts/{temporalActId}/cancel`.

The adapter-local body record `ScNorthboundCancelTemporalActHttpBody` must NOT contain
`temporalActId`. The controller assembles the full request from the path variable + body:

```java
@PostMapping("/{temporalActId}/cancel")
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

If `temporalActId` appears in the body, the test that verifies the path variable is
authoritative will fail.

### INV-HTTP-005: listTemporalActs ACTIVE mode silently ignores maxResults

This is pre-existing facade behavior. The facade's `listTemporalActs` implementation,
when mode is `ACTIVE`, calls `temporalActObservationPort.listActive(habitatId)` without
passing `maxResults`. The HTTP controller must pass the `NorthboundTemporalActFilter`
as-is and must not compensate, pre-paginate or warn the caller.

The test for `?mode=ACTIVE&maxResults=50` must assert only HTTP 200 and a non-null
list body — not that `maxResults` was applied.

### INV-HTTP-006: UNKNOWN_PENDING_NORMALIZATION maps to HTTP 200 — body status is what matters

`getNorthboundDiagnostics` returns `ScNorthboundStatus.OK` at the response level, but
the `payload.migrationReadiness().status()` field contains `"UNKNOWN_PENDING_NORMALIZATION"`.
This is confirmed by `NorthboundFacadeSpringContextTest.getNorthboundDiagnosticsIsAccessibleThroughFacadeBean`.

The HTTP status for diagnostics is therefore 200 (from `OK`). The mapper maps the
`ScNorthboundResponse.status()` field, not any field inside the payload.
A test must confirm that the body still contains `"UNKNOWN_PENDING_NORMALIZATION"`
somewhere in the JSON — not stripped, not hidden.

### INV-HTTP-007: @WebMvcTest is a strict isolation boundary

`@WebMvcTest(SomeController.class)` loads only the web slice. `TemporalEngineConfiguration`,
`TopologyPersistenceConfiguration`, Flyway, SQLite, the temporal engine lifecycle — none
of these run. If a controller injects anything beyond `ScCoreNorthboundFacade`, the
`@WebMvcTest` context will fail to start. This is the desired guard, not a problem to
work around. Controllers must only inject `ScCoreNorthboundFacade`.

### INV-HTTP-008: the two stale tests have different replacement strategies

Test 1 — `NorthboundFacadeNegativeBoundaryTest.noExternalExposureLayerIsIntroducedByMu019`:
This test scans ALL of `src/main/java/com/sovereign/connect` and forbids
`org.springframework.web` globally. It must be REPLACED (not updated) with two tests
that have narrower, accurate semantics. The second test in the class,
`defaultFacadeConstructorUsesOnlyApprovedCollaborators`, must NOT be touched.

Test 2 — `NorthboundFacadeSpringContextTest.noHttpControllerBeanIsIntroduced`:
This test asserts `controllerBeans.isEmpty()`. It must be REPLACED (the method body
rewritten, same method name or a new name) with a controller-package assertion.
The other three tests in `NorthboundFacadeSpringContextTest` must NOT be touched.

---

## 3. New files — exact class signatures

### 3.1 ScNorthboundHttpResponseMapper (stateless utility)

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

Note: sealed switch on enum — Java 21 compiler enforces exhaustiveness.
If a new `ScNorthboundStatus` value is added later, this class will fail to compile,
which is the correct behavior.

### 3.2 ScNorthboundCancelTemporalActHttpBody (adapter-local record)

```java
package com.sovereign.connect.adapter.northbound.http;

record ScNorthboundCancelTemporalActHttpBody(
    String requestedByRef,
    String idempotencyKey,
    String reason
) {}
```

Package-private record. No annotations needed — Jackson deserializes records by default
once `spring-boot-starter-web` is on the classpath and the ObjectMapper has
`findAndRegisterModules()`.

### 3.3 ScNorthboundHttpProperties

```java
package com.sovereign.connect.adapter.northbound.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sc.northbound.http")
public record ScNorthboundHttpProperties(
    boolean enabled,
    String basePath,
    String exposureProfile
) {}
```

MU-021 uses `/sc/v1` as a static seed route prefix in controller annotations.
`basePath` records the selected seed prefix for diagnostics/configuration visibility;
runtime-remappable base paths are deferred.

`enabled` MUST have behavior if introduced: controllers MUST be guarded with
`@ConditionalOnProperty(prefix = "sc.northbound.http", name = "enabled", havingValue = "true", matchIfMissing = true)`.
This avoids a dead configuration flag while keeping the default seed behavior enabled.

### 3.4 ScNorthboundHttpConfiguration

```java
package com.sovereign.connect.adapter.northbound.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScNorthboundHttpProperties.class)
public class ScNorthboundHttpConfiguration {
}
```

### 3.5 ScNorthboundTopologyHttpController — class-level annotation pattern

```java
package com.sovereign.connect.adapter.northbound.http;

import com.sovereign.connect.core.northbound.ScCoreNorthboundFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "sc.northbound.http", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/sc/v1/habitats/{habitatId}")
public class ScNorthboundTopologyHttpController {

    private final ScCoreNorthboundFacade facade;

    public ScNorthboundTopologyHttpController(ScCoreNorthboundFacade facade) {
        this.facade = facade;
    }

    // 14 @GetMapping methods — see §9 route table
}
```

### 3.6 ScNorthboundTemporalHttpController — class-level annotation pattern

```java
package com.sovereign.connect.adapter.northbound.http;

import com.sovereign.connect.core.northbound.ScCoreNorthboundFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "sc.northbound.http", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/sc/v1/habitats/{habitatId}")
public class ScNorthboundTemporalHttpController {

    private final ScCoreNorthboundFacade facade;

    public ScNorthboundTemporalHttpController(ScCoreNorthboundFacade facade) {
        this.facade = facade;
    }

    // 7 methods — see §9 route table
}
```

---

## 4. Required dependency changes

Exact `pom.xml` additions:

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

Do NOT add:
```text
spring-boot-starter-webflux
springdoc-openapi-starter-webmvc-ui
grpc / connectrpc / mcp / graphql / websocket / nats / jetstream
```

---

## 5. HTTP status mapping (authoritative)

```text
OK                            -> 200
CREATED                       -> 201
ACCEPTED                      -> 202
CANCELLED                     -> 200
NOT_FOUND                     -> 404
INVALID_REQUEST               -> 400
INVALID_CANONICAL_ID          -> 400
VALIDATION_ERROR              -> 422
UNSUPPORTED_PROFILE           -> 501   ← never 200
DEFERRED_SC_B_REQUIRED        -> 503   ← never 200
UNKNOWN_PENDING_NORMALIZATION -> 200   ← body status still visible
INTERNAL_ERROR                -> 500
```

---

## 6. Route table (authoritative, base path `/sc/v1/habitats/{habitatId}`)

### Profile A — ScNorthboundTopologyHttpController

| HTTP method | Path | Facade call |
|---|---|---|
| GET | `/topology` | `getTopologySnapshot(habitatId)` |
| GET | `/topology/version` | `getTopologyVersion(habitatId)` |
| GET | `/rooms` | `listRooms(habitatId)` |
| GET | `/zones` | `listZones(habitatId)` |
| GET | `/devices` | `listDevices(habitatId)` |
| GET | `/devices/{deviceId}` | `getDevice(habitatId, deviceId)` |
| GET | `/devices/{deviceId}/health` | `getDeviceHealth(habitatId, deviceId)` |
| GET | `/devices/{deviceId}/runtime-state` | `getDeviceRuntimeState(habitatId, deviceId)` — functional, returns OK or NOT_FOUND |
| GET | `/endpoints` | `listEndpoints(habitatId)` |
| GET | `/endpoints/{endpointId}` | `getEndpoint(habitatId, endpointId)` |
| GET | `/endpoints/{endpointId}/health` | `getEndpointHealth(habitatId, endpointId)` |
| GET | `/endpoints/{endpointId}/runtime-state` | `getEndpointRuntimeState(habitatId, endpointId)` — returns UNSUPPORTED_PROFILE → HTTP 501 |
| GET | `/locations/{roomOrZoneId}/devices` | `listDevicesLocatedIn(habitatId, roomOrZoneId)` |
| GET | `/locations/{roomOrZoneId}/endpoints` | `listEndpointsLocatedIn(habitatId, roomOrZoneId)` |

### Profile B — ScNorthboundTemporalHttpController

| HTTP method | Path | Facade call |
|---|---|---|
| GET | `/temporal/runtime-status` | `getTemporalRuntimeStatus(habitatId)` |
| GET | `/recovery/status` | `getRecoveryStatus(habitatId)` — returns UNSUPPORTED_PROFILE → HTTP 501 |
| GET | `/diagnostics` | `getNorthboundDiagnostics(habitatId)` — returns OK with UNKNOWN_PENDING_NORMALIZATION in payload |
| GET | `/temporal-acts` | `listTemporalActs(habitatId, filter)` — query params: `mode`, `maxResults` |
| GET | `/temporal-acts/{temporalActId}` | `getTemporalAct(habitatId, temporalActId)` |
| POST | `/temporal-acts` | `createSignalTemporalAct(habitatId, request)` — body: NorthboundCreateSignalTemporalActRequest |
| POST | `/temporal-acts/{temporalActId}/cancel` | `cancelTemporalAct(habitatId, constructed request)` — see INV-HTTP-004 |

---

## 7. listTemporalActs controller method (exact implementation)

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

---

## 8. ObjectMapper patch (exact)

File: `src/main/java/com/sovereign/connect/config/TemporalEngineConfiguration.java`

Replace:
```java
@Bean
public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
}
```

With:
```java
@Bean
@Primary
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
}
```

Add imports if not already present:
```java
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Primary;
```

(`org.springframework.context.annotation.Primary` is already imported in the file for
other beans — verify before adding to avoid duplicate import.)

---

## 9. application.yml addition

Append to `src/main/resources/application.yml`:

```yaml
sc:
  northbound:
    http:
      enabled: true
      base-path: /sc/v1
      exposure-profile: local-trusted
```

---

## 10. Test implementation — exact patterns

### 10.1 ScNorthboundTopologyHttpControllerTest — skeleton with imports

```java
package com.sovereign.connect.adapter.northbound.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.northbound.ScCoreNorthboundFacade;
import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.ScNorthboundStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScNorthboundTopologyHttpController.class)
class ScNorthboundTopologyHttpControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ScCoreNorthboundFacade facade;

    private static final String HABITAT = "habitat-001";

    @Test
    void getTopologySnapshot_returns200_whenFacadeReturnsOk() throws Exception {
        when(facade.getTopologySnapshot(HABITAT))
            .thenReturn(ScNorthboundResponse.ok(/* minimal snapshot fixture */));

        mockMvc.perform(get("/sc/v1/habitats/{h}/topology", HABITAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void getDevice_returns404_whenFacadeReturnsNotFound() throws Exception {
        when(facade.getDevice(HABITAT, "device.p.d"))
            .thenReturn(ScNorthboundResponse.notFound("device not found"));

        mockMvc.perform(get("/sc/v1/habitats/{h}/devices/{d}", HABITAT, "device.p.d"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    @Test
    void getEndpointRuntimeState_returns501_whenFacadeReturnsUnsupportedProfile() throws Exception {
        when(facade.getEndpointRuntimeState(HABITAT, "endpoint.p.d.e"))
            .thenReturn(ScNorthboundResponse.unsupportedProfile("endpoint runtime state not supported"));

        mockMvc.perform(get("/sc/v1/habitats/{h}/endpoints/{e}/runtime-state", HABITAT, "endpoint.p.d.e"))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.status").value("UNSUPPORTED_PROFILE"));
    }

    @Test
    void getDeviceRuntimeState_returns404_whenFacadeReturnsNotFound() throws Exception {
        when(facade.getDeviceRuntimeState(HABITAT, "device.p.d"))
            .thenReturn(ScNorthboundResponse.notFound("no runtime state recorded"));

        mockMvc.perform(get("/sc/v1/habitats/{h}/devices/{d}/runtime-state", HABITAT, "device.p.d"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }
}
```

### 10.2 ScNorthboundTemporalHttpControllerTest — skeleton with critical temporal tests

```java
package com.sovereign.connect.adapter.northbound.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.northbound.ScCoreNorthboundFacade;
import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.temporal.NorthboundCancelTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScNorthboundTemporalHttpController.class)
class ScNorthboundTemporalHttpControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ScCoreNorthboundFacade facade;

    private static final String HABITAT = "habitat-001";

    @Test
    void listTemporalActs_unknownMode_returns400_withoutInvokingFacade() throws Exception {
        mockMvc.perform(get("/sc/v1/habitats/{h}/temporal-acts", HABITAT)
                .param("mode", "BOGUS"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("INVALID_REQUEST"));

        verify(facade, never()).listTemporalActs(any(), any());
    }

    @Test
    void cancelTemporalAct_pathVariableIsAuthoritative_bodyDoesNotContainTemporalActId()
            throws Exception {
        String actId = "act-abc-123";
        when(facade.cancelTemporalAct(eq(HABITAT), any(NorthboundCancelTemporalActRequest.class)))
            .thenReturn(ScNorthboundResponse.cancelled(null));

        String body = """
            {"requestedByRef": "hub-1", "idempotencyKey": "k1", "reason": "test"}
            """;

        mockMvc.perform(post("/sc/v1/habitats/{h}/temporal-acts/{id}/cancel", HABITAT, actId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        ArgumentCaptor<NorthboundCancelTemporalActRequest> captor =
            ArgumentCaptor.forClass(NorthboundCancelTemporalActRequest.class);
        verify(facade).cancelTemporalAct(eq(HABITAT), captor.capture());
        assertThat(captor.getValue().temporalActId()).isEqualTo(actId);
        assertThat(captor.getValue().requestedByRef()).isEqualTo("hub-1");
    }

    @Test
    void getRecoveryStatus_returns501_whenFacadeReturnsUnsupportedProfile() throws Exception {
        when(facade.getRecoveryStatus(HABITAT))
            .thenReturn(ScNorthboundResponse.unsupportedProfile("recovery status not supported"));

        mockMvc.perform(get("/sc/v1/habitats/{h}/recovery/status", HABITAT))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.status").value("UNSUPPORTED_PROFILE"));
    }
}
```

### 10.3 ScNorthboundHttpSerializationTest — paste-ready fixture

```java
package com.sovereign.connect.adapter.northbound.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sovereign.connect.core.northbound.ScNorthboundError;
import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.ScNorthboundWarning;
import com.sovereign.connect.core.northbound.temporal.NorthboundCreateSignalTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActView;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologySnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScNorthboundHttpSerializationTest {

    // Use the SAME construction as TemporalEngineConfiguration.objectMapper() after patch
    private final ObjectMapper mapper = new ObjectMapper()
        .findAndRegisterModules()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    @Test
    void instantSerializesAsIso8601String() throws Exception {
        var view = new NorthboundTemporalActView(
            "act-1", "habitat-001", "ACTIVE",
            Instant.parse("2026-05-26T12:00:00Z"),
            "SIGNAL", "label", null, null, "hub-1",
            Instant.parse("2026-05-26T11:00:00Z"),
            Instant.parse("2026-05-26T11:00:00Z"),
            null, null, null
        );
        String json = mapper.writeValueAsString(view);
        assertThat(json).contains("\"2026-05-26T12:00:00Z\"");
        assertThat(json).doesNotContainPattern("\"dueAt\"\\s*:\\s*\\d{10,}");
    }

    @Test
    void northboundCreateSignalTemporalActRequest_deserializesInstantFromIso8601() throws Exception {
        String json = """
            {"dueAt":"2026-05-26T14:00:00Z","label":"test","signalKind":"ALARM",
             "notificationTargetRef":"surface:1","createdByRef":"hub-1","idempotencyKey":"k1"}
            """;
        var req = mapper.readValue(json, NorthboundCreateSignalTemporalActRequest.class);
        assertThat(req.dueAt()).isEqualTo(Instant.parse("2026-05-26T14:00:00Z"));
    }

    @Test
    void scNorthboundResponseBodyContainsAllRootFields() throws Exception {
        var response = ScNorthboundResponse.notFound("DEVICE_NOT_FOUND", "device not found");
        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("\"status\"");
        assertThat(json).contains("\"payload\"");
        assertThat(json).contains("\"warnings\"");
        assertThat(json).contains("\"error\"");
    }

    @Test
    void scNorthboundErrorSerializesSourceField() throws Exception {
        var err = new ScNorthboundError("CODE", "message", "northbound.validation");
        String json = mapper.writeValueAsString(err);
        assertThat(json).contains("\"source\"");
        assertThat(json).contains("northbound.validation");
    }

    @Test
    void scNorthboundWarningSerializesSourceField() throws Exception {
        var warn = new ScNorthboundWarning("WARN_CODE", "warning", "northbound.query");
        String json = mapper.writeValueAsString(warn);
        assertThat(json).contains("\"source\"");
        assertThat(json).contains("northbound.query");
    }

    @Test
    void unknownPendingNormalizationStatusRemainsVisibleInBody() throws Exception {
        var response = ScNorthboundResponse.unknownPendingNormalization(
            "UNKNOWN_PENDING_NORMALIZATION", "normalization pending");
        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("UNKNOWN_PENDING_NORMALIZATION");
    }
}
```

### 10.4 ScNorthboundHttpArchitectureTest — paste-ready with helper reuse

Pattern follows `StorageLegacyCleanupArchitectureTest` (already in test suite).
Same `containsAny(Path, String...)` helper pattern.

```java
package com.sovereign.connect.adapter.northbound.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScNorthboundHttpArchitectureTest {

    private final Path allMain = Path.of("src/main/java/com/sovereign/connect");
    private final Path httpAdapter = Path.of(
        "src/main/java/com/sovereign/connect/adapter/northbound/http");

    @Test
    void webImportsForbiddenOutsideHttpAdapterPackage() throws IOException {
        List<String> webTokens = List.of(
            "org.springframework.web",
            "@RestController", "@Controller",
            "@RequestMapping", "@GetMapping", "@PostMapping",
            "SseEmitter", "ResponseEntity"
        );
        try (Stream<Path> paths = Files.walk(allMain)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toAbsolutePath().startsWith(httpAdapter.toAbsolutePath()))
                .filter(p -> containsAny(p, webTokens))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("Spring Web imports are only allowed under adapter.northbound.http")
                .isEmpty();
        }
    }

    @Test
    void forbiddenTechnologiesAbsentFromAllMainSource() throws IOException {
        List<String> forbidden = List.of(
            "@MessageMapping", "graphql", "io.grpc", "connectrpc",
            "io.nats", "JetStream", "SseEmitter", "Flux<", "Mono<",
            "spring-boot-starter-webflux"
        );
        try (Stream<Path> paths = Files.walk(allMain)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p, forbidden))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("Forbidden technologies must not appear in main source")
                .isEmpty();
        }
    }

    @Test
    void httpAdapterDelegatesOnlyToFacade() throws IOException {
        List<String> forbidden = List.of(
            "CoreSnapshotQueryService",
            "BaseTopologyService",
            "TemporalActApplicationPort",
            "TemporalActObservationPort",
            "SQLiteTemporalActRepository",
            "SQLiteBaseTopologyRepository",
            "JdbcTemplate",
            "javax.sql.DataSource",
            "Flyway"
        );
        if (!Files.exists(httpAdapter)) return;
        try (Stream<Path> paths = Files.walk(httpAdapter)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p, forbidden))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("HTTP adapter must delegate only to ScCoreNorthboundFacade")
                .isEmpty();
        }
    }

    @Test
    void httpAdapterDoesNotLeakEibProjectionOrAuthorityConcepts() throws IOException {
        List<String> forbidden = List.of(
            "Projection", "EffectiveView", "Session", "Identity",
            "Authority", "Policy", "Surface"
        );
        if (!Files.exists(httpAdapter)) return;
        try (Stream<Path> paths = Files.walk(httpAdapter)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p, forbidden))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("HTTP adapter must not contain EIB/Projection/Authority/Policy concepts")
                .isEmpty();
        }
    }

    private boolean containsAny(Path path, List<String> needles) {
        try {
            String content = Files.readString(path);
            return needles.stream().anyMatch(content::contains);
        } catch (IOException e) {
            return false;
        }
    }
}
```

### 10.5 Stale test replacements — exact method bodies

#### In NorthboundFacadeNegativeBoundaryTest — replace the ONE stale method:

Replace the method body of `noExternalExposureLayerIsIntroducedByMu019` entirely.
Keep the method name to preserve the MU-019 historical record, or rename it to
`webImportsForbiddenOutsideHttpAdapterPackage`. Keep `defaultFacadeConstructorUsesOnlyApprovedCollaborators` and the private `containsAny` helper UNCHANGED.

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

#### In NorthboundFacadeSpringContextTest — replace the ONE stale method:

Replace the method body of `noHttpControllerBeanIsIntroduced`. Keep all other
methods (scCoreNorthboundFacadeBeanExistsAndUsesDefaultImplementation,
requiredCollaboratorsResolveFromSpringContext, getNorthboundDiagnosticsIsAccessibleThroughFacadeBean)
UNCHANGED.

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

### 10.6 ScNorthboundHttpSpringContextTest — full skeleton

```java
package com.sovereign.connect.adapter.northbound.http;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/http-context.sqlite"
})
@AutoConfigureMockMvc
class ScNorthboundHttpSpringContextTest {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mockMvc;

    @Test
    void contextLoadsWithHttpControllers() {
        assertThat(context.getBean(ScNorthboundTopologyHttpController.class)).isNotNull();
        assertThat(context.getBean(ScNorthboundTemporalHttpController.class)).isNotNull();
    }

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

    @Test
    void openApiEndpointResolvesToNonEmptyDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
        // Further: assert response body is non-empty JSON
    }
}
```

---

## 11. Stop conditions (verify before proceeding)

```text
STOP-1: After adding spring-boot-starter-web — run mvn test.
         If NorthboundFacadeNegativeBoundaryTest or NorthboundFacadeSpringContextTest
         fails, fix those stale tests first before adding any controller files.

STOP-2: After adding ObjectMapper @Primary + disable timestamps — run mvn test.
         All 196 existing tests must still pass before adding new files.

STOP-3: After adding all controller files — run mvn test.
         All @WebMvcTest tests must pass. If context fails with
         "expected single matching bean but found 2" for any bean type,
         check for duplicate @Primary declarations or missing @MockBean.

STOP-4: Final validation — mvn test must report >= 226 tests (196 baseline +
         at least 30 new tests from the 5 new test classes), 0 failures, 0 errors.
         Record the exact count in implementation-report.md.
```

---

## 12. Files to modify / create summary

```text
MODIFY:
  pom.xml
  src/main/resources/application.yml
  src/main/java/com/sovereign/connect/config/TemporalEngineConfiguration.java
  src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeNegativeBoundaryTest.java
  src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeSpringContextTest.java

CREATE (main):
  src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTopologyHttpController.java
  src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTemporalHttpController.java
  src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpResponseMapper.java
  src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpConfiguration.java
  src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpProperties.java
  src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundCancelTemporalActHttpBody.java

CREATE (test):
  src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTopologyHttpControllerTest.java
  src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTemporalHttpControllerTest.java
  src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpSerializationTest.java
  src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpArchitectureTest.java
  src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpSpringContextTest.java

DO NOT MODIFY:
  src/main/java/com/sovereign/connect/core/northbound/**
  src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeArchitectureTest.java
  src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeBehavioralTest.java
```
