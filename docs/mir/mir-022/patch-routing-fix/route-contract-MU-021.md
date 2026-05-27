# MU-021 HTTP/OpenAPI Route Contract — Subset Consumed by EIB

These are the exact upstream SC-C routes exposed by MU-021 and consumed by MU-022.

Base URL configured in EIB:

```text
sc.eib.northbound.base-url = http://localhost:8080/sc/v1
```

Therefore EIB client paths are relative to `/sc/v1`.

## Required routes

```text
GET  /habitats/{habitatId}/topology
GET  /habitats/{habitatId}/topology/version
GET  /habitats/{habitatId}/devices/{deviceId}/health
GET  /habitats/{habitatId}/endpoints/{endpointId}/health
GET  /habitats/{habitatId}/devices/{deviceId}/runtime-state
GET  /habitats/{habitatId}/endpoints/{endpointId}/runtime-state
GET  /habitats/{habitatId}/diagnostics
GET  /habitats/{habitatId}/temporal-acts?mode={mode}&maxResults={maxResults}
GET  /habitats/{habitatId}/temporal-acts/{temporalActId}
POST /habitats/{habitatId}/temporal-acts
POST /habitats/{habitatId}/temporal-acts/{temporalActId}/cancel
```

## Forbidden incorrect routes from the failed iteration

```text
GET  /habitats/{habitatId}/topology/snapshot
GET  /habitats/{habitatId}/runtime-state/{subjectId}
GET  /habitats/{habitatId}/temporal-acts?status={mode}&limit={maxResults}
POST /habitats/{habitatId}/temporal-acts/signal
```

These paths MUST NOT appear in production code or tests after the patch.
