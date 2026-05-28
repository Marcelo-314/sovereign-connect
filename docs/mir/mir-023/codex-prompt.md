# Codex Prompt — MU-023 EIB Hardening

You are implementing MU-023 for Sovereign Connect.

Read in this exact order before writing any code:

```text
docs/mir/mir-023/context.md           ← primary implementation guide (all exact code here)
docs/mir/mir-023/acceptance-map.md    ← maps every AC to required evidence
docs/mir/mir-023/MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001.md
docs/mir/mir-023/code-surface-audit.md
```

---

## Hard rule

```text
Do not modify any file under:
  src/main/java/com/sovereign/connect/**
  src/test/java/com/sovereign/connect/**

All changes are confined to eib/** and docs/mir/mir-023/.
```

---

## Step 1 — Timeout factory (ACTION-EIB-H-001)

Patch `eib/src/main/java/com/sovereign/eib/config/EibConfiguration.java`.

Replace only `eibRestClientBuilder`. Use the **exact code from context.md §6.1**.

Critical: apply the `SimpleClientHttpRequestFactory` to the builder **before**
`build()`. Do not build the RestClient inside this bean — `eibRestClient` does
that from the builder. Preserve the builder-bean pattern.

Required imports: `java.time.Duration`,
`org.springframework.http.client.SimpleClientHttpRequestFactory`.

**STOP-1:** `cd eib && mvn compile -q` must succeed.
Verify: `grep -r "com.sovereign.connect" src/main/java/ | wc -l` returns `0`.

---

## Step 2 — Client-level timeout test (AC-023-008)

Create `eib/src/test/java/com/sovereign/eib/EibScNorthboundClientTimeoutTest.java`.

Use the **exact code from context.md §6.2**.

Key points:
- Use `192.0.2.1` as the base URL — this is TEST-NET (RFC 5737), non-routable,
  no external network dependency.
- Set 1ms connect/read timeout — forces timeout normalization quickly.
- Do NOT use `MockRestServiceServer` for this test — it cannot simulate timeouts.
- Do NOT use any live public IP or localhost:unused-port — they are flaky.

---

## Step 3 — Route regression gap (ACTION-EIB-H-002)

Add four missing route tests to `EibScNorthboundClientTest` or a new
`EibScNorthboundClientRouteRegressionTest`.

Use the **pattern from context.md §7** and match the existing
`MockRestServiceServer` setup already in `EibScNorthboundClientTest`.

Missing routes:
```text
GET /devices/{deviceId}/health
GET /endpoints/{endpointId}/health
GET /diagnostics
GET /temporal-acts/{temporalActId}
```

Preserve all 11 existing tests in `EibScNorthboundClientTest` unchanged.

---

## Step 4 — CANCELLED → COMPLETED (ACTION-EIB-H-003)

Patch `mapScStatusToAdmissionStatus` in
`eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java`.

Use the **exact one-line change from context.md §8**.

No other changes to this method or this file in this step.

---

## Step 5 — Full admission vocabulary mapper (ACTION-EIB-H-004)

**Step 5a:** Create
`eib/src/main/java/com/sovereign/eib/api/EibHttpResponseMapper.java`
using the **exact code from context.md §9.1**.

The class is a final utility with a static method — no Spring annotations.

**Step 5b:** Patch `EibTemporalActController.admissionResponse()` to delegate
to `EibHttpResponseMapper.admissionResponse(decision)` as shown in
context.md §9.2. The rest of the controller is unchanged.

**Step 5c:** Create
`eib/src/test/java/com/sovereign/eib/EibAdmissionResponseMapperTest.java`
using the **exact code from context.md §9.3**.

The test has 11 methods — one per vocabulary entry plus the composite
non-ACCEPTED regression test.

**Step 5d:** Patch stale existing controller tests in `EibApiControllerTest` using
context.md §9.4.

Required updates:
- `signalAdmissionReturnsEibResponseWrapper` must expect top-level `ADMITTED`, not `ACCEPTED`.
- `cancelAdmissionReturnsEibResponseWrapperWithNullEffectiveRef` must stub `COMPLETED` and expect HTTP 200 / top-level `COMPLETED`.

Do not leave old expectations that treat every non-failure admission as `ACCEPTED`.

**STOP-2:** `cd eib && mvn test -Dtest="EibAdmissionResponseMapperTest" -q`
must pass with 11 tests, 0 failures.

---

## Step 6 — DEBT-EIB-015 closure (ACTION-EIB-H-005)

Patch `admitTemporalCancellation` in
`eib/src/main/java/com/sovereign/eib/service/EibTemporalAdmissionService.java`.

Insert the `isSuccess` guard using the **exact code from context.md §10**.

The guard goes immediately after the `listTemporalActs` try/catch block and
before `codec.resolveTemporalRef(...)`. Do not change the try/catch, do not
change the `resolveTemporalRef` call or the `orElseGet` path.

Create `eib/src/test/java/com/sovereign/eib/EibTemporalAdmissionServiceHardeningTest.java`
using the **exact code from context.md §10** (the 3-test class).

---

## Step 7 — Diagnostic/admin truth table (ACTION-EIB-H-006)

Add two new methods to
`eib/src/test/java/com/sovereign/eib/EibEffectiveViewMapperTest.java`
using the **exact code from context.md §11**.

Do not remove or modify the 2 existing tests. After this step the class has
4 tests covering all four truth-table rows.

---

## Step 8 — API-level timeout test (AC-023-008a)

Add one test to `eib/src/test/java/com/sovereign/eib/EibApiControllerTest.java`
using the **exact code from context.md §6.3**.

The test stubs `admissionService` to return `FAILED_UPSTREAM_UNAVAILABLE` and
asserts `HTTP 503` + `$.status = UPSTREAM_UNAVAILABLE`. Do not make the
controller catch `EibUpstreamUnavailableException`; service-level normalization
precedes API mapping in the current architecture. The `EibApiControllerTest`
already has `mockMvc` and `admissionService` mock wired — add the method directly.

---

## Step 9 — Run full suite (STOP-4)

```bash
cd eib && mvn test -q
```

Expected: **≥ 56 tests, 0 failures, 0 errors, 0 skipped**.

Manually verify:
```text
EibScNorthboundClientTimeoutTest      2 (new)
EibScNorthboundClientTest             15 (11 existing + 4 new routes)
  or existing 11 + new RouteRegression class with 4
EibAdmissionResponseMapperTest        11 (new)
EibTemporalAdmissionServiceHardeningTest  3 (new)
EibEffectiveViewMapperTest            4 (2 existing + 2 new)
EibApiControllerTest                  5+ (4 existing + 1 new AC-023-008a)
All other existing suites             unchanged
```

If count is below 56: check which new test class was not created.
If any failure: do not proceed to Step 10 until green.

---

## Step 10 — SC-C baseline (STOP-5)

```bash
cd ..
mvn test -q
```

Expected: **240 tests, 0 failures** (SC-C unchanged).

If SC-C tests are not rerun because only EIB was changed, record this
explicitly in the implementation report with rationale.

---

## Step 11 — Update implementation report

Update `docs/mir/mir-023/implementation-report.md`.

Record:

```text
- changed files (production and test)
- implementation commit
- evidence commit
- EIB test count and result
- SC-C baseline result or not-rerun rationale
- closure of DEBT-EIB-014 (if and only if STOP-2 timeout tests pass)
- closure of DEBT-EIB-015 (if and only if STOP-3 hardening tests pass)
- retained debts table
```

Suggested commit message:

```bash
git commit -m "fix(eib): harden boundary semantics and upstream timeouts"
```

---

## Hard stop conditions

Stop and report rather than improvising if:

```text
- Any SC-C source file would need to change.
- MockRestServiceServer is the only available mechanism for timeout tests
  (use 192.0.2.1 TEST-NET pattern from context.md instead).
- The EibHttpResponseMapper switch requires a type other than String for
  decision.status() — use String comparison as shown in context.md.
- Adding the isSuccess guard requires changing the return type of
  admitTemporalCancellation — it must not; the guard returns the same
  InteractionAdmissionDecision type as all other paths.
```
