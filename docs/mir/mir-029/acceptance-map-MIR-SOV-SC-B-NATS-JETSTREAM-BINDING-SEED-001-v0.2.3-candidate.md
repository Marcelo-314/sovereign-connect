# Acceptance Map — MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001

```text
Document ID:  acceptance-map-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
Version:      v0.2.3-candidate
Status:       Execution acceptance map / candidate
Date:         2026-05-30
Target MIR:   MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v0.2.0-candidate
```

---

## 1. Acceptance summary

Baseline before implementation:

```text
sovereign-connect: 411 tests, 0 failures, 0 errors, 0 skipped
EIB reports:       56 tests, 0 failures, 0 errors, 0 skipped
```

Expected after MU-029:

```text
sovereign-connect: >=437 tests, 0 failures, 0 errors, 0 skipped
```

Acceptance target:

```text
Validated L4
```

---

## 2. Acceptance criteria mapping

| AC | Requirement | Evidence expected |
|---|---|---|
| AC-029-001 | `NatsScBusPort implements ScBusPort` from `bus.runtime.port` | Source + compile + architecture test |
| AC-029-002 | SC-C production code does not import `io.nats` or `bus.runtime.nats` | `ScBusNatsArchitectureTest` |
| AC-029-003 | `bus.runtime.serialization` does not import NATS/Testcontainers | existing serialization architecture test |
| AC-029-004 | NATS dependency confined to SC-B NATS surface and tests | architecture test / pom inspection |
| AC-029-005 | No Adapter Manifest runtime | source diff / report |
| AC-029-006 | No Capability Semantics runtime | source diff / report |
| AC-029-007 | No productive provider adapter | source diff / report |
| AC-029-008 | No productive fact-family runtime | source diff / report |
| AC-029-009 | pom intentionally includes `io.nats:jnats` | pom inspection / architecture test |
| AC-029-010 | Testcontainers NATS default or fallback justified | integration test / report |
| AC-029-011 | broker-blocking tests updated to NATS-only allowlist in same commit | git log / source diff |
| AC-029-012 | non-NATS brokers remain rejected | architecture test |
| AC-029-013 | `ScBusNatsArchitectureTest` or equivalent exists | test source |
| AC-029-014 | `bus.runtime.nats` does not import core/adapter/integration | architecture test |
| AC-029-015 | SC-C code does not import NATS | architecture test |
| AC-029-016 | event timer publish subject is `sc.v1.{habitatRoute}.event.timer.sc-c.timer-fired` | `NatsSubjectBuilderTest` |
| AC-029-017 | habitat/adapter/target routes use `ScSubjectIdTokenCodec.encodeScid1` | subject builder tests |
| AC-029-018 | command fixture subject uses explicit habitat/adapter/target route | `NatsSubjectBuilderTest` |
| AC-029-019 | response subject uses `ScCorrelationTokenCodec.encode` and grammar `sc.v1.{habitat}.response.{adapter}.{correlationHex}` | `NatsSubjectBuilderTest` |
| AC-029-020 | unsafe subject tokens rejected; wildcards rejected as tokens | `NatsSubjectBuilderTest` |
| AC-029-021 | subject builder does not parse route tokens into canonical identity | source / architecture test |
| AC-029-022 | `NatsScBusPort` serializes via `ScJsonWireCodec` overloads with payloadType/schemaVersion | source / integration tests |
| AC-029-023 | `WireEnvelopeValidator.validate` is called before publish | source / test if feasible |
| AC-029-024 | received bytes decode as SC-JSON-WIRE-v1 | NATS integration test |
| AC-029-025 | raw payload without envelope is never published | source / tests |
| AC-029-026 | payloadTypes are not Java class names | wire fixture tests / registry tests |
| AC-029-027 | RuntimeDispatchService dispatches timer-fired through NATS | `RuntimeDispatchToNatsEventIntegrationTest` |
| AC-029-028 | NATS subscriber receives event on expected subject | integration test |
| AC-029-029 | received event decodes to EVENT lane | integration test |
| AC-029-030 | DispatchState records technical dispatched state | runtime integration test |
| AC-029-031 | DispatchObservation persists technical delivery observation | runtime integration test |
| AC-029-032 | no semantic success inferred from publish/ack | source / tests / report |
| AC-029-033 | registry exposes `COMMAND_SCD_V1` | registry/wire test |
| AC-029-034 | `ScdCommand` production record exists under `bus.contract.scd` | source + wire payload test |
| AC-029-035 | optional `INVOKE_CAPABILITY` fixture is wire-only if included | test/report or N/A |
| AC-029-036 | registry exposes `RESPONSE_SCD_EXECUTION_RESULT_V1` | registry/wire test |
| AC-029-037 | `ScdExecutionResult` production record exists under `bus.contract.scd` | source + test |
| AC-029-038 | `ScdExecutionResult` not terminal semantic authority | report / source boundary |
| AC-029-039 | `SCB_*_V1` stream names/patterns defined | `NatsStreamConfigurationTest` |
| AC-029-040 | if live JetStream is claimed, stream config is applied against a real NATS server with JetStream enabled | JetStream integration test OR explicit split report |
| AC-029-041 | JetStream does not replace `DispatchStateWritePort` | source / architecture / report |
| AC-029-042 | JetStream does not replace `DispatchObservationPort` | source / architecture / report |
| AC-029-043 | no silent JetStream omission | implementation report |
| AC-029-044 | lifecycle subject mapping fixtures included if mapper exists | mapper test or N/A |
| AC-029-045 | lifecycle tests do not implement admission/ACTIVE authority | mapper tests/source or N/A |
| AC-029-046 | route-assignment push subject construction tested if mapper exists | mapper test or N/A |
| AC-029-047 | report records branch/commits/changed files | implementation report |
| AC-029-048 | report records final tests and NATS fixture strategy | implementation report |
| AC-029-049 | report records JetStream disposition | implementation report |
| AC-029-050 | report confirms no SC-C direct NATS publishing | implementation report + architecture test |
| AC-029-051 | report confirms retained downstream debts | implementation report |
| AC-029-052 | acceptance map links all ACs | this map + post-implementation update |

---

## 3. Stop condition mapping

If a stop condition fires, the implementation report must record:

```text
stop id
symptom
changed files before stop
whether rollback is required
recommended next artifact or split
```

Most likely split condition:

```text
STOP-9 — live JetStream cannot be made stable.
Recommended split: MU-029 NATS Core live binding seed, MU-029B JetStream hardening seed.
```


---

## v0.2.2 package precision checks

```text
AM-PREC-001 — Codex prompt documents the verified existing DispatchOutcome.Dispatched
               record shape instead of relying on an unreferenced constructor.

AM-PREC-002 — RuntimeDispatchToNatsEventIntegrationTest must use a DataSource/Flyway
               pattern that creates SC-B dispatch tables V100/V101 before constructing
               JdbcDispatchStateRepository / JdbcDispatchObservationRepository.
```
