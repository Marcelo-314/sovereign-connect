# Acceptance map — MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001

```text
Document ID:  acceptance-map-MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001
Version:      v0.2.1-candidate
Status:       Candidate
```

| AC / Debt | Evidence required | Tests / files |
|---|---|---|
| DEBT-B-NATS-009 closed | `ensureStream` classifies non-10058 errors and detects StorageType.File vs Memory conflict | `NatsJetStreamStreamApplicatorTest.incompatibleStorageTypeConflictThrowsIllegalStateException` |
| DEBT-B-NATS-005 closed | `NatsScBusPort` implements `AutoCloseable`; close is idempotent; no flush after drain; publish/register after close fail deterministically | `NatsScBusPortDrainTest` + `ScBusNatsArchitectureTest.natsScBusPortImplementsAutoCloseable` |
| DEBT-B-NATS-004 closed | `ConnectionListener` wired; forceReconnect yields reconnect/resubscribed event; timer-fired handler receives before and after reconnect | `NatsScBusPortReconnectTest` |
| Boundary preserved | No manifest runtime, route assignment, command/response subscriptions, SC-C NATS import, or dispatch persistence replacement | Existing and new architecture tests |
| Code-surface exactness | `NatsSubjectBuilder()` no-arg only; no static codec instantiation; event publisher uses complete `ScJsonWireEnvelope` | Compile + targeted tests |

Expected regression:

```text
>= 480 tests, 0 failures, 0 errors, 0 skipped
```
