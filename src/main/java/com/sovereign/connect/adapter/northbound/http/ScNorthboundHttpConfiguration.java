package com.sovereign.connect.adapter.northbound.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScNorthboundHttpProperties.class)
public class ScNorthboundHttpConfiguration {}
