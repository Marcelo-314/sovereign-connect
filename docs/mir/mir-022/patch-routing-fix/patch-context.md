# MU-022 Patch Context — EIB Routing Fix

```text
Document ID:  PATCH-CONTEXT-SOV-SC-EIB-MIR-022-ROUTING-FIX
Version:      v0.1.0
Status:       Execution patch context
MU:           MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
Slot:         MU-022
Branch:       feat/sc-eib-mir-022-effective-interaction-boundary-seed
Patch target: current MU-022 implementation attempt
```

---

## 0. Purpose

This patch context fixes the blocking defects found in the first MU-022 implementation review.

The current implementation is architecturally promising but **not L4** because the EIB upstream client calls routes that do not exist in the validated SC-C MU-021 HTTP/OpenAPI binding.

This is not a new MIR and not a scope expansion. It is an implementation correction within the same MU-022 iteration.

---

## 1. Non-negotiable boundaries

Preserve these decisions from the MIR/CSA:

```text
Same repository: allowed.
Same SC-C runtime application context: NOT allowed.
EIB consumes SC-C only through HTTP/OpenAPI.
```

Do not move EIB into `src/main/java/com/sovereign/connect/**`.
Do not add Java dependencies from EIB to SC-C classes.
Do not import `com.sovereign.connect.*` from `eib/src/main/java`.
Do not modify SC-C production code for this patch.

Allowed changes:

```text
eib/src/main/java/**
eib/src/test/java/**
eib/src/test/resources/**
docs/mir/mir-022/implementation-report.md
```

Avoid modifying SC-C root `pom.xml`, SC-C controllers, SC-C DTOs or SC-C tests unless a compile failure proves an unavoidable test-harness issue. No such modification is expected for this patch.

---

## 2. Blocking defects to fix

### BLOCKER-001 — topology snapshot route

Current incorrect behavior:

```text
GET /habitats/{habitatId}/topology/snapshot
```

Required behavior:

```text
GET /habitats/{habitatId}/topology
```

### BLOCKER-002 — runtime state route

Current incorrect behavior:

```text
GET /habitats/{habitatId}/runtime-state/{subjectId}
```

Required behavior:

```text
GET /habitats/{habitatId}/devices/{deviceId}/runtime-state
GET /habitats/{habitatId}/endpoints/{endpointId}/runtime-state
```

The `EibScNorthboundClient` port MUST expose separate methods:

```java
ScEnvelope<NorthboundRuntimeStateViewDto> getDeviceRuntimeState(String habitatId, String deviceId);
ScEnvelope<NorthboundRuntimeStateViewDto> getEndpointRuntimeState(String habitatId, String endpointId);
```

Remove or stop using any generic `getRuntimeState(habitatId, subjectId)` method.

### BLOCKER-003 — temporal list query params

Current incorrect behavior:

```text
GET /habitats/{habitatId}/temporal-acts?status={mode}&limit={maxResults}
```

Required behavior:

```text
GET /habitats/{habitatId}/temporal-acts?mode={mode}&maxResults={maxResults}
```

### BLOCKER-004 — Signal TemporalAct create route

Current incorrect behavior:

```text
POST /habitats/{habitatId}/temporal-acts/signal
```

Required behavior:

```text
POST /habitats/{habitatId}/temporal-acts
```

### BLOCKER-005 — missing topology version call

The EIB upstream client MUST implement:

```java
ScEnvelope<NorthboundTopologyVersionViewDto> getTopologyVersion(String habitatId);
```

Route:

```text
GET /habitats/{habitatId}/topology/version
```

The response body is a structured DTO, not a bare string:

```java
record NorthboundTopologyVersionViewDto(
    String habitatId,
    String value,
    String scopeType,
    String scopeId
) {}
```

### BLOCKER-006 — admission routes must return EibResponse<T>

The EIB API routes for admission MUST NOT return a bare `InteractionAdmissionDecision`.

Required route bodies:

```java
EibResponse<InteractionAdmissionDecision>
```

Applicable routes:

```text
POST /eib/v1/habitats/{habitatId}/temporal-acts/signal
POST /eib/v1/habitats/{habitatId}/temporal-acts/{effectiveTemporalActRef}/cancel
```

`EibResponse.payload` contains the `InteractionAdmissionDecision`.

---

## 3. Exact route implementation table

Use this table to patch `RestClientEibScNorthboundClient` and tests.

| Method | HTTP route |
|---|---|
| `getTopologySnapshot(habitatId)` | `GET /habitats/{id}/topology` |
| `getTopologyVersion(habitatId)` | `GET /habitats/{id}/topology/version` |
| `getDeviceHealth(habitatId, deviceId)` | `GET /habitats/{id}/devices/{deviceId}/health` |
| `getEndpointHealth(habitatId, endpointId)` | `GET /habitats/{id}/endpoints/{endpointId}/health` |
| `getDeviceRuntimeState(habitatId, deviceId)` | `GET /habitats/{id}/devices/{deviceId}/runtime-state` |
| `getEndpointRuntimeState(habitatId, endpointId)` | `GET /habitats/{id}/endpoints/{endpointId}/runtime-state` |
| `getDiagnostics(habitatId)` | `GET /habitats/{id}/diagnostics` |
| `listTemporalActs(habitatId, mode, maxResults)` | `GET /habitats/{id}/temporal-acts?mode={mode}&maxResults={maxResults}` |
| `getTemporalAct(habitatId, temporalActId)` | `GET /habitats/{id}/temporal-acts/{temporalActId}` |
| `createSignalTemporalAct(habitatId, request)` | `POST /habitats/{id}/temporal-acts` |
| `cancelTemporalAct(habitatId, temporalActId, request)` | `POST /habitats/{id}/temporal-acts/{temporalActId}/cancel` |

---

## 4. RestClient implementation rules

Use `ParameterizedTypeReference<ScEnvelope<T>>` for every typed envelope response.

`RestClientEibScNorthboundClient` must still distinguish:

```text
HTTP 503 with valid ScEnvelope(DEFERRED_SC_B_REQUIRED) -> semantic SC-C response
HTTP 503 with no body / invalid body / non-JSON body -> EibUpstreamUnavailableException
Malformed JSON -> EibUpstreamUnavailableException
Connection failure -> EibUpstreamUnavailableException
```

Do not collapse `DEFERRED_SC_B_REQUIRED` into upstream unavailable if a valid `ScEnvelope` body is present.

---

## 5. Tests to update or add

### 5.1 Update existing MockRestServiceServer expectations

Every test must use the real MU-021 route names. The following strings MUST NOT remain:

```text
/topology/snapshot
/runtime-state/{subjectId}
status=
limit=
/temporal-acts/signal
```

The test suite should include exact positive route expectations for:

```text
/topology
/topology/version
/devices/{deviceId}/runtime-state
/endpoints/{endpointId}/runtime-state
/temporal-acts?mode=ACTIVE&maxResults=50
/temporal-acts
/temporal-acts/{temporalActId}/cancel
```

### 5.2 Required test additions

Add or update tests in `EibScNorthboundClientTest`:

```text
- getTopologySnapshot uses /topology, not /topology/snapshot.
- getTopologyVersion uses /topology/version and deserializes NorthboundTopologyVersionViewDto.value.
- getDeviceRuntimeState uses /devices/{deviceId}/runtime-state.
- getEndpointRuntimeState uses /endpoints/{endpointId}/runtime-state.
- listTemporalActs uses mode and maxResults query params.
- createSignalTemporalAct posts to /temporal-acts, not /temporal-acts/signal.
- semantic 503 with ScEnvelope(DEFERRED_SC_B_REQUIRED) returns envelope.
- 503 with no valid envelope throws EibUpstreamUnavailableException.
- malformed JSON throws EibUpstreamUnavailableException.
```

Add or update API/controller tests:

```text
- POST /eib/v1/.../temporal-acts/signal returns EibResponse<InteractionAdmissionDecision> body.
- POST /eib/v1/.../temporal-acts/{ref}/cancel returns EibResponse<InteractionAdmissionDecision> body.
```

Required JSON assertions for admission routes:

```text
$.status exists
$.payload.status exists
$.payload.effectiveRef exists for accepted signal create
$.payload.effectiveRef is null for cancel
$.canonicalTrace may be null at wrapper level, because trace is inside payload.canonicalTrace
```

### 5.3 Regression grep

After patching, run:

```bash
grep -R "topology/snapshot\|runtime-state/\{subjectId\}\|status=\|limit=\|temporal-acts/signal" eib/src/main eib/src/test
```

Expected: no matches for upstream SC-C client routes.

Note: `/eib/v1/.../temporal-acts/signal` is an EIB public route and may still appear in EIB API controller/tests. The forbidden `/temporal-acts/signal` applies to SC-C upstream client paths only.

---

## 6. Stop conditions

### STOP-PATCH-1 — compile

```bash
cd eib
mvn compile
```

Expected: build success.

### STOP-PATCH-2 — focused client tests

```bash
cd eib
mvn test -Dtest=EibScNorthboundClientTest
```

Expected: all tests pass. Test output must include route coverage for all corrected routes.

### STOP-PATCH-3 — focused API/admission tests

```bash
cd eib
mvn test -Dtest=EibApiControllerTest,EibTemporalAdmissionServiceTest
```

Expected: all tests pass. Admission API routes return `EibResponse<InteractionAdmissionDecision>`.

### STOP-PATCH-4 — full EIB suite

```bash
cd eib
mvn test
```

Expected: all EIB tests pass, 0 failures, 0 errors, 0 skipped.

### STOP-PATCH-5 — SC-C baseline

From repo root:

```bash
mvn test
```

Expected: SC-C baseline remains green. Previously observed baseline was 240 tests, 0 failures, 0 errors, 0 skipped.

---

## 7. Implementation report update

Update `docs/mir/mir-022/implementation-report.md` with:

```text
Patch commit: <hash>
Patch scope: MU-022 routing alignment against MU-021 HTTP/OpenAPI binding

Resolved blockers:
  BLOCKER-001 — /topology/snapshot -> /topology
  BLOCKER-002 — generic runtime-state route replaced with device/endpoint-specific routes
  BLOCKER-003 — status/limit query params -> mode/maxResults
  BLOCKER-004 — /temporal-acts/signal -> /temporal-acts for SC-C upstream create
  BLOCKER-005 — getTopologyVersion implemented with NorthboundTopologyVersionViewDto
  BLOCKER-006 — admission routes return EibResponse<InteractionAdmissionDecision>

Validation:
  EIB compile: PASS
  EIB focused client tests: PASS, <N> tests
  EIB full suite: PASS, <N> tests, 0 failures, 0 errors, 0 skipped
  SC-C baseline: PASS, <N> tests, 0 failures, 0 errors, 0 skipped

Residual status:
  Re-review required before L4 closure.
```
