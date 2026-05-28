# Package Review Notes — MU-023 EIB Hardening

```text
Package: execution-package-MU-023-eib-hardening
Reviewed version: v0.3.1-reviewed
Date: 2026-05-28
Reviewer disposition: Ready for Codex after targeted corrections
```

## Adopted baseline

The user-provided `context-mu023.md` and `codex-prompt-mu023.md` were adopted as the operational baseline because they are more executable than the earlier package and provide concrete code snippets, stop conditions and test expectations.

## Corrections applied

### 1. `EibTemporalAdmissionService` constructor order

The submitted context instantiated:

```java
new EibTemporalAdmissionService(client, codec, envelopeMapper)
```

The MU-022 baseline constructor is:

```java
new EibTemporalAdmissionService(client, envelopeMapper, codec)
```

The context was corrected accordingly.

### 2. Stale `EibApiControllerTest` expectations

Introducing `EibHttpResponseMapper` changes top-level admission response vocabulary. Existing tests that expected top-level `ACCEPTED` for every non-failure response would become stale.

The context/prompt now require:

```text
signal admission:
  HTTP 202 / EibResponse.status = ADMITTED

cancel terminal admission:
  HTTP 200 / EibResponse.status = COMPLETED
```

### 3. API-level upstream-unavailable test wording

The controller does not catch `EibUpstreamUnavailableException` directly. The service catches upstream failures and returns `InteractionAdmissionDecision(status = FAILED_UPSTREAM_UNAVAILABLE)`, and the controller maps that decision through `EibHttpResponseMapper`.

The context/prompt were corrected to avoid instructing Codex to add a controller-level exception catch.

### 4. Timeout test wording

The submitted timeout test used `192.0.2.1` for both operations and called the second method a read-timeout test. That is not strictly accurate; it validates timeout normalization through a second operation, not necessarily a server-side delayed-read path.

The method was renamed to:

```text
listTemporalActsTimeoutProducesEibUpstreamUnavailableException
```

No new test dependency is introduced.

### 5. Typo correction

`hardenig` -> `hardening` in the Codex prompt.

## Final status

This package is ready for Codex under the same branch and commit strategy:

```text
Branch: feat/sc-eib-mir-023-effective-interaction-boundary-hardening
Commit: fix(eib): harden boundary semantics and upstream timeouts
```
