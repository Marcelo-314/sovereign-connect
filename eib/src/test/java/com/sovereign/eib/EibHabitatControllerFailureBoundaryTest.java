package com.sovereign.eib;

import com.sovereign.eib.api.EibHabitatController;
import com.sovereign.eib.config.EibDiagnosticAdminProperties;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import com.sovereign.eib.service.EibEffectiveViewMapper;
import com.sovereign.eib.service.EibEffectiveViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class EibHabitatControllerFailureBoundaryTest {

    private final EibScNorthboundClient client = mock(EibScNorthboundClient.class);
    private final EibEffectiveRefCodec codec =
            new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256");
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EibEffectiveViewService effectiveViewService = new EibEffectiveViewService(
                client, new EibCanonicalEnvelopeMapper(), new EibEffectiveViewMapper(codec));
        mockMvc = MockMvcBuilders.standaloneSetup(new EibHabitatController(
                effectiveViewService, client, new EibCanonicalEnvelopeMapper(),
                new EibDiagnosticAdminProperties(false)
        )).build();
    }

    @Test
    void singleDeviceRoutePropagatesUpstreamUnavailableFromParent() throws Exception {
        when(client.getTopologySnapshot("habitat.alpha"))
                .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        mockMvc.perform(get("/eib/v1/habitats/habitat.alpha/devices/eib.device.any"))
                .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void singleEndpointRoutePropagatesUpstreamUnavailableFromParent() throws Exception {
        when(client.getTopologySnapshot("habitat.alpha"))
                .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        mockMvc.perform(get("/eib/v1/habitats/habitat.alpha/endpoints/eib.endpoint.any"))
                .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void diagnosticsRouteReturnsUpstreamUnavailableWhenClientThrows() throws Exception {
        when(client.getDiagnostics("habitat.alpha"))
                .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        mockMvc.perform(get("/eib/v1/habitats/habitat.alpha/diagnostics"))
                .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }
}
