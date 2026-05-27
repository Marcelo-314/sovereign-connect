package com.sovereign.eib.service;

import com.sovereign.eib.domain.EffectiveTemporalActView;
import com.sovereign.eib.domain.EibError;
import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.domain.EibResponse;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EibTemporalActProjectionService {

    private final EibScNorthboundClient client;
    private final EibCanonicalEnvelopeMapper envelopeMapper;
    private final EibEffectiveViewMapper viewMapper;
    private final EibEffectiveRefCodec codec;

    public EibTemporalActProjectionService(
            EibScNorthboundClient client,
            EibCanonicalEnvelopeMapper envelopeMapper,
            EibEffectiveViewMapper viewMapper,
            EibEffectiveRefCodec codec
    ) {
        this.client = client;
        this.envelopeMapper = envelopeMapper;
        this.viewMapper = viewMapper;
        this.codec = codec;
    }

    public EibResponse<List<EffectiveTemporalActView>> listEffectiveTemporalActs(String habitatId, EibRequestContext ctx) {
        ScEnvelope<List<NorthboundTemporalActViewDto>> envelope;
        try {
            envelope = client.listTemporalActs(habitatId, "ACTIVE", 50);
        } catch (EibUpstreamUnavailableException e) {
            return new EibResponse<>("UPSTREAM_UNAVAILABLE", List.of(), List.of(),
                    new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound"), null);
        }
        if (!envelopeMapper.isSuccess(envelope) || envelope.payload() == null) {
            return new EibResponse<>(envelopeMapper.extractScStatus(envelope), List.of(),
                    envelopeMapper.mapWarnings(envelope.warnings()), envelopeMapper.mapError(envelope.error()), null);
        }
        return new EibResponse<>("OK",
                envelope.payload().stream().map(act -> viewMapper.temporal(habitatId, act, ctx)).toList(),
                envelopeMapper.mapWarnings(envelope.warnings()), null, null);
    }

    public EibResponse<EffectiveTemporalActView> getEffectiveTemporalAct(String habitatId, String effectiveRef, EibRequestContext ctx) {
        ScEnvelope<List<NorthboundTemporalActViewDto>> envelope;
        try {
            envelope = client.listTemporalActs(habitatId, "ACTIVE", 50);
        } catch (EibUpstreamUnavailableException e) {
            return new EibResponse<>("UPSTREAM_UNAVAILABLE", null, List.of(),
                    new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound"), null);
        }
        if (!envelopeMapper.isSuccess(envelope) || envelope.payload() == null) {
            return new EibResponse<>(envelopeMapper.extractScStatus(envelope), null,
                    envelopeMapper.mapWarnings(envelope.warnings()), envelopeMapper.mapError(envelope.error()), null);
        }
        return envelope.payload().stream()
                .filter(act -> codec.generateRef("eib.temporal", habitatId, act.temporalActId()).equals(effectiveRef))
                .findFirst()
                .map(act -> new EibResponse<>("OK", viewMapper.temporal(habitatId, act, ctx), List.of(), null, null))
                .orElseGet(() -> new EibResponse<>("NOT_FOUND", null, List.of(),
                        new EibError("NOT_FOUND", "temporal act not visible", "eib.temporal"), null));
    }
}
