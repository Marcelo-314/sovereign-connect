package com.sovereign.eib.northbound;

import com.sovereign.eib.northbound.dto.ScEnvelope;

public class EibSemanticScCException extends RuntimeException {

    private final ScEnvelope<?> envelope;

    public EibSemanticScCException(ScEnvelope<?> envelope) {
        super("semantic upstream response");
        this.envelope = envelope;
    }

    @SuppressWarnings("unchecked")
    public <T> ScEnvelope<T> getEnvelope() {
        return (ScEnvelope<T>) envelope;
    }
}
