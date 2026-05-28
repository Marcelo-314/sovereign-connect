package com.sovereign.eib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.eib.config.EibScNorthboundClientProperties;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.northbound.RestClientEibScNorthboundClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EibScNorthboundClientTimeoutTest {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().findAndRegisterModules();

    private RestClientEibScNorthboundClient clientWithTimeout(long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestClient restClient = RestClient.builder()
                .baseUrl("http://192.0.2.1/sc/v1")
                .requestFactory(factory)
                .build();
        return new RestClientEibScNorthboundClient(
                restClient, MAPPER,
                new EibScNorthboundClientProperties("http://192.0.2.1/sc/v1", timeoutMs));
    }

    @Test
    void connectTimeoutProducesEibUpstreamUnavailableException() {
        var client = clientWithTimeout(1);

        assertThatThrownBy(() -> client.getTopologySnapshot("habitat.test"))
                .isInstanceOf(EibUpstreamUnavailableException.class);
    }

    @Test
    void listTemporalActsTimeoutProducesEibUpstreamUnavailableException() {
        var client = clientWithTimeout(1);

        assertThatThrownBy(() -> client.listTemporalActs("habitat.test", "ACTIVE", 10))
                .isInstanceOf(EibUpstreamUnavailableException.class);
    }
}
