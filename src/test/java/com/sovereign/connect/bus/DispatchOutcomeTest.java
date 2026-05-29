package com.sovereign.connect.bus;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.dispatch.model.Failed;
import com.sovereign.connect.bus.runtime.dispatch.model.NoHandler;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchOutcomeTest {
    @Test
    void dispatchOutcomeIsSealedWithExpectedPermits() {
        assertThat(DispatchOutcome.class.isSealed()).isTrue();
        assertThat(Arrays.stream(DispatchOutcome.class.getPermittedSubclasses()).map(Class::getSimpleName))
                .containsExactlyInAnyOrder("Dispatched", "Failed", "NoHandler");
    }

    @Test
    void dispatchedIsTechnicalDeliveryOnly() {
        Dispatched dispatched = new Dispatched(UUID.randomUUID(), UUID.randomUUID(), "topic", "partition");
        assertThat(dispatched).isInstanceOf(DispatchOutcome.class);
        assertThat(dispatched.toString()).doesNotContain("success");
    }

    @Test
    void failedAndNoHandlerCarryTechnicalDiagnostics() {
        Failed failed = new Failed(UUID.randomUUID(), UUID.randomUUID(), "BROKERLESS", "handler failed", true);
        NoHandler noHandler = new NoHandler(UUID.randomUUID(), UUID.randomUUID(), "topic", "missing");
        assertThat(failed.retryable()).isTrue();
        assertThat(noHandler.sanitizedReason()).isEqualTo("missing");
    }

    @Test
    void dispatchStateValuesAreCompleteAndDistinctFromDomainSemantics() {
        assertThat(DispatchState.values()).extracting(Enum::name).containsExactly(
                "PENDING", "CLAIMED", "DISPATCHING", "DISPATCHED", "DELIVERY_FAILED",
                "RETRY_SCHEDULED", "EXHAUSTED", "CANCELLED_BY_SUPERSEDE"
        );
        assertThat(Arrays.stream(DispatchState.values()).map(Enum::name))
                .doesNotContain("SUCCESS", "DOMAIN_FAILURE", "ADAPTER_ACCEPTED");
    }
}
