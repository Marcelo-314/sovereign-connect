# Acceptance Map — PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001 v0.1.0

| AC | Requirement | Evidence |
|---|---|---|
| AC-PATCH-001 | `NatsConnectionFactory` exists under `com.sovereign.connect.bus.runtime.nats`. | Source file present. |
| AC-PATCH-002 | Factory owns `Options` construction and calls `Nats.connect(...)`. | Source inspection + tests. |
| AC-PATCH-003 | Factory supports optional `ConnectionListener` wiring. | Factory test with listener or documented equivalent. |
| AC-PATCH-004 | Existing reconnect/drain/JetStream debt-closure behavior remains green. | Full `mvn test`; existing MIR-031 tests pass. |
| AC-PATCH-005 | Reconnect tests no longer build `Options` ad hoc when the factory can be used. | `NatsScBusPortReconnectTest` helper uses `NatsConnectionFactory`. |
| AC-PATCH-006 | `NatsScBusPort` remains responsible for handler registry and bus behavior. | No scope expansion; source inspection. |
| AC-PATCH-007 | No Manifest/runtime/route assignment/command admission/Flyway changes. | `git diff --name-status`; source inspection. |
| AC-PATCH-008 | `NatsConnectionFactory` does not import `core.**`, `adapter.**`, `integration.**`, manifest, dispatch persistence. | Architecture test or source inspection. |
| AC-PATCH-009 | `NatsSubjectBuilder` shape remains no-arg. | Tests/source inspection. |
| AC-PATCH-010 | Final regression passes. | Expected >=483 tests, 0 failures/errors/skips. |
