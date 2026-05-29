# Acceptance Map — MU-025 SC-B Dispatch State Persistence

```text
Document ID:  acceptance-map-MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
Version:      v0.1.0
Status:       Execution package acceptance map
Corpus:       Sovereign Connect
Plane:        SC-B
MU ID:        MU-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
```

---

## Expected test baseline

```text
Pre-MU-025 sovereign-connect: 294 tests, 0 failures, 0 errors, 0 skipped
Expected post-MU-025:         >= 306 tests, 0 failures, 0 errors, 0 skipped
Expected bus delta:           +12 tests minimum
EIB, if run:                  56 tests, 0 failures, 0 errors, 0 skipped
```

---

## Acceptance criteria mapping

| AC | Criterion | Evidence required |
|---|---|---|
| AC-025-001 | H1 dispatch state persistence only | Changed files; no H2/H3 packages |
| AC-025-002 | No outbox bridge, no `ScOutboxDispatchReadPort`, no `integration.scledgerdispatch` | Architecture test; file inventory |
| AC-025-003 | No dispatch observation persistence | Changed files; `DispatchObservationPort` implementation unchanged or still in-memory |
| AC-025-004 | No NATS/JetStream/broker dependencies | Architecture test over sources and POMs |
| AC-025-005 | `bus.**` does not import `core.**` | `ScBusHardeningArchitectureTest` |
| AC-025-006 | `core.**` does not import `bus.runtime.**` | `ScBusHardeningArchitectureTest` |
| AC-025-007 | Persistent adapter for `DispatchStateWritePort` exists | `JdbcDispatchStateRepository` or equivalent |
| AC-025-008 | Persistent adapter supports all port methods | `JdbcDispatchStateRepositoryTest` |
| AC-025-009 | `InMemoryDispatchStateRepository` remains usable | Existing tests still pass; file still present |
| AC-025-010 | JDBC/Flyway-compatible storage; no JPA | Source review; POM review |
| AC-025-011 | Allowed MU-024 transitions remain allowed | State transition tests |
| AC-025-012 | Forbidden MU-024 transitions remain rejected | Invalid transition tests |
| AC-025-013 | `CLAIMED` does not mean adapter acceptance | Test names/assertions/report rationale |
| AC-025-014 | `DISPATCHED` does not mean semantic success | Test names/assertions/report rationale |
| AC-025-015 | `EXHAUSTED` does not mean domain failure | Test names/assertions/report rationale |
| AC-025-016 | `currentAttempt` survives repository recreation | Restart visibility test |
| AC-025-017 | CLAIMED/DISPATCHING/DELIVERY_FAILED/RETRY_SCHEDULED restart-visible | Restart visibility tests |
| AC-025-018 | EXHAUSTED remains terminal after restart | Terminal-state test |
| AC-025-019 | CANCELLED_BY_SUPERSEDE remains terminal after restart | Terminal-state/evidence test |
| AC-025-020 | Retry attemptNumber continuity survives recreation | Retry continuity test |
| AC-025-021 | Supersede cancellation rejects null/blank evidenceRef | Evidence validation tests |
| AC-025-022 | Supersede evidence is persisted or equivalent diagnostic field exists | Persistence/evidence test |
| AC-025-023 | Retry from DELIVERY_FAILED → RETRY_SCHEDULED → CLAIMED creates new attempt | Retry test |
| AC-025-024 | SC-B migration creates only `sc_b_*` tables | Migration SQL architecture test |
| AC-025-025 | SC-B migration does not collide with SC-C Flyway V1–V4 | Migration naming test |
| AC-025-026 | Implementation report records migration strategy and sourceRecordId decision | Implementation report |
| AC-025-027 | Implementation report records branch, commit and changed files | Implementation report |
| AC-025-028 | Implementation report records test command and full summary | Implementation report |
| AC-025-029 | Implementation report maps every AC-025 criterion | Implementation report AC table |
| AC-025-030 | Implementation report preserves retained debts H2/H3 | Implementation report debt section |

---

## Required test classes

Recommended names:

```text
JdbcDispatchStateRepositoryTest
ScBusHardeningArchitectureTest
```

Equivalent names are acceptable if the implementation report maps them to all ACs.

---

## Negative-scope assertions

The acceptance evidence must show:

```text
ScOutboxDispatchReadPort absent
integration.scledgerdispatch absent
OutboxEntryStatus not imported by bus.runtime.persistence
NATS / JetStream absent
SC-B migration not named V5–V99
```
