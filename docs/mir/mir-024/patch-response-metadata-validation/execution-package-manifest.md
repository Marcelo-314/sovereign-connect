# Execution Package Manifest — MU-024 Response Metadata Validation Patch

```text
Package ID:   EP-PATCH-SOV-SC-B-ABSTRACT-BUS-SEED-RESPONSE-METADATA-VALIDATION-001
Version:      v0.2.0-candidate
Parent MIR:   MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate
Scope:        Patch execution package to close response metadata validation gap
Status:       Candidate / ready for Codex execution
Branch:       feat/sc-b-mir-024-abstract-bus-seed
Commit:       fix(sc-b): validate response metadata in response envelopes
```

---

## Files

```text
docs/mir/mir-024/patch-response-metadata-validation/
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report-patch-template.md
  execution-package-manifest.md
```

---

## Purpose

This package authorizes a focused patch to the implemented MU-024 abstract bus seed.

It does not open a new MIR.

It does not create a new branch.

It must be applied on the existing MU-024 branch:

```text
feat/sc-b-mir-024-abstract-bus-seed
```

It closes the response envelope validation gap found during validation review.

---

## Expected changes

```text
Production:
  src/main/java/com/sovereign/connect/bus/runtime/validation/EnvelopeValidationService.java

Tests:
  src/test/java/com/sovereign/connect/bus/EnvelopeValidationServiceTest.java

Documentation:
  docs/mir/mir-024/implementation-report.md
```

No canonical shape may be changed.

No broker/runtime binding dependency may be introduced.

No `com.sovereign.connect.core.*` import may be introduced under `bus.**`.

---

## Closure rule

When the patch passes targeted and full regression tests and the implementation report records the evidence, close:

```text
MU-SOV-SC-B-ABSTRACT-BUS-SEED-001 — Validated L4
```

Expected full regression evidence:

```text
mvn -q test
Tests run: 294
Failures: 0
Errors: 0
Skipped: 0
SC-C / non-bus baseline: 240 tests still green
```
