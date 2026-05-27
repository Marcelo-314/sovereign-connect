package com.sovereign.eib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sc.eib.northbound")
public record EibScNorthboundClientProperties(String baseUrl, long timeoutMs) {
}
