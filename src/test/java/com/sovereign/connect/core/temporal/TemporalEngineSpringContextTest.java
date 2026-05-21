package com.sovereign.connect.core.temporal;

import com.sovereign.connect.core.temporal.engine.TemporalEngineLifecycle;
import com.sovereign.connect.core.temporal.engine.TemporalEngineStatus;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TemporalEngineSpringContextTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:sqlite:target/temporal-context-" + UUID.randomUUID() + ".sqlite");
        registry.add("sovereign.temporal.engine.habitat-id", () -> "habitat-001");
        registry.add("sovereign.temporal.engine.polling-interval-ms", () -> "10000");
        registry.add("sovereign.temporal.engine.single-node-guard-ttl-ms", () -> "30000");
        registry.add("sovereign.temporal.engine.shutdown-timeout-ms", () -> "1000");
    }

    @Autowired
    TemporalEngineLifecycle lifecycle;

    @Autowired
    TemporalEngineHealth health;

    @Test
    void temporalEngineLifecycleBeanIsPresentAndRecoveryGateCompleted() {
        assertThat(lifecycle).isNotNull();
        assertThat(lifecycle.getPhase()).isEqualTo(Integer.MAX_VALUE - 100);
        assertThat(health.status()).isIn(TemporalEngineStatus.RUNNING, TemporalEngineStatus.DEGRADED);
    }
}
