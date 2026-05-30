# Acceptance Map — PATCH-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-OPAQUE-PAYLOADTYPE-001

```text
Document ID:  acceptance-map-PATCH-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-OPAQUE-PAYLOADTYPE-001
Version:      v0.1.1
Status:       Acceptance map / patch
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B
Scope:        MU-028 micro-patch
```

| ID | Criterion | Evidence |
|---|---|---|
| PATCH-028-001 | `WireEnvelopeValidatorTest.acceptsUnknownButSyntacticallyValidPayloadType` exists. | Test source. |
| PATCH-028-002 | The test uses a payloadType that is syntactically valid but not a seed registry constant. | `sc.command.future.v1` or equivalent. |
| PATCH-028-003 | `WireEnvelopeValidator` accepts the envelope. | Test pass. |
| PATCH-028-004 | `WireEnvelopeValidator` does not enforce closed registry membership. | Test pass / code inspection. |
| PATCH-028-005 | No NATS dependency is introduced. | `pom.xml` / architecture tests. |
| PATCH-028-006 | No NATS binding classes are introduced. | File diff. |
| PATCH-028-007 | No productive `ScdCommand` or SC-D fact family is introduced. | File diff. |
| PATCH-028-008 | Implementation report records final counts and patch outcome. | `docs/mir/mir-028/implementation-report.md`. |
| PATCH-028-009 | Full regression passes. | Surefire report / terminal output. |

Expected full regression after patch:

```text
>= 411 tests, 0 failures, 0 errors, 0 skipped
```
