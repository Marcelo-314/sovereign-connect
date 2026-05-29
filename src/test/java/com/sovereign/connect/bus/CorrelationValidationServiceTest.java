package com.sovereign.connect.bus;

import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.runtime.validation.CorrelationValidationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationValidationServiceTest {
    private final CorrelationValidationService service = new CorrelationValidationService();

    @Test
    void rootMessageRequiresCorrelationToEqualMessageIdAndNoCausation() {
        UUID messageId = UUID.randomUUID();
        assertThatNoException().isThrownBy(() -> service.validate(new ScMessageMetadata(messageId, Instant.now(), messageId, null, null)));
    }

    @Test
    void rootMessageRejectsCausationId() {
        UUID messageId = UUID.randomUUID();
        assertThatThrownBy(() -> service.validate(new ScMessageMetadata(messageId, Instant.now(), messageId, UUID.randomUUID(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonRootRequiresCorrelationAndCausation() {
        assertThatNoException().isThrownBy(() -> service.validate(ScBusTestSupport.childMetadata()));
        assertThatThrownBy(() -> service.validate(new ScMessageMetadata(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullAndMissingIdsAreRejected() {
        assertThatThrownBy(() -> service.validate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new ScMessageMetadata(null, Instant.now(), UUID.randomUUID(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new ScMessageMetadata(UUID.randomUUID(), Instant.now(), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
