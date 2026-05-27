# SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001

## Effective Interaction Boundary Implementation Design

```text
Document ID:  SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001
Title:        Effective Interaction Boundary Implementation Design
Version:      v0.2.0-candidate
Status:       Candidate / PDR candidate consumed / SDD review corrections applied / CSA required before MIR
Date:         2026-05-26
Corpus:       Sovereign Connect
Type:         SDD
Plane:        SC-X / product-facing membrane
Owner:        Connect Architecture / SC
Scope:        Defines an implementable design for the initial EIB implementation slice as a non-co-located service that consumes the validated SC-C Northbound HTTP/OpenAPI binding, composes effective read projection, admits SC-C-local TemporalAct interactions, and preserves canonical uncertainty without absorbing SC-C, SC-B, SC-D, Authority, Policy, Hub, Surface, MCP, GraphQL or UX responsibilities.
```

---

## Changelog v0.2.0-candidate

Candidate promotion after SDD review.

This version:

1. Replaces SDD-level maturity wording such as “seed” with implementation-neutral terms such as “initial implementation slice/profile”, while preserving MIR/MU identifiers that legitimately carry `SEED`.
2. Corrects `getTopologyVersion(...)` from `ScEnvelope<String>` to `ScEnvelope<NorthboundTopologyVersionViewDto>`.
3. Clarifies that rooms and zones are derived from the topology snapshot in the initial implementation slice; separate room/zone upstream calls are not required.
4. Adds `EffectiveRoomView` and `EffectiveZoneView` candidate shapes.
5. Requires `InteractionAdmissionDecision.effectiveRef` to be populated from the newly created `EffectiveTemporalActRef` when SC-C accepts a Signal TemporalAct creation.
6. Clarifies that `/eib/v1` HTTP routes return `EibResponse<T>` as their body.

## Changelog v0.1.0-draft

Initial SDD opened after `PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.3.0-candidate`.

This version:

1. Defines EIB as a separate runtime/service boundary, not co-located with SC-C.
2. Requires EIB to consume SC-C through Northbound HTTP/OpenAPI and never through the in-process `ScCoreNorthboundFacade`.
3. Defines the initial implementable EIB implementation slice around effective read projection plus SC-C-local TemporalAct observation/admission/cancellation.
4. Introduces EIB runtime components, ports, adapters and DTO families.
5. Specifies an upstream HTTP client boundary over SC-C Northbound.
6. Defines effective reference generation and resolution without exposing canonical IDs to ordinary product surfaces.
7. Defines response-envelope preservation and canonical-status traceability.
8. Defines diagnostic/admin gating for canonical IDs.
9. Defines TemporalAct admission mapping to SC-C `createSignalTemporalAct` and `cancelTemporalAct`.
10. Keeps device/endpoint action intent, discovery intent, live updates, GraphQL, MCP, gRPC/ConnectRPC, WebSocket, SSE, Authority/Policy implementation and external UX outside the initial implementation slice.
11. Declares post-SDD CSA obligations before MIR.
12. Defines acceptance criteria for the future MIR.

---

## Depends on

- `Sovereign Connect — Curator & Descent Instructions`
- `RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate`
- `RFC-SOV-TOPOLOGY-BASE-PROJECTION-SPLIT-001`
- `ADR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-draft`
- `PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.3.0-candidate`
- `ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-BINDING-001 v0.1.1-draft`
- `SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001 v0.2.0-candidate / accepted`
- `MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001 v1.0.0-accepted`
- `PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.16-draft`
- `INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.22-draft`
- `SYNC-SOV-SC-MU-BACKLOG-001 v0.1.23-draft`

## Related

- `PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001 v0.1.1-draft`
- `PDR-SOV-SC-HUB-SC-BOUNDARY-001`, pending
- `PDR-SOV-SC-TOPOLOGY-PROJECTION-BOUNDARY-001`, pending / may be superseded by EIB/VC work
- `PDR-SOV-SC-DIAGNOSTIC-ADMIN-BOUNDARY-001`, pending
- `PDR-SOV-SC-MCP-FACADE-001`, pending / separate AI-tool/admin exposure track
- `SDD-SOV-SC-C-NORTHBOUND-GRPC-CONNECTRPC-001`, future downstream option
- `TCK-SOV-SC-EIB-CONFORMANCE-001`, future

---

# 0. Purpose

This SDD defines how to implement the initial Effective Interaction Boundary implementation slice.

The initial EIB implementation slice consumes the validated SC-C Northbound HTTP/OpenAPI binding and produces context-sensitive effective interaction outputs for downstream product clients.

SC-C now exposes canonical truth externally through HTTP/OpenAPI. EIB turns canonical truth into effective, contextual, product-safe interaction without changing canonical ownership.

This SDD is implementation-oriented, but it does **not** authorize implementation. A post-SDD CSA, MIR, execution package and implementation evidence remain required.

---

# 1. Design thesis

Canonical formula:

```text
SC-C owns canonical state and canonical request lifecycle.
SC-C Northbound HTTP/OpenAPI exposes canonical truth across a process boundary.
EIB consumes SC-C Northbound HTTP/OpenAPI.
EIB composes effective visibility, operability and admission.
Hub, SApp and Surfaces consume EIB, not SC-C.
Authority, Policy, Identity and Session are context inputs, not owned by EIB.
```

EIB is not a wrapper around `ScCoreNorthboundFacade`.

EIB is a product-facing membrane with two initial implementation responsibilities:

```text
Read side:
  Build EffectiveHabitatView and related effective views from SC-C Northbound responses.

Admission side:
  Admit or reject SC-C-local Signal TemporalAct create/cancel requests and submit admitted requests through SC-C Northbound HTTP/OpenAPI.
```

EIB MUST preserve canonical uncertainty. It may translate it for product clients, but it must not erase it internally.

---

# 2. Runtime placement and topology

## 2.1 Non-co-located service

EIB is not co-located with SC-C.

Initial runtime topology:

```text
Product client / Hub / SApp / Surface
  -> EIB service API
  -> EIB upstream SC-C Northbound client
  -> SC-C Northbound HTTP/OpenAPI
  -> ScCoreNorthboundFacade
  -> SC-C canonical state
```

Rules:

```text
EIB MUST NOT import ScCoreNorthboundFacade.
EIB MUST NOT import SC-C repositories.
EIB MUST NOT import SC-C services.
EIB MUST NOT import SC-C persistence adapters.
EIB MUST NOT use in-process Java calls to SC-C.
EIB MUST consume SC-C over HTTP/OpenAPI.
```

## 2.2 Repository placement

The post-SDD CSA MUST decide the concrete codebase placement.

Allowed implementation placements:

```text
Option A — same monorepo, separate package/module:
  useful for initial implementation speed;
  still enforces runtime separation by HTTP client only.

Option B — separate service repository:
  cleaner runtime boundary;
  higher initial operational cost.
```

Implementation may be in the same monorepo for initial implementation convenience only if the boundary tests prove that EIB does not call SC-C internals.

Preferred initial implementation package/module names if implemented in the same repository:

```text
com.sovereign.connect.eib
com.sovereign.connect.eib.api
com.sovereign.connect.eib.application
com.sovereign.connect.eib.domain
com.sovereign.connect.eib.northbound
com.sovereign.connect.eib.projection
com.sovereign.connect.eib.admission
com.sovereign.connect.eib.diagnostics
```

Forbidden package dependency:

```text
com.sovereign.connect.eib.* -> com.sovereign.connect.core.*
```

The only permitted SC-C dependency is over the configured HTTP base URL and its JSON/OpenAPI contract.

---

# 3. Technology posture

## 3.1 Initial implementation runtime

Recommended initial implementation stack:

```text
Java 21
Spring Boot
Spring Web / MVC
Jackson with JavaTimeModule
HTTP client boundary using Spring RestClient or equivalent
JUnit + Spring test utilities
```

Rationale:

```text
MU-021 already validated Spring MVC / HTTP/OpenAPI for SC-C Northbound.
The initial EIB implementation slice should minimize technology divergence.
RestClient keeps the seed synchronous and simple.
WebClient/reactive runtime is not required for the initial implementation slice.
```

## 3.2 Upstream SC-C client strategy

Decision `D-SDD-EIB-001`:

```text
The initial EIB implementation slice SHOULD use an explicit EibScNorthboundClient port and an HTTP adapter implementation.
```

Candidate interface:

```java
interface EibScNorthboundClient {
    ScEnvelope<NorthboundTopologySnapshotDto> getTopologySnapshot(String habitatId);
    ScEnvelope<NorthboundTopologyVersionViewDto> getTopologyVersion(String habitatId);
    ScEnvelope<NorthboundDeviceViewDto> getDevice(String habitatId, String deviceId);
    ScEnvelope<NorthboundEndpointViewDto> getEndpoint(String habitatId, String endpointId);
    ScEnvelope<List<NorthboundDeviceViewDto>> listDevices(String habitatId);
    ScEnvelope<List<NorthboundEndpointViewDto>> listEndpoints(String habitatId);
    ScEnvelope<NorthboundDeviceHealthViewDto> getDeviceHealth(String habitatId, String deviceId);
    ScEnvelope<NorthboundEndpointHealthViewDto> getEndpointHealth(String habitatId, String endpointId);
    ScEnvelope<NorthboundRuntimeStateViewDto> getDeviceRuntimeState(String habitatId, String deviceId);
    ScEnvelope<NorthboundRuntimeStateViewDto> getEndpointRuntimeState(String habitatId, String endpointId);
    ScEnvelope<NorthboundDiagnosticsViewDto> getDiagnostics(String habitatId);
    ScEnvelope<List<NorthboundTemporalActViewDto>> listTemporalActs(String habitatId, TemporalActMode mode, Integer maxResults);
    ScEnvelope<NorthboundTemporalActViewDto> getTemporalAct(String habitatId, String temporalActId);
    ScEnvelope<NorthboundTemporalActViewDto> createSignalTemporalAct(String habitatId, EibCreateSignalTemporalActUpstreamRequest request);
    ScEnvelope<NorthboundTemporalActViewDto> cancelTemporalAct(String habitatId, String temporalActId, EibCancelTemporalActUpstreamRequest request);
}
```

Candidate topology-version DTO shape:

```java
record NorthboundTopologyVersionViewDto(
    String value,
    String scopeId,
    String scopeType,
    String habitatId
) {}
```

Rules:

```text
EIB MUST NOT treat topologyVersion as a bare String in its upstream client boundary.
The minimum required field for effective view generation is `value`, but the DTO must preserve the upstream structure so `scopeId`, `scopeType` and `habitatId` remain available for diagnostics and traceability.
```

This is conceptual shape. Exact DTO names and route coverage must be verified by the post-SDD CSA.

## 3.3 Generated OpenAPI client disposition

Decision `D-SDD-EIB-002`:

```text
The initial implementation slice MAY use the SC-C OpenAPI document as a contract reference.
The initial implementation slice MUST NOT require generated client code unless the CSA confirms generation setup and versioning strategy.
```

Preferred initial implementation approach:

```text
Handwritten, narrow HTTP adapter over the routes required by the initial implementation slice.
```

Rationale:

```text
Generated OpenAPI clients introduce build-generation, naming and versioning complexity.
The initial implementation slice needs a small, auditable surface.
```

A generated client may be introduced in a later hardening MU or TCK/conformance track.

---

# 4. Scope

## 4.1 In scope for the initial SDD/MIR implementation slice

```text
Profile EIB-A — Effective read projection:
  EffectiveHabitatView.
  EffectiveRoomView / EffectiveZoneView.
  EffectiveDeviceView.
  EffectiveEndpointView.
  EffectiveCapabilityAffordance.
  Effective health summary.
  Effective device/endpoint runtime observation.
  Effective diagnostics.

Profile EIB-C partial — TemporalAct observation:
  listEffectiveTemporalActs.
  getEffectiveTemporalAct.
  EffectiveTemporalActView.

Profile EIB-B partial — SC-C-local request admission:
  admitTemporalSignalRequest.
  admitTemporalCancellation.

Boundary and traceability:
  Canonical response vocabulary preservation.
  Effective refs and handles.
  Diagnostic/admin canonical ID gating.
  Submission trace for TemporalAct create/cancel.
```

## 4.2 Explicitly out of scope

```text
Device/endpoint action command execution.
Discovery request execution.
SC-B runtime.
SC-D adapter runtime.
Outbox dispatcher.
Provider-side verification.
GraphQL.
MCP.
gRPC / ConnectRPC.
WebSocket.
SSE live updates.
Authority / Policy system-of-record.
Identity / Session system-of-record.
Hub memory.
Surface UX rendering.
View Composer as separate deployable service.
HTTP TCK / conformance harness.
```

---

# 5. Component model

## 5.1 High-level components

```text
EibApiController
  Receives product-facing HTTP/API calls.

EibRequestContextResolver
  Builds EibRequestContext from headers/request metadata.

EibEffectiveViewService
  Orchestrates effective read projection.

EibTemporalActProjectionService
  Projects SC-C TemporalActs into EffectiveTemporalActView.

EibInteractionAdmissionService
  Admits/rejects product-facing requests.

EibTemporalAdmissionService
  Maps admitted temporal signal/cancel requests to SC-C Northbound HTTP/OpenAPI.

EibScNorthboundClient
  Port for SC-C Northbound HTTP/OpenAPI consumption.

HttpEibScNorthboundClient
  HTTP adapter implementing EibScNorthboundClient.

EibEffectiveRefCodec
  Creates/verifies effective refs and handles.

EibPolicyContextPort
  Reads context/authorization/admission signals without owning Policy/Authority.

EibCanonicalEnvelopeMapper
  Preserves and maps ScNorthboundResponse vocabulary.

EibDiagnosticsService
  Builds product-safe diagnostics from SC-C diagnostics and EIB-local state.
```

## 5.2 Dependency direction

Allowed dependencies:

```text
api -> application
application -> domain
application -> northbound port
application -> context/policy ports
northbound http adapter -> configured SC-C HTTP endpoint
diagnostics -> northbound port + local health
```

Forbidden dependencies:

```text
eib -> core.northbound Java package
eib -> SC-C repositories
eib -> SC-C persistence adapters
eib -> SC-B runtime
eib -> SC-D adapters
eib -> provider SDKs
eib -> Surface UI rendering libraries
eib -> Hub conversation memory
eib -> Authority/Policy storage internals
```

---

# 6. EIB API surface for the initial implementation slice

The initial implementation slice MAY expose HTTP endpoints for downstream product clients. Exact route names may be adjusted by CSA/MIR, but the initial implementation slice should keep a narrow, stable route family.

Recommended route prefix:

```text
/eib/v1
```

Response body rule:

```text
All `/eib/v1` routes return `EibResponse<T>` as the HTTP body.
The route notation below shows the payload type `T` for brevity.
For example, `-> EffectiveHabitatView` means `EibResponse<EffectiveHabitatView>`.
```

## 6.1 Effective read routes

```text
GET /eib/v1/habitats/{habitatId}/effective-view
  -> EffectiveHabitatView

GET /eib/v1/habitats/{habitatId}/devices
  -> List<EffectiveDeviceView>

GET /eib/v1/habitats/{habitatId}/devices/{effectiveDeviceRef}
  -> EffectiveDeviceView

GET /eib/v1/habitats/{habitatId}/endpoints
  -> List<EffectiveEndpointView>

GET /eib/v1/habitats/{habitatId}/endpoints/{effectiveEndpointRef}
  -> EffectiveEndpointView

GET /eib/v1/habitats/{habitatId}/diagnostics
  -> EffectiveDiagnosticsView
```

## 6.2 TemporalAct routes

```text
GET /eib/v1/habitats/{habitatId}/temporal-acts?mode=ACTIVE&maxResults=50
  -> List<EffectiveTemporalActView>

GET /eib/v1/habitats/{habitatId}/temporal-acts/{effectiveTemporalActRef}
  -> EffectiveTemporalActView

POST /eib/v1/habitats/{habitatId}/temporal-acts/signal
  -> InteractionAdmissionDecision + CanonicalSubmissionTrace

POST /eib/v1/habitats/{habitatId}/temporal-acts/{effectiveTemporalActRef}/cancel
  -> InteractionAdmissionDecision + CanonicalSubmissionTrace
```

## 6.3 Diagnostics/admin mode

Diagnostic/admin mode MAY expose additional fields, including canonical IDs, only if the request context explicitly enables diagnostic/admin mode and authorization context confirms access.

Recommended initial implementation signal:

```text
X-SC-Diagnostic-Mode: true
```

This header alone is insufficient. It must be combined with an authorization/context decision.

Initial implementations without a real authorization provider MUST default to:

```text
ordinary product context
canonical IDs hidden
operatorDetails omitted
```

---

# 7. Request context

## 7.1 EibRequestContext

Candidate record:

```java
record EibRequestContext(
    String contextRef,
    String actorRef,
    String surfaceRef,
    String locale,
    boolean diagnosticModeRequested,
    boolean diagnosticAdminAuthorized,
    Instant requestedAt,
    String clientRequestRef
) {}
```

Rules:

```text
contextRef is opaque to EIB.
actorRef is opaque and must not become canonical identity authority.
surfaceRef is delivery/rendering context, not topology authority.
diagnosticModeRequested does not imply diagnosticAdminAuthorized.
clientRequestRef is product-side trace input, not SC-C idempotency authority by itself.
```

## 7.2 Context resolution

Initial implementation resolver MAY derive context from headers:

```text
X-SC-Context-Ref
X-SC-Actor-Ref
X-SC-Surface-Ref
X-SC-Locale
X-SC-Diagnostic-Mode
X-SC-Client-Request-Ref
```

Rules:

```text
Missing context must not grant diagnostic/admin access.
Missing actor/policy context should result in conservative defaults.
EIB must not infer personal identity semantics from opaque refs.
```

## 7.3 Policy/authorization port

Candidate port:

```java
interface EibPolicyContextPort {
    EibContextDecision evaluate(EibRequestContext context, EibContextSubject subject);
}
```

Initial implementation:

```text
No Authority/Policy system-of-record.
No user database.
No permission model beyond deterministic initial implementation behavior.
Diagnostic/admin access defaults to false unless explicitly provided by a test/configurable stub.
Visibility defaults may be permissive for non-sensitive canonical topology, but canonical IDs remain hidden.
```

---

# 8. Effective reference and handle design

## 8.1 Problem

Ordinary product surfaces must not receive canonical IDs as their normal interaction handles.

EIB needs effective refs that:

```text
hide canonical IDs from ordinary surfaces;
are stable enough for a request cycle;
can be resolved by EIB;
can detect stale views or invalid handles;
are deterministic enough for tests.
```

## 8.2 EffectiveRefCodec

Decision `D-SDD-EIB-003`:

```text
The initial implementation slice SHOULD use deterministic effective refs derived from canonical IDs by a server-side secret and habitat/context scope.
```

Candidate ref families:

```text
eib.room.<token>
eib.zone.<token>
eib.device.<token>
eib.endpoint.<token>
eib.temporal.<token>
eib.action.<token>
```

Token generation:

```text
token = base64url(truncate(HMAC_SHA256(secret, habitatId + type + canonicalId + optionalContextScope)))
```

The token is not reversible.

## 8.3 EffectiveRef resolution

Because HMAC refs are not reversible, EIB resolves them by rebuilding an index from current SC-C observations.

Resolution flow:

```text
1. EIB fetches the relevant SC-C canonical view/list.
2. EIB computes effective refs for visible entities.
3. EIB matches the supplied effective ref.
4. If no match exists, reject as invalid/stale/not-visible according to context.
```

This avoids exposing canonical IDs and avoids adding persistence to the initial implementation slice.

## 8.4 EffectiveActionHandle

Effective action handles are opaque request admission handles.

Candidate fields:

```java
record EffectiveActionHandle(
    String handleId,
    Instant issuedAt,
    Instant expiresAt,
    String sourceTopologyVersion,
    String targetScope,
    String capabilityKind,
    Map<String, Object> constraints
) {}
```

Rules:

```text
Handle does not replace SC-C target validation.
Handle does not prove permanent policy.
Handle must be rejected if stale, expired, mismatched or invalid.
Device/endpoint action handles are reserved in the initial implementation slice because action intent is SC-B-gated.
Temporal cancellation handles may be issued for visible TemporalActs.
```

---

# 9. Upstream SC-C Northbound consumption

## 9.1 Base URL

Configuration:

```yaml
sc:
  eib:
    northbound:
      base-url: http://localhost:8080/sc/v1
      timeout-ms: 2000
      max-retries: 0
```

Initial implementation defaults:

```text
synchronous requests
short timeout
no automatic semantic retry
no circuit breaker required in the initial implementation slice
```

Network failure maps to EIB upstream-unavailable status. It must not be treated as SC-C semantic failure.

## 9.2 Upstream routes consumed by the initial implementation slice

The SDD expects the following SC-C HTTP/OpenAPI routes to exist from MU-021:

```text
GET  /sc/v1/habitats/{habitatId}/topology
GET  /sc/v1/habitats/{habitatId}/topology/version
GET  /sc/v1/habitats/{habitatId}/devices
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/health
GET  /sc/v1/habitats/{habitatId}/devices/{deviceId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/endpoints
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/health
GET  /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/runtime-state
GET  /sc/v1/habitats/{habitatId}/temporal-acts?mode=ACTIVE&maxResults=50
GET  /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}
POST /sc/v1/habitats/{habitatId}/temporal-acts
POST /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
GET  /sc/v1/habitats/{habitatId}/diagnostics
```

Room/zone sourcing decision:

```text
The initial implementation slice derives `EffectiveRoomView` and `EffectiveZoneView` from the full topology snapshot returned by `GET /sc/v1/habitats/{habitatId}/topology`.
Separate upstream calls to `/rooms` and `/zones` are not required for the initial implementation slice.
A later implementation may use separate room/zone endpoints for optimization or partial fetch behavior, but that is not required by this SDD.
```

The post-SDD CSA MUST verify exact route names against MU-021 implementation.

## 9.3 Upstream response shape

EIB consumes the canonical Northbound body as an envelope.

Candidate internal representation:

```java
record ScEnvelope<T>(
    ScStatus status,
    T payload,
    List<ScWarning> warnings,
    ScError error
) {}

record ScError(String code, String message, String source) {}
record ScWarning(String code, String message, String source) {}
```

Rules:

```text
EIB MUST preserve all twelve upstream ScNorthboundStatus values internally.
EIB MUST preserve error.source.
EIB MUST preserve warning.source.
EIB MUST NOT flatten upstream body into payload-only DTOs.
EIB MAY translate to product-safe statuses only after preserving canonical trace.
```

---

# 10. Canonical status mapping

## 10.1 Internal SC status vocabulary

The initial implementation slice must model all SC-C Northbound statuses:

```text
OK
CREATED
ACCEPTED
CANCELLED
NOT_FOUND
INVALID_REQUEST
INVALID_CANONICAL_ID
VALIDATION_ERROR
UNSUPPORTED_PROFILE
DEFERRED_SC_B_REQUIRED
UNKNOWN_PENDING_NORMALIZATION
INTERNAL_ERROR
```

## 10.2 Required explicit handling

The following statuses require non-silent handling:

```text
UNSUPPORTED_PROFILE
  EIB must not present the underlying capability as available.

DEFERRED_SC_B_REQUIRED
  EIB must not claim a cross-plane operation exists locally.

UNKNOWN_PENDING_NORMALIZATION
  EIB must preserve visible uncertainty and must not fabricate known state.

INTERNAL_ERROR
  EIB must not leak internals to product clients.

NOT_FOUND
  EIB may translate into hidden/not-visible in context, but canonical cause must remain traceable internally.

VALIDATION_ERROR
  EIB may expose product-safe validation details; canonical source/status must remain traceable.
```

## 10.3 EIB status vocabulary

Candidate EIB response status values:

```text
OK
PARTIAL
NOT_VISIBLE
NOT_FOUND
NOT_OPERABLE
REJECTED
ADMITTED
DEFERRED
UPSTREAM_UNAVAILABLE
UPSTREAM_ERROR
INTERNAL_ERROR
```

Admission-specific values remain in `InteractionAdmissionDecision`.

---

# 11. Effective view data contracts

## 11.1 EibResponse<T>

Candidate external response wrapper:

```java
record EibResponse<T>(
    EibResponseStatus status,
    T payload,
    List<EibWarning> warnings,
    EibError error,
    CanonicalTraceSummary canonicalTrace
) {}
```

Rules:

```text
canonicalTrace may be omitted or redacted for ordinary product surfaces.
Internal service must still preserve full upstream trace.
Warnings must preserve product-safe explanation of unknown/deferred/unsupported states.
```

## 11.2 EffectiveHabitatView

Candidate fields:

```java
record EffectiveHabitatView(
    String habitatRef,
    String sourceTopologyVersion,
    Instant generatedAt,
    List<EffectiveRoomView> rooms,
    List<EffectiveZoneView> zones,
    List<EffectiveDeviceView> devices,
    List<EffectiveEndpointView> endpoints,
    List<EffectiveTemporalActView> temporalActs,
    EffectiveDiagnosticsSummary diagnostics,
    List<EibWarning> warnings
) {}
```

Rules:

```text
sourceTopologyVersion must come from SC-C topologyVersion.
EffectiveHabitatView must not become Base Topology authority.
EffectiveHabitatView may be regenerated from SC-C canonical inputs.
```

## 11.3 EffectiveRoomView and EffectiveZoneView

Candidate fields:

```java
record EffectiveRoomView(
    String effectiveRoomRef,
    String displayName,
    List<String> effectiveDeviceRefs,
    EffectiveVisibilityStatus visibilityStatus,
    List<EibWarning> warnings
) {}

record EffectiveZoneView(
    String effectiveZoneRef,
    String displayName,
    List<String> effectiveDeviceRefs,
    EffectiveVisibilityStatus visibilityStatus,
    List<EibWarning> warnings
) {}
```

Rules:

```text
EffectiveRoomView and EffectiveZoneView are derived from SC-C topology snapshot room/zone records and the context-visible device set.
They are not canonical RoomNode / ZoneNode authority.
They MUST NOT expose canonical room/zone identifiers to ordinary product surfaces unless a later diagnostic/admin contract explicitly authorizes that exposure.
The post-SDD CSA must verify the exact MU-021 topology snapshot wire shapes for rooms and zones.
```

## 11.4 EffectiveDeviceView

Candidate fields:

```java
record EffectiveDeviceView(
    String effectiveDeviceRef,
    String canonicalDeviceId,          // diagnostic/admin only
    String displayName,
    String roomRef,
    String zoneRef,
    List<EffectiveEndpointView> endpoints,
    EffectiveHealthSummary healthSummary,
    EffectiveRuntimeSummary runtimeSummary,
    EffectiveVisibilityStatus visibilityStatus,
    EffectiveOperabilityStatus operabilityStatus,
    List<EibWarning> warnings
) {}
```

Rules:

```text
canonicalDeviceId MUST be null/omitted for ordinary product surfaces.
canonicalDeviceId MAY be included only in diagnostic/admin context with authorization confirmed.
EIB must verify context before including it.
```

## 11.5 EffectiveEndpointView

Candidate fields:

```java
record EffectiveEndpointView(
    String effectiveEndpointRef,
    String canonicalEndpointId,        // diagnostic/admin only
    String displayName,
    List<EffectiveCapabilityAffordance> capabilities,
    EffectiveHealthSummary healthSummary,
    EffectiveRuntimeSummary runtimeSummary,
    EffectiveVisibilityStatus visibilityStatus,
    EffectiveOperabilityStatus operabilityStatus,
    List<EibWarning> warnings
) {}
```

Rules:

```text
canonicalEndpointId MUST be null/omitted for ordinary product surfaces.
canonicalEndpointId MAY be included only in diagnostic/admin context with authorization confirmed.
EIB must verify context before including it.
```

## 11.6 EffectiveCapabilityAffordance

Candidate fields:

```java
record EffectiveCapabilityAffordance(
    String affordanceRef,
    String capabilityKind,
    String displayLabel,
    EffectiveVisibilityStatus visibilityStatus,
    EffectiveOperabilityStatus operabilityStatus,
    boolean requiresConfirmation,
    boolean requiresAdditionalInput,
    String deferredReason,
    EffectiveActionHandle effectiveActionHandle
) {}
```

Rules:

```text
SC-B-gated action affordances may be visible but not operable in the initial implementation slice.
EffectiveActionHandle for device/endpoint action execution is deferred.
Signal TemporalAct admission may expose temporal-specific handles/requests.
```

## 11.7 EffectiveTemporalActView

Candidate fields:

```java
record EffectiveTemporalActView(
    String effectiveTemporalActRef,
    String canonicalTemporalActId,     // diagnostic/admin only
    String label,
    String status,
    Instant dueAt,
    String payloadKind,
    String notificationContextSummary,
    EffectiveVisibilityStatus visibilityStatus,
    EffectiveOperabilityStatus operabilityStatus,
    boolean canCancel,
    String terminalSummary,
    List<EibWarning> warnings
) {}
```

Rules:

```text
canonicalTemporalActId MUST be null/omitted for ordinary product surfaces.
canonicalTemporalActId MAY be included only in diagnostic/admin context with authorization confirmed.
EIB must verify context before including it.
EIB may hide TemporalActs not visible to context.
EIB may expose canCancel only for visible non-terminal/cancelable TemporalActs.
SC-C remains owner of TemporalAct status and terminal semantics.
```

## 11.8 EffectiveDiagnosticsView

Candidate fields:

```java
record EffectiveDiagnosticsView(
    Instant observedAt,
    String sourceTopologyVersion,
    String northboundReachability,
    EffectiveHealthSummary migrationReadiness,
    List<String> unsupportedProfiles,
    List<EibWarning> warnings,
    Map<String, Object> operatorDetails
) {}
```

Rules:

```text
operatorDetails is diagnostic/admin only.
EIB must not fabricate migration readiness.
UNKNOWN_PENDING_NORMALIZATION must remain visible.
```

---

# 12. Effective status vocabularies

## 12.1 EffectiveVisibilityStatus

```text
VISIBLE
HIDDEN_BY_POLICY
HIDDEN_BY_CONTEXT
HIDDEN_UNAVAILABLE
HIDDEN_UNKNOWN
```

## 12.2 EffectiveOperabilityStatus

```text
OPERABLE
READ_ONLY
DISABLED_BY_POLICY
DISABLED_BY_CONTEXT
DISABLED_UNSUPPORTED_PROFILE
DISABLED_PENDING_NORMALIZATION
DISABLED_DEGRADED
DISABLED_SC_B_REQUIRED
DISABLED_UNKNOWN
```

## 12.3 EffectiveHealthSummary

```text
HEALTHY
DEGRADED
UNHEALTHY
UNKNOWN
UNKNOWN_PENDING_NORMALIZATION
```

Rules:

```text
EIB may summarize health.
EIB must preserve SC-C as health authority.
EIB must not turn UNKNOWN_PENDING_NORMALIZATION into HEALTHY.
```

---

# 13. Effective projection algorithm

## 13.1 EffectiveHabitatView generation

Algorithm:

```text
1. Resolve EibRequestContext.
2. Fetch SC-C topology snapshot through EibScNorthboundClient; derive rooms and zones from that snapshot in the initial implementation slice.
3. Fetch diagnostics and relevant health/runtime/TemporalAct slices as required by the initial implementation slice.
4. Preserve each upstream ScEnvelope<T>.
5. Build effective refs for visible entities.
6. Apply context evaluation to visibility and operability.
7. Convert canonical health/runtime/diagnostics into effective summaries.
8. Attach warnings for unsupported/deferred/pending-normalization states.
9. Omit canonical IDs unless diagnostic/admin access is confirmed.
10. Return EffectiveHabitatView with sourceTopologyVersion.
```

## 13.2 Handling partial upstream failures

Rules:

```text
If topology snapshot is unavailable, EffectiveHabitatView cannot be produced.
If optional runtime/health/diagnostics slice is unavailable, EIB may return PARTIAL with warnings.
If SC-C returns UNKNOWN_PENDING_NORMALIZATION, EIB must include uncertainty warning.
If SC-C returns UNSUPPORTED_PROFILE, EIB must mark relevant feature disabled/unsupported.
```

## 13.3 No canonical mutation

Effective view generation MUST NOT mutate SC-C.

It must not call:

```text
createSignalTemporalAct
cancelTemporalAct
future action/discovery request routes
```

Read projection and request admission are separate flows.

---

# 14. TemporalAct observation

## 14.1 listEffectiveTemporalActs

Input:

```text
habitatId
mode, optional, default ACTIVE
maxResults, optional
EibRequestContext
```

Algorithm:

```text
1. Fetch SC-C temporal acts through Northbound HTTP/OpenAPI.
2. Preserve upstream envelope/status/warnings/error.
3. Map visible canonical TemporalActs to EffectiveTemporalActView.
4. Generate effectiveTemporalActRef.
5. Hide or mark non-visible acts according to context.
6. Compute canCancel for visible non-terminal/cancelable acts.
7. Omit canonicalTemporalActId unless diagnostic/admin access is confirmed.
```

## 14.2 getEffectiveTemporalAct

Algorithm:

```text
1. Rebuild effective temporal ref index from visible TemporalActs.
2. Resolve supplied effectiveTemporalActRef.
3. If unresolved, reject as NOT_FOUND / NOT_VISIBLE depending on context policy.
4. Fetch or reuse SC-C TemporalAct record.
5. Return EffectiveTemporalActView.
```

The initial implementation slice may resolve by list + match. Direct `getTemporalAct` by canonical ID is diagnostic/internal only after effective ref resolution.

---

# 15. TemporalAct admission

## 15.1 admitTemporalSignalRequest

Candidate external request:

```java
record EibTemporalSignalAdmissionRequest(
    Instant dueAt,
    String label,
    String signalKind,
    String notificationTargetRef,
    String idempotencyKey,
    String clientRequestRef
) {}
```

Admission algorithm:

```text
1. Resolve EibRequestContext.
2. Validate request shape product-side.
3. Evaluate context/admission rules.
4. If rejected, return InteractionAdmissionDecision without calling SC-C.
5. Map to SC-C NorthboundCreateSignalTemporalActRequest.
6. Set createdByRef from EIB-controlled opaque subsystem/context reference, not personal identity.
7. Submit to SC-C through HTTP/OpenAPI.
8. Preserve ScNorthboundResponse and status.
8b. If SC-C returns `ACCEPTED` with a `NorthboundTemporalActView` payload, generate `effectiveTemporalActRef` for the returned canonical TemporalAct ID and set `InteractionAdmissionDecision.effectiveRef` to that generated ref.
9. Return InteractionAdmissionDecision + CanonicalSubmissionTrace.
```

Mapping to SC-C request:

```text
dueAt                   -> dueAt
label                   -> label
signalKind              -> signalKind
notificationTargetRef   -> notificationTargetRef
createdByRef            -> eib.<opaqueContextOrServiceRef>
idempotencyKey          -> idempotencyKey or deterministic key derived from clientRequestRef
```

Rules:

```text
EIB MUST NOT send userId/sessionId as createdByRef.
EIB MUST NOT interpret natural-language time.
EIB receives dueAt as absolute Instant.
SC-C remains owner of TemporalAct lifecycle.
```

## 15.2 admitTemporalCancellation

Candidate external request:

```java
record EibTemporalCancellationAdmissionRequest(
    String idempotencyKey,
    String reason,
    String clientRequestRef
) {}
```

Admission algorithm:

```text
1. Resolve EibRequestContext.
2. Resolve effectiveTemporalActRef to canonicalTemporalActId by rebuilding visible temporal ref index.
3. If not resolved, reject without calling SC-C.
4. Evaluate canCancel and context/admission rules.
5. Map to SC-C cancel request body.
6. Submit POST /temporal-acts/{canonicalTemporalActId}/cancel to SC-C.
7. Preserve upstream envelope/status/warnings/error.
8. Return InteractionAdmissionDecision + CanonicalSubmissionTrace.
```

Mapping to SC-C cancel body:

```text
requestedByRef  -> eib.<opaqueContextOrServiceRef>
idempotencyKey  -> idempotencyKey or deterministic key derived from clientRequestRef
reason          -> reason
```

Rules:

```text
The canonical TemporalAct ID is never accepted directly from ordinary product clients.
Only EIB-resolved canonical ID may be submitted to SC-C.
```

---

# 16. InteractionAdmissionDecision

Candidate record:

```java
record InteractionAdmissionDecision(
    InteractionAdmissionStatus status,
    String interactionAdmissionId,
    String effectiveRef,
    CanonicalSubmissionTrace canonicalTrace,
    List<EibWarning> warnings,
    EibError error
) {}
```

Candidate statuses:

```text
ADMITTED
REJECTED_NOT_VISIBLE
REJECTED_NOT_OPERABLE
REJECTED_NOT_AUTHORIZED
REJECTED_STALE_VIEW
REJECTED_INVALID_HANDLE
REJECTED_INVALID_REQUEST
DEFERRED_SC_B_REQUIRED
DEFERRED_UNSUPPORTED_PROFILE
DEFERRED_PENDING_NORMALIZATION
FAILED_CANONICAL_SUBMISSION
UPSTREAM_UNAVAILABLE
```

Rules:

```text
ADMITTED means EIB admitted and submitted or accepted for submission.
ADMITTED does not mean provider-side effect succeeded.
ADMITTED does not mean SC-B dispatch happened.
ADMITTED does not mean physical device state changed.
```

---

# 17. CanonicalSubmissionTrace

Candidate record:

```java
record CanonicalSubmissionTrace(
    String clientRequestRef,
    String interactionAdmissionId,
    String canonicalRequestRef,
    Instant submittedAt,
    ScStatus scNorthboundStatus,
    List<ScWarning> warnings,
    ScError error
) {}
```

Rules:

```text
Trace must preserve SC-C Northbound status.
Trace must preserve error.source and warning.source.
Trace may be redacted for ordinary product clients.
Trace must be available internally for diagnostics and tests.
```

---

# 18. Error and warning policy

## 18.1 Upstream errors

EIB maps upstream errors as follows:

```text
SC-C NOT_FOUND
  -> NOT_FOUND or NOT_VISIBLE depending on context; canonical cause preserved.

SC-C INVALID_REQUEST / INVALID_CANONICAL_ID / VALIDATION_ERROR
  -> REJECTED_INVALID_REQUEST or FAILED_CANONICAL_SUBMISSION; product-safe details.

SC-C UNSUPPORTED_PROFILE
  -> DEFERRED_UNSUPPORTED_PROFILE or disabled effective affordance.

SC-C DEFERRED_SC_B_REQUIRED
  -> DEFERRED_SC_B_REQUIRED.

SC-C UNKNOWN_PENDING_NORMALIZATION
  -> DEFERRED_PENDING_NORMALIZATION or PARTIAL effective view.

SC-C INTERNAL_ERROR
  -> UPSTREAM_ERROR with redacted message for product clients.
```

## 18.2 Network/transport errors

```text
HTTP timeout
connection refused
malformed upstream response
non-JSON upstream response
```

These map to:

```text
UPSTREAM_UNAVAILABLE
```

They must not be confused with SC-C semantic failures.

---

# 19. Diagnostics and canonical ID exposure

## 19.1 Diagnostic/admin mode

EIB may expose canonical IDs and operator details only when:

```text
request context says diagnostic/admin mode was requested;
authorization/context port confirms diagnostic/admin access;
the response path is diagnostic/admin capable.
```

All three conditions are required.

## 19.2 Ordinary product mode

Ordinary product responses MUST NOT include:

```text
canonicalDeviceId
canonicalEndpointId
canonicalTemporalActId
provider-native IDs as authority
SC-C internal repository identifiers
policy internals
session secrets
```

## 19.3 EffectiveDiagnosticsView

Effective diagnostics may include:

```text
northbound reachability
source topologyVersion
unsupported profile summary
pending normalization summary
last upstream response status
EIB local configuration health
```

It must not fabricate readiness.

---

# 20. Security posture for the initial implementation slice

The initial implementation slice is not a complete security product.

Initial implementation security profile:

```text
local/trusted technical API
ordinary product mode by default
diagnostic/admin disabled by default unless test/config explicitly grants it
no identity system of record
no policy system of record
no authorization database
```

Required hardening later:

```text
real Authority/Policy/Identity/Session integration
credential handling
service-to-service authentication
request signing or mTLS if deployed separately
rate limiting
operator audit logging
```

---

# 21. Persistence posture

The initial EIB implementation slice SHOULD be stateless.

Allowed local state:

```text
configuration
runtime health indicators
short-lived in-memory request context / trace for tests
```

Deferred persistence:

```text
effective ref registry
audit log
admission ledger
client request deduplication store
operator diagnostic history
projection cache
```

Rationale:

```text
SC-C remains canonical authority.
The initial EIB implementation slice should prove effective projection/admission before adding its own durable stores.
```

Idempotency for SC-C submissions is delegated to SC-C through the canonical idempotency key. EIB may derive or forward idempotency keys but does not own terminal canonical request state in the initial implementation slice.

---

# 22. Live updates disposition

Live updates are deferred.

The initial implementation slice MUST NOT implement:

```text
SSE from EIB to product clients
WebSocket
GraphQL subscriptions
MCP live tools
SC-B event subscription
polling loop as fake event stream
```

EIB may be designed so future live updates can be added later.

---

# 23. GraphQL / MCP / gRPC disposition

## 23.1 GraphQL

GraphQL belongs near EIB/View Composer, not SC-C direct Northbound.

Disposition:

```text
Deferred.
Not part of the initial EIB implementation slice.
```

## 23.2 MCP

MCP is a separate AI/tool/admin exposure track.

Disposition:

```text
Deferred.
Not EIB primary product path.
```

## 23.3 gRPC / ConnectRPC

gRPC/ConnectRPC remains a downstream option for typed service-to-service binding.

Disposition:

```text
Deferred.
HTTP/OpenAPI remains the first validated EIB upstream binding.
```

---

# 24. Post-SDD CSA obligations

Before MIR, a CSA MUST inspect the actual target repository/codebase and resolve:

```text
CSA-EIB-001 — concrete codebase placement: same monorepo module/package vs separate service.
CSA-EIB-002 — existing EIB code surface, if any.
CSA-EIB-003 — exact dependency patch for Spring Web/client/test stack.
CSA-EIB-004 — exact upstream SC-C HTTP routes from MU-021 implementation.
CSA-EIB-005 — exact DTO strategy: copied DTOs, generated DTOs, or EIB-owned DTOs matching wire shape.
CSA-EIB-006 — exact EibScNorthboundClient implementation strategy: RestClient, WebClient, generated client or equivalent.
CSA-EIB-007 — exact effective ref codec implementation and secret/config strategy.
CSA-EIB-008 — exact context/authorization initial implementation strategy.
CSA-EIB-009 — architecture tests preventing direct SC-C imports.
CSA-EIB-010 — TemporalAct observation/admission route coverage.
CSA-EIB-011 — canonical ID diagnostic/admin gating tests.
CSA-EIB-012 — response-envelope preservation tests.
CSA-EIB-013 — network failure / upstream unavailable test strategy.
CSA-EIB-014 — retained debts and MIR implementation scope.
```

No MIR may be opened unless the CSA resolves these obligations or records safe omissions.

---

# 25. Test strategy for future MIR

## 25.1 Unit tests

```text
EibCanonicalEnvelopeMapperTest
  preserves all twelve ScNorthboundStatus values.
  preserves error.source and warning.source.
  maps explicit statuses without silent success.

EibEffectiveRefCodecTest
  generates non-reversible effective refs.
  resolves refs by rebuilt index.
  rejects invalid/mismatched refs.

EibEffectiveViewMapperTest
  maps topology/device/endpoint/health/runtime/diagnostics into effective views.
  hides canonical IDs in ordinary mode.
  includes canonical IDs only in diagnostic/admin authorized mode.

EibTemporalActMapperTest
  maps SC-C TemporalAct views into EffectiveTemporalActView.
  preserves terminal/pending/unknown states.

EibTemporalAdmissionServiceTest
  maps signal create and cancel requests correctly.
  rejects invisible/stale/invalid temporal refs without calling SC-C.
```

## 25.2 HTTP/API tests

```text
GET /eib/v1/habitats/{habitatId}/effective-view
  returns effective view with sourceTopologyVersion.

GET /eib/v1/habitats/{habitatId}/temporal-acts
  returns EffectiveTemporalActView list.

POST /eib/v1/habitats/{habitatId}/temporal-acts/signal
  admits valid signal temporal request and submits to SC-C.

POST /eib/v1/habitats/{habitatId}/temporal-acts/{effectiveTemporalActRef}/cancel
  resolves effective ref and submits canonical cancel to SC-C.

Ordinary mode responses omit canonical IDs.
Diagnostic/admin authorized responses may include canonical IDs.
```

## 25.3 Upstream client tests

Use mock HTTP server / Spring HTTP test utilities to assert:

```text
EIB calls SC-C HTTP/OpenAPI routes, not in-process code.
EIB preserves upstream ScNorthboundResponse body.
EIB handles HTTP 404/422/501/503/500 distinctly.
EIB handles timeout/connection failure as UPSTREAM_UNAVAILABLE.
```

## 25.4 Architecture tests

Required:

```text
eibDoesNotImportCoreNorthboundFacade
  scan EIB source for ScCoreNorthboundFacade and core.northbound imports.

eibDoesNotImportScCRepositoriesOrAdapters
  scan EIB source for repository/persistence/Jdbc/Flyway adapter imports.

eibDoesNotImportScBOrScD
  scan EIB source for SC-B / SC-D adapter runtime packages.

eibDoesNotExposeCanonicalIdsInOrdinaryViews
  test DTO output in ordinary context.
```

---

# 26. Acceptance criteria

## Contract and boundary

```text
AC-EIB-SDD-001 — SDD defines EIB as non-co-located with SC-C.
AC-EIB-SDD-002 — SDD forbids direct import/call to ScCoreNorthboundFacade.
AC-EIB-SDD-003 — SDD requires SC-C consumption through Northbound HTTP/OpenAPI.
AC-EIB-SDD-004 — SDD keeps Hub, SApp and Surfaces as EIB consumers, not SC-C consumers.
AC-EIB-SDD-005 — SDD keeps Authority/Policy/Identity/Session as context inputs, not EIB-owned systems of record.
```

## Initial implementation scope

```text
AC-EIB-SDD-006 — SDD includes effective read projection in the initial implementation slice, including rooms and zones derived from the topology snapshot.
AC-EIB-SDD-007 — SDD includes EffectiveTemporalAct observation in the initial implementation slice.
AC-EIB-SDD-008 — SDD includes TemporalAct signal admission and cancellation in the initial implementation slice.
AC-EIB-SDD-009 — SDD defers device/endpoint action intent because it is SC-B-gated.
AC-EIB-SDD-010 — SDD defers discovery intent because it is SC-B-gated.
AC-EIB-SDD-011 — SDD defers live updates and does not fake an event stream.
```

## Envelope and status

```text
AC-EIB-SDD-012 — SDD preserves ScNorthboundResponse<T> vocabulary internally.
AC-EIB-SDD-012a — SDD requires EIB HTTP routes to return EibResponse<T> bodies rather than bare DTOs.
AC-EIB-SDD-013 — SDD requires all twelve ScNorthboundStatus values to be represented.
AC-EIB-SDD-014 — SDD preserves error.source and warning.source.
AC-EIB-SDD-015 — SDD explicitly handles UNSUPPORTED_PROFILE, DEFERRED_SC_B_REQUIRED, UNKNOWN_PENDING_NORMALIZATION, INTERNAL_ERROR and NOT_FOUND.
AC-EIB-SDD-016 — SDD distinguishes upstream network failure from SC-C semantic failure.
```

## Effective refs and canonical IDs

```text
AC-EIB-SDD-017 — SDD defines effective refs that do not expose canonical IDs.
AC-EIB-SDD-018 — SDD defines effective ref resolution without requiring persistence in the initial implementation slice.
AC-EIB-SDD-019 — SDD forbids canonicalDeviceId in ordinary product responses.
AC-EIB-SDD-020 — SDD forbids canonicalEndpointId in ordinary product responses.
AC-EIB-SDD-021 — SDD forbids canonicalTemporalActId in ordinary product responses.
AC-EIB-SDD-022 — SDD allows canonical IDs only under diagnostic/admin context with authorization confirmation.
```

## Temporal admission

```text
AC-EIB-SDD-023 — SDD maps temporal signal admission to SC-C createSignalTemporalAct.
AC-EIB-SDD-024 — SDD maps temporal cancellation to SC-C cancelTemporalAct.
AC-EIB-SDD-025 — SDD ensures cancellation resolves effectiveTemporalActRef before submitting canonical ID to SC-C.
AC-EIB-SDD-026 — SDD forbids user/session IDs as SC-C createdByRef/requestedByRef.
AC-EIB-SDD-027 — SDD preserves CanonicalSubmissionTrace for SC-C submission status.
AC-EIB-SDD-027a — SDD requires Signal TemporalAct creation to populate InteractionAdmissionDecision.effectiveRef when SC-C returns ACCEPTED with a TemporalAct payload.
```

## CSA / implementation readiness

```text
AC-EIB-SDD-028 — SDD declares post-SDD CSA obligations before MIR.
AC-EIB-SDD-029 — SDD declares test strategy for unit, HTTP/client and architecture tests.
AC-EIB-SDD-029a — SDD models getTopologyVersion as structured NorthboundTopologyVersionViewDto rather than a bare String.
AC-EIB-SDD-030 — SDD declares retained deferred tracks: GraphQL, MCP, gRPC/ConnectRPC, SSE/WebSocket, Authority/Policy implementation, HTTP TCK.
AC-EIB-SDD-031 — SDD identifies candidate MIR as bounded EIB initial implementation slice only.
AC-EIB-SDD-032 — SDD does not authorize implementation directly.
```

---

# 27. Retained debts after this SDD

```text
DEBT-EIB-001 — EIB post-SDD CSA not yet performed.
DEBT-EIB-002 — EIB MIR not yet opened.
DEBT-EIB-003 — EIB implementation not yet attempted.
DEBT-EIB-004 — Authority/Policy/Identity/Session integration remains context-port only.
DEBT-EIB-005 — Device/endpoint action admission remains SC-B-gated and deferred.
DEBT-EIB-006 — Discovery admission remains SC-B-gated and deferred.
DEBT-EIB-007 — Live updates remain deferred.
DEBT-EIB-008 — GraphQL facade remains deferred.
DEBT-EIB-009 — MCP AI/tool/admin facade remains separate and deferred.
DEBT-EIB-010 — gRPC/ConnectRPC binding remains downstream.
DEBT-EIB-011 — EIB conformance/TCK remains absent.
DEBT-EIB-012 — Durable EIB audit/admission ledger remains deferred.
```

HTTP debts retained from MU-021 remain open unless closed by specific downstream artifacts:

```text
DEBT-HTTP-001 — SSE remains SSE-P0 / deferred.
DEBT-HTTP-002 — local-trusted only / no complete auth-authz.
DEBT-HTTP-003 — Swagger UI / static YAML deferred.
DEBT-HTTP-004 — EIB runtime requires its own contract/design/descent before implementation; this SDD advances but does not alone close it.
DEBT-HTTP-005 — gRPC/ConnectRPC downstream.
DEBT-HTTP-006 — MCP separate track.
DEBT-HTTP-007 — ACTIVE mode ignores maxResults, inherited from facade.
DEBT-HTTP-008 — HTTP TCK absent.
```

---

# 28. Candidate MIR and downstream sequence

Candidate MIR:

```text
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
```

Candidate MU:

```text
MU-SOV-SC-X-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
```

Candidate branch:

```text
feat/sc-eib-mir-022-effective-interaction-boundary-seed
```

Candidate commit:

```text
feat(eib): add effective interaction boundary seed
```

Required sequence:

```text
1. Review SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001.
2. Promote SDD to candidate if accepted.
3. Perform CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001.
4. Open MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001.
5. Build execution package.
6. Implement the MIR-authorized slice.
7. Validate L4.
```

---

# 29. Final dictum

`SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.2.0-candidate` is accepted as SDD-candidate and requires post-SDD CSA before MIR.

This SDD defines a bounded initial EIB implementation slice:

```text
EIB consumes SC-C through validated Northbound HTTP/OpenAPI.
EIB composes effective read projection.
EIB includes TemporalAct observation and SC-C-local TemporalAct admission/cancellation.
EIB preserves canonical response vocabulary and uncertainty.
EIB hides canonical IDs from ordinary product surfaces.
EIB does not implement SC-B-gated actions, discovery, live updates, GraphQL, MCP, gRPC, Authority/Policy system-of-record, Hub, Surface UX or SC-D adapters.
```

The next gate is post-SDD CSA, not implementation.
