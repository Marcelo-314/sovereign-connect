# Informe de Revisión Mergeado — MU-022 Effective Interaction Boundary Seed

```text
Documento:    REVIEW-MU-022-EFFECTIVE-INTERACTION-BOUNDARY-SEED-MERGED
Versión:      v1.0.0-merged
Estado:       Final / Merged Review
Fecha:        2026-05-27
MU:           MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
Slot:         MU-022
Branch:       feat/sc-eib-mir-022-effective-interaction-boundary-seed
Baseline:     sovereign-connect-022.zip
Resultado:    NO L4 — bloqueado por routing incompatible con SC-C MU-021
```

---

## 1. Veredicto

**MU-022 no alcanza L4 en esta iteración.**

La implementación tiene una base arquitectónica correcta y varios invariantes del MIR están implementados y probados. Sin embargo, el `RestClientEibScNorthboundClient` consume rutas que no existen en el binding HTTP/OpenAPI de SC-C validado en MU-021. Por lo tanto, la suite puede pasar contra mocks, pero el consumo real contra SC-C fallaría en runtime con `404` o `400` antes de llegar a la lógica semántica de EIB.

Conclusión operacional:

```text
NO promover MU-022 a Validated L4 todavía.
Aplicar patch de routing + tests.
Re-ejecutar suite EIB y SC-C baseline.
Revisar nuevamente para L4.
```

---

## 2. Evidencia de ejecución observada

No se ejecutó una corrida fresca de Maven en el entorno de revisión porque no había `mvn` ni `mvnw` disponible. La evidencia incluida en el ZIP y en el informe revisado indica:

```text
EIB:
  7 suites
  12 tests
  0 failures
  0 errors
  0 skipped

SC-C baseline:
  27 suites
  240 tests
  0 failures
  0 errors
  0 skipped
```

La suite verde no es suficiente para L4 porque los tests del cliente HTTP mockean las rutas incorrectas, y los tests de servicios mockean directamente `EibScNorthboundClient`, sin verificar compatibilidad real con las rutas SC-C MU-021.

---

## 3. Lo que está correctamente implementado

### 3.1 Placement y separación de runtime

EIB está ubicado bajo:

```text
eib/
  pom.xml
  src/main/java/com/sovereign/eib/**
  src/test/java/com/sovereign/eib/**
```

Esto respeta la decisión crítica del MIR/CSA mergeado:

```text
Same repository is allowed.
Same SC-C runtime application context is not allowed.
EIB consumes SC-C only through HTTP/OpenAPI.
```

No se observaron imports `com.sovereign.connect.*` en `eib/src/main/java` durante la revisión estática. Esto preserva la separación entre EIB y el runtime SC-C.

### 3.2 DTOs espejo y no importación de DTOs SC-C

EIB define sus propios DTOs bajo:

```text
com.sovereign.eib.northbound.dto.*
```

Esto cumple la regla de no importar tipos Java de `core.northbound` y permite que EIB consuma el wire contract HTTP/OpenAPI como boundary físico.

### 3.3 Supresión de provider-native IDs

La implementación evita exponer `providerDeviceId` y `providerEndpointId` en `EffectiveDeviceView` y `EffectiveEndpointView`. Los tests reportados verifican que esos valores no aparecen en vistas ordinarias ni en modo diagnóstico. Este punto satisface la obligación más sensible de privacidad/abstracción de provider-native metadata.

### 3.4 Preservación del envelope SC-C

`EibCanonicalEnvelopeMapperTest` itera los 12 valores de `ScNorthboundStatus` y verifica preservación de:

```text
scNorthboundStatus
error.source
warning.source
```

Esto cubre la decisión PDR/SDD de no aplanar ni silenciar el envelope canónico SC-C dentro de EIB.

### 3.5 EffectiveHabitatView: `sourceTopologyVersion` y `temporalActs`

La implementación reportada cubre los dos ACs agregados durante la revisión del MIR:

```text
AC-022-014a — EffectiveHabitatView.sourceTopologyVersion
AC-022-018a — EffectiveHabitatView.temporalActs
```

El test citado en el informe valida que `sourceTopologyVersion = "42"` se deriva desde `topologyVersionValue`, y que `temporalActs` contiene un `effectiveTemporalActRef` con prefijo `eib.temporal.`.

### 3.6 Admisión temporal

La lógica de admisión temporal está bien orientada:

```text
SC-C ACCEPTED + NorthboundTemporalActView payload
  -> EIB ADMITTED
  -> effectiveRef generado con prefijo eib.temporal.
```

También está correctamente probado el caso de cancelación con ref no visible: EIB rechaza localmente y no llama a SC-C.

### 3.7 MockRestServiceServer: patrón corregido

El patrón de test para `MockRestServiceServer` fue corregido respecto del riesgo detectado en el package review: se construye el builder local, se hace `bindTo(builder)`, y recién después se construye el cliente usando ese builder interceptado. No depende de un bean productivo ya construido.

---

## 4. Bloqueantes

### BLOCKER-001 — `getTopologySnapshot` usa ruta inexistente

Implementación actual:

```java
.uri("/habitats/{habitatId}/topology/snapshot", habitatId)
```

Ruta real de SC-C MU-021:

```text
GET /sc/v1/habitats/{habitatId}/topology
```

Corrección requerida:

```java
.uri("/habitats/{habitatId}/topology", habitatId)
```

Impacto:

```text
EffectiveHabitatView falla en runtime contra SC-C real con HTTP 404.
```

### BLOCKER-002 — runtime state usa ruta genérica inexistente

Implementación actual:

```java
.uri("/habitats/{habitatId}/runtime-state/{subjectId}", habitatId, subjectId)
```

Rutas reales de SC-C MU-021:

```text
GET /sc/v1/habitats/{habitatId}/devices/{deviceId}/runtime-state
GET /sc/v1/habitats/{habitatId}/endpoints/{endpointId}/runtime-state
```

Corrección requerida:

```java
ScEnvelope<NorthboundRuntimeStateViewDto> getDeviceRuntimeState(String habitatId, String deviceId);
ScEnvelope<NorthboundRuntimeStateViewDto> getEndpointRuntimeState(String habitatId, String endpointId);
```

Con implementación:

```java
.uri("/habitats/{habitatId}/devices/{deviceId}/runtime-state", habitatId, deviceId)
.uri("/habitats/{habitatId}/endpoints/{endpointId}/runtime-state", habitatId, endpointId)
```

Impacto:

```text
Observación efectiva de runtime state falla en runtime contra SC-C real.
```

### BLOCKER-003 — `listTemporalActs` usa query params incorrectos

Implementación actual:

```text
GET /temporal-acts?status={status}&limit={limit}
```

Ruta real de SC-C MU-021:

```text
GET /temporal-acts?mode={mode}&maxResults={maxResults}
```

Corrección requerida:

```java
.queryParamIfPresent("mode", Optional.ofNullable(mode))
.queryParamIfPresent("maxResults", Optional.ofNullable(maxResults))
```

O, si se usa template manual:

```text
/habitats/{habitatId}/temporal-acts?mode={mode}&maxResults={maxResults}
```

Impacto:

```text
TemporalAct observation falla o retorna INVALID_REQUEST contra SC-C real.
```

### BLOCKER-004 — create Signal TemporalAct usa ruta inexistente

Implementación actual:

```java
.uri("/habitats/{habitatId}/temporal-acts/signal", habitatId)
```

Ruta real de SC-C MU-021:

```text
POST /sc/v1/habitats/{habitatId}/temporal-acts
```

Corrección requerida:

```java
.uri("/habitats/{habitatId}/temporal-acts", habitatId)
```

Impacto:

```text
Admisión de timers falla con HTTP 404 contra SC-C real.
```

### BLOCKER-005 — falta método explícito `getTopologyVersion(...)`

El MIR/SDD exige `NorthboundTopologyVersionViewDto`, no `String`, y la ruta real existe en MU-021:

```text
GET /sc/v1/habitats/{habitatId}/topology/version
```

En la implementación inspeccionada:

```text
NorthboundTopologyVersionViewDto existe.
EibScNorthboundClient no declara getTopologyVersion(...).
RestClientEibScNorthboundClient no implementa getTopologyVersion(...).
```

Corrección requerida:

```java
ScEnvelope<NorthboundTopologyVersionViewDto> getTopologyVersion(String habitatId);
```

Con implementación:

```java
.uri("/habitats/{habitatId}/topology/version", habitatId)
```

Disposición:

```text
Bloqueante si AC-022-010 se mantiene como aceptación obligatoria.
No es suficiente derivar sourceTopologyVersion desde /topology si el MIR exige cliente explícito para /topology/version.
```

### BLOCKER-006 — rutas de admisión EIB no devuelven `EibResponse<T>`

El SDD/MIR fijó que todas las rutas `/eib/v1` retornan `EibResponse<T>` como body HTTP. En la implementación inspeccionada:

```java
POST /temporal-acts/signal
POST /temporal-acts/{effectiveTemporalActRef}/cancel
```

retornan:

```java
ResponseEntity<InteractionAdmissionDecision>
```

Corrección requerida:

```java
ResponseEntity<EibResponse<InteractionAdmissionDecision>>
```

O directamente:

```java
EibResponse<InteractionAdmissionDecision>
```

si no se necesita controlar el HTTP status explícitamente.

Disposición:

```text
Bloqueante para AC-EIB-SDD-033 / MIR AC de uniformidad de envelope EIB.
```

---

## 5. Observaciones significativas no bloqueantes después del patch principal

### OBS-001 — Tests verdes no prueban compatibilidad real de rutas

Los tests actuales de `RestClientEibScNorthboundClient` deben actualizarse para esperar rutas reales de MU-021. En su estado actual, los mocks aceptan rutas incorrectas y por eso no detectan el desfasaje.

Tests mínimos a corregir/agregar:

```text
readsSuccessfulTopologySnapshotEnvelope
  espera /habitats/habitat.alpha/topology, no /topology/snapshot

listTemporalActs
  espera ?mode=ACTIVE&maxResults=50, no ?status=ACTIVE&limit=50

createSignalTemporalAct
  espera POST /temporal-acts, no /temporal-acts/signal

getDeviceRuntimeState
  espera /devices/{deviceId}/runtime-state

getEndpointRuntimeState
  espera /endpoints/{endpointId}/runtime-state

getTopologyVersion
  espera /topology/version
```

### OBS-002 — disambiguación 503 semántico vs transporte debe probarse explícitamente

La implementación tiene una base útil en `semanticOrUnavailable(...)`, pero la revisión recomienda testear explícitamente:

```text
HTTP 503 + body ScEnvelope(status=DEFERRED_SC_B_REQUIRED)
  -> respuesta semántica SC-C preservada

HTTP 503 sin body / body no JSON
  -> EibUpstreamUnavailableException / UPSTREAM_UNAVAILABLE
```

### OBS-003 — malformed JSON debe mapearse a upstream unavailable

Agregar test:

```text
HTTP 200 con body no parseable como ScEnvelope<T>
  -> UPSTREAM_UNAVAILABLE o error EIB equivalente, no éxito silencioso
```

### OBS-004 — `timeout-ms` configurado pero no aplicado

El archivo de configuración contiene `timeout-ms`, pero la implementación de `RestClient` no aplica un request factory con timeouts. No bloquea L4 si no está cubierto por AC explícito, pero debe registrarse como deuda o implementarse.

### OBS-005 — get by effective ref puede ocultar upstream failure como NOT_FOUND

Los endpoints:

```text
GET /devices/{effectiveDeviceRef}
GET /endpoints/{effectiveEndpointRef}
```

resuelven por list+match a partir de `effectiveView(...)`. Si el upstream falla y la lista queda vacía o payload null, existe riesgo de devolver `NOT_FOUND` en vez de propagar `UPSTREAM_UNAVAILABLE`. Debe testearse que una falla upstream no se convierta en invisibilidad contextual.

---

## 6. Cobertura AC mergeada

| Grupo | Estado | Nota |
|---|---|---|
| AC-022-001 a 006 — Placement y separación | PASS | `/eib` separado y sin imports SC-C observados |
| AC-022-007 a 013 — Cliente upstream y DTOs | FAIL / BLOQUEADO | DTOs correctos, rutas incorrectas |
| AC-022-014 / 014a — EffectiveHabitatView y sourceTopologyVersion | PASS con mocks | Requiere rutas corregidas para integración real |
| AC-022-018 / 018a — TemporalActs en EffectiveHabitatView | PASS con mocks | Requiere `mode/maxResults` real |
| AC-022-019 a 021 — ID gating | PASS | Incluye provider-native suppression |
| AC-022-022 a 027 — Envelope preservation | PASS | 12 statuses + source preservados |
| AC-022-028 a 032 — Effective refs | PASS | HMAC refs correctos |
| AC-022-033 a 038 — TemporalAct admission | PASS con mocks / BLOQUEADO por ruta | `/temporal-acts/signal` incorrecta |
| AC-022-039 a 050 — Negative scope | PASS | No WebFlux/gRPC/MCP/GraphQL/SSE observados |
| AC-022-051 a 055 — Evidence | CONDICIONAL | Requiere patch, rerun, commit y report actualizado |

---

## 7. Patch plan exacto

### PATCH-001 — corregir rutas upstream

En `RestClientEibScNorthboundClient`:

```text
/topology/snapshot -> /topology
/runtime-state/{subjectId} -> /devices/{deviceId}/runtime-state + /endpoints/{endpointId}/runtime-state
?status=&limit= -> ?mode=&maxResults=
/temporal-acts/signal -> /temporal-acts
```

### PATCH-002 — ampliar `EibScNorthboundClient`

Reemplazar método genérico:

```java
getRuntimeState(String habitatId, String subjectId)
```

por:

```java
getDeviceRuntimeState(String habitatId, String deviceId)
getEndpointRuntimeState(String habitatId, String endpointId)
```

Agregar:

```java
getTopologyVersion(String habitatId)
```

### PATCH-003 — ajustar servicios consumidores

Actualizar servicios/proyecciones que usen `getRuntimeState(...)` para llamar al método correcto según tipo de subject:

```text
device -> getDeviceRuntimeState
endpoint -> getEndpointRuntimeState
```

### PATCH-004 — uniformar API EIB con `EibResponse<T>`

Cambiar admisión temporal:

```text
ResponseEntity<InteractionAdmissionDecision>
```

por:

```text
ResponseEntity<EibResponse<InteractionAdmissionDecision>>
```

Preservar `CanonicalSubmissionTrace` dentro del envelope EIB.

### PATCH-005 — actualizar tests de cliente HTTP

Actualizar todos los `MockRestServiceServer.expect(...)` para rutas reales MU-021.

Agregar tests:

```text
getTopologyVersion_usesTopologyVersionRoute
getDeviceRuntimeState_usesDeviceRuntimeStateRoute
getEndpointRuntimeState_usesEndpointRuntimeStateRoute
listTemporalActs_usesModeAndMaxResults
createSignalTemporalAct_usesTemporalActsRootRoute
semantic503EnvelopeIsNotTransportFailure
http503WithoutScEnvelopeIsUpstreamUnavailable
malformedJsonMapsToUpstreamUnavailable
```

### PATCH-006 — actualizar implementation report

Registrar:

```text
- desvíos detectados;
- correcciones aplicadas;
- evidencia de test rerun;
- commit de patch;
- estado de ACs condicionados ahora PASS o todavía bloqueados.
```

---

## 8. Estado recomendado después del patch

Si las correcciones anteriores se aplican y pasan tests:

```text
EIB suite: expected >12 tests, 0 failures/errors/skips
SC-C baseline: 240 tests, 0 failures/errors/skips
```

Entonces MU-022 puede volver a revisión para L4.

---

## 9. Dictamen final

```text
MU-022 status: Implementation attempted / NOT L4.
Reason: upstream HTTP route mismatch against validated SC-C MU-021 binding.
Severity: blocking.
Governance patch required: no, if corrected within same MU-022 implementation iteration.
Execution patch required: yes.
Re-review required: yes.
```

El problema no es doctrinal: el placement, la separación de runtime, los DTOs, el envelope, los refs efectivos y la supresión de provider IDs están bien encaminados. El problema es de contract binding: el cliente EIB no está llamando exactamente al contrato HTTP/OpenAPI que MU-021 validó.

