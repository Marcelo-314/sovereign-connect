# MU-023 Implementation Report - EIB Hardening

```text
MIR:       MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
MU:        MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
Slot:      MU-023
Status:    Implemented / pending review
Branch:    feat/sc-eib-mir-023-effective-interaction-boundary-hardening
Base HEAD: dab740044cf1a3900cd425e4ef0b745fceaf2bf1
```

## 1. Summary

MU-023 hardens the EIB boundary without modifying SC-C production or test code.

Implemented:

```text
ACTION-EIB-H-001  RestClient.Builder now applies connect/read timeout factory from sc.eib.northbound.timeout-ms.
ACTION-EIB-H-002  Positive route regression now covers device health, endpoint health, diagnostics and getTemporalAct.
ACTION-EIB-H-003  SC-C CANCELLED maps to EIB COMPLETED.
ACTION-EIB-H-004  Admission HTTP/EibResponse mapping is centralized in EibHttpResponseMapper.
ACTION-EIB-H-005  Cancellation checks listTemporalActs envelope success before effective-ref resolution.
ACTION-EIB-H-006  Diagnostic/admin truth-table coverage is complete.
```

Boundary preserved:

```text
EIB remains under eib/.
No SC-C production code changed.
No SC-C test code changed.
No com.sovereign.connect imports in eib/src/main/java.
RestClient.Builder bean pattern preserved.
```

## 2. Changed files

Production:

```text
eib/src/main/java/com/sovereign/eib/config/EibConfiguration.java
eib/src/main/java/com/sovereign/eib/api/EibHttpResponseMapper.java
eib/src/main/java/com/sovereign/eib/api/EibTemporalActController.java
eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java
```

Tests:

```text
eib/src/test/java/com/sovereign/eib/EibScNorthboundClientTest.java
eib/src/test/java/com/sovereign/eib/EibScNorthboundClientTimeoutTest.java
eib/src/test/java/com/sovereign/eib/EibAdmissionResponseMapperTest.java
eib/src/test/java/com/sovereign/eib/EibTemporalAdmissionServiceHardeningTest.java
eib/src/test/java/com/sovereign/eib/EibEffectiveViewMapperTest.java
eib/src/test/java/com/sovereign/eib/EibApiControllerTest.java
```

Documentation:

```text
docs/mir/mir-023/implementation-report.md
```

## 3. Tests

```text
EIB compile:
  command:  cd eib && mvn compile -q
  result:   PASS

Forbidden import scan:
  command:  cd eib && rg "com\.sovereign\.connect" src/main/java
  result:   no matches

Timeout + mapper:
  command:  cd eib && mvn test "-Dtest=EibScNorthboundClientTimeoutTest,EibAdmissionResponseMapperTest" -q
  result:   PASS

Hardening focused tests:
  command:  cd eib && mvn test "-Dtest=EibTemporalAdmissionServiceHardeningTest,EibEffectiveViewMapperTest,EibApiControllerTest" -q
  result:   PASS

EIB full suite:
  command:  cd eib && mvn test -q
  tests:    56
  failures: 0
  errors:   0
  skipped:  0

SC-C baseline:
  command:  mvn test -q
  tests:    240
  failures: 0
  errors:   0
  skipped:  0
```

Suite evidence:

```text
EibScNorthboundClientTimeoutTest: 2 tests
EibScNorthboundClientTest: 15 tests
EibAdmissionResponseMapperTest: 11 tests
EibTemporalAdmissionServiceHardeningTest: 3 tests
EibEffectiveViewMapperTest: 4 tests
EibApiControllerTest: 5 tests
```

## 4. Acceptance Criteria Result

```text
AC-023-001 through AC-023-053: PASS for implemented hardening scope.
```

Evidence highlights:

```text
AC-023-005/006/007: EibConfiguration.eibRestClientBuilder applies SimpleClientHttpRequestFactory to the builder.
AC-023-008: EibScNorthboundClientTimeoutTest validates timeout normalization.
AC-023-008a: EibApiControllerTest validates API-level UPSTREAM_UNAVAILABLE / HTTP 503.
AC-023-011 through AC-023-019: EibAdmissionResponseMapperTest covers admission vocabulary.
AC-023-020/021/022: CANCELLED maps to COMPLETED and cancel API returns HTTP 200 / COMPLETED.
AC-023-023 through AC-023-027: EibTemporalAdmissionServiceHardeningTest covers list non-success propagation.
AC-023-028 through AC-023-034: EibEffectiveViewMapperTest covers diagnostic/admin gating.
AC-023-035 through AC-023-046: EibScNorthboundClientTest and architecture checks cover route regression.
AC-023-047 through AC-023-052: this report records test counts, baseline, debt and commit status.
```

## 5. Debt Disposition

```text
DEBT-EIB-014 - Timeout configured but not enforced.
  status: CLOSED
  evidence: timeout factory applied; EibScNorthboundClientTimeoutTest passed.

DEBT-EIB-015 - Cancellation effective-ref resolution may collapse non-success list envelopes.
  status: CLOSED
  evidence: isSuccess guard added before resolveTemporalRef; EibTemporalAdmissionServiceHardeningTest passed.
```

Retained debts:

```text
DEBT-EIB-003  Authority/Policy/Identity/Session real integration absent.
              Classification: requires-resolution-before-SApp-Surface.

DEBT-EIB-004  Device/endpoint action admission remains SC-B-gated.
              Classification: requires-resolution-before-EIB-action-admission.

DEBT-EIB-005  Discovery admission remains SC-B-gated.
              Classification: requires-resolution-before-EIB-action-admission.

DEBT-EIB-006  Live updates deferred.
              Classification: requires-resolution-before-View-Composer.

DEBT-EIB-010  EIB conformance/TCK absent.
              Classification: requires-resolution-before-industrial.

DEBT-EIB-011  Durable audit/admission ledger absent.
              Classification: requires-resolution-before-industrial.

DEBT-EIB-012  Persistent effective-ref registry absent.
              Classification: accepted-for-now.

DEBT-EIB-013  Auth/authz stubbed/config-gated.
              Classification: requires-resolution-before-SApp-Surface.
```

## 6. Commits

```text
Implementation commit: not created in this execution turn
Evidence/report commit: not created in this execution turn
```

## 7. Notes

```text
No SC-C source files were modified.
No new test dependency was added.
MockRestServiceServer remains the route regression mechanism.
Timeout tests use 192.0.2.1 TEST-NET with 1ms timeout as required by the execution package.
```
