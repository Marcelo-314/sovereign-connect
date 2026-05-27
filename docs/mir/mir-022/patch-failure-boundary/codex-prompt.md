# Codex Prompt — MU-022 Failure-Boundary Patch

```text
Version: v0.2.1-reviewed
```

You are working in the Sovereign Connect repository on branch:

```text
feat/sc-eib-mir-022-effective-interaction-boundary-seed
```

Apply a **bounded failure-boundary patch** for MU-022.

Read first:

```text
docs/mir/mir-022/patch-failure-boundary/patch-context.md    ← primary guide
docs/mir/mir-022/patch-failure-boundary/acceptance-map-patch.md
docs/mir/mir-022/patch-failure-boundary/failure-boundary-contract.md
```

---

## Goal

The routing-fix patch already aligned EIB upstream HTTP routes with SC-C MU-021.
Do not rework that patch.

Your task is to ensure that `EibUpstreamUnavailableException` never escapes as a
framework error and is never silently degraded to `NOT_FOUND`.

```text
RULE: EibUpstreamUnavailableException MUST be caught at the service/controller
      boundary and converted to EibResponse<T> with status = UPSTREAM_UNAVAILABLE.
      It MUST NOT propagate as an unhandled exception.
      It MUST NOT be converted to NOT_FOUND.
```

---

## Files to change (exact list)

```text
MODIFY:
  eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java
  eib/src/main/java/com/sovereign/eib/service/EibTemporalActProjectionService.java
  eib/src/main/java/com/sovereign/eib/api/EibHabitatController.java
  eib/src/main/java/com/sovereign/eib/api/EibTemporalActController.java

MODIFY (add tests only — do NOT remove existing tests):
  eib/src/test/java/com/sovereign/eib/EibTemporalAdmissionServiceTest.java
  eib/src/test/java/com/sovereign/eib/EibApiControllerTest.java

CREATE:
  eib/src/test/java/com/sovereign/eib/EibTemporalActProjectionServiceTest.java
  eib/src/test/java/com/sovereign/eib/EibHabitatControllerFailureBoundaryTest.java

UPDATE:
  docs/mir/mir-022/implementation-report.md
```

Do NOT modify:
```text
SC-C production code or tests
EIB northbound routes corrected by routing-fix patch
RestClientEibScNorthboundClient route paths
EIB architecture boundary checks
EIB provider-native ID suppression
EIB HMAC ref behavior
EibEffectiveViewService temporal-act degraded-warning behavior
```

---

## Task 1 — Patch EibTemporalAdmissionService

Use the **exact code** from `patch-context.md §4.1`. Add the
`EibUpstreamUnavailableException` import if needed.

The change has two parts:

**Part A — admitTemporalSignalRequest:** wrap `client.createSignalTemporalAct(...)`
in try/catch. On `EibUpstreamUnavailableException` return:

```java
new InteractionAdmissionDecision(
    admissionId, "FAILED_UPSTREAM_UNAVAILABLE", null,
    new CanonicalSubmissionTrace(
        ctx.clientRequestRef(), admissionId, "UPSTREAM_UNAVAILABLE", error, List.of()),
    List.of(), error)
```

where `error = new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound")`.

**Part B — admitTemporalCancellation:** wrap the list call and the cancel call
separately. On either throwing, return the same `FAILED_UPSTREAM_UNAVAILABLE`
decision. The list+match `resolveTemporalRef` returns `REJECTED_NOT_VISIBLE` only
when the list succeeds but the ref is absent — not on upstream failure.

The record constructor field order is confirmed:
```java
// InteractionAdmissionDecision(admissionId, status, effectiveRef, canonicalTrace, warnings, error)
// CanonicalSubmissionTrace(clientRequestRef, admissionId, scNorthboundStatus, error, warnings)
```

---

## Task 2 — Patch EibTemporalActProjectionService

Use the **exact code** from `patch-context.md §4.2`. Add the
`EibUpstreamUnavailableException` import if needed.

Both `listEffectiveTemporalActs` and `getEffectiveTemporalAct` must:
1. Declare `ScEnvelope<...> envelope` before the try block.
2. Catch `EibUpstreamUnavailableException` and return:
   - list route: `new EibResponse<>("UPSTREAM_UNAVAILABLE", List.of(), List.of(), error, null)`
   - get route: `new EibResponse<>("UPSTREAM_UNAVAILABLE", null, List.of(), error, null)`

The `NOT_FOUND` path in `getEffectiveTemporalAct` must remain — it is valid when
the list succeeds but the ref is absent.

---

## Task 3 — Patch EibHabitatController

Use the **exact code** from `patch-context.md §4.3`. Add the
`EibUpstreamUnavailableException` import if needed.

**Part A — single device/endpoint routes:** Add a status check before the
stream filter. If `devices.status()` or `endpoints.status()` is not `"OK"`,
return the parent response with null payload. Do not filter and do not
return `NOT_FOUND`.

**Part B — diagnostics:** Wrap `client.getDiagnostics(habitatId)` in try/catch.
On `EibUpstreamUnavailableException` return:
```java
new EibResponse<>("UPSTREAM_UNAVAILABLE", null, List.of(), error, null)
```

---

## Task 4 — Patch EibTemporalActController

Use the **exact code** from `patch-context.md §4.4`.

Add the private `admissionResponse(InteractionAdmissionDecision)` helper and
replace the inline `ResponseEntity.status(HttpStatus.ACCEPTED).body(...)` in
both `signal(...)` and `cancel(...)` with a call to `admissionResponse(decision)`.

The helper returns HTTP 503 with `EibResponse.status = UPSTREAM_UNAVAILABLE`
when `decision.status().equals("FAILED_UPSTREAM_UNAVAILABLE")`.

---

## Task 5 — Add tests

### In EibTemporalAdmissionServiceTest — add 3 methods

Use the **exact code** from `patch-context.md §5.1`. Add to the existing class.
The existing 2 tests must be preserved. The 3 new tests verify:

```text
- createSignalTemporalAct throws -> FAILED_UPSTREAM_UNAVAILABLE (not ADMITTED)
- listTemporalActs throws in cancel -> FAILED_UPSTREAM_UNAVAILABLE (not REJECTED_NOT_VISIBLE)
- cancelTemporalAct throws after successful list+match -> FAILED_UPSTREAM_UNAVAILABLE
```

The third test generates the exact effectiveRef using the codec before mocking
the cancel throw, so the list+match succeeds first.

### Create EibTemporalActProjectionServiceTest

Use the **exact code** from `patch-context.md §5.2`.

### Create EibHabitatControllerFailureBoundaryTest

Use the **exact code** from `patch-context.md §5.3`.

The test triggers upstream failure by making `client.getTopologySnapshot(...)` and
`client.getDiagnostics(...)` throw. It verifies that the response contains
`$.status = UPSTREAM_UNAVAILABLE` and `$.error.code = UPSTREAM_UNAVAILABLE`.

### In EibApiControllerTest — add 2 methods

Use the **exact code** from `patch-context.md §5.4`. Add to the existing class
(which already has 2 tests). The 2 new tests verify the full JSON shape of the
controller response when the service returns `FAILED_UPSTREAM_UNAVAILABLE`.

---

## Task 6 — Run validation

```bash
cd eib
mvn compile
# Verify: grep -r "com.sovereign.connect" src/main/ | wc -l returns 0

mvn test -Dtest=EibTemporalAdmissionServiceTest
# Expected: 5 tests, 0 failures (2 existing + 3 new)

mvn test -Dtest=EibTemporalActProjectionServiceTest,EibHabitatControllerFailureBoundaryTest,EibApiControllerTest
# Expected: 9 tests, 0 failures

mvn test
# Expected: all pass, 0 failures, 0 errors
# Expected minimum: 33 tests (23 baseline + 10 new)

cd ..
mvn test
# Expected: 240 tests, 0 failures, 0 errors (SC-C baseline unchanged)
```

---

## Task 7 — Update implementation report

Add the failure-boundary patch section to `docs/mir/mir-022/implementation-report.md`
as shown in `patch-context.md §7`.

---

## Hard stop conditions

Stop and report instead of improvising if:

```text
- A fix requires importing com.sovereign.connect.* into EIB.
- A fix requires modifying SC-C production code.
- A fix requires changing the routing-fix patch routes.
- Tests pass only by catching Exception broadly and swallowing errors.
- A fix changes effective-view partial temporal-act degradation from warning to global failure.
- A fix modifies RestClientEibScNorthboundClient route paths already corrected by the routing patch.
- The test for cancelTemporalAct throw cannot generate the correct effectiveRef
  without understanding the codec — in that case, use codec.generateRef("eib.temporal",
  "habitat.alpha", "temporal.visible") with the secret "test-secret-32-chars-for-hmac-256".
```

---

## Expected final state

```text
EIB full suite: ≥33 tests, 0 failures, 0 errors, 0 skipped
SC-C baseline: 240 tests, 0 failures, 0 errors, 0 skipped
EibUpstreamUnavailableException: does not escape as framework error anywhere
NOT_FOUND from device/endpoint routes: only after successful parent fetch
Routing-fix tests: still passing
Architecture boundary: still passing
```
