package com.sovereign.connect.adapter.northbound.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sc.northbound.http")
public record ScNorthboundHttpProperties(
    boolean enabled,
    String basePath,
    String exposureProfile
) {}
