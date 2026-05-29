package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;

public interface ScBusPort {
    DispatchOutcome publishCommand(ScCommandEnvelope<?> envelope);
    DispatchOutcome publishEvent(ScEventEnvelope<?> envelope);
    DispatchOutcome publishResponse(ScResponseEnvelope<?> envelope);

    void registerCommandHandler(String topicOrRoute, ScCommandHandler handler);
    void registerEventHandler(String topicOrRoute, ScEventHandler handler);
    void registerResponseHandler(String topicOrRoute, ScResponseHandler handler);
}
