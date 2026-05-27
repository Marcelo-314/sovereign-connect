# PATCH-SOV-SC-EIB-MIR-022-FAILURE-BOUNDARY-001

```text
Document ID:  PATCH-SOV-SC-EIB-MIR-022-FAILURE-BOUNDARY-001
Title:        MU-022 Failure-Boundary Patch
Version:      v0.1.0
Status:       Patch execution package / implementation-authorizing for bounded correction
Date:         2026-05-27
Corpus:       Sovereign Connect
Plane:        SC-X / EIB
Scope:        Patch remaining failure-boundary blockers after MU-022 routing fix
Parent MU:    MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
Slot:         MU-022
```

## 1. Purpose

This patch closes the remaining execution blockers detected after the MU-022 routing-fix patch.

The routing-fix patch correctly aligned EIB upstream calls with the validated SC-C MU-021 HTTP/OpenAPI binding. However, the EIB API/service layer still has incomplete failure-boundary behavior:

```text
- EibUpstreamUnavailableException can escape from temporal admission.
- EibUpstreamUnavailableException can escape from temporal projection list/get.
- single device/endpoint routes can degrade parent UPSTREAM_UNAVAILABLE into NOT_FOUND.
- diagnostics can leak upstream transport failure as framework error.
```

This patch requires that affected `/eib/v1` routes return `EibResponse<T>` for upstream failures rather than Spring framework errors or false `NOT_FOUND` results.

## 2. Non-goals

This patch MUST NOT:

```text
- change PDR/SDD/MIR semantics;
- alter non-co-located EIB placement;
- move EIB into the SC-C runtime;
- add SC-C Java imports to EIB;
- introduce WebFlux, GraphQL, gRPC, MCP, SSE or SC-B runtime;
- implement Authority/Policy/Identity/Session;
- implement persistent effective-ref registry;
- implement full EIB product readiness;
- close DEBT-EIB-* or DEBT-HTTP-* beyond the specific blocker named here.
```

## 3. Required patch decisions

### PATCH-D-001 — Upstream unavailable is an EIB response status

When the EIB upstream HTTP client cannot obtain a valid `ScEnvelope<T>` from SC-C, the EIB layer MUST expose:

```text
EibResponse.status = UPSTREAM_UNAVAILABLE
EibResponse.payload = null for read/projection routes
EibResponse.error.code = UPSTREAM_UNAVAILABLE
EibResponse.error.source = eib.northbound
```

It MUST NOT expose a raw Spring 500 and MUST NOT convert the condition to `NOT_FOUND`.

### PATCH-D-002 — Semantic SC-C failures remain semantic

A valid `ScEnvelope<T>` returned by SC-C, even when carried over HTTP 503, remains a semantic SC-C response. Example:

```text
HTTP 503 + ScEnvelope(status=DEFERRED_SC_B_REQUIRED)
```

MUST be handled as SC-C semantic status, not as `UPSTREAM_UNAVAILABLE`.

### PATCH-D-003 — Admission failure keeps admission structure

Temporal admission routes MUST continue to return `EibResponse<InteractionAdmissionDecision>`.

If the upstream failure occurs while submitting to SC-C, the decision MUST indicate failure without fabricating canonical success:

```text
InteractionAdmissionDecision.status = FAILED_UPSTREAM_UNAVAILABLE
InteractionAdmissionDecision.effectiveRef = null
CanonicalSubmissionTrace.scNorthboundStatus = UPSTREAM_UNAVAILABLE
EibError.source = eib.northbound
```

The outer response SHOULD be:

```text
EibResponse.status = UPSTREAM_UNAVAILABLE
EibResponse.payload = InteractionAdmissionDecision
EibResponse.error = same upstream error or equivalent
```

HTTP status MAY be `503 Service Unavailable`; the body MUST be `EibResponse<InteractionAdmissionDecision>`.

### PATCH-D-004 — Parent response propagation in single entity routes

Routes implemented by deriving from a parent list/effective view MUST propagate non-OK parent responses.

Examples:

```text
GET /eib/v1/habitats/{habitatId}/devices/{effectiveDeviceRef}
GET /eib/v1/habitats/{habitatId}/endpoints/{effectiveEndpointRef}
```

If the parent `devices(...)` / `endpoints(...)` response has status `UPSTREAM_UNAVAILABLE`, the single-entity route MUST return `UPSTREAM_UNAVAILABLE`, not `NOT_FOUND`.

## 4. Files expected to change

Expected production files:

```text
eib/src/main/java/com/sovereign/eib/api/EibHabitatController.java
eib/src/main/java/com/sovereign/eib/api/EibTemporalActController.java
eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java
eib/src/main/java/com/sovereign/eib/service/EibTemporalActProjectionService.java
```

Optional helper file, if it reduces duplication:

```text
eib/src/main/java/com/sovereign/eib/service/EibFailureResponses.java
```

Expected test files to add or modify:

```text
eib/src/test/java/com/sovereign/eib/EibTemporalAdmissionServiceTest.java
eib/src/test/java/com/sovereign/eib/EibTemporalActProjectionServiceTest.java
eib/src/test/java/com/sovereign/eib/EibApiControllerTest.java
eib/src/test/java/com/sovereign/eib/EibHabitatControllerFailureBoundaryTest.java
```

The exact class names may differ if the same coverage is demonstrably present.

## 5. Acceptance criteria

```text
AC-PATCH-022-FB-001 — Signal temporal admission catches EibUpstreamUnavailableException and returns InteractionAdmissionDecision.status = FAILED_UPSTREAM_UNAVAILABLE.
AC-PATCH-022-FB-002 — Signal temporal admission trace has scNorthboundStatus = UPSTREAM_UNAVAILABLE.
AC-PATCH-022-FB-003 — Signal temporal admission controller returns EibResponse<InteractionAdmissionDecision>, not a framework 500, when SC-C is unavailable.
AC-PATCH-022-FB-004 — Cancel temporal admission catches upstream unavailable during list+match and returns FAILED_UPSTREAM_UNAVAILABLE or EibResponse.status = UPSTREAM_UNAVAILABLE, not REJECTED_NOT_VISIBLE.
AC-PATCH-022-FB-005 — Cancel temporal admission catches upstream unavailable during submit/cancel and returns EibResponse<InteractionAdmissionDecision> with upstream failure status.
AC-PATCH-022-FB-006 — listEffectiveTemporalActs returns EibResponse.status = UPSTREAM_UNAVAILABLE when listTemporalActs throws EibUpstreamUnavailableException.
AC-PATCH-022-FB-007 — getEffectiveTemporalAct returns EibResponse.status = UPSTREAM_UNAVAILABLE when listTemporalActs throws EibUpstreamUnavailableException.
AC-PATCH-022-FB-008 — GET /devices/{effectiveDeviceRef} propagates parent UPSTREAM_UNAVAILABLE instead of returning NOT_FOUND.
AC-PATCH-022-FB-009 — GET /endpoints/{effectiveEndpointRef} propagates parent UPSTREAM_UNAVAILABLE instead of returning NOT_FOUND.
AC-PATCH-022-FB-010 — GET /diagnostics returns EibResponse.status = UPSTREAM_UNAVAILABLE when getDiagnostics throws EibUpstreamUnavailableException.
AC-PATCH-022-FB-011 — Existing routing-fix tests still validate real MU-021 upstream routes.
AC-PATCH-022-FB-012 — Existing semantic 503 test still proves HTTP 503 + valid ScEnvelope(DEFERRED_SC_B_REQUIRED) is not UPSTREAM_UNAVAILABLE.
AC-PATCH-022-FB-013 — Existing malformed JSON / invalid envelope test still proves transport failure maps to upstream unavailable.
AC-PATCH-022-FB-014 — EIB architecture boundary remains intact: no com.sovereign.connect imports in eib/src/main.
AC-PATCH-022-FB-015 — EIB test suite passes.
AC-PATCH-022-FB-016 — SC-C baseline test suite remains green.
AC-PATCH-022-FB-017 — implementation-report records patch commit, tests run, failures/errors/skips and remaining debt.
```

## 6. Re-review condition

After the patch, MU-022 may be re-reviewed for L4 only if:

```text
- all blocker cases above are covered by tests;
- EIB suite is green;
- SC-C baseline is green;
- worktree is clean or the diff is explicitly bounded to MU-022 patch files;
- implementation-report has patch evidence.
```
