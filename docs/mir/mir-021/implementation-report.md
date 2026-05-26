# Implementation Report - MU-021 Northbound HTTP/SSE Binding Seed

```text
Document ID:  IMPLEMENTATION-REPORT-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001
Version:      v1.0.0
Status:       Completed
MU:           MU-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001
Slot:         MU-021
```

## 1. Execution Metadata

```text
Branch:                         feat/sc-c-mir-021-northbound-http-sse-binding
Implementation code commit:     59828db
Implementation evidence commit: pending
Date:                           2026-05-26
Executor:                       Codex
```

## 2. Scope Summary

```text
HTTP/OpenAPI binding seed over ScCoreNorthboundFacade: completed.
SSE-P0 / deferred: preserved.
No EIB, View Composer, Projection, SC-B runtime, SC-D adapters, gRPC, MCP, GraphQL, WebSocket: confirmed.
```

## 3. Dependency Changes

```text
spring-boot-starter-web: added
springdoc-openapi-starter-webmvc-api version: 2.5.0
Anything else: none
SpringDoc version deviation from CSA recommendation: none
```

## 4. Main Source File List

Added:

```text
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTopologyHttpController.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTemporalHttpController.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpResponseMapper.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpConfiguration.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpProperties.java
src/main/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundCancelTemporalActHttpBody.java
```

Modified:

```text
pom.xml
src/main/resources/application.yml
src/main/java/com/sovereign/connect/config/TemporalEngineConfiguration.java
```

```text
HTTP enabled property enforced by @ConditionalOnProperty on controllers: yes
Static seed route prefix: /sc/v1
Configured base-path property: /sc/v1
Exposure profile: local-trusted
```

## 5. Test File List

Added:

```text
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTopologyHttpControllerTest.java
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundTemporalHttpControllerTest.java
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpSerializationTest.java
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpArchitectureTest.java
src/test/java/com/sovereign/connect/adapter/northbound/http/ScNorthboundHttpSpringContextTest.java
```

Modified:

```text
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeNegativeBoundaryTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeSpringContextTest.java
```

Unchanged by this MU:

```text
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeArchitectureTest.java
src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeBehavioralTest.java
```

## 6. ObjectMapper Decision

```text
@Primary applied: yes
WRITE_DATES_AS_TIMESTAMPS disabled: yes
findAndRegisterModules preserved: yes
Deviation from MIR AC-021-025: no
```

## 7. Route Coverage

```text
Profile A routes implemented: 14/14
Profile B routes implemented: 7/7
Safe route deferrals: none
```

Explicit confirmations:

```text
getDeviceRuntimeState route functional OK/NOT_FOUND: yes
getEndpointRuntimeState route 501 for UNSUPPORTED_PROFILE: yes
getRecoveryStatus route 501 for UNSUPPORTED_PROFILE: yes
getNorthboundDiagnostics route: yes
listTemporalActs uses mode/maxResults: yes
cancelTemporalAct path/body conflict avoided: yes
create temporal act route: signal-only seed; future ActionTemporalAct route/discriminator remains deferred
```

## 8. SSE Disposition

```text
SSE-P0 / deferred confirmed: yes
No ScNorthboundSseController: yes
No SseEmitter: yes
No WebFlux / Flux / Mono: yes
```

## 9. Validation

Command:

```bash
mvn test
```

Final result:

```text
Tests run: 240
Failures: 0
Errors:   0
Skipped:  0
Build:    SUCCESS
```

Relevant new suites:

```text
ScNorthboundHttpArchitectureTest:       5 run, 0 failures, 0 errors
ScNorthboundHttpSerializationTest:      9 run, 0 failures, 0 errors
ScNorthboundHttpSpringContextTest:      5 run, 0 failures, 0 errors
ScNorthboundTemporalHttpControllerTest: 10 run, 0 failures, 0 errors
ScNorthboundTopologyHttpControllerTest: 15 run, 0 failures, 0 errors
```

Relevant retained suites:

```text
NorthboundFacadeArchitectureTest:       4 run, 0 failures, 0 errors
NorthboundFacadeBehavioralTest:         18 run, 0 failures, 0 errors
NorthboundFacadeNegativeBoundaryTest:   2 run, 0 failures, 0 errors
NorthboundFacadeSpringContextTest:      4 run, 0 failures, 0 errors
```

## 10. Acceptance Criteria Status

```text
AC-021-001: PASS
AC-021-002: PASS
AC-021-003: PASS
AC-021-004: PASS
AC-021-005: PASS
AC-021-006: PASS
AC-021-007: PASS
AC-021-008: PASS
AC-021-009: PASS
AC-021-010: PASS
AC-021-011: PASS
AC-021-012: PASS
AC-021-013: PASS
AC-021-014: PASS
AC-021-015: PASS
AC-021-016: PASS
AC-021-017: PASS
AC-021-018: PASS
AC-021-019: PASS
AC-021-020: PASS
AC-021-021: PASS
AC-021-022: PASS
AC-021-023: PASS
AC-021-024: PASS
AC-021-025: PASS
AC-021-026: PASS
AC-021-027: PASS
AC-021-028: PASS
AC-021-029: PASS
AC-021-030: PASS
AC-021-031: PASS
AC-021-032: PASS
AC-021-033: PASS
AC-021-034: PASS
AC-021-035: PASS
AC-021-036: PASS
AC-021-037: PASS
AC-021-038: PASS
AC-021-039: PASS
AC-021-040: PASS
AC-021-041: PASS
AC-021-042: PASS
AC-021-043: PASS
AC-021-044: PASS
AC-021-045: PASS
```

## 11. Retained Debts

```text
DEBT-HTTP-001 - SSE remains SSE-P0 / deferred: retained
DEBT-HTTP-002 - No authentication/authorization layer in HTTP seed; local-trusted only: retained
DEBT-HTTP-003 - Swagger UI/static OpenAPI artifact deferred: retained
DEBT-HTTP-004 - EIB runtime consumption still requires EIB-side contract/SDD: retained
DEBT-HTTP-005 - gRPC/ConnectRPC binding remains downstream: retained
DEBT-HTTP-006 - MCP adapter remains separate AI/tool/admin exposure track: retained
DEBT-HTTP-007 - ACTIVE mode maxResults behavior remains inherited from facade: retained
DEBT-HTTP-008 - HTTP binding TCK/conformance harness absent: retained
```

Unexpected retained debt:

```text
- None
```

## 12. Deviations

```text
Decision deviated from:
  The literal Spring context stale-test replacement in context.md asserted every @Controller bean package must start with com.sovereign.connect.adapter.northbound.http.

Reason:
  Adding spring-boot-starter-web and SpringDoc introduces framework controller beans such as BasicErrorController and OpenApiWebMvcResource. The safety property needed by MU-021 is that Sovereign Connect-owned controller beans live only under adapter.northbound.http.

Risk:
  Low. Framework-provided controllers are expected infrastructure for WebMVC/OpenAPI and are not SC-C business bypasses.

Tests proving safety:
  NorthboundFacadeSpringContextTest.httpControllerBeansAreOnlyInHttpAdapterPackage
  ScNorthboundHttpSpringContextTest.httpControllerBeansAreOnlyInHttpAdapterPackage
  ScNorthboundHttpArchitectureTest.webImportsForbiddenOutsideHttpAdapterPackage
  mvn test: 240 run, 0 failures, 0 errors

Whether governance patch is required:
  No MIR/CSA semantic deviation. The implementation preserves the boundary intent by filtering to com.sovereign.connect-owned controllers.
```

```text
Decision deviated from:
  Controller paste-ready snippets omitted explicit @PathVariable/@RequestParam names.

Reason:
  The project compiler configuration does not enable -parameters, so Spring MVC cannot infer method parameter names through reflection.

Risk:
  Low. Explicit names are more stable and preserve the same route semantics.

Tests proving safety:
  ScNorthboundTopologyHttpControllerTest
  ScNorthboundTemporalHttpControllerTest
  mvn test: 240 run, 0 failures, 0 errors

Whether governance patch is required:
  No. This is an adapter binding implementation detail.
```

## 13. Final Implementation Dictum

```text
MU-SOV-SC-C-NORTHBOUND-HTTP-SSE-BINDING-SEED-001
Status recommendation: Validated L4
Evidence path: docs/mir/mir-021/implementation-report.md
Validation summary: mvn test passed with 240 tests, 0 failures, 0 errors, 0 skipped.
```
