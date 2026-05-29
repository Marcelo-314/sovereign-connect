package com.sovereign.connect.bus.runtime.validation;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;

public class EnvelopeValidationService {
    private final CorrelationValidationService correlationValidationService = new CorrelationValidationService();
    private final RoutingKeyValidationService routingKeyValidationService = new RoutingKeyValidationService();

    public void validateCommand(ScCommandEnvelope<?> envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope is required");
        }
        validateParts(envelope.metadata(), envelope.payload(), envelope.routingKey());
        requireLane(envelope.routingKey().lane(), ScBusLane.COMMAND);
    }

    public void validateEvent(ScEventEnvelope<?> envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope is required");
        }
        validateParts(envelope.metadata(), envelope.payload(), envelope.routingKey());
        requireLane(envelope.routingKey().lane(), ScBusLane.EVENT);
    }

    public void validateResponse(ScResponseEnvelope<?> envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope is required");
        }
        validateParts(envelope.metadata(), envelope.payload(), envelope.routingKey());
        requireLane(envelope.routingKey().lane(), ScBusLane.RESPONSE);
    }

    private void validateParts(Object metadata, Object payload, com.sovereign.connect.bus.contract.ScRoutingKey routingKey) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        correlationValidationService.validate((com.sovereign.connect.bus.contract.ScMessageMetadata) metadata);
        routingKeyValidationService.validate(routingKey);
    }

    private void requireLane(ScBusLane actual, ScBusLane expected) {
        if (actual != expected) {
            throw new IllegalArgumentException("envelope family does not match lane");
        }
    }
}
