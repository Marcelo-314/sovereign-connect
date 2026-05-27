package com.sovereign.eib.service;

import com.sovereign.eib.domain.CanonicalSubmissionTrace;
import com.sovereign.eib.domain.EibError;
import com.sovereign.eib.domain.EibWarning;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.northbound.dto.ScErrorDto;
import com.sovereign.eib.northbound.dto.ScWarningDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EibCanonicalEnvelopeMapper {

    public String extractScStatus(ScEnvelope<?> envelope) {
        return envelope == null ? "UPSTREAM_UNAVAILABLE" : envelope.status();
    }

    public boolean isSuccess(ScEnvelope<?> envelope) {
        return switch (extractScStatus(envelope)) {
            case "OK", "CREATED", "ACCEPTED", "CANCELLED" -> true;
            default -> false;
        };
    }

    public CanonicalSubmissionTrace toTrace(String clientRequestRef, String admissionId, ScEnvelope<?> envelope) {
        return new CanonicalSubmissionTrace(
                clientRequestRef,
                admissionId,
                extractScStatus(envelope),
                mapError(envelope == null ? null : envelope.error()),
                mapWarnings(envelope == null ? null : envelope.warnings())
        );
    }

    public EibError mapError(ScErrorDto error) {
        return error == null ? null : new EibError(error.code(), error.message(), error.source());
    }

    public List<EibWarning> mapWarnings(List<ScWarningDto> warnings) {
        if (warnings == null) {
            return List.of();
        }
        return warnings.stream()
                .map(warning -> new EibWarning(warning.code(), warning.message(), warning.source()))
                .toList();
    }
}
