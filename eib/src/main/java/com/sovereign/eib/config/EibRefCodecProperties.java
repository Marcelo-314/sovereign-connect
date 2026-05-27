package com.sovereign.eib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sc.eib.ref-codec")
public record EibRefCodecProperties(String secret) {
}
