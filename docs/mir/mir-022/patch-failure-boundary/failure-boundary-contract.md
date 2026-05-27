# Failure Boundary Contract — MU-022 Patch

## 1. Status vocabulary

For this patch, EIB introduces or uses the following status at the EIB boundary:

```text
UPSTREAM_UNAVAILABLE
```

This status means:

```text
EIB could not obtain a valid ScEnvelope<T> from SC-C over HTTP/OpenAPI.
```

It does not mean:

```text
SC-C returned NOT_FOUND.
SC-C returned UNSUPPORTED_PROFILE.
SC-C returned DEFERRED_SC_B_REQUIRED inside a valid ScEnvelope.
The entity is not visible.
The action was semantically rejected by SC-C.
```

## 2. Semantic vs transport failure

| Situation | Classification | EIB response |
|---|---|---|
| HTTP 503 + valid `ScEnvelope(status=DEFERRED_SC_B_REQUIRED)` | SC-C semantic response | preserve `DEFERRED_SC_B_REQUIRED` |
| connection refused | upstream transport failure | `UPSTREAM_UNAVAILABLE` |
| timeout | upstream transport failure | `UPSTREAM_UNAVAILABLE` |
| HTTP 503 with empty/non-JSON body | upstream transport/wire failure | `UPSTREAM_UNAVAILABLE` |
| malformed JSON | upstream wire failure | `UPSTREAM_UNAVAILABLE` |
| valid `ScEnvelope(status=NOT_FOUND)` | SC-C semantic response | preserve `NOT_FOUND` / map to not-visible only where contextually appropriate |

## 3. Single entity rule

A single-entity route may return `NOT_FOUND` only after a successful parent fetch.

Correct:

```text
parent response = OK + payload list
entity absent in list
=> NOT_FOUND
```

Incorrect:

```text
parent response = UPSTREAM_UNAVAILABLE
payload null/empty
=> NOT_FOUND
```

## 4. Admission rule

Temporal admission has two layers:

```text
EibResponse<InteractionAdmissionDecision>
InteractionAdmissionDecision
```

When upstream is unavailable:

```text
EibResponse.status = UPSTREAM_UNAVAILABLE
InteractionAdmissionDecision.status = FAILED_UPSTREAM_UNAVAILABLE
CanonicalSubmissionTrace.scNorthboundStatus = UPSTREAM_UNAVAILABLE
```

No `effectiveRef` is generated.
No SC-C canonical success is fabricated.
