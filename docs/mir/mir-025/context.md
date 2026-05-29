# Execution Context — MU-025 SC-B Dispatch State Persistence

```text
Document ID:  context-MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
Version:      v0.1.0
Status:       Execution package context
Corpus:       Sovereign Connect
Plane:        SC-B
MU ID:        MU-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
MU Slot:      MU-025
Track:        SC-B Runtime Dispatch Hardening / H1
Branch:       feat/sc-b-mir-025-dispatch-state-persistence
Commit:       feat(sc-b): persist runtime dispatch state
```

---

## 0. Reading order

Before writing code, read:

```text
docs/mir/mir-025/MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001.md
docs/mir/mir-025/context.md
docs/mir/mir-025/acceptance-map.md
docs/mir/mir-025/code-surface-audit.md
```

This execution package includes the MIR as a first-class reference artifact. The MIR remains normative for scope; this context is operational guidance for implementation.

---

## 1. Purpose

Implement `MU-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001` as the first hardening increment after `MU-024`.

The increment replaces the in-memory SC-B dispatch state repository with restart-visible JDBC-backed persistence.

It hardens only:

```text
DispatchStateWritePort
DispatchAttempt lifecycle
claim/retry/exhaustion/supersede technical state
restart visibility of SC-B technical dispatch state
SC-B migration boundary
```

It does not implement the outbox bridge, observation persistence, lifecycle channel, NATS, JetStream or any SC-D adapter behavior.

---

## 2. Verified baseline

Post-MU-024 baseline:

```text
sovereign-connect module:
  SC-C / non-bus baseline: 240 tests, 0 failures, 0 errors, 0 skipped
  Bus delta:               54 tests, 0 failures, 0 errors, 0 skipped
  Total:                   294 tests, 0 failures, 0 errors, 0 skipped

EIB module:
  56 tests, 0 failures, 0 errors, 0 skipped
```

Current package tree:

```text
com.sovereign.connect.bus.contract
com.sovereign.connect.bus.runtime.dispatch
com.sovereign.connect.bus.runtime.dispatch.model
com.sovereign.connect.bus.runtime.port
com.sovereign.connect.bus.runtime.inmemory
com.sovereign.connect.bus.runtime.validation
```

Absent packages:

```text
com.sovereign.connect.bus.runtime.persistence
com.sovereign.connect.integration.scledgerdispatch
```

H1 creates `bus.runtime.persistence`. H2 later creates `integration.scledgerdispatch`.

---

## 3. Hard boundaries

### 3.1 H1 allowed

```text
- Add com.sovereign.connect.bus.runtime.persistence.
- Add JdbcDispatchStateRepository or equivalently named persistent adapter.
- Add SC-B migration file using V100+ unless a separate SC-B migration location already exists.
- Add tests for persistent dispatch state.
- Add/extend architecture tests for SC-B migration naming and boundary cleanliness.
- Preserve InMemoryDispatchStateRepository.
```

### 3.2 H1 forbidden

```text
- No SC-C outbox bridge.
- No ScOutboxDispatchReadPort.
- No integration.scledgerdispatch package.
- No OutboxEntry -> DispatchCandidate projection.
- No DispatchObservationPort persistence.
- No DeliveryLane -> ScBusLane bridge mapping.
- No SIGNAL handling.
- No mutation or reading of OutboxEntryStatus.
- No NATS, JetStream, Redis, Vert.x, MQTT, Kafka, gRPC or broker dependency.
- No SC-D lifecycle, adapter onboarding, ScdCommand or fact-family production shapes.
```

### 3.3 Import boundaries

```text
bus.** MUST NOT import com.sovereign.connect.core.**
core.** MUST NOT import com.sovereign.connect.bus.runtime.**
bus.** MUST NOT import physical broker/runtime APIs.
```

The persistent SC-B state repository belongs under:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence
```

not under `adapter.persistence.sqlite` and not under `core.*`.

---

## 4. Existing code shapes

### 4.1 DispatchState

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

public enum DispatchState {
    PENDING,
    CLAIMED,
    DISPATCHING,
    DISPATCHED,
    DELIVERY_FAILED,
    RETRY_SCHEDULED,
    EXHAUSTED,
    CANCELLED_BY_SUPERSEDE
}
```

### 4.2 DispatchAttempt

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

import java.time.Instant;
import java.util.UUID;

public record DispatchAttempt(
        UUID attemptId,
        UUID dispatchRecordId,
        int attemptNumber,
        DispatchState state,
        Instant claimedAt
) {
}
```

Do not change this record unless unavoidable. If changed, justify in implementation report.

### 4.3 DispatchStateWritePort

```java
package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;

import java.util.Optional;
import java.util.UUID;

public interface DispatchStateWritePort {
    DispatchAttempt claim(UUID dispatchRecordId);
    DispatchAttempt transition(UUID dispatchRecordId, DispatchState targetState);
    DispatchAttempt transitionWithEvidence(UUID dispatchRecordId, DispatchState targetState, String evidenceRef);
    Optional<DispatchAttempt> currentAttempt(UUID dispatchRecordId);
}
```

H1 must implement this existing port. Avoid changing it.

### 4.4 Current in-memory semantics to preserve

`InMemoryDispatchStateRepository` currently:

```text
- treats no current record as implicit PENDING;
- allows claim from PENDING;
- allows claim from RETRY_SCHEDULED;
- rejects claim from all other states;
- sets first attemptNumber = 1;
- increments attemptNumber after RETRY_SCHEDULED;
- preserves attemptId and claimedAt across transitions;
- requires evidenceRef for CANCELLED_BY_SUPERSEDE;
- permits CANCELLED_BY_SUPERSEDE only from PENDING, CLAIMED and RETRY_SCHEDULED;
- treats DISPATCHED, EXHAUSTED and CANCELLED_BY_SUPERSEDE as terminal technical states.
```

The persistent adapter must preserve these semantics.

---

## 5. Required production files

Expected production files:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepository.java
src/main/resources/db/migration/V100__sc_b_dispatch_state_persistence.sql
```

Optional internal row-mapping helpers:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/DispatchStateRow.java
src/main/java/com/sovereign/connect/bus/runtime/persistence/DispatchAttemptRow.java
```

Naming may vary only if mapped in the implementation report.

---

## 6. Migration strategy

Use single-stream high-number offset unless the active repository already has a multi-location SC-B Flyway strategy.

Default file:

```text
src/main/resources/db/migration/V100__sc_b_dispatch_state_persistence.sql
```

Rules:

```text
- V1–V4 are SC-C-owned.
- Reserve V5–V99 for SC-C.
- Use V100+ for SC-B seed/hardening migrations.
- The migration must create only sc_b_* tables.
- The migration must not alter SC-C tables.
```

Recommended minimal DDL:

```sql
CREATE TABLE IF NOT EXISTS sc_b_dispatch_records (
    dispatch_record_id TEXT PRIMARY KEY,
    source_record_id TEXT NULL,
    current_attempt_id TEXT NULL,
    current_attempt_number INTEGER NOT NULL DEFAULT 0,
    current_state TEXT NOT NULL,
    supersession_evidence_ref TEXT NULL,
    created_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    CHECK (current_state IN (
        'PENDING', 'CLAIMED', 'DISPATCHING', 'DISPATCHED',
        'DELIVERY_FAILED', 'RETRY_SCHEDULED', 'EXHAUSTED',
        'CANCELLED_BY_SUPERSEDE'
    ))
);

CREATE TABLE IF NOT EXISTS sc_b_dispatch_attempts (
    attempt_id TEXT PRIMARY KEY,
    dispatch_record_id TEXT NOT NULL,
    attempt_number INTEGER NOT NULL,
    state TEXT NOT NULL,
    claimed_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    supersession_evidence_ref TEXT NULL,
    FOREIGN KEY (dispatch_record_id) REFERENCES sc_b_dispatch_records(dispatch_record_id),
    UNIQUE (dispatch_record_id, attempt_number),
    CHECK (state IN (
        'PENDING', 'CLAIMED', 'DISPATCHING', 'DISPATCHED',
        'DELIVERY_FAILED', 'RETRY_SCHEDULED', 'EXHAUSTED',
        'CANCELLED_BY_SUPERSEDE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_sc_b_dispatch_attempts_record
    ON sc_b_dispatch_attempts(dispatch_record_id);
```

`source_record_id` is nullable and opaque. It exists only as schema preparation for H2. H1 must not populate it from `OutboxEntry`, read SC-C outbox rows, join against `core.scledger`, or treat it as semantic authority.

---

## 7. JdbcDispatchStateRepository behavior

### 7.1 Constructor

Expected shape:

```java
public final class JdbcDispatchStateRepository implements DispatchStateWritePort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDispatchStateRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
    }
}
```

### 7.2 claim(UUID dispatchRecordId)

Must:

```text
- reject null dispatchRecordId;
- create the first CLAIMED attempt when no record exists for dispatchRecordId;
- treat missing row as implicit PENDING;
- allow claim from explicit PENDING;
- allow claim from RETRY_SCHEDULED;
- reject claim from CLAIMED, DISPATCHING, DISPATCHED, DELIVERY_FAILED, EXHAUSTED, CANCELLED_BY_SUPERSEDE;
- set attemptNumber = 1 for the first CLAIMED attempt;
- increment attemptNumber when claiming after RETRY_SCHEDULED;
- persist current record and attempt row atomically enough for seed/JDBC local behavior;
- return DispatchAttempt(attemptId, dispatchRecordId, attemptNumber, CLAIMED, claimedAt).
```

### 7.3 transition(UUID dispatchRecordId, DispatchState targetState)

Must:

```text
- reject null dispatchRecordId;
- reject null targetState;
- reject CANCELLED_BY_SUPERSEDE without evidenceRef;
- preserve allowed MU-024 transitions;
- reject forbidden transitions;
- preserve current attemptId, attemptNumber and claimedAt;
- update current state and current attempt row;
- return updated DispatchAttempt.
```

Allowed transitions:

```text
PENDING -> CLAIMED
CLAIMED -> DISPATCHING
DISPATCHING -> DISPATCHED
DISPATCHING -> DELIVERY_FAILED
DELIVERY_FAILED -> RETRY_SCHEDULED
RETRY_SCHEDULED -> CLAIMED
DELIVERY_FAILED -> EXHAUSTED
PENDING -> CANCELLED_BY_SUPERSEDE, only through transitionWithEvidence
CLAIMED -> CANCELLED_BY_SUPERSEDE, only through transitionWithEvidence
RETRY_SCHEDULED -> CANCELLED_BY_SUPERSEDE, only through transitionWithEvidence
```

### 7.4 transitionWithEvidence(UUID, CANCELLED_BY_SUPERSEDE, evidenceRef)

Must:

```text
- reject null/blank evidenceRef;
- permit cancellation only from PENDING, CLAIMED or RETRY_SCHEDULED;
- persist state CANCELLED_BY_SUPERSEDE;
- persist supersessionEvidenceRef;
- keep terminal after restart.
```

### 7.5 currentAttempt(UUID dispatchRecordId)

Must:

```text
- return Optional.empty() for unknown dispatchRecordId;
- return persisted current attempt for known dispatchRecordId when an attempt exists;
- preserve attemptId, dispatchRecordId, attemptNumber, state and claimedAt across repository recreation.
```

If the implementation supports explicit PENDING rows with no attempt, `currentAttempt` may return `Optional.empty()` until the first claim.

---

## 8. Tests required

Add tests under:

```text
src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepositoryTest.java
src/test/java/com/sovereign/connect/bus/ScBusHardeningArchitectureTest.java
```

Minimum test coverage:

```text
1. first claim is persisted;
2. currentAttempt survives repository recreation;
3. CLAIMED -> DISPATCHING -> DISPATCHED survives restart;
4. DISPATCHING -> DELIVERY_FAILED survives restart;
5. DELIVERY_FAILED -> RETRY_SCHEDULED -> CLAIMED increments attemptNumber;
6. retry attemptNumber continuity survives repository recreation;
7. EXHAUSTED is persistent and not claimable;
8. CANCELLED_BY_SUPERSEDE requires evidenceRef;
9. CANCELLED_BY_SUPERSEDE persists evidenceRef or equivalent diagnostic field;
10. invalid transitions are rejected;
11. unknown currentAttempt returns Optional.empty();
12. SC-B migrations do not collide with SC-C V1–V4;
13. bus.** still does not import core.**;
14. core.** still does not import bus.runtime.**;
15. no broker dependency is introduced;
16. sourceRecordId, if present, remains nullable/opaque and no H1 code imports OutboxEntry.
```

Recommended minimum delta: `+12 tests`.

Expected full test result:

```text
mvn -q test
previous total: 294 tests
expected total: >= 306 tests
failures/errors/skipped: 0
```

---

## 9. Architecture test requirements

Use the existing file-walk + `Files.readString` + AssertJ style. Do not add ArchUnit.

Required architecture checks:

```text
- bus runtime persistence does not import core.*;
- core does not import bus.runtime.*;
- bus does not import broker APIs;
- no new physical bus binding dependency in pom.xml or eib/pom.xml;
- SC-B migrations do not use V1–V9 SC-C range;
- SC-B migration file creates only sc_b_* tables;
- H1 did not introduce ScOutboxDispatchReadPort;
- H1 did not introduce integration.scledgerdispatch.
```

---

## 10. Implementation report obligations

The implementation report must record:

```text
- branch;
- implementation commit hash;
- evidence/docs commit hash, if separate;
- changed files;
- new package(s);
- migration strategy: V100+ or separate SC-B migration location;
- whether nullable sourceRecordId is present;
- exact test commands and summaries;
- AC-025-001..030 PASS/FAIL map;
- retained debts H2/H3;
- confirmation that no OutboxEntry/OutboxEntryStatus was read or mutated;
- confirmation that no broker dependencies were introduced.
```
