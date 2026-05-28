# CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001

## Post-SDD Code Surface Audit — EIB Hardening

```text
Document ID:  CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
Title:        Post-SDD Code Surface Audit — EIB Hardening
Version:      v0.1.1-merged
Status:       Merged Draft / Post-SDD / Pre-MIR
Date:         2026-05-28
Corpus:       Sovereign Connect
Plane:        SC-X / EIB
Scope:        Code surface reconnaissance for EIB hardening MU-023
Baseline:     sovereign-connect-022-patch-2.zip (post-MU-022 patch-2)
Branch:       feat/sc-eib-mir-022-effective-interaction-boundary-seed
Input PDR:    PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate
Input SDD:    SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate
Result:       Approvable to open MIR with six mandatory implementation actions
Completeness: CSA-C3 — change-impact-complete for hardening scope
```

---

## 0. Purpose

This is the required post-SDD / pre-MIR code surface audit for
`SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate`.

It merges:

```text
- CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.1.0-draft
- independent implementation audit over sovereign-connect-022-patch-2.zip
```

This CSA does not authorize implementation by itself. It resolves the SDD CSA obligations, identifies the exact code surface to modify, and defines the mandatory implementation actions that the MIR and execution package must carry forward.

---

## 1. Executive verdict

```text
Verdict: Approvable to open MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001.
Suggested slot: MU-023
```

The baseline after MU-022 is structurally suitable for EIB hardening:

```text
- EIB remains a separate runtime under /eib.
- EIB consumes SC-C only through HTTP/OpenAPI.
- SC-C production code does not need to change.
- The upstream routing fixes from MU-022 patch are preserved.
- Failure-boundary normalization from MU-022 patch-2 is preserved.
- EIB baseline tests are green in included Surefire evidence.
```

However, the hardening gaps identified by the PDR/SDD are present in the current code surface and must be resolved before this EIB boundary can be treated as hardened for downstream SC-B planning.

Mandatory implementation actions:

```text
ACTION-EIB-H-001 — Apply timeout factory to RestClient.Builder before build().
ACTION-EIB-H-002 — Complete positive route regression coverage for missing upstream routes.
ACTION-EIB-H-003 — Update mapScStatusToAdmissionStatus: CANCELLED -> COMPLETED.
ACTION-EIB-H-004 — Replace admissionResponse() with full admission vocabulary mapper.
ACTION-EIB-H-005 — Add isSuccess() guard in admitTemporalCancellation before effective-ref resolution.
ACTION-EIB-H-006 — Add complete diagnostic/admin truth-table tests.
```

No new runtime dependency is required. No SC-C production code change is required.

---

## 2. Completeness envelope

### 2.1 Completeness level

```text
CSA-C3 — Change-impact-complete.
```

The audit covers the implementation surfaces touched by the hardening SDD:

```text
- EIB RestClient construction and timeout wiring;
- EIB upstream client route coverage;
- admission status mapping;
- HTTP/EibResponse wrapper mapping;
- TemporalAct cancellation effective-ref resolution;
- diagnostic/admin gating;
- retained debt disposition;
- test strategy and expected test delta.
```

### 2.2 Baseline

```text
ZIP:            sovereign-connect-022-patch-2.zip
Branch:         feat/sc-eib-mir-022-effective-interaction-boundary-seed
EIB evidence:   10 suites / 33 tests / 0 failures / 0 errors / 0 skipped
SC-C baseline:  27 suites / 240 tests / 0 failures / 0 errors / 0 skipped
Spring Boot:    3.3.5
Spring:         6.1.x via Spring Boot BOM
```

### 2.3 Audit limitations

```text
mvn is unavailable in the audit environment.
Fresh test execution was not performed.
The audit uses source inspection plus included Surefire reports.
Spring Framework 6.1.x API compatibility was checked by version/BOM surface.
```

---

## 3. Current code surface

### 3.1 Runtime placement

Current EIB placement is correct and must be preserved:

```text
eib/pom.xml
eib/src/main/java/com/sovereign/eib/EibApplication.java
eib/src/main/java/com/sovereign/eib/**
eib/src/test/java/com/sovereign/eib/**
```

Hardening MUST NOT move EIB into the SC-C Spring application context.

Rule preserved:

```text
Same repository is allowed.
Same SC-C runtime application context is not allowed.
EIB consumes SC-C only through HTTP/OpenAPI.
```

### 3.2 Relevant production classes

```text
eib/src/main/java/com/sovereign/eib/config/EibConfiguration.java
eib/src/main/java/com/sovereign/eib/config/EibScNorthboundClientProperties.java
eib/src/main/java/com/sovereign/eib/config/EibDiagnosticAdminProperties.java
eib/src/main/java/com/sovereign/eib/api/EibHabitatController.java
eib/src/main/java/com/sovereign/eib/api/EibTemporalActController.java
eib/src/main/java/com/sovereign/eib/domain/EibResponse.java
eib/src/main/java/com/sovereign/eib/domain/EibRequestContext.java
eib/src/main/java/com/sovereign/eib/domain/InteractionAdmissionDecision.java
eib/src/main/java/com/sovereign/eib/northbound/EibScNorthboundClient.java
eib/src/main/java/com/sovereign/eib/northbound/RestClientEibScNorthboundClient.java
eib/src/main/java/com/sovereign/eib/northbound/EibUpstreamUnavailableException.java
eib/src/main/java/com/sovereign/eib/service/EibCanonicalEnvelopeMapper.java
eib/src/main/java/com/sovereign/eib/service/EibEffectiveViewMapper.java
eib/src/main/java/com/sovereign/eib/service/EibEffectiveViewService.java
eib/src/main/java/com/sovereign/eib/service/EibTemporalActProjectionService.java
eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java
```

### 3.3 Relevant test classes

```text
eib/src/test/java/com/sovereign/eib/EibApiControllerTest.java
eib/src/test/java/com/sovereign/eib/EibArchitectureBoundaryTest.java
eib/src/test/java/com/sovereign/eib/EibCanonicalEnvelopeMapperTest.java
eib/src/test/java/com/sovereign/eib/EibEffectiveRefCodecTest.java
eib/src/test/java/com/sovereign/eib/EibEffectiveViewMapperTest.java
eib/src/test/java/com/sovereign/eib/EibEffectiveViewServiceTest.java
eib/src/test/java/com/sovereign/eib/EibHabitatControllerFailureBoundaryTest.java
eib/src/test/java/com/sovereign/eib/EibScNorthboundClientTest.java
eib/src/test/java/com/sovereign/eib/EibTemporalActProjectionServiceTest.java
eib/src/test/java/com/sovereign/eib/EibTemporalAdmissionServiceTest.java
```

---

## 4. Evidence snapshot

### 4.1 Dependency surface

`eib/pom.xml` includes:

```text
spring-boot-starter-web
jackson-datatype-jsr310
spring-boot-starter-test        test scope
```

Absent:

```text
OkHttp MockWebServer
WireMock
WebFlux
gRPC / ConnectRPC
GraphQL
MCP
NATS / JetStream
```

Decision:

```text
No new dependency is required for this hardening MU.
MockRestServiceServer remains sufficient for route regression.
Timeout testing can be done with SimpleClientHttpRequestFactory and a controlled timeout scenario.
```

### 4.2 Existing configuration

`EibScNorthboundClientProperties` already defines:

```java
@ConfigurationProperties(prefix = "sc.eib.northbound")
public record EibScNorthboundClientProperties(String baseUrl, long timeoutMs) {}
```

`application.yml` already provides:

```yaml
sc:
  eib:
    northbound:
      base-url: http://localhost:8080/sc/v1
      timeout-ms: 2000
```

### 4.3 Current RestClient wiring

Current `EibConfiguration`:

```java
@Bean
public RestClient.Builder eibRestClientBuilder(EibScNorthboundClientProperties props) {
    return RestClient.builder().baseUrl(props.baseUrl());
}

@Bean
public RestClient eibRestClient(RestClient.Builder eibRestClientBuilder) {
    return eibRestClientBuilder.build();
}
```

Finding:

```text
timeout-ms is configured but not applied.
RestClient.Builder bean is present and must be preserved.
Timeout request factory must be attached to this builder before RestClient is built.
```

### 4.4 Current admission mapping

Current `EibTemporalAdmissionService#mapScStatusToAdmissionStatus`:

```text
OK / CREATED / ACCEPTED / CANCELLED -> ADMITTED
UNSUPPORTED_PROFILE                 -> DEFERRED_UNSUPPORTED_PROFILE
DEFERRED_SC_B_REQUIRED              -> DEFERRED_SC_B_REQUIRED
UNKNOWN_PENDING_NORMALIZATION       -> DEFERRED_PENDING_NORMALIZATION
```

Finding:

```text
CANCELLED still maps to ADMITTED.
The SDD requires context-free CANCELLED -> COMPLETED.
```

### 4.5 Current controller wrapper mapping

Current `EibTemporalActController#admissionResponse(...)` effectively handles:

```text
FAILED_UPSTREAM_UNAVAILABLE -> HTTP 503 / EibResponse.status = UPSTREAM_UNAVAILABLE
all other decisions          -> HTTP 202 / EibResponse.status = ACCEPTED
```

Finding:

```text
This violates the hardening PDR/SDD rule that HTTP 202 is not a generic success wrapper.
The response wrapper must reflect the admission result, not merely the fact that EIB produced a response.
```

### 4.6 Current cancellation list+match behavior

Current cancellation path obtains visible temporal acts and then resolves effective ref by list+match.

Observed gap:

```text
If listTemporalActs returns a non-success SC-C envelope without throwing EibUpstreamUnavailableException,
the code can treat payload == null as an empty list and return REJECTED_NOT_VISIBLE.
```

Effect:

```text
DEFERRED_SC_B_REQUIRED, UNKNOWN_PENDING_NORMALIZATION or UNSUPPORTED_PROFILE from listTemporalActs
can be hidden as NOT_VISIBLE.
```

This is the concrete DEBT-EIB-015 closure target.

### 4.7 Current diagnostic/admin gating

Implementation in `EibRequestContext.fromHeaders`:

```java
boolean diagnosticRequested = Boolean.parseBoolean(request.getHeader("X-EIB-Diagnostic"));
// ...
diagnosticRequested && diagnosticAdminEnabled
```

Finding:

```text
The conjunctive rule is correct in production code.
The test suite lacks complete truth-table coverage.
```

Covered rows:

```text
header=false + config=false -> ordinary
header=true  + config=true  -> admin
```

Missing rows:

```text
header=true  + config=false -> ordinary
header=false + config=true  -> ordinary
```

### 4.8 Current route regression coverage

Confirmed current coverage in `EibScNorthboundClientTest`:

```text
GET  /topology                                      covered
GET  /topology/version                              covered
GET  /devices/{deviceId}/runtime-state              covered
GET  /endpoints/{endpointId}/runtime-state          covered
GET  /temporal-acts?mode=ACTIVE&maxResults=50       covered
POST /temporal-acts                                 covered
POST /temporal-acts/{temporalActId}/cancel          covered
HTTP 503 + valid ScEnvelope semantic response        covered
HTTP 503 + empty/non-envelope transport failure      covered
malformed JSON                                      covered
connection refused                                  covered
```

Missing positive route regression:

```text
GET /devices/{deviceId}/health
GET /endpoints/{endpointId}/health
GET /diagnostics
GET /temporal-acts/{temporalActId}
```

These routes are implemented but not protected by route-level tests. They must be covered in the hardening MU.

---

## 5. CSA obligation resolutions

### CSA-EIB-H-001 — Timeout enforcement

Resolved as mandatory implementation action.

Required patch target:

```text
eib/src/main/java/com/sovereign/eib/config/EibConfiguration.java
```

Required behavior:

```text
sc.eib.northbound.timeout-ms must apply to both connect timeout and read timeout.
The RestClient.Builder bean must be preserved.
The request factory must be attached to the builder before RestClient is built.
Timeout failures must normalize to EibUpstreamUnavailableException at client boundary and UPSTREAM_UNAVAILABLE at API boundary.
```

### CSA-EIB-H-002 — Admission response vocabulary hardening

Resolved as mandatory implementation action.

Required behavior:

```text
ADMITTED                         -> HTTP 202 / EibResponse.status = ADMITTED
COMPLETED                        -> HTTP 200 / EibResponse.status = COMPLETED
REJECTED_NOT_VISIBLE             -> HTTP 404 / EibResponse.status = NOT_VISIBLE
REJECTED_INVALID_REQUEST         -> HTTP 400 / EibResponse.status = INVALID_REQUEST
DEFERRED_SC_B_REQUIRED           -> HTTP 503 / EibResponse.status = DEFERRED
DEFERRED_PENDING_NORMALIZATION   -> HTTP 409 / EibResponse.status = PENDING_NORMALIZATION
DEFERRED_UNSUPPORTED_PROFILE     -> HTTP 501 / EibResponse.status = UNSUPPORTED
FAILED_UPSTREAM_UNAVAILABLE      -> HTTP 503 / EibResponse.status = UPSTREAM_UNAVAILABLE
FAILED_CANONICAL_SUBMISSION      -> HTTP 500 / EibResponse.status = FAILED
```

HTTP 202 remains valid only when EIB actually accepts responsibility for later completion. It is not a generic wrapper for any non-exception response.

### CSA-EIB-H-003 — CANCELLED mapping

Resolved as mandatory implementation action.

Required behavior:

```text
CANCELLED -> COMPLETED
```

Rationale:

```text
SC-C CANCELLED is terminal from the perspective of EIB cancellation admission.
It must not be exposed as ADMITTED or top-level ACCEPTED.
```

### CSA-EIB-H-004 — DEBT-EIB-015 closure

Resolved as mandatory implementation action.

Required behavior:

```text
admitTemporalCancellation must inspect listTemporalActs envelope success before effective-ref resolution.
If listTemporalActs is non-success, EIB must propagate the semantic status.
It must not collapse non-success envelopes into REJECTED_NOT_VISIBLE.
```

### CSA-EIB-H-005 — Diagnostic/admin truth table

Resolved as mandatory implementation action.

Required truth table:

```text
header=false + config=false -> ordinary
header=true  + config=false -> ordinary
header=false + config=true  -> ordinary
header=true  + config=true  -> diagnostic/admin
```

### CSA-EIB-H-006 — Upstream route boundary regression

Resolved as mandatory implementation action.

The hardening MU must complete positive route regression for the missing four routes and preserve existing route coverage.

### CSA-EIB-H-007 — Provider-native ID suppression

Current state is structurally correct:

```text
providerDeviceId/providerEndpointId have no fields in EffectiveDeviceView/EffectiveEndpointView.
```

Recommended hardening addition:

```text
Add JSON-level assertion that serialized ordinary EffectiveDeviceView/EffectiveEndpointView do not contain raw provider-native IDs.
```

This is recommended, not a blocker for MIR opening.

### CSA-EIB-H-008 — getTemporalAct production vs client-route status

Decision preserved from SDD:

```text
getTemporalAct remains active as an upstream client route and must have positive route regression.
Production getEffectiveTemporalAct may continue to use list+match for effective-ref resolution.
```

This is intentional, not a defect.

---

## 6. Mandatory implementation actions — exact specification

### ACTION-EIB-H-001 — Timeout factory and timeout tests

File:

```text
eib/src/main/java/com/sovereign/eib/config/EibConfiguration.java
```

Patch `eibRestClientBuilder`:

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

Required imports:

```java
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
```

Keep:

```java
@Bean
public RestClient eibRestClient(RestClient.Builder eibRestClientBuilder) {
    return eibRestClientBuilder.build();
}
```

Test requirements:

```text
- client-level timeout normalizes to EibUpstreamUnavailableException;
- API boundary normalizes timeout to EibResponse.status = UPSTREAM_UNAVAILABLE.
```

No new dependency is required. Acceptable strategies:

```text
A. Use SimpleClientHttpRequestFactory with very small timeout and TEST-NET / controlled unreachable address.
B. Use a JDK-local HTTP server with delayed response if the implementation wants a fully local read-timeout test.
```

Do not add MockWebServer/WireMock unless execution proves the no-dependency strategy unstable.

---

### ACTION-EIB-H-002 — Route regression gap coverage

Add to `EibScNorthboundClientTest` or a new `EibScNorthboundClientRouteRegressionTest`:

```text
getDeviceHealthUsesDeviceHealthRoute
  -> requestTo(".../devices/device.alpha/health")

getEndpointHealthUsesEndpointHealthRoute
  -> requestTo(".../endpoints/endpoint.alpha/health")

getDiagnosticsUsesDiagnosticsRoute
  -> requestTo(".../diagnostics")

getTemporalActUsesTemporalActByIdRoute
  -> requestTo(".../temporal-acts/temporal.3")
```

Preserve existing route regression for:

```text
/topology
/topology/version
/devices/{deviceId}/runtime-state
/endpoints/{endpointId}/runtime-state
/temporal-acts?mode=ACTIVE&maxResults=50
POST /temporal-acts
POST /temporal-acts/{temporalActId}/cancel
```

---

### ACTION-EIB-H-003 — mapScStatusToAdmissionStatus: CANCELLED -> COMPLETED

File:

```text
eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java
```

Patch:

```java
// Before:
case "OK", "CREATED", "ACCEPTED", "CANCELLED" -> "ADMITTED";

// After:
case "OK", "CREATED", "ACCEPTED" -> "ADMITTED";
case "CANCELLED" -> "COMPLETED";
```

---

### ACTION-EIB-H-004 — Full admission vocabulary mapper

Replace controller-local generic fallback with a full vocabulary mapper.

Recommended placement:

```text
eib/src/main/java/com/sovereign/eib/api/EibHttpResponseMapper.java
```

Required mapping:

```java
public ResponseEntity<EibResponse<InteractionAdmissionDecision>> admissionResponse(
        InteractionAdmissionDecision decision) {
    return switch (decision.status()) {
        case "ADMITTED" ->
            ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new EibResponse<>("ADMITTED", decision,
                    decision.warnings(), decision.error(), null));
        case "COMPLETED" ->
            ResponseEntity.status(HttpStatus.OK)
                .body(new EibResponse<>("COMPLETED", decision,
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
        default ->
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new EibResponse<>("FAILED", decision,
                    decision.warnings(), decision.error(), null));
    };
}
```

Required tests:

```text
EibAdmissionResponseMapperTest
  one assertion per vocabulary entry;
  verify both HTTP status and EibResponse.status.
```

Also extend API controller tests so the observable `/eib/v1` boundary uses this mapper.

---

### ACTION-EIB-H-005 — DEBT-EIB-015 closure in cancellation path

File:

```text
eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java
```

In `admitTemporalCancellation`, after `listTemporalActs(...)` succeeds and before `codec.resolveTemporalRef(...)`, add success check:

```java
if (!envelopeMapper.isSuccess(visible)) {
    EibError error = envelopeMapper.mapError(visible.error());
    String admissionStatus = mapScStatusToAdmissionStatus(visible.status());
    return new InteractionAdmissionDecision(
        admissionId,
        admissionStatus,
        null,
        new CanonicalSubmissionTrace(
            ctx.clientRequestRef(),
            admissionId,
            visible.status(),
            error,
            List.of()),
        envelopeMapper.mapWarnings(visible.warnings()),
        error);
}
```

Required tests:

```text
cancelPropagatesDeferredScBFromList
  listTemporalActs -> DEFERRED_SC_B_REQUIRED
  decision.status -> DEFERRED_SC_B_REQUIRED
  cancelTemporalAct is not invoked

cancelPropagatesUnknownPendingNormalizationFromList
  listTemporalActs -> UNKNOWN_PENDING_NORMALIZATION
  decision.status -> DEFERRED_PENDING_NORMALIZATION
  cancelTemporalAct is not invoked

cancelPropagatesUnsupportedProfileFromList
  listTemporalActs -> UNSUPPORTED_PROFILE
  decision.status -> DEFERRED_UNSUPPORTED_PROFILE
  cancelTemporalAct is not invoked
```

---

### ACTION-EIB-H-006 — Diagnostic/admin truth-table completion

File:

```text
eib/src/test/java/com/sovereign/eib/EibEffectiveViewMapperTest.java
```

Add missing rows:

```text
header=true  + config=false -> ordinary
header=false + config=true  -> ordinary
```

Acceptable implementation:

```text
- construct EibRequestContext values that represent both rows; or
- add controller/request-context tests that exercise EibRequestContext.fromHeaders directly.
```

The stronger option is to test both:

```text
1. EibRequestContext.fromHeaders computes diagnosticAdminAuthorized correctly;
2. EibEffectiveViewMapper suppresses canonical IDs when diagnosticAdminAuthorized is false.
```

---

## 7. Recommended non-blocking additions

### 7.1 JSON-level provider-native ID suppression test

Add a serialization assertion:

```text
ordinary EffectiveDeviceView JSON does not contain providerDeviceId raw value.
ordinary EffectiveEndpointView JSON does not contain providerEndpointId raw value.
```

Rationale:

```text
Current suppression is structural, but JSON-level regression catches accidental future leaks.
```

### 7.2 Timeout validation

If execution touches properties validation, it MAY add:

```text
timeoutMs > 0 validation
```

This is useful, but not mandatory for MIR opening unless the MIR chooses to require it.

---

## 8. Expected test impact

Current EIB baseline:

```text
33 tests / 0 failures / 0 errors / 0 skipped
```

Minimum expected hardening delta:

```text
EibScNorthboundClientTimeoutTest             +2
EibScNorthboundClientRouteRegressionTest     +4
EibAdmissionResponseMapperTest               +9
EibTemporalAdmissionServiceHardeningTest     +3
Diagnostic/admin truth-table tests           +2 to +4
EibApiControllerAdmissionStatusTest          +6 to +8
```

Expected minimum final EIB test count:

```text
>= 56 tests
```

The exact count may differ if tests are consolidated into existing classes, but coverage must not be lower than the mandatory behaviors listed above.

---

## 9. Retained debt table

| Debt | Description | Classification | Disposition in hardening |
|---|---|---|---|
| `DEBT-EIB-014` | Timeout configured but not enforced | `requires-resolution-before-SC-B` | MUST close |
| `DEBT-EIB-015` | Cancel collapses non-success list envelope to NOT_VISIBLE | `requires-resolution-before-EIB-action-admission` | MUST close |
| `DEBT-EIB-012` | Persistent effective-ref registry absent | `accepted-for-now` | Deferred; list+match remains |
| `DEBT-EIB-003` | Authority/Policy/Identity/Session real integration absent | `requires-resolution-before-SApp-Surface` | Deferred |
| `DEBT-EIB-013` | Auth/authz stubbed/config-gated | `requires-resolution-before-SApp-Surface` | Deferred; gating tests required now |
| `DEBT-EIB-010` | EIB conformance/TCK absent | `requires-resolution-before-industrial` | Deferred |
| `DEBT-EIB-004` | Action admission SC-B-gated | `requires-resolution-before-EIB-action-admission` | Deferred |
| `DEBT-EIB-005` | Discovery admission SC-B-gated | `requires-resolution-before-EIB-action-admission` | Deferred |
| `DEBT-EIB-006` | Live updates deferred | `requires-resolution-before-View-Composer` | Deferred |
| `DEBT-EIB-011` | Durable audit/admission ledger absent | `requires-resolution-before-industrial` | Deferred |

---

## 10. Surface inventory matrix

| Surface | Expected by SDD | Current state | Disposition |
|---|---|---|---|
| `EibConfiguration.eibRestClientBuilder` | Timeout factory on builder | Builder only, no factory | ACTION-EIB-H-001 |
| `EibScNorthboundClientTimeoutTest` | Timeout boundary tests | Absent | ACTION-EIB-H-001 |
| Route regression: health×2, diagnostics, getTemporalAct | Positive assertions | Absent | ACTION-EIB-H-002 |
| `mapScStatusToAdmissionStatus: CANCELLED` | `COMPLETED` | `ADMITTED` | ACTION-EIB-H-003 |
| `admissionResponse()` | Full vocabulary mapper | 2-branch mapper | ACTION-EIB-H-004 |
| `EibAdmissionResponseMapperTest` | Required | Absent | ACTION-EIB-H-004 |
| `admitTemporalCancellation` non-success guard | Before effective-ref resolution | Absent | ACTION-EIB-H-005 |
| `EibTemporalAdmissionServiceHardeningTest` | Required | Absent | ACTION-EIB-H-005 |
| Diagnostic/admin truth table | Four rows | Two rows | ACTION-EIB-H-006 |
| Provider-native ID JSON suppression | Recommended | Structural only | Recommended |
| MockWebServer / WireMock | Not required | Absent | No action |
| SC-C production code | No changes | Unchanged | No action |

---

## 11. Search ledger

```text
EibConfiguration.java
  - eibRestClientBuilder: RestClient.builder().baseUrl() only; no request factory.

EibScNorthboundClientProperties.java
  - fields: baseUrl (String), timeoutMs (long); no rename needed.

EibTemporalActController.java
  - admissionResponse: 2-branch method, generic HTTP 202/ACCEPTED fallback.

EibTemporalAdmissionService.java
  - mapScStatusToAdmissionStatus: CANCELLED -> ADMITTED.
  - admitTemporalCancellation: no isSuccess guard before resolveTemporalRef.

EibRequestContext.java
  - diagnosticAdminAuthorized = diagnosticRequested && diagnosticAdminEnabled.

EibEffectiveViewMapper.java
  - canonicalId gated by ctx.diagnosticAdminAuthorized().
  - providerDeviceId/providerEndpointId not present in effective view records.

EibEffectiveViewMapperTest.java
  - covers ordinary(F,F) and admin(T,T).
  - missing rows (T,F) and (F,T).

EibScNorthboundClientTest.java
  - current route regression covers 7 routes + failure modes.
  - missing health×2, diagnostics and getTemporalAct route assertions.

pom.xml (eib)
  - spring-boot-starter-test present.
  - MockRestServiceServer available.
  - MockWebServer/WireMock absent and not required.
```

---

## 12. MIR opening requirements

The downstream MIR must include acceptance criteria that verify at least:

```text
AC-H-001 — RestClient builder applies timeout factory from timeout-ms.
AC-H-002 — timeout is normalized to EibUpstreamUnavailableException / UPSTREAM_UNAVAILABLE.
AC-H-003 — CANCELLED maps to COMPLETED.
AC-H-004 — admission HTTP/EibResponse mapper covers all hardening vocabulary statuses.
AC-H-005 — non-success listTemporalActs envelopes propagate before effective-ref resolution.
AC-H-006 — diagnostic/admin gating truth-table is complete.
AC-H-007 — missing route regression cases are added.
AC-H-008 — SC-C production code is untouched.
AC-H-009 — EIB remains a separate runtime under /eib.
AC-H-010 — DEBT-EIB-014 and DEBT-EIB-015 are closed if and only if tests pass.
```

---

## 13. Verdict

```text
Post-SDD CSA verdict: Approvable.
MIR may be opened.
Mandatory actions: six.
No new test dependency required.
No SC-C production code change required.
Expected EIB test count after implementation: >= 56.
```

Next artifact:

```text
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.1.0-draft
Operational slot: MU-023
```
