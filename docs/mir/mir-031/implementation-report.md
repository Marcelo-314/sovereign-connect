# Implementation report - MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001

```text
Document ID:  implementation-report-MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001
Version:      v0.2.1-candidate
Status:       Implemented
Date:         2026-06-01
```

## Branch / commits

```text
Branch: feat/sc-b-mir-031-nats-jetstream-debt-closure
Commits:
  - HEAD feat(sc-b): close nats jetstream debt
```

## Final test evidence

```text
Baseline:
  mvn -q test
  sovereign-connect: 469 tests, 0 failures, 0 errors, 0 skipped

Targeted:
  mvn -q test -Dtest="NatsJetStreamStreamApplicatorTest,NatsScBusPortDrainTest,NatsScBusPortReconnectTest,ScBusNatsArchitectureTest"
  Result: pass

Final:
  mvn -q test
  sovereign-connect: 481 tests, 0 failures, 0 errors, 0 skipped

EIB: not applicable in this repository
```

## Debt closure status

```text
DEBT-B-NATS-004 - CLOSED
Evidence:
  - ConnectionListener wired: yes, NatsScBusPort registers a listener on the injected Connection
  - forceReconnect produced RECONNECTED/RESUBSCRIBED: yes, covered by NatsScBusPortReconnectTest
  - timer-fired handler received event before and after reconnect: yes
  - durable lifecycle consumer replay after reconnect: yes

DEBT-B-NATS-005 - CLOSED
Evidence:
  - AutoCloseable implemented: yes
  - connection.drain(Duration) used: yes
  - Dispatcher.drain(Duration) not used: yes
  - dispatcher cleanup API: connection.closeDispatcher(dispatcher)
  - publish/register after close deterministic: yes
  - no flush-after-drain behavior introduced: yes

DEBT-B-NATS-009 - CLOSED
Evidence:
  - StorageType.File vs Memory conflict fixture used: yes
  - subject pattern change not used as conflict source: yes
  - unexpected JetStreamApiException no longer enters blind update path: yes
  - existing-stream handling is limited to NATS API error 10058 before update
```

## Scope exclusions confirmed

```text
Adapter Manifest runtime introduced: no
Manifest-over-NATS transport introduced: no
Route assignment runtime introduced: no
registerCommandHandler real subscription introduced: no
registerResponseHandler real subscription introduced: no
SC-C imports io.nats.* or bus.runtime.nats.*: no
DispatchStateWritePort / DispatchObservationPort bypassed: no
Provider execution runtime introduced: no
SC-C NATS publishing introduced: no
Flyway migrations introduced: no
```

## Notes / deviations

```text
NATS server version observed in tests: 2.14.1

Reconnect/resubscribe ownership:
  NatsScBusPort owns listener registration against the injected jnats Connection.
  Tests create a reconnect-aware Connection with maxReconnects(-1), force a local server reconnect,
  and verify listener counters plus event delivery across reconnect.

Drain ownership:
  NatsScBusPort.close() is idempotent, closes tracked dispatchers via connection.closeDispatcher(...),
  then drains the injected Connection with connection.drain(Duration.ofSeconds(10)).get().

JetStream conflict behavior:
  The applicator no longer treats all JetStreamApiException cases as "already exists".
  Only API error 10058 enters the existing-stream update path. Incompatible storage type causes
  IllegalStateException and prevents a blind update attempt.

One targeted run initially hit a local NatsLocalServer startup connection error in a single drain test.
The same test passed when rerun individually, and the full targeted command passed afterwards.
```

## Patch: PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001

```text
Status: Implemented
Commit: refactor(sc-b): centralize nats connection creation

Files added:
  - src/main/java/com/sovereign/connect/bus/runtime/nats/NatsConnectionFactory.java
  - src/test/java/com/sovereign/connect/bus/runtime/nats/NatsConnectionFactoryTest.java

Files modified:
  - src/test/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPortReconnectTest.java
  - src/test/java/com/sovereign/connect/bus/ScBusNatsArchitectureTest.java

Listener ownership:
  NatsScBusPort listener ownership remains internal.
  NatsConnectionFactory centralizes Options construction and Nats.connect(...) creation.
  The factory also supports optional ConnectionListener wiring at connection creation time.

Scope confirmation:
  Adapter Manifest runtime introduced: no
  Manifest-over-NATS transport introduced: no
  Route assignment runtime introduced: no
  Command/response handler runtime introduced: no
  Provider execution introduced: no
  Command admission runtime introduced: no
  SC-C direct NATS import introduced: no
  Flyway migration introduced: no

Verification:
  Targeted:
    mvn -q test -Dtest="NatsConnectionFactoryTest,NatsScBusPortReconnectTest,ScBusNatsArchitectureTest,NatsScBusPortDrainTest,NatsJetStreamStreamApplicatorTest"
    Result: pass

  Full:
    mvn -q test
    sovereign-connect: 484 tests, 0 failures, 0 errors, 0 skipped
    EIB: not applicable in this repository

Acceptance:
  AC-PATCH-001: PASS
  AC-PATCH-002: PASS
  AC-PATCH-003: PASS
  AC-PATCH-004: PASS
  AC-PATCH-005: PASS
  AC-PATCH-006: PASS
  AC-PATCH-007: PASS
  AC-PATCH-008: PASS
  AC-PATCH-009: PASS
  AC-PATCH-010: PASS

Notes:
  The first full mvn -q test run exposed a test-source architecture violation because the new
  assertion contained the forbidden adapter import string literally. The assertion was changed
  to match the existing concatenated-string pattern, and the subsequent full suite passed.
```
