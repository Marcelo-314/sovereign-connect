package com.sovereign.connect.bus.runtime.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;

import java.io.IOException;
import java.util.Objects;

public final class ScJsonWireCodec {

    private final ObjectMapper mapper;

    public ScJsonWireCodec() {
        this.mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ScJsonWireCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper is required");
    }

    public byte[] marshal(ScJsonWireEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope is required");
        try {
            return mapper.writeValueAsBytes(envelope);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to marshal wire envelope", e);
        }
    }

    public ScJsonWireEnvelope unmarshal(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes is required");
        try {
            return mapper.readValue(bytes, ScJsonWireEnvelope.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to unmarshal wire envelope", e);
        }
    }

    public <T> ScJsonWireEnvelope toWireEnvelope(
            ScCommandEnvelope<T> envelope,
            String payloadType,
            String payloadSchemaVersion) {
        Objects.requireNonNull(envelope, "envelope is required");
        JsonNode payloadNode = mapper.valueToTree(envelope.payload());
        return new ScJsonWireEnvelope(
            ScWireEnvelopeKind.COMMAND,
            payloadType,
            payloadSchemaVersion,
            envelope.metadata(),
            envelope.routingKey(),
            null,
            payloadNode
        );
    }

    public <T> ScJsonWireEnvelope toWireEnvelope(
            ScEventEnvelope<T> envelope,
            String payloadType,
            String payloadSchemaVersion) {
        Objects.requireNonNull(envelope, "envelope is required");
        JsonNode payloadNode = mapper.valueToTree(envelope.payload());
        return new ScJsonWireEnvelope(
            ScWireEnvelopeKind.EVENT,
            payloadType,
            payloadSchemaVersion,
            envelope.metadata(),
            envelope.routingKey(),
            null,
            payloadNode
        );
    }

    public <T> ScJsonWireEnvelope toWireEnvelope(
            ScResponseEnvelope<T> envelope,
            String payloadType,
            String payloadSchemaVersion) {
        Objects.requireNonNull(envelope, "envelope is required");
        JsonNode payloadNode = mapper.valueToTree(envelope.payload());
        return new ScJsonWireEnvelope(
            ScWireEnvelopeKind.RESPONSE,
            payloadType,
            payloadSchemaVersion,
            envelope.metadata(),
            envelope.routingKey(),
            envelope.responseMetadata(),
            payloadNode
        );
    }
}
