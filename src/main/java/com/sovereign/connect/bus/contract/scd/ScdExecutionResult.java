package com.sovereign.connect.bus.contract.scd;

import com.fasterxml.jackson.databind.JsonNode;

public record ScdExecutionResult(
        String status,
        String providerCorrelationRef,
        JsonNode observedState,
        boolean retryable,
        String sanitizedReason
) {}
