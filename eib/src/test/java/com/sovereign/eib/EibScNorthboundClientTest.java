package com.sovereign.eib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.eib.config.EibScNorthboundClientProperties;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.northbound.RestClientEibScNorthboundClient;
import com.sovereign.eib.northbound.dto.NorthboundCancelTemporalActRequestDto;
import com.sovereign.eib.northbound.dto.NorthboundCreateSignalTemporalActRequestDto;
import com.sovereign.eib.northbound.dto.NorthboundDeviceHealthViewDto;
import com.sovereign.eib.northbound.dto.NorthboundDiagnosticsViewDto;
import com.sovereign.eib.northbound.dto.NorthboundEndpointHealthViewDto;
import com.sovereign.eib.northbound.dto.NorthboundRuntimeStateViewDto;
import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;
import com.sovereign.eib.northbound.dto.NorthboundTopologySnapshotDto;
import com.sovereign.eib.northbound.dto.NorthboundTopologyVersionViewDto;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.northbound.dto.ScErrorDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EibScNorthboundClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockRestServiceServer server;
    private RestClientEibScNorthboundClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://sc-c-mock/sc/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientEibScNorthboundClient(builder.build(), objectMapper,
                new EibScNorthboundClientProperties("http://sc-c-mock/sc/v1", 500));
    }

    @Test
    void getTopologySnapshotUsesTopologyRoute() throws Exception {
        var envelope = new ScEnvelope<>("OK",
                new NorthboundTopologySnapshotDto("habitat.alpha", "7", "HABITAT", List.of(), List.of(), List.of(), List.of(),
                        Instant.parse("2026-01-01T00:00:00Z")), List.of(), null);
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/topology"))
                .andExpect(method(GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON));

        ScEnvelope<NorthboundTopologySnapshotDto> actual = client.getTopologySnapshot("habitat.alpha");

        assertThat(actual.status()).isEqualTo("OK");
        assertThat(actual.payload().topologyVersionValue()).isEqualTo("7");
        server.verify();
    }

    @Test
    void getTopologyVersionUsesTopologyVersionRouteAndDeserializesValue() throws Exception {
        var envelope = new ScEnvelope<>("OK",
                new NorthboundTopologyVersionViewDto("habitat.alpha", "8", "HABITAT", "habitat.alpha"), List.of(), null);
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/topology/version"))
                .andExpect(method(GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON));

        ScEnvelope<NorthboundTopologyVersionViewDto> actual = client.getTopologyVersion("habitat.alpha");

        assertThat(actual.payload().value()).isEqualTo("8");
        server.verify();
    }

    @Test
    void getDeviceHealthUsesDeviceHealthRoute() throws Exception {
        var dto = new NorthboundDeviceHealthViewDto(
                "device.alpha", "HEALTHY", "northbound",
                1, Instant.now(), List.of());
        server.expect(once(),
                        requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/devices/device.alpha/health"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(new ScEnvelope<>("OK", dto, List.of(), null)),
                        MediaType.APPLICATION_JSON));

        var result = client.getDeviceHealth("habitat.alpha", "device.alpha");

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.payload().deviceId()).isEqualTo("device.alpha");
        server.verify();
    }

    @Test
    void getEndpointHealthUsesEndpointHealthRoute() throws Exception {
        var dto = new NorthboundEndpointHealthViewDto(
                "endpoint.alpha", "HEALTHY", Instant.parse("2026-01-01T00:00:00Z"), "ok");
        server.expect(once(),
                        requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/endpoints/endpoint.alpha/health"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(new ScEnvelope<>("OK", dto, List.of(), null)),
                        MediaType.APPLICATION_JSON));

        var result = client.getEndpointHealth("habitat.alpha", "endpoint.alpha");

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.payload().endpointId()).isEqualTo("endpoint.alpha");
        server.verify();
    }

    @Test
    void getDeviceRuntimeStateUsesDeviceRoute() throws Exception {
        var envelope = new ScEnvelope<>("OK",
                new NorthboundRuntimeStateViewDto("habitat.alpha", "device.alpha", "DEVICE", Map.of("on", true),
                        Instant.parse("2026-01-01T00:00:00Z")), List.of(), null);
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/devices/device.alpha/runtime-state"))
                .andExpect(method(GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON));

        assertThat(client.getDeviceRuntimeState("habitat.alpha", "device.alpha").payload().subjectId()).isEqualTo("device.alpha");
        server.verify();
    }

    @Test
    void getEndpointRuntimeStateUsesEndpointRoute() throws Exception {
        var envelope = new ScEnvelope<>("OK",
                new NorthboundRuntimeStateViewDto("habitat.alpha", "endpoint.alpha", "ENDPOINT", Map.of("level", 50),
                        Instant.parse("2026-01-01T00:00:00Z")), List.of(), null);
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/endpoints/endpoint.alpha/runtime-state"))
                .andExpect(method(GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON));

        assertThat(client.getEndpointRuntimeState("habitat.alpha", "endpoint.alpha").payload().subjectId()).isEqualTo("endpoint.alpha");
        server.verify();
    }

    @Test
    void getDiagnosticsUsesDiagnosticsRoute() throws Exception {
        var dto = new NorthboundDiagnosticsViewDto(
                "habitat.alpha", "8", null, null,
                Instant.parse("2026-01-01T00:00:00Z"), List.of());
        server.expect(once(),
                        requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/diagnostics"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(new ScEnvelope<>("OK", dto, List.of(), null)),
                        MediaType.APPLICATION_JSON));

        var result = client.getDiagnostics("habitat.alpha");

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.payload().topologyVersion()).isEqualTo("8");
        server.verify();
    }

    @Test
    void listTemporalActsUsesModeAndMaxResultsQueryParams() throws Exception {
        var envelope = new ScEnvelope<>("OK", List.of(temporal("temporal.1")), List.of(), null);
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/temporal-acts?mode=ACTIVE&maxResults=50"))
                .andExpect(method(GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON));

        assertThat(client.listTemporalActs("habitat.alpha", "ACTIVE", 50).payload()).hasSize(1);
        server.verify();
    }

    @Test
    void getTemporalActUsesTemporalActByIdRoute() throws Exception {
        var envelope = new ScEnvelope<>("OK", temporal("temporal.3"), List.of(), null);
        server.expect(once(),
                        requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/temporal-acts/temporal.3"))
                .andExpect(method(GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON));

        var result = client.getTemporalAct("habitat.alpha", "temporal.3");

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.payload().temporalActId()).isEqualTo("temporal.3");
        server.verify();
    }

    @Test
    void createSignalTemporalActPostsToTemporalActsRootRoute() throws Exception {
        var act = temporal("temporal.2");
        var envelope = new ScEnvelope<>("ACCEPTED", act, List.of(), null);
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/temporal-acts"))
                .andExpect(method(POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON));

        var actual = client.createSignalTemporalAct("habitat.alpha",
                new NorthboundCreateSignalTemporalActRequestDto(act.dueAt(), "Wake", "WAKE", "target", "creator", "idem"));

        assertThat(actual.status()).isEqualTo("ACCEPTED");
        server.verify();
    }

    @Test
    void cancelTemporalActPostsToCancelRoute() throws Exception {
        var envelope = new ScEnvelope<>("CANCELLED", temporal("temporal.3"), List.of(), null);
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/habitat.alpha/temporal-acts/temporal.3/cancel"))
                .andExpect(method(POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON));

        var actual = client.cancelTemporalAct("habitat.alpha", "temporal.3",
                new NorthboundCancelTemporalActRequestDto("requester", "idem", "reason"));

        assertThat(actual.status()).isEqualTo("CANCELLED");
        server.verify();
    }

    @Test
    void http503WithValidSemanticEnvelopeReturnsEnvelope() throws Exception {
        var envelope = new ScEnvelope<NorthboundTopologySnapshotDto>("DEFERRED_SC_B_REQUIRED", null, List.of(),
                new ScErrorDto("DEFERRED_SC_B_REQUIRED", "transport required", "sc-b"));
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/deferred/topology"))
                .andRespond(withStatus(SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(envelope)));

        ScEnvelope<NorthboundTopologySnapshotDto> actual = client.getTopologySnapshot("deferred");

        assertThat(actual.status()).isEqualTo("DEFERRED_SC_B_REQUIRED");
        assertThat(actual.error().source()).isEqualTo("sc-b");
    }

    @Test
    void http503WithoutValidEnvelopeThrowsUpstreamUnavailable() {
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/down/topology"))
                .andRespond(withStatus(SERVICE_UNAVAILABLE).body("unavailable").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.getTopologySnapshot("down"))
                .isInstanceOf(EibUpstreamUnavailableException.class);
    }

    @Test
    void malformedJsonThrowsUpstreamUnavailable() {
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/malformed/topology"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getTopologySnapshot("malformed"))
                .isInstanceOf(EibUpstreamUnavailableException.class);
    }

    @Test
    void connectionFailureThrowsUpstreamUnavailable() {
        server.expect(once(), requestTo("http://sc-c-mock/sc/v1/habitats/refused/topology"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.getTopologySnapshot("refused"))
                .isInstanceOf(EibUpstreamUnavailableException.class);
    }

    private NorthboundTemporalActViewDto temporal(String id) {
        return new NorthboundTemporalActViewDto(id, "habitat.alpha", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"),
                "SIGNAL", "Wake", "WAKE", "target", "creator", null, null, null, null, null);
    }
}
