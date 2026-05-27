package com.sovereign.eib.northbound;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.eib.config.EibScNorthboundClientProperties;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

public class RestClientEibScNorthboundClient implements EibScNorthboundClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EibScNorthboundClientProperties props;

    public RestClientEibScNorthboundClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            EibScNorthboundClientProperties props
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public ScEnvelope<NorthboundTopologySnapshotDto> getTopologySnapshot(String habitatId) {
        return execute(() -> restClient.get()
                .uri("/habitats/{habitatId}/topology", habitatId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundTopologySnapshotDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<NorthboundTopologyVersionViewDto> getTopologyVersion(String habitatId) {
        return execute(() -> restClient.get()
                .uri("/habitats/{habitatId}/topology/version", habitatId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundTopologyVersionViewDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<NorthboundDeviceHealthViewDto> getDeviceHealth(String habitatId, String deviceId) {
        return execute(() -> restClient.get()
                .uri("/habitats/{habitatId}/devices/{deviceId}/health", habitatId, deviceId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundDeviceHealthViewDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<NorthboundEndpointHealthViewDto> getEndpointHealth(String habitatId, String endpointId) {
        return execute(() -> restClient.get()
                .uri("/habitats/{habitatId}/endpoints/{endpointId}/health", habitatId, endpointId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundEndpointHealthViewDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<NorthboundRuntimeStateViewDto> getDeviceRuntimeState(String habitatId, String deviceId) {
        return execute(() -> restClient.get()
                .uri("/habitats/{habitatId}/devices/{deviceId}/runtime-state", habitatId, deviceId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundRuntimeStateViewDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<NorthboundRuntimeStateViewDto> getEndpointRuntimeState(String habitatId, String endpointId) {
        return execute(() -> restClient.get()
                .uri("/habitats/{habitatId}/endpoints/{endpointId}/runtime-state", habitatId, endpointId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundRuntimeStateViewDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<NorthboundDiagnosticsViewDto> getDiagnostics(String habitatId) {
        return execute(() -> restClient.get()
                .uri("/habitats/{habitatId}/diagnostics", habitatId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundDiagnosticsViewDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<List<NorthboundTemporalActViewDto>> listTemporalActs(String habitatId, String mode, Integer maxResults) {
        return execute(() -> restClient.get()
                .uri("/habitats/{habitatId}/temporal-acts?mode={mode}&maxResults={maxResults}",
                        habitatId, mode, maxResults)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<List<NorthboundTemporalActViewDto>>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<NorthboundTemporalActViewDto> getTemporalAct(String habitatId, String temporalActId) {
        return execute(() -> restClient.get()
                .uri("/habitats/{habitatId}/temporal-acts/{temporalActId}", habitatId, temporalActId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundTemporalActViewDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<NorthboundTemporalActViewDto> createSignalTemporalAct(
            String habitatId,
            NorthboundCreateSignalTemporalActRequestDto request
    ) {
        return execute(() -> restClient.post()
                .uri("/habitats/{habitatId}/temporal-acts", habitatId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundTemporalActViewDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public ScEnvelope<NorthboundTemporalActViewDto> cancelTemporalAct(
            String habitatId,
            String temporalActId,
            NorthboundCancelTemporalActRequestDto request
    ) {
        return execute(() -> restClient.post()
                .uri("/habitats/{habitatId}/temporal-acts/{temporalActId}/cancel", habitatId, temporalActId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) ->
                        semanticOrUnavailable(response.getBody(), response.getStatusCode(),
                                new TypeReference<ScEnvelope<NorthboundTemporalActViewDto>>() {}))
                .body(new ParameterizedTypeReference<>() {}));
    }

    private <T> ScEnvelope<T> execute(Supplier<ScEnvelope<T>> supplier) {
        try {
            ScEnvelope<T> envelope = supplier.get();
            if (envelope == null || envelope.status() == null) {
                throw new EibUpstreamUnavailableException("empty upstream envelope from " + props.baseUrl());
            }
            return envelope;
        } catch (EibSemanticScCException e) {
            return e.getEnvelope();
        } catch (RestClientException e) {
            throw new EibUpstreamUnavailableException("upstream unavailable at " + props.baseUrl(), e);
        }
    }

    private <T> void semanticOrUnavailable(
            InputStream body,
            HttpStatusCode statusCode,
            TypeReference<ScEnvelope<T>> typeReference
    ) throws IOException {
        try {
            ScEnvelope<T> envelope = objectMapper.readValue(body, typeReference);
            if (envelope != null && envelope.status() != null) {
                throw new EibSemanticScCException(envelope);
            }
        } catch (EibSemanticScCException e) {
            throw e;
        } catch (Exception ignored) {
            throw new EibUpstreamUnavailableException("HTTP " + statusCode.value());
        }
        throw new EibUpstreamUnavailableException("HTTP " + statusCode.value());
    }
}
