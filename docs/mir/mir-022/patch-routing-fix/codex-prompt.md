# Codex Prompt — MU-022 Routing Fix Patch

You are patching the current implementation of `MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001` (MU-022).

This is a focused patch. Do not reimplement MU-022 from scratch.

Read first:

```text
docs/mir/mir-022/patch-routing-fix/patch-context.md
docs/mir/mir-022/patch-routing-fix/route-contract-MU-021.md
docs/mir/mir-022/patch-routing-fix/acceptance-map-patch.md
docs/mir/mir-022/patch-routing-fix/review-MU-022-effective-interaction-boundary-implementation-merged.md
```

## Goal

Make the current EIB implementation consume the real SC-C MU-021 HTTP/OpenAPI binding.

The current implementation is not L4 because it calls upstream routes that do not exist in MU-021. Fix only the implementation and tests required to resolve the blockers.

## Hard boundaries

Preserve the runtime boundary:

```text
EIB stays under /eib as a separate Spring Boot application.
EIB MUST NOT import com.sovereign.connect.*.
EIB MUST NOT be moved into the SC-C Spring context.
SC-C production code MUST NOT be modified for this patch.
```

## Patch tasks

### Task 1 — Fix upstream route strings in RestClientEibScNorthboundClient

Update `RestClientEibScNorthboundClient` so it uses exactly these upstream routes:

```text
GET  /habitats/{id}/topology
GET  /habitats/{id}/topology/version
GET  /habitats/{id}/devices/{deviceId}/health
GET  /habitats/{id}/endpoints/{endpointId}/health
GET  /habitats/{id}/devices/{deviceId}/runtime-state
GET  /habitats/{id}/endpoints/{endpointId}/runtime-state
GET  /habitats/{id}/diagnostics
GET  /habitats/{id}/temporal-acts?mode={mode}&maxResults={maxResults}
GET  /habitats/{id}/temporal-acts/{temporalActId}
POST /habitats/{id}/temporal-acts
POST /habitats/{id}/temporal-acts/{temporalActId}/cancel
```

Remove or stop using incorrect upstream routes:

```text
/topology/snapshot
/runtime-state/{subjectId}
?status=...
?limit=...
/temporal-acts/signal
```

### Task 2 — Split runtime-state client methods

If the port currently has a generic method like:

```java
getRuntimeState(String habitatId, String subjectId)
```

replace it with:

```java
ScEnvelope<NorthboundRuntimeStateViewDto> getDeviceRuntimeState(String habitatId, String deviceId);
ScEnvelope<NorthboundRuntimeStateViewDto> getEndpointRuntimeState(String habitatId, String endpointId);
```

Update all callers accordingly.

### Task 3 — Add getTopologyVersion

Ensure the port and adapter expose:

```java
ScEnvelope<NorthboundTopologyVersionViewDto> getTopologyVersion(String habitatId);
```

Route:

```text
GET /habitats/{id}/topology/version
```

Test that `payload.value()` is deserialized from the response.

### Task 4 — Wrap admission API responses in EibResponse<T>

The EIB public routes:

```text
POST /eib/v1/habitats/{habitatId}/temporal-acts/signal
POST /eib/v1/habitats/{habitatId}/temporal-acts/{effectiveTemporalActRef}/cancel
```

must return `EibResponse<InteractionAdmissionDecision>` as the HTTP body.

Do not return bare `InteractionAdmissionDecision`.

Required JSON shape:

```json
{
  "status": "OK" | "ACCEPTED" | "ERROR" | "PARTIAL",
  "payload": {
    "status": "ADMITTED" | "REJECTED_NOT_VISIBLE" | "REJECTED_INVALID_REQUEST" | "DEFERRED_SC_B_REQUIRED" | "UPSTREAM_UNAVAILABLE",
    "effectiveRef": "eib.temporal..." | null,
    "canonicalTrace": { "scNorthboundStatus": "..." }
  },
  "warnings": [],
  "error": null,
  "canonicalTrace": null
}
```

It is acceptable that `canonicalTrace` appears inside `payload.canonicalTrace`; the wrapper-level `canonicalTrace` may be null for admission routes if the existing design places trace inside the payload.

### Task 5 — Update MockRestServiceServer tests

Update existing expectations and add missing route tests.

Required test assertions in `EibScNorthboundClientTest`:

```text
- getTopologySnapshot expects /habitats/habitat.alpha/topology exactly.
- getTopologyVersion expects /habitats/habitat.alpha/topology/version exactly.
- getDeviceRuntimeState expects /habitats/habitat.alpha/devices/device.alpha/runtime-state.
- getEndpointRuntimeState expects /habitats/habitat.alpha/endpoints/endpoint.alpha/runtime-state.
- listTemporalActs expects query params mode=ACTIVE and maxResults=50.
- createSignalTemporalAct expects POST /habitats/habitat.alpha/temporal-acts.
- cancelTemporalAct expects POST /habitats/habitat.alpha/temporal-acts/{temporalActId}/cancel.
- HTTP 503 with valid ScEnvelope(DEFERRED_SC_B_REQUIRED) returns envelope, not exception.
- HTTP 503 without valid envelope throws EibUpstreamUnavailableException.
- malformed JSON throws EibUpstreamUnavailableException.
```

Use exact URL expectations where practical. Avoid `containsString("/topology")` for the route-regression test, because it would not catch `/topology/snapshot`.

### Task 6 — Add route-regression grep test

Add a test or architecture assertion that fails if these incorrect upstream route fragments remain in `eib/src/main/java/com/sovereign/eib/northbound`:

```text
/topology/snapshot
/runtime-state/{subjectId}
status
limit
/temporal-acts/signal
```

Important: the EIB public API route `/eib/v1/.../temporal-acts/signal` is valid. The prohibition applies to the upstream SC-C northbound client, not to EIB API controllers.

### Task 7 — Run validation

Run:

```bash
cd eib
mvn compile
mvn test -Dtest=EibScNorthboundClientTest
mvn test -Dtest=EibApiControllerTest,EibTemporalAdmissionServiceTest
mvn test
cd ..
mvn test
```

Record exact counts in `docs/mir/mir-022/implementation-report.md`.

## Stop conditions

Stop and report instead of improvising if:

```text
- A fix requires importing com.sovereign.connect.* into EIB.
- A fix requires changing SC-C production code.
- A fix requires changing MU-021 route contract.
- EIB tests pass only by mocking routes that do not exist in MU-021.
- Admission API cannot return EibResponse<T> without changing domain records.
```

## Expected final state

After this patch:

```text
MU-022 remains an implementation attempt until re-reviewed.
All route mismatches are corrected.
EIB tests validate the real MU-021 route contract.
Admission routes return EibResponse<T>.
Implementation report records patch commit and test counts.
```
