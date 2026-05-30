# Execution Package Manifest — PATCH-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-OPAQUE-PAYLOADTYPE-001

```text
Document ID:  execution-package-manifest-PATCH-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-OPAQUE-PAYLOADTYPE-001
Version:      v0.1.1
Status:       Manifest
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B
Scope:        MU-028 micro-patch
```

## Files

```text
context.md
codex-prompt.md
acceptance-map.md
implementation-report.md
execution-package-manifest.md
```

## Canonical local path

```text
docs/mir/mir-028/patch-opaque-payloadtype-validation/
```

## Branch

```text
feat/sc-b-mir-028-serialization-utilities-seed
```

## Suggested commit

```text
fix(sc-b): cover opaque payload type validation
```

## Expected result

```text
MU-028 remains within serialization utilities scope.
One extra validator-level test closes the validation evidence gap.
Full regression >= 411 tests, 0 failures, 0 errors, 0 skipped.
```


## v0.1.1 update

This version refines the Codex prompt by including the exact validator test body for `WireEnvelopeValidatorTest.acceptsUnknownButSyntacticallyValidPayloadType`, using `sc.command.future.v1` as a syntactically valid payload type outside the seed registry.
