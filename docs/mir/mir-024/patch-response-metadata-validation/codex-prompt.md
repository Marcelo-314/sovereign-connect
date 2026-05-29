# Codex Prompt — MU-024 Response Metadata Validation Patch

```text
Version:      v0.2.0-candidate
Parent MIR:   MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate
Patch ID:     PATCH-SOV-SC-B-ABSTRACT-BUS-SEED-RESPONSE-METADATA-VALIDATION-001
```

Apply a focused patch to `MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001` on the existing MU-024 branch:

```text
feat/sc-b-mir-024-abstract-bus-seed
```

Do not create a new branch. Do not open a new MIR. This patch closes the validation gap required before marking MU-024 as Validated L4.

Read first:

```text
docs/mir/mir-024/patch-response-metadata-validation/context.md
docs/mir/mir-024/patch-response-metadata-validation/acceptance-map.md
```

---

## What and why

`EnvelopeValidationService.validateResponse(...)` validates null envelope,
payload, metadata, messageId, correlationId, routing key, and lane mismatch —
but does not validate `responseMetadata`. This patch closes that gap.

Only the validator, its test, and the MU-024 implementation report should change. Nothing else.

---

## Step 1 — Patch EnvelopeValidationService

File: `src/main/java/com/sovereign/connect/bus/runtime/validation/EnvelopeValidationService.java`

Use the **exact code from context.md §4**:

1. Add `import com.sovereign.connect.bus.contract.ScResponseMetadata;`
2. Add `validateResponseMetadata(envelope.responseMetadata());` call at the end
   of `validateResponse()`, after `requireLane(...)`.
3. Add the `private void validateResponseMetadata(ScResponseMetadata)` method.

Do not touch `validateCommand`, `validateEvent`, `validateParts`, or
`requireLane`.

**STOP-1:** `mvn -q compile` must succeed. Only one production code file may change.

---

## Step 2 — Add five test methods

File: `src/test/java/com/sovereign/connect/bus/EnvelopeValidationServiceTest.java`

This file is in the `com.sovereign.connect.bus` package, not in a `validation`
subpackage. Do not create a new test class.

Add the **exact five test methods from context.md §5**. Do not modify or remove
any existing test.

Add three imports at the top:
```java
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import java.util.List;
```

All five tests use `ScBusTestSupport.childMetadata()` — not `rootMetadata()`.
This is required so that correlation validation passes before reaching
`responseMetadata` validation. Using `rootMetadata()` would produce a failure
at the wrong validation layer.

---

## Step 3 — Run targeted tests

```bash
mvn -q test -Dtest="EnvelopeValidationServiceTest,InMemoryScBusPortTest,RuntimeDispatchServiceTest,ScBusArchitectureTest"
```

Expected: all pass, 0 failures.

Manually verify: `EnvelopeValidationServiceTest` now has 5 more test methods
than the original 4 (total = 9 or more, depending on existing count).

---

## Step 4 — Full regression

```bash
mvn -q test
```

Expected: **294 tests, 0 failures, 0 errors, 0 skipped.**

The count is 289 (baseline) + 5 (new) = 294. If the total is not 294, check
whether all five new test methods were added. If SC-C 240 non-bus tests show
any failure, stop immediately.

---

## Step 5 — Update implementation report

Update `docs/mir/mir-024/implementation-report.md` using the exact template
in context.md §7.

Commit on the existing branch:
```bash
git commit -m "fix(sc-b): validate response metadata in response envelopes"
```

---

## Hard stops

```text
- A fix requires changing ScResponseEnvelope, ScResponseMetadata, or
  ScResponseKind field order or constructor shape → STOP
- A fix requires importing com.sovereign.connect.core.* into bus.** → STOP
- Any broker/runtime dependency appears necessary → STOP
- Tests pass only by weakening or removing response lane validation → STOP
- Full mvn -q test total is not 294 or any SC-C test fails → STOP
```
