# MU-023 Execution Context — EIB Hardening

```text
Package:  execution-package-MU-023-eib-hardening
Version:  v0.3.0
MIR:      MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate
MU:       MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
Slot:     MU-023
Baseline: post-MU-022 patch-2 — EIB 33/0/0, SC-C 240/0/0
```

---

## 1. Goal

Implement the EIB hardening increment authorized by MIR-023.

This is a focused hardening patch. It must not reimplement EIB, change SC-C
production code, add product-facing construction, implement Bus, implement
View Composer, or introduce Authority/Policy/Identity/Session.

---

## 2. Hard invariants

```text
EIB remains a separate runtime under /eib.
EIB does not share the SC-C application context.
EIB does not import SC-C internals.
EIB consumes SC-C only through HTTP/OpenAPI.
SC-C production code must remain unchanged.
Provider-native IDs remain absent from ordinary and diagnostic/admin product views.
Canonical IDs remain ordinary-mode hidden and diagnostic/admin-gated.
RestClient.Builder bean must be preserved (test infrastructure depends on it).
```

---

## 3. Confirmed source surface from baseline

```text
eib/src/main/java/com/sovereign/eib/config/EibConfiguration.java
eib/src/main/java/com/sovereign/eib/config/EibScNorthboundClientProperties.java
eib/src/main/java/com/sovereign/eib/config/EibDiagnosticAdminProperties.java
eib/src/main/java/com/sovereign/eib/api/EibTemporalActController.java
eib/src/main/java/com/sovereign/eib/api/EibHabitatController.java
eib/src/main/java/com/sovereign/eib/domain/EibResponse.java
eib/src/main/java/com/sovereign/eib/domain/InteractionAdmissionDecision.java
eib/src/main/java/com/sovereign/eib/domain/CanonicalSubmissionTrace.java
eib/src/main/java/com/sovereign/eib/domain/EibError.java
eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java
eib/src/main/java/com/sovereign/eib/service/EibCanonicalEnvelopeMapper.java
eib/src/main/java/com/sovereign/eib/northbound/RestClientEibScNorthboundClient.java
eib/src/main/java/com/sovereign/eib/northbound/EibUpstreamUnavailableException.java
```

Do not rename packages or move runtime placement.

---

## 4. Confirmed domain record shapes

These constructors appear throughout implementation and tests.
Field order is exact — do not transpose.

```java
// EibResponse<T>(status, payload, warnings, error, canonicalTrace)
// canonicalTrace is null for most routes
record EibResponse<T>(
    String status,
    T payload,
    List<EibWarning> warnings,
    EibError error,
    CanonicalTraceSummary canonicalTrace
) {}

// InteractionAdmissionDecision(admissionId, status, effectiveRef,
//                              canonicalTrace, warnings, error)
record InteractionAdmissionDecision(
    String admissionId,
    String status,
    String effectiveRef,
    CanonicalSubmissionTrace canonicalTrace,
    List<EibWarning> warnings,
    EibError error
) {}

// CanonicalSubmissionTrace(clientRequestRef, admissionId,
//                          scNorthboundStatus, error, warnings)
record CanonicalSubmissionTrace(
    String clientRequestRef,
    String admissionId,
    String scNorthboundStatus,
    EibError error,
    List<EibWarning> warnings
) {}

// EibError(code, message, source)
record EibError(String code, String message, String source) {}
```

---

## 5. Confirmed EibCanonicalEnvelopeMapper helpers

```java
// Available in EibCanonicalEnvelopeMapper (already injected in EibTemporalAdmissionService):
boolean isSuccess(ScEnvelope<?> envelope)
EibError mapError(ScErrorDto error)
List<EibWarning> mapWarnings(List<ScWarningDto> warnings)
String extractScStatus(ScEnvelope<?> envelope)
CanonicalSubmissionTrace toTrace(String clientRequestRef, String admissionId, ScEnvelope<?> envelope)
```

---

## 6. ACTION-EIB-H-001 — Timeout factory

### 6.1 Production patch

File: `eib/src/main/java/com/sovereign/eib/config/EibConfiguration.java`

Replace only `eibRestClientBuilder`. Keep `eibRestClient` and all other beans
unchanged.

```java
@Bean
public RestClient.Builder eibRestClientBuilder(EibScNorthboundClientProperties props) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(props.timeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));
    return RestClient.builder()
        .baseUrl(props.baseUrl())
        .requestFactory(factory);
}
```

Required imports (add to EibConfiguration.java):

```java
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
```

`eibRestClientBuilder.build()` in `eibRestClient` bean is unchanged.

### 6.2 Client-level timeout test (AC-023-008)

Create `EibScNorthboundClientTimeoutTest`.

Use `192.0.2.1` — TEST-NET (RFC 5737), non-routable and safe for tests.
Set 1ms timeout to force the client timeout path. This validates timeout
normalization at the client boundary without requiring MockWebServer/WireMock.

Note: both methods below intentionally validate timeout normalization through
two client operations. Do not label the second method as proof of a true
server-side read-delay timeout unless a local delayed server is introduced.

```java
package com.sovereign.eib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.eib.config.EibScNorthboundClientProperties;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.northbound.RestClientEibScNorthboundClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EibScNorthboundClientTimeoutTest {

    private static final ObjectMapper MAPPER =
        new ObjectMapper().findAndRegisterModules();

    private RestClientEibScNorthboundClient clientWithTimeout(long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestClient restClient = RestClient.builder()
            .baseUrl("http://192.0.2.1/sc/v1")  // TEST-NET, RFC 5737, non-routable
            .requestFactory(factory)
            .build();
        return new RestClientEibScNorthboundClient(
            restClient, MAPPER,
            new EibScNorthboundClientProperties("http://192.0.2.1/sc/v1", timeoutMs));
    }

    @Test
    void connectTimeoutProducesEibUpstreamUnavailableException() {
        var client = clientWithTimeout(1);

        assertThatThrownBy(() -> client.getTopologySnapshot("habitat.test"))
            .isInstanceOf(EibUpstreamUnavailableException.class);
    }

    @Test
    void listTemporalActsTimeoutProducesEibUpstreamUnavailableException() {
        // Same timeout-normalization mechanism through a second client operation.
        var client = clientWithTimeout(1);

        assertThatThrownBy(() -> client.listTemporalActs("habitat.test", "ACTIVE", 10))
            .isInstanceOf(EibUpstreamUnavailableException.class);
    }
}
```

### 6.3 API-level timeout normalization test (AC-023-008a)

Add to `EibApiControllerTest` or to a new
`EibApiControllerAdmissionStatusTest`.

The test must stub `admissionService` to return an
`InteractionAdmissionDecision` with status `FAILED_UPSTREAM_UNAVAILABLE` and
assert `HTTP 503` + `$.status = UPSTREAM_UNAVAILABLE`.

Do not make `EibTemporalActController` catch `EibUpstreamUnavailableException`
directly. In this architecture the service catches the upstream exception and
returns a normalized admission decision; the controller/API boundary maps that
decision into `EibResponse<T>` via `EibHttpResponseMapper`.

The `EibApiControllerTest` already has the `MockMvc` setup wired to a mock
`admissionService` — add directly there:

```java
@Test
void signalAdmissionTimeoutNormalizesToUpstreamUnavailableAtApiLevel() throws Exception {
    when(admissionService.admitTemporalSignalRequest(eq("habitat.alpha"), any(), any()))
        .thenReturn(new InteractionAdmissionDecision(
            "adm.timeout", "FAILED_UPSTREAM_UNAVAILABLE", null,
            new CanonicalSubmissionTrace(
                "client", "adm.timeout", "UPSTREAM_UNAVAILABLE",
                new EibError("UPSTREAM_UNAVAILABLE", "timeout", "eib.northbound"),
                List.of()),
            List.of(),
            new EibError("UPSTREAM_UNAVAILABLE", "timeout", "eib.northbound")));

    mockMvc.perform(post("/eib/v1/habitats/habitat.alpha/temporal-acts/signal")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new EibTemporalSignalAdmissionRequest(
                Instant.parse("2026-01-01T00:00:00Z"), "Wake", "WAKE", "target", "idem"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
        .andExpect(jsonPath("$.payload.status").value("FAILED_UPSTREAM_UNAVAILABLE"));
}
```

---

## 7. ACTION-EIB-H-002 — Route regression gap

Add four missing positive route assertions to `EibScNorthboundClientTest`
or a new `EibScNorthboundClientRouteRegressionTest`.

Pattern is identical to existing tests — bind `MockRestServiceServer`, set
`server.expect(once(), requestTo("..."))`, respond with success envelope,
call client method, call `server.verify()`.

```text
Missing routes to cover:
  GET /habitats/{habitatId}/devices/{deviceId}/health
  GET /habitats/{habitatId}/endpoints/{endpointId}/health
  GET /habitats/{habitatId}/diagnostics
  GET /habitats/{habitatId}/temporal-acts/{temporalActId}

Already covered (preserve, do not break):
  GET /topology
  GET /topology/version
  GET /devices/{deviceId}/runtime-state
  GET /endpoints/{endpointId}/runtime-state
  GET /temporal-acts?mode=ACTIVE&maxResults=50
  POST /temporal-acts
  POST /temporal-acts/{temporalActId}/cancel
  HTTP 503 semantic vs transport
  malformed JSON
  connection refused
```

Example for `getDeviceHealth`:

```java
@Test
void getDeviceHealthUsesDeviceHealthRoute() throws Exception {
    var dto = new NorthboundDeviceHealthViewDto(
        "device.alpha", "HEALTHY", "northbound",
        1, Instant.now(), List.of());
    server.expect(once(),
        requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/devices/device.alpha/health"))
        .andRespond(withSuccess(
            objectMapper.writeValueAsString(new ScEnvelope<>("OK", dto, List.of(), null)),
            MediaType.APPLICATION_JSON));

    var result = client.getDeviceHealth("habitat.alpha", "device.alpha");

    assertThat(result.status()).isEqualTo("OK");
    assertThat(result.payload().deviceId()).isEqualTo("device.alpha");
    server.verify();
}
```

Apply the same pattern for `getEndpointHealth`, `getDiagnostics`,
`getTemporalAct`.

---

## 8. ACTION-EIB-H-003 — CANCELLED → COMPLETED

File: `eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java`

Patch `mapScStatusToAdmissionStatus` — change one line only:

```java
// Before:
case "OK", "CREATED", "ACCEPTED", "CANCELLED" -> "ADMITTED";

// After:
case "OK", "CREATED", "ACCEPTED" -> "ADMITTED";
case "CANCELLED" -> "COMPLETED";
```

No other changes to this method.

---

## 9. ACTION-EIB-H-004 — Full admission vocabulary mapper

### 9.1 New class: EibHttpResponseMapper

Create `eib/src/main/java/com/sovereign/eib/api/EibHttpResponseMapper.java`.
This is a stateless utility — no Spring bean annotation required.

```java
package com.sovereign.eib.api;

import com.sovereign.eib.domain.EibResponse;
import com.sovereign.eib.domain.InteractionAdmissionDecision;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class EibHttpResponseMapper {

    private EibHttpResponseMapper() {}

    public static ResponseEntity<EibResponse<InteractionAdmissionDecision>>
        admissionResponse(InteractionAdmissionDecision decision) {

        return switch (decision.status()) {
            case "ADMITTED" ->
                ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new EibResponse<>("ADMITTED", decision,
                        decision.warnings(), decision.error(), null));

            case "COMPLETED" ->
                ResponseEntity.ok(new EibResponse<>("COMPLETED", decision,
                    decision.warnings(), decision.error(), null));

            case "REJECTED_NOT_VISIBLE" ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new EibResponse<>("NOT_VISIBLE", decision,
                        decision.warnings(), decision.error(), null));

            case "REJECTED_INVALID_REQUEST" ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new EibResponse<>("INVALID_REQUEST", decision,
                        decision.warnings(), decision.error(), null));

            case "DEFERRED_SC_B_REQUIRED" ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new EibResponse<>("DEFERRED", decision,
                        decision.warnings(), decision.error(), null));

            case "DEFERRED_PENDING_NORMALIZATION" ->
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new EibResponse<>("PENDING_NORMALIZATION", decision,
                        decision.warnings(), decision.error(), null));

            case "DEFERRED_UNSUPPORTED_PROFILE" ->
                ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(new EibResponse<>("UNSUPPORTED", decision,
                        decision.warnings(), decision.error(), null));

            case "FAILED_UPSTREAM_UNAVAILABLE" ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new EibResponse<>("UPSTREAM_UNAVAILABLE", decision,
                        decision.warnings(), decision.error(), null));

            default ->  // covers FAILED_CANONICAL_SUBMISSION and any unclassified
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new EibResponse<>("FAILED", decision,
                        decision.warnings(), decision.error(), null));
        };
    }
}
```

### 9.2 Patch EibTemporalActController

Replace the existing private `admissionResponse` method with a delegation:

```java
private ResponseEntity<EibResponse<InteractionAdmissionDecision>> admissionResponse(
        InteractionAdmissionDecision decision) {
    return EibHttpResponseMapper.admissionResponse(decision);
}
```

The rest of the controller is unchanged.

### 9.3 Required test: EibAdmissionResponseMapperTest

Create `eib/src/test/java/com/sovereign/eib/EibAdmissionResponseMapperTest.java`.

One method per vocabulary entry. Use the confirmed record constructors from §4.

```java
package com.sovereign.eib;

import com.sovereign.eib.api.EibHttpResponseMapper;
import com.sovereign.eib.domain.CanonicalSubmissionTrace;
import com.sovereign.eib.domain.InteractionAdmissionDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.*;

class EibAdmissionResponseMapperTest {

    private InteractionAdmissionDecision decision(String status) {
        return new InteractionAdmissionDecision(
            "adm.test", status, null,
            new CanonicalSubmissionTrace("ref", "adm.test", status, null, List.of()),
            List.of(), null);
    }

    @Test void admittedMaps202AndAdmitted() {
        var r = EibHttpResponseMapper.admissionResponse(decision("ADMITTED"));
        assertThat(r.getStatusCode()).isEqualTo(ACCEPTED);
        assertThat(r.getBody().status()).isEqualTo("ADMITTED");
    }

    @Test void completedMaps200AndCompleted() {
        var r = EibHttpResponseMapper.admissionResponse(decision("COMPLETED"));
        assertThat(r.getStatusCode()).isEqualTo(OK);
        assertThat(r.getBody().status()).isEqualTo("COMPLETED");
    }

    @Test void rejectedNotVisibleMaps404AndNotVisible() {
        var r = EibHttpResponseMapper.admissionResponse(decision("REJECTED_NOT_VISIBLE"));
        assertThat(r.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(r.getBody().status()).isEqualTo("NOT_VISIBLE");
    }

    @Test void rejectedInvalidRequestMaps400AndInvalidRequest() {
        var r = EibHttpResponseMapper.admissionResponse(decision("REJECTED_INVALID_REQUEST"));
        assertThat(r.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(r.getBody().status()).isEqualTo("INVALID_REQUEST");
    }

    @Test void deferredScBMaps503AndDeferred() {
        var r = EibHttpResponseMapper.admissionResponse(decision("DEFERRED_SC_B_REQUIRED"));
        assertThat(r.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
        assertThat(r.getBody().status()).isEqualTo("DEFERRED");
    }

    @Test void deferredPendingNormalizationMaps409AndPendingNormalization() {
        var r = EibHttpResponseMapper.admissionResponse(decision("DEFERRED_PENDING_NORMALIZATION"));
        assertThat(r.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(r.getBody().status()).isEqualTo("PENDING_NORMALIZATION");
    }

    @Test void deferredUnsupportedProfileMaps501AndUnsupported() {
        var r = EibHttpResponseMapper.admissionResponse(decision("DEFERRED_UNSUPPORTED_PROFILE"));
        assertThat(r.getStatusCode()).isEqualTo(NOT_IMPLEMENTED);
        assertThat(r.getBody().status()).isEqualTo("UNSUPPORTED");
    }

    @Test void failedUpstreamUnavailableMaps503AndUpstreamUnavailable() {
        var r = EibHttpResponseMapper.admissionResponse(decision("FAILED_UPSTREAM_UNAVAILABLE"));
        assertThat(r.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
        assertThat(r.getBody().status()).isEqualTo("UPSTREAM_UNAVAILABLE");
    }

    @Test void failedCanonicalSubmissionMaps500AndFailed() {
        var r = EibHttpResponseMapper.admissionResponse(decision("FAILED_CANONICAL_SUBMISSION"));
        assertThat(r.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().status()).isEqualTo("FAILED");
    }

    @Test void scCInternalErrorReachesFailedPath() {
        // SC-C INTERNAL_ERROR maps to FAILED_CANONICAL_SUBMISSION via mapScStatusToAdmissionStatus
        // This test verifies the mapper receives it via the default path
        var r = EibHttpResponseMapper.admissionResponse(decision("FAILED_CANONICAL_SUBMISSION"));
        assertThat(r.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().status()).isEqualTo("FAILED");
    }

    @Test void noRejectedOrDeferredStatusReturnsTopLevelAccepted() {
        for (String nonAdmittedStatus : List.of(
                "REJECTED_NOT_VISIBLE", "REJECTED_INVALID_REQUEST",
                "DEFERRED_SC_B_REQUIRED", "DEFERRED_PENDING_NORMALIZATION",
                "DEFERRED_UNSUPPORTED_PROFILE", "FAILED_UPSTREAM_UNAVAILABLE",
                "FAILED_CANONICAL_SUBMISSION")) {
            var r = EibHttpResponseMapper.admissionResponse(decision(nonAdmittedStatus));
            assertThat(r.getBody().status())
                .as("Top-level EibResponse.status for " + nonAdmittedStatus)
                .isNotEqualTo("ACCEPTED");
        }
    }
}
```

---

### 9.4 Patch stale EibApiControllerTest expectations

After introducing `EibHttpResponseMapper`, two existing tests in
`EibApiControllerTest` become stale and must be updated. Do not leave them
expecting top-level `ACCEPTED` for every non-failure response.

Update `signalAdmissionReturnsEibResponseWrapper`:

```java
.andExpect(status().isAccepted())
.andExpect(jsonPath("$.status").value("ADMITTED"))
.andExpect(jsonPath("$.payload.status").value("ADMITTED"))
```

Update `cancelAdmissionReturnsEibResponseWrapperWithNullEffectiveRef` so the
mocked decision represents the new terminal cancel semantics:

```java
when(admissionService.admitTemporalCancellation(eq("habitat.alpha"), eq("eib.temporal.abc"), any(), any()))
    .thenReturn(new InteractionAdmissionDecision("adm.2", "COMPLETED", null,
        new CanonicalSubmissionTrace("client", "adm.2", "CANCELLED", null, List.of()),
        List.of(), null));
```

and update assertions:

```java
.andExpect(status().isOk())
.andExpect(jsonPath("$.status").value("COMPLETED"))
.andExpect(jsonPath("$.payload.status").value("COMPLETED"))
```

The two existing upstream-unavailable controller tests remain semantically valid,
but they should pass through `EibHttpResponseMapper` after `admissionResponse()`
delegates to it.

---

## 10. ACTION-EIB-H-005 — DEBT-EIB-015 closure

File: `eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java`

In `admitTemporalCancellation`, insert after the `listTemporalActs` try/catch
block and before `codec.resolveTemporalRef(...)`:

```java
// After this block:
//   } catch (EibUpstreamUnavailableException e) { ... return FAILED_UPSTREAM_UNAVAILABLE }

// Add immediately:
if (!envelopeMapper.isSuccess(visible)) {
    EibError error = envelopeMapper.mapError(visible.error());
    String admDecision = mapScStatusToAdmissionStatus(visible.status());
    return new InteractionAdmissionDecision(
        admissionId, admDecision, null,
        new CanonicalSubmissionTrace(
            ctx.clientRequestRef(), admissionId,
            visible.status(), error, List.of()),
        envelopeMapper.mapWarnings(visible.warnings()), error);
}

// Then the existing resolveTemporalRef call follows unchanged:
return codec.resolveTemporalRef(...)
```

### Required tests: EibTemporalAdmissionServiceHardeningTest

Create `eib/src/test/java/com/sovereign/eib/EibTemporalAdmissionServiceHardeningTest.java`.

The existing `EibTemporalAdmissionServiceTest` already has `client`, `codec`,
`service`, `envelopeMapper`, `ctx` fields. If adding tests to that class,
reuse those fields. If creating a new class, replicate the setup.

```java
package com.sovereign.eib;

import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import com.sovereign.eib.service.EibEffectiveViewMapper;
import com.sovereign.eib.service.EibTemporalAdmissionService;
import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.domain.EibTemporalCancellationAdmissionRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EibTemporalAdmissionServiceHardeningTest {

    private final EibScNorthboundClient client = mock(EibScNorthboundClient.class);
    private final EibEffectiveRefCodec codec =
        new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256");
    private final EibCanonicalEnvelopeMapper envelopeMapper = new EibCanonicalEnvelopeMapper();
    private final EibTemporalAdmissionService service =
        new EibTemporalAdmissionService(client, envelopeMapper, codec);
    private final EibRequestContext ctx = new EibRequestContext(
        "ctx", "actor", "surface", "en",
        false, false, Instant.now(), "req");
    private final EibTemporalCancellationAdmissionRequest cancelReq =
        new EibTemporalCancellationAdmissionRequest("idem", "reason");

    @Test
    void cancelPropagatesDeferredScBRequiredFromListWithoutCallingCancel() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
            .thenReturn(new ScEnvelope<>("DEFERRED_SC_B_REQUIRED", null, List.of(), null));

        var decision = service.admitTemporalCancellation(
            "habitat.alpha", "eib.temporal.any", cancelReq, ctx);

        assertThat(decision.status()).isEqualTo("DEFERRED_SC_B_REQUIRED");
        assertThat(decision.status()).isNotEqualTo("REJECTED_NOT_VISIBLE");
        verify(client, never()).cancelTemporalAct(any(), any(), any());
    }

    @Test
    void cancelPropagatesUnknownPendingNormalizationFromListWithoutCallingCancel() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
            .thenReturn(new ScEnvelope<>("UNKNOWN_PENDING_NORMALIZATION", null, List.of(), null));

        var decision = service.admitTemporalCancellation(
            "habitat.alpha", "eib.temporal.any", cancelReq, ctx);

        assertThat(decision.status()).isEqualTo("DEFERRED_PENDING_NORMALIZATION");
        assertThat(decision.status()).isNotEqualTo("REJECTED_NOT_VISIBLE");
        verify(client, never()).cancelTemporalAct(any(), any(), any());
    }

    @Test
    void cancelPropagatesUnsupportedProfileFromListWithoutCallingCancel() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
            .thenReturn(new ScEnvelope<>("UNSUPPORTED_PROFILE", null, List.of(), null));

        var decision = service.admitTemporalCancellation(
            "habitat.alpha", "eib.temporal.any", cancelReq, ctx);

        assertThat(decision.status()).isEqualTo("DEFERRED_UNSUPPORTED_PROFILE");
        assertThat(decision.status()).isNotEqualTo("REJECTED_NOT_VISIBLE");
        verify(client, never()).cancelTemporalAct(any(), any(), any());
    }
}
```

---

## 11. ACTION-EIB-H-006 — Diagnostic/admin truth-table completion

File: `eib/src/test/java/com/sovereign/eib/EibEffectiveViewMapperTest.java`

Add two methods to the existing class. The class already has a `mapper` field.
Do not remove existing tests.

```java
@Test
void diagnosticRequestedButAdminNotEnabledYieldsOrdinaryMode() {
    // Truth-table row: diagnosticRequested=true, adminEnabled=false → ordinary
    // diagnosticAdminAuthorized = diagnosticRequested && adminEnabled = true && false = false
    EibRequestContext ctx = new EibRequestContext(
        "ctx", "actor", "surface", "en",
        true,   // diagnosticRequested = true
        false,  // diagnosticAdminAuthorized = false (config not enabled)
        Instant.now(), "req");

    var device = new NorthboundDeviceViewDto(
        "device.p.d1", "alias", "Lamp",
        "room.1", "zone.1", "LIGHT", "p",
        List.of(), List.of(), "raw-p-id");
    var view = mapper.device("habitat.alpha", device, List.of(), ctx);

    assertThat(view.canonicalDeviceId()).isNull();
}

@Test
void adminEnabledButDiagnosticNotRequestedYieldsOrdinaryMode() {
    // Truth-table row: diagnosticRequested=false, adminEnabled=true → ordinary
    // diagnosticAdminAuthorized = false && true = false
    EibRequestContext ctx = new EibRequestContext(
        "ctx", "actor", "surface", "en",
        false,  // diagnosticRequested = false
        false,  // diagnosticAdminAuthorized = false (header absent overrides config)
        Instant.now(), "req");
    // Note: EibRequestContext.fromHeaders computes diagnosticAdminAuthorized =
    // diagnosticRequested && adminEnabled. When constructing directly in tests,
    // set diagnosticAdminAuthorized = false to represent header-absent scenario.

    var device = new NorthboundDeviceViewDto(
        "device.p.d1", "alias", "Lamp",
        "room.1", "zone.1", "LIGHT", "p",
        List.of(), List.of(), "raw-p-id");
    var view = mapper.device("habitat.alpha", device, List.of(), ctx);

    assertThat(view.canonicalDeviceId()).isNull();
}
```

---

## 12. Stop conditions

### STOP-1 — Compile

```bash
cd eib && mvn compile -q
# Expected: BUILD SUCCESS
# Verify: grep -r "com.sovereign.connect" src/main/java/ | wc -l returns 0
```

### STOP-2 — Timeout and mapper

```bash
cd eib && mvn test -Dtest="EibScNorthboundClientTimeoutTest,EibAdmissionResponseMapperTest" -q
# Expected:
#   EibScNorthboundClientTimeoutTest: 2 tests, 0 failures
#   EibAdmissionResponseMapperTest: 11 tests, 0 failures
```

### STOP-3 — Hardening tests

```bash
cd eib && mvn test \
  -Dtest="EibTemporalAdmissionServiceHardeningTest,EibEffectiveViewMapperTest,EibApiControllerTest" -q
# Expected:
#   EibTemporalAdmissionServiceHardeningTest: 3 tests, 0 failures
#   EibEffectiveViewMapperTest: 4 tests (2 existing + 2 new), 0 failures
#   EibApiControllerTest: at least 5 tests (4 existing + 1 new AC-023-008a test), 0 failures
```

### STOP-4 — Full EIB suite

```bash
cd eib && mvn test -q
# Expected: >= 56 tests, 0 failures, 0 errors, 0 skipped
# Verify manually:
#   EibScNorthboundClientTimeoutTest: 2 (new)
#   EibScNorthboundClientTest: 11 + 4 new route regression = 15 (or in new class)
#   EibAdmissionResponseMapperTest: 11 (new)
#   EibTemporalAdmissionServiceHardeningTest: 3 (new)
#   EibEffectiveViewMapperTest: 4 (2 existing + 2 new)
#   EibApiControllerTest: at least 5 (4 existing + 1 new)
#   All other existing suites: unchanged
```

### STOP-5 — Non-regression

```bash
cd ..
mvn test -q
# Expected: SC-C 240 tests, 0 failures, 0 errors (unchanged)
# If SC-C is not rerun, record rationale in implementation report.
```

---

## 13. Negative scope

Do not:

```text
- modify SC-C production code
- reintroduce /topology/snapshot
- reintroduce /runtime-state/{subjectId}
- reintroduce ?status= or ?limit= for temporal-act listing
- reintroduce upstream /temporal-acts/signal
- implement View Composer, SApp, Surface, SC-B, SC-D
- add WireMock or MockWebServer (not needed)
- catch broad Exception instead of typed EibUpstreamUnavailableException
- collapse non-success listTemporalActs envelopes into REJECTED_NOT_VISIBLE
- use live external addresses for timeout tests (use 192.0.2.1 TEST-NET)
```
