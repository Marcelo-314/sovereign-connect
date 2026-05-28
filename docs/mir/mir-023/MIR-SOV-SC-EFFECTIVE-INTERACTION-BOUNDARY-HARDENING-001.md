# MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001

## Materialization Increment Record — EIB Hardening

```text
Document ID:  MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
Title:        Materialization Increment Record — EIB Hardening
Version:      v0.2.0-candidate
Status:       Candidate / PDR candidate consumed / SDD candidate consumed / CSA-approved / execution package enabled
Date:         2026-05-28
Corpus:       Sovereign Connect
Type:         MIR
Plane:        SC-X / EIB
MU ID:        MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
MU Slot:      MU-023
```

---

## 0. MIR boundary

This MIR authorizes an implementation attempt for the EIB hardening increment following MU-022.

It does not include an execution prompt, implementation context, acceptance map or Codex instructions. Those artifacts must be produced separately under the execution package if this MIR is promoted to candidate.

This MIR is not product-facing construction. It is not View Composer. It is not SApp. It is not SC-B runtime implementation. It is not SC-D adapter implementation.

---

## 1. Purpose

MU-022 validated the initial EIB implementation slice as a separate runtime consuming SC-C only through the validated Northbound HTTP/OpenAPI binding.

This MIR hardens that boundary before downstream SC-B integration and before any View Composer / SApp / Surface-facing descent depends on EIB.

The hardening closes three concrete fragility classes found after MU-022:

```text
1. Operational boundary fragility:
   sc.eib.northbound.timeout-ms exists but is not enforced by RestClient wiring.

2. Admission response fragility:
   EIB admission responses currently collapse most decision outcomes into
   top-level HTTP 202 / EibResponse.status = ACCEPTED.

3. Diagnostic/admin gating fragility:
   diagnostic/admin exposure is logically conjunctive, but the full truth table
   is not protected by tests.
```

The increment also adds route-boundary regression coverage for upstream SC-C HTTP routes and closes the known cancellation list+match semantic collapse debt.

---

## 2. Depends on

```text
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate
SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate
CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.1.1-merged
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001 v1.0.0-accepted
MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001 v1.0.0-accepted
```

---

## 3. Related

```text
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.3.0-candidate
SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.2.0-candidate
CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-merged
REVIEW-MU-022-EFFECTIVE-INTERACTION-BOUNDARY-SEED v1.0.0
REVIEW-MU-022-ROUTING-FIX-PATCH-HALLAZGOS
review-MU-022-routing-fix-patch-implementation
review-MU-022-failure-boundary-patch-2-implementation
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.16-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.22-draft
SYNC-SOV-SC-MU-BACKLOG-001 v0.1.23-draft
```

---

## 4. Baseline state

Baseline: post-MU-022 EIB initial implementation slice after routing fix and failure-boundary patch.

Recorded validation baseline:

```text
EIB:  33 tests, 0 failures, 0 errors, 0 skipped
SC-C: 240 tests, 0 failures, 0 errors, 0 skipped
```

Baseline properties to preserve:

```text
- EIB remains a separate runtime under /eib.
- EIB does not share the SC-C runtime application context.
- EIB does not import SC-C internals.
- EIB consumes SC-C only through HTTP/OpenAPI.
- SC-C production code remains untouched by this hardening.
- EIB-owned DTOs remain separate from SC-C Java DTOs.
- providerDeviceId and providerEndpointId remain suppressed from ordinary product views.
- canonical IDs remain diagnostic/admin only.
- production getEffectiveTemporalAct remains list+match for this increment.
```

---

## 5. Problem statement

MU-022 is valid as an initial implementation slice, but not sufficiently hardened for the next integration wave.

The most important current defects are:

```text
DEF-EIB-H-001 — timeout-ms configured but not enforced.
DEF-EIB-H-002 — admissionResponse() maps all non-upstream-failure cases to HTTP 202 / ACCEPTED.
DEF-EIB-H-003 — SC-C CANCELLED maps to EIB ADMITTED instead of COMPLETED.
DEF-EIB-H-004 — DEFERRED_UNSUPPORTED_PROFILE lacks proper HTTP/EibResponse mapping.
DEF-EIB-H-005 — cancellation may collapse non-success listTemporalActs envelopes to NOT_VISIBLE.
DEF-EIB-H-006 — diagnostic/admin gating lacks full truth-table regression.
DEF-EIB-H-007 — route regression does not cover all required upstream routes.
```

---

## 6. Goals

### G-MIR-023-001 — Timeout enforcement

Apply `sc.eib.northbound.timeout-ms` to EIB upstream HTTP calls.

Timeout failures must normalize to `UPSTREAM_UNAVAILABLE` at the EIB boundary.

### G-MIR-023-002 — Admission response vocabulary hardening

Replace the generic top-level `ACCEPTED` wrapper behavior with an explicit admission decision mapper.

Top-level `EibResponse.status` must describe the EIB admission result, not merely the fact that EIB produced a response.

### G-MIR-023-003 — Synchronous terminal cancellation semantics

Map SC-C `CANCELLED` to EIB `COMPLETED`, not `ADMITTED`.

A synchronous terminal cancellation result should return HTTP 200 / `EibResponse.status = COMPLETED`.

### G-MIR-023-004 — Unsupported profile disposition

Map `DEFERRED_UNSUPPORTED_PROFILE` to HTTP 501 / `EibResponse.status = UNSUPPORTED`.

### G-MIR-023-005 — Cancellation non-success propagation

Before effective-ref resolution in cancellation, inspect the `listTemporalActs` envelope.

If that envelope is non-success, propagate the semantic status instead of collapsing it to `REJECTED_NOT_VISIBLE`.

### G-MIR-023-006 — Diagnostic/admin gating hardening

Add truth-table coverage proving diagnostic/admin mode requires both:

```text
X-EIB-Diagnostic = true
and
configuration admin/diagnostic enabled = true
```

### G-MIR-023-007 — Upstream route regression completion

Add route regression coverage for all required upstream SC-C HTTP routes used by EIB.

### G-MIR-023-008 — Debt disposition

Close `DEBT-EIB-014` and `DEBT-EIB-015` only if implementation and tests pass.

Preserve all other retained EIB debts with updated classifications.

---

## 7. Non-goals

This MIR does not authorize:

```text
- View Composer implementation.
- SApp implementation.
- Surface-facing product API.
- SC-B runtime dispatch.
- SC-D adapter runtime.
- Action admission over SC-B.
- Discovery admission over SC-B.
- Persistent effective-ref registry.
- Durable EIB admission ledger.
- Authority/Policy/Identity/Session real integration.
- EIB TCK/conformance harness.
- gRPC/ConnectRPC/MCP/GraphQL/WebSocket/SSE implementation.
- Any SC-C production code change.
```

---

## 8. Mandatory implementation actions

The post-SDD CSA merged audit authorizes the MIR with six mandatory implementation actions.

```text
ACTION-EIB-H-001 — Apply timeout factory to RestClient.Builder before build().
ACTION-EIB-H-002 — Complete positive route regression coverage for missing upstream routes.
ACTION-EIB-H-003 — Update mapScStatusToAdmissionStatus: CANCELLED -> COMPLETED.
ACTION-EIB-H-004 — Replace admissionResponse() with full admission vocabulary mapper.
ACTION-EIB-H-005 — Add isSuccess() guard in admitTemporalCancellation before effective-ref resolution.
ACTION-EIB-H-006 — Add complete diagnostic/admin truth-table tests.
```

These actions are mandatory for L4 validation of MU-023.

---

## 9. Design decisions for this MIR

### D-MIR-023-001 — EIB runtime separation preserved

EIB must remain a separate runtime project under `/eib` or an equivalent separate runtime boundary.

Same repository is allowed. Same SC-C runtime application context is not allowed.

### D-MIR-023-002 — SC-C remains untouched

This hardening is scoped to EIB implementation and `docs/mir/mir-023/` evidence.

No SC-C production code may be modified.

### D-MIR-023-003 — Timeout factory applied to builder

`SimpleClientHttpRequestFactory` or an equivalent Spring-supported `ClientHttpRequestFactory` must be configured with connect/read timeout from `sc.eib.northbound.timeout-ms` and applied to the EIB `RestClient.Builder` before `RestClient` construction.

The `RestClient.Builder` bean must be preserved.

### D-MIR-023-004 — No new HTTP test dependency required

`MockRestServiceServer` remains sufficient for route regression.

No MockWebServer/WireMock dependency is required unless execution discovers a concrete incompatibility and records a justified deviation in the implementation report.

### D-MIR-023-005 — HTTP 202 is not a generic success wrapper

EIB must not use HTTP 202 merely because it produced a response.

HTTP 202 is reserved for cases where EIB has genuinely accepted responsibility for later processing.

### D-MIR-023-006 — Admission mapper is explicit

Admission status mapping should be centralized in a mapper such as `EibHttpResponseMapper` or an equivalent focused component.

Controller logic should not retain ad hoc two-branch admission mapping.

### D-MIR-023-007 — CANCELLED is terminal

SC-C `CANCELLED` must map to EIB admission status `COMPLETED`.

It must not map to `ADMITTED`.

### D-MIR-023-008 — Full admission vocabulary

The implementation must support the following admission outcomes:

```text
ADMITTED
COMPLETED
REJECTED_NOT_VISIBLE
REJECTED_INVALID_REQUEST
DEFERRED_SC_B_REQUIRED
DEFERRED_PENDING_NORMALIZATION
DEFERRED_UNSUPPORTED_PROFILE
FAILED_UPSTREAM_UNAVAILABLE
FAILED_CANONICAL_SUBMISSION
```

### D-MIR-023-009 — Required HTTP/EibResponse mapping

Required mapping:

| Decision status | HTTP | EibResponse.status |
|---|---:|---|
| `ADMITTED` | 202 | `ADMITTED` |
| `COMPLETED` | 200 | `COMPLETED` |
| `REJECTED_NOT_VISIBLE` | 404 | `NOT_VISIBLE` |
| `REJECTED_INVALID_REQUEST` | 400 | `INVALID_REQUEST` |
| `DEFERRED_SC_B_REQUIRED` | 503 | `DEFERRED` |
| `DEFERRED_PENDING_NORMALIZATION` | 409 | `PENDING_NORMALIZATION` |
| `DEFERRED_UNSUPPORTED_PROFILE` | 501 | `UNSUPPORTED` |
| `FAILED_UPSTREAM_UNAVAILABLE` | 503 | `UPSTREAM_UNAVAILABLE` |
| `FAILED_CANONICAL_SUBMISSION` | 500 | `FAILED` |

### D-MIR-023-010 — Cancellation envelope check precedes ref resolution

In cancellation, `listTemporalActs` must be checked for semantic success before effective-ref resolution.

If the envelope is non-success, its semantic status must be mapped to an admission status and returned without calling `cancelTemporalAct`.

### D-MIR-023-011 — Diagnostic/admin mode requires conjunction

Diagnostic/admin exposure requires both diagnostic request intent and configuration authorization.

Required truth table:

| Diagnostic requested | Admin enabled | Result |
|---|---|---|
| false | false | ordinary |
| true | false | ordinary |
| false | true | ordinary |
| true | true | diagnostic/admin |

### D-MIR-023-012 — Route regression is positive

The implementation must assert the positive upstream route paths actually emitted by EIB.

Negative absence checks alone are insufficient.

### D-MIR-023-013 — getTemporalAct remains client-active

`getTemporalAct` must remain active at client route-test level.

Production `getEffectiveTemporalAct` may remain list+match in this hardening increment.

### D-MIR-023-014 — Debt closure is evidence-gated

`DEBT-EIB-014` and `DEBT-EIB-015` may be marked closed only after implementation evidence and tests pass.

---

## 10. Expected implementation surface

Expected production code surface:

```text
eib/src/main/java/com/sovereign/eib/config/EibConfiguration.java
eib/src/main/java/com/sovereign/eib/api/EibTemporalActController.java
eib/src/main/java/com/sovereign/eib/api/EibHttpResponseMapper.java        [new or equivalent]
eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java
```

Expected test surface:

```text
eib/src/test/java/com/sovereign/eib/EibScNorthboundClientTest.java
eib/src/test/java/com/sovereign/eib/EibScNorthboundClientTimeoutTest.java       [new or equivalent]
eib/src/test/java/com/sovereign/eib/EibAdmissionResponseMapperTest.java         [new or equivalent]
eib/src/test/java/com/sovereign/eib/EibTemporalAdmissionServiceHardeningTest.java [new or equivalent]
eib/src/test/java/com/sovereign/eib/EibEffectiveViewMapperTest.java
eib/src/test/java/com/sovereign/eib/EibApiControllerTest.java                   [or controller-focused equivalent]
```

Names may vary only if the implementation report maps the actual classes to the acceptance criteria.

---

## 11. Required upstream route regression coverage

The route regression suite must positively verify the emitted request path for at least:

```text
GET  /habitats/{habitatId}/topology
GET  /habitats/{habitatId}/topology/version
GET  /habitats/{habitatId}/devices/{deviceId}/health
GET  /habitats/{habitatId}/endpoints/{endpointId}/health
GET  /habitats/{habitatId}/devices/{deviceId}/runtime-state
GET  /habitats/{habitatId}/endpoints/{endpointId}/runtime-state
GET  /habitats/{habitatId}/diagnostics
GET  /habitats/{habitatId}/temporal-acts?mode=ACTIVE&maxResults=50
GET  /habitats/{habitatId}/temporal-acts/{temporalActId}
POST /habitats/{habitatId}/temporal-acts
POST /habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
```

The suite must not reintroduce routes previously rejected in MU-022 patches:

```text
/topology/snapshot
/runtime-state/{subjectId}
?status=
?limit=
/temporal-acts/signal as upstream SC-C route
```

---

## 12. Acceptance criteria

### 12.1 Runtime and boundary

```text
AC-023-001 — EIB remains a separate runtime boundary and does not share SC-C application context.
AC-023-002 — No SC-C production code is modified by the implementation.
AC-023-003 — EIB does not import SC-C internals or ScCoreNorthboundFacade.
AC-023-004 — EIB continues to consume SC-C only through HTTP/OpenAPI.
```

### 12.2 Timeout enforcement

```text
AC-023-005 — eibRestClientBuilder applies a ClientHttpRequestFactory with connect timeout from sc.eib.northbound.timeout-ms.
AC-023-006 — eibRestClientBuilder applies a ClientHttpRequestFactory with read timeout from sc.eib.northbound.timeout-ms.
AC-023-007 — The RestClient.Builder bean is preserved and timeout factory is bound before RestClient construction.
AC-023-008 — A client-level test verifies that a connect or read timeout produces EibUpstreamUnavailableException.
AC-023-008a — An API-level test verifies that EibUpstreamUnavailableException is normalized to EibResponse.status = UPSTREAM_UNAVAILABLE and HTTP 503 at the controller boundary.
```

### 12.3 Admission status mapping

```text
AC-023-009 — admissionResponse() generic fallback to HTTP 202 / ACCEPTED is removed.
AC-023-010 — A full admission response mapper exists or equivalent controller logic covers all required statuses.
AC-023-011 — ADMITTED maps to HTTP 202 and EibResponse.status = ADMITTED.
AC-023-012 — COMPLETED maps to HTTP 200 and EibResponse.status = COMPLETED.
AC-023-013 — REJECTED_NOT_VISIBLE maps to HTTP 404 and EibResponse.status = NOT_VISIBLE.
AC-023-014 — REJECTED_INVALID_REQUEST maps to HTTP 400 and EibResponse.status = INVALID_REQUEST.
AC-023-015 — DEFERRED_SC_B_REQUIRED maps to HTTP 503 and EibResponse.status = DEFERRED.
AC-023-016 — DEFERRED_PENDING_NORMALIZATION maps to HTTP 409 and EibResponse.status = PENDING_NORMALIZATION.
AC-023-017 — DEFERRED_UNSUPPORTED_PROFILE maps to HTTP 501 and EibResponse.status = UNSUPPORTED.
AC-023-018 — FAILED_UPSTREAM_UNAVAILABLE maps to HTTP 503 and EibResponse.status = UPSTREAM_UNAVAILABLE.
AC-023-019 — FAILED_CANONICAL_SUBMISSION maps to HTTP 500 and EibResponse.status = FAILED.

Implementation note: the execution package SHOULD require mapper tests to include SC-C INTERNAL_ERROR as an input that reaches the FAILED_CANONICAL_SUBMISSION default path and returns HTTP 500 / EibResponse.status = FAILED.
```

### 12.4 CANCELLED / cancellation semantics

```text
AC-023-020 — mapScStatusToAdmissionStatus maps SC-C CANCELLED to EIB COMPLETED.
AC-023-021 — SC-C CANCELLED no longer maps to ADMITTED.
AC-023-022 — Cancel route returns top-level COMPLETED/HTTP 200 when the cancellation is synchronously terminal.
```

### 12.5 Cancellation non-success list envelope propagation

```text
AC-023-023 — admitTemporalCancellation checks listTemporalActs envelope success before effective-ref resolution.
AC-023-024 — listTemporalActs DEFERRED_SC_B_REQUIRED is propagated and does not collapse to REJECTED_NOT_VISIBLE.
AC-023-025 — listTemporalActs UNKNOWN_PENDING_NORMALIZATION is propagated as DEFERRED_PENDING_NORMALIZATION and does not collapse to REJECTED_NOT_VISIBLE.
AC-023-026 — listTemporalActs UNSUPPORTED_PROFILE is propagated as DEFERRED_UNSUPPORTED_PROFILE and does not collapse to REJECTED_NOT_VISIBLE.
AC-023-027 — When listTemporalActs is non-success, EIB does not call cancelTemporalAct.
```

### 12.6 Diagnostic/admin gating

```text
AC-023-028 — diagnosticRequested=false and adminEnabled=false yields ordinary mode.
AC-023-029 — diagnosticRequested=true and adminEnabled=false yields ordinary mode.
AC-023-030 — diagnosticRequested=false and adminEnabled=true yields ordinary mode.
AC-023-031 — diagnosticRequested=true and adminEnabled=true yields diagnostic/admin mode.
AC-023-032 — Provider-native IDs remain absent from ordinary and diagnostic/admin product views.
AC-023-033 — Canonical IDs remain absent from ordinary views.
AC-023-034 — Canonical IDs may appear only in diagnostic/admin mode.
```

### 12.7 Route regression

```text
AC-023-035 — Route regression positively verifies GET /topology.
AC-023-036 — Route regression positively verifies GET /topology/version.
AC-023-037 — Route regression positively verifies GET /devices/{deviceId}/health.
AC-023-038 — Route regression positively verifies GET /endpoints/{endpointId}/health.
AC-023-039 — Route regression positively verifies GET /devices/{deviceId}/runtime-state.
AC-023-040 — Route regression positively verifies GET /endpoints/{endpointId}/runtime-state.
AC-023-041 — Route regression positively verifies GET /diagnostics.
AC-023-042 — Route regression positively verifies GET /temporal-acts?mode=ACTIVE&maxResults=50.
AC-023-043 — Route regression positively verifies GET /temporal-acts/{temporalActId}.
AC-023-044 — Route regression positively verifies POST /temporal-acts.
AC-023-045 — Route regression positively verifies POST /temporal-acts/{temporalActId}/cancel.
AC-023-046 — Regression verifies rejected upstream routes do not reappear.
```

### 12.8 Evidence and reporting

```text
AC-023-047 — Implementation report records final EIB test count and result.
AC-023-048 — Implementation report records SC-C baseline test count and result or explicitly states SC-C tests were not rerun with rationale.
AC-023-049 — Implementation report records closure of DEBT-EIB-014 if and only if timeout tests pass.
AC-023-050 — Implementation report records closure of DEBT-EIB-015 if and only if cancellation non-success propagation tests pass.
AC-023-051 — Implementation report records retained debts with downstream classification.
AC-023-052 — Implementation report records implementation commit and evidence commit.
AC-023-053 — Acceptance map links every AC-023 criterion to tests, source files or implementation report evidence.
```

---

## 13. Expected validation

Minimum expected validation after implementation:

```text
EIB tests: existing 33 + hardening tests.
Expected EIB total: >= 56 tests.
Expected result: 0 failures, 0 errors, 0 skipped.
```

SC-C baseline should remain green. If SC-C tests are not rerun because only EIB code changed, the implementation report must state this explicitly.

---

## 14. Retained debt disposition

### Closed by this MIR if validated

```text
DEBT-EIB-014 — Timeout configured but not enforced.
  Closure condition: timeout factory is applied and timeout tests pass.

DEBT-EIB-015 — Cancellation effective-ref resolution may collapse non-success list envelopes to NOT_VISIBLE.
  Closure condition: non-success listTemporalActs envelopes are propagated before resolution and tests pass.
```

### Retained after this MIR

```text
DEBT-EIB-003 — Authority/Policy/Identity/Session real integration absent.
  Classification: requires-resolution-before-SApp-Surface.

DEBT-EIB-004 — Device/endpoint action admission remains SC-B-gated.
  Classification: requires-resolution-before-EIB-action-admission.

DEBT-EIB-005 — Discovery admission remains SC-B-gated.
  Classification: requires-resolution-before-EIB-action-admission.

DEBT-EIB-006 — Live updates deferred.
  Classification: requires-resolution-before-View-Composer.

DEBT-EIB-010 — EIB conformance/TCK absent.
  Classification: requires-resolution-before-industrial.

DEBT-EIB-011 — Durable audit/admission ledger absent.
  Classification: requires-resolution-before-industrial.

DEBT-EIB-012 — Persistent effective-ref registry absent.
  Classification: accepted-for-now.

DEBT-EIB-013 — Auth/authz stubbed/config-gated.
  Classification: requires-resolution-before-SApp-Surface.
```

---

## 15. Risks

### RISK-MIR-023-001 — Over-hardening into product-facing scope

Mitigation: This MIR explicitly excludes View Composer, SApp, Surfaces, Authority/Policy implementation and product-facing API.

### RISK-MIR-023-002 — Mapper vocabulary drift

Mitigation: Admission mapper tests must cover every required status and HTTP code.

### RISK-MIR-023-003 — Timeout test nondeterminism

Mitigation: Use deterministic local/factory-level timeout test strategy accepted by CSA. Avoid slow external network dependencies.

### RISK-MIR-023-004 — Diagnostic/admin leakage

Mitigation: Truth-table tests protect the conjunctive rule and provider-native IDs remain structurally suppressed.

### RISK-MIR-023-005 — Reopening route drift

Mitigation: Positive route regression covers all required SC-C upstream routes and negative checks prevent rejected route shapes from returning.

---

## 16. MIR acceptance checklist

This MIR may be promoted to candidate when:

```text
- PDR v0.2.0-candidate is accepted for hardening descent.
- SDD v0.2.0-candidate is accepted for hardening descent.
- CSA v0.1.1-merged is accepted.
- The six ACTION-EIB-H items are reflected in ACs.
- No context/prompt is embedded in the MIR.
- Branch and commit suggestions are present.
```

---

## 17. Suggested branch and commit

Suggested branch:

```text
feat/sc-eib-mir-023-effective-interaction-boundary-hardening
```

Suggested implementation commit:

```text
fix(eib): harden interaction boundary responses
```

Alternative commit if execution includes timeout and mapper work in one commit:

```text
fix(eib): harden northbound timeout and admission responses
```

---

## 18. Final dictum

```text
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.1.0-draft
is opened for review.

It is not yet candidate.
It does not authorize implementation until promoted and accompanied by an execution package.
```
