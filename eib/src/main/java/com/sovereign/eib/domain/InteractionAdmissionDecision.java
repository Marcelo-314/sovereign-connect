package com.sovereign.eib.domain;

import java.util.List;

public record InteractionAdmissionDecision(
        String admissionId,
        String status,
        String effectiveRef,
        CanonicalSubmissionTrace canonicalTrace,
        List<EibWarning> warnings,
        EibError error
) {
}
