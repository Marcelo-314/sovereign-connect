# MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001

## Effective Interaction Boundary Seed

**Document ID:** MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001  
**Title:** Effective Interaction Boundary Seed  
**Version:** v0.2.0-candidate  
**Status:** Candidate / PDR candidate consumed / SDD candidate consumed / CSA-approved / execution package enabled  
**Date:** 2026-05-26  
**Corpus:** Sovereign Connect  
**Type:** MIR  
**Plane:** SC-X / EIB  
**Materialization Unit:** MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001  
**Operational Slot:** MU-022  
**Scope:** First implementation-oriented materialization of the Effective Interaction Boundary as a non-co-located runtime that consumes the validated SC-C Northbound HTTP/OpenAPI binding. This MIR authorizes an initial EIB implementation slice for effective read projection, effective TemporalAct observation, SC-C-local Signal TemporalAct admission/cancellation, canonical envelope preservation, effective reference generation and diagnostic/admin canonical ID gating. This MIR does not implement View Composer as a separate service, Hub, SApp, Surfaces, Authority/Policy/Identity/Session systems of record, device/endpoint action dispatch, discovery admission, live updates, GraphQL, MCP, gRPC/ConnectRPC, WebSocket, SSE, SC-B runtime, SC-D adapters or EIB conformance/TCK.

---

## Changelog v0.2.0-candidate

Candidate promotion.

This version:

1. Promotes `MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001` from `v0.1.0-draft` to `v0.2.0-candidate`.
2. Adds AC coverage requiring `EffectiveHabitatView.sourceTopologyVersion` to be derived from the SC-C topology snapshot `topologyVersionValue`.
3. Adds AC coverage requiring `EffectiveHabitatView.temporalActs` to be populated from SC-C `GET /temporal-acts` when SC-C returns temporal acts.
4. Removes the escape hatch from AC-022-011: rooms and zones are derived from `GET /sc/v1/habitats/{habitatId}/topology`; separate `/rooms` and `/zones` calls are not required.
5. Keeps MIR scope unchanged: EIB remains non-co-located, consumes SC-C only through HTTP/OpenAPI, and does not implement device/endpoint action admission, discovery, live updates, GraphQL, MCP, gRPC/ConnectRPC, WebSocket, SSE, SC-B runtime or SC-D adapters.

---

## Changelog v0.1.0-draft

Initial MIR draft.

This version:

1. Opens `MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001` as `MU-022` after validation of `MU-021` and acceptance of the EIB PDR/SDD/CSA chain.
2. Adopts `PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.3.0-candidate` as the contract baseline.
3. Adopts `SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.2.0-candidate` as the design baseline.
4. Adopts `CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-merged` as the controlling code-surface audit.
5. Materializes EIB as a separate runtime boundary, preferably under a top-level `eib/` Maven project in the same repository.
6. Preserves the rule: same repository is allowed; same SC-C runtime application context is not allowed.
7. Requires EIB to consume SC-C only through the Northbound HTTP/OpenAPI binding validated by MU-021.
8. Requires EIB-owned mirror DTOs for the upstream SC-C wire shape; direct imports from SC-C core packages are forbidden.
9. Requires `EibScNorthboundClient` as the explicit upstream client boundary.
10. Requires effective reference generation using an HMAC-SHA256-style non-reversible codec or equivalent deterministic non-leaking codec.
11. Requires `EffectiveHabitatView`, `EffectiveRoomView`, `EffectiveZoneView`, `EffectiveDeviceView`, `EffectiveEndpointView`, `EffectiveCapabilityAffordance`, `EffectiveTemporalActView`, `EffectiveDiagnosticsView`, `InteractionAdmissionDecision` and `CanonicalSubmissionTrace` data contracts.
12. Requires EIB to preserve `ScNorthboundResponse<T>`, all twelve `ScNorthboundStatus` values, `ScNorthboundError.source` and `ScNorthboundWarning.source` internally.
13. Requires provider-native IDs (`providerDeviceId`, `providerEndpointId`) to be suppressed from ordinary product-facing views.
14. Requires canonical IDs (`canonicalDeviceId`, `canonicalEndpointId`, `canonicalTemporalActId`) to be exposed only under explicit diagnostic/admin context with authorization confirmation.
15. Requires TemporalAct observation and SC-C-local Signal TemporalAct admission/cancellation in the first implementation-oriented slice.
16. Requires network/transport failures to be distinguished from valid semantic SC-C envelopes that carry non-2xx HTTP statuses.
17. Keeps action intent over device/endpoint, discovery intent, live updates, persistent effective-ref registry, real Authority/Policy integration and EIB TCK out of scope.
18. Externalizes implementation context, Codex prompt, acceptance map and implementation report according to MU/MIR governance.

---

## 0. Governance note

This MIR does not include Codex prompts, operational context or acceptance-map details inline.

Execution assets MUST be produced separately under:

```text
docs/mir/mir-022/
  MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001.md
  code-surface-audit.md
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report-template.md
  implementation-report.md
```

Operational rule:

```text
MIR defines the materialization scope.
CSA constrains the actual code surface.
context.md and codex-prompt.md operationalize implementation.
acceptance-map.md maps MIR/SDD/CSA criteria to tests and evidence.
implementation-report.md returns post-execution evidence.
```

This MIR is candidate. It does not authorize implementation until accompanied by an accepted execution package.

---

## 1. Disposition

```text
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001 v0.2.0-candidate:
  Candidate MIR for MU-022.

Execution readiness:
  PDR candidate exists.
  SDD candidate exists.
  Post-SDD CSA merged and approvable.
  MIR review completed.
  Candidate promotion completed.
  Execution package required before implementation.

Implementation authorization:
  Direct implementation is authorized only after acceptance of context.md,
  codex-prompt.md, acceptance-map.md and implementation-report-template.md
  as the external execution package.
```

Draft constraints:

```text
- MU-022 identity and operational slot remain bound to INDEX/SYNC governance.
- CSA decisions are binding for the execution package.
- Execution package MUST NOT broaden scope beyond this MIR.
- This MIR MUST NOT implement EIB inside the SC-C runtime application context.
- This MIR MUST NOT add EIB controllers/services under the SC-C application package.
- This MIR MUST NOT import SC-C core Java packages from EIB.
- This MIR MUST NOT implement device/endpoint action dispatch, discovery, SC-B runtime, SC-D adapters, live updates, GraphQL, MCP, gRPC/ConnectRPC, WebSocket or SSE.
```

---

## 2. Background

MU-019 created the in-process canonical SC-C Northbound Facade.

MU-020 hardened that facade by stabilizing the response envelope, source-bearing errors/warnings, validation behavior, diagnostics and health derivation.

MU-021 created the first real external exposure binding for SC-C Northbound through HTTP/OpenAPI.

Post-MU-021 state:

```text
SC-C Northbound HTTP/OpenAPI exposure:
  Validated L4.
  Externally consumable by authorized technical consumers.
  SSE remains deferred.
  gRPC/ConnectRPC remains downstream.
  MCP remains a separate AI/tool/admin exposure track.
```

EIB exists because external canonical truth is still not effective product interaction.

SC-C answers canonical questions:

```text
What exists canonically?
What state or health is known canonically?
What request lifecycle status does SC-C report?
What is unsupported, deferred or pending normalization?
```

EIB answers effective interaction questions:

```text
What is visible in this context?
What is operable in this context?
Which canonical affordances become effective affordances?
Which effective request is admissible?
How should canonical uncertainty be represented product-safely?
Which downstream product client may see or do what?
```

The intended chain after MU-022 is:

```text
SC-C canonical state
  -> SC-C Northbound HTTP/OpenAPI binding
  -> EIB non-co-located runtime
  -> Hub / SApp / Surfaces / View Composer-facing clients later
```

Forbidden chain:

```text
EIB
  -> ScCoreNorthboundFacade Java interface
  -> core.northbound / core.topology / core.temporal packages
  -> repositories / SQLite / JdbcTemplate / Flyway
```

---

## 3. Depends on

```text
Sovereign Connect — Curator & Descent Instructions
RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate
RFC-SOV-TOPOLOGY-BASE-PROJECTION-SPLIT-001
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.16-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.22-draft
SYNC-SOV-SC-MU-BACKLOG-001 v0.1.23-draft
ADR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-draft
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.3.0-candidate
SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.2.0-candidate
CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-merged
ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-BINDING-001 v0.1.1-draft
SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001 v0.2.0-candidate / accepted
MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001 v1.0.0-accepted
MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001 v1.0.0-accepted
MIR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v1.0.0-accepted
```

If governance documents use newer versions in the repository at execution time, the execution package MUST reference the newer canonical versions while preserving the same MU identity and decisions.

---

## 4. Related

```text
PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001 v0.1.1-draft
PDR-SOV-SC-C-TEMPORAL-ACTS-001 v0.1.1-draft
SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.1.2-draft
PDR-SOV-SC-VIEW-COMPOSER-BOUNDARY-001, future / EIB-framed
SDD-SOV-SC-VIEW-COMPOSER-001, future / EIB-framed
PDR-SOV-SC-HUB-SC-BOUNDARY-001, pending
PDR-SOV-SC-TOPOLOGY-PROJECTION-BOUNDARY-001, pending / may be absorbed or superseded by EIB/VC artifacts
PDR-SOV-SC-DIAGNOSTIC-ADMIN-BOUNDARY-001, pending
PDR-SOV-SC-MCP-FACADE-001, separate AI/tool/admin exposure track
SDD-SOV-SC-C-NORTHBOUND-GRPC-CONNECTRPC-001, future downstream option
TCK-SOV-SC-EIB-CONFORMANCE-001, future
```

---

## 5. Materialization thesis

MU-022 materializes the first EIB runtime boundary.

Canonical statement:

```text
SC-C owns canonical truth.
SC-C Northbound HTTP/OpenAPI exposes canonical truth across a process boundary.
EIB consumes that exposed canonical truth and composes effective interaction.
Hub, SApp and Surfaces consume EIB later, not SC-C directly.
```

Implementation statement:

```text
MU-022 creates a separate EIB runtime slice.
It consumes SC-C through HTTP/OpenAPI.
It projects effective read views.
It admits SC-C-local Signal TemporalAct requests and cancellations.
It preserves canonical envelope semantics internally.
It does not mutate canonical topology.
It does not execute device/provider operations.
```

Key boundary rule:

```text
Same repository is allowed.
Same SC-C runtime application context is not allowed.
```

---

## 6. Goals

### G-022-001 — Separate EIB runtime placement

Implement EIB as a separate runtime boundary.

Preferred repository placement:

```text
eib/
  pom.xml
  src/main/java/com/sovereign/eib/EibApplication.java
  src/main/java/com/sovereign/eib/**
  src/test/java/com/sovereign/eib/**
```

The execution package MAY refine class/package names, but MUST preserve runtime separation.

### G-022-002 — SC-C HTTP/OpenAPI upstream consumption

Implement EIB upstream consumption through the validated SC-C Northbound HTTP/OpenAPI binding.

Required base URL configuration:

```yaml
sc:
  eib:
    northbound:
      base-url: http://localhost:8080/sc/v1
      timeout-ms: 2000
```

### G-022-003 — EIB-owned mirror DTOs

Implement EIB-owned mirror DTOs for SC-C Northbound wire shapes.

EIB MUST NOT import SC-C Java DTOs or `ScCoreNorthboundFacade`.

### G-022-004 — Canonical envelope preservation

EIB MUST preserve internally:

```text
ScNorthboundResponse<T> equivalent as ScEnvelope<T>
all twelve ScNorthboundStatus values
ScNorthboundError(code, message, source)
ScNorthboundWarning(code, message, source)
```

### G-022-005 — Effective view projection

Implement effective projection for:

```text
EffectiveHabitatView
EffectiveRoomView
EffectiveZoneView
EffectiveDeviceView
EffectiveEndpointView
EffectiveCapabilityAffordance
EffectiveTemporalActView
EffectiveDiagnosticsView
```

### G-022-006 — Effective references

Generate non-canonical effective references for product-facing views.

Required families:

```text
eib.room.<token>
eib.zone.<token>
eib.device.<token>
eib.endpoint.<token>
eib.temporal.<token>
```

### G-022-007 — Diagnostic/admin canonical ID gating

Ordinary product-facing views MUST NOT expose canonical IDs or provider-native IDs.

Canonical IDs MAY be exposed only under explicit diagnostic/admin context with authorization confirmation.

Provider-native IDs MUST NOT be exposed in ordinary views and MUST remain metadata only.

### G-022-008 — TemporalAct observation

Implement effective TemporalAct observation:

```text
listEffectiveTemporalActs
getEffectiveTemporalAct
```

### G-022-009 — TemporalAct admission

Implement SC-C-local Signal TemporalAct admission and cancellation:

```text
admitTemporalSignalRequest
admitTemporalCancellation
```

If SC-C accepts creation and returns `NorthboundTemporalActView`, EIB MUST generate `effectiveTemporalActRef` for the newly created canonical temporal act and set `InteractionAdmissionDecision.effectiveRef`.

### G-022-010 — Network failure disambiguation

EIB MUST distinguish:

```text
valid SC-C semantic envelope with HTTP 503 and status DEFERRED_SC_B_REQUIRED
```

from:

```text
upstream transport failure / unavailable SC-C / invalid or missing response body
```

The latter maps to EIB-level `UPSTREAM_UNAVAILABLE` or equivalent.

### G-022-011 — Tests and regression evidence

Implement unit, HTTP/API, upstream-client and architecture tests sufficient to validate the MIR acceptance criteria.

---

## 7. Non-goals

This MIR MUST NOT implement:

```text
- EIB as co-located SC-C service/controller.
- Direct Java dependency from EIB to com.sovereign.connect.core.*.
- Direct use of ScCoreNorthboundFacade.
- Direct use of SC-C repositories, SQLite adapters, JdbcTemplate, DataSource or Flyway.
- View Composer as a separate service.
- Hub, SApp or Surface implementation.
- Authority / Policy / Identity / Session systems of record.
- Device/endpoint action admission or dispatch.
- Discovery admission.
- SC-B runtime.
- SC-D adapter runtime.
- Outbox dispatcher.
- Live updates.
- SSE.
- GraphQL.
- MCP.
- gRPC / ConnectRPC.
- WebSocket.
- Persistent effective-ref registry.
- Durable EIB admission/audit ledger.
- EIB TCK / conformance harness.
```

---

## 8. MIR decisions

### D-MIR-022-001 — EIB runtime placement

EIB MUST be materialized as a separate runtime boundary.

Preferred placement is a top-level `eib/` Maven project inside the same repository.

Allowed:

```text
same repository
separate Maven project
separate Spring Boot application
HTTP/OpenAPI consumption of SC-C
```

Forbidden:

```text
same SC-C Spring application context
controller/service under SC-C runtime package
imports from com.sovereign.connect.core.*
direct facade injection
```

### D-MIR-022-002 — EIB upstream client

EIB MUST define an explicit upstream client port:

```java
interface EibScNorthboundClient { ... }
```

The implementation SHOULD use Spring `RestClient` or equivalent synchronous HTTP client.

### D-MIR-022-003 — EIB upstream configuration

EIB MUST define configuration for:

```text
sc.eib.northbound.base-url
sc.eib.northbound.timeout-ms
sc.eib.ref-codec.secret
sc.eib.diagnostic-admin.enabled
```

Test defaults MUST be safe and deterministic.

### D-MIR-022-004 — EIB-owned DTOs

EIB MUST mirror upstream wire DTOs in its own package.

Examples:

```text
NorthboundTopologySnapshotDto
NorthboundTopologyVersionViewDto
NorthboundRoomViewDto
NorthboundZoneViewDto
NorthboundDeviceViewDto
NorthboundEndpointViewDto
NorthboundCapabilityViewDto
NorthboundDeviceHealthViewDto
NorthboundEndpointHealthViewDto
NorthboundRuntimeStateViewDto
NorthboundTemporalActViewDto
NorthboundDiagnosticsViewDto
ScEnvelope<T>
ScWarningDto
ScErrorDto
```

These are EIB-owned wire DTOs, not SC-C Java class imports.

### D-MIR-022-005 — Topology snapshot as primary source

EIB SHOULD use:

```text
GET /sc/v1/habitats/{habitatId}/topology
```

as the primary upstream call for `EffectiveHabitatView` construction.

Rooms and zones are derived from the topology snapshot.

Separate `/rooms` and `/zones` calls are not required for the first implementation-oriented slice.

### D-MIR-022-006 — Effective reference codec

EIB MUST generate product-safe effective refs using a deterministic non-reversible codec.

Recommended implementation:

```text
HMAC-SHA256(secret, habitatId + ':' + type + ':' + canonicalId)
Base64 URL-safe token
```

The implementation MUST NOT expose canonical IDs through ordinary effective refs.

### D-MIR-022-007 — Effective ref resolution

Because HMAC refs are non-reversible, `getEffectiveTemporalAct` and `admitTemporalCancellation` MAY resolve refs by list+match for this MIR.

This behavior MUST be documented in the implementation report as known first-slice behavior.

Persistent effective-ref registry remains deferred.

### D-MIR-022-008 — Canonical and provider-native ID gating

EIB MUST suppress from ordinary product views:

```text
canonicalDeviceId
canonicalEndpointId
canonicalTemporalActId
providerDeviceId
providerEndpointId
```

Canonical IDs MAY be present only in diagnostic/admin mode with authorization confirmation.

Provider-native IDs MUST remain metadata and MUST NOT become ordinary product identifiers.

### D-MIR-022-009 — Envelope mapping

EIB MUST preserve original SC-C status, errors, warnings and sources internally.

EIB MAY translate to product-safe status language, but the original SC-C status MUST remain traceable in `CanonicalSubmissionTrace` or internal mapping structures.

### D-MIR-022-010 — TemporalAct create/cancel admission

EIB MUST implement:

```text
admitTemporalSignalRequest
admitTemporalCancellation
```

Both flows must submit to SC-C through HTTP/OpenAPI.

Both flows must distinguish EIB admission from SC-C acceptance/completion.

### D-MIR-022-011 — Upstream failure classification

Transport/network failures, malformed upstream JSON and unavailable SC-C endpoints MUST be represented as EIB upstream failures, not as SC-C semantic statuses.

### D-MIR-022-012 — ACTION-EIB obligations

The following actions from the merged CSA are binding:

```text
ACTION-EIB-001 — Preserve non-co-located runtime placement.
ACTION-EIB-002 — Add EIB upstream configuration.
ACTION-EIB-003 — Add EIB ref-codec and diagnostic configuration.
ACTION-EIB-004 — Implement EIB-owned mirror DTOs.
ACTION-EIB-005 — Suppress provider-native IDs from ordinary product views.
ACTION-EIB-006 — Preserve canonical envelope semantics.
ACTION-EIB-007 — Generate effectiveTemporalActRef after accepted create.
ACTION-EIB-008 — Disambiguate network failure vs semantic SC-C failure.
ACTION-EIB-009 — Add architecture tests.
ACTION-EIB-010 — Document list+match effective ref resolution.
```

---

## 9. Expected implementation surface

Expected new top-level project:

```text
eib/
  pom.xml
  src/main/java/com/sovereign/eib/EibApplication.java
  src/main/resources/application.yml
  src/test/resources/application-test.yml
  src/main/java/com/sovereign/eib/api/**
  src/main/java/com/sovereign/eib/application/**
  src/main/java/com/sovereign/eib/domain/**
  src/main/java/com/sovereign/eib/northbound/**
  src/main/java/com/sovereign/eib/projection/**
  src/main/java/com/sovereign/eib/admission/**
  src/main/java/com/sovereign/eib/diagnostics/**
  src/test/java/com/sovereign/eib/**
```

Expected components:

```text
EibApplication
EibScNorthboundClient
RestClientEibScNorthboundClient
EibScNorthboundClientProperties
EibEffectiveRefCodec
EibRefCodecProperties
EibRequestContext
EibContextResolver
EibPolicyContextPort
PermissiveEibPolicyContextPort
EibEffectiveViewService
EibTemporalActProjectionService
EibInteractionAdmissionService
EibTemporalAdmissionService
EibCanonicalEnvelopeMapper
EibDiagnosticsService
Eib API controllers
```

Expected DTO families:

```text
EibResponse<T>
EibError
EibWarning
EffectiveHabitatView
EffectiveRoomView
EffectiveZoneView
EffectiveDeviceView
EffectiveEndpointView
EffectiveCapabilityAffordance
EffectiveTemporalActView
EffectiveDiagnosticsView
InteractionAdmissionDecision
CanonicalSubmissionTrace
ScEnvelope<T>
ScErrorDto
ScWarningDto
Northbound*Dto mirror records
```

---

## 10. Upstream SC-C route surface

EIB initial implementation slice consumes:

```text
GET  /sc/v1/habitats/{habitatId}/topology
GET  /sc/v1/habitats/{habitatId}/topology/version
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/health
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/health
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/diagnostics
GET  /sc/v1/habitats/{habitatId}/temporal-acts?mode=ACTIVE&maxResults=50
GET  /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}
POST /sc/v1/habitats/{habitatId}/temporal-acts
POST /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
```

Routes available but not required for first EIB projection:

```text
GET /sc/v1/habitats/{habitatId}/rooms
GET /sc/v1/habitats/{habitatId}/zones
GET /sc/v1/habitats/{habitatId}/locations/{roomOrZoneId}/devices
GET /sc/v1/habitats/{habitatId}/locations/{roomOrZoneId}/endpoints
GET /sc/v1/habitats/{habitatId}/temporal/runtime-status
GET /sc/v1/habitats/{habitatId}/recovery/status
```

---

## 11. EIB API surface

The execution package MUST define exact route names. The expected initial API surface is:

```text
GET  /eib/v1/habitats/{habitatId}/effective-view
GET  /eib/v1/habitats/{habitatId}/devices/{effectiveDeviceRef}
GET  /eib/v1/habitats/{habitatId}/endpoints/{effectiveEndpointRef}
GET  /eib/v1/habitats/{habitatId}/temporal-acts
GET  /eib/v1/habitats/{habitatId}/temporal-acts/{effectiveTemporalActRef}
POST /eib/v1/habitats/{habitatId}/temporal-acts/signal
POST /eib/v1/habitats/{habitatId}/temporal-acts/{effectiveTemporalActRef}/cancel
GET  /eib/v1/habitats/{habitatId}/diagnostics
```

All routes MUST return:

```text
EibResponse<T>
```

as the HTTP body.

---

## 12. Test obligations

The execution package MUST map acceptance criteria to concrete tests.

Expected test families:

### 12.1 Unit tests

```text
EibEffectiveRefCodecTest
EibEffectiveViewMapperTest
EibCanonicalEnvelopeMapperTest
EibTemporalAdmissionServiceTest
EibTemporalActProjectionServiceTest
EibContextResolverTest
```

Required coverage:

```text
- effective refs are deterministic and non-canonical.
- provider-native IDs do not appear in ordinary view JSON.
- canonical IDs appear only in diagnostic/admin mode.
- all twelve SC-C statuses are preserved internally.
- error.source and warning.source are preserved.
- ACCEPTED Signal TemporalAct create generates effectiveTemporalActRef.
- list+match resolution is documented and tested for temporal refs.
```

### 12.2 HTTP/API tests

```text
EibEffectiveViewControllerTest
EibTemporalActControllerTest
EibDiagnosticsControllerTest
```

Required coverage:

```text
- all /eib/v1 routes return EibResponse<T>.
- EffectiveHabitatView carries a non-empty sourceTopologyVersion from the SC-C topology snapshot.
- EffectiveHabitatView.temporalActs is populated when SC-C returns temporal acts.
- ordinary responses omit canonical IDs and provider-native IDs.
- diagnostic/admin responses may include canonical IDs when authorized.
- TemporalAct create returns InteractionAdmissionDecision with effectiveRef after SC-C ACCEPTED.
- TemporalAct cancel resolves effectiveRef and posts canonical cancel to SC-C.
```

### 12.3 Upstream client tests

Use `MockRestServiceServer` or equivalent.

Required coverage:

```text
- SC-C topology snapshot success.
- SC-C diagnostics success with UNKNOWN_PENDING_NORMALIZATION visible.
- valid SC-C envelope with DEFERRED_SC_B_REQUIRED and HTTP 503 remains semantic SC-C response.
- HTTP 503 with missing/non-JSON body maps to UPSTREAM_UNAVAILABLE.
- connection failure maps to UPSTREAM_UNAVAILABLE.
- malformed JSON maps to UPSTREAM_UNAVAILABLE.
```

### 12.4 Architecture tests

Required coverage:

```text
- EIB does not import com.sovereign.connect.core.*.
- EIB does not import ScCoreNorthboundFacade or DefaultScCoreNorthboundFacade.
- EIB does not import SC-C persistence adapters, JdbcTemplate, DataSource or Flyway.
- EIB does not introduce WebFlux, GraphQL, MCP, gRPC, ConnectRPC, NATS, JetStream, SSE or WebSocket.
- If EIB is placed under top-level eib/, SC-C boundary tests remain unchanged.
```

---

## 13. Acceptance criteria

### Governance and placement

AC-022-001 — MIR identifies `MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001` as `MU-022`.

AC-022-002 — Implementation creates a separate EIB runtime boundary, preferably under top-level `eib/`.

AC-022-003 — EIB is not registered in the SC-C Spring application context.

AC-022-004 — EIB does not import `com.sovereign.connect.core.*`.

AC-022-005 — EIB does not inject or reference `ScCoreNorthboundFacade`.

AC-022-006 — EIB consumes SC-C only through HTTP/OpenAPI base URL configuration.

### Upstream client and DTOs

AC-022-007 — `EibScNorthboundClient` or equivalent explicit upstream client boundary exists.

AC-022-008 — Upstream client uses EIB-owned mirror DTOs and does not import SC-C Northbound Java DTOs.

AC-022-009 — `sc.eib.northbound.base-url` and `sc.eib.northbound.timeout-ms` are configurable with test defaults.

AC-022-010 — `getTopologyVersion` uses a structured `NorthboundTopologyVersionViewDto`, not a plain `String`.

AC-022-011 — Rooms and zones for `EffectiveHabitatView` are derived from the topology snapshot returned by `GET /sc/v1/habitats/{habitatId}/topology`. Separate `/rooms` and `/zones` upstream calls are not required.

AC-022-012 — `ScEnvelope<T>` preserves `status`, `payload`, `warnings` and `error`.

AC-022-013 — `ScErrorDto` and `ScWarningDto` preserve `code`, `message` and `source`.

### Effective projection

AC-022-014 — `EffectiveHabitatView` is produced from SC-C topology snapshot without mutating SC-C.

AC-022-014a — `EffectiveHabitatView` includes `sourceTopologyVersion` derived from the SC-C topology snapshot `topologyVersionValue`. It must not be null or empty when SC-C returns a valid topology snapshot.

AC-022-015 — `EffectiveRoomView` and `EffectiveZoneView` are implemented.

AC-022-016 — `EffectiveDeviceView` and `EffectiveEndpointView` use effective refs, not canonical IDs, in ordinary mode.

AC-022-017 — `EffectiveCapabilityAffordance` is implemented for capability exposure at effective-view level.

AC-022-018 — `EffectiveDiagnosticsView` preserves canonical uncertainty product-safely.

AC-022-018a — `EffectiveHabitatView.temporalActs` is populated from `listEffectiveTemporalActs` using the SC-C `GET /temporal-acts` route, with default mode `ACTIVE` for the initial implementation slice. It must not remain silently empty when SC-C returns temporal acts.

AC-022-019 — EIB does not expose `providerDeviceId` or `providerEndpointId` in ordinary product views.

AC-022-020 — Ordinary responses do not expose `canonicalDeviceId`, `canonicalEndpointId` or `canonicalTemporalActId`.

AC-022-021 — Diagnostic/admin responses expose canonical IDs only when diagnostic/admin context is explicitly authorized.

### Envelope and status preservation

AC-022-022 — All twelve SC-C statuses are preserved internally.

AC-022-023 — `UNSUPPORTED_PROFILE` is not treated as success.

AC-022-024 — `DEFERRED_SC_B_REQUIRED` is not treated as success.

AC-022-025 — `UNKNOWN_PENDING_NORMALIZATION` remains visible and is not fabricated as known.

AC-022-026 — `INTERNAL_ERROR` does not leak internal details to product clients.

AC-022-027 — `NOT_FOUND` may be translated product-safely, but canonical cause remains traceable internally.

### Effective refs

AC-022-028 — Effective refs are deterministic for the same habitat/type/canonical ID under the same secret.

AC-022-029 — Effective refs do not reveal canonical IDs.

AC-022-030 — `eib.room`, `eib.zone`, `eib.device`, `eib.endpoint` and `eib.temporal` ref families are supported.

AC-022-031 — Temporal ref resolution by list+match is implemented or explicitly deferred with candidate-blocking rationale.

AC-022-032 — The implementation report documents list+match as known first-slice behavior if used.

### TemporalAct observation and admission

AC-022-033 — `listEffectiveTemporalActs` consumes SC-C temporal act list route and returns effective temporal views.

AC-022-034 — `getEffectiveTemporalAct` resolves effective temporal ref and returns the matching effective temporal view or product-safe not-found.

AC-022-035 — `admitTemporalSignalRequest` submits to SC-C `POST /temporal-acts`.

AC-022-036 — If SC-C returns `ACCEPTED` with `NorthboundTemporalActView`, EIB generates `effectiveTemporalActRef` and sets `InteractionAdmissionDecision.effectiveRef`.

AC-022-037 — `admitTemporalCancellation` resolves effective temporal ref and submits to SC-C cancel route.

AC-022-038 — EIB distinguishes `ADMITTED` from SC-C accepted/completed/provider-side effect.

AC-022-039 — EIB does not implement device/endpoint action admission.

AC-022-040 — EIB does not implement discovery admission.

### Network and failure handling

AC-022-041 — Network connection failure maps to EIB upstream-unavailable status/error.

AC-022-042 — Malformed upstream JSON maps to EIB upstream-unavailable status/error.

AC-022-043 — HTTP 503 with valid SC-C `ScEnvelope` body and `DEFERRED_SC_B_REQUIRED` is treated as semantic SC-C response.

AC-022-044 — HTTP 503 with missing/non-JSON body is treated as upstream transport failure.

### Boundaries and negative scope

AC-022-045 — EIB does not import SC-C repositories, persistence adapters, `JdbcTemplate`, `DataSource` or `Flyway`.

AC-022-046 — EIB does not introduce WebFlux, GraphQL, MCP, gRPC, ConnectRPC, NATS, JetStream, SSE or WebSocket.

AC-022-047 — EIB does not implement live updates.

AC-022-048 — EIB does not implement Authority/Policy/Identity/Session systems of record.

AC-022-049 — EIB does not implement persistent effective-ref registry.

AC-022-050 — EIB does not implement durable audit/admission ledger.

### Evidence and reports

AC-022-051 — Test evidence records EIB project test count, failures, errors and skipped tests.

AC-022-052 — Architecture tests confirm non-co-location and absence of direct SC-C imports.

AC-022-053 — Implementation report records all retained debts.

AC-022-054 — Implementation report records any deviation from preferred `eib/` placement; such deviation requires explicit governance patch before L4 closure.

AC-022-055 — Implementation report records whether `DEBT-HTTP-004` is closed for the EIB initial implementation slice or remains partially open.

---

## 14. Retained debt after MU-022

Inherited HTTP debts expected to remain unless explicitly closed by implementation evidence:

```text
DEBT-HTTP-001 — SSE remains SSE-P0 / deferred.
DEBT-HTTP-002 — local-trusted only; no full auth/authz.
DEBT-HTTP-003 — Swagger UI / static YAML deferred.
DEBT-HTTP-005 — gRPC/ConnectRPC downstream.
DEBT-HTTP-006 — MCP adapter separate track.
DEBT-HTTP-007 — ACTIVE mode ignores maxResults in SC-C facade behavior.
DEBT-HTTP-008 — HTTP TCK absent.
```

`DEBT-HTTP-004` disposition:

```text
DEBT-HTTP-004 — EIB runtime requires its own contract/design/descent before implementation.
Expected MU-022 disposition:
  closed for EIB initial implementation slice if MU-022 implementation passes L4;
  not closed for full EIB product readiness, EIB TCK, Authority/Policy integration or View Composer service readiness.
```

New / carried EIB debts:

```text
DEBT-EIB-001 — EIB implementation not yet attempted until MU-022 executes.
DEBT-EIB-002 — Authority/Policy/Identity/Session integration remains a context-port stub.
DEBT-EIB-003 — Device/endpoint action admission remains SC-B-gated and deferred.
DEBT-EIB-004 — Discovery admission remains SC-B-gated and deferred.
DEBT-EIB-005 — Live updates remain deferred.
DEBT-EIB-006 — GraphQL facade remains deferred.
DEBT-EIB-007 — MCP AI/tool/admin facade remains separate track.
DEBT-EIB-008 — gRPC/ConnectRPC remains downstream.
DEBT-EIB-009 — EIB conformance/TCK remains absent.
DEBT-EIB-010 — Durable audit/admission ledger remains deferred.
DEBT-EIB-011 — Persistent effective-ref registry remains deferred; list+match accepted for initial implementation slice.
DEBT-EIB-012 — Product-facing Surface UX remains outside EIB seed.
```

---

## 15. Risks

### RISK-022-001 — Accidental co-location

Risk:

```text
Implementation places EIB inside the SC-C Spring Boot application context for convenience.
```

Mitigation:

```text
MIR requires top-level separate EIB runtime project unless governance explicitly patches placement.
Architecture tests must reject SC-C core imports.
```

### RISK-022-002 — DTO leakage

Risk:

```text
EIB imports SC-C Northbound Java DTOs instead of mirroring wire DTOs.
```

Mitigation:

```text
EIB owns wire DTO mirrors. Architecture tests scan for forbidden imports.
```

### RISK-022-003 — Provider-native ID leakage

Risk:

```text
providerDeviceId/providerEndpointId from SC-C wire DTOs leak into ordinary product views.
```

Mitigation:

```text
Explicit mapper tests and JSON serialization tests must assert absence.
```

### RISK-022-004 — Canonical status flattening

Risk:

```text
EIB collapses statuses into generic success/failure and loses SC-C canonical cause.
```

Mitigation:

```text
Envelope mapper tests preserve all twelve statuses and source fields.
```

### RISK-022-005 — Effective ref resolution cost

Risk:

```text
Non-reversible effective refs require list+match resolution.
```

Mitigation:

```text
Accepted for initial implementation slice; persistent effective-ref registry retained as debt.
```

### RISK-022-006 — Network vs semantic 503 confusion

Risk:

```text
SC-C semantic DEFERRED_SC_B_REQUIRED also uses HTTP 503.
```

Mitigation:

```text
Client tests distinguish valid ScEnvelope body from transport failure.
```

---

## 16. Branch and commit suggestion

Suggested branch:

```text
feat/sc-eib-mir-022-effective-interaction-boundary-seed
```

Suggested implementation commit:

```text
feat(eib): add effective interaction boundary seed
```

Suggested evidence commit:

```text
docs(mir-022): record eib seed implementation evidence
```

---

## 17. Final dictum

```text
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001 v0.2.0-candidate is accepted as candidate.

The execution package may be prepared under docs/mir/mir-022/.

This MIR authorizes a future implementation only after the external execution package is accepted and only for the EIB initial
implementation slice defined above. It does not authorize EIB co-location with
SC-C, direct Java facade access, device/endpoint action admission, discovery,
live updates, GraphQL, MCP, gRPC/ConnectRPC, WebSocket, SSE, SC-B runtime,
SC-D adapters, View Composer as a separate service, Hub, SApp, Surfaces or
Authority/Policy systems of record.
```
