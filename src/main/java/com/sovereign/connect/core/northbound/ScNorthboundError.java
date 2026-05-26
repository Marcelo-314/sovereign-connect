package com.sovereign.connect.core.northbound;

public record ScNorthboundError(
    String code,
    String message,
    String source
) {
}
