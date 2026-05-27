package com.sovereign.eib.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.RestClientEibScNorthboundClient;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        EibScNorthboundClientProperties.class,
        EibRefCodecProperties.class,
        EibDiagnosticAdminProperties.class
})
public class EibConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public RestClient.Builder eibRestClientBuilder(EibScNorthboundClientProperties props) {
        return RestClient.builder().baseUrl(props.baseUrl());
    }

    @Bean
    public RestClient eibRestClient(RestClient.Builder eibRestClientBuilder) {
        return eibRestClientBuilder.build();
    }

    @Bean
    public EibScNorthboundClient eibScNorthboundClient(
            RestClient eibRestClient,
            ObjectMapper objectMapper,
            EibScNorthboundClientProperties props
    ) {
        return new RestClientEibScNorthboundClient(eibRestClient, objectMapper, props);
    }

    @Bean
    public EibEffectiveRefCodec eibEffectiveRefCodec(EibRefCodecProperties props) {
        return new EibEffectiveRefCodec(props.secret());
    }
}
