# Acceptance Map — MU-022 Effective Interaction Boundary Initial Implementation Slice

```text
Document ID:  ACCEPTANCE-MAP-SOV-SC-EIB-MIR-022
Version:      v0.3.1-reviewed
Status:       Execution package acceptance map
MU:           MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
```

---

## 0. Purpose

This map translates MIR acceptance criteria into implementation evidence and test
expectations. A criterion passes only if the implementation report records PASS and
the cited test/evidence exists and asserts the stated property.

---

## 1. Summary gates

```text
Gate A — Placement:    EIB is a separate runtime boundary, preferably top-level eib/.
Gate B — Upstream:     EIB consumes SC-C through HTTP/OpenAPI only; no SC-C Java imports.
Gate C — DTOs:         EIB owns mirror DTOs; providerDeviceId/providerEndpointId suppressed.
Gate D — Envelope:     All 12 SC-C statuses and source fields are preserved internally.
Gate E — Projection:   sourceTopologyVersion, rooms/zones, devices/endpoints and temporalActs
                       are populated correctly; healthSummary defaults documented.
Gate F — IDs:          Canonical and provider-native IDs are gated/suppressed correctly.
Gate G — Temporal:     create/cancel Signal TemporalActs work through SC-C HTTP lifecycle;
                       effectiveRef generated after create; null after cancel.
Gate H — Failure:      Network failures are distinct from semantic SC-C failures;
                       503 disambiguation tested.
Gate I — Negative:     No action/discovery/live updates/non-HTTP bindings introduced.
Gate J — Evidence:     Tests, implementation report and debts are recorded.
```

---

## 2. Acceptance criteria matrix

| AC | Requirement | Required evidence / test methods | Status |
|---|---|---|---|
| `AC-022-001` | MIR identifies `MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001` as `MU-022`. | implementation-report.md §1 (MU identity recorded); project layout under eib/ | Pending |
| `AC-022-002` | Implementation creates a separate EIB runtime boundary, preferably under top-level `eib/`. | eib/pom.xml exists; EibApplication under com.sovereign.eib; implementation-report §placement | Pending |
| `AC-022-003` | EIB is not registered in the SC-C Spring application context. | EibArchitectureTest.eibDoesNotImportScCCoreOrAdapterPackages; implementation-report §placement | Pending |
| `AC-022-004` | EIB does not import `com.sovereign.connect.core.*`. | EibArchitectureTest.eibDoesNotImportScCCoreOrAdapterPackages | Pending |
| `AC-022-005` | EIB does not inject or reference `ScCoreNorthboundFacade`. | EibArchitectureTest.eibDoesNotImportScCCoreOrAdapterPackages (ScCoreNorthboundFacade in forbidden list) | Pending |
| `AC-022-006` | EIB consumes SC-C only through HTTP/OpenAPI base URL configuration. | EibScNorthboundClientTest (all calls go through RestClient); EibArchitectureTest | Pending |
| `AC-022-007` | `EibScNorthboundClient` or equivalent explicit upstream client boundary exists. | EibScNorthboundClient.java interface exists; RestClientEibScNorthboundClient implements it | Pending |
| `AC-022-008` | Upstream client uses EIB-owned mirror DTOs and does not import SC-C Northbound Java DTOs. | EibArchitectureTest.eibDoesNotImportScCCoreOrAdapterPackages; all Dto records in eib.northbound.dto | Pending |
| `AC-022-009` | `sc.eib.northbound.base-url` and `sc.eib.northbound.timeout-ms` are configurable with test defaults. | EibScNorthboundClientTest (uses application-test.yml base-url override) | Pending |
| `AC-022-010` | `getTopologyVersion` uses a structured `NorthboundTopologyVersionViewDto`, not a plain `String`. | EibScNorthboundClient.java interface signature; EibScNorthboundClientTest verifying DTO deserialization | Pending |
| `AC-022-011` | Rooms and zones for `EffectiveHabitatView` are derived from the topology snapshot returned by `GET /sc/v1/habitats/{habitatId}/topology`. Separate `/rooms` and `/zones` upstream calls are not required. | EibEffectiveViewServiceTest: verify only one upstream topology call made for rooms/zones in habitat view | Pending |
| `AC-022-012` | `ScEnvelope<T>` preserves `status`, `payload`, `warnings` and `error`. | EibCanonicalEnvelopeMapperTest.preservesAllTwelveScCStatuses; EibScNorthboundClientTest | Pending |
| `AC-022-013` | `ScErrorDto` and `ScWarningDto` preserve `code`, `message` and `source`. | EibCanonicalEnvelopeMapperTest.preservesErrorSource; EibCanonicalEnvelopeMapperTest.preservesWarningSources | Pending |
| `AC-022-014` | `EffectiveHabitatView` is produced from SC-C topology snapshot without mutating SC-C. | EibEffectiveViewServiceTest: no POST/PUT/DELETE calls to SC-C in habitat view generation; read-only assertions | Pending |
| `AC-022-014a` | `EffectiveHabitatView` includes `sourceTopologyVersion` derived from the SC-C topology snapshot `topologyVersionValue`. It must not be null or empty when SC-C returns a valid topology snapshot. | EibEffectiveViewServiceTest.habitatViewSourceTopologyVersionEqualsSnapshotValue; EibApiControllerTest: assertThat(response.payload().sourceTopologyVersion()).isNotNull().isNotEmpty() | Pending |
| `AC-022-015` | `EffectiveRoomView` and `EffectiveZoneView` are implemented. | EibEffectiveViewServiceTest: rooms and zones non-null in returned EffectiveHabitatView; effective room/zone refs start with "eib.room." and "eib.zone." | Pending |
| `AC-022-016` | `EffectiveDeviceView` and `EffectiveEndpointView` use effective refs, not canonical IDs, in ordinary mode. | EibEffectiveViewMapperTest.ordinaryDeviceViewHasEffectiveRef; assertThat(view.effectiveDeviceRef()).startsWith("eib.device.") | Pending |
| `AC-022-017` | `EffectiveCapabilityAffordance` is implemented for capability exposure at effective-view level. | EibEffectiveViewMapperTest: device with capabilities → EffectiveDeviceView.capabilities non-empty | Pending |
| `AC-022-018` | `EffectiveDiagnosticsView` preserves canonical uncertainty product-safely. | EibEffectiveViewServiceTest: UNKNOWN_PENDING_NORMALIZATION from SC-C migrationReadiness → present in EffectiveDiagnosticsView.migrationReadiness | Pending |
| `AC-022-018a` | `EffectiveHabitatView.temporalActs` is populated from `listEffectiveTemporalActs` using the SC-C `GET /temporal-acts` route, with default mode `ACTIVE`. It must not remain silently empty when SC-C returns temporal acts. | EibEffectiveViewServiceTest.habitatViewTemporalActsNonEmptyWhenUpstreamReturnsActs: mock returns 2 acts → assertThat(view.temporalActs()).hasSize(2); EibApiControllerTest: effective-view response contains non-empty temporalActs | Pending |
| `AC-022-019` | EIB does not expose `providerDeviceId` or `providerEndpointId` in ordinary product views. | EibEffectiveViewMapperTest.ordinaryDeviceViewDoesNotContainProviderDeviceId: assertThat(json).doesNotContain(dto.providerDeviceId()); EibEffectiveViewMapperTest.ordinaryEndpointViewDoesNotContainProviderEndpointId | Pending |
| `AC-022-020` | Ordinary responses do not expose `canonicalDeviceId`, `canonicalEndpointId` or `canonicalTemporalActId`. | EibEffectiveViewMapperTest.ordinaryDeviceViewCanonicalDeviceIdIsNull; EibEffectiveViewMapperTest.ordinaryTemporalActViewCanonicalIdIsNull | Pending |
| `AC-022-021` | Diagnostic/admin responses expose canonical IDs only when diagnostic/admin context is explicitly authorized. | EibEffectiveViewMapperTest.diagnosticDeviceViewContainsCanonicalDeviceId; EibEffectiveViewMapperTest.diagnosticDeviceViewDoesNotContainProviderDeviceId | Pending |
| `AC-022-022` | All twelve SC-C statuses are preserved internally. | EibCanonicalEnvelopeMapperTest.preservesAllTwelveScCStatuses: iterates all 12 values; assertThat(mapper.extractScStatus(env)).isEqualTo(status) | Pending |
| `AC-022-023` | `UNSUPPORTED_PROFILE` is not treated as success. | EibCanonicalEnvelopeMapperTest.unsupportedProfileIsNotSuccess: assertThat(mapper.isSuccess(...)).isFalse() | Pending |
| `AC-022-024` | `DEFERRED_SC_B_REQUIRED` is not treated as success. | EibCanonicalEnvelopeMapperTest.deferredScBRequiredIsNotSuccess | Pending |
| `AC-022-025` | `UNKNOWN_PENDING_NORMALIZATION` remains visible and is not fabricated as known. | EibCanonicalEnvelopeMapperTest.unknownPendingNormalizationIsNotSuccess; EibEffectiveViewServiceTest: UNKNOWN_PENDING_NORMALIZATION visible in diagnostics | Pending |
| `AC-022-026` | `INTERNAL_ERROR` does not leak internal details to product clients. | EibCanonicalEnvelopeMapperTest.internalErrorIsNotSuccess; EibApiControllerTest: INTERNAL_ERROR from SC-C → product response does not expose internal stack trace | Pending |
| `AC-022-027` | `NOT_FOUND` may be translated product-safely, but canonical cause remains traceable internally. | EibCanonicalEnvelopeMapperTest: NOT_FOUND → CanonicalSubmissionTrace.scNorthboundStatus equals "NOT_FOUND" | Pending |
| `AC-022-028` | Effective refs are deterministic for the same habitat/type/canonical ID under the same secret. | EibEffectiveRefCodecTest.sameInputProducesSameRef | Pending |
| `AC-022-029` | Effective refs do not reveal canonical IDs. | EibEffectiveRefCodecTest.refDoesNotContainCanonicalId | Pending |
| `AC-022-030` | `eib.room`, `eib.zone`, `eib.device`, `eib.endpoint` and `eib.temporal` ref families are supported. | EibEffectiveRefCodecTest.prefixFamiliesAreCorrect: assertThat(ref).startsWith("eib.device.") for each family | Pending |
| `AC-022-031` | Temporal ref resolution by list+match is implemented. | EibEffectiveRefCodecTest.resolveTemporalRefFindsByListMatch; EibTemporalAdmissionServiceTest.cancelResolvesRefBeforeSubmitting | Pending |
| `AC-022-032` | The implementation report documents list+match as known first-slice behavior. | implementation-report.md §list+match section present and non-empty | Pending |
| `AC-022-033` | `listEffectiveTemporalActs` consumes SC-C temporal act list route and returns effective temporal views. | EibTemporalActProjectionServiceTest.listEffectiveTemporalActsReturnsEffectiveViews: mock returns 3 acts → 3 EffectiveTemporalActViews with effectiveTemporalActRef starting "eib.temporal." | Pending |
| `AC-022-034` | `getEffectiveTemporalAct` resolves effective temporal ref and returns the matching effective temporal view or product-safe not-found. | EibTemporalActProjectionServiceTest.getEffectiveTemporalActResolvesRef; EibTemporalActProjectionServiceTest.getEffectiveTemporalActReturnsNotFoundForUnknownRef | Pending |
| `AC-022-035` | `admitTemporalSignalRequest` submits to SC-C `POST /temporal-acts`. | EibTemporalAdmissionServiceTest.signalCreateSubmitsToScC (via mock client verify) | Pending |
| `AC-022-036` | If SC-C returns `ACCEPTED` with `NorthboundTemporalActView`, EIB generates `effectiveTemporalActRef` and sets `InteractionAdmissionDecision.effectiveRef`. | EibTemporalAdmissionServiceTest.signalCreateAcceptedSetsEffectiveRef: assertThat(decision.effectiveRef()).isNotNull().startsWith("eib.temporal.") | Pending |
| `AC-022-037` | `admitTemporalCancellation` resolves effective temporal ref and submits to SC-C cancel route. | EibTemporalAdmissionServiceTest.cancelResolvesRefAndSubmitsToScC | Pending |
| `AC-022-038` | EIB distinguishes `ADMITTED` from SC-C accepted/completed/provider-side effect. | EibTemporalAdmissionServiceTest.admittedDoesNotImplyProviderSideEffect: ACCEPTED from SC-C → ADMITTED in EIB, but no assertion about device state | Pending |
| `AC-022-039` | EIB does not implement device/endpoint action admission. | EibArchitectureTest.eibDoesNotIntroduceForbiddenTechnologies; no EibActionAdmissionService class exists | Pending |
| `AC-022-040` | EIB does not implement discovery admission. | No EibDiscoveryAdmissionService class exists; implementation-report negative scope | Pending |
| `AC-022-041` | Network connection failure maps to EIB upstream-unavailable status/error. | EibScNorthboundClientTest.connectionFailureMapsToUpstreamUnavailable | Pending |
| `AC-022-042` | Malformed upstream JSON maps to EIB upstream-unavailable status/error. | EibScNorthboundClientTest.malformedJsonMapsToUpstreamUnavailable | Pending |
| `AC-022-043` | HTTP 503 with valid SC-C `ScEnvelope` body and `DEFERRED_SC_B_REQUIRED` is treated as semantic SC-C response. | EibScNorthboundClientTest.http503WithValidEnvelopeMapsToSemanticScCResponse: assertThat(result.status()).isEqualTo("DEFERRED_SC_B_REQUIRED") | Pending |
| `AC-022-044` | HTTP 503 with missing/non-JSON body is treated as upstream transport failure. | EibScNorthboundClientTest.http503WithNoBodyMapsToUpstreamUnavailable | Pending |
| `AC-022-045` | EIB does not import SC-C repositories, persistence adapters, `JdbcTemplate`, `DataSource` or `Flyway`. | EibArchitectureTest.eibDoesNotImportScCCoreOrAdapterPackages (JdbcTemplate, javax.sql.DataSource, flywaydb in forbidden list) | Pending |
| `AC-022-046` | EIB does not introduce WebFlux, GraphQL, MCP, gRPC, ConnectRPC, NATS, JetStream, SSE or WebSocket. | EibArchitectureTest.eibDoesNotIntroduceForbiddenTechnologies | Pending |
| `AC-022-047` | EIB does not implement live updates. | EibArchitectureTest.eibDoesNotIntroduceForbiddenTechnologies (SseEmitter, Flux< in forbidden list) | Pending |
| `AC-022-048` | EIB does not implement Authority/Policy/Identity/Session systems of record. | No Authority/Policy/Identity/Session storage classes; implementation-report negative scope | Pending |
| `AC-022-049` | EIB does not implement persistent effective-ref registry. | No EibEffectiveRefRegistry or persistence bean; DEBT-EIB-011 recorded | Pending |
| `AC-022-050` | EIB does not implement durable audit/admission ledger. | No admission ledger storage class; DEBT-EIB-010 recorded | Pending |
| `AC-022-051` | Test evidence records EIB project test count, failures, errors and skipped tests. | implementation-report.md §test-summary with exact counts | Pending |
| `AC-022-052` | Architecture tests confirm non-co-location and absence of direct SC-C imports. | EibArchitectureTest: both test methods pass with empty violations list | Pending |
| `AC-022-053` | Implementation report records all retained debts. | implementation-report.md §retained-debts covering DEBT-HTTP-001-003,005-008 and DEBT-EIB-002-012 | Pending |
| `AC-022-054` | Implementation report records any deviation from preferred `eib/` placement; such deviation requires explicit governance patch before L4 closure. | implementation-report.md §placement-decision; if not top-level eib/, patch reference required | Pending |
| `AC-022-055` | Implementation report records whether `DEBT-HTTP-004` is closed for the EIB initial implementation slice or remains partially open. | implementation-report.md §debt-http-004-disposition: "closed for EIB initial implementation slice" if L4 passes | Pending |

---

## 3. Required test suite names

Minimum test classes and the ACs they primarily satisfy:

```text
EibArchitectureTest                    ← AC-022-003,004,005,039,040,045,046,047,052
EibEffectiveRefCodecTest               ← AC-022-028,029,030,031
EibCanonicalEnvelopeMapperTest         ← AC-022-012,013,022,023,024,025,026,027
EibScNorthboundClientTest              ← AC-022-006,007,008,009,010,041,042,043,044
EibEffectiveViewMapperTest             ← AC-022-016,017,019,020,021
EibEffectiveViewServiceTest            ← AC-022-011,014,014a,015,018,018a
EibTemporalActProjectionServiceTest    ← AC-022-033,034,031
EibTemporalAdmissionServiceTest        ← AC-022-035,036,037,038
EibApiControllerTest                   ← AC-022-014a,018a,019,020,021,038
```

The implementation report MUST map every AC to at least one concrete test class and
method name or explicit evidence statement.
