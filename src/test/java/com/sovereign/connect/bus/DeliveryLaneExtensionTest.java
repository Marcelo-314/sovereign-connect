package com.sovereign.connect.bus;

import com.sovereign.connect.core.scledger.model.DeliveryLane;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryLaneExtensionTest {
    @Test
    void deliveryLanePreservesExistingValuesAndAddsEventAndResponse() {
        assertThat(DeliveryLane.values())
                .containsExactly(DeliveryLane.SIGNAL, DeliveryLane.COMMAND, DeliveryLane.EVENT, DeliveryLane.RESPONSE);
    }

    @Test
    void existingSignalAndCommandNamesRemainStable() {
        assertThat(DeliveryLane.SIGNAL.name()).isEqualTo("SIGNAL");
        assertThat(DeliveryLane.COMMAND.name()).isEqualTo("COMMAND");
    }
}
