# decision-confirmation.md — MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001

```text
Document:         decision-confirmation.md
MU:               MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001
Operational Slot: MU-015
Version:          v0.1.1-draft
Status:           Approved for execution package
MIR:              MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001 v0.1.1-draft
CSA:              code-surface-audit-MU-015-v0.1.1-merged
Date:             2026-05-16
```

---

## DEC-015-001 — Adapter placement

Decision:

```text
Use H2BaseTopologyRepository for MU-015 seed implementation.
```

Constraint:

```text
This is seed-local consolidation only.
It MUST NOT be interpreted as production persistence modularity.
```

Deferred note:

```text
A future production adapter may split ledger/outbox into a dedicated persistence adapter/module.
```

---

## DEC-015-002 — Port names

Decision:

```text
Use ScLedgerWritePort and ScOutboxWritePort.
```

Rationale:

```text
WritePort makes append-only scope explicit and avoids implying query, claim or dispatcher behavior.
```

---

## DEC-015-003 — Package placement

Decision:

```text
Use com.sovereign.connect.core.scledger.model
Use com.sovereign.connect.core.scledger.port
```

Rationale:

```text
The ledger is the semantic anchor.
Outbox is durable dispatch intent derived from semantic records.
core.scledger prevents the seed from being read as transport-first.
```

This resolves the prior MIR/CSA package naming divergence in favor of `core.scledger`.

---

## DEC-015-004 — Readback strategy

Decision:

```text
Do not introduce read/claim ports.
Use test-local JdbcTemplate/direct SQL for readback.
```

Rationale:

```text
Recovery-visible readback is test evidence, not a dispatcher/query contract.
```

---

## DEC-015-005 — Append semantics

Decision:

```text
Append is INSERT-only.
MERGE is forbidden for ledger/outbox append in MU-015.
```

Rationale:

```text
Ledger rows are immutable semantic evidence.
Outbox rows are append-created in MU-015 and remain PENDING only.
Future dispatcher/status updates are explicitly deferred.
```

---

## DEC-015-006 — Idempotency key scope

Decision:

```text
Use UNIQUE(habitat_id, idempotency_key) for both ledger and outbox seed tables.
```

Rationale:

```text
Preserves habitat scoping and supports future TemporalAct fire idempotency without global key collisions across habitats.
```

Required proof:

```text
- duplicateLedgerIdempotencyKeyRejected
- duplicateOutboxIdempotencyKeyRejected
- sameIdempotencyKeyAllowedAcrossDifferentHabitats
```

---

## DEC-015-007 — Delivery observations

Decision:

```text
sc_c_delivery_observations remains out of scope.
```

Rationale:

```text
MU-015 has no delivery attempts or dispatcher.
```

---

## DEC-015-008 — Terminal responses

Decision:

```text
sc_c_terminal_responses remains out of scope.
```

Rationale:

```text
Terminal response persistence belongs to command/request boundary or Profile B work.
```

---

## DEC-015-009 — TemporalAct aggregate

Decision:

```text
No TemporalAct aggregate, repository, query port or service is introduced.
```

Rationale:

```text
MU-015 supports future TemporalAct records but does not implement TemporalActs.
```

---

## DEC-015-010 — Index strategy

Decision:

```text
Use normal H2 indexes, not partial indexes, for the seed.
```

Rationale:

```text
Dispatcher performance is out of scope; normal indexes avoid H2-version friction.
```

---

## DEC-015-011 — H2 FK enforcement

Decision:

```text
H2 FK enforcement must be enabled at connection/setup level, or appendOutboxEntry(...) must validate ledger_entry_id existence before INSERT.
```

Preferred seed implementation:

```text
Execute SET REFERENTIAL_INTEGRITY TRUE during schema/setup if needed by the H2 profile.
```

Fallback:

```text
appendOutboxEntry(...) checks that ledger_entry_id exists in sc_c_ledger_entries and throws a deterministic exception if missing.
```

Required proof:

```text
outboxEntryWithoutLedgerReferenceRejected
```

---

## DEC-015-012 — Ledger/outbox habitat coherence

Decision:

```text
An OutboxEntry MUST reference a LedgerEntry in the same habitat.
```

Preferred seed implementation:

```text
Use composite FK:
  FOREIGN KEY (ledger_entry_id, habitat_id)
  REFERENCES sc_c_ledger_entries(ledger_entry_id, habitat_id)

and make (ledger_entry_id, habitat_id) unique on sc_c_ledger_entries.
```

Fallback:

```text
If H2 composite FK behavior creates implementation friction, appendOutboxEntry(...)
MUST explicitly validate that the referenced ledger row exists with the same habitat_id.
```

Required proof:

```text
outboxEntryHabitatMustMatchLedgerHabitat
```
