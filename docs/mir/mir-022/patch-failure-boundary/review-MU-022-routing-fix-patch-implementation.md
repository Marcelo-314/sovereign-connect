# Informe de Revisión — MU-022 Routing Fix Patch

```text
Documento:    REVIEW-MU-022-ROUTING-FIX-PATCH
Versión:      v1.0.0-review
Estado:       Final / Patch Review
Fecha:        2026-05-27
MU:           MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
Slot:         MU-022
Baseline:     sovereign-connect-022-patch.zip
Resultado:    Patch routing aplicado; NO promover todavía a L4 por manejo incompleto de failures upstream en endpoints EIB
```

---

## 1. Veredicto

El patch corrige los seis bloqueantes del informe mergeado anterior:

```text
BLOCKER-001 — /topology/snapshot -> /topology
BLOCKER-002 — runtime-state genérico -> rutas device/endpoint específicas
BLOCKER-003 — status/limit -> mode/maxResults
BLOCKER-004 — /temporal-acts/signal upstream -> /temporal-acts
BLOCKER-005 — getTopologyVersion(...) estructurado
BLOCKER-006 — admission routes devuelven EibResponse<InteractionAdmissionDecision>
```

Sin embargo, **no recomiendo cerrar MU-022 como L4 todavía**. La revisión detecta un nuevo bloqueo de comportamiento: algunos endpoints EIB no convierten fallas upstream en `EibResponse<T>` product-safe, y otros pueden ocultar errores upstream como `NOT_FOUND`.

Estado recomendado:

```text
MU-022: implementation attempted + routing patch applied.
L4: pending failure-boundary patch.
```

---

## 2. Evidencia observada

No pude ejecutar una corrida fresca porque el entorno de revisión no tiene `mvn` ni `mvnw`.

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

La evidencia de `docs/mir/mir-022/implementation-report.md` registra que el patch fue aplicado y que los comandos esperados pasaron, pero también registra que no hay commit de patch creado aún.

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

Grep sobre `eib/src/main/java/com/sovereign/eib/northbound` no muestra las rutas incorrectas anteriores:

```text
topology/snapshot
runtime-state/{subjectId}
status=
limit=
temporal-acts/signal
```

Nota: `temporal-acts/signal` sigue apareciendo en tests de API EIB como ruta downstream `/eib/v1/.../temporal-acts/signal`, lo cual es correcto. El path prohibido era el upstream hacia SC-C.

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

con rutas SC-C reales.

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

No a `/temporal-acts/signal`.

### 3.6 Admission API ya usa `EibResponse<T>`

`EibTemporalActController.signal(...)` y `cancel(...)` devuelven:

```java
ResponseEntity<EibResponse<InteractionAdmissionDecision>>
```

Esto corrige el bloqueo anterior de bare `InteractionAdmissionDecision`.

---

## 4. Bloqueante nuevo — failure-boundary incompleto

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
Si SC-C está caído, devuelve body no JSON, o hay fallo de red,
la excepción sube hasta el controller y Spring devuelve error framework,
no EibResponse<InteractionAdmissionDecision>.
```

Esto viola la intención de:

```text
AC-022-041 — Network connection failure maps to EIB upstream-unavailable status/error.
AC-022-042 — Malformed upstream JSON maps to EIB upstream-unavailable status/error.
All /eib/v1 routes return EibResponse<T> as body.
```

### BLOCKER-PATCH-002 — `device(...)` y `endpoint(...)` pueden ocultar upstream failure como `NOT_FOUND`

`EibHabitatController.device(...)` llama `devices(...)`. Si `effectiveView(...)` devolvió por ejemplo:

```text
EibResponse(status = UPSTREAM_UNAVAILABLE, payload = null)
```

entonces `devices(...)` transforma `payload == null` en `List.of()`. Después `device(...)` busca en lista vacía y devuelve:

```text
NOT_FOUND / device not visible
```

Esto oculta una falla upstream como ausencia de entidad. El mismo patrón existe para `endpoint(...)`.

Corrección requerida:

```text
Si la respuesta padre no es OK o si payload es null por error upstream/semántico,
propagar el status/error/warnings originales, no convertir a NOT_FOUND.
```

### BLOCKER-PATCH-003 — falta cobertura de controller/service para failures upstream

Los tests nuevos cubren muy bien el cliente HTTP:

```text
http503WithValidSemanticEnvelopeReturnsEnvelope
http503WithoutValidEnvelopeThrowsUpstreamUnavailable
malformedJsonThrowsUpstreamUnavailable
connectionFailureThrowsUpstreamUnavailable
```

Pero falta probar el comportamiento de los endpoints EIB cuando esas excepciones llegan desde el cliente:

```text
POST /eib/v1/.../temporal-acts/signal
POST /eib/v1/.../temporal-acts/{ref}/cancel
GET  /eib/v1/.../devices/{ref}
GET  /eib/v1/.../endpoints/{ref}
GET  /eib/v1/.../temporal-acts
GET  /eib/v1/.../temporal-acts/{ref}
```

La cobertura actual prueba que el cliente lanza `EibUpstreamUnavailableException`, pero no que el API EIB la convierte siempre en `EibResponse<T>`.

---

## 5. Observaciones no bloqueantes

### OBS-001 — `timeout-ms` está configurado pero no aplicado

`EibScNorthboundClientProperties` define `timeoutMs`, pero `EibConfiguration` no lo aplica al `RestClient` request factory.

Esto no bloquea el patch de routing ni L4 si no estaba en AC explícito, pero conviene registrarlo como deuda:

```text
DEBT-EIB-014 — EIB upstream timeout property is configured but not enforced by RestClient wiring.
```

### OBS-002 — Worktree muy sucio / no hay commit de patch

El ZIP muestra muchos cambios fuera de `eib/**` y `docs/mir/mir-022/**`, probablemente ruido histórico/line endings/IDE. Además, `implementation-report.md` todavía registra:

```text
Implementation commit(s): not created in this execution turn
Patch commit: not created in this execution turn
```

Antes del cierre L4 debe existir un commit acotado o, al menos, un estado local limpio verificable:

```bash
git status --short
git diff --name-status
git diff --stat
```

El cierre L4 no debería apoyarse en un ZIP con worktree masivamente dirty.

### OBS-003 — `EibResponse.status` en admission usa `ACCEPTED`

Los endpoints de admisión devuelven `EibResponse.status = ACCEPTED` y `payload.status = ADMITTED`.

Esto es tolerable para el patch porque el objetivo bloqueante era envolver el body en `EibResponse<T>`, pero conceptualmente mezcla el vocabulario SC-C con el vocabulario EIB. Una versión posterior debería decidir si el wrapper externo debe usar `ADMITTED`, `REJECTED`, `DEFERRED`, etc., y dejar el estado SC-C solo en `CanonicalSubmissionTrace`.

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

Option A — local service-level handling:

```text
Catch EibUpstreamUnavailableException in:
  EibTemporalAdmissionService
  EibTemporalActProjectionService
  EibHabitatController.device/endpoint parent-response handling

Return EibResponse with status UPSTREAM_UNAVAILABLE and source eib.northbound.
```

Option B — global `@RestControllerAdvice`:

```text
Add EibExceptionHandler that converts EibUpstreamUnavailableException to EibResponse<?>.
```

Option A is safer for AC traceability because each service can return the correct generic type.
Option B is useful as a last-line guard, but should not be the only handling if typed response semantics matter.

---

## 7. Final recommendation

```text
Routing patch: accepted.
MU-022 L4: not yet.
Reason: failure-boundary behavior incomplete across EIB API/service layer.
Next step: focused failure-boundary patch, then re-review.
```
