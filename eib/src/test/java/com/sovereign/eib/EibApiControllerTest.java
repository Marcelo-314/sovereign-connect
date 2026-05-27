package com.sovereign.eib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.eib.api.EibTemporalActController;
import com.sovereign.eib.config.EibDiagnosticAdminProperties;
import com.sovereign.eib.domain.CanonicalSubmissionTrace;
import com.sovereign.eib.domain.EibError;
import com.sovereign.eib.domain.EibTemporalCancellationAdmissionRequest;
import com.sovereign.eib.domain.EibTemporalSignalAdmissionRequest;
import com.sovereign.eib.domain.InteractionAdmissionDecision;
import com.sovereign.eib.service.EibTemporalActProjectionService;
import com.sovereign.eib.service.EibTemporalAdmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EibApiControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final EibTemporalAdmissionService admissionService = mock(EibTemporalAdmissionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EibTemporalActController(
                mock(EibTemporalActProjectionService.class),
                admissionService,
                new EibDiagnosticAdminProperties(false)
        )).build();
    }

    @Test
    void signalAdmissionReturnsEibResponseWrapper() throws Exception {
        when(admissionService.admitTemporalSignalRequest(eq("habitat.alpha"), any(), any()))
                .thenReturn(new InteractionAdmissionDecision("adm.1", "ADMITTED", "eib.temporal.abc",
                        new CanonicalSubmissionTrace("client", "adm.1", "ACCEPTED", null, List.of()),
                        List.of(), null));

        mockMvc.perform(post("/eib/v1/habitats/habitat.alpha/temporal-acts/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EibTemporalSignalAdmissionRequest(
                                Instant.parse("2026-01-01T00:00:00Z"), "Wake", "WAKE", "target", "idem"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.payload.status").value("ADMITTED"))
                .andExpect(jsonPath("$.payload.effectiveRef").value("eib.temporal.abc"))
                .andExpect(jsonPath("$.payload.canonicalTrace.scNorthboundStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.canonicalTrace").value(nullValue()));
    }

    @Test
    void cancelAdmissionReturnsEibResponseWrapperWithNullEffectiveRef() throws Exception {
        when(admissionService.admitTemporalCancellation(eq("habitat.alpha"), eq("eib.temporal.abc"), any(), any()))
                .thenReturn(new InteractionAdmissionDecision("adm.2", "ADMITTED", null,
                        new CanonicalSubmissionTrace("client", "adm.2", "CANCELLED", null, List.of()),
                        List.of(), null));

        mockMvc.perform(post("/eib/v1/habitats/habitat.alpha/temporal-acts/eib.temporal.abc/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EibTemporalCancellationAdmissionRequest("idem", "reason"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.payload.status").value("ADMITTED"))
                .andExpect(jsonPath("$.payload.effectiveRef").value(nullValue()))
                .andExpect(jsonPath("$.payload.canonicalTrace.scNorthboundStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.canonicalTrace").value(nullValue()));
    }

    @Test
    void signalAdmissionControllerReturnsUpstreamUnavailableBodyWhenServiceFails() throws Exception {
        when(admissionService.admitTemporalSignalRequest(eq("habitat.alpha"), any(), any()))
                .thenReturn(new InteractionAdmissionDecision(
                        "adm.fail", "FAILED_UPSTREAM_UNAVAILABLE", null,
                        new CanonicalSubmissionTrace("client", "adm.fail", "UPSTREAM_UNAVAILABLE",
                                new EibError("UPSTREAM_UNAVAILABLE", "connection refused", "eib.northbound"),
                                List.of()),
                        List.of(),
                        new EibError("UPSTREAM_UNAVAILABLE", "connection refused", "eib.northbound")));

        mockMvc.perform(post("/eib/v1/habitats/habitat.alpha/temporal-acts/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EibTemporalSignalAdmissionRequest(
                                Instant.parse("2026-01-01T00:00:00Z"), "Wake", "WAKE", "target", "idem"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.payload.status").value("FAILED_UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.payload.effectiveRef").value(nullValue()))
                .andExpect(jsonPath("$.payload.canonicalTrace.scNorthboundStatus")
                        .value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void cancelAdmissionControllerReturnsUpstreamUnavailableBodyWhenServiceFails() throws Exception {
        when(admissionService.admitTemporalCancellation(
                eq("habitat.alpha"), eq("eib.temporal.abc"), any(), any()))
                .thenReturn(new InteractionAdmissionDecision(
                        "adm.fail", "FAILED_UPSTREAM_UNAVAILABLE", null,
                        new CanonicalSubmissionTrace("client", "adm.fail", "UPSTREAM_UNAVAILABLE",
                                new EibError("UPSTREAM_UNAVAILABLE", "connection refused", "eib.northbound"),
                                List.of()),
                        List.of(),
                        new EibError("UPSTREAM_UNAVAILABLE", "connection refused", "eib.northbound")));

        mockMvc.perform(post("/eib/v1/habitats/habitat.alpha/temporal-acts/eib.temporal.abc/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EibTemporalCancellationAdmissionRequest("idem", "reason"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.payload.status").value("FAILED_UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.payload.effectiveRef").value(nullValue()));
    }
}
