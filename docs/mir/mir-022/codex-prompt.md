# Codex Prompt — MU-022 Effective Interaction Boundary Initial Implementation Slice

You are implementing `MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001` (MU-022).

Read in this exact order before writing any code:

```text
docs/mir/mir-022/context.md           ← primary implementation guide
docs/mir/mir-022/acceptance-map.md    ← maps every AC to required tests
docs/mir/mir-022/MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001.md
docs/mir/mir-022/code-surface-audit.md
```

---

## Hard rule — runtime boundary

```text
Same repository: allowed.
Same SC-C runtime application context: NOT allowed.
```

EIB is a separate Spring Boot application under `eib/`. It consumes SC-C only through
the configured HTTP base URL. It MUST NOT import or call any class from
`com.sovereign.connect.*`.

Verify this before every commit: `grep -r "com.sovereign.connect" eib/src/main/` must
return zero results.

---

## Step 1 — Create eib/ project skeleton

Create `eib/pom.xml` using the **exact complete file** from context.md §2. This is a
standalone Spring Boot project with `spring-boot-starter-parent` as parent. It has no
dependency on the SC-C project.

Create `eib/src/main/java/com/sovereign/eib/EibApplication.java`:

```java
@SpringBootApplication
public class EibApplication {
    public static void main(String[] args) {
        SpringApplication.run(EibApplication.class, args);
    }
}
```

Create `eib/src/main/resources/application.yml` (exact content in context.md §3).
Create `eib/src/test/resources/application-test.yml` (exact content in context.md §3).

**STOP-1:** `cd eib && mvn compile` must succeed.
Then verify: `grep -r "com.sovereign.connect" src/main/ | wc -l` must print `0`.

---

## Step 2 — Configuration properties and wiring

In `eib/src/main/java/com/sovereign/eib/config/`:

Create three `@ConfigurationProperties` records (exact shapes in context.md §3):
- `EibScNorthboundClientProperties` — prefix `sc.eib.northbound`
- `EibRefCodecProperties` — prefix `sc.eib.ref-codec`
- `EibDiagnosticAdminProperties` — prefix `sc.eib.diagnostic-admin`

Create `EibConfiguration.java` using the **exact bean wiring from context.md §5.1**.

Critical: The `RestClient.Builder` MUST be a named `@Bean` (`eibRestClientBuilder`),
not created inline inside `restClient()`. The `RestClient` bean is then built from
that builder. This is required for `MockRestServiceServer` to work in tests.

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

The configuration class must carry `@EnableConfigurationProperties` for all three
properties records.

---

## Step 3 — Upstream mirror DTOs

In `eib/src/main/java/com/sovereign/eib/northbound/dto/`, create all records from
context.md §4. Use the **exact field names** shown — Jackson deserializes by field name.

Critical fields:

```text
NorthboundDeviceViewDto.providerDeviceId    ← include for deserialization; suppressed from product views
NorthboundEndpointViewDto.providerEndpointId ← same rule
NorthboundTopologySnapshotDto.topologyVersionValue ← source of sourceTopologyVersion
NorthboundTopologyVersionViewDto            ← structured DTO with habitatId, value, scopeType, scopeId
                                              NOT a bare String — see context.md §4
NorthboundCancelTemporalActRequestDto       ← fields: requestedByRef, idempotencyKey, reason
                                              temporalActId is NOT a field — it goes in the URL path
```

Also create the envelope types:
```text
ScEnvelope<T>(String status, T payload, List<ScWarningDto> warnings, ScErrorDto error)
ScErrorDto(String code, String message, String source)
ScWarningDto(String code, String message, String source)
```

---

## Step 4 — EibEffectiveRefCodec

Create `eib/src/main/java/com/sovereign/eib/projection/EibEffectiveRefCodec.java`
using the **exact implementation** from context.md §6:

- HMAC-SHA256 with configurable secret
- Input: `habitatId + ":" + type + ":" + canonicalId`
- Encoding: URL-safe Base64 without padding, first 22 chars
- Prefix families: `eib.room.`, `eib.zone.`, `eib.device.`, `eib.endpoint.`, `eib.temporal.`
- Include `resolveTemporalRef(suppliedRef, habitatId, visibleActs)` returning `Optional<String>`

Create `eib/src/test/java/com/sovereign/eib/EibEffectiveRefCodecTest.java`.

**STOP-2:** `cd eib && mvn test -Dtest=EibEffectiveRefCodecTest`

Required assertions:
```java
// Determinism
assertThat(codec.generateRef("eib.device", "h1", "device.tuya.x"))
    .isEqualTo(codec.generateRef("eib.device", "h1", "device.tuya.x"));
// No canonical ID leakage
assertThat(codec.generateRef("eib.device", "h1", "device.tuya.light-1"))
    .doesNotContain("device.tuya.light-1");
// Different IDs → different refs
assertThat(codec.generateRef("eib.device", "h1", "device.tuya.light-1"))
    .isNotEqualTo(codec.generateRef("eib.device", "h1", "device.tuya.light-2"));
// Prefix present
assertThat(codec.generateRef("eib.temporal", "h1", "act-1"))
    .startsWith("eib.temporal.");
// resolveTemporalRef finds canonical ID in list
// resolveTemporalRef returns empty for unknown ref
```

---

## Step 5 — EibScNorthboundClient port and RestClient adapter

Create the port `EibScNorthboundClient.java`:

```java
interface EibScNorthboundClient {
    ScEnvelope<NorthboundTopologySnapshotDto> getTopologySnapshot(String habitatId);
    ScEnvelope<NorthboundTopologyVersionViewDto> getTopologyVersion(String habitatId);
    ScEnvelope<NorthboundDeviceHealthViewDto> getDeviceHealth(String habitatId, String deviceId);
    ScEnvelope<NorthboundEndpointHealthViewDto> getEndpointHealth(String habitatId, String endpointId);
    ScEnvelope<NorthboundRuntimeStateViewDto> getDeviceRuntimeState(String habitatId, String deviceId);
    ScEnvelope<NorthboundRuntimeStateViewDto> getEndpointRuntimeState(String habitatId, String endpointId);
    ScEnvelope<NorthboundDiagnosticsViewDto> getDiagnostics(String habitatId);
    ScEnvelope<List<NorthboundTemporalActViewDto>> listTemporalActs(
        String habitatId, String mode, Integer maxResults);
    ScEnvelope<NorthboundTemporalActViewDto> getTemporalAct(
        String habitatId, String temporalActId);
    ScEnvelope<NorthboundTemporalActViewDto> createSignalTemporalAct(
        String habitatId, NorthboundCreateSignalTemporalActRequestDto request);
    ScEnvelope<NorthboundTemporalActViewDto> cancelTemporalAct(
        String habitatId, String temporalActId, NorthboundCancelTemporalActRequestDto request);
}
```

Create `RestClientEibScNorthboundClient.java` using:
- `ParameterizedTypeReference<ScEnvelope<T>>(){}` for typed deserialization (context.md §5.2)
- `onStatus` handler for 503 disambiguation (context.md §5.3)
- Catch `RestClientException`, wrap as `EibUpstreamUnavailableException`
- `EibSemanticScCException` carries the valid envelope for semantic non-2xx responses

Create:
```text
EibUpstreamUnavailableException extends RuntimeException
EibSemanticScCException extends RuntimeException  { ScEnvelope<?> getEnvelope() }
```

Create `eib/src/test/java/com/sovereign/eib/EibScNorthboundClientTest.java` using
the **exact MockRestServiceServer pattern from context.md §13**.

Important: inject `@Qualifier("eibRestClientBuilder") RestClient.Builder`, bind
`MockRestServiceServer` to that builder in `@BeforeEach`, then build a local
`RestClient` and local `RestClientEibScNorthboundClient` for the test. Do not
rely on an autowired production `RestClientEibScNorthboundClient`, because that
bean may already have been constructed before the mock server binds to the builder.

**STOP-3:** `cd eib && mvn test -Dtest=EibScNorthboundClientTest`

Before running, verify the test constructs its client after `MockRestServiceServer.bindTo(...)`.

Required test cases:
```text
✓ connection refused → EibUpstreamUnavailableException
✓ HTTP 503 with valid ScEnvelope(DEFERRED_SC_B_REQUIRED) → envelope returned, status preserved
✓ HTTP 503 with no body → EibUpstreamUnavailableException
✓ malformed JSON → EibUpstreamUnavailableException
✓ topology snapshot success → topologyVersionValue is non-null
✓ temporal acts list success → list deserialized with temporalActId fields
```

---

## Step 6 — Effective view domain types

In `eib/src/main/java/com/sovereign/eib/domain/`, create all records from context.md §7:

```text
EibResponse<T>                     ← wrapper for all /eib/v1 routes
EibError, EibWarning
EffectiveHabitatView               ← includes sourceTopologyVersion and List<EffectiveTemporalActView>
EffectiveRoomView, EffectiveZoneView
EffectiveDeviceView                ← canonicalDeviceId: null in ordinary mode
EffectiveEndpointView              ← canonicalEndpointId: null in ordinary mode
EffectiveCapabilityAffordance
EffectiveTemporalActView           ← canonicalTemporalActId: null in ordinary mode
EffectiveDiagnosticsView, EffectiveDiagnosticsSummary
InteractionAdmissionDecision       ← effectiveRef: non-null only after accepted create; null for cancel
CanonicalSubmissionTrace           ← scNorthboundStatus must preserve SC-C status string
EibTemporalSignalAdmissionRequest
EibTemporalCancellationAdmissionRequest
```

Create `EibRequestContext.java` with the `fromHeaders` factory method from context.md §8.
Note the `isOrdinaryMode()` helper method: `return !diagnosticAdminAuthorized`.

---

## Step 7 — Projection services, mapper and envelope mapper

Create `EibEffectiveViewMapper.java`. Enforce these rules from context.md §11:

```text
canonicalDeviceId:      null unless ctx.diagnosticAdminAuthorized()
canonicalEndpointId:    null unless ctx.diagnosticAdminAuthorized()
canonicalTemporalActId: null unless ctx.diagnosticAdminAuthorized()
providerDeviceId:       NEVER copied to EffectiveDeviceView under any mode
providerEndpointId:     NEVER copied to EffectiveEndpointView under any mode
healthSummary/runtimeSummary in habitat view: default to "UNKNOWN"
```

Create `EibCanonicalEnvelopeMapper.java`:
```text
extractScStatus(ScEnvelope<?>): returns status string unchanged
isSuccess(ScEnvelope<?>): true only for "OK","CREATED","ACCEPTED","CANCELLED"
toTrace(clientRef, admissionId, ScEnvelope<?>): builds CanonicalSubmissionTrace preserving status/error/warnings
```

Create `EibEffectiveViewService.java` using the **exact algorithm from context.md §9**:
1. CALL 1: `getTopologySnapshot` → topology (mandatory; if fails, return error)
2. CALL 2: `listTemporalActs("ACTIVE", null)` → temporalActs (degradable: PARTIAL + warning if fails)
3. Build rooms/zones/devices/endpoints from snapshot; healthSummary defaults to "UNKNOWN"

Create `EibTemporalActProjectionService.java` for `listEffectiveTemporalActs` and
`getEffectiveTemporalAct` (list+match for get).

Create tests:
- `EibEffectiveViewMapperTest.java` (assertions from context.md §11)
- `EibCanonicalEnvelopeMapperTest.java` (assertions from context.md §12)
- `EibEffectiveViewServiceTest.java` with assertions:
  - `sourceTopologyVersion` equals mock snapshot's `topologyVersionValue`
  - `temporalActs` is non-empty when upstream returns acts
  - `temporalActs` is empty and warnings are added when upstream fails

---

## Step 8 — Temporal admission service

Create `EibTemporalAdmissionService.java` using the **exact algorithms from context.md §10**.

`admitTemporalSignalRequest`:
- `createdByRef` = `"eib-service"` — never `ctx.actorRef()` or any personal identity
- If SC-C returns `ACCEPTED` with non-null `payload.temporalActId()`:
  generate `effectiveTemporalActRef` → set `InteractionAdmissionDecision.effectiveRef`
- Use `mapScStatusToAdmissionStatus` switch (exact mapping in context.md §10)

`admitTemporalCancellation`:
- Resolve `effectiveTemporalActRef` via `listTemporalActs + refCodec.resolveTemporalRef`
- If ref not found → return `REJECTED_NOT_VISIBLE` without calling SC-C cancel
- Cancel body: `NorthboundCancelTemporalActRequestDto("eib-service", idempotencyKey, reason)`
- `temporalActId` in URL path only — never in body
- `effectiveRef` in the returned `InteractionAdmissionDecision` is `null` (cancel does not create)

Create `EibTemporalAdmissionServiceTest.java`:
```text
✓ Signal create: ACCEPTED with payload → effectiveRef non-null, starts with "eib.temporal."
✓ Signal create: VALIDATION_ERROR → REJECTED_INVALID_REQUEST, effectiveRef=null
✓ Signal create: DEFERRED_SC_B_REQUIRED → DEFERRED_SC_B_REQUIRED, effectiveRef=null
✓ Cancel: visible temporal ref → cancel submitted to SC-C, effectiveRef=null
✓ Cancel: invisible temporal ref → REJECTED_NOT_VISIBLE, SC-C cancel not called
✓ Cancel: effectiveRef in decision is null regardless of SC-C outcome
✓ createdByRef sent to SC-C is "eib-service", never ctx.actorRef()
```

---

## Step 9 — API controllers

In `eib/src/main/java/com/sovereign/eib/api/`:

`EibHabitatController` — `@RequestMapping("/eib/v1/habitats/{habitatId}")`:

```text
GET /effective-view                         → EibResponse<EffectiveHabitatView>
GET /devices                                → EibResponse<List<EffectiveDeviceView>>
GET /devices/{effectiveDeviceRef}           → EibResponse<EffectiveDeviceView>
GET /endpoints                              → EibResponse<List<EffectiveEndpointView>>
GET /endpoints/{effectiveEndpointRef}       → EibResponse<EffectiveEndpointView>
GET /diagnostics                            → EibResponse<EffectiveDiagnosticsView>
```

`EibTemporalActController` — same base mapping:

```text
GET  /temporal-acts                              → EibResponse<List<EffectiveTemporalActView>>
GET  /temporal-acts/{effectiveTemporalActRef}    → EibResponse<EffectiveTemporalActView>
POST /temporal-acts/signal                       → EibResponse<InteractionAdmissionDecision>
POST /temporal-acts/{effectiveTemporalActRef}/cancel → EibResponse<InteractionAdmissionDecision>
```

All controllers resolve `EibRequestContext` from request headers:
```java
EibRequestContext.fromHeaders(httpServletRequest, diagnosticAdminProperties.enabled())
```

All responses are wrapped in `EibResponse<T>` — never return bare DTOs.

---

## Step 10 — Architecture and isolation tests

Create `EibArchitectureTest.java` (exact pattern from context.md §14).

Both test methods scan `src/main/java` recursively and assert empty violations list.

---

## Step 11 — Complete test suite and STOP-4

Create `EibApiControllerTest.java`:

```text
✓ GET /effective-view returns EibResponse; payload.sourceTopologyVersion is non-null and non-empty
✓ GET /effective-view when SC-C returns temporal acts: payload.temporalActs is non-empty
✓ GET /effective-view when SC-C temporal acts fail: response still contains topology; warnings present
✓ ordinary GET /devices: JSON does not contain any providerDeviceId value
✓ ordinary GET /devices: JSON does not contain "canonicalDeviceId" key with a non-null value
✓ GET /devices with X-SC-Diagnostic-Mode: true (admin enabled): canonicalDeviceId present in JSON
✓ POST /temporal-acts/signal: 202 + EibResponse with effectiveRef non-null after ACCEPTED
✓ POST /temporal-acts/{ref}/cancel: resolves ref, returns EibResponse with effectiveRef=null
✓ unknown mode param returns 400 or equivalent EibResponse with error
```

**STOP-4:** `cd eib && mvn test`

Expected: all EIB tests pass, 0 failures, 0 errors.

Verify manually:
```text
1. Find any test asserting EffectiveHabitatView.sourceTopologyVersion — must be non-null
2. Find any test asserting EffectiveHabitatView.temporalActs — must be non-empty when upstream provides acts
3. Find any test serializing EffectiveDeviceView in ordinary mode — must not contain providerDeviceId value
4. Find admitTemporalCancellation test — InteractionAdmissionDecision.effectiveRef must be null
```

---

## Step 12 — Non-regression and implementation report

Run SC-C tests to confirm baseline unchanged:

```bash
cd ..
mvn test
```

Expected: 240 tests, 0 failures (SC-C baseline unchanged).

Update `docs/mir/mir-022/implementation-report.md`:

```text
Branch: feat/sc-eib-mir-022-effective-interaction-boundary-seed
Implementation commit: [hash]
Evidence commit: [hash]

EIB tests run: [N]
Failures: 0
Errors: 0
Skipped: 0

SC-C baseline: 240 tests, 0 failures, 0 errors (unchanged)

Placement decision:
  Same repository: yes
  Same SC-C runtime context: no
  Top-level eib/ placement: yes

DEBT-HTTP-004: closed for EIB initial implementation slice
  (runtime exists, consumes SC-C HTTP/OpenAPI, projection and admission validated)

List+match behavior: admitted for this slice; documented as DEBT-EIB-011

effectiveRef in cancel: null — cancel does not create a new canonical resource

AC-022-001 through AC-022-055: [PASS / status]

Retained debts: DEBT-HTTP-001, 002, 003, 005-008, DEBT-EIB-002 through DEBT-EIB-012
```
