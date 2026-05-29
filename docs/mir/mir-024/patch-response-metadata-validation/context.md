# Patch Context — MU-024 Response Metadata Validation

```text
Document ID:  PATCH-SOV-SC-B-ABSTRACT-BUS-SEED-RESPONSE-METADATA-VALIDATION-001
Version:      v0.2.0-candidate
Status:       Candidate / ready for Codex execution
Date:         2026-05-29
Corpus:       Sovereign Connect
Plane:        SC-B
Parent MIR:   MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate
MU:           MU-SOV-SC-B-ABSTRACT-BUS-SEED-001
Slot:         MU-024
Baseline:     feat/sc-b-mir-024-abstract-bus-seed
Primary commit: 899235a2e094ba4d4adaf4d6f0c6331bfc3560e7
Report commit:  0a2b5ab docs(sc-b): record abstract bus seed implementation report
```

---

## 0. Purpose

Closes the only blocking validation gap in `MU-SOV-SC-B-ABSTRACT-BUS-SEED-001`.

`EnvelopeValidationService.validateResponse(...)` does not currently reject a
response envelope whose `responseMetadata` is null. Because the response lane
exists specifically to preserve request-bound response semantics,
`ScResponseMetadata` is mandatory for every response envelope.

This patch adds `responseMetadata` validation and five new tests, without
changing any canonical shape or broader abstract bus seed scope.

This patch MUST be applied on the existing MU-024 branch. It does not open a
new branch and does not open a new MIR.

---

## 1. Confirmed baseline facts (verified against sovereign-connect-024.zip)

```text
EnvelopeValidationService location:
  src/main/java/com/sovereign/connect/bus/runtime/validation/EnvelopeValidationService.java

Test class location — important: NOT in the validation subpackage:
  src/test/java/com/sovereign/connect/bus/EnvelopeValidationServiceTest.java

ScBusTestSupport location:
  src/test/java/com/sovereign/connect/bus/ScBusTestSupport.java

Existing validateResponse method (confirmed):
  validates null envelope, null payload, null metadata, messageId, correlationId,
  routing key, and lane/family mismatch.
  Does NOT validate responseMetadata — this is the gap.

ScBusTestSupport.responseEnvelope() builds:
  new ScResponseEnvelope<>(
    childMetadata(),
    new TestResponsePayload("ok"),
    routing(ScBusLane.RESPONSE),
    new ScResponseMetadata(UUID.randomUUID(), UUID.randomUUID(),
      ScResponseKind.EXECUTION_RESULT, true, false, null, List.of()))

  → already valid: non-null requestMessageId, responseKind, List.of() warnings
  → existing test acceptsValidCommandEventAndResponseFamilies will still pass after patch

Test payload types already defined in src/test/java:
  TestCommandPayload, TestEventPayload, TestResponsePayload, ScdCommandStub

ScResponseMetadata constructor (confirmed field order):
  ScResponseMetadata(UUID requestMessageId, UUID requestId,
                     ScResponseKind responseKind,
                     boolean terminal, boolean retryable,
                     String sanitizedReason, List<ScResponseWarning> warnings)
```

---

## 2. Patch scope

### 2.1 Production file to change (one file only)

```text
src/main/java/com/sovereign/connect/bus/runtime/validation/EnvelopeValidationService.java
```

Change: add `validateResponseMetadata(ScResponseMetadata)` private method and
call it from `validateResponse(...)` after existing lane validation.

### 2.2 Test file to change (one file, add methods only)

```text
src/test/java/com/sovereign/connect/bus/EnvelopeValidationServiceTest.java
```

Change: add five new test methods. Do not modify or remove existing tests.

### 2.3 Documentation to update

```text
docs/mir/mir-024/implementation-report.md
```

Add patch note section (see §7).

---

## 3. Required validation behavior

`EnvelopeValidationService.validateResponse(...)` MUST reject when:

```text
1. envelope == null                                  (already validated)
2. envelope.payload() == null                        (already validated)
3. envelope.metadata() == null                       (already validated)
4. envelope.metadata().messageId() == null           (already validated)
5. envelope.metadata().correlationId() == null       (already validated)
6. envelope.routingKey() == null                     (already validated)
7. envelope.routingKey().lane() != RESPONSE          (already validated)
8. envelope.responseMetadata() == null               ← GAP: add this
9. envelope.responseMetadata().requestMessageId() == null  ← GAP
10. envelope.responseMetadata().responseKind() == null     ← GAP
11. envelope.responseMetadata().warnings() == null         ← GAP
```

A response metadata with empty `List.of()` warnings is valid.

`requestId` and `sanitizedReason` MAY remain nullable — `ScBusTestSupport`
already builds valid envelopes with `requestId` = non-null and
`sanitizedReason` = null. Do not add validation for those fields.

---

## 4. Production patch — exact code

In `EnvelopeValidationService.java`, add the private method and call it:

```java
public void validateResponse(ScResponseEnvelope<?> envelope) {
    if (envelope == null) {
        throw new IllegalArgumentException("envelope is required");
    }
    validateParts(envelope.metadata(), envelope.payload(), envelope.routingKey());
    requireLane(envelope.routingKey().lane(), ScBusLane.RESPONSE);
    validateResponseMetadata(envelope.responseMetadata());   // ← add this call
}

private void validateResponseMetadata(ScResponseMetadata responseMetadata) {
    if (responseMetadata == null) {
        throw new IllegalArgumentException("responseMetadata is required");
    }
    if (responseMetadata.requestMessageId() == null) {
        throw new IllegalArgumentException("responseMetadata.requestMessageId is required");
    }
    if (responseMetadata.responseKind() == null) {
        throw new IllegalArgumentException("responseMetadata.responseKind is required");
    }
    if (responseMetadata.warnings() == null) {
        throw new IllegalArgumentException("responseMetadata.warnings is required");
    }
}
```

Required additional import in `EnvelopeValidationService.java`:

```java
import com.sovereign.connect.bus.contract.ScResponseMetadata;
```

The existing `validateCommand`, `validateEvent`, `validateParts` and
`requireLane` methods are unchanged.

---

## 5. Test patch — exact code, paste-ready

Add these five methods to `EnvelopeValidationServiceTest`. Do not modify
or remove any existing test method.

```java
@Test
void rejectsNullResponseMetadata() {
    assertThatThrownBy(() -> service.validateResponse(
            new ScResponseEnvelope<>(
                ScBusTestSupport.childMetadata(),
                new TestResponsePayload("ok"),
                ScBusTestSupport.routing(ScBusLane.RESPONSE),
                null)))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void rejectsNullRequestMessageIdInResponseMetadata() {
    var meta = new ScResponseMetadata(
        null, UUID.randomUUID(),
        ScResponseKind.EXECUTION_RESULT, true, false, null, List.of());
    assertThatThrownBy(() -> service.validateResponse(
            new ScResponseEnvelope<>(
                ScBusTestSupport.childMetadata(),
                new TestResponsePayload("ok"),
                ScBusTestSupport.routing(ScBusLane.RESPONSE),
                meta)))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void rejectsNullResponseKindInResponseMetadata() {
    var meta = new ScResponseMetadata(
        UUID.randomUUID(), UUID.randomUUID(),
        null, true, false, null, List.of());
    assertThatThrownBy(() -> service.validateResponse(
            new ScResponseEnvelope<>(
                ScBusTestSupport.childMetadata(),
                new TestResponsePayload("ok"),
                ScBusTestSupport.routing(ScBusLane.RESPONSE),
                meta)))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void rejectsNullWarningsListInResponseMetadata() {
    var meta = new ScResponseMetadata(
        UUID.randomUUID(), UUID.randomUUID(),
        ScResponseKind.EXECUTION_RESULT, true, false, null, null);
    assertThatThrownBy(() -> service.validateResponse(
            new ScResponseEnvelope<>(
                ScBusTestSupport.childMetadata(),
                new TestResponsePayload("ok"),
                ScBusTestSupport.routing(ScBusLane.RESPONSE),
                meta)))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void acceptsEmptyWarningsListInResponseMetadata() {
    var meta = new ScResponseMetadata(
        UUID.randomUUID(), UUID.randomUUID(),
        ScResponseKind.EXECUTION_RESULT, true, false, null, List.of());
    assertThatNoException().isThrownBy(() -> service.validateResponse(
            new ScResponseEnvelope<>(
                ScBusTestSupport.childMetadata(),
                new TestResponsePayload("ok"),
                ScBusTestSupport.routing(ScBusLane.RESPONSE),
                meta)));
}
```

Required additional imports in `EnvelopeValidationServiceTest.java`:

```java
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import java.util.List;
```

Note on `childMetadata()`: these tests use `ScBusTestSupport.childMetadata()`
(correlationId ≠ messageId, causationId non-null) to pass correlation
validation before reaching responseMetadata validation. Using `rootMetadata()`
would cause the tests to fail at correlation validation instead — the wrong
failure point.

---

## 6. Stop conditions

```text
STOP-1: mvn -q compile → BUILD SUCCESS.
        Verify: one modified production file, one modified test file.

STOP-2: mvn -q test -Dtest="EnvelopeValidationServiceTest,InMemoryScBusPortTest,
                             RuntimeDispatchServiceTest,ScBusArchitectureTest"
        Expected: all pass, 0 failures.
        Verify manually: 5 new test methods present in EnvelopeValidationServiceTest.

STOP-3: mvn -q test
        Expected: 294 tests, 0 failures, 0 errors, 0 skipped.
        (289 baseline + 5 new = 294)
        If count is not 294, check whether all 5 new methods were added.
        If SC-C 240 non-bus tests show any failure, stop — do not proceed.
```

---

## 7. Implementation report addition

Add to `docs/mir/mir-024/implementation-report.md`:

```text
## Response metadata validation patch

Patch commit: <hash>
Description: add responseMetadata validation to validateResponse()

Gap closed:
  EnvelopeValidationService.validateResponse() did not reject
  null responseMetadata or its required fields.

Changes:
  EnvelopeValidationService.java — added validateResponseMetadata() private method
  EnvelopeValidationServiceTest.java — added 5 test methods

Validation:
  Targeted tests: PASS
  Full mvn -q test: 294 tests / 0 failures / 0 errors / 0 skipped
  SC-C baseline (240 tests): still green

MU-SOV-SC-B-ABSTRACT-BUS-SEED-001 closure: Validated L4
```

---

## 8. Negative scope

```text
Do not change: ScResponseEnvelope, ScResponseMetadata, ScResponseKind shapes
Do not change: RuntimeDispatchService state machine
Do not change: InMemoryScBusPort handler behavior
Do not add:    ScDeliveryError canonical shape
Do not add:    ScdCommand production class
Do not add:    SC-D fact-family production classes
Do not add:    NATS, JetStream, Redis, Vert.x, gRPC dependency
Do not import: com.sovereign.connect.core.* from bus.**
Do not remove: any existing test
Do not weaken: ScBusArchitectureTest assertions
```
