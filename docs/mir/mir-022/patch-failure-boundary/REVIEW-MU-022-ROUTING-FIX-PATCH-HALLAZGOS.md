# Informe de Revisión — MU-022 Routing Fix Patch

```text
Documento:    REVIEW-MU-022-ROUTING-FIX-PATCH-HALLAZGOS
Versión:      v1.0.0
Estado:       Final / Patch Review
Fecha:        2026-05-27
MU:           MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
Slot:         MU-022
Branch:       feat/sc-eib-mir-022-effective-interaction-boundary-seed
Baseline:     sovereign-connect-022-patch.zip
Veredicto:    Routing patch accepted / MU-022 NOT L4 yet
```

---

## 1. Veredicto

El patch de routing corrige los seis bloqueantes identificados en la revisión mergeada anterior:

```text
BLOCKER-001 — /topology/snapshot -> /topology
BLOCKER-002 — runtime-state genérico -> rutas device/endpoint específicas
BLOCKER-003 — status/limit -> mode/maxResults
BLOCKER-004 — /temporal-acts/signal upstream -> /temporal-acts
BLOCKER-005 — getTopologyVersion(...) estructurado
BLOCKER-006 — admission routes devuelven EibResponse<InteractionAdmissionDecision>
```

Sin embargo, **MU-022 no debe promoverse todavía a L4**. La revisión detecta un nuevo bloqueo de boundary: las fallas upstream de SC-C no quedan convertidas uniformemente a `EibResponse<T>` en todos los endpoints EIB. Además, algunos endpoints pueden degradar un `UPSTREAM_UNAVAILABLE` a `NOT_FOUND`, ocultando el defecto real.

Estado recomendado:

```text
MU-022: implementation attempted + routing patch applied.
L4: pending failure-boundary patch.
```

---

## 2. Evidencia observada

No se ejecutó una corrida fresca de Maven en el entorno de revisión porque no están disponibles `mvn` ni `mvnw`.

El ZIP incluye reportes Surefire:

```text
EIB:
  Suites:   8
  Tests:    23
  Failures: 0
  Errors:   0
  Skipped:  0

SC-C baseline:
  Suites:   27
  Tests:    240
  Failures: 0
  Errors:   0
  Skipped:  0
```

La evidencia documental indica que el patch fue aplicado, pero el `implementation-report.md` todavía registra ausencia de commit de patch:

```text
Implementation commit(s): not created in this execution turn
Patch commit: not created in this execution turn
```

---

## 3. Validaciones positivas

### 3.1 Rutas upstream corregidas

`RestClientEibScNorthboundClient` usa ahora las rutas reales de MU-021:

```text
GET  /habitats/{habitatId}/topology
GET  /habitats/{habitatId}/topology/version
GET  /habitats/{habitatId}/devices/{deviceId}/runtime-state
GET  /habitats/{habitatId}/endpoints/{endpointId}/runtime-state
GET  /habitats/{habitatId}/temporal-acts?mode={mode}&maxResults={maxResults}
POST /habitats/{habitatId}/temporal-acts
POST /habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
```

No aparecen en el cliente upstream las rutas incorrectas anteriores:

```text
topology/snapshot
runtime-state/{subjectId}
status=
limit=
temporal-acts/signal
```

Nota: `temporal-acts/signal` puede seguir existiendo como ruta downstream de EIB, por ejemplo `/eib/v1/.../temporal-acts/signal`. Lo prohibido era usar `/temporal-acts/signal` como ruta upstream hacia SC-C.

### 3.2 `getTopologyVersion(...)` implementado

El port `EibScNorthboundClient` y `RestClientEibScNorthboundClient` implementan:

```java
ScEnvelope<NorthboundTopologyVersionViewDto> getTopologyVersion(String habitatId)
```

La ruta usada es:

```text
GET /habitats/{habitatId}/topology/version
```

El test incluido verifica `payload.value()`.

### 3.3 Runtime state separado por sujeto

El port ya no usa una ruta genérica `runtime-state/{subjectId}`. Expone:

```java
getDeviceRuntimeState(String habitatId, String deviceId)
getEndpointRuntimeState(String habitatId, String endpointId)
```

con rutas SC-C reales:

```text
/devices/{deviceId}/runtime-state
/endpoints/{endpointId}/runtime-state
```

### 3.4 `mode` / `maxResults`

`listTemporalActs(...)` usa:

```text
?mode={mode}&maxResults={maxResults}
```

El test `listTemporalActsUsesModeAndMaxResultsQueryParams` lo valida con URL exacta.

### 3.5 Create Signal TemporalAct upstream corregido

El cliente EIB postea a:

```text
POST /habitats/{habitatId}/temporal-acts
```

No a:

```text
POST /habitats/{habitatId}/temporal-acts/signal
```

### 3.6 Admission API usa `EibResponse<T>`

`EibTemporalActController.signal(...)` y `cancel(...)` devuelven:

```java
ResponseEntity<EibResponse<InteractionAdmissionDecision>>
```

Esto corrige el bloqueo anterior de bare `InteractionAdmissionDecision`.

### 3.7 Tests de cliente mejorados

Los tests del cliente ahora cubren:

```text
- HTTP 503 con ScEnvelope válido como respuesta semántica.
- HTTP 503 sin envelope válido como upstream unavailable.
- malformed JSON como upstream unavailable.
- connection failure como upstream unavailable.
```

Esto resuelve parte del boundary de transporte en el cliente HTTP.

---

## 4. Bloqueantes nuevos

### BLOCKER-PATCH-001 — `EibTemporalAdmissionService` no captura `EibUpstreamUnavailableException`

`admitTemporalSignalRequest(...)` llama:

```java
client.createSignalTemporalAct(...)
```

`admitTemporalCancellation(...)` llama:

```java
client.listTemporalActs(...)
client.cancelTemporalAct(...)
```

Ninguno de esos caminos captura `EibUpstreamUnavailableException`.

Consecuencia:

```text
Si SC-C está caído, devuelve body no JSON o hay fallo de red,
la excepción sube hasta el controller y Spring puede devolver error framework,
no EibResponse<InteractionAdmissionDecision>.
```

Esto viola la intención de:

```text
AC-022-041 — Network connection failure maps to EIB upstream-unavailable status/error.
AC-022-042 — Malformed upstream JSON maps to EIB upstream-unavailable status/error.
All /eib/v1 routes return EibResponse<T> as body.
```

Corrección requerida:

```text
Capturar EibUpstreamUnavailableException en EibTemporalAdmissionService
para create signal y cancel.
Devolver EibResponse<InteractionAdmissionDecision> con status UPSTREAM_UNAVAILABLE,
error.source = eib.northbound o equivalente.
```

---

### BLOCKER-PATCH-002 — `device(...)` y `endpoint(...)` pueden ocultar upstream failure como `NOT_FOUND`

`EibHabitatController.device(...)` llama a `devices(...)`. Si `effectiveView(...)` devuelve:

```text
EibResponse(status = UPSTREAM_UNAVAILABLE, payload = null)
```

entonces `devices(...)` transforma `payload == null` en `List.of()`. Luego `device(...)` busca en lista vacía y devuelve:

```text
NOT_FOUND / device not visible
```

Esto degrada una falla upstream a ausencia de entidad.

El mismo patrón existe para `endpoint(...)`.

Corrección requerida:

```text
Si la respuesta padre no es OK o si payload es null por error upstream/semántico,
propagar status/error/warnings originales.
No convertir a NOT_FOUND.
```

Resultado esperado:

```text
SC-C unavailable -> EIB UPSTREAM_UNAVAILABLE.
Entidad realmente ausente / invisible -> NOT_FOUND o REJECTED_NOT_VISIBLE, según ruta.
```

---

### BLOCKER-PATCH-003 — Falta cobertura endpoint/service para failures upstream

Los tests nuevos cubren el cliente HTTP, pero no prueban que los endpoints `/eib/v1` traduzcan esas fallas a `EibResponse<T>`.

Faltan tests para:

```text
POST /eib/v1/habitats/{habitatId}/temporal-acts/signal
POST /eib/v1/habitats/{habitatId}/temporal-acts/{effectiveTemporalActRef}/cancel
GET  /eib/v1/habitats/{habitatId}/devices/{effectiveDeviceRef}
GET  /eib/v1/habitats/{habitatId}/endpoints/{effectiveEndpointRef}
GET  /eib/v1/habitats/{habitatId}/temporal-acts
GET  /eib/v1/habitats/{habitatId}/temporal-acts/{effectiveTemporalActRef}
```

Mínimo requerido:

```text
Cada familia de endpoint debe tener al menos un test que demuestre que
EibUpstreamUnavailableException no escapa como error framework y se convierte
en EibResponse<T> con status UPSTREAM_UNAVAILABLE.
```

---

## 5. Observaciones no bloqueantes

### OBS-001 — `timeout-ms` configurado pero no aplicado

`EibScNorthboundClientProperties` define `timeoutMs`, pero el wiring del `RestClient` no lo aplica a un request factory o timeout configuration.

No lo considero blocker para el patch actual porque no era AC explícito, pero debe registrarse como deuda:

```text
DEBT-EIB-014 — EIB upstream timeout property is configured but not enforced by RestClient wiring.
```

### OBS-002 — Worktree sucio / commit pendiente

El ZIP muestra muchos cambios fuera de `eib/**` y `docs/mir/mir-022/**`, probablemente ruido histórico, line endings o IDE. Además, el implementation report todavía no registra commit de patch.

Antes de cierre L4 debe verificarse:

```bash
git status --short
git diff --name-status
git diff --stat
```

El cierre L4 no debería apoyarse en un ZIP con worktree masivamente dirty.

### OBS-003 — `EibResponse.status` en admission usa `ACCEPTED`

Los endpoints de admisión devuelven:

```text
EibResponse.status = ACCEPTED
payload.status = ADMITTED
```

Esto es tolerable para el patch actual, porque el objetivo bloqueante era envolver el body en `EibResponse<T>`. Pero conceptualmente mezcla el vocabulario SC-C con el vocabulario EIB.

Una versión posterior debería decidir si el wrapper externo debe usar vocabulario EIB (`ADMITTED`, `REJECTED`, `DEFERRED`) y dejar el estado SC-C solo en `CanonicalSubmissionTrace`.

---

## 6. Patch plan recomendado

### Required patch before L4 closure

```text
PATCH-2-001 — Add failure-boundary handling for temporal admission.
PATCH-2-002 — Add failure-boundary handling for temporal projection list/get.
PATCH-2-003 — Propagate non-OK/non-payload parent responses in single device/endpoint routes.
PATCH-2-004 — Add tests proving all /eib/v1 routes return EibResponse<T> on upstream unavailable/malformed upstream JSON.
PATCH-2-005 — Update implementation-report with patch commit and final clean status.
```

### Minimal implementation strategy

#### Option A — Service-level typed handling

```text
Catch EibUpstreamUnavailableException in:
  EibTemporalAdmissionService
  EibTemporalActProjectionService
  EibHabitatController.device/endpoint parent-response handling

Return EibResponse<T> with:
  status = UPSTREAM_UNAVAILABLE
  error.code = UPSTREAM_UNAVAILABLE
  error.source = eib.northbound
```

This option is preferred because each service/controller can preserve the correct generic response type.

#### Option B — Global `@RestControllerAdvice`

```text
Add EibExceptionHandler that converts EibUpstreamUnavailableException to EibResponse<?>.
```

This is useful as a last-line guard, but should not be the only handling if typed response semantics matter.

Recommended path:

```text
Use Option A for known service flows.
Optionally add Option B as a safety net.
```

---

## 7. Acceptance criteria impacted

```text
AC-022-041 — Network connection failure maps to EIB upstream-unavailable status/error.
Status: not fully satisfied at API/service boundary.

AC-022-042 — Malformed upstream JSON maps to EIB upstream-unavailable status/error.
Status: client-level covered; API/service boundary not fully covered.

AC-022-033..038 — TemporalAct admission/cancellation.
Status: positive paths covered; upstream unavailable path incomplete.

AC-022-051..055 — Evidence / implementation report / final status.
Status: pending commit and clean branch evidence.
```

---

## 8. Final recommendation

```text
Routing patch: accepted.
MU-022 L4: not yet.
Reason: failure-boundary behavior incomplete across EIB API/service layer.
Next step: focused failure-boundary patch, then re-review.
```

No governance patch is required if this is corrected within the same MU-022 implementation iteration. This is an execution correction, not a doctrinal change.
