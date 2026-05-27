package com.sovereign.eib;

import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.domain.EibTemporalCancellationAdmissionRequest;
import com.sovereign.eib.domain.EibTemporalSignalAdmissionRequest;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.northbound.dto.NorthboundCancelTemporalActRequestDto;
import com.sovereign.eib.northbound.dto.NorthboundCreateSignalTemporalActRequestDto;
import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import com.sovereign.eib.service.EibTemporalAdmissionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EibTemporalAdmissionServiceTest {

    private final EibScNorthboundClient client = mock(EibScNorthboundClient.class);
    private final EibEffectiveRefCodec codec = new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256");
    private final EibTemporalAdmissionService service =
            new EibTemporalAdmissionService(client, new EibCanonicalEnvelopeMapper(), codec);
    private final EibRequestContext ctx = new EibRequestContext("ctx", "actor", "surface", "en",
            false, false, Instant.now(), "client-req");

    @Test
    void admitsAcceptedSignalAndReturnsEffectiveRef() {
        var act = temporal("temporal.1");
        when(client.createSignalTemporalAct(eq("habitat.alpha"), any(NorthboundCreateSignalTemporalActRequestDto.class)))
                .thenReturn(new ScEnvelope<>("ACCEPTED", act, List.of(), null));

        var decision = service.admitTemporalSignalRequest("habitat.alpha",
                new EibTemporalSignalAdmissionRequest(act.dueAt(), "Wake", "WAKE", "target", "idem-1"), ctx);

        assertThat(decision.status()).isEqualTo("ADMITTED");
        assertThat(decision.effectiveRef()).startsWith("eib.temporal.");
        assertThat(decision.canonicalTrace().scNorthboundStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void cancellationRejectsInvisibleEffectiveRefWithoutCanonicalSubmission() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
                .thenReturn(new ScEnvelope<>("OK", List.of(temporal("temporal.visible")), List.of(), null));

        var decision = service.admitTemporalCancellation("habitat.alpha", "eib.temporal.not-visible",
                new EibTemporalCancellationAdmissionRequest("idem-2", "user"), ctx);

        assertThat(decision.status()).isEqualTo("REJECTED_NOT_VISIBLE");
        assertThat(decision.canonicalTrace().scNorthboundStatus()).isEqualTo("NOT_SUBMITTED");
        verify(client, never()).cancelTemporalAct(any(), any(), any(NorthboundCancelTemporalActRequestDto.class));
    }

    @Test
    void signalAdmissionReturnsFailedUpstreamUnavailableWhenCreateThrows() {
        when(client.createSignalTemporalAct(eq("habitat.alpha"), any()))
                .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        var decision = service.admitTemporalSignalRequest("habitat.alpha",
                new EibTemporalSignalAdmissionRequest(
                        Instant.parse("2026-01-01T00:00:00Z"), "Wake", "WAKE", "target", "idem"), ctx);

        assertThat(decision.status()).isEqualTo("FAILED_UPSTREAM_UNAVAILABLE");
        assertThat(decision.effectiveRef()).isNull();
        assertThat(decision.canonicalTrace().scNorthboundStatus()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(decision.error().code()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(decision.error().source()).isEqualTo("eib.northbound");
    }

    @Test
    void cancelAdmissionReturnsFailedUpstreamUnavailableWhenListThrows() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
                .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        var decision = service.admitTemporalCancellation("habitat.alpha", "eib.temporal.any",
                new EibTemporalCancellationAdmissionRequest("idem", "reason"), ctx);

        assertThat(decision.status()).isEqualTo("FAILED_UPSTREAM_UNAVAILABLE");
        assertThat(decision.status()).isNotEqualTo("REJECTED_NOT_VISIBLE");
        assertThat(decision.canonicalTrace().scNorthboundStatus()).isEqualTo("UPSTREAM_UNAVAILABLE");
        verify(client, never()).cancelTemporalAct(any(), any(), any());
    }

    @Test
    void cancelAdmissionReturnsFailedUpstreamUnavailableWhenCancelThrows() {
        var act = temporal("temporal.visible");
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
                .thenReturn(new ScEnvelope<>("OK", List.of(act), List.of(), null));
        String ref = codec.generateRef("eib.temporal", "habitat.alpha", "temporal.visible");
        when(client.cancelTemporalAct(eq("habitat.alpha"), eq("temporal.visible"), any()))
                .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        var decision = service.admitTemporalCancellation("habitat.alpha", ref,
                new EibTemporalCancellationAdmissionRequest("idem", "reason"), ctx);

        assertThat(decision.status()).isEqualTo("FAILED_UPSTREAM_UNAVAILABLE");
        assertThat(decision.canonicalTrace().scNorthboundStatus()).isEqualTo("UPSTREAM_UNAVAILABLE");
    }

    private NorthboundTemporalActViewDto temporal(String id) {
        return new NorthboundTemporalActViewDto(id, "habitat.alpha", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"),
                "SIGNAL", "Wake", "WAKE", "target", "creator", null, null, null, null, null);
    }
}
