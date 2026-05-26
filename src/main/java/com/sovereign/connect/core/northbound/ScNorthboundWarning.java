package com.sovereign.connect.core.northbound;

public record ScNorthboundWarning(
    String code,
    String message,
    String source
) {
}
