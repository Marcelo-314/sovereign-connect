package com.sovereign.connect.bus.contract.scd;

import com.fasterxml.jackson.databind.JsonNode;

public record ScdCommand(
        String targetRef,
        String endpointRef,
        String operationKind,
        String capabilityRef,
        JsonNode params
) {}
