package com.sovereign.eib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sc.eib.diagnostic-admin")
public record EibDiagnosticAdminProperties(boolean enabled) {
}
