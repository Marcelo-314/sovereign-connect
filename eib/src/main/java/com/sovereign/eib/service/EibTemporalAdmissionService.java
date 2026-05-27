package com.sovereign.eib.service;

import com.sovereign.eib.domain.CanonicalSubmissionTrace;
import com.sovereign.eib.domain.EibError;
import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.domain.EibTemporalCancellationAdmissionRequest;
import com.sovereign.eib.domain.EibTemporalSignalAdmissionRequest;
import com.sovereign.eib.domain.InteractionAdmissionDecision;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.northbound.dto.NorthboundCancelTemporalActRequestDto;
import com.sovereign.eib.northbound.dto.NorthboundCreateSignalTemporalActRequestDto;
import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EibTemporalAdmissionService {

    private final EibScNorthboundClient client;
    private final EibCanonicalEnvelopeMapper envelopeMapper;
    private final EibEffectiveRefCodec codec;

    public EibTemporalAdmissionService(
            EibScNorthboundClient client,
            EibCanonicalEnvelopeMapper envelopeMapper,
            EibEffectiveRefCodec codec
    ) {
        this.client = client;
        this.envelopeMapper = envelopeMapper;
        this.codec = codec;
    }

    public InteractionAdmissionDecision admitTemporalSignalRequest(
            String habitatId,
            EibTemporalSignalAdmissionRequest request,
            EibRequestContext ctx
    ) {
        String admissionId = "adm." + UUID.randomUUID();
        ScEnvelope<NorthboundTemporalActViewDto> envelope;
        try {
            envelope = client.createSignalTemporalAct(habitatId,
                    new NorthboundCreateSignalTemporalActRequestDto(
                            request.dueAt(),
                            request.label(),
                            request.signalKind(),
                            request.notificationTargetRef(),
                            "eib-service",
                            request.idempotencyKey()
                    ));
        } catch (EibUpstreamUnavailableException e) {
            EibError error = new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound");
            return new InteractionAdmissionDecision(
                    admissionId, "FAILED_UPSTREAM_UNAVAILABLE", null,
                    new CanonicalSubmissionTrace(
                            ctx.clientRequestRef(), admissionId, "UPSTREAM_UNAVAILABLE", error, List.of()),
                    List.of(), error);
        }
        CanonicalSubmissionTrace trace = envelopeMapper.toTrace(ctx.clientRequestRef(), admissionId, envelope);
        String effectiveRef = envelopeMapper.isSuccess(envelope) && envelope.payload() != null
                ? codec.generateRef("eib.temporal", habitatId, envelope.payload().temporalActId())
                : null;
        return new InteractionAdmissionDecision(admissionId, mapScStatusToAdmissionStatus(envelope.status()),
                effectiveRef, trace, envelopeMapper.mapWarnings(envelope.warnings()), envelopeMapper.mapError(envelope.error()));
    }

    public InteractionAdmissionDecision admitTemporalCancellation(
            String habitatId,
            String effectiveRef,
            EibTemporalCancellationAdmissionRequest request,
            EibRequestContext ctx
    ) {
        String admissionId = "adm." + UUID.randomUUID();

        ScEnvelope<List<NorthboundTemporalActViewDto>> visible;
        try {
            visible = client.listTemporalActs(habitatId, "ACTIVE", 50);
        } catch (EibUpstreamUnavailableException e) {
            EibError error = new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound");
            return new InteractionAdmissionDecision(
                    admissionId, "FAILED_UPSTREAM_UNAVAILABLE", null,
                    new CanonicalSubmissionTrace(
                            ctx.clientRequestRef(), admissionId, "UPSTREAM_UNAVAILABLE", error, List.of()),
                    List.of(), error);
        }

        return codec.resolveTemporalRef(effectiveRef, habitatId, visible.payload() == null ? List.of() : visible.payload())
                .map(canonicalId -> {
                    ScEnvelope<NorthboundTemporalActViewDto> envelope;
                    try {
                        envelope = client.cancelTemporalAct(habitatId, canonicalId,
                                new NorthboundCancelTemporalActRequestDto("eib-service", request.idempotencyKey(), request.reason()));
                    } catch (EibUpstreamUnavailableException e) {
                        EibError error = new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound");
                        return new InteractionAdmissionDecision(
                                admissionId, "FAILED_UPSTREAM_UNAVAILABLE", null,
                                new CanonicalSubmissionTrace(
                                        ctx.clientRequestRef(), admissionId, "UPSTREAM_UNAVAILABLE", error, List.of()),
                                List.of(), error);
                    }
                    return new InteractionAdmissionDecision(admissionId, mapScStatusToAdmissionStatus(envelope.status()),
                            null, envelopeMapper.toTrace(ctx.clientRequestRef(), admissionId, envelope),
                            envelopeMapper.mapWarnings(envelope.warnings()), envelopeMapper.mapError(envelope.error()));
                })
                .orElseGet(() -> new InteractionAdmissionDecision(admissionId, "REJECTED_NOT_VISIBLE", null,
                        new CanonicalSubmissionTrace(ctx.clientRequestRef(), admissionId, "NOT_SUBMITTED", null, List.of()),
                        List.of(), new EibError("NOT_VISIBLE", "effective temporal act is not visible", "eib.temporal")));
    }

    private String mapScStatusToAdmissionStatus(String scStatus) {
        return switch (scStatus) {
            case "OK", "CREATED", "ACCEPTED", "CANCELLED" -> "ADMITTED";
            case "NOT_FOUND" -> "REJECTED_NOT_VISIBLE";
            case "INVALID_REQUEST", "INVALID_CANONICAL_ID", "VALIDATION_ERROR" -> "REJECTED_INVALID_REQUEST";
            case "UNSUPPORTED_PROFILE" -> "DEFERRED_UNSUPPORTED_PROFILE";
            case "DEFERRED_SC_B_REQUIRED" -> "DEFERRED_SC_B_REQUIRED";
            case "UNKNOWN_PENDING_NORMALIZATION" -> "DEFERRED_PENDING_NORMALIZATION";
            default -> "FAILED_CANONICAL_SUBMISSION";
        };
    }
}
