# Implementation Report Template — MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001

```text
Document ID:  implementation-report-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
Version:      v0.2.3-template
Status:       Template
Date:         2026-05-30
Target MIR:   MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v0.2.0-candidate
```

---

## 1. Execution summary

```text
Branch:
Base branch / base commit:
Primary implementation commit:
Documentation/evidence commit:
Final commit:
Execution date:
Executor:
```

Verdict:

```text
[ ] Validated L4
[ ] Partially validated
[ ] Stopped by hard stop
[ ] Failed
```

---

## 2. Scope confirmation

Implemented:

```text
[ ] io.nats:jnats dependency
[ ] Testcontainers NATS or justified fallback
[ ] NatsScBusPort implements ScBusPort
[ ] NatsSubjectBuilder
[ ] NatsStreamConfiguration / JetStream disposition
[ ] NatsLifecycleChannelMapper if included
[ ] ScdCommand production wire reference record
[ ] ScdExecutionResult production wire reference record
[ ] COMMAND_SCD_V1 / RESPONSE_SCD_EXECUTION_RESULT_V1 registry constants
[ ] RuntimeDispatchService -> NATS EVENT / TIMER_FIRED_SIGNAL E2E
[ ] COMMAND wire fixture
[ ] RESPONSE wire fixture
[ ] architecture test allowlist update
```

Explicitly not implemented:

```text
[ ] productive adapter runtime
[ ] lifecycle admission runtime
[ ] Adapter Manifest runtime
[ ] Capability Semantics runtime
[ ] provider execution
[ ] productive fact-family runtime
[ ] semantic retry
[ ] terminal semantic success ownership
[ ] SC-C direct NATS publishing
```

---

## 3. Changed files

```text
Production files:
-

Test files:
-

Docs/evidence files:
-

Build files:
-
```

---

## 4. Dependency and fixture strategy

NATS dependency:

```text
jnats version:
```

Test fixture:

```text
[ ] Testcontainers NATS
[ ] local nats-server fallback
```

If local fallback:

```text
Justification:
How NATS server is started:
How tests avoid environment coupling:
```

---

## 5. JetStream disposition

Select one:

```text
[ ] Live JetStream seed implemented and verified against real NATS server with --js.
[ ] Config-only stream names/patterns implemented; live JetStream split recommended.
[ ] JetStream stopped by hard stop; split required.
```

Evidence:

```text
Test class:
Assertions:
Stream names:
Subject patterns:
Real server with JetStream enabled: yes/no
How stream existence/ack was verified:
```

If split recommended:

```text
Recommended artifact:
Reason:
```

---

## 6. Test evidence

Targeted tests:

```text
mvn -q compile
Result:

mvn -q test -Dtest="NatsSubjectBuilderTest,ScdCommandWirePayloadTest,ScdExecutionResultWirePayloadTest,ScBusNatsArchitectureTest"
Result:

mvn -q test -Dtest="NatsScBusPortEventIntegrationTest,RuntimeDispatchToNatsEventIntegrationTest,NatsScBusPortCommandWireFixtureTest,NatsScBusPortResponseWireFixtureTest,NatsStreamConfigurationTest"
Result:
```

Full regression:

```text
mvn -q test
Tests run:
Failures:
Errors:
Skipped:
```

EIB reports, if applicable:

```text
Tests run:
Failures:
Errors:
Skipped:
```

---

## 7. Acceptance criteria disposition

```text
AC-029-001: PASS/FAIL — evidence:
AC-029-002: PASS/FAIL — evidence:
AC-029-003: PASS/FAIL — evidence:
AC-029-004: PASS/FAIL — evidence:
AC-029-005: PASS/FAIL — evidence:
AC-029-006: PASS/FAIL — evidence:
AC-029-007: PASS/FAIL — evidence:
AC-029-008: PASS/FAIL — evidence:
AC-029-009: PASS/FAIL — evidence:
AC-029-010: PASS/FAIL — evidence:
AC-029-011: PASS/FAIL — evidence:
AC-029-012: PASS/FAIL — evidence:
AC-029-013: PASS/FAIL — evidence:
AC-029-014: PASS/FAIL — evidence:
AC-029-015: PASS/FAIL — evidence:
AC-029-016: PASS/FAIL — evidence:
AC-029-017: PASS/FAIL — evidence:
AC-029-018: PASS/FAIL — evidence:
AC-029-019: PASS/FAIL — evidence:
AC-029-020: PASS/FAIL — evidence:
AC-029-021: PASS/FAIL — evidence:
AC-029-022: PASS/FAIL — evidence:
AC-029-023: PASS/FAIL — evidence:
AC-029-024: PASS/FAIL — evidence:
AC-029-025: PASS/FAIL — evidence:
AC-029-026: PASS/FAIL — evidence:
AC-029-027: PASS/FAIL — evidence:
AC-029-028: PASS/FAIL — evidence:
AC-029-029: PASS/FAIL — evidence:
AC-029-030: PASS/FAIL — evidence:
AC-029-031: PASS/FAIL — evidence:
AC-029-032: PASS/FAIL — evidence:
AC-029-033: PASS/FAIL — evidence:
AC-029-034: PASS/FAIL — evidence:
AC-029-035: PASS/FAIL/N/A — evidence:
AC-029-036: PASS/FAIL — evidence:
AC-029-037: PASS/FAIL — evidence:
AC-029-038: PASS/FAIL — evidence:
AC-029-039: PASS/FAIL — evidence:
AC-029-040: PASS/FAIL/N/A — evidence:
AC-029-041: PASS/FAIL — evidence:
AC-029-042: PASS/FAIL — evidence:
AC-029-043: PASS/FAIL — evidence:
AC-029-044: PASS/FAIL/N/A — evidence:
AC-029-045: PASS/FAIL/N/A — evidence:
AC-029-046: PASS/FAIL/N/A — evidence:
AC-029-047: PASS/FAIL — evidence:
AC-029-048: PASS/FAIL — evidence:
AC-029-049: PASS/FAIL — evidence:
AC-029-050: PASS/FAIL — evidence:
AC-029-051: PASS/FAIL — evidence:
AC-029-052: PASS/FAIL — evidence:
```

---

## 8. Boundary confirmations

```text
[ ] SC-C does not publish directly to NATS.
[ ] NATS/JetsStream does not replace SC-C semantic authority.
[ ] NATS/JetsStream does not replace DispatchStateWritePort.
[ ] NATS/JetsStream does not replace DispatchObservationPort.
[ ] Adapter runtime is not implemented.
[ ] Lifecycle admission / ACTIVE authority is not implemented.
[ ] Manifest runtime is not implemented.
[ ] Capability runtime is not implemented.
[ ] Command runtime E2E is deferred until route assignment/manifest/lifecycle substrate exists.
```

---

## 9. Retained debts

```text
DEBT-B-NATS-H-001 — JetStream hardening if live JetStream is split.
DEBT-SCD-MANIFEST-001 — Adapter Manifest runtime absent.
DEBT-SCD-CAPABILITY-001 — Capability Semantics runtime absent.
DEBT-SCD-FACTS-001 — SC-D fact family payloads absent.
DEBT-SCD-LIFECYCLE-RUNTIME-001 — lifecycle admission runtime absent.
DEBT-SCD-COMMAND-E2E-001 — productive command E2E to adapter absent.
```


---

## v0.2.2 precision evidence

Record the following:

```text
- DispatchOutcome.Dispatched constructor used by NatsScBusPort, with file path.
- DataSource pattern used by RuntimeDispatchToNatsEventIntegrationTest.
- Confirmation that Flyway V100/V101 migrations ran before SC-B dispatch state / observation repositories were used.
```
