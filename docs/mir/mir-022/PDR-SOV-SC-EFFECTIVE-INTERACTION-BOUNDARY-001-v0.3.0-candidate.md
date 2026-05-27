# PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001

## Effective Interaction Boundary

**Document ID:** PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001  
**Title:** Effective Interaction Boundary  
**Version:** v0.3.0-candidate  
**Status:** Candidate / post-MU-021 contract finalization accepted / SDD-ready  
**Date:** 2026-05-26  
**Corpus:** Sovereign Connect  
**Type:** PDR  
**Plane:** SC-X / product-facing membrane  
**Owner:** Connect Architecture / SC  
**Scope:** Defines the Effective Interaction Boundary (EIB) as the product-facing membrane that consumes the validated SC-C Northbound HTTP/OpenAPI exposure and composes effective interaction for downstream product surfaces. This PDR does not implement EIB, View Composer, Hub, SApp, Surfaces, Authority, Policy, Session, Identity, SC-B runtime, SC-D adapters, gRPC/ConnectRPC, MCP, GraphQL, WebSocket, product UX or a TCK.

---

## Changelog v0.3.0-candidate

Candidate promotion after review.

This version:

1. Corrects response-envelope preservation by separating structural preservation of `ScNorthboundResponse<T>` from explicit handling of selected `ScNorthboundStatus` values.
2. Requires EIB to preserve all twelve `ScNorthboundStatus` values internally, not only unsupported/deferred/pending-normalization statuses.
3. Clarifies that EIB may translate statuses into product-safe language only if the original canonical status remains internally traceable.
4. Expands the first EIB seed profile to include effective TemporalAct observation and SC-C-local TemporalAct admission/cancellation.
5. Keeps SC-B-gated action intent and discovery intent deferred from the first seed.
6. Adds diagnostic/admin-context rules for exposing `canonicalDeviceId`, `canonicalEndpointId` and `canonicalTemporalActId`.
7. Preserves ordinary product surfaces as effective-ref / handle consumers, not canonical-ID consumers.
8. Confirms SDD readiness after resolving review findings H-001 through H-003.

---

## Changelog v0.2.0-draft

Major contract update after MU-021.

This version:

1. Reclassifies EIB from `contract-preparation / implementation-deferred` into the next active contract-finalization target after `MU-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001` validation.
2. Records that SC-C Northbound is now externally consumable through the validated HTTP/OpenAPI binding.
3. Records that EIB is not co-located with SC-C and therefore MUST NOT consume the in-process `ScCoreNorthboundFacade` directly.
4. Defines the initial EIB upstream consumption rule: EIB consumes SC-C through the Northbound HTTP/OpenAPI binding and preserves the canonical `ScNorthboundResponse<T>` vocabulary.
5. Reframes `DEBT-HTTP-004` as the active EIB-enabling debt: EIB runtime requires its own contract/design/descent before implementation.
6. Keeps `DEBT-HTTP-001` through `DEBT-HTTP-008` open unless explicitly closed by downstream artifacts.
7. Clarifies that closing `DEBT-HTTP-004` does not require immediate implementation; it requires an accepted EIB SDD and then a dedicated CSA/MIR/execution package for implementation descent.
8. Defines EIB as a bidirectional product-facing membrane: read projection + request admission + product-safe lifecycle interpretation.
9. Defines View Composer as the read/projection facet of EIB, not as SC-C and not necessarily as the whole EIB.
10. Refines initial contract families: `EffectiveHabitatView`, `EffectiveDeviceView`, `EffectiveEndpointView`, `EffectiveCapabilityAffordance`, `EffectiveActionHandle`, `EffectiveTemporalActView`, `InteractionAdmissionRequest`, `InteractionAdmissionDecision`, `EffectiveDiagnosticsView`, and `CanonicalSubmissionTrace`.
11. Defines initial status vocabularies for effective visibility, operability and admission.
12. Preserves Base Topology / Effective View separation.
13. Preserves that Hub, SApp and Surfaces consume EIB, not SC-C directly.
14. Preserves that EIB consumes Session / Identity / Authority / Policy inputs without becoming their system of record.
15. Defines downstream SDD, CSA, MIR and seed-MU expectations.

---

## Prior changelog v0.1.0-draft

Initial draft.

This version opened `PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001` as the canonicalization target after the first Northbound facade seed. It defined EIB as the bidirectional product-facing membrane above the SC-C Northbound Facade and separated EIB from SC-C, View Composer, Hub, SApp, Surfaces, Session, Identity, Authority and Policy.

---

## Depends on

- `Sovereign Connect — Curator & Descent Instructions`
- `RFC-SOV-SC-C-CORE-BOUNDARY-001 v0.3.0-candidate`
- `RFC-SOV-TOPOLOGY-BASE-PROJECTION-SPLIT-001`
- `ADR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-draft`
- `PDR-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft`
- `SDD-SOV-SC-C-NORTHBOUND-FACADE-001 v0.1.1-draft`
- `PDR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.2.0-candidate`
- `SDD-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v0.1.1-draft`
- `ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-BINDING-001 v0.1.1-draft`
- `SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001 v0.2.0-candidate / accepted`
- `MIR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001 v1.0.0-accepted`
- `MIR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001 v1.0.0-accepted`
- `MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001 v1.0.0-accepted`
- `PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.16-draft`
- `INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.22-draft`
- `SYNC-SOV-SC-MU-BACKLOG-001 v0.1.23-draft`

## Related

- `PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001 v0.1.1-draft`
- `ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-TECH-001 v0.1.1-draft`
- `NT-SOV-SC-C-NORTHBOUND-EXPOSURE-TECH-SOTA-001 v0.1.0-draft`
- `PDR-SOV-SC-VIEW-COMPOSER-BOUNDARY-001`, to be revised under EIB framing
- `SDD-SOV-SC-VIEW-COMPOSER-001`, to be revised under EIB framing
- `PDR-SOV-SC-HUB-SC-BOUNDARY-001`, pending
- `PDR-SOV-SC-TOPOLOGY-PROJECTION-BOUNDARY-001`, pending / may be absorbed or superseded by EIB/VC PDRs
- `PDR-SOV-SC-DIAGNOSTIC-ADMIN-BOUNDARY-001`, pending
- `PDR-SOV-SC-MCP-FACADE-001`, pending / outside EIB primary product path
- `PDR-SOV-SC-X-DISCOVERY-REQUEST-FLOW-001`, planned
- `SDD-SOV-SC-C-NORTHBOUND-GRPC-CONNECTRPC-001`, future downstream option
- `TCK-SOV-SC-EIB-CONFORMANCE-001`, future

---

# 0. Purpose

This PDR defines the **Effective Interaction Boundary** (EIB): the product-facing contract membrane through which Hub, SApp, Surfaces and later product-facing services interact with Sovereign Connect without directly consuming SC-C internals.

EIB exists because SC-C now exposes canonical truth through an externally consumable HTTP/OpenAPI Northbound binding, but canonical truth is not yet effective product interaction.

SC-C answers:

```text
What is canonically true?
What canonical entity exists?
What canonical state/health/runtime information is known?
What canonical request lifecycle status exists?
What is unsupported, deferred or pending normalization?
```

EIB answers:

```text
What is visible in this context?
What is operable in this context?
Which canonical affordances become effective affordances?
Which effective request is admissible?
How should canonical uncertainty be represented product-safely?
Which downstream product client may see or do what?
```

This PDR does not authorize implementation. It is a contract-finalization artifact that enables an EIB SDD, CSA, MIR and execution package.

---

# 1. Thesis

EIB is the bidirectional product-facing membrane between product clients and SC-C canonical truth.

Canonical formula:

```text
SC-C owns canonical state and canonical request lifecycle.
SC-C Northbound HTTP/OpenAPI exposes canonical SC-C truth across a process boundary.
EIB consumes SC-C Northbound and composes effective projection/admission.
View Composer is the read/projection facet of EIB.
Hub / SApp / Surfaces consume EIB, not SC-C.
Authority / Policy / Identity / Session provide context and decisions to EIB.
SC-B routes and correlates cross-plane operations when required.
SC-D observes, translates and executes provider-level behavior.
```

EIB MUST make the system usable without corrupting the ownership model.

EIB is not a convenience wrapper around SC-C.

EIB prevents:

```text
Surfaces from shaping canonical topology.
Hub from bypassing canonical request lifecycle.
SC-C from absorbing Session / Identity / Policy / UX.
Projection from leaking into Base Topology.
Effective View from being mistaken for canonical state.
HTTP route shape from becoming canonical topology semantics.
MCP / GraphQL / WebSocket schemas from becoming SC-C contracts.
```

---

# 2. Post-MU-021 baseline

The baseline for this PDR is:

```text
MU-019 — SC-C Northbound Facade Seed                 Validated L4
MU-020 — SC-C Northbound Facade Hardening            Validated L4
MU-021 — SC-C Northbound HTTP/SSE Binding Seed       Validated L4
```

MU-021 validates the first real external technical exposure of SC-C Northbound through HTTP/OpenAPI.

The operative chain is now:

```text
SC-C canonical state
  -> ScCoreNorthboundFacade
  -> HTTP/OpenAPI adapter
  -> EIB or other authorized technical consumer
```

EIB is not co-located with SC-C.

Therefore:

```text
EIB MUST consume SC-C through the validated Northbound HTTP/OpenAPI binding.
EIB MUST NOT call the in-process ScCoreNorthboundFacade directly.
EIB MUST NOT import SC-C repositories, services, ports or internal adapters.
```

---

# 3. DEBT-HTTP-004 disposition

`DEBT-HTTP-004` is the active EIB-enabling debt:

```text
DEBT-HTTP-004 — EIB runtime requires its own contract/design/descent before implementation.
```

This PDR puts `DEBT-HTTP-004` in treatment by defining the EIB contract over the validated SC-C Northbound HTTP/OpenAPI exposure.

This PDR does not close `DEBT-HTTP-004` completely.

Closure rule:

```text
DEBT-HTTP-004 may be closed only when EIB has:
  - accepted PDR contract;
  - accepted implementation-oriented SDD;
  - CSA appropriate to its code surface;
  - MIR authorizing a bounded implementation attempt;
  - execution evidence validating the seed or closure profile.
```

If governance wants a weaker document-only closure, it may define:

```text
DEBT-HTTP-004-doc-closure:
  closed when this PDR and the first EIB SDD are accepted.
```

Runtime closure still requires a validated EIB MU.

---

# 4. Scope

EIB MAY define contracts over:

- effective habitat view composition;
- effective room / zone / device / endpoint / capability views;
- contextual visibility;
- contextual operability;
- capability affordance shaping;
- effective action handles;
- product-safe request admission;
- product-safe rejection reasons;
- TemporalAct effective observation and cancellation admission;
- canonical lifecycle interpretation for product clients;
- effective diagnostics for authorized operators;
- discovery intent presentation and admission, after SC-B/SC-D discovery contracts exist;
- projection cache / invalidation semantics, if later specified;
- live update model, if later specified.

EIB MUST NOT own:

- SC-C canonical state;
- Base Topology;
- topologyVersion;
- canonical IDs;
- canonical materialization;
- endpoint/device health authority;
- canonical persistence;
- SC-C migration/readiness authority;
- SC-B routing/correlation;
- SC-D provider protocol;
- provider-native wire payloads;
- final topology mutation;
- adapter lifecycle admission;
- identity source of truth;
- authority source of truth;
- policy source of truth;
- Hub conversation memory;
- Surface rendering state;
- NLU interpretation internals.

EIB MAY consume Session / Identity / Authority / Policy context.

EIB MUST NOT become the authoritative source for those contexts unless a future artifact explicitly defines a separate module boundary.

---

# 5. Boundary invariant

```text
SC-C Northbound HTTP/OpenAPI exposes canonical SC-C surfaces.
EIB consumes Northbound HTTP/OpenAPI and performs effective admission/projection.
View Composer is the read/projection facet of EIB.
Hub, SApp and Surfaces consume EIB.
SC-B transports/correlates cross-plane operations.
SC-D executes protocol-level discovery/commands and emits facts.
```

Invalid anti-models:

```text
Hub -> SC-C directly as product architecture
SApp -> SC-C directly as product architecture
Surface -> SC-C directly as product architecture
EIB imports ScCoreNorthboundFacade directly
EIB imports SC-C repositories, ports or adapters
EIB = SC-C
EIB = SC-B
EIB = SC-D adapter runtime
EIB = Hub memory
EIB = Authority source of truth
EIB = Policy source of truth
EIB mutates Base Topology directly
EIB fabricates topologyVersion
EIB exposes provider-native wire payloads as product contract
EIB treats Effective View as Base Topology
View Composer writes canonical state
GraphQL query shape becomes canonical topology
MCP tool schema becomes product interaction contract
```

---

# 6. Role separation

## 6.1 SC-C Northbound HTTP/OpenAPI

SC-C Northbound HTTP/OpenAPI provides canonical observation and bounded request entrypoints across a process boundary.

It returns canonical truth and explicit unsupported/deferred statuses through `ScNorthboundResponse<T>`.

It does not decide effective visibility, authority, surface affordance or product UX.

## 6.2 EIB

EIB consumes SC-C Northbound HTTP/OpenAPI and composes effective interaction.

EIB is responsible for:

```text
effective projection
effective admission
affordance shaping
contextual operability
product-safe rejection reasons
surface-safe handles
effective runtime observation
canonical uncertainty interpretation
product-safe lifecycle state mapping
```

EIB is not responsible for:

```text
canonical topology mutation
canonical state persistence
provider protocol execution
transport routing
identity registry ownership
policy corpus ownership
surface rendering
conversation memory
```

## 6.3 View Composer

View Composer is the **read/projection facet** of EIB.

It composes Effective Habitat View from canonical SC-C observations plus contextual inputs.

View Composer MUST NOT:

```text
write SC-C canonical state
redefine Base Topology
own canonical IDs
own topologyVersion
own health authority
decide provider-level effects
```

View Composer MAY be implemented as an internal EIB component, a separate service or a module inside the EIB runtime, subject to a downstream SDD/ADR.

## 6.4 Hub

Hub may perform conversation, NLU orchestration, product orchestration and high-level intent mediation.

Hub MUST consume EIB for habitat interaction.

Hub MUST NOT call SC-C directly as product architecture.

Hub MAY hold conversation memory, but that memory MUST NOT become SC-C memory or Base Topology.

## 6.5 SApp / Surfaces

SApp and Surfaces render interaction and collect user gestures / commands.

They consume Effective Views and effective affordances from EIB.

They MUST NOT:

```text
consume SC-C Northbound directly as product architecture
derive policy by themselves
treat local view cache as canonical state
inject provider-native IDs as canonical IDs
```

## 6.6 Authority / Policy / Identity / Session

These are contextual inputs or external decision sources for EIB.

EIB may ask:

```text
Who is acting?
What session/context is active?
What policy applies?
Is this operation allowed?
Which affordances should be visible or operable?
```

EIB MUST NOT silently become the system of record for identity, authority or policy.

---

# 7. Upstream SC-C consumption contract

EIB consumes SC-C only through the validated Northbound HTTP/OpenAPI binding in the first implementation track.

## 7.1 HTTP/OpenAPI as first upstream binding

Rules:

```text
EIB -> SC-C uses HTTP/OpenAPI for first runtime consumption.
EIB MUST NOT call in-process ScCoreNorthboundFacade directly.
EIB MUST NOT depend on Java records as an in-process ABI.
EIB MAY use generated OpenAPI client code if a downstream SDD selects it.
EIB MUST treat the OpenAPI schema as binding-specific, not as a replacement for canonical SC-C contracts.
```

## 7.2 Response envelope preservation

EIB MUST preserve and interpret the canonical response vocabulary internally:

```text
ScNorthboundResponse<T>
ScNorthboundStatus (all 12 values)
ScNorthboundError(code, message, source)
ScNorthboundWarning(code, message, source)
```

EIB MUST NOT flatten the canonical response into a flat payload body.

EIB MUST NOT discard:

```text
status
error.code
error.message
error.source
warning.code
warning.message
warning.source
```

EIB MAY translate any `ScNorthboundStatus` value into product-safe language for downstream surfaces, but the internal EIB contract MUST preserve the original canonical status and its traceability.

The following statuses require explicit handling and MUST NOT be silently collapsed:

```text
UNSUPPORTED_PROFILE
  not success; must not be presented as available.

DEFERRED_SC_B_REQUIRED
  not success; operation is pending cross-plane capability.

UNKNOWN_PENDING_NORMALIZATION
  visible uncertainty; must not be fabricated as known.

INTERNAL_ERROR
  must not leak internals to product clients.

NOT_FOUND
  may become hidden/not-visible in a product context, but the canonical cause
  must remain traceable internally.
```

Other statuses, including `OK`, `CREATED`, `ACCEPTED`, `CANCELLED`, `INVALID_REQUEST`, `INVALID_CANONICAL_ID` and `VALIDATION_ERROR`, also remain part of the preserved internal vocabulary even when translated for product-facing presentation.

## 7.3 Status interpretation

EIB MUST distinguish at least:

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

Rules:

```text
UNSUPPORTED_PROFILE is not success.
DEFERRED_SC_B_REQUIRED is not success.
UNKNOWN_PENDING_NORMALIZATION is visible uncertainty, not absence.
INTERNAL_ERROR must not leak internals to product clients.
NOT_FOUND may become hidden/not-visible depending on context, but the canonical cause must remain traceable internally.
```

## 7.4 Upstream route families

This PDR does not freeze HTTP route shapes, but EIB SDD MUST map to the validated SC-C Northbound route families:

```text
topology observation
room / zone listing
device observation
endpoint observation
health observation
runtime-state observation
temporal runtime status
temporal act create / cancel / get / list
diagnostics
```

## 7.5 Upstream non-goals

EIB first descent MUST NOT require SC-C upstream features that remain deferred:

```text
SSE live event stream
SC-C gRPC / ConnectRPC binding
SC-C MCP adapter
GraphQL over SC-C
SC-B runtime
SC-D adapter execution
command-to-adapter E2E
provider-side effect verification
HTTP TCK
```

---

# 8. Operation profiles

## 8.1 Profile EIB-A — Effective read projection

Operations that return contextual effective views.

Candidate operations:

```text
getEffectiveHabitatView(context)
getEffectiveRoomView(context, roomId)
getEffectiveZoneView(context, zoneId)
getEffectiveDeviceView(context, effectiveDeviceRef)
getEffectiveEndpointView(context, effectiveEndpointRef)
listEffectiveDevices(context, filter)
listEffectiveEndpoints(context, filter)
listEffectiveAffordances(context, targetRef)
```

Characteristics:

```text
read-oriented
contextual
derived from SC-C canonical observations
filtered/shaped by Session / Identity / Authority / Policy / Surface context
does not mutate SC-C
does not require SC-B unless live cross-plane state refresh is explicitly requested in a later profile
```

## 8.2 Profile EIB-B — Effective request admission

Operations that receive product-facing interaction intent and decide whether to translate it into a canonical SC-C request or a later cross-plane request.

Candidate operations:

```text
admitActionIntent(context, effectiveActionHandle, params)
admitTemporalSignalRequest(context, request)
admitTemporalCancellation(context, effectiveTemporalActRef)
admitDiscoveryIntent(context, request)
```

Characteristics:

```text
validates effective handle
checks contextual admission
returns admitted/rejected/deferred decision
does not claim provider-side success
does not bypass SC-C request lifecycle
```

If admitted, EIB calls the proper downstream canonical boundary:

```text
SC-C Northbound HTTP/OpenAPI for SC-C-local requests.
SC-C / SC-B-gated canonical request entrypoint after cross-plane contracts exist.
```

EIB MUST NOT call SC-D adapters directly.

## 8.3 Profile EIB-C — Effective runtime observation

Operations that project runtime state into product-safe view.

Candidate operations:

```text
getEffectiveTemporalActs(context, filter)
getEffectiveRuntimeStatus(context)
getEffectiveDeviceOperationalState(context, effectiveDeviceRef)
getEffectiveEndpointOperationalState(context, effectiveEndpointRef)
getEffectiveDiagnostics(context)
```

Characteristics:

```text
derived from canonical runtime state
may hide unsupported/deferred Core surfaces from ordinary users
must represent uncertainty honestly
must not fabricate health/state authority
```

## 8.4 Profile EIB-D — Live updates

Live updates are reserved for a downstream SDD/ADR.

Candidate models:

```text
EIB polling SC-C Northbound and publishing effective deltas
SC-C Northbound SSE when DEBT-HTTP-001 is closed
EIB-local SSE/WebSocket for product surfaces
GraphQL subscriptions at EIB/VC only, if selected later
```

This PDR does not select the live update technology.

## 8.5 Profile EIB-E — Diagnostics / operator mode

Diagnostics may expose more canonical detail than user-facing views.

Rules:

```text
diagnostic mode requires explicit authorization context
diagnostic mode may show canonical IDs
diagnostic mode may show unsupported/deferred Core statuses
diagnostic mode must not expose provider-native wire payloads as product language
diagnostic mode must not bypass EIB admission for actions
```

## 8.6 Profile EIB-F — AI / tool/admin interaction

AI/tool/admin interaction is not the first EIB implementation.

MCP, if used, belongs to a future AI/tool/admin facade over EIB or a diagnostic/admin boundary.

MCP MUST NOT become:

```text
SC-C primary API
EIB primary product API
Base Topology contract
Authority source of truth
```

---

# 9. Effective view principles

## 9.1 Effective View is derived

Effective View is a derived product-facing representation.

It is not canonical topology.

Every Effective View MUST be traceable to:

```text
canonical SC-C observation(s)
context input(s)
projection/admission rule(s)
```

## 9.2 Base Topology exclusion

EIB MUST NOT write back Effective View fields into Base Topology.

Forbidden fields in Base Topology remain forbidden:

```text
VisibilityRule
actor role filters
session filters
identity projection
authority projection
policy-derived topology
guest-specific topology
owner-specific topology
conversation metadata
surface layout metadata
```

## 9.3 Effective operability

A device or endpoint may exist canonically but be not operable effectively.

Examples:

```text
visible but disabled by policy
visible but unsupported in current surface
visible but read-only
hidden from current actor
available but requires confirmation
available but SC-B-gated/deferred
available but degraded/unverified
available but unknown pending normalization
```

EIB MUST distinguish:

```text
exists canonically
visible effectively
operable effectively
admitted for request
submitted to canonical lifecycle
completed semantically
```

## 9.4 Effective action handles

EIB MAY expose effective action handles.

An effective action handle is:

```text
opaque to Surface / SApp / Hub
bound to an effective view context
resolvable by EIB
not a canonical endpointId
not a provider-native ID
not a permission grant by itself
not a durable canonical identity
```

EIB MUST validate an effective action handle at request time.

A stale, revoked, mismatched or context-invalid handle MUST be rejected.

## 9.5 Projection consistency marker

Effective views SHOULD include consistency markers.

Candidate fields:

```text
effectiveViewVersion
sourceTopologyVersion
observedAt
policyEvaluationVersion
contextHash or contextRef
```

At minimum, an Effective View MUST expose the `sourceTopologyVersion` when derived from SC-C topology.

---

# 10. Data contract families

This section names contract families but does not freeze byte-for-byte records.

## 10.1 EffectiveHabitatView

Represents a product-facing habitat view for a given context.

Candidate fields:

```text
habitatRef
sourceTopologyVersion
observedAt
rooms
zones
devices
endpoints
temporalSlice
diagnosticsSummary
warnings
```

Must not include:

```text
raw SC-C aggregate objects
SQLite details
NATS subjects
JetStream state
provider-native wire payloads
policy internals
identity secrets
session secrets
```

## 10.2 EffectiveRoomView / EffectiveZoneView

Contextual room/zone views.

Candidate fields:

```text
roomRef / zoneRef
displayName
effectiveDevices
effectiveEndpoints
effectiveAffordances
visibilityStatus
operabilitySummary
sourceTopologyVersion
```

## 10.3 EffectiveDeviceView

Contextual device view.

Candidate fields:

```text
effectiveDeviceRef
canonicalDeviceId, optional diagnostic/admin exposure
displayName
roomRef
zoneRefs
effectiveEndpoints
effectiveCapabilities
healthSummary
runtimeSummary
visibilityStatus
operabilityStatus
warnings
```

Rules:

```text
canonicalDeviceId MUST NOT be included in views returned to ordinary product surfaces.
canonicalDeviceId MAY be included only when the request context is explicitly classified as diagnostic/admin mode and the authorization context confirms diagnostic/admin access.
EIB MUST verify the authorization context before including canonical IDs in any response.
Surface-facing UI should normally use effective refs/handles.
Provider-native IDs remain metadata only and must not become action authority.
```

## 10.4 EffectiveEndpointView

Contextual endpoint view.

Candidate fields:

```text
effectiveEndpointRef
canonicalEndpointId, optional diagnostic/admin exposure
displayName
effectiveCapabilities
healthSummary
runtimeSummary
visibilityStatus
operabilityStatus
warnings
```

Rules:

```text
canonicalEndpointId MUST NOT be included in views returned to ordinary product surfaces.
canonicalEndpointId MAY be included only when the request context is explicitly classified as diagnostic/admin mode and the authorization context confirms diagnostic/admin access.
EIB MUST verify the authorization context before including canonical IDs in any response.
Surface-facing UI should normally use effective refs/handles.
Provider-native IDs remain metadata only and must not become action authority.
```

## 10.5 EffectiveCapabilityAffordance

Represents what the actor/context can perceive or do.

Candidate fields:

```text
affordanceRef
capabilityKind
displayLabel
visibilityStatus
operabilityStatus
requiresConfirmation
requiresAdditionalInput
deferredReason
effectiveActionHandle, optional
```

## 10.6 EffectiveActionHandle

Opaque request handle.

Candidate fields:

```text
handleId
issuedAt
expiresAt, optional
sourceTopologyVersion
targetScope
capabilityKind
constraints
```

Rules:

```text
Surface / Hub / SApp cannot parse handleId.
EIB must validate handle before canonical request admission.
Handle does not replace canonical target validation.
Handle does not prove policy forever.
```

## 10.7 EffectiveTemporalActView

Contextual temporal runtime view.

Candidate fields:

```text
effectiveTemporalActRef
canonicalTemporalActId, optional diagnostic/admin exposure
label
status
dueAt
payloadKind
notificationContextSummary
visibilityStatus
operabilityStatus
canCancel
terminalSummary
```

Rules:

```text
canonicalTemporalActId MUST NOT be included in views returned to ordinary product surfaces.
canonicalTemporalActId MAY be included only when the request context is explicitly classified as diagnostic/admin mode and the authorization context confirms diagnostic/admin access.
EIB MUST verify the authorization context before including canonical IDs in any response.
EIB may hide TemporalActs not visible to a context.
EIB may expose cancel affordance only if admission says it is allowed.
SC-C remains owner of TemporalAct status and terminal semantics.
```

## 10.8 InteractionAdmissionRequest

Represents a product-facing request to perform or schedule something.

Candidate fields:

```text
context
effectiveActionHandle or effectiveTemporalActRef
params
idempotencyKey
requestedAt
clientRequestRef
```

Must not include:

```text
provider-native target as authority
raw SC-C persistence identifiers as only authority, unless diagnostic/admin flow explicitly allows it
session secret
policy internals
surface rendering state
```

## 10.9 InteractionAdmissionDecision

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
```

Rules:

```text
ADMITTED means EIB accepted the request for canonical submission.
ADMITTED does not mean provider-side effect succeeded.
For SC-B-gated operations, EIB must not claim semantic completion at admission time.
```

## 10.10 EffectiveDiagnosticsView

Represents product-safe technical status.

Candidate fields:

```text
observedAt
sourceTopologyVersion
northboundReachability
migrationReadiness
unsupportedProfiles
warnings
operatorDetails, optional authorized diagnostic mode only
```

Rules:

```text
EIB may summarize SC-C diagnostics.
EIB must preserve unknown/pending/deferred states internally.
EIB must not fabricate readiness.
```

## 10.11 CanonicalSubmissionTrace

Represents traceability from EIB admission to canonical submission.

Candidate fields:

```text
clientRequestRef
interactionAdmissionId
canonicalRequestRef
submittedAt
scNorthboundStatus
warnings
error
```

Rules:

```text
This trace does not expose transport internals as product semantics.
This trace does not prove provider-side effect.
```

---

# 11. Status vocabularies

## 11.1 EffectiveVisibilityStatus

Candidate values:

```text
VISIBLE
HIDDEN_BY_POLICY
HIDDEN_BY_CONTEXT
HIDDEN_UNAVAILABLE
HIDDEN_UNKNOWN
```

## 11.2 EffectiveOperabilityStatus

Candidate values:

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

## 11.3 EffectiveHealthSummary

Candidate values:

```text
HEALTHY
DEGRADED
UNHEALTHY
UNKNOWN
UNKNOWN_PENDING_NORMALIZATION
```

Rules:

```text
EIB may summarize health, but must preserve the fact that SC-C is the health authority.
EIB must not turn UNKNOWN_PENDING_NORMALIZATION into HEALTHY.
```

---

# 12. Request semantics

EIB MUST distinguish:

```text
effective view generated
effective handle issued
request submitted to EIB
request admitted by EIB
request rejected by EIB
request submitted to SC-C Northbound HTTP/OpenAPI
request accepted by SC-C
request deferred by SC-C
request completed semantically by SC-C
request failed semantically by SC-C
transport/delivery failed technically, where applicable later
provider-side failed, where applicable later
```

EIB MUST NOT collapse:

```text
admitted by EIB
accepted by SC-C
dispatched through SC-B
applied by provider
verified by SC-C
```

For product UX, EIB MAY translate these into user-friendly states, but the internal contract must preserve the distinctions.

---

# 13. Interaction with SC-C Northbound HTTP/OpenAPI

## 13.1 Profile A consumption

EIB consumes SC-C Profile A observations to build Effective Views.

It may call the HTTP/OpenAPI counterparts for:

```text
getTopologySnapshot
getTopologyVersion
getDevice
getEndpoint
listRooms
listZones
listDevices
listEndpoints
listDevicesLocatedIn
listEndpointsLocatedIn
getEndpointHealth
getDeviceHealth
getDeviceRuntimeState
getEndpointRuntimeState
getTemporalRuntimeStatus
getNorthboundDiagnostics
```

EIB MUST preserve SC-C uncertainty and unsupported statuses.

Examples:

```text
SC-C getDeviceHealth -> UNKNOWN_PENDING_NORMALIZATION
EIB may show "health unavailable" or "derived status unavailable".
EIB must not fabricate healthy/degraded/unhealthy device state.

SC-C getEndpointRuntimeState -> UNSUPPORTED_PROFILE
EIB may hide endpoint runtime state from ordinary users.
EIB must not fabricate endpoint runtime state.
```

## 13.2 Profile B consumption

EIB may use SC-C Profile B for SC-C-local requests, such as Signal TemporalAct create/cancel.

EIB must perform admission first.

Examples:

```text
create reminder / alarm / timer
cancel visible TemporalAct
list visible TemporalActs
```

EIB MUST NOT treat Profile B as Surface direct access.

## 13.3 Profile C deferral

Profile C operations remain SC-B-gated.

EIB may present discovery or adapter-driven interactions only as deferred/reserved until required contracts exist.

EIB MUST NOT implement discovery by polling adapters or calling SC-D directly.

## 13.4 Profile D ownership

Profile D belongs to EIB / View Composer / product-facing interaction.

SC-C Northbound provides canonical inputs; EIB owns the effective layer.

---

# 14. Treatment of retained HTTP debts

The following debts remain open unless explicitly closed downstream.

```text
DEBT-HTTP-001 — SSE remains SSE-P0 / deferred.
DEBT-HTTP-002 — no auth/authz; local-trusted only.
DEBT-HTTP-003 — Swagger UI and static YAML generation deferred.
DEBT-HTTP-004 — EIB runtime requires own contract/design/descent before implementation.
DEBT-HTTP-005 — gRPC/ConnectRPC binding remains downstream.
DEBT-HTTP-006 — MCP adapter remains separate AI/tool/admin exposure track.
DEBT-HTTP-007 — ACTIVE mode ignores maxResults due to inherited facade behavior.
DEBT-HTTP-008 — HTTP TCK/conformance harness absent.
```

EIB first SDD MUST reference `DEBT-HTTP-004` as its enabling debt.

EIB first SDD SHOULD NOT attempt to close `DEBT-HTTP-001`, `DEBT-HTTP-005` or `DEBT-HTTP-006` unless its scope explicitly changes.

---

# 15. Technology posture

This PDR selects only the **EIB upstream consumption binding**:

```text
EIB consumes SC-C through Northbound HTTP/OpenAPI for first descent.
```

This PDR does not select:

```text
EIB product-facing API technology
EIB internal module framework
GraphQL schema
WebSocket channel
SSE channel
MCP tools/resources/prompts
gRPC/ConnectRPC service
mobile local API
cloud relay
```

Placement constraints:

```text
GraphQL, if used, belongs at EIB/View Composer, not SC-C.
WebSocket/SSE, if used for live surfaces, belongs at EIB/Surface boundary or an explicitly defined external exposure boundary.
MCP, if used, belongs to AI/tool/admin facade over EIB or diagnostics, not primary EIB product API.
gRPC/ConnectRPC may be considered for future EIB service-to-service boundaries.
HTTP/OpenAPI is already selected for EIB -> SC-C first consumption.
```

---

# 16. Non-goals

This PDR does not implement or specify:

- EIB runtime implementation;
- View Composer implementation;
- SC-C code changes;
- SC-C Northbound changes;
- new HTTP routes in SC-C;
- SSE streams;
- WebSocket sessions;
- GraphQL schema/resolvers;
- MCP tools/resources/prompts;
- gRPC services;
- ConnectRPC services;
- Hub implementation;
- SApp implementation;
- Surface rendering;
- Session store;
- Identity provider;
- Authority engine;
- Policy engine;
- SC-B runtime;
- NATS / JetStream binding;
- SC-D adapter runtime;
- Discovery E2E implementation;
- command-to-adapter E2E implementation;
- outbox dispatcher.

Concrete implementation belongs to downstream SDD, CSA, MIR and execution package.

---

# 17. Acceptance criteria

This PDR is acceptable if:

- AC-EIB-001: Defines EIB as the product-facing membrane above SC-C Northbound.
- AC-EIB-002: Preserves SC-C as canonical state authority.
- AC-EIB-003: Records that EIB is not co-located with SC-C.
- AC-EIB-004: Requires EIB to consume SC-C through the validated Northbound HTTP/OpenAPI binding for first descent.
- AC-EIB-005: Forbids EIB from calling `ScCoreNorthboundFacade` directly.
- AC-EIB-006: Forbids EIB from importing SC-C repositories, internal ports or persistence adapters.
- AC-EIB-007: Preserves that Hub, SApp and Surfaces consume EIB, not SC-C directly.
- AC-EIB-008: Separates Base Topology from Effective View.
- AC-EIB-009: Defines View Composer as EIB read/projection facet, not SC-C.
- AC-EIB-010: Defines EIB responsibilities for effective projection and request admission.
- AC-EIB-011: Prevents EIB from owning canonical topology, topologyVersion, canonical IDs, health authority or persistence.
- AC-EIB-012: Prevents EIB from becoming SC-B transport or SC-D adapter runtime.
- AC-EIB-013: Prevents EIB from becoming Session, Identity, Authority or Policy source of truth.
- AC-EIB-014: Allows EIB to consume Session / Identity / Authority / Policy context.
- AC-EIB-015: Defines operation profiles for effective read projection, SC-C-local TemporalAct request admission, runtime observation, live updates, diagnostics and AI/tool/admin deferral.
- AC-EIB-016: Defines initial data contract families for Effective Views, affordances, handles, diagnostics and admission decisions.
- AC-EIB-017: Requires effective views to be derived from SC-C canonical observations and contextual inputs.
- AC-EIB-018: Requires effective views to preserve `sourceTopologyVersion` or equivalent source consistency marker.
- AC-EIB-019: Requires effective action handles to be opaque, contextual and revalidated at request time.
- AC-EIB-020: Requires EIB to distinguish admitted-by-EIB from accepted/completed by SC-C.
- AC-EIB-021: Requires EIB to preserve `ScNorthboundResponse<T>` status/error/warning semantics internally.
- AC-EIB-022: Requires EIB to preserve unsupported/deferred/pending-normalization states honestly and to handle NOT_FOUND / INTERNAL_ERROR without silent semantic collapse.
- AC-EIB-023: Preserves SC-B-gated status for cross-plane operations.
- AC-EIB-024: Forbids EIB from directly invoking SC-D adapters.
- AC-EIB-025: Requires `DEBT-HTTP-004` to be referenced by the first EIB SDD.
- AC-EIB-026: Keeps `DEBT-HTTP-001`, `DEBT-HTTP-005` and `DEBT-HTTP-006` open unless a downstream artifact explicitly changes scope.
- AC-EIB-027: Forbids GraphQL/MCP/WebSocket/gRPC/ConnectRPC implementation as part of this PDR.
- AC-EIB-028: Records downstream SDD/CSA/MIR work before implementation descent.
- AC-EIB-029: Requires `canonicalDeviceId`, `canonicalEndpointId` and `canonicalTemporalActId` to be exposed only under explicit diagnostic/admin context with authorization confirmation.

---

# 18. Open questions

## OQ-EIB-001 — EIB runtime placement

Should first EIB implementation be:

```text
separate JVM service
separate service in another language
Hub-adjacent module
local appliance module
```

Current recommendation:

```text
Do not co-locate EIB with SC-C.
Do not decide implementation technology in this PDR.
Decide in SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 or a narrow ADR if needed.
```

## OQ-EIB-002 — View Composer split

Should View Composer be a separate module/service or an internal EIB component?

Current recommendation:

```text
Treat View Composer as EIB read/projection facet.
Defer implementation split to SDD.
```

## OQ-EIB-003 — Authority / Policy integration shape

Should EIB call an external Authority/Policy service, consume decisions from Hub, or evaluate local policy rules?

Current recommendation:

```text
EIB may consume authority/policy inputs, but should not own identity or policy source of truth in this PDR.
```

## OQ-EIB-004 — Effective action handle lifetime

How long should effective action handles remain valid?

Current recommendation:

```text
Bind handles to sourceTopologyVersion and context.
Expire or reject handles on stale topology, changed policy context or missing capability.
```

## OQ-EIB-005 — Effective view live updates

Should live updates use SSE, WebSocket, GraphQL subscriptions or polling?

Current recommendation:

```text
Do not decide here.
The choice belongs to EIB SDD / product-facing exposure ADR.
Do not depend on SC-C SSE until DEBT-HTTP-001 is closed.
```

## OQ-EIB-006 — GraphQL placement

Should GraphQL be used for Effective Habitat View?

Current recommendation:

```text
GraphQL is allowed only at EIB/View Composer if a downstream ADR/SDD selects it.
GraphQL must not be SC-C Northbound.
```

## OQ-EIB-007 — MCP/admin placement

Should MCP expose EIB tools?

Current recommendation:

```text
Possible future AI/tool/admin facade.
Not part of first EIB product membrane implementation.
```

## OQ-EIB-008 — Discovery interaction

Should EIB expose discovery UX before SC-B discovery runtime exists?

Current recommendation:

```text
Only as deferred/reserved affordance.
Do not implement discovery execution until SC-B/SC-D contracts exist.
```

## OQ-EIB-009 — HTTP client strategy

Should EIB use generated OpenAPI client code or hand-written HTTP client code?

Current recommendation:

```text
Decide in SDD.
The PDR only requires consumption through the validated SC-C Northbound HTTP/OpenAPI binding.
```

---

# 19. Downstream artifacts

Required downstream artifacts:

```text
SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001
CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
```

Likely downstream artifacts:

```text
PDR-SOV-SC-VIEW-COMPOSER-BOUNDARY-001, revise under EIB framing
SDD-SOV-SC-VIEW-COMPOSER-001, revise under EIB framing
PDR-SOV-SC-HUB-SC-BOUNDARY-001
PDR-SOV-SC-TOPOLOGY-PROJECTION-BOUNDARY-001, if not absorbed by EIB/VC
PDR-SOV-SC-DIAGNOSTIC-ADMIN-BOUNDARY-001
PDR-SOV-SC-MCP-FACADE-001, future / outside primary product path
TCK-SOV-SC-EIB-CONFORMANCE-001, future
```

Implementation descent is not yet authorized.

Before a MIR, the corpus must close at least:

```text
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001
SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001
CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001
```

---

# 20. Preliminary seed profile for future MIR

This PDR does not authorize implementation, but it identifies a likely first EIB seed profile.

Potential seed scope:

```text
Profile EIB-A — Effective read projection:
  Effective topology, rooms, zones, devices, endpoints, capabilities.
  Effective health summary derived from canonical SC-C health observations.
  Effective runtime observation for device and endpoint states.
  Effective diagnostics.
  Effective TemporalAct observation (Profile EIB-C partial):
    listEffectiveTemporalActs with contextual visibility filtering.
    getEffectiveTemporalAct.
    EffectiveTemporalActView with visibilityStatus and operabilityStatus.

Profile EIB-B — Effective request admission (SC-C-local only):
  admitTemporalSignalRequest — maps to SC-C createSignalTemporalAct.
  admitTemporalCancellation — maps to SC-C cancelTemporalAct.

Explicitly deferred from seed:
  admitActionIntent over device/endpoint — SC-B-gated; deferred until cross-plane contracts exist.
  admitDiscoveryIntent — SC-B-gated; deferred.
  Live updates (Profile EIB-D) — deferred; technology decision belongs to SDD/ADR.
  View Composer as separate service — deferred; internal EIB component in seed.
  GraphQL / MCP / gRPC / WebSocket / SSE — not in seed.
  Authority / Policy integration — consumed as context inputs if available; no system-of-record ownership in seed.
```

Potential validation target:

```text
Given a set of SC-C Northbound HTTP/OpenAPI responses,
EIB can produce a deterministic EffectiveHabitatView including effective TemporalAct observations,
without mutating SC-C and without losing canonical uncertainty.

EIB can admit and submit a Signal TemporalAct request and a cancellation request
through the canonical SC-C lifecycle.
```

---

# 21. Versioning decision

Proposed lifecycle:

```text
v0.2.0-draft
  post-MU-021 contract update

v0.3.0-candidate
  after review patch H-001 / H-002 / H-003: envelope preservation, TemporalAct seed inclusion and diagnostic/admin canonical-ID exposure rules

v1.0.0-accepted
  after SDD-readiness review
```

---

# 22. Summary rule

```text
SC-C answers what is canonically true.
EIB answers what is effectively visible, operable and admissible in context.
View Composer composes the read side of that answer.
Hub, SApp and Surfaces consume the answer, not SC-C.
EIB consumes SC-C through validated Northbound HTTP/OpenAPI, not through in-process Core internals.
```
