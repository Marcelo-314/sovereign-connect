# Package Review Notes — MU-022 v0.4.1-reviewed

The user-provided versioned execution documents were adopted as the new baseline because they are substantially more executable than v0.2.0.

## Adopted

- Top-level `eib/` standalone Spring Boot project.
- Exact `eib/pom.xml`.
- Exact EIB-owned DTO inventory.
- HMAC-SHA256 effective ref codec.
- TemporalAct observation/admission flow.
- `sourceTopologyVersion` and `temporalActs` coverage in EffectiveHabitatView.
- Provider-native ID suppression tests.
- 503 semantic-vs-transport disambiguation.
- 55-AC acceptance map.

## Corrected

`MockRestServiceServer` cannot reliably intercept an already-constructed production `RestClient` bean. The revised context and prompt require tests to bind the server to `RestClient.Builder` first, then build a local `RestClient` and local `RestClientEibScNorthboundClient`.

## Still intentionally out of scope

- Same runtime/context co-location with SC-C.
- Direct imports of `com.sovereign.connect.*`.
- Device/endpoint action admission.
- Discovery admission.
- Live updates / SSE.
- GraphQL / MCP / gRPC / ConnectRPC.
- Persistent effective-ref registry.
- Durable EIB audit/admission ledger.
