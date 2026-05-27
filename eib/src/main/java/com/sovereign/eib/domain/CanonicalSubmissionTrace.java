package com.sovereign.eib.domain;

import java.util.List;

public record CanonicalSubmissionTrace(
        String clientRequestRef,
        String admissionId,
        String scNorthboundStatus,
        EibError error,
        List<EibWarning> warnings
) {
}
