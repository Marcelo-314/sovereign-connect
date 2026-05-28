# SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001

## Effective Interaction Boundary Hardening Design

```text
Document ID:  SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
Title:        Effective Interaction Boundary Hardening Design
Version:      v0.2.0-candidate
Status:       Candidate / PDR candidate consumed / CSA required / not implementation-authorizing
Date:         2026-05-28
Corpus:       Sovereign Connect
Type:         SDD
Plane:        SC-X / EIB
Scope:        Implementable design for the EIB hardening descent after MU-022,
              covering upstream timeout enforcement, admission response vocabulary,
              diagnostic/admin gating, upstream route regression and retained debt
              disposition before SC-B runtime integration.
```

---

## Changelog

### v0.2.0-candidate

Candidate patch after review.

This version:

1. Makes the admission mapper decision explicit: `CANCELLED` maps context-free to `COMPLETED`, not `ADMITTED`.
2. Preserves the `RestClient.Builder` bean construction pattern so timeout enforcement and test interception remain compatible.
3. Aligns the expected code surface with the current MU-022 package/class names.
4. Keeps `getTemporalAct` active at client-regression level while production effective-ref resolution remains list+match.

### v0.1.0-draft

Initial draft.

This version:

1. Consumes `PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate`.
2. Defines timeout enforcement for the EIB upstream SC-C `RestClient`.
3. Defines the EIB admission HTTP/status mapper and removes generic top-level `ACCEPTED` behavior.
4. Resolves the PDR open question on synchronous admitted outcomes: synchronous terminal cancellation maps to HTTP `200` with top-level `COMPLETED`, not HTTP `202`.
5. Preserves HTTP `202` only for true deferred responsibility / canonical lifecycle continuation.
6. Requires propagation of non-success `listTemporalActs` envelopes before effective-ref resolution.
7. Defines diagnostic/admin truth-table tests.
8. Defines upstream route regression tests against the real MU-021 SC-C HTTP/OpenAPI surface.
9. Resolves `GET /temporal-acts/{temporalActId}` as an active client route for client-level regression, while production effective-ref resolution remains list+match until a persistent effective-ref registry exists.
10. Requires post-SDD / pre-MIR CSA before opening MIR.

---

## Depends on

- `PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate`
- `PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.3.0-candidate`
- `SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.2.0-candidate`
- `CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-merged`
- `MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001 v1.0.0-accepted`
- `MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001 v1.0.0-accepted`

## Related

- `REVIEW-MU-022-EFFECTIVE-INTERACTION-BOUNDARY-SEED`
- `REVIEW-MU-022-ROUTING-FIX-PATCH-HALLAZGOS`
- `review-MU-022-routing-fix-patch-implementation`
- `review-MU-022-failure-boundary-patch-2-implementation`
- `docs/mir/mir-022/`
- `PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.16-draft`
- `INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.22-draft`
- `SYNC-SOV-SC-MU-BACKLOG-001 v0.1.23-draft`

---

# 0. Purpose

This SDD translates the accepted EIB hardening PDR into an implementable design.

It is not a MIR and does not authorize implementation directly.

It defines the code-level design that a downstream CSA, MIR and execution package must verify before implementation.

---

# 1. Baseline and target

## 1.1 Baseline

This SDD assumes:

```text
MU-021:
  SC-C Northbound HTTP/OpenAPI binding is Validated L4.

MU-022:
  EIB initial implementation slice is Validated L4.
  EIB exists as a separate runtime consumer of SC-C through HTTP/OpenAPI.
```

The EIB implementation currently has validated behavior for:

```text
separate runtime placement;
EIB-owned mirror DTOs;
RestClient-based upstream client;
effective refs;
effective views;
TemporalAct observation;
Signal TemporalAct admission/cancellation;
canonical envelope preservation;
provider-native ID suppression;
upstream failure normalization for the implemented slice.
```

## 1.2 Target

This hardening design must make EIB safer as a runtime boundary before SC-B integration and before View Composer / SApp / Surface-facing descent.

Targeted hardening areas:

```text
H-001 timeout enforcement;
H-002 admission response vocabulary hardening;
H-003 diagnostic/admin gating hardening;
H-004 upstream route boundary regression;
H-005 retained debt disposition.
```

## 1.3 Maturity classification note

This SDD is maturity-neutral. It defines design.

Any Seed / Hardening / Industrial classification belongs to MIR closure, implementation reports, INDEX/SYNC and explicit closure records.

---

# 2. Non-goals

This SDD does not design or authorize:

```text
SC-B runtime implementation;
NATS / JetStream / broker binding;
SC-D adapter runtime;
device/endpoint action execution;
discovery execution;
View Composer;
SApp;
Surface-facing API;
GraphQL;
MCP;
gRPC / ConnectRPC;
WebSocket / live updates;
real Authority / Policy / Identity / Session integration;
persistent effective-ref registry;
EIB TCK or conformance harness.
```

---

# 3. Runtime and package boundary

## 3.1 Placement invariant

EIB remains a separate runtime boundary.

```text
Same repository is allowed.
Same SC-C runtime application context is not allowed.
EIB consumes SC-C only through HTTP/OpenAPI.
```

This hardening work must stay inside the EIB runtime module and its tests, except for documentation under `docs/mir/mir-023/` or the exact path chosen by MIR governance.

## 3.2 Forbidden changes

The hardening implementation must not:

```text
modify SC-C core behavior;
modify SC-C Northbound HTTP routes;
introduce direct imports from com.sovereign.connect.* into EIB production code;
move EIB back into the SC-C Spring context;
introduce SC-B runtime;
introduce product-facing UX;
introduce Action/Discovery admission over devices/endpoints;
replace list+match effective-ref resolution with a persistent registry;
introduce broad catch Exception swallowing.
```

---

# 4. Code surface expected by this design

The post-SDD CSA must verify exact class names and paths, but the expected implementation surface is:

```text
eib/src/main/java/com/sovereign/eib/northbound/RestClientEibScNorthboundClient.java
eib/src/main/java/com/sovereign/eib/northbound/EibScNorthboundClient.java
eib/src/main/java/com/sovereign/eib/northbound/EibUpstreamUnavailableException.java
eib/src/main/java/com/sovereign/eib/config/EibScNorthboundClientProperties.java
eib/src/main/java/com/sovereign/eib/config/EibNorthboundClientConfiguration.java
eib/src/main/java/com/sovereign/eib/api/EibHabitatController.java
eib/src/main/java/com/sovereign/eib/api/EibTemporalActController.java
eib/src/main/java/com/sovereign/eib/domain/EibResponse.java
eib/src/main/java/com/sovereign/eib/domain/InteractionAdmissionDecision.java
eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java
eib/src/main/java/com/sovereign/eib/service/EibEffectiveViewService.java
eib/src/main/java/com/sovereign/eib/service/EibTemporalActProjectionService.java
eib/src/main/java/com/sovereign/eib/service/EibCanonicalEnvelopeMapper.java
eib/src/main/java/com/sovereign/eib/service/EibEffectiveViewMapper.java
```

The actual package names may vary only if the CSA confirms the current implementation uses different names. The MIR must use exact names from the codebase.

---

# 5. Timeout enforcement design

## 5.1 Configuration

Existing configuration:

```yaml
sc:
  eib:
    northbound:
      base-url: http://localhost:8080/sc/v1
      timeout-ms: 2000
```

`timeout-ms` must become operational.

Rules:

```text
- timeout-ms MUST be positive.
- timeout-ms MUST configure connect timeout.
- timeout-ms MUST configure read/response timeout.
- timeout failures MUST become EibUpstreamUnavailableException or equivalent.
- API boundary MUST normalize timeout failure as EibResponse.status = UPSTREAM_UNAVAILABLE with HTTP 503.
```

## 5.2 RestClient construction

Expected implementation strategy:

```java
@Configuration
@EnableConfigurationProperties(EibScNorthboundClientProperties.class)
public class EibNorthboundClientConfiguration {

    @Bean
    public RestClient.Builder eibRestClientBuilder(EibScNorthboundClientProperties props) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(props.timeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));

        return RestClient.builder()
            .baseUrl(props.baseUrl())
            .requestFactory(requestFactory);
    }

    @Bean
    public RestClient eibRestClient(RestClient.Builder eibRestClientBuilder) {
        return eibRestClientBuilder.build();
    }
}
```

Design rule:

```text
Timeout configuration MUST be applied to the builder before the RestClient is built.
The RestClient.Builder bean MUST remain available for tests that bind MockRestServiceServer
before constructing the RestClient under test.
```

The CSA must verify the exact Spring version APIs. If `SimpleClientHttpRequestFactory` timeout signatures differ in the local Spring version, the CSA may recommend an equivalent `ClientHttpRequestFactory` with the same semantics.

The CSA must also verify that timeout enforcement does not break the existing test pattern in which `MockRestServiceServer.bindTo(builder)` is installed before constructing the `RestClient` and `RestClientEibScNorthboundClient` used by the test.

## 5.3 Exception normalization

`RestClientEibScNorthboundClient` must treat the following as upstream unavailability:

```text
connection timeout;
read timeout;
connection refused;
no route to host;
malformed/non-JSON response when a ScEnvelope is required;
HTTP 5xx without a parseable ScEnvelope body.
```

It must not treat a valid SC-C `ScEnvelope` with non-OK status as transport failure.

Example distinction:

```text
HTTP 503 + valid ScEnvelope(status = DEFERRED_SC_B_REQUIRED)
  -> semantic SC-C response, not transport failure.

HTTP 503 + no body / malformed body / non-envelope body
  -> EibUpstreamUnavailableException.
```

## 5.4 Timeout tests

The MIR must include tests equivalent to:

```text
- connect/read timeout produces EibUpstreamUnavailableException;
- timeout is normalized to EibResponse.status = UPSTREAM_UNAVAILABLE at API boundary;
- semantic HTTP 503 with valid ScEnvelope is not treated as transport failure.
```

Preferred test mechanism:

```text
MockWebServer / WireMock / equivalent local mock HTTP server that can delay response;
or another deterministic test mechanism approved by CSA.
```

`MockRestServiceServer` may be used only if the test binds before constructing the `RestClient` under test.

---

# 6. EIB response vocabulary design

## 6.1 Wire-level status vocabulary

`EibResponse<T>.status` remains a product-safe EIB status string at the API boundary.

This SDD defines the following required top-level statuses for hardening:

```text
ADMITTED
COMPLETED
NOT_VISIBLE
INVALID_REQUEST
DEFERRED
PENDING_NORMALIZATION
UNSUPPORTED
UPSTREAM_UNAVAILABLE
FAILED
```

`COMPLETED` is introduced for synchronous operation-specific success where EIB and SC-C have already reached a terminal outcome during the request.

The SDD does not require replacing records with enums, but the implementation SHOULD centralize constants or an enum internally to prevent spelling drift.

## 6.2 Mapping table

| Condition | HTTP | EibResponse.status | Notes |
|---|---:|---|---|
| Signal TemporalAct create admitted into canonical lifecycle | 202 | `ADMITTED` | EIB submitted to SC-C and canonical lifecycle continues after response. |
| TemporalAct cancellation completed synchronously | 200 | `COMPLETED` | SC-C returned `CANCELLED` or equivalent terminal cancellation status. No later EIB responsibility is implied. |
| Effective ref or entity not visible | 404 | `NOT_VISIBLE` | Not a success, not accepted. |
| Invalid request / invalid effective ref syntax | 400 | `INVALID_REQUEST` | Product-safe invalid request. |
| SC-B required but unavailable/not implemented | 503 | `DEFERRED` | Not admitted. Must not be 202. |
| Pending normalization / uncertain effective state | 409 | `PENDING_NORMALIZATION` | Not admitted. Must not fabricate certainty. |
| Unsupported upstream profile | 501 | `UNSUPPORTED` | Explicit `DEFERRED_UNSUPPORTED_PROFILE` or SC-C `UNSUPPORTED_PROFILE`. |
| Upstream SC-C unavailable | 503 | `UPSTREAM_UNAVAILABLE` | Transport/runtime boundary failure. |
| Unclassified internal EIB failure | 500 | `FAILED` | Must not leak internals. |

## 6.3 HTTP 202 restriction

HTTP `202` is allowed only for the first row above.

The following must never return HTTP `202`:

```text
NOT_VISIBLE;
INVALID_REQUEST;
DEFERRED;
PENDING_NORMALIZATION;
UNSUPPORTED;
UPSTREAM_UNAVAILABLE;
FAILED;
synchronous terminal cancellation success.
```

## 6.4 Mapper placement

Current `admissionResponse()` logic must be replaced or hardened through a dedicated mapper, for example:

```text
EibHttpResponseMapper#admissionResponse(InteractionAdmissionDecision decision)
```

The mapper must be the single place that converts admission decisions into:

```text
HTTP status;
EibResponse.status;
warnings;
error;
canonicalTrace.
```

Controllers should not duplicate decision-to-status mapping.

---

# 7. Admission decision mapping

## 7.1 Existing decision statuses

The current implementation may contain decision statuses equivalent to:

```text
ADMITTED
REJECTED_NOT_VISIBLE
REJECTED_INVALID_REQUEST
DEFERRED_SC_B_REQUIRED
DEFERRED_PENDING_NORMALIZATION
DEFERRED_UNSUPPORTED_PROFILE
FAILED_UPSTREAM_UNAVAILABLE
```

The hardening implementation must map these explicitly.

## 7.2 Required mapping

Decision: **Option A — context-free `CANCELLED -> COMPLETED` mapping.**

The hardening descent MUST update the admission status mapper so that SC-C `CANCELLED` never maps to `ADMITTED`. `CANCELLED` denotes a terminal cancellation outcome and MUST map to `COMPLETED` regardless of which code path produced the envelope.

Required core mapper rule:

```text
ACCEPTED  -> ADMITTED
CANCELLED -> COMPLETED
```

If the implementation keeps one method such as `mapScStatusToAdmissionStatus(scStatus)`, that method MUST implement the rule above. If the implementation splits operation-specific mappers later, the cancel-specific mapper MUST still preserve `CANCELLED -> COMPLETED`.

```text
ADMITTED from create Signal TemporalAct:
  -> HTTP 202
  -> EibResponse.status = ADMITTED

CANCELLED from cancel TemporalAct where SC-C already returned terminal cancellation:
  -> HTTP 200
  -> EibResponse.status = COMPLETED

REJECTED_NOT_VISIBLE:
  -> HTTP 404
  -> EibResponse.status = NOT_VISIBLE

REJECTED_INVALID_REQUEST:
  -> HTTP 400
  -> EibResponse.status = INVALID_REQUEST

DEFERRED_SC_B_REQUIRED:
  -> HTTP 503
  -> EibResponse.status = DEFERRED

DEFERRED_PENDING_NORMALIZATION:
  -> HTTP 409
  -> EibResponse.status = PENDING_NORMALIZATION

DEFERRED_UNSUPPORTED_PROFILE:
  -> HTTP 501
  -> EibResponse.status = UNSUPPORTED

FAILED_UPSTREAM_UNAVAILABLE:
  -> HTTP 503
  -> EibResponse.status = UPSTREAM_UNAVAILABLE
```

## 7.3 Non-success `listTemporalActs` propagation

This closes `DEBT-EIB-015`.

Current risk:

```text
listTemporalActs returns non-success envelope;
payload is null;
EIB resolves effective ref against empty list;
EIB returns REJECTED_NOT_VISIBLE.
```

Required behavior:

```text
1. Call listTemporalActs.
2. Inspect envelope status before effective-ref resolution.
3. If the envelope is non-success, map the semantic status to an admission decision.
4. Return the mapped EibResponse.
5. Do not perform effective-ref resolution against an empty fallback list.
6. Do not call cancelTemporalAct upstream.
```

Example mappings:

```text
ScEnvelope.status = DEFERRED_SC_B_REQUIRED
  -> InteractionAdmissionDecision.status = DEFERRED_SC_B_REQUIRED
  -> HTTP 503 / EibResponse.status = DEFERRED

ScEnvelope.status = UNKNOWN_PENDING_NORMALIZATION
  -> InteractionAdmissionDecision.status = DEFERRED_PENDING_NORMALIZATION
  -> HTTP 409 / EibResponse.status = PENDING_NORMALIZATION

ScEnvelope.status = UNSUPPORTED_PROFILE
  -> InteractionAdmissionDecision.status = DEFERRED_UNSUPPORTED_PROFILE
  -> HTTP 501 / EibResponse.status = UNSUPPORTED
```

The SDD deliberately keeps exact method names for CSA verification because the current code may use `isSuccess(...)`, `visible.payload()` or similar helpers.

## 7.4 Canonical trace preservation

Admission responses must preserve canonical trace semantics.

For non-submitted outcomes:

```text
canonicalTrace.scNorthboundStatus = NOT_SUBMITTED
```

unless the non-success outcome came from an actual SC-C envelope. In that case:

```text
canonicalTrace.scNorthboundStatus = original SC-C envelope status
canonicalTrace.error.source       = original error.source, when present
canonicalTrace.warning.source     = original warning.source, when present
```

---

# 8. TemporalAct route activation decision

## 8.1 Decision

`GET /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}` remains an active upstream client capability and must be covered by a client-level route regression test.

However, production `getEffectiveTemporalAct(effectiveTemporalActRef)` continues to use list+match until a persistent effective-ref registry exists.

Reason:

```text
Effective refs are non-reversible HMAC refs.
Without a persistent effective-ref registry, EIB cannot derive temporalActId directly from effectiveTemporalActRef.
```

## 8.2 Required documentation

The implementation report must explicitly state:

```text
- getTemporalAct upstream route is implemented and route-tested at client level;
- getEffectiveTemporalAct production flow still uses list+match;
- persistent effective-ref registry remains deferred.
```

## 8.3 Required route regression

A route regression test must still verify:

```text
GET /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}
```

using the client method directly.

---

# 9. Diagnostic/admin gating design

## 9.1 Context inputs

Current EIB context may derive diagnostic mode from headers and configuration.

Required effective rule:

```text
diagnosticAdminAuthorized = diagnosticModeRequested && diagnosticAdminEnabled
```

## 9.2 Truth table

The implementation must prove:

| Request diagnostic header | Config/admin enabled | Result |
|---|---|---|
| absent / false | false | ordinary mode |
| true | false | ordinary mode |
| absent / false | true | ordinary mode |
| true | true | diagnostic/admin mode |

## 9.3 Data exposure rules

Ordinary mode MUST suppress:

```text
canonicalDeviceId;
canonicalEndpointId;
canonicalTemporalActId;
providerDeviceId;
providerEndpointId.
```

Diagnostic/admin mode MAY expose canonical IDs but MUST still suppress provider-native IDs unless a later diagnostic/admin artifact explicitly authorizes provider-native exposure.

## 9.4 Required tests

Tests must cover all four truth-table rows.

Positive-only diagnostic tests are insufficient.

---

# 10. Upstream route regression design

## 10.1 Required route contract

EIB hardening must include positive regression tests for these SC-C MU-021 routes:

```text
GET  /sc/v1/habitats/{habitatId}/topology
GET  /sc/v1/habitats/{habitatId}/topology/version
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/health
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/health
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/diagnostics
GET  /sc/v1/habitats/{habitatId}/temporal-acts?mode=ACTIVE&maxResults=50
GET  /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}
POST /sc/v1/habitats/{habitatId}/temporal-acts
POST /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
```

## 10.2 Preferred testing mechanism

Preferred mechanism:

```text
MockWebServer or equivalent local HTTP server that records requests.
```

Required assertions:

```text
method;
path;
query string;
request body for POST create;
request body for POST cancel;
content type, where applicable.
```

If the CSA rejects a new test dependency, `MockRestServiceServer` may be used, but only if the test construction order is explicit:

```text
bind mock server to builder;
build RestClient;
build RestClientEibScNorthboundClient;
execute request.
```

## 10.3 Negative route regression

Tests must fail if these former fantasy routes return:

```text
/topology/snapshot;
/runtime-state/{subjectId};
?status=&limit=;
upstream /temporal-acts/signal.
```

The implementation may express this as absence assertions over recorded requests or as exact route assertions for all relevant client methods.

---

# 11. Debt disposition design

## 11.1 Closed by this hardening if implemented

```text
DEBT-EIB-014 — upstream timeout configured but not enforced.
DEBT-EIB-015 — cancellation non-success listTemporalActs envelope collapse.
```

## 11.2 Deferred debts

```text
DEBT-EIB-012 — persistent effective-ref registry absent.
  Classification: accepted-for-now / requires-resolution-before-scale.

DEBT-EIB-003 — Authority/Policy/Identity/Session real integration absent.
  Classification: requires-resolution-before-SApp-Surface.

DEBT-EIB-013 — auth/authz remains stubbed or config-gated.
  Classification: requires-resolution-before-SApp-Surface.

DEBT-EIB-010 — EIB conformance/TCK absent.
  Classification: requires-resolution-before-industrial.

DEBT-EIB-004 — device/endpoint action admission remains SC-B-gated.
  Classification: requires-resolution-before-EIB-action-admission.

DEBT-EIB-005 — discovery admission remains SC-B-gated.
  Classification: requires-resolution-before-EIB-action-admission.

DEBT-EIB-006 — live updates deferred.
  Classification: requires-resolution-before-View-Composer or later.

DEBT-EIB-011 — durable audit/admission ledger absent.
  Classification: requires-resolution-before-industrial.
```

## 11.3 Implementation report requirement

The implementation report must include a retained debt table with:

```text
debt id;
meaning;
classification;
closed/deferred status;
evidence or rationale.
```

---

# 12. Test design

## 12.1 Required test classes

The MIR/CSA may adjust exact names, but the expected tests are:

```text
EibScNorthboundClientTimeoutTest
EibScNorthboundClientRouteRegressionTest
EibAdmissionResponseMapperTest
EibTemporalAdmissionServiceHardeningTest
EibDiagnosticAdminGatingTest
EibApiControllerAdmissionStatusTest
```

## 12.2 Required test coverage

Timeout:

```text
timeout-ms applied to RestClient request factory;
connect/read timeout normalized to EibUpstreamUnavailableException;
API boundary returns HTTP 503 / UPSTREAM_UNAVAILABLE.
```

Admission mapping:

```text
create Signal admitted -> 202 / ADMITTED;
cancel synchronous terminal -> 200 / COMPLETED;
not visible -> 404 / NOT_VISIBLE;
invalid request -> 400 / INVALID_REQUEST;
SC-B required -> 503 / DEFERRED;
pending normalization -> 409 / PENDING_NORMALIZATION;
unsupported profile -> 501 / UNSUPPORTED;
upstream unavailable -> 503 / UPSTREAM_UNAVAILABLE;
no rejected/deferred/unsupported/upstream failure returns top-level ACCEPTED.
```

Cancellation debt:

```text
non-success listTemporalActs envelope is propagated before effective-ref resolution;
no cancel upstream call occurs after non-success listTemporalActs;
NOT_VISIBLE is used only after successful listTemporalActs with no matching effective ref.
```

Diagnostic/admin:

```text
all four truth-table rows;
canonical IDs only in both-conditions-true case;
provider-native IDs still suppressed.
```

Routes:

```text
all eleven required SC-C routes emitted correctly;
former fantasy routes absent/rejected by exact assertions.
```

---

# 13. CSA obligations

The post-SDD / pre-MIR CSA must inspect and report:

```text
CSA-EIB-H-001 — exact current RestClient construction and feasible timeout factory patch;
CSA-EIB-H-002 — exact current EibScNorthboundClientProperties shape;
CSA-EIB-H-003 — exact current admissionResponse mapper location;
CSA-EIB-H-004 — exact current InteractionAdmissionDecision statuses;
CSA-EIB-H-005 — exact current cancellation list+match code path;
CSA-EIB-H-006 — exact current diagnostic/admin gating code path;
CSA-EIB-H-007 — exact current provider-native/canonical ID suppression tests;
CSA-EIB-H-008 — exact current route tests and mock mechanism;
CSA-EIB-H-009 — whether MockWebServer/WireMock test dependency should be added;
CSA-EIB-H-010 — exact MIR test class names and expected test count impact;
CSA-EIB-H-011 — retained debt table and closure/disposition requirements;
CSA-EIB-H-012 — whether any implementation package must patch documentation only outside eib/.
```

The CSA must also confirm that no SC-C production code changes are required.

---

# 14. MIR requirements

The MIR must include acceptance criteria at least equivalent to:

```text
AC-EIB-H-001 through AC-EIB-H-033 from the PDR;
plus operation-specific ACs for:
  - cancel -> HTTP 200 / COMPLETED when SC-C cancellation is terminal;
  - create -> HTTP 202 / ADMITTED only when canonical lifecycle continues;
  - non-success listTemporalActs propagation;
  - route regression for getTemporalAct as client-level active route;
  - implementation report debt table.
```

Candidate MIR:

```text
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
```

Candidate MU:

```text
MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
```

Candidate operational slot:

```text
MU-023
```

---

# 15. Risks and mitigations

## RISK-EIB-H-SDD-001 — Timeout tests become flaky

Mitigation:

```text
Prefer deterministic local mock server delay or request factory-level tests.
Keep timeout values conservative in tests.
Avoid remote/non-routable public IP dependencies.
```

## RISK-EIB-H-SDD-002 — Mapper drift through duplicated controller logic

Mitigation:

```text
Centralize admission-to-response mapping in one mapper.
Controllers delegate mapping.
Tests target the mapper and representative controller routes.
```

## RISK-EIB-H-SDD-003 — Product-facing scope creep

Mitigation:

```text
Keep changes limited to boundary reliability, status mapping, gating and route regression.
No View Composer, SApp, Surface or SC-B implementation.
```

## RISK-EIB-H-SDD-004 — Route fantasy recurrence

Mitigation:

```text
Positive emitted-request route tests.
Exact path/query assertions.
No mock-only route names not backed by MU-021.
```

---

# 16. Acceptance criteria for this SDD

```text
AC-SDD-EIB-H-001 — SDD consumes PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate.
AC-SDD-EIB-H-002 — SDD defines timeout enforcement design.
AC-SDD-EIB-H-003 — SDD defines admission response vocabulary and HTTP mapping.
AC-SDD-EIB-H-004 — SDD resolves synchronous cancellation as HTTP 200 / COMPLETED.
AC-SDD-EIB-H-004a — SDD explicitly chooses Option A: SC-C `CANCELLED` maps context-free to `COMPLETED`, not `ADMITTED`.
AC-SDD-EIB-H-005 — SDD preserves HTTP 202 only for true later responsibility / canonical lifecycle continuation.
AC-SDD-EIB-H-006 — SDD requires DEFERRED_UNSUPPORTED_PROFILE mapping to HTTP 501 / UNSUPPORTED.
AC-SDD-EIB-H-007 — SDD requires DEBT-EIB-015 closure by propagating non-success listTemporalActs envelopes.
AC-SDD-EIB-H-008 — SDD defines diagnostic/admin truth-table testing.
AC-SDD-EIB-H-009 — SDD defines positive upstream route regression tests.
AC-SDD-EIB-H-010 — SDD resolves getTemporalAct as active client route but list+match production effective-ref resolution remains.
AC-SDD-EIB-H-011 — SDD requires post-SDD / pre-MIR CSA.
AC-SDD-EIB-H-012 — SDD remains non-authorizing for implementation.
```

---

# 17. Final dictum

```text
SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate
is accepted as candidate for post-SDD / pre-MIR CSA.
```

This SDD defines a narrow EIB hardening design before SC-B runtime descent.

It does not implement SC-B, View Composer, SApp, Surface APIs or product-facing UX.
