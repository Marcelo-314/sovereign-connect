package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.contract.ScEventEnvelope;

@FunctionalInterface
public interface ScEventHandler {
    void handle(ScEventEnvelope<?> envelope);
}
