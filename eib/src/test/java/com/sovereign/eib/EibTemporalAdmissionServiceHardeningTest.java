package com.sovereign.eib;

import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.domain.EibTemporalCancellationAdmissionRequest;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import com.sovereign.eib.service.EibTemporalAdmissionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EibTemporalAdmissionServiceHardeningTest {

    private final EibScNorthboundClient client = mock(EibScNorthboundClient.class);
    private final EibEffectiveRefCodec codec =
            new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256");
    private final EibCanonicalEnvelopeMapper envelopeMapper = new EibCanonicalEnvelopeMapper();
    private final EibTemporalAdmissionService service =
            new EibTemporalAdmissionService(client, envelopeMapper, codec);
    private final EibRequestContext ctx = new EibRequestContext(
            "ctx", "actor", "surface", "en",
            false, false, Instant.now(), "req");
    private final EibTemporalCancellationAdmissionRequest cancelReq =
            new EibTemporalCancellationAdmissionRequest("idem", "reason");

    @Test
    void cancelPropagatesDeferredScBRequiredFromListWithoutCallingCancel() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
                .thenReturn(new ScEnvelope<>("DEFERRED_SC_B_REQUIRED", null, List.of(), null));

        var decision = service.admitTemporalCancellation(
                "habitat.alpha", "eib.temporal.any", cancelReq, ctx);

        assertThat(decision.status()).isEqualTo("DEFERRED_SC_B_REQUIRED");
        assertThat(decision.status()).isNotEqualTo("REJECTED_NOT_VISIBLE");
        verify(client, never()).cancelTemporalAct(any(), any(), any());
    }

    @Test
    void cancelPropagatesUnknownPendingNormalizationFromListWithoutCallingCancel() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
                .thenReturn(new ScEnvelope<>("UNKNOWN_PENDING_NORMALIZATION", null, List.of(), null));

        var decision = service.admitTemporalCancellation(
                "habitat.alpha", "eib.temporal.any", cancelReq, ctx);

        assertThat(decision.status()).isEqualTo("DEFERRED_PENDING_NORMALIZATION");
        assertThat(decision.status()).isNotEqualTo("REJECTED_NOT_VISIBLE");
        verify(client, never()).cancelTemporalAct(any(), any(), any());
    }

    @Test
    void cancelPropagatesUnsupportedProfileFromListWithoutCallingCancel() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
                .thenReturn(new ScEnvelope<>("UNSUPPORTED_PROFILE", null, List.of(), null));

        var decision = service.admitTemporalCancellation(
                "habitat.alpha", "eib.temporal.any", cancelReq, ctx);

        assertThat(decision.status()).isEqualTo("DEFERRED_UNSUPPORTED_PROFILE");
        assertThat(decision.status()).isNotEqualTo("REJECTED_NOT_VISIBLE");
        verify(client, never()).cancelTemporalAct(any(), any(), any());
    }
}
