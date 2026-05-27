# Acceptance Map — MU-021 Northbound HTTP/SSE Binding Seed

```text
Document ID:  ACCEPTANCE-MAP-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001
Version:      v0.1.0
Status:       Execution package acceptance map
MU:           MU-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001
Slot:         MU-021
```

This map binds MIR ACs to implementation evidence.

| AC | Required evidence | Expected tests / inspection |
|---|---|---|
| AC-021-001 | `spring-boot-starter-web` added and context starts | `ScNorthboundHttpSpringContextTest.contextLoadsWithHttpControllers` |
| AC-021-002 | WebFlux absent | `ScNorthboundHttpArchitectureTest.forbiddenTechnologiesAbsentFromAllMainSource`; pom inspection |
| AC-021-003 | SpringDoc WebMVC API added; `/v3/api-docs` non-empty | `ScNorthboundHttpSpringContextTest.openApiEndpointResolvesToNonEmptyDocument` |
| AC-021-004 | HTTP adapter package exists outside `core.northbound` | file list; architecture test |
| AC-021-005 | Topology controller delegates only to facade | `ScNorthboundHttpArchitectureTest.httpAdapterDelegatesOnlyToFacade`; controller test mocks |
| AC-021-006 | Temporal controller delegates only to facade | `ScNorthboundHttpArchitectureTest.httpAdapterDelegatesOnlyToFacade`; controller test mocks |
| AC-021-007 | No forbidden direct injections | `ScNorthboundHttpArchitectureTest.httpAdapterDoesNotImportRepositoriesOrDomainServices` |
| AC-021-008 | `ScNorthboundResponse<T>` body preserved | controller tests + serialization tests |
| AC-021-009 | Status mapping complete | `ScNorthboundHttpResponseMapper` unit/controller coverage |
| AC-021-010 | `UNSUPPORTED_PROFILE -> 501` visible in body | endpoint runtime-state route test; recovery route test |
| AC-021-011 | `DEFERRED_SC_B_REQUIRED -> 503` visible in body | mapper test or representative controller test |
| AC-021-012 | `VALIDATION_ERROR -> 422` exercised | `POST /temporal-acts` validation test |
| AC-021-013 | `UNKNOWN_PENDING_NORMALIZATION` visible in body | diagnostics serialization/controller test |
| AC-021-014 | `getDeviceRuntimeState` functional route | device runtime-state test OK or NOT_FOUND |
| AC-021-015 | `getEndpointRuntimeState` 501 when unsupported | endpoint runtime-state test |
| AC-021-016 | `getRecoveryStatus` 501 when unsupported | recovery route test |
| AC-021-017 | Diagnostics route 200 + canonical body | diagnostics controller test |
| AC-021-018 | `POST /temporal-acts` binds existing request | temporal controller test verifies facade argument |
| AC-021-019 | Cancel body excludes `temporalActId`; path is authoritative | temporal controller test verifies constructed request |
| AC-021-020 | Temporal list uses `mode` and `&` | temporal controller test with `mode=ACTIVE&maxResults=50` |
| AC-021-021 | Unknown mode returns 400 without facade call | temporal controller test verifies no interaction |
| AC-021-022 | Supported modes match facade enum | tests for ACTIVE plus inspection for enum usage; optional terminal/misfired tests |
| AC-021-023 | Future ActionTemporalAct decision deferred | implementation report |
| AC-021-024 | `findAndRegisterModules()` preserved | serialization test / source inspection |
| AC-021-025 | `@Primary` applied or deviation governance recorded | source inspection; implementation report if deviation |
| AC-021-026 | timestamps disabled or ISO proof | serialization tests |
| AC-021-027 | Instant serializes ISO-8601 | serialization test |
| AC-021-028 | `dueAt` deserializes ISO-8601 | serialization test |
| AC-021-029 | error/warning `source` serializes | serialization test |
| AC-021-030 | properties class + application defaults; `enabled` guarded by `@ConditionalOnProperty`; static `/sc/v1` prefix recorded | source inspection; context test |
| AC-021-031 | `core.northbound` HTTP-free | existing `NorthboundFacadeArchitectureTest`; new architecture test |
| AC-021-032 | Spring Web only under HTTP adapter | `webImportsForbiddenOutsideHttpAdapterPackage` |
| AC-021-033 | forbidden tech absent | `forbiddenTechnologiesAbsentFromAllMainSource` |
| AC-021-034 | adapter avoids repositories/services/ports | `httpAdapterDoesNotImportRepositoriesOrDomainServices` |
| AC-021-035 | no EIB/Projection/Auth/Policy/etc. | `httpAdapterDoesNotLeakEibProjectionOrAuthorityConcepts` |
| AC-021-036 | SSE/WebFlux absent | architecture test + file list |
| AC-021-037 | facade behavioral tests still pass | full `mvn test` |
| AC-021-038 | core architecture test unchanged/pass | full `mvn test`; implementation report notes unchanged or justified diff |
| AC-021-039 | stale global no-exposure test replaced | modified test file; full `mvn test` |
| AC-021-040 | stale no-controller assertion replaced | modified test file; full `mvn test` |
| AC-021-041 | report records dependency/file/test/results | implementation report |
| AC-021-042 | retained debts recorded | implementation report |
| AC-021-043 | SpringDoc version deviation recorded if any | implementation report |
| AC-021-044 | SSE-P0 recorded | implementation report |
| AC-021-045 | EIB unimplemented; HTTP only exposes Northbound | implementation report |

## Required validation command

```bash
mvn test
```

Final test count must be recorded exactly in implementation report.
