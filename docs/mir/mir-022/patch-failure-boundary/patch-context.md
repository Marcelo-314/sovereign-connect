# Patch Context — MU-022 Failure-Boundary Patch

```text
Version: v0.2.1-reviewed
Patch:   PATCH-SOV-SC-EIB-MIR-022-FAILURE-BOUNDARY-001
Branch:  feat/sc-eib-mir-022-effective-interaction-boundary-seed
```

---

## 1. Current state

MU-022 is implemented in `eib/` as a separate Spring Boot runtime. The routing-fix
patch aligned EIB upstream routes with SC-C MU-021. The following remains intact
and MUST NOT be changed:

```text
- EIB placement under eib/
- Root package com.sovereign.eib
- No imports of com.sovereign.connect.* in eib/src/main/java
- All route corrections from the routing-fix patch
- Effective-ref HMAC semantics
- Provider-native ID suppression
- Canonical envelope mapping for valid ScEnvelope responses
```

Remaining blocker:

```text
EibUpstreamUnavailableException can escape as a framework error in temporal
admission and temporal projection. Single device/endpoint routes silently
convert an upstream failure to NOT_FOUND. Diagnostics lacks upstream-failure
handling. No tests prove the failure-boundary behavior at the API layer.
```

Non-targeted behavior to preserve:

```text
EibEffectiveViewService already handles topology-fetch upstream failure as
UPSTREAM_UNAVAILABLE and degrades temporal-act list failure inside
EffectiveHabitatView into a warning. Do not change that behavior in this patch
unless a compile/test failure proves it necessary. The patch target is the
explicit temporal projection routes, temporal admission routes, single
device/endpoint routes and diagnostics route.
```

---

## 2. Failure boundary rule

```text
Case A — SC-C semantic response:
  Body is valid ScEnvelope<T> regardless of HTTP status code.
  EIB MUST preserve the SC-C semantic status from the envelope.

Case B — upstream transport/wire failure:
  No valid ScEnvelope<T> can be obtained.
  EibUpstreamUnavailableException is already thrown by RestClientEibScNorthboundClient.
  EIB MUST return EibResponse.status = UPSTREAM_UNAVAILABLE.
```

Single-entity rule:

```text
NOT_FOUND is valid ONLY after a successful parent fetch (OK status, non-null payload).
If parent status is not OK, propagate parent status — do NOT convert to NOT_FOUND.
```

---

## 3. Domain record shapes — confirmed from source

```java
// EibResponse<T>
record EibResponse<T>(
    String status,
    T payload,
    List<EibWarning> warnings,
    EibError error,
    CanonicalTraceSummary canonicalTrace   // null for most routes
) {}

// InteractionAdmissionDecision — field order matters for constructor
record InteractionAdmissionDecision(
    String admissionId,
    String status,
    String effectiveRef,
    CanonicalSubmissionTrace canonicalTrace,
    List<EibWarning> warnings,
    EibError error
) {}

// CanonicalSubmissionTrace — field order matters for constructor
record CanonicalSubmissionTrace(
    String clientRequestRef,
    String admissionId,
    String scNorthboundStatus,
    EibError error,
    List<EibWarning> warnings
) {}
```

These constructors are used in existing tests. Match the field order exactly.

---

## 4. Required production changes — exact code

### 4.1 EibTemporalAdmissionService

File: `eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java`

Add the import if absent:

```java
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
```

**admitTemporalSignalRequest — wrap createSignalTemporalAct:**

```java
public InteractionAdmissionDecision admitTemporalSignalRequest(
        String habitatId,
        EibTemporalSignalAdmissionRequest request,
        EibRequestContext ctx) {

    String admissionId = "adm." + UUID.randomUUID();
    ScEnvelope<NorthboundTemporalActViewDto> envelope;
    try {
        envelope = client.createSignalTemporalAct(habitatId,
            new NorthboundCreateSignalTemporalActRequestDto(
                request.dueAt(), request.label(), request.signalKind(),
                request.notificationTargetRef(), "eib-service", request.idempotencyKey()
            ));
    } catch (EibUpstreamUnavailableException e) {
        EibError error = new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound");
        return new InteractionAdmissionDecision(
            admissionId, "FAILED_UPSTREAM_UNAVAILABLE", null,
            new CanonicalSubmissionTrace(
                ctx.clientRequestRef(), admissionId, "UPSTREAM_UNAVAILABLE", error, List.of()),
            List.of(), error);
    }
    CanonicalSubmissionTrace trace = envelopeMapper.toTrace(ctx.clientRequestRef(), admissionId, envelope);
    String effectiveRef = envelopeMapper.isSuccess(envelope) && envelope.payload() != null
        ? codec.generateRef("eib.temporal", habitatId, envelope.payload().temporalActId())
        : null;
    return new InteractionAdmissionDecision(
        admissionId, mapScStatusToAdmissionStatus(envelope.status()),
        effectiveRef, trace,
        envelopeMapper.mapWarnings(envelope.warnings()), envelopeMapper.mapError(envelope.error()));
}
```

**admitTemporalCancellation — wrap both calls:**

```java
public InteractionAdmissionDecision admitTemporalCancellation(
        String habitatId,
        String effectiveRef,
        EibTemporalCancellationAdmissionRequest request,
        EibRequestContext ctx) {

    String admissionId = "adm." + UUID.randomUUID();

    // Step 1: list+match — upstream failure is NOT a rejection
    ScEnvelope<List<NorthboundTemporalActViewDto>> visible;
    try {
        visible = client.listTemporalActs(habitatId, "ACTIVE", 50);
    } catch (EibUpstreamUnavailableException e) {
        EibError error = new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound");
        return new InteractionAdmissionDecision(
            admissionId, "FAILED_UPSTREAM_UNAVAILABLE", null,
            new CanonicalSubmissionTrace(
                ctx.clientRequestRef(), admissionId, "UPSTREAM_UNAVAILABLE", error, List.of()),
            List.of(), error);
    }

    // Step 2: resolve ref — not found is a genuine rejection
    return codec.resolveTemporalRef(
            effectiveRef, habitatId,
            visible.payload() == null ? List.of() : visible.payload())
        .map(canonicalId -> {
            // Step 3: submit cancel — upstream failure during submit
            ScEnvelope<NorthboundTemporalActViewDto> envelope;
            try {
                envelope = client.cancelTemporalAct(habitatId, canonicalId,
                    new NorthboundCancelTemporalActRequestDto(
                        "eib-service", request.idempotencyKey(), request.reason()));
            } catch (EibUpstreamUnavailableException e) {
                EibError error = new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound");
                return new InteractionAdmissionDecision(
                    admissionId, "FAILED_UPSTREAM_UNAVAILABLE", null,
                    new CanonicalSubmissionTrace(
                        ctx.clientRequestRef(), admissionId, "UPSTREAM_UNAVAILABLE", error, List.of()),
                    List.of(), error);
            }
            return new InteractionAdmissionDecision(
                admissionId, mapScStatusToAdmissionStatus(envelope.status()), null,
                envelopeMapper.toTrace(ctx.clientRequestRef(), admissionId, envelope),
                envelopeMapper.mapWarnings(envelope.warnings()), envelopeMapper.mapError(envelope.error()));
        })
        .orElseGet(() -> new InteractionAdmissionDecision(
            admissionId, "REJECTED_NOT_VISIBLE", null,
            new CanonicalSubmissionTrace(
                ctx.clientRequestRef(), admissionId, "NOT_SUBMITTED", null, List.of()),
            List.of(), new EibError("NOT_VISIBLE", "effective temporal act is not visible", "eib.temporal")));
}
```

Note: `mapScStatusToAdmissionStatus` is unchanged.

---

### 4.2 EibTemporalActProjectionService

File: `eib/src/main/java/com/sovereign/eib/service/EibTemporalActProjectionService.java`

Add the import if absent:

```java
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
```

**listEffectiveTemporalActs:**

```java
public EibResponse<List<EffectiveTemporalActView>> listEffectiveTemporalActs(
        String habitatId, EibRequestContext ctx) {

    ScEnvelope<List<NorthboundTemporalActViewDto>> envelope;
    try {
        envelope = client.listTemporalActs(habitatId, "ACTIVE", 50);
    } catch (EibUpstreamUnavailableException e) {
        return new EibResponse<>("UPSTREAM_UNAVAILABLE", List.of(), List.of(),
            new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound"), null);
    }
    if (!envelopeMapper.isSuccess(envelope) || envelope.payload() == null) {
        return new EibResponse<>(envelopeMapper.extractScStatus(envelope), List.of(),
            envelopeMapper.mapWarnings(envelope.warnings()),
            envelopeMapper.mapError(envelope.error()), null);
    }
    return new EibResponse<>("OK",
        envelope.payload().stream().map(act -> viewMapper.temporal(habitatId, act, ctx)).toList(),
        envelopeMapper.mapWarnings(envelope.warnings()), null, null);
}
```

**getEffectiveTemporalAct:**

```java
public EibResponse<EffectiveTemporalActView> getEffectiveTemporalAct(
        String habitatId, String effectiveRef, EibRequestContext ctx) {

    ScEnvelope<List<NorthboundTemporalActViewDto>> envelope;
    try {
        envelope = client.listTemporalActs(habitatId, "ACTIVE", 50);
    } catch (EibUpstreamUnavailableException e) {
        return new EibResponse<>("UPSTREAM_UNAVAILABLE", null, List.of(),
            new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound"), null);
    }
    if (!envelopeMapper.isSuccess(envelope) || envelope.payload() == null) {
        return new EibResponse<>(envelopeMapper.extractScStatus(envelope), null,
            envelopeMapper.mapWarnings(envelope.warnings()),
            envelopeMapper.mapError(envelope.error()), null);
    }
    return envelope.payload().stream()
        .filter(act -> codec.generateRef("eib.temporal", habitatId, act.temporalActId()).equals(effectiveRef))
        .findFirst()
        .map(act -> new EibResponse<>("OK", viewMapper.temporal(habitatId, act, ctx), List.of(), null, null))
        .orElseGet(() -> new EibResponse<>("NOT_FOUND", null, List.of(),
            new EibError("NOT_FOUND", "temporal act not visible", "eib.temporal"), null));
}
```

---

### 4.3 EibHabitatController

File: `eib/src/main/java/com/sovereign/eib/api/EibHabitatController.java`

Add the import if absent:

```java
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
```

**Single device route — propagate parent non-OK:**

```java
@GetMapping("/devices/{effectiveDeviceRef}")
public EibResponse<EffectiveDeviceView> device(
        @PathVariable String habitatId,
        @PathVariable String effectiveDeviceRef,
        HttpServletRequest request) {

    EibResponse<List<EffectiveDeviceView>> devices = devices(habitatId, request);
    if (!"OK".equals(devices.status())) {
        return new EibResponse<>(devices.status(), null,
            devices.warnings(), devices.error(), devices.canonicalTrace());
    }
    return devices.payload().stream()
        .filter(device -> device.effectiveDeviceRef().equals(effectiveDeviceRef))
        .findFirst()
        .map(device -> new EibResponse<>("OK", device, devices.warnings(), null, null))
        .orElseGet(() -> new EibResponse<>("NOT_FOUND", null, List.of(),
            new EibError("NOT_FOUND", "device not visible", "eib.device"), null));
}
```

**Single endpoint route — symmetrical:**

```java
@GetMapping("/endpoints/{effectiveEndpointRef}")
public EibResponse<EffectiveEndpointView> endpoint(
        @PathVariable String habitatId,
        @PathVariable String effectiveEndpointRef,
        HttpServletRequest request) {

    EibResponse<List<EffectiveEndpointView>> endpoints = endpoints(habitatId, request);
    if (!"OK".equals(endpoints.status())) {
        return new EibResponse<>(endpoints.status(), null,
            endpoints.warnings(), endpoints.error(), endpoints.canonicalTrace());
    }
    return endpoints.payload().stream()
        .filter(endpoint -> endpoint.effectiveEndpointRef().equals(effectiveEndpointRef))
        .findFirst()
        .map(endpoint -> new EibResponse<>("OK", endpoint, endpoints.warnings(), null, null))
        .orElseGet(() -> new EibResponse<>("NOT_FOUND", null, List.of(),
            new EibError("NOT_FOUND", "endpoint not visible", "eib.endpoint"), null));
}
```

**Diagnostics — wrap upstream call:**

```java
@GetMapping("/diagnostics")
public EibResponse<EffectiveDiagnosticsView> diagnostics(@PathVariable String habitatId) {
    ScEnvelope<NorthboundDiagnosticsViewDto> envelope;
    try {
        envelope = client.getDiagnostics(habitatId);
    } catch (EibUpstreamUnavailableException e) {
        return new EibResponse<>("UPSTREAM_UNAVAILABLE", null, List.of(),
            new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound"), null);
    }
    if (!envelopeMapper.isSuccess(envelope) || envelope.payload() == null) {
        return new EibResponse<>(envelopeMapper.extractScStatus(envelope), null,
            envelopeMapper.mapWarnings(envelope.warnings()),
            envelopeMapper.mapError(envelope.error()), null);
    }
    NorthboundDiagnosticsViewDto diag = envelope.payload();
    EffectiveDiagnosticsView view = new EffectiveDiagnosticsView(
        "eib.habitat." + habitatId,
        diag.topologyVersion(),
        diag.migrationReadiness() == null ? "UNKNOWN" : diag.migrationReadiness().status(),
        diag.migrationReadiness() == null ? null : diag.migrationReadiness().source(),
        diag.temporalEngineStatus() == null ? "UNKNOWN" : diag.temporalEngineStatus().engineStatus(),
        diag.readAt(),
        envelopeMapper.mapWarnings(diag.warnings())
    );
    return new EibResponse<>("OK", view, envelopeMapper.mapWarnings(envelope.warnings()), null, null);
}
```

---

### 4.4 EibTemporalActController

File: `eib/src/main/java/com/sovereign/eib/api/EibTemporalActController.java`

Add private helper and use it in both POST methods:

```java
private ResponseEntity<EibResponse<InteractionAdmissionDecision>> admissionResponse(
        InteractionAdmissionDecision decision) {
    if ("FAILED_UPSTREAM_UNAVAILABLE".equals(decision.status())) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new EibResponse<>("UPSTREAM_UNAVAILABLE", decision,
                decision.warnings(), decision.error(), null));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(new EibResponse<>("ACCEPTED", decision,
            decision.warnings(), decision.error(), null));
}

@PostMapping("/signal")
public ResponseEntity<EibResponse<InteractionAdmissionDecision>> signal(
        @PathVariable String habitatId,
        @RequestBody EibTemporalSignalAdmissionRequest body,
        HttpServletRequest request) {
    return admissionResponse(
        admissionService.admitTemporalSignalRequest(habitatId, body, ctx(request)));
}

@PostMapping("/{effectiveTemporalActRef}/cancel")
public ResponseEntity<EibResponse<InteractionAdmissionDecision>> cancel(
        @PathVariable String habitatId,
        @PathVariable String effectiveTemporalActRef,
        @RequestBody EibTemporalCancellationAdmissionRequest body,
        HttpServletRequest request) {
    return admissionResponse(
        admissionService.admitTemporalCancellation(
            habitatId, effectiveTemporalActRef, body, ctx(request)));
}
```

---

## 5. Required tests — exact code

### 5.1 EibTemporalAdmissionServiceTest — add three methods

The class already has `client`, `codec`, `service`, `ctx` and the `temporal()`
helper. Add these three methods. Do not remove existing tests. Add the import
if absent:

```java
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
```

```java
@Test
void signalAdmissionReturnsFailedUpstreamUnavailableWhenCreateThrows() {
    when(client.createSignalTemporalAct(eq("habitat.alpha"), any()))
        .thenThrow(new EibUpstreamUnavailableException("connection refused"));

    var decision = service.admitTemporalSignalRequest("habitat.alpha",
        new EibTemporalSignalAdmissionRequest(
            Instant.parse("2026-01-01T00:00:00Z"), "Wake", "WAKE", "target", "idem"), ctx);

    assertThat(decision.status()).isEqualTo("FAILED_UPSTREAM_UNAVAILABLE");
    assertThat(decision.effectiveRef()).isNull();
    assertThat(decision.canonicalTrace().scNorthboundStatus()).isEqualTo("UPSTREAM_UNAVAILABLE");
    assertThat(decision.error().code()).isEqualTo("UPSTREAM_UNAVAILABLE");
    assertThat(decision.error().source()).isEqualTo("eib.northbound");
}

@Test
void cancelAdmissionReturnsFailedUpstreamUnavailableWhenListThrows() {
    when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
        .thenThrow(new EibUpstreamUnavailableException("connection refused"));

    var decision = service.admitTemporalCancellation("habitat.alpha", "eib.temporal.any",
        new EibTemporalCancellationAdmissionRequest("idem", "reason"), ctx);

    assertThat(decision.status()).isEqualTo("FAILED_UPSTREAM_UNAVAILABLE");
    assertThat(decision.status()).isNotEqualTo("REJECTED_NOT_VISIBLE");
    assertThat(decision.canonicalTrace().scNorthboundStatus()).isEqualTo("UPSTREAM_UNAVAILABLE");
    verify(client, never()).cancelTemporalAct(any(), any(), any());
}

@Test
void cancelAdmissionReturnsFailedUpstreamUnavailableWhenCancelThrows() {
    var act = temporal("temporal.visible");
    when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
        .thenReturn(new ScEnvelope<>("OK", List.of(act), List.of(), null));
    // Generate the exact ref for temporal.visible so resolveTemporalRef finds it
    String ref = codec.generateRef("eib.temporal", "habitat.alpha", "temporal.visible");
    when(client.cancelTemporalAct(eq("habitat.alpha"), eq("temporal.visible"), any()))
        .thenThrow(new EibUpstreamUnavailableException("connection refused"));

    var decision = service.admitTemporalCancellation("habitat.alpha", ref,
        new EibTemporalCancellationAdmissionRequest("idem", "reason"), ctx);

    assertThat(decision.status()).isEqualTo("FAILED_UPSTREAM_UNAVAILABLE");
    assertThat(decision.canonicalTrace().scNorthboundStatus()).isEqualTo("UPSTREAM_UNAVAILABLE");
}
```

---

### 5.2 EibTemporalActProjectionServiceTest — new class

```java
package com.sovereign.eib;

import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import com.sovereign.eib.service.EibEffectiveViewMapper;
import com.sovereign.eib.service.EibTemporalActProjectionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EibTemporalActProjectionServiceTest {

    private final EibScNorthboundClient client = mock(EibScNorthboundClient.class);
    private final EibEffectiveRefCodec codec =
        new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256");
    private final EibTemporalActProjectionService service =
        new EibTemporalActProjectionService(
            client, new EibCanonicalEnvelopeMapper(),
            new EibEffectiveViewMapper(codec), codec);
    private final EibRequestContext ctx =
        new EibRequestContext("ctx", "actor", "surface", "en",
            false, false, Instant.now(), "req");

    @Test
    void listReturnsUpstreamUnavailableWhenClientThrows() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
            .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        var response = service.listEffectiveTemporalActs("habitat.alpha", ctx);

        assertThat(response.status()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(response.error().code()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(response.error().source()).isEqualTo("eib.northbound");
    }

    @Test
    void getReturnsUpstreamUnavailableWhenClientThrows() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
            .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        var response = service.getEffectiveTemporalAct("habitat.alpha", "eib.temporal.any", ctx);

        assertThat(response.status()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(response.payload()).isNull();
        assertThat(response.error().code()).isEqualTo("UPSTREAM_UNAVAILABLE");
    }
}
```

---

### 5.3 EibHabitatControllerFailureBoundaryTest — new class

```java
package com.sovereign.eib;

import com.sovereign.eib.api.EibHabitatController;
import com.sovereign.eib.config.EibDiagnosticAdminProperties;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import com.sovereign.eib.service.EibEffectiveViewMapper;
import com.sovereign.eib.service.EibEffectiveViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class EibHabitatControllerFailureBoundaryTest {

    private final EibScNorthboundClient client = mock(EibScNorthboundClient.class);
    private final EibEffectiveRefCodec codec =
        new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256");
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EibEffectiveViewService effectiveViewService = new EibEffectiveViewService(
            client, new EibCanonicalEnvelopeMapper(), new EibEffectiveViewMapper(codec));
        mockMvc = MockMvcBuilders.standaloneSetup(new EibHabitatController(
            effectiveViewService, client, new EibCanonicalEnvelopeMapper(),
            new EibDiagnosticAdminProperties(false)
        )).build();
    }

    @Test
    void singleDeviceRoutePropagatesUpstreamUnavailableFromParent() throws Exception {
        when(client.getTopologySnapshot("habitat.alpha"))
            .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        mockMvc.perform(get("/eib/v1/habitats/habitat.alpha/devices/eib.device.any"))
            .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
            .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void singleEndpointRoutePropagatesUpstreamUnavailableFromParent() throws Exception {
        when(client.getTopologySnapshot("habitat.alpha"))
            .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        mockMvc.perform(get("/eib/v1/habitats/habitat.alpha/endpoints/eib.endpoint.any"))
            .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
            .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void diagnosticsRouteReturnsUpstreamUnavailableWhenClientThrows() throws Exception {
        when(client.getDiagnostics("habitat.alpha"))
            .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        mockMvc.perform(get("/eib/v1/habitats/habitat.alpha/diagnostics"))
            .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
            .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }
}
```

---

### 5.4 EibApiControllerTest — add two methods to existing class

The class already has `objectMapper`, `admissionService` mock, `mockMvc` and
`@BeforeEach`. Add these two methods. Existing two tests must be preserved.

```java
@Test
void signalAdmissionControllerReturnsUpstreamUnavailableBodyWhenServiceFails() throws Exception {
    when(admissionService.admitTemporalSignalRequest(eq("habitat.alpha"), any(), any()))
        .thenReturn(new InteractionAdmissionDecision(
            "adm.fail", "FAILED_UPSTREAM_UNAVAILABLE", null,
            new CanonicalSubmissionTrace("client", "adm.fail", "UPSTREAM_UNAVAILABLE",
                new com.sovereign.eib.domain.EibError(
                    "UPSTREAM_UNAVAILABLE", "connection refused", "eib.northbound"),
                List.of()),
            List.of(),
            new com.sovereign.eib.domain.EibError(
                "UPSTREAM_UNAVAILABLE", "connection refused", "eib.northbound")));

    mockMvc.perform(post("/eib/v1/habitats/habitat.alpha/temporal-acts/signal")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new EibTemporalSignalAdmissionRequest(
                Instant.parse("2026-01-01T00:00:00Z"), "Wake", "WAKE", "target", "idem"))))
        .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
        .andExpect(jsonPath("$.payload.status").value("FAILED_UPSTREAM_UNAVAILABLE"))
        .andExpect(jsonPath("$.payload.effectiveRef").value(nullValue()))
        .andExpect(jsonPath("$.payload.canonicalTrace.scNorthboundStatus")
            .value("UPSTREAM_UNAVAILABLE"));
}

@Test
void cancelAdmissionControllerReturnsUpstreamUnavailableBodyWhenServiceFails() throws Exception {
    when(admissionService.admitTemporalCancellation(
            eq("habitat.alpha"), eq("eib.temporal.abc"), any(), any()))
        .thenReturn(new InteractionAdmissionDecision(
            "adm.fail", "FAILED_UPSTREAM_UNAVAILABLE", null,
            new CanonicalSubmissionTrace("client", "adm.fail", "UPSTREAM_UNAVAILABLE",
                new com.sovereign.eib.domain.EibError(
                    "UPSTREAM_UNAVAILABLE", "connection refused", "eib.northbound"),
                List.of()),
            List.of(),
            new com.sovereign.eib.domain.EibError(
                "UPSTREAM_UNAVAILABLE", "connection refused", "eib.northbound")));

    mockMvc.perform(post("/eib/v1/habitats/habitat.alpha/temporal-acts/eib.temporal.abc/cancel")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new EibTemporalCancellationAdmissionRequest("idem", "reason"))))
        .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
        .andExpect(jsonPath("$.payload.status").value("FAILED_UPSTREAM_UNAVAILABLE"))
        .andExpect(jsonPath("$.payload.effectiveRef").value(nullValue()));
}
```

---

## 6. Stop conditions

### STOP-FB-1 — compile

```bash
cd eib && mvn compile
```

Expected: BUILD SUCCESS. Verify: `grep -r "com.sovereign.connect" src/main/ | wc -l` = 0.

### STOP-FB-2 — admission service failures

```bash
cd eib && mvn test -Dtest=EibTemporalAdmissionServiceTest
```

Expected: 5 tests pass (2 existing + 3 new).

### STOP-FB-3 — projection and controller failures

```bash
cd eib && mvn test -Dtest=EibTemporalActProjectionServiceTest,EibHabitatControllerFailureBoundaryTest,EibApiControllerTest
```

Expected: all pass. `EibApiControllerTest` = 4 tests (2 existing + 2 new).

### STOP-FB-4 — full EIB suite

```bash
cd eib && mvn test
```

Expected: all tests pass, 0 failures, 0 errors.
Minimum expected test count: 33 (23 previous + 3 admission + 2 projection + 3 habitat + 2 api).
Verify:
```text
EibScNorthboundClientTest: 11 tests (routing-fix tests intact)
EibArchitectureBoundaryTest: 2 tests
EibTemporalAdmissionServiceTest: 5 tests
EibTemporalActProjectionServiceTest: 2 tests
EibHabitatControllerFailureBoundaryTest: 3 tests
EibApiControllerTest: 4 tests
```

### STOP-FB-5 — SC-C baseline

```bash
cd .. && mvn test
```

Expected: 240 tests, 0 failures, 0 errors.

---

## 7. Implementation report addition

Add to `docs/mir/mir-022/implementation-report.md`:

```text
## Failure-boundary patch

Patch commit: <hash>
Description: normalize upstream transport failure across EIB API/service layer

Resolved:
  BLOCKER-PATCH-001 — EibTemporalAdmissionService catches EibUpstreamUnavailableException
                      for createSignalTemporalAct and both cancel calls
  BLOCKER-PATCH-002 — Single device/endpoint routes propagate parent non-OK status
  BLOCKER-PATCH-003 — New tests cover failure-boundary behavior at API/service layer

Validation:
  EIB compile: PASS
  EibTemporalAdmissionServiceTest: PASS, 5 tests
  EibTemporalActProjectionServiceTest: PASS, 2 tests (new)
  EibHabitatControllerFailureBoundaryTest: PASS, 3 tests (new)
  EibApiControllerTest: PASS, 4 tests (2 existing + 2 new)
  EIB full suite: PASS, <N> tests, 0 failures, 0 errors, 0 skipped
  SC-C baseline: PASS, 240 tests, 0 failures, 0 errors, 0 skipped

Routing-fix tests: still passing (EibScNorthboundClientTest 11 tests)
Architecture boundary: still passing
```
