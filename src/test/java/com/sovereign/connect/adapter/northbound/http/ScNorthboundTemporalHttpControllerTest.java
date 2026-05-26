package com.sovereign.connect.adapter.northbound.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.northbound.ScCoreNorthboundFacade;
import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.ScNorthboundWarning;
import com.sovereign.connect.core.northbound.runtime.NorthboundDiagnosticsView;
import com.sovereign.connect.core.northbound.runtime.NorthboundMigrationReadinessView;
import com.sovereign.connect.core.northbound.runtime.NorthboundTemporalRuntimeStatusView;
import com.sovereign.connect.core.northbound.temporal.NorthboundCancelTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundCreateSignalTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActFilter;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScNorthboundTemporalHttpController.class)
class ScNorthboundTemporalHttpControllerTest {

    private static final String HABITAT = "habitat-001";
    private static final Instant NOW = Instant.parse("2026-05-26T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScCoreNorthboundFacade facade;

    @Test
    void getTemporalRuntimeStatus_returns200() throws Exception {
        when(facade.getTemporalRuntimeStatus(HABITAT)).thenReturn(ScNorthboundResponse.ok(runtimeStatus()));

        mockMvc.perform(get("/sc/v1/habitats/{h}/temporal/runtime-status", HABITAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.engineStatus").value("RUNNING"));
    }

    @Test
    void getRecoveryStatus_returns501_whenFacadeReturnsUnsupportedProfile() throws Exception {
        when(facade.getRecoveryStatus(HABITAT))
            .thenReturn(ScNorthboundResponse.unsupportedProfile("recovery status not supported"));

        mockMvc.perform(get("/sc/v1/habitats/{h}/recovery/status", HABITAT))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.status").value("UNSUPPORTED_PROFILE"));
    }

    @Test
    void getDiagnostics_returns200_andKeepsPendingNormalizationInBody() throws Exception {
        when(facade.getNorthboundDiagnostics(HABITAT)).thenReturn(ScNorthboundResponse.ok(diagnostics()));

        mockMvc.perform(get("/sc/v1/habitats/{h}/diagnostics", HABITAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OK"))
            .andExpect(jsonPath("$.payload.migrationReadiness.status").value("UNKNOWN_PENDING_NORMALIZATION"));
    }

    @Test
    void getTemporalAct_returns200() throws Exception {
        when(facade.getTemporalAct(HABITAT, "act-1")).thenReturn(ScNorthboundResponse.ok(act("act-1")));

        mockMvc.perform(get("/sc/v1/habitats/{h}/temporal-acts/{id}", HABITAT, "act-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.temporalActId").value("act-1"));
    }

    @Test
    void createSignalTemporalAct_validBody_returns202_andDelegatesRequest() throws Exception {
        when(facade.createSignalTemporalAct(eq(HABITAT), any(NorthboundCreateSignalTemporalActRequest.class)))
            .thenReturn(ScNorthboundResponse.accepted(act("act-1")));
        var request = new NorthboundCreateSignalTemporalActRequest(
            NOW.plusSeconds(60), "label", "ALARM", "surface:1", "hub-1", "k1");

        mockMvc.perform(post("/sc/v1/habitats/{h}/temporal-acts", HABITAT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        ArgumentCaptor<NorthboundCreateSignalTemporalActRequest> captor =
            ArgumentCaptor.forClass(NorthboundCreateSignalTemporalActRequest.class);
        verify(facade).createSignalTemporalAct(eq(HABITAT), captor.capture());
        assertThat(captor.getValue().dueAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void createSignalTemporalAct_validationError_returns422() throws Exception {
        when(facade.createSignalTemporalAct(eq(HABITAT), any(NorthboundCreateSignalTemporalActRequest.class)))
            .thenReturn(ScNorthboundResponse.validationError("INVALID_DUE_AT", "dueAt must be future"));

        mockMvc.perform(post("/sc/v1/habitats/{h}/temporal-acts", HABITAT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"dueAt":"2026-05-26T10:00:00Z","label":"test","signalKind":"ALARM",
                     "notificationTargetRef":"surface:1","createdByRef":"hub-1","idempotencyKey":"k1"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"));
    }

    @Test
    void cancelTemporalAct_pathVariableIsAuthoritative_bodyDoesNotContainTemporalActId() throws Exception {
        String actId = "act-abc-123";
        when(facade.cancelTemporalAct(eq(HABITAT), any(NorthboundCancelTemporalActRequest.class)))
            .thenReturn(ScNorthboundResponse.cancelled(null));

        mockMvc.perform(post("/sc/v1/habitats/{h}/temporal-acts/{id}/cancel", HABITAT, actId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"requestedByRef": "hub-1", "idempotencyKey": "k1", "reason": "test"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        ArgumentCaptor<NorthboundCancelTemporalActRequest> captor =
            ArgumentCaptor.forClass(NorthboundCancelTemporalActRequest.class);
        verify(facade).cancelTemporalAct(eq(HABITAT), captor.capture());
        assertThat(captor.getValue().temporalActId()).isEqualTo(actId);
        assertThat(captor.getValue().requestedByRef()).isEqualTo("hub-1");
    }

    @Test
    void listTemporalActs_activeModeAndMaxResults_passesFilter() throws Exception {
        when(facade.listTemporalActs(eq(HABITAT), any(NorthboundTemporalActFilter.class)))
            .thenReturn(ScNorthboundResponse.ok(List.of(act("act-1"))));

        mockMvc.perform(get("/sc/v1/habitats/{h}/temporal-acts", HABITAT)
                .param("mode", "ACTIVE")
                .param("maxResults", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload[0].temporalActId").value("act-1"));

        ArgumentCaptor<NorthboundTemporalActFilter> captor = ArgumentCaptor.forClass(NorthboundTemporalActFilter.class);
        verify(facade).listTemporalActs(eq(HABITAT), captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo(NorthboundTemporalActFilter.Mode.ACTIVE);
        assertThat(captor.getValue().maxResults()).isEqualTo(50);
    }

    @Test
    void listTemporalActs_terminalMode_passesSupportedEnum() throws Exception {
        when(facade.listTemporalActs(eq(HABITAT), any(NorthboundTemporalActFilter.class)))
            .thenReturn(ScNorthboundResponse.ok(List.of()));

        mockMvc.perform(get("/sc/v1/habitats/{h}/temporal-acts", HABITAT)
                .param("mode", "TERMINAL"))
            .andExpect(status().isOk());

        ArgumentCaptor<NorthboundTemporalActFilter> captor = ArgumentCaptor.forClass(NorthboundTemporalActFilter.class);
        verify(facade).listTemporalActs(eq(HABITAT), captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo(NorthboundTemporalActFilter.Mode.TERMINAL);
    }

    @Test
    void listTemporalActs_unknownMode_returns400_withoutInvokingFacade() throws Exception {
        mockMvc.perform(get("/sc/v1/habitats/{h}/temporal-acts", HABITAT)
                .param("mode", "BOGUS"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("INVALID_REQUEST"));

        verify(facade, never()).listTemporalActs(any(), any());
    }

    private NorthboundTemporalRuntimeStatusView runtimeStatus() {
        return new NorthboundTemporalRuntimeStatusView(HABITAT, "RUNNING", true, NOW, NOW, 1, 0, 0, 0, 0);
    }

    private NorthboundDiagnosticsView diagnostics() {
        return new NorthboundDiagnosticsView(
            HABITAT,
            "7",
            runtimeStatus(),
            new NorthboundMigrationReadinessView(
                "UNKNOWN_PENDING_NORMALIZATION",
                "migration.readiness",
                "No migration readiness read port exists; deferred to future MU."
            ),
            NOW,
            List.of(new ScNorthboundWarning(
                "MIGRATION_READINESS_PENDING_NORMALIZATION",
                "Migration readiness is not yet exposed through a Northbound-safe port.",
                "migration.readiness"
            ))
        );
    }

    private NorthboundTemporalActView act(String id) {
        return new NorthboundTemporalActView(id, HABITAT, "ACTIVE", NOW.plusSeconds(60), "SIGNAL", "label",
            "ALARM", "surface:1", "hub-1", NOW, NOW, null, null, null);
    }
}
