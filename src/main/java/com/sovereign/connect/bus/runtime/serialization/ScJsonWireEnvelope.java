package com.sovereign.connect.bus.runtime.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;

public record ScJsonWireEnvelope(
    ScWireEnvelopeKind envelopeKind,
    String payloadType,
    String payloadSchemaVersion,
    ScMessageMetadata metadata,
    ScRoutingKey routingKey,
    ScResponseMetadata responseMetadata,
    JsonNode payload
) {}
