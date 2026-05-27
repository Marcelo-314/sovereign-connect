package com.sovereign.eib.service;

import com.sovereign.eib.domain.EffectiveDiagnosticsSummary;
import com.sovereign.eib.domain.EffectiveHabitatView;
import com.sovereign.eib.domain.EibError;
import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.domain.EibResponse;
import com.sovereign.eib.domain.EibWarning;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;
import com.sovereign.eib.northbound.dto.NorthboundTopologySnapshotDto;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EibEffectiveViewService {

    private final EibScNorthboundClient client;
    private final EibCanonicalEnvelopeMapper envelopeMapper;
    private final EibEffectiveViewMapper viewMapper;

    public EibEffectiveViewService(
            EibScNorthboundClient client,
            EibCanonicalEnvelopeMapper envelopeMapper,
            EibEffectiveViewMapper viewMapper
    ) {
        this.client = client;
        this.envelopeMapper = envelopeMapper;
        this.viewMapper = viewMapper;
    }

    public EibResponse<EffectiveHabitatView> getEffectiveView(String habitatId, EibRequestContext ctx) {
        ScEnvelope<NorthboundTopologySnapshotDto> topology;
        try {
            topology = client.getTopologySnapshot(habitatId);
        } catch (EibUpstreamUnavailableException e) {
            return new EibResponse<>("UPSTREAM_UNAVAILABLE", null, List.of(),
                    new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound"), null);
        }
        if (!envelopeMapper.isSuccess(topology) || topology.payload() == null) {
            return new EibResponse<>(envelopeMapper.extractScStatus(topology), null,
                    envelopeMapper.mapWarnings(topology.warnings()), envelopeMapper.mapError(topology.error()), null);
        }

        List<EibWarning> warnings = new ArrayList<>(envelopeMapper.mapWarnings(topology.warnings()));
        List<NorthboundTemporalActViewDto> temporalActs = List.of();
        try {
            ScEnvelope<List<NorthboundTemporalActViewDto>> temporalEnvelope = client.listTemporalActs(habitatId, "ACTIVE", 50);
            if (envelopeMapper.isSuccess(temporalEnvelope) && temporalEnvelope.payload() != null) {
                temporalActs = temporalEnvelope.payload();
            } else {
                warnings.add(new EibWarning("TEMPORAL_ACTS_DEGRADED", "Temporal acts are temporarily unavailable", "eib.temporal"));
            }
        } catch (EibUpstreamUnavailableException e) {
            warnings.add(new EibWarning("TEMPORAL_ACTS_DEGRADED", "Temporal acts are temporarily unavailable", "eib.temporal"));
        }

        NorthboundTopologySnapshotDto snapshot = topology.payload();
        EffectiveHabitatView view = new EffectiveHabitatView(
                "eib.habitat." + habitatId,
                snapshot.topologyVersionValue(),
                Instant.now(),
                safe(snapshot.rooms()).stream().map(room -> viewMapper.room(habitatId, room)).toList(),
                safe(snapshot.zones()).stream().map(zone -> viewMapper.zone(habitatId, zone)).toList(),
                safe(snapshot.devices()).stream().map(device -> viewMapper.device(habitatId, device, safe(snapshot.endpoints()), ctx)).toList(),
                safe(snapshot.endpoints()).stream().map(endpoint -> viewMapper.endpoint(habitatId, endpoint, ctx)).toList(),
                temporalActs.stream().map(act -> viewMapper.temporal(habitatId, act, ctx)).toList(),
                new EffectiveDiagnosticsSummary("UNKNOWN", "UNKNOWN", "UNKNOWN"),
                warnings
        );
        return new EibResponse<>("OK", view, warnings, null, null);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
