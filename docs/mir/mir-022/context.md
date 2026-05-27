# MU-022 Execution Context — Effective Interaction Boundary Initial Implementation Slice

```text
Document ID:  CONTEXT-SOV-SC-EIB-MIR-022
Version:      v0.4.1-reviewed
Status:       Execution package context
MU:           MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
Slot:         MU-022
Branch:       feat/sc-eib-mir-022-effective-interaction-boundary-seed
```

---

## 0. Purpose

This context constrains the implementation of MU-022. It is not a replacement for
the MIR, SDD or CSA — it operationalizes them with exact signatures, wiring patterns,
test code and stop conditions.

Authoritative inputs in read order:

```text
docs/mir/mir-022/MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001.md
docs/mir/mir-022/code-surface-audit.md
docs/mir/mir-022/acceptance-map.md
```

---

## 1. Non-negotiable architectural rule

```text
Same repository: allowed.
Same SC-C runtime application context: NOT allowed.
EIB consumes SC-C only through HTTP/OpenAPI.
```

Forbidden unless a governance patch is explicitly made before L4 closure:

```text
- Adding EIB classes under src/main/java/com/sovereign/connect/**
- Registering EIB beans in SovereignConnectApplication
- Importing com.sovereign.connect.core.* from EIB
- Importing com.sovereign.connect.adapter.* from EIB
- Direct Java calls to ScCoreNorthboundFacade or any SC-C service
```

---

## 2. Project structure and Maven coordinates

```text
eib/
  pom.xml
  src/main/java/com/sovereign/eib/EibApplication.java
  src/main/java/com/sovereign/eib/api/
  src/main/java/com/sovereign/eib/northbound/          ← RestClient + mirror DTOs
  src/main/java/com/sovereign/eib/northbound/dto/
  src/main/java/com/sovereign/eib/projection/
  src/main/java/com/sovereign/eib/admission/
  src/main/java/com/sovereign/eib/config/
  src/main/resources/application.yml
  src/test/java/com/sovereign/eib/
  src/test/resources/application-test.yml
```

Exact `eib/pom.xml` — complete file, paste verbatim:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.sovereign.eib</groupId>
    <artifactId>sovereign-eib</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>sovereign-eib</name>
    <description>Effective Interaction Boundary</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

No dependency on the SC-C project. No WebFlux, WireMock, gRPC, MCP,
GraphQL, WebSocket, NATS.

---

## 3. Configuration

`eib/src/main/resources/application.yml`:

```yaml
server:
  port: 8081

spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false

sc:
  eib:
    northbound:
      base-url: http://localhost:8080/sc/v1
      timeout-ms: 2000
    ref-codec:
      secret: change-me-in-production
    diagnostic-admin:
      enabled: false
```

`eib/src/test/resources/application-test.yml`:

```yaml
sc:
  eib:
    northbound:
      base-url: http://sc-c-mock/sc/v1
      timeout-ms: 500
    ref-codec:
      secret: test-secret-32-chars-for-hmac-256
    diagnostic-admin:
      enabled: true
```

Properties classes — exact records used in tests:

```java
@ConfigurationProperties(prefix = "sc.eib.northbound")
public record EibScNorthboundClientProperties(String baseUrl, long timeoutMs) {}

@ConfigurationProperties(prefix = "sc.eib.ref-codec")
public record EibRefCodecProperties(String secret) {}

@ConfigurationProperties(prefix = "sc.eib.diagnostic-admin")
public record EibDiagnosticAdminProperties(boolean enabled) {}
```

---

## 4. Upstream wire DTOs — exact field declarations

All records in `com.sovereign.eib.northbound.dto`. These are EIB-owned copies of
the SC-C wire shape. They are distinct Java types — do not import SC-C classes.

Jackson deserializes these from the HTTP response body using standard camelCase
field names.

```java
// Envelope — status is String, not the SC-C enum
record ScEnvelope<T>(String status, T payload, List<ScWarningDto> warnings, ScErrorDto error) {}
record ScErrorDto(String code, String message, String source) {}
record ScWarningDto(String code, String message, String source) {}

// Topology
record NorthboundTopologySnapshotDto(
    String habitatId,
    String topologyVersionValue,   // ← source for EffectiveHabitatView.sourceTopologyVersion
    String topologyVersionScope,
    List<NorthboundRoomViewDto> rooms,
    List<NorthboundZoneViewDto> zones,
    List<NorthboundDeviceViewDto> devices,
    List<NorthboundEndpointViewDto> endpoints,
    Instant readAt
) {}

record NorthboundTopologyVersionViewDto(
    String habitatId,
    String value,       // ← the version string; NOT a bare String response
    String scopeType,
    String scopeId
) {}

record NorthboundRoomViewDto(
    String roomId, String roomName,
    List<String> zoneIds, List<String> deviceIds, List<String> endpointIds
) {}

record NorthboundZoneViewDto(
    String zoneId, String zoneName, String roomId,
    List<String> deviceIds, List<String> endpointIds
) {}

record NorthboundDeviceViewDto(
    String deviceId, String alias, String displayName,
    String roomId, String zoneId, String kind, String provider,
    List<String> endpointIds,
    List<NorthboundCapabilityViewDto> capabilities,
    String providerDeviceId   // ← deserialize this field; MUST NOT pass to ordinary product views
) {}

record NorthboundEndpointViewDto(
    String endpointId, String deviceId, String alias, String displayName,
    String kind, String roomId, String zoneId,
    List<NorthboundCapabilityViewDto> capabilities,
    String providerEndpointId  // ← deserialize this field; MUST NOT pass to ordinary product views
) {}

record NorthboundCapabilityViewDto(String capabilityId, String name, String kind) {}

// Health / runtime
record NorthboundDeviceHealthViewDto(
    String deviceId, String status, String source,
    int endpointCount, Instant readAt, List<ScWarningDto> warnings
) {}

record NorthboundEndpointHealthViewDto(
    String endpointId, String status, Instant lastSeenAt, String details
) {}

record NorthboundRuntimeStateViewDto(
    String habitatId, String subjectId, String subjectType,
    Map<String, Object> state, Instant readAt
) {}

// Diagnostics
record NorthboundDiagnosticsViewDto(
    String habitatId, String topologyVersion,
    NorthboundTemporalRuntimeStatusViewDto temporalEngineStatus,
    NorthboundMigrationReadinessViewDto migrationReadiness,
    Instant readAt, List<ScWarningDto> warnings
) {}

record NorthboundTemporalRuntimeStatusViewDto(
    String habitatId, String engineStatus, boolean isReady,
    Instant lastPollAt, Instant lastSuccessfulPollAt,
    long firedTotal, long misfiredTotal, long cancelledTotal,
    long failedTotal, long skippedTotal
) {}

record NorthboundMigrationReadinessViewDto(String status, String source, String message) {}

// Temporal
record NorthboundTemporalActViewDto(
    String temporalActId, String habitatId, String status,
    Instant dueAt, String payloadKind, String label, String signalKind,
    String notificationTargetRef, String createdByRef,
    Instant createdAt, Instant updatedAt, Instant firedAt,
    Instant terminalAt, String terminalReason
) {}

// Upstream request bodies
record NorthboundCreateSignalTemporalActRequestDto(
    Instant dueAt, String label, String signalKind,
    String notificationTargetRef, String createdByRef, String idempotencyKey
) {}

// Cancel body: temporalActId is NOT a field here — it goes in the URL path
record NorthboundCancelTemporalActRequestDto(
    String requestedByRef, String idempotencyKey, String reason
) {}
```

---

## 5. RestClient exact wiring pattern

`EibScNorthboundClient` port and `RestClientEibScNorthboundClient` adapter.

### 5.1 Bean wiring — important: expose RestClient.Builder as a named bean

The `RestClient.Builder` must be exposed as a separate `@Bean` so tests can build
a mock-bound client after `MockRestServiceServer.bindTo(builder)` has been applied.
`MockRestServiceServer.bindTo()` accepts `RestClient.Builder`, not a built
`RestClient` instance.

Important test constraint:

```text
Do not rely on an autowired production `RestClientEibScNorthboundClient` in
MockRestServiceServer tests. That bean may have been constructed before the
server bound itself to the builder. In tests, bind the server to the builder first,
then build a local RestClient and local RestClientEibScNorthboundClient.
```

```java
@Configuration
@EnableConfigurationProperties({
    EibScNorthboundClientProperties.class,
    EibRefCodecProperties.class,
    EibDiagnosticAdminProperties.class
})
public class EibConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // Exposed as a bean so MockRestServiceServer can bind to it in tests.
    @Bean
    public RestClient.Builder eibRestClientBuilder(EibScNorthboundClientProperties props) {
        return RestClient.builder().baseUrl(props.baseUrl());
    }

    // Production runtime client. Tests that use MockRestServiceServer MUST build
    // a local client after binding the server to the builder; see §13.
    @Bean
    public RestClient eibRestClient(RestClient.Builder eibRestClientBuilder) {
        return eibRestClientBuilder.build();
    }

    @Bean
    public EibScNorthboundClient eibScNorthboundClient(
            RestClient eibRestClient,
            ObjectMapper objectMapper,
            EibScNorthboundClientProperties props) {
        return new RestClientEibScNorthboundClient(eibRestClient, objectMapper, props);
    }

    @Bean
    public EibEffectiveRefCodec eibEffectiveRefCodec(EibRefCodecProperties props) {
        return new EibEffectiveRefCodec(props.secret());
    }
}
```

### 5.2 Typed RestClient call pattern for generic ScEnvelope<T>

Jackson cannot deserialize `ScEnvelope<T>` without explicit type information.
Use `ParameterizedTypeReference`:

```java
// For a single-entity response:
ScEnvelope<NorthboundTopologySnapshotDto> getTopologySnapshot(String habitatId) {
    return eibRestClient.get()
        .uri("/habitats/{id}/topology", habitatId)
        .retrieve()
        .body(new ParameterizedTypeReference<ScEnvelope<NorthboundTopologySnapshotDto>>() {});
}

// For a list response with optional query params:
ScEnvelope<List<NorthboundTemporalActViewDto>> listTemporalActs(
        String habitatId, String mode, Integer maxResults) {
    return eibRestClient.get()
        .uri(b -> b.path("/habitats/{id}/temporal-acts")
                   .queryParamIfPresent("mode", Optional.ofNullable(mode))
                   .queryParamIfPresent("maxResults", Optional.ofNullable(maxResults))
                   .build(habitatId))
        .retrieve()
        .body(new ParameterizedTypeReference<ScEnvelope<List<NorthboundTemporalActViewDto>>>() {});
}
```

### 5.3 Network failure vs semantic SC-C 503 disambiguation

SC-C returns HTTP 503 for `DEFERRED_SC_B_REQUIRED`. The exact disambiguation pattern:

```java
ScEnvelope<NorthboundTemporalActViewDto> createSignalTemporalAct(
        String habitatId, NorthboundCreateSignalTemporalActRequestDto request) {
    try {
        return eibRestClient.post()
            .uri("/habitats/{id}/temporal-acts", habitatId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, resp) -> {
                // Try to read body as ScEnvelope before throwing
                try (InputStream body = resp.getBody()) {
                    ScEnvelope<NorthboundTemporalActViewDto> envelope =
                        objectMapper.readValue(body,
                            new TypeReference<ScEnvelope<NorthboundTemporalActViewDto>>() {});
                    if (envelope != null && envelope.status() != null) {
                        // Valid SC-C semantic response (e.g. DEFERRED_SC_B_REQUIRED)
                        throw new EibSemanticScCException(envelope);
                    }
                } catch (IOException ignored) {}
                // No valid envelope → transport failure
                throw new EibUpstreamUnavailableException(
                    "HTTP " + resp.getStatusCode().value());
            })
            .body(new ParameterizedTypeReference<ScEnvelope<NorthboundTemporalActViewDto>>() {});
    } catch (EibSemanticScCException e) {
        return e.getEnvelope();
    } catch (RestClientException e) {
        throw new EibUpstreamUnavailableException(e.getMessage(), e);
    }
}
```

Apply the same `onStatus` pattern to all upstream calls. Never catch
`RestClientException` silently — always wrap as `EibUpstreamUnavailableException`.

Also create:
```java
class EibUpstreamUnavailableException extends RuntimeException { /* standard constructors */ }
class EibSemanticScCException extends RuntimeException {
    private final ScEnvelope<?> envelope;
    public EibSemanticScCException(ScEnvelope<?> envelope) {
        super("SC-C semantic: " + envelope.status());
        this.envelope = envelope;
    }
    @SuppressWarnings("unchecked")
    public <T> ScEnvelope<T> getEnvelope() { return (ScEnvelope<T>) envelope; }
}
```

---

## 6. EibEffectiveRefCodec — exact implementation

```java
public final class EibEffectiveRefCodec {

    private final byte[] secret;

    public EibEffectiveRefCodec(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateRef(String type, String habitatId, String canonicalId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] hash = mac.doFinal(
                (habitatId + ":" + type + ":" + canonicalId)
                    .getBytes(StandardCharsets.UTF_8));
            return type + "." + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(hash)
                .substring(0, 22);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("EIB ref codec failure", e);
        }
    }

    // Resolve by rebuilding index: fetch visible entities, generate their refs, match.
    // Returns canonical ID if found. This is the list+match strategy accepted for
    // the initial implementation slice (see DEBT-EIB-011).
    public Optional<String> resolveTemporalRef(
            String suppliedRef, String habitatId,
            List<NorthboundTemporalActViewDto> visible) {
        return visible.stream()
            .filter(act -> generateRef("eib.temporal", habitatId, act.temporalActId())
                .equals(suppliedRef))
            .map(NorthboundTemporalActViewDto::temporalActId)
            .findFirst();
    }
}
```

Ref families and their inputs:

```text
eib.room.<token>      type="eib.room",     canonicalId = NorthboundRoomViewDto.roomId
eib.zone.<token>      type="eib.zone",     canonicalId = NorthboundZoneViewDto.zoneId
eib.device.<token>    type="eib.device",   canonicalId = NorthboundDeviceViewDto.deviceId
eib.endpoint.<token>  type="eib.endpoint", canonicalId = NorthboundEndpointViewDto.endpointId
eib.temporal.<token>  type="eib.temporal", canonicalId = NorthboundTemporalActViewDto.temporalActId
```

---

## 7. Effective view data contracts — exact record shapes

All in `com.sovereign.eib.domain` or `com.sovereign.eib.projection`.

```java
// Response wrapper — all /eib/v1 routes return EibResponse<T> as HTTP body
record EibResponse<T>(
    String status,
    T payload,
    List<EibWarning> warnings,
    EibError error,
    CanonicalTraceSummary canonicalTrace  // null for read routes
) {}

record EibError(String code, String message, String source) {}
record EibWarning(String code, String message, String source) {}

// Habitat view
record EffectiveHabitatView(
    String habitatRef,
    String sourceTopologyVersion,  // from NorthboundTopologySnapshotDto.topologyVersionValue; MUST NOT be null
    Instant generatedAt,
    List<EffectiveRoomView> rooms,
    List<EffectiveZoneView> zones,
    List<EffectiveDeviceView> devices,
    List<EffectiveEndpointView> endpoints,
    List<EffectiveTemporalActView> temporalActs,  // from GET /temporal-acts mode=ACTIVE; non-empty when SC-C returns acts
    EffectiveDiagnosticsSummary diagnostics,
    List<EibWarning> warnings
) {}

record EffectiveRoomView(
    String effectiveRoomRef,
    String displayName,
    List<String> effectiveDeviceRefs,
    String visibilityStatus,
    List<EibWarning> warnings
) {}

record EffectiveZoneView(
    String effectiveZoneRef,
    String displayName,
    List<String> effectiveDeviceRefs,
    String visibilityStatus,
    List<EibWarning> warnings
) {}

record EffectiveDeviceView(
    String effectiveDeviceRef,
    String canonicalDeviceId,       // null in ordinary mode; set only in diagnostic/admin
    String displayName,
    String roomRef,
    String zoneRef,
    List<EffectiveEndpointView> endpoints,
    List<EffectiveCapabilityAffordance> capabilities,
    String healthSummary,           // "UNKNOWN" in habitat view (no per-device health call); set from individual route
    String runtimeSummary,          // "UNKNOWN" in habitat view; set from individual route
    String visibilityStatus,
    String operabilityStatus,
    List<EibWarning> warnings
) {}

record EffectiveEndpointView(
    String effectiveEndpointRef,
    String canonicalEndpointId,     // null in ordinary mode
    String displayName,
    List<EffectiveCapabilityAffordance> capabilities,
    String healthSummary,           // "UNKNOWN" in habitat view; set from individual route
    String runtimeSummary,          // "UNKNOWN" in habitat view; set from individual route
    String visibilityStatus,
    String operabilityStatus,
    List<EibWarning> warnings
) {}

record EffectiveCapabilityAffordance(
    String affordanceRef,
    String capabilityKind,
    String displayLabel,
    String visibilityStatus,
    String operabilityStatus,
    boolean requiresConfirmation,
    String deferredReason
) {}

record EffectiveTemporalActView(
    String effectiveTemporalActRef,
    String canonicalTemporalActId,  // null in ordinary mode
    String label,
    String status,                  // pass through NorthboundTemporalActViewDto.status
    Instant dueAt,
    String payloadKind,
    String notificationContextSummary,
    String visibilityStatus,
    String operabilityStatus,
    boolean canCancel,
    String terminalSummary,
    List<EibWarning> warnings
) {}

record EffectiveDiagnosticsView(
    Instant observedAt,
    String sourceTopologyVersion,
    String northboundReachability,
    String migrationReadiness,
    List<String> unsupportedProfiles,
    List<EibWarning> warnings
) {}

record EffectiveDiagnosticsSummary(
    String northboundReachability,
    String migrationReadiness
) {}

// Admission types
record InteractionAdmissionDecision(
    String status,                     // ADMITTED, REJECTED_*, DEFERRED_*, FAILED_*, UPSTREAM_UNAVAILABLE
    String interactionAdmissionId,
    String effectiveRef,               // non-null only after accepted Signal TemporalAct create; null for cancel and rejections
    CanonicalSubmissionTrace canonicalTrace,
    List<EibWarning> warnings,
    EibError error
) {}

record CanonicalSubmissionTrace(
    String clientRequestRef,
    String interactionAdmissionId,
    String canonicalRequestRef,
    Instant submittedAt,
    String scNorthboundStatus,         // preserved SC-C status string — never null when SC-C was reached
    List<ScWarningDto> warnings,
    ScErrorDto error
) {}

record EibTemporalSignalAdmissionRequest(
    Instant dueAt,
    String label,
    String signalKind,
    String notificationTargetRef,
    String idempotencyKey,
    String clientRequestRef
) {}

record EibTemporalCancellationAdmissionRequest(
    String idempotencyKey,
    String reason,
    String clientRequestRef
) {}
```

---

## 8. EibRequestContext — exact shape

```java
record EibRequestContext(
    String contextRef,
    String actorRef,
    String surfaceRef,
    String locale,
    boolean diagnosticModeRequested,
    boolean diagnosticAdminAuthorized,
    Instant requestedAt,
    String clientRequestRef
) {
    public static EibRequestContext fromHeaders(HttpServletRequest req, boolean adminEnabled) {
        boolean requested = "true".equalsIgnoreCase(
            req.getHeader("X-SC-Diagnostic-Mode"));
        return new EibRequestContext(
            req.getHeader("X-SC-Context-Ref"),
            req.getHeader("X-SC-Actor-Ref"),
            req.getHeader("X-SC-Surface-Ref"),
            req.getHeader("X-SC-Locale"),
            requested,
            requested && adminEnabled,  // both conditions required
            Instant.now(),
            req.getHeader("X-SC-Client-Request-Ref")
        );
    }

    public boolean isOrdinaryMode() {
        return !diagnosticAdminAuthorized;
    }
}
```

---

## 9. EffectiveHabitatView construction algorithm

Two upstream calls are required. A third (health/runtime per entity) is
intentionally deferred to individual device/endpoint routes to avoid N+1 calls.

```text
CALL 1 (mandatory): GET /habitats/{habitatId}/topology
  → NorthboundTopologySnapshotDto
  → provides: rooms, zones, devices, endpoints, topologyVersionValue, readAt

CALL 2 (degradable): GET /habitats/{habitatId}/temporal-acts?mode=ACTIVE
  → ScEnvelope<List<NorthboundTemporalActViewDto>>
  → provides: temporalActs for EffectiveHabitatView.temporalActs

Note on health/runtime in the habitat view:
  EffectiveDeviceView.healthSummary and runtimeSummary are set to "UNKNOWN"
  in the habitat view. Individual device/endpoint routes call the health and
  runtime-state routes per entity. Do not make N device-health calls inside
  buildEffectiveHabitatView.
```

```java
EffectiveHabitatView buildEffectiveHabitatView(String habitatId, EibRequestContext ctx) {

    // Step 1: topology snapshot (mandatory)
    ScEnvelope<NorthboundTopologySnapshotDto> snapshotEnv =
        client.getTopologySnapshot(habitatId);
    // If snapshotEnv.status() is not "OK", return error to caller:
    //   EibResponse with status=UPSTREAM_ERROR or UPSTREAM_UNAVAILABLE

    NorthboundTopologySnapshotDto snapshot = snapshotEnv.payload();
    String sourceTopologyVersion = snapshot.topologyVersionValue(); // MUST be non-null

    // Step 2: temporal acts (degradable — failure → PARTIAL with warning)
    List<EffectiveTemporalActView> effectiveTemporalActs = List.of();
    List<EibWarning> warnings = new ArrayList<>();
    try {
        ScEnvelope<List<NorthboundTemporalActViewDto>> temporalEnv =
            client.listTemporalActs(habitatId, "ACTIVE", null);
        if ("OK".equals(temporalEnv.status()) && temporalEnv.payload() != null) {
            effectiveTemporalActs = temporalEnv.payload().stream()
                .map(act -> toEffectiveTemporalActView(act, habitatId, ctx))
                .toList();
        } else if (temporalEnv.status() != null) {
            warnings.add(new EibWarning("TEMPORAL_ACTS_SC_ERROR",
                "SC-C status for temporal-acts: " + temporalEnv.status(), "eib.projection"));
        }
    } catch (EibUpstreamUnavailableException e) {
        warnings.add(new EibWarning("TEMPORAL_ACTS_UNAVAILABLE",
            "Temporal acts upstream unavailable: " + e.getMessage(), "eib.projection"));
    }

    // Step 3: build effective rooms, zones, devices, endpoints from snapshot
    // healthSummary and runtimeSummary default to "UNKNOWN" (no per-entity call here)
    List<EffectiveRoomView> rooms = snapshot.rooms().stream()
        .map(r -> toEffectiveRoomView(r, habitatId, ctx)).toList();
    List<EffectiveZoneView> zones = snapshot.zones().stream()
        .map(z -> toEffectiveZoneView(z, habitatId, ctx)).toList();
    List<EffectiveDeviceView> devices = snapshot.devices().stream()
        .map(d -> toEffectiveDeviceView(d, habitatId, ctx)).toList();
    List<EffectiveEndpointView> endpoints = snapshot.endpoints().stream()
        .map(e -> toEffectiveEndpointView(e, habitatId, ctx)).toList();

    return new EffectiveHabitatView(
        habitatId,
        sourceTopologyVersion,   // from snapshot.topologyVersionValue()
        Instant.now(),
        rooms, zones, devices, endpoints,
        effectiveTemporalActs,
        new EffectiveDiagnosticsSummary("REACHABLE", "UNKNOWN"),
        warnings
    );
}
```

---

## 10. TemporalAct admission — exact mapping

### admitTemporalSignalRequest

```java
InteractionAdmissionDecision admitTemporalSignalRequest(
        String habitatId,
        EibTemporalSignalAdmissionRequest req,
        EibRequestContext ctx) {

    String admissionId = UUID.randomUUID().toString();

    NorthboundCreateSignalTemporalActRequestDto upstream =
        new NorthboundCreateSignalTemporalActRequestDto(
            req.dueAt(),
            req.label(),
            req.signalKind(),
            req.notificationTargetRef(),
            "eib-service",              // ← EIB service identifier; NEVER userId/sessionId/ctx.actorRef()
            req.idempotencyKey()
        );

    ScEnvelope<NorthboundTemporalActViewDto> scResponse =
        client.createSignalTemporalAct(habitatId, upstream);

    CanonicalSubmissionTrace trace = new CanonicalSubmissionTrace(
        req.clientRequestRef(), admissionId, null,
        Instant.now(), scResponse.status(),
        scResponse.warnings(), scResponse.error()
    );

    // AC-022-036: if ACCEPTED with payload, generate effectiveTemporalActRef
    String effectiveRef = null;
    if ("ACCEPTED".equals(scResponse.status()) && scResponse.payload() != null) {
        effectiveRef = refCodec.generateRef(
            "eib.temporal", habitatId, scResponse.payload().temporalActId());
    }

    return new InteractionAdmissionDecision(
        mapScStatusToAdmissionStatus(scResponse.status()),
        admissionId, effectiveRef, trace, List.of(), null);
}
```

### admitTemporalCancellation — list+match pattern

```java
InteractionAdmissionDecision admitTemporalCancellation(
        String habitatId,
        String effectiveTemporalActRef,
        EibTemporalCancellationAdmissionRequest req,
        EibRequestContext ctx) {

    String admissionId = UUID.randomUUID().toString();

    // Resolve effective ref to canonical ID by list+match (DEBT-EIB-011)
    ScEnvelope<List<NorthboundTemporalActViewDto>> listEnv;
    try {
        listEnv = client.listTemporalActs(habitatId, null, null);
    } catch (EibUpstreamUnavailableException e) {
        return new InteractionAdmissionDecision(
            "UPSTREAM_UNAVAILABLE", admissionId, null, null,
            List.of(new EibWarning("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.admission")),
            new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.admission"));
    }

    if (listEnv.payload() == null || !"OK".equals(listEnv.status())) {
        return new InteractionAdmissionDecision(
            "REJECTED_NOT_VISIBLE", admissionId, null, null, List.of(),
            new EibError("REJECTED_NOT_VISIBLE",
                "could not resolve temporal acts: " + listEnv.status(), "eib.admission"));
    }

    Optional<String> canonicalId = refCodec.resolveTemporalRef(
        effectiveTemporalActRef, habitatId, listEnv.payload());

    if (canonicalId.isEmpty()) {
        return new InteractionAdmissionDecision(
            "REJECTED_NOT_VISIBLE", admissionId, null, null, List.of(),
            new EibError("REJECTED_NOT_VISIBLE",
                "effective temporal act ref not found or not visible", "eib.admission"));
    }

    // Cancel body: temporalActId in URL path, NOT in body
    NorthboundCancelTemporalActRequestDto cancelBody =
        new NorthboundCancelTemporalActRequestDto(
            "eib-service",        // requestedByRef — NEVER personal identity
            req.idempotencyKey(),
            req.reason()
        );

    ScEnvelope<NorthboundTemporalActViewDto> scResponse =
        client.cancelTemporalAct(habitatId, canonicalId.get(), cancelBody);

    CanonicalSubmissionTrace trace = new CanonicalSubmissionTrace(
        req.clientRequestRef(), admissionId, canonicalId.get(),
        Instant.now(), scResponse.status(),
        scResponse.warnings(), scResponse.error()
    );

    // effectiveRef is null for cancel — cancel does not create a new canonical resource
    return new InteractionAdmissionDecision(
        mapScStatusToAdmissionStatus(scResponse.status()),
        admissionId, null, trace, List.of(), null);
}
```

### mapScStatusToAdmissionStatus

```java
private String mapScStatusToAdmissionStatus(String scStatus) {
    return switch (scStatus) {
        case "OK", "ACCEPTED", "CANCELLED" -> "ADMITTED";
        case "NOT_FOUND" -> "REJECTED_NOT_VISIBLE";
        case "INVALID_REQUEST", "INVALID_CANONICAL_ID", "VALIDATION_ERROR"
            -> "REJECTED_INVALID_REQUEST";
        case "UNSUPPORTED_PROFILE" -> "DEFERRED_UNSUPPORTED_PROFILE";
        case "DEFERRED_SC_B_REQUIRED" -> "DEFERRED_SC_B_REQUIRED";
        case "UNKNOWN_PENDING_NORMALIZATION" -> "DEFERRED_PENDING_NORMALIZATION";
        case "INTERNAL_ERROR" -> "FAILED_CANONICAL_SUBMISSION";
        default -> "FAILED_CANONICAL_SUBMISSION";
    };
}
```

---

## 11. Canonical ID gating — the exact invariant

The mapper from upstream Dto to product-facing effective view enforces:

```java
// Canonical IDs: null in ordinary mode, set only in diagnostic/admin
String resolveCanonicalDeviceId(NorthboundDeviceViewDto dto, EibRequestContext ctx) {
    return ctx.diagnosticAdminAuthorized() ? dto.deviceId() : null;
}

String resolveCanonicalEndpointId(NorthboundEndpointViewDto dto, EibRequestContext ctx) {
    return ctx.diagnosticAdminAuthorized() ? dto.endpointId() : null;
}

String resolveCanonicalTemporalActId(NorthboundTemporalActViewDto dto, EibRequestContext ctx) {
    return ctx.diagnosticAdminAuthorized() ? dto.temporalActId() : null;
}

// providerDeviceId / providerEndpointId: NEVER copied to any effective view field, any mode.
// These fields exist in the upstream DTO for deserialization only.
// Do not add them to EffectiveDeviceView or EffectiveEndpointView.
```

Required tests — verify at JSON serialization level, not just field level:

```java
@Test
void ordinaryDeviceViewDoesNotContainProviderDeviceId() throws Exception {
    NorthboundDeviceViewDto dto = new NorthboundDeviceViewDto(
        "device.tuya.light-1", "alias", "Light", "room.1", null, "LIGHT", "tuya",
        List.of(), List.of(), "tuya-native-id-12345"
    );
    EibRequestContext ordinaryCtx = new EibRequestContext(
        null, null, null, null, false, false, Instant.now(), null);
    EffectiveDeviceView view = mapper.toDeviceView(dto, "habitat-001", ordinaryCtx);

    String json = objectMapper.writeValueAsString(view);
    assertThat(json).doesNotContain("tuya-native-id-12345");
    assertThat(json).doesNotContain("providerDeviceId");
    assertThat(view.canonicalDeviceId()).isNull();
}

@Test
void diagnosticDeviceViewContainsCanonicalDeviceIdButNotProviderDeviceId() throws Exception {
    EibRequestContext adminCtx = new EibRequestContext(
        null, null, null, null, true, true, Instant.now(), null);
    EffectiveDeviceView view = mapper.toDeviceView(dto, "habitat-001", adminCtx);
    assertThat(view.canonicalDeviceId()).isEqualTo("device.tuya.light-1");
    // Provider ID must still be absent even in admin mode
    assertThat(objectMapper.writeValueAsString(view)).doesNotContain("tuya-native-id-12345");
}
```

---

## 12. Envelope preservation tests — exact assertions

```java
@Test
void preservesAllTwelveScCStatuses() {
    List<String> all12 = List.of(
        "OK", "CREATED", "ACCEPTED", "CANCELLED",
        "NOT_FOUND", "INVALID_REQUEST", "INVALID_CANONICAL_ID",
        "VALIDATION_ERROR", "UNSUPPORTED_PROFILE", "DEFERRED_SC_B_REQUIRED",
        "UNKNOWN_PENDING_NORMALIZATION", "INTERNAL_ERROR"
    );
    for (String status : all12) {
        ScEnvelope<String> env = new ScEnvelope<>(status, null, List.of(), null);
        assertThat(mapper.extractScStatus(env)).isEqualTo(status);
    }
}

@Test
void preservesErrorSource() {
    ScErrorDto err = new ScErrorDto("CODE", "msg", "northbound.validation");
    ScEnvelope<Void> env = new ScEnvelope<>("INVALID_REQUEST", null, List.of(), err);
    CanonicalSubmissionTrace trace = mapper.toTrace("ref", "adm", env);
    assertThat(trace.error().source()).isEqualTo("northbound.validation");
}

@Test
void preservesWarningSources() {
    ScWarningDto warn = new ScWarningDto("W", "msg", "northbound.query");
    ScEnvelope<String> env = new ScEnvelope<>("OK", null, List.of(warn), null);
    CanonicalSubmissionTrace trace = mapper.toTrace("ref", "adm", env);
    assertThat(trace.warnings()).hasSize(1);
    assertThat(trace.warnings().get(0).source()).isEqualTo("northbound.query");
}

@Test
void unsupportedProfileIsNotSuccess() {
    assertThat(mapper.isSuccess(new ScEnvelope<>("UNSUPPORTED_PROFILE", null, List.of(), null))).isFalse();
}

@Test
void deferredScBRequiredIsNotSuccess() {
    assertThat(mapper.isSuccess(new ScEnvelope<>("DEFERRED_SC_B_REQUIRED", null, List.of(), null))).isFalse();
}

@Test
void unknownPendingNormalizationIsNotSuccess() {
    assertThat(mapper.isSuccess(new ScEnvelope<>("UNKNOWN_PENDING_NORMALIZATION", null, List.of(), null))).isFalse();
}

@Test
void internalErrorIsNotSuccess() {
    assertThat(mapper.isSuccess(new ScEnvelope<>("INTERNAL_ERROR", null, List.of(), null))).isFalse();
}

@Test
void canonicalSubmissionTracePreservesScStatus() {
    ScEnvelope<NorthboundTemporalActViewDto> env =
        new ScEnvelope<>("ACCEPTED", temporalActDto, List.of(), null);
    CanonicalSubmissionTrace trace = mapper.toTrace("client-ref", "admission-id", env);
    assertThat(trace.scNorthboundStatus()).isEqualTo("ACCEPTED");
}
```

---

## 13. MockRestServiceServer test patterns

The builder pattern is required. `MockRestServiceServer.bindTo()` accepts
`RestClient.Builder` (Spring 6.1), not a built `RestClient` instance.

Critical rule:

```text
In `EibScNorthboundClientTest`, do not autowire the production
`RestClientEibScNorthboundClient` bean and expect MockRestServiceServer to
intercept it. That bean may have been constructed before the mock server bound
itself to the builder.

Instead:
  1. inject the named `eibRestClientBuilder` bean;
  2. bind MockRestServiceServer to that builder in `@BeforeEach`;
  3. build a local RestClient from the now-bound builder;
  4. construct a local RestClientEibScNorthboundClient for the test.
```

```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "sc.eib.northbound.base-url=http://sc-c-test/sc/v1",
        "sc.eib.northbound.timeout-ms=500",
        "sc.eib.ref-codec.secret=test-secret-32-chars-for-hmac-256",
        "sc.eib.diagnostic-admin.enabled=true"
    }
)
class EibScNorthboundClientTest {

    @Autowired
    @Qualifier("eibRestClientBuilder")
    private RestClient.Builder eibRestClientBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EibScNorthboundClientProperties props;

    private MockRestServiceServer server;
    private RestClientEibScNorthboundClient client;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.bindTo(eibRestClientBuilder).build();
        RestClient mockBoundRestClient = eibRestClientBuilder.build();
        client = new RestClientEibScNorthboundClient(
            mockBoundRestClient, objectMapper, props);
    }

    @Test
    void connectionFailureMapsToUpstreamUnavailable() {
        server.expect(requestTo(containsString("/topology")))
            .andRespond(withException(new java.io.IOException("connection refused")));

        assertThatThrownBy(() -> client.getTopologySnapshot("habitat-001"))
            .isInstanceOf(EibUpstreamUnavailableException.class);
        server.verify();
    }

    @Test
    void http503WithValidEnvelopeMapsToSemanticScCResponse() throws Exception {
        String semanticBody = objectMapper.writeValueAsString(
            new ScEnvelope<>("DEFERRED_SC_B_REQUIRED", null,
                List.of(), new ScErrorDto("SC_B_REQUIRED", "SC-B required", "northbound.deferred"))
        );
        server.expect(requestTo(containsString("/temporal-acts")))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(semanticBody));

        ScEnvelope<NorthboundTemporalActViewDto> result =
            client.createSignalTemporalAct("habitat-001", anyCreateRequest());

        assertThat(result.status()).isEqualTo("DEFERRED_SC_B_REQUIRED");
        assertThat(result.error().source()).isEqualTo("northbound.deferred");
        server.verify();
    }

    @Test
    void http503WithNoBodyMapsToUpstreamUnavailable() {
        server.expect(requestTo(containsString("/temporal-acts")))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.createSignalTemporalAct("habitat-001", anyCreateRequest()))
            .isInstanceOf(EibUpstreamUnavailableException.class);
        server.verify();
    }

    @Test
    void malformedJsonMapsToUpstreamUnavailable() {
        server.expect(requestTo(containsString("/topology")))
            .andRespond(withBody("not-json").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getTopologySnapshot("habitat-001"))
            .isInstanceOf(EibUpstreamUnavailableException.class);
        server.verify();
    }

    @Test
    void topologySnapshotDeserializesTopologyVersionValue() throws Exception {
        NorthboundTopologySnapshotDto expected = new NorthboundTopologySnapshotDto(
            "habitat-001", "7", "HABITAT",
            List.of(), List.of(), List.of(), List.of(), Instant.now());
        server.expect(requestTo(containsString("/topology")))
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(
                    new ScEnvelope<>("OK", expected, List.of(), null)),
                MediaType.APPLICATION_JSON));

        ScEnvelope<NorthboundTopologySnapshotDto> result =
            client.getTopologySnapshot("habitat-001");

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.payload().topologyVersionValue()).isEqualTo("7");
        server.verify();
    }
}
```

---

## 14. Architecture test pattern

```java
class EibArchitectureTest {

    private final Path eibMain = Path.of("src/main/java");

    @Test
    void eibDoesNotImportScCCoreOrAdapterPackages() throws IOException {
        List<String> forbidden = List.of(
            "com.sovereign.connect.core.",
            "com.sovereign.connect.adapter.",
            "com.sovereign.connect.config.",
            "ScCoreNorthboundFacade",
            "DefaultScCoreNorthboundFacade",
            "JdbcTemplate",
            "javax.sql.DataSource",
            "org.flywaydb"
        );
        try (Stream<Path> paths = Files.walk(eibMain)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p, forbidden))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("EIB must not import SC-C internals")
                .isEmpty();
        }
    }

    @Test
    void eibDoesNotIntroduceForbiddenTechnologies() throws IOException {
        List<String> forbidden = List.of(
            "Flux<", "Mono<", "webflux",
            "io.grpc", "connectrpc", "graphql",
            "io.nats", "JetStream", "SseEmitter"
        );
        try (Stream<Path> paths = Files.walk(eibMain)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p, forbidden))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("Forbidden technologies must not appear in EIB source")
                .isEmpty();
        }
    }

    private boolean containsAny(Path path, List<String> needles) {
        try {
            String content = Files.readString(path);
            return needles.stream().anyMatch(content::contains);
        } catch (IOException e) { return false; }
    }
}
```

---

## 15. Stop conditions

```text
STOP-1: After creating eib/pom.xml, EibApplication, config classes and DTOs —
        run: cd eib && mvn compile
        Expected: BUILD SUCCESS.
        Verify: grep -r "com.sovereign.connect" src/main/ → zero results.

STOP-2: After implementing EibEffectiveRefCodec —
        run: cd eib && mvn test -Dtest=EibEffectiveRefCodecTest
        Expected:
          ✓ same input → same ref (determinism)
          ✓ ref does not contain canonical ID
          ✓ different canonical IDs → different refs
          ✓ resolveTemporalRef finds the correct canonical ID in a list
          ✓ resolveTemporalRef returns empty for an unknown ref

STOP-3: After implementing RestClientEibScNorthboundClient —
        run: cd eib && mvn test -Dtest=EibScNorthboundClientTest
        Expected: test builds a local mock-bound RestClientEibScNorthboundClient
        after MockRestServiceServer.bindTo(eibRestClientBuilder).
          ✓ connection refused → EibUpstreamUnavailableException
          ✓ HTTP 503 with valid ScEnvelope(DEFERRED_SC_B_REQUIRED) → status preserved, not exception
          ✓ HTTP 503 with no body → EibUpstreamUnavailableException
          ✓ malformed JSON → EibUpstreamUnavailableException
          ✓ topology snapshot success → topologyVersionValue is "7" (not null)

STOP-4: After implementing projection, admission and controllers —
        run: cd eib && mvn test
        Expected: all EIB tests pass, 0 failures, 0 errors.
        Critical assertions to verify manually:
          ✓ EffectiveHabitatView.sourceTopologyVersion is non-null and equals
            the topologyVersionValue from the topology snapshot
          ✓ EffectiveHabitatView.temporalActs is non-empty when
            the upstream temporal-acts mock returns acts
          ✓ serialized ordinary EffectiveDeviceView does not contain
            providerDeviceId value or "providerDeviceId" key
          ✓ InteractionAdmissionDecision.effectiveRef is non-null and starts
            with "eib.temporal." after Signal TemporalAct create with ACCEPTED response
          ✓ admitTemporalCancellation returns effectiveRef = null

STOP-FINAL: Record exact test count in implementation-report.md.
        run: cd .. && mvn test
        Expected: SC-C baseline still 240 tests, 0 failures (unchanged).
```

---

## 16. Files to create summary

```text
CREATE (eib/ project — do not modify any file under src/main/java/com/sovereign/connect/**):

  eib/pom.xml                                          ← exact content from §2
  eib/src/main/resources/application.yml               ← §3
  eib/src/test/resources/application-test.yml          ← §3

  eib/src/main/java/com/sovereign/eib/EibApplication.java
  eib/src/main/java/com/sovereign/eib/config/EibConfiguration.java          ← §5.1
  eib/src/main/java/com/sovereign/eib/config/EibScNorthboundClientProperties.java
  eib/src/main/java/com/sovereign/eib/config/EibRefCodecProperties.java
  eib/src/main/java/com/sovereign/eib/config/EibDiagnosticAdminProperties.java

  eib/src/main/java/com/sovereign/eib/northbound/EibScNorthboundClient.java
  eib/src/main/java/com/sovereign/eib/northbound/RestClientEibScNorthboundClient.java
  eib/src/main/java/com/sovereign/eib/northbound/EibUpstreamUnavailableException.java
  eib/src/main/java/com/sovereign/eib/northbound/EibSemanticScCException.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/ScEnvelope.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/ScErrorDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/ScWarningDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundTopologySnapshotDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundTopologyVersionViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundRoomViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundZoneViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundDeviceViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundEndpointViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundCapabilityViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundDeviceHealthViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundEndpointHealthViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundRuntimeStateViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundTemporalActViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundDiagnosticsViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundTemporalRuntimeStatusViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundMigrationReadinessViewDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundCreateSignalTemporalActRequestDto.java
  eib/src/main/java/com/sovereign/eib/northbound/dto/NorthboundCancelTemporalActRequestDto.java

  eib/src/main/java/com/sovereign/eib/domain/EibResponse.java
  eib/src/main/java/com/sovereign/eib/domain/EibError.java
  eib/src/main/java/com/sovereign/eib/domain/EibWarning.java
  eib/src/main/java/com/sovereign/eib/domain/EffectiveHabitatView.java
  eib/src/main/java/com/sovereign/eib/domain/EffectiveRoomView.java
  eib/src/main/java/com/sovereign/eib/domain/EffectiveZoneView.java
  eib/src/main/java/com/sovereign/eib/domain/EffectiveDeviceView.java
  eib/src/main/java/com/sovereign/eib/domain/EffectiveEndpointView.java
  eib/src/main/java/com/sovereign/eib/domain/EffectiveCapabilityAffordance.java
  eib/src/main/java/com/sovereign/eib/domain/EffectiveTemporalActView.java
  eib/src/main/java/com/sovereign/eib/domain/EffectiveDiagnosticsView.java
  eib/src/main/java/com/sovereign/eib/domain/EffectiveDiagnosticsSummary.java
  eib/src/main/java/com/sovereign/eib/domain/InteractionAdmissionDecision.java
  eib/src/main/java/com/sovereign/eib/domain/CanonicalSubmissionTrace.java
  eib/src/main/java/com/sovereign/eib/domain/EibTemporalSignalAdmissionRequest.java
  eib/src/main/java/com/sovereign/eib/domain/EibTemporalCancellationAdmissionRequest.java
  eib/src/main/java/com/sovereign/eib/domain/EibRequestContext.java

  eib/src/main/java/com/sovereign/eib/projection/EibEffectiveRefCodec.java
  eib/src/main/java/com/sovereign/eib/projection/EibEffectiveViewMapper.java
  eib/src/main/java/com/sovereign/eib/projection/EibEffectiveViewService.java
  eib/src/main/java/com/sovereign/eib/projection/EibTemporalActProjectionService.java
  eib/src/main/java/com/sovereign/eib/projection/EibCanonicalEnvelopeMapper.java

  eib/src/main/java/com/sovereign/eib/admission/EibTemporalAdmissionService.java

  eib/src/main/java/com/sovereign/eib/api/EibHabitatController.java
  eib/src/main/java/com/sovereign/eib/api/EibTemporalActController.java

  eib/src/test/java/com/sovereign/eib/EibEffectiveRefCodecTest.java
  eib/src/test/java/com/sovereign/eib/EibCanonicalEnvelopeMapperTest.java
  eib/src/test/java/com/sovereign/eib/EibEffectiveViewMapperTest.java
  eib/src/test/java/com/sovereign/eib/EibEffectiveViewServiceTest.java
  eib/src/test/java/com/sovereign/eib/EibTemporalActProjectionServiceTest.java
  eib/src/test/java/com/sovereign/eib/EibTemporalAdmissionServiceTest.java
  eib/src/test/java/com/sovereign/eib/EibScNorthboundClientTest.java
  eib/src/test/java/com/sovereign/eib/EibApiControllerTest.java
  eib/src/test/java/com/sovereign/eib/EibArchitectureTest.java

DO NOT MODIFY anything under:
  src/main/java/com/sovereign/connect/**
  src/test/java/com/sovereign/connect/**
  pom.xml (SC-C root)
```
