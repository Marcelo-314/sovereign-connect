package com.sovereign.connect.core.northbound.temporal;

public record NorthboundTemporalActFilter(
    Mode mode,
    Integer maxResults
) {
    public enum Mode {
        ACTIVE,
        TERMINAL,
        MISFIRED
    }
}
