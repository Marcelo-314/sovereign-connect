package com.sovereign.connect.bus.runtime.validation;

import com.sovereign.connect.bus.contract.ScMessageMetadata;

public class CorrelationValidationService {
    public void validate(ScMessageMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (metadata.messageId() == null) {
            throw new IllegalArgumentException("messageId is required");
        }
        if (metadata.correlationId() == null) {
            throw new IllegalArgumentException("correlationId is required");
        }
        boolean root = metadata.messageId().equals(metadata.correlationId());
        if (root && metadata.causationId() != null) {
            throw new IllegalArgumentException("root message must not have causationId");
        }
        if (!root && metadata.causationId() == null) {
            throw new IllegalArgumentException("non-root message requires causationId");
        }
    }
}
