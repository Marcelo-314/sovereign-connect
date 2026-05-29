package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.contract.ScResponseEnvelope;

@FunctionalInterface
public interface ScResponseHandler {
    void handle(ScResponseEnvelope<?> envelope);
}
