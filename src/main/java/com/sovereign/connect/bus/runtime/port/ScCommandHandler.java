package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.contract.ScCommandEnvelope;

@FunctionalInterface
public interface ScCommandHandler {
    void handle(ScCommandEnvelope<?> envelope);
}
