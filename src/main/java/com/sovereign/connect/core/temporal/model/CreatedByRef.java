package com.sovereign.connect.core.temporal.model;

import java.util.Objects;

public record CreatedByRef(String value) {

    public CreatedByRef {
        Objects.requireNonNull(value, "value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
