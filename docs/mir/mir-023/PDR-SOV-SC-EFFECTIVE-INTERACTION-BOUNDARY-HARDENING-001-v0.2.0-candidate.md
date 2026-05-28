# PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001

## Effective Interaction Boundary Hardening

```text
Document ID:  PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
Title:        Effective Interaction Boundary Hardening
Version:      v0.2.0-candidate
Status:       Candidate
Date:         2026-05-27
Corpus:       Sovereign Connect
Type:         PDR
Plane:        SC-X / EIB
Scope:        Contract-level hardening requirements for the validated EIB initial
              implementation slice before SC-B runtime integration and before
              View Composer / SApp / Surface-facing descent.
```

---

## Changelog

### v0.2.0-candidate

Candidate promotion patch.

This version:

1. Adds explicit `DEFERRED_UNSUPPORTED_PROFILE` admission mapping.
2. Strengthens `DEBT-EIB-015` from optional closure/defer to mandatory closure in this hardening descent.
3. Records `ADMITTED` synchronous HTTP 200 vs asynchronous HTTP 202 as a downstream SDD decision, while preserving the rule that HTTP 202 is allowed only for true deferred responsibility.
4. Records `GET /temporal-acts/{temporalActId}` activation vs latent-client-route behavior as a downstream SDD decision.
5. Promotes the PDR to candidate after review corrections.

### v0.1.0-draft

Initial draft.

This version:

1. Defines EIB hardening as the immediate post-MU-022 target.
2. Clarifies that EIB hardening is not product-facing construction and not SC-B implementation.
3. Records timeout enforcement as a required operational boundary hardening item.
4. Defines admission response vocabulary hardening and rejects top-level `ACCEPTED`/HTTP `202` unless EIB has truly accepted responsibility for later completion.
5. Requires diagnostic/admin gating boundary tests for the combined header + configuration condition.
6. Requires upstream HTTP route boundary regression against the validated MU-021 SC-C Northbound HTTP/OpenAPI surface.
7. Requires retained EIB debt disposition by downstream gate.
8. Establishes the next descent path: SDD, CSA, MIR, execution package.

---

## Depends on

- `PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.3.0-candidate`
- `SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.2.0-candidate`
- `CSA-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-001 v0.1.1-merged`
- `MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001 v1.0.0-accepted`
- `MIR-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001 v1.0.0-accepted`
- `PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.16-draft`
- `INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.22-draft`
- `SYNC-SOV-SC-MU-BACKLOG-001 v0.1.23-draft`

## Related

- `ADR-SOV-SC-C-NORTHBOUND-EXPOSURE-BINDING-001 v0.1.1-draft`
- `SDD-SOV-SC-C-NORTHBOUND-HTTP-SSE-001 v0.2.0-candidate`
- `REVIEW-MU-022-EFFECTIVE-INTERACTION-BOUNDARY-SEED`
- `REVIEW-MU-022-ROUTING-FIX-PATCH-HALLAZGOS`
- `review-MU-022-failure-boundary-patch-2-implementation`
- `docs/mir/mir-022/`

---

# 0. Purpose

This PDR defines the hardening requirements for the Effective Interaction Boundary
(`EIB`) after the initial implementation slice has been validated at L4.

The purpose is to make EIB a reliable runtime boundary before it becomes a dependency
for:

```text
SC-B runtime integration;
EIB action/discovery admission;
View Composer;
SApp;
Surface-facing construction.
```

This PDR does not implement EIB.

It does not authorize code changes directly.

It defines the contract-level hardening obligations that a downstream SDD, CSA, MIR
and execution package must satisfy.

---

# 1. Baseline

This PDR assumes the following validated baseline:

```text
MU-021 — SC-C Northbound HTTP/OpenAPI Binding Seed
  Status: Validated L4
  Result: SC-C Northbound is externally consumable through HTTP/OpenAPI
          for technical consumers.

MU-022 — Effective Interaction Boundary Initial Implementation Slice
  Status: Validated L4
  Result: EIB exists as a separate runtime consumer of SC-C Northbound HTTP/OpenAPI.
```

The current EIB implementation slice validates:

```text
- separate runtime placement;
- no direct SC-C in-process consumption;
- EIB-owned mirror DTOs;
- effective refs;
- effective topology/device/endpoint/temporal views;
- TemporalAct observation;
- Signal TemporalAct admission/cancellation;
- canonical envelope preservation;
- provider-native ID suppression;
- upstream failure normalization for the implemented slice.
```

However, the slice retains operational and semantic fragilities that should not be
allowed to leak into later SC-B or product-facing descent.

---

# 2. Strategic thesis

The immediate target after MU-022 is **not** View Composer, SApp or Surface-facing
construction.

The immediate target is also not direct SC-B implementation.

The correct next target is EIB hardening.

Rationale:

```text
EIB is now a runtime boundary.
A runtime boundary that downstream domains will traverse must be hardened before
those domains build assumptions on top of it.
```

Canonical sequence:

```text
EIB hardening
  ↓
SC-B runtime / abstract bus seed + technology descent
  ↓
EIB continuation with action/discovery admission, SC-B-gated
  ↓
View Composer / SApp / Surface
```

EIB hardening is therefore part of the path toward SC industrial-grade. It is not a
detour into product-facing UX.

---

# 3. Non-goals

This PDR does not define or authorize:

```text
SC-B runtime implementation;
NATS / JetStream / physical broker binding;
SC-D adapter runtime;
action execution over devices/endpoints;
discovery execution;
View Composer implementation;
SApp implementation;
Surface-facing API;
GraphQL;
MCP;
gRPC / ConnectRPC;
live updates;
real Authority / Policy / Identity / Session integration;
persistent effective-ref registry;
EIB conformance/TCK.
```

This PDR may classify these as downstream debts or blockers, but it does not close
them unless explicitly stated.

---

# 4. Boundary invariants

## 4.1 SC-C authority remains unchanged

```text
SC-C owns canonical state.
SC-C owns Base Topology.
SC-C owns TemporalActs.
SC-C owns canonical Northbound responses.
```

EIB consumes SC-C through HTTP/OpenAPI and does not become SC-C authority.

## 4.2 EIB remains a runtime membrane

EIB owns effective interaction semantics:

```text
effective visibility;
effective operability;
admission vocabulary;
product-safe status exposure;
diagnostic/admin gating;
canonical trace preservation;
translation between effective refs and canonical refs, where permitted.
```

## 4.3 EIB must not hide canonical uncertainty

EIB may translate canonical statuses into product-safe language, but must not
fabricate certainty.

In particular:

```text
UNSUPPORTED_PROFILE must not be presented as supported.
UNKNOWN_PENDING_NORMALIZATION must not be presented as known.
DEFERRED_SC_B_REQUIRED must not be presented as completed or admitted.
UPSTREAM_UNAVAILABLE must not be collapsed into NOT_FOUND.
```

## 4.4 HTTP 202 is not a generic success wrapper

EIB MUST NOT use HTTP `202 Accepted` or top-level `EibResponse.status = ACCEPTED`
merely because EIB produced a response.

HTTP `202` is allowed only when EIB has accepted responsibility for continuing
processing later.

If EIB rejects, cannot admit, cannot see, cannot normalize, or cannot reach SC-C,
the top-level response must say so.

---

# 5. Problem statement

MU-022 validated the existence of EIB as a separate runtime boundary. It did not fully
harden that boundary.

Three fragility classes must be addressed before SC-B or View Composer / SApp / Surface
layers depend on EIB.

## 5.1 Operational boundary fragility

`sc.eib.northbound.timeout-ms` exists as configuration but is not enforced by the
upstream HTTP client.

A configured but unenforced timeout creates false operational confidence.

In real local-first deployments, SC-C may be slow, temporarily unavailable, or under
load. EIB must not wait indefinitely.

## 5.2 Admission semantic fragility

The current admission response mapping is too coarse if it treats every non-upstream
failure as `ACCEPTED` at the top-level `EibResponse`.

A product or downstream technical consumer should not have to inspect deep payload
fields to discover that admission was rejected, deferred or impossible.

The top-level EIB response vocabulary must reflect the EIB admission result.

This includes cancellation effective-ref resolution: if `listTemporalActs` returns a
non-success semantic envelope, EIB MUST propagate that semantic outcome instead of
resolving against an empty list and returning `REJECTED_NOT_VISIBLE`.

## 5.3 Diagnostic/admin gating fragility

Diagnostic/admin mode depends on a conjunctive rule:

```text
diagnostic mode requested by request context
AND
diagnostic/admin enabled by configuration / policy context
```

Both conditions must be necessary.

Tests must prove the full truth table.

## 5.4 Upstream route regression fragility

MU-022 already exposed the risk of tests validating mock routes that do not exist in
SC-C MU-021.

EIB must have regression tests that positively assert the actual emitted upstream
request paths and query strings for the required SC-C routes.

---

# 6. Hardening goals

## G-EIB-H-001 — Timeout enforcement

EIB MUST enforce upstream SC-C timeout configuration.

The downstream SDD must define the exact implementation strategy, but the expected
approach is:

```text
RestClient configured with a ClientHttpRequestFactory or equivalent mechanism;
connect timeout derived from sc.eib.northbound.timeout-ms;
read timeout derived from sc.eib.northbound.timeout-ms;
timeout failures mapped to EibUpstreamUnavailableException;
EibUpstreamUnavailableException mapped to EibResponse.status = UPSTREAM_UNAVAILABLE.
```

The timeout property must not remain decorative.

## G-EIB-H-002 — Admission response vocabulary hardening

EIB admission responses MUST expose a truthful top-level status.

The admission response mapper must not use a generic top-level `ACCEPTED` for rejected,
deferred, pending-normalization or upstream-unavailable outcomes.

This PDR introduces the following required admission response vocabulary:

```text
ADMITTED
NOT_VISIBLE
INVALID_REQUEST
DEFERRED
PENDING_NORMALIZATION
UNSUPPORTED
UPSTREAM_UNAVAILABLE
FAILED
```

The downstream SDD may refine names if necessary, but it must preserve the semantic
separations.

## G-EIB-H-003 — Diagnostic/admin gating hardening

EIB MUST prove that diagnostic/admin visibility requires both:

```text
request diagnostic mode requested;
configuration / policy context authorizes diagnostic/admin exposure.
```

Canonical IDs and diagnostic-only data MUST NOT appear when only one condition is true.

Provider-native IDs MUST NOT appear in ordinary or diagnostic product views unless a
later diagnostic artifact explicitly authorizes provider-native diagnostic exposure.

## G-EIB-H-004 — Upstream route boundary regression

EIB MUST include positive upstream route regression tests for the required SC-C routes.

The tests must observe actual emitted HTTP request paths and query strings.

They must not only verify the absence of formerly incorrect routes.

## G-EIB-H-005 — Retained debt disposition

The downstream SDD/MIR must classify retained EIB debts by downstream blocker status.

At minimum, each debt must be classified as one of:

```text
accepted-for-now
requires-resolution-before-SC-B
requires-resolution-before-EIB-action-admission
requires-resolution-before-View-Composer
requires-resolution-before-SApp-Surface
requires-resolution-before-industrial
closed-by-this-hardening
```

---

# 7. Admission response mapping

## 7.1 Required principle

The top-level `EibResponse.status` must describe the EIB result, not merely the fact
that EIB returned an HTTP body.

## 7.2 Normative mapping table

| Admission decision / condition | HTTP status | EibResponse.status | Rule |
|---|---:|---|---|
| `ADMITTED` | 202 | `ADMITTED` | Allowed only when EIB has accepted responsibility for later processing or submission has been accepted into the canonical lifecycle. |
| `REJECTED_NOT_VISIBLE` | 404 | `NOT_VISIBLE` | Entity/action is not visible in the effective context. Must not appear as accepted. |
| `REJECTED_INVALID_REQUEST` | 400 | `INVALID_REQUEST` | Request shape, effective ref or admissibility input is invalid. |
| `DEFERRED_SC_B_REQUIRED` | 503 | `DEFERRED` | Required cross-plane capability is unavailable/not implemented. Not admitted. |
| `DEFERRED_PENDING_NORMALIZATION` | 409 | `PENDING_NORMALIZATION` | EIB cannot safely admit because canonical/effective state is uncertain or pending normalization. |
| `DEFERRED_UNSUPPORTED_PROFILE` | 501 | `UNSUPPORTED` | Required upstream canonical profile is explicitly unsupported. Must not be collapsed into generic success or generic accepted. |
| `FAILED_UPSTREAM_UNAVAILABLE` | 503 | `UPSTREAM_UNAVAILABLE` | SC-C transport/runtime boundary unavailable. Must not be collapsed into `NOT_FOUND`. |
| unclassified internal EIB failure | 500 | `FAILED` | Must preserve trace internally and avoid leaking implementation internals. |

## 7.3 HTTP 202 restriction

HTTP `202` is reserved for cases where EIB actually admits or accepts responsibility for
later processing.

Examples that MUST NOT return HTTP `202`:

```text
REJECTED_NOT_VISIBLE
REJECTED_INVALID_REQUEST
DEFERRED_SC_B_REQUIRED
DEFERRED_PENDING_NORMALIZATION
DEFERRED_UNSUPPORTED_PROFILE
FAILED_UPSTREAM_UNAVAILABLE
```

This keeps the API honest for clients that look only at HTTP status and top-level
`EibResponse.status`.

## 7.4 SDD-required admission clarifications

The downstream SDD MUST resolve two design questions without weakening §7.2 and §7.3.

### 7.4.1 Synchronous admitted outcomes

The table in §7.2 uses HTTP `202` for `ADMITTED` only when EIB has accepted responsibility
for later processing or when submission has entered a canonical lifecycle that continues
after the response.

If a hardening implementation can prove that a specific admission operation is fully
synchronous and terminal from EIB's perspective, the SDD MAY map that operation-specific
`ADMITTED` result to HTTP `200` instead of HTTP `202`.

The SDD MUST NOT use HTTP `202` merely because a request passed validation.

### 7.4.2 `getTemporalAct` route activation

`GET /sc/v1/habitats/{habitatId}/temporal-acts/{temporalActId}` exists in the SC-C
Northbound HTTP/OpenAPI binding and in the EIB upstream client surface. The current
EIB effective-ref strategy may still use list+match for effective-ref resolution.

The downstream SDD MUST explicitly decide whether this route becomes active in the
hardening descent or remains a latent client capability. If it remains latent, route
regression may test the client method directly, but the implementation report MUST say
that production `getEffectiveTemporalAct` still uses list+match.

---

# 8. Timeout enforcement requirements

## 8.1 Configuration source

The existing EIB configuration surface includes:

```yaml
sc:
  eib:
    northbound:
      base-url: http://localhost:8080/sc/v1
      timeout-ms: 2000
```

The hardening descent must make `timeout-ms` operational.

## 8.2 Required behavior

When SC-C does not connect or does not respond within the configured timeout, EIB must
return:

```text
HTTP 503
EibResponse.status = UPSTREAM_UNAVAILABLE
error.source = eib.upstream
```

or an equivalent source-bearing EIB error shape defined by the SDD.

## 8.3 Required tests

The downstream implementation must include a test that proves a timeout produces
`EibUpstreamUnavailableException` or the downstream equivalent.

A controller/API test must prove the exception is normalized into `EibResponse<T>`.

---

# 9. Diagnostic/admin gating requirements

## 9.1 Required truth table

The implementation must satisfy:

| Request diagnostic header | Config/admin authorization | Result |
|---|---|---|
| absent / false | false | ordinary mode |
| true | false | ordinary mode |
| absent / false | true | ordinary mode |
| true | true | diagnostic/admin mode |

## 9.2 Required protections

In ordinary mode:

```text
canonicalDeviceId MUST NOT be exposed.
canonicalEndpointId MUST NOT be exposed.
canonicalTemporalActId MUST NOT be exposed.
providerDeviceId MUST NOT be exposed.
providerEndpointId MUST NOT be exposed.
```

In diagnostic/admin mode:

```text
canonical IDs MAY be exposed if authorized by context.
provider-native IDs remain suppressed unless a later diagnostic/admin artifact explicitly authorizes them.
```

## 9.3 Required tests

The downstream implementation must include tests for all truth-table rows.

Positive-only diagnostic tests are insufficient.

---

# 10. Upstream route regression requirements

## 10.1 Required route set

EIB must positively verify the actual emitted upstream request path and query string for
the following SC-C MU-021 routes:

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

## 10.2 Testing requirement

Tests must observe emitted HTTP requests.

A mock service that merely returns data for a hardcoded fantasy path is insufficient.

Acceptable approaches include:

```text
MockRestServiceServer, if bound before constructing the RestClient under test;
MockWebServer / WireMock / equivalent mock HTTP server;
other mechanism that records emitted request path and query string.
```

The downstream CSA must decide the exact test mechanism.

---

# 11. Debt disposition

## 11.1 Debt normalization note

Prior governance and CSA notes have used overlapping numeric debt IDs for EIB debts.
This PDR preserves user-facing debt names where known and requires downstream SYNC/INDEX
updates to normalize aliases if needed.

The semantic debt meaning is authoritative over numeric drift.

## 11.2 Required disposition table

| Debt | Meaning | Required classification | Disposition in this hardening track |
|---|---|---|---|
| `DEBT-EIB-014` | Upstream timeout configured but not enforced | `requires-resolution-before-SC-B` | MUST close in this hardening descent. |
| `DEBT-EIB-015` / cancellation non-success envelope debt | Cancellation effective-ref resolution may collapse non-success `listTemporalActs` envelopes to not-visible | `requires-resolution-before-EIB-action-admission` | MUST close in this hardening descent by propagating non-success `listTemporalActs` envelopes before effective-ref resolution. |
| `DEBT-EIB-012` | Persistent effective-ref registry absent | `accepted-for-now` / `requires-resolution-before-scale` | Deferred. List+match remains acceptable for the current slice. |
| `DEBT-EIB-003` | Authority/Policy/Identity/Session real integration absent | `requires-resolution-before-SApp-Surface` | Deferred. Boundary tests must preserve ordinary-mode safety. |
| `DEBT-EIB-013` | Auth/authz remains stubbed or config-gated | `requires-resolution-before-SApp-Surface` | Deferred. Diagnostic/admin gating tests required now. |
| `DEBT-EIB-010` | EIB conformance/TCK absent | `requires-resolution-before-industrial` | Deferred unless later hardening expands scope. |
| `DEBT-EIB-004` | Device/endpoint action admission remains SC-B-gated | `requires-resolution-before-EIB-action-admission` | Deferred until SC-B runtime exists. |
| `DEBT-EIB-005` | Discovery admission remains SC-B-gated | `requires-resolution-before-EIB-action-admission` | Deferred until SC-B runtime exists. |
| `DEBT-EIB-006` | Live updates deferred | `requires-resolution-before-View-Composer` or later | Deferred. |
| `DEBT-EIB-011` | Durable audit/admission ledger absent | `requires-resolution-before-industrial` | Deferred unless SDD scopes otherwise. |

---

# 12. Acceptance criteria

The downstream SDD/MIR must produce acceptance criteria at least equivalent to the
following.

## 12.1 Timeout enforcement

```text
AC-EIB-H-001 — sc.eib.northbound.timeout-ms is applied to the upstream HTTP client.
AC-EIB-H-002 — Connect timeout against SC-C produces EibUpstreamUnavailableException or equivalent.
AC-EIB-H-003 — Read timeout against SC-C produces EibUpstreamUnavailableException or equivalent.
AC-EIB-H-004 — Timeout failure returns EibResponse.status = UPSTREAM_UNAVAILABLE and HTTP 503 at API boundary.
```

## 12.2 Admission response vocabulary

```text
AC-EIB-H-005 — ADMITTED maps to HTTP 202 and EibResponse.status = ADMITTED only when EIB has accepted responsibility for later processing or canonical lifecycle continuation; the SDD may map fully synchronous operation-specific ADMITTED results to HTTP 200.
AC-EIB-H-006 — REJECTED_NOT_VISIBLE maps to HTTP 404 and EibResponse.status = NOT_VISIBLE.
AC-EIB-H-007 — REJECTED_INVALID_REQUEST maps to HTTP 400 and EibResponse.status = INVALID_REQUEST.
AC-EIB-H-008 — DEFERRED_SC_B_REQUIRED maps to HTTP 503 and EibResponse.status = DEFERRED.
AC-EIB-H-009 — DEFERRED_PENDING_NORMALIZATION maps to HTTP 409 and EibResponse.status = PENDING_NORMALIZATION.
AC-EIB-H-010 — DEFERRED_UNSUPPORTED_PROFILE maps to HTTP 501 and EibResponse.status = UNSUPPORTED.
AC-EIB-H-011 — FAILED_UPSTREAM_UNAVAILABLE maps to HTTP 503 and EibResponse.status = UPSTREAM_UNAVAILABLE.
AC-EIB-H-012 — No rejected/deferred/upstream-unavailable/unsupported admission result returns top-level EibResponse.status = ACCEPTED.
AC-EIB-H-013 — HTTP 202 is used only when EIB has accepted responsibility for later processing.
```

## 12.3 Diagnostic/admin gating

```text
AC-EIB-H-014 — header absent/false + config disabled yields ordinary mode.
AC-EIB-H-015 — header true + config disabled yields ordinary mode.
AC-EIB-H-016 — header absent/false + config enabled yields ordinary mode.
AC-EIB-H-017 — header true + config enabled yields diagnostic/admin mode.
AC-EIB-H-018 — ordinary mode suppresses canonicalDeviceId, canonicalEndpointId and canonicalTemporalActId.
AC-EIB-H-019 — ordinary mode suppresses providerDeviceId and providerEndpointId.
```

## 12.4 Upstream route regression

```text
AC-EIB-H-020 — Tests positively verify the emitted GET /topology request.
AC-EIB-H-021 — Tests positively verify the emitted GET /topology/version request.
AC-EIB-H-022 — Tests positively verify emitted device health/runtime-state routes.
AC-EIB-H-023 — Tests positively verify emitted endpoint health/runtime-state routes.
AC-EIB-H-024 — Tests positively verify emitted GET /diagnostics request.
AC-EIB-H-025 — Tests positively verify emitted GET /temporal-acts?mode=ACTIVE&maxResults=50 request.
AC-EIB-H-026 — Tests positively verify emitted GET /temporal-acts/{temporalActId} request or explicitly record it as a latent client capability if the SDD keeps production effective-ref resolution on list+match.
AC-EIB-H-027 — Tests positively verify emitted POST /temporal-acts request.
AC-EIB-H-028 — Tests positively verify emitted POST /temporal-acts/{temporalActId}/cancel request.
AC-EIB-H-029 — Tests fail if fantasy routes such as /topology/snapshot, /runtime-state/{subjectId}, ?status=&limit=, or upstream /temporal-acts/signal are reintroduced.
```

## 12.5 Debt disposition and evidence

```text
AC-EIB-H-030 — Implementation report classifies retained debts by downstream blocker status.
AC-EIB-H-031 — DEBT-EIB-014 is closed if timeout enforcement is implemented and tested.
AC-EIB-H-032 — DEBT-EIB-015 is closed by propagating non-success `listTemporalActs` envelopes before effective-ref resolution.
AC-EIB-H-033 — The implementation report records test evidence and commits.
```

---

# 13. Downstream artifact requirements

## 13.1 Next artifact

The next artifact is:

```text
SDD-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
```

It must convert this PDR into implementable design.

## 13.2 CSA requirement

A post-SDD / pre-MIR CSA is required.

The CSA must inspect the current EIB code surface and verify:

```text
actual RestClient construction;
current timeout configuration path;
current admissionResponse mapper;
current diagnostic/admin context logic;
current route tests;
current EibResponse<T> shape;
current controller response status mapping;
current retained debt registry.
```

## 13.3 MIR requirement

A MIR is required before implementation.

Candidate MIR name:

```text
MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
```

Candidate MU name:

```text
MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
```

Candidate operational slot:

```text
MU-023
```

The operational slot may be adjusted by INDEX/SYNC if governance chooses a different
slot ordering.

---

# 14. Risks

## RISK-EIB-H-001 — Over-hardening into product-facing scope

Hardening must not become View Composer, SApp or Surface construction.

Mitigation:

```text
Keep scope limited to timeout enforcement, admission vocabulary, diagnostic/admin gating,
route regression and debt disposition.
```

## RISK-EIB-H-002 — Premature SC-B semantics

The PDR mentions `DEFERRED_SC_B_REQUIRED`, but must not implement SC-B.

Mitigation:

```text
Represent SC-B absence honestly as DEFERRED, not as an implemented delivery path.
```

## RISK-EIB-H-003 — Status vocabulary drift

If EIB invents product statuses without mapping discipline, downstream consumers will
interpret responses inconsistently.

Mitigation:

```text
Define explicit EibResponse.status values and HTTP status mapping in the SDD.
```

## RISK-EIB-H-004 — Test route fantasy recurrence

Tests may again validate mock-only routes.

Mitigation:

```text
Positive emitted-request regression tests are mandatory.
```

---

# 15. Final dictum

```text
PDR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate
is accepted as the candidate post-MU-022 hardening contract.
```

This PDR establishes that EIB hardening is required before SC-B runtime descent and
before View Composer / SApp / Surface-facing construction.

The hardening scope is deliberately narrow:

```text
timeout enforcement;
admission response vocabulary;
diagnostic/admin gating;
upstream route regression;
retained debt disposition.
```

It does not implement SC-B, View Composer, SApp, Surface APIs or product-facing UX.

