package com.sovereign.connect.bus.runtime.inmemory;

import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.dispatch.model.Failed;
import com.sovereign.connect.bus.runtime.dispatch.model.NoHandler;
import com.sovereign.connect.bus.runtime.port.ScBusPort;
import com.sovereign.connect.bus.runtime.port.ScCommandHandler;
import com.sovereign.connect.bus.runtime.port.ScEventHandler;
import com.sovereign.connect.bus.runtime.port.ScResponseHandler;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryScBusPort implements ScBusPort {
    private final Map<String, ScCommandHandler> commandHandlers = new ConcurrentHashMap<>();
    private final Map<String, ScEventHandler> eventHandlers = new ConcurrentHashMap<>();
    private final Map<String, ScResponseHandler> responseHandlers = new ConcurrentHashMap<>();
    private final EnvelopeValidationService envelopeValidationService = new EnvelopeValidationService();

    @Override
    public DispatchOutcome publishCommand(ScCommandEnvelope<?> envelope) {
        UUID dispatchRecordId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        try {
            envelopeValidationService.validateCommand(envelope);
            String topic = envelope.routingKey().topic();
            ScCommandHandler handler = commandHandlers.get(topic);
            if (handler == null) {
                return new NoHandler(dispatchRecordId, attemptId, topic, "no command handler registered");
            }
            handler.handle(envelope);
            return new Dispatched(dispatchRecordId, attemptId, topic, envelope.routingKey().partitionKey());
        } catch (RuntimeException ex) {
            return failed(dispatchRecordId, attemptId, ex);
        }
    }

    @Override
    public DispatchOutcome publishEvent(ScEventEnvelope<?> envelope) {
        UUID dispatchRecordId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        try {
            envelopeValidationService.validateEvent(envelope);
            String topic = envelope.routingKey().topic();
            ScEventHandler handler = eventHandlers.get(topic);
            if (handler == null) {
                return new NoHandler(dispatchRecordId, attemptId, topic, "no event handler registered");
            }
            handler.handle(envelope);
            return new Dispatched(dispatchRecordId, attemptId, topic, envelope.routingKey().partitionKey());
        } catch (RuntimeException ex) {
            return failed(dispatchRecordId, attemptId, ex);
        }
    }

    @Override
    public DispatchOutcome publishResponse(ScResponseEnvelope<?> envelope) {
        UUID dispatchRecordId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        try {
            envelopeValidationService.validateResponse(envelope);
            String topic = envelope.routingKey().topic();
            ScResponseHandler handler = responseHandlers.get(topic);
            if (handler == null) {
                return new NoHandler(dispatchRecordId, attemptId, topic, "no response handler registered");
            }
            handler.handle(envelope);
            return new Dispatched(dispatchRecordId, attemptId, topic, envelope.routingKey().partitionKey());
        } catch (RuntimeException ex) {
            return failed(dispatchRecordId, attemptId, ex);
        }
    }

    @Override
    public void registerCommandHandler(String topicOrRoute, ScCommandHandler handler) {
        commandHandlers.put(requireTopic(topicOrRoute), requireHandler(handler));
    }

    @Override
    public void registerEventHandler(String topicOrRoute, ScEventHandler handler) {
        eventHandlers.put(requireTopic(topicOrRoute), requireHandler(handler));
    }

    @Override
    public void registerResponseHandler(String topicOrRoute, ScResponseHandler handler) {
        responseHandlers.put(requireTopic(topicOrRoute), requireHandler(handler));
    }

    private Failed failed(UUID dispatchRecordId, UUID attemptId, RuntimeException ex) {
        return new Failed(dispatchRecordId, attemptId, "SC_B_DISPATCH_FAILED", ex.getMessage(), true);
    }

    private String requireTopic(String topicOrRoute) {
        if (topicOrRoute == null || topicOrRoute.isBlank()) {
            throw new IllegalArgumentException("topicOrRoute is required");
        }
        return topicOrRoute;
    }

    private <T> T requireHandler(T handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler is required");
        }
        return handler;
    }
}
