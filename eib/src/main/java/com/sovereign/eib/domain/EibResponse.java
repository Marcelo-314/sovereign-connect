package com.sovereign.eib.domain;

import java.util.List;

public record EibResponse<T>(
        String status,
        T payload,
        List<EibWarning> warnings,
        EibError error,
        CanonicalTraceSummary canonicalTrace
) {
}
