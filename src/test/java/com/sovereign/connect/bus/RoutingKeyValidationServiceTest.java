package com.sovereign.connect.bus;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.runtime.validation.RoutingKeyValidationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingKeyValidationServiceTest {
    private final RoutingKeyValidationService service = new RoutingKeyValidationService();

    @Test
    void treatsDeviceAndEndpointIdsAsOpaqueStrings() {
        ScRoutingKey routingKey = new ScRoutingKey(ScBusLane.COMMAND, "device.1/endpoint:main", "topic", "habitat", "adapter", "device.1/with:chars", "device.1/endpoint:main");
        assertThatNoException().isThrownBy(() -> service.validate(routingKey));
    }

    @Test
    void rejectsEndpointSentinels() {
        assertThatThrownBy(() -> service.validate(new ScRoutingKey(ScBusLane.COMMAND, "p", "topic", null, null, null, "")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new ScRoutingKey(ScBusLane.COMMAND, "none", "topic", null, null, null, "none")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new ScRoutingKey(ScBusLane.COMMAND, "default", "topic", null, null, null, "default")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresEndpointToMatchPartitionWhenPresent() {
        assertThatThrownBy(() -> service.validate(new ScRoutingKey(ScBusLane.COMMAND, "device", "topic", null, null, null, "endpoint")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesHabitatIdAndAllowsNoEndpoint() {
        ScRoutingKey routingKey = new ScRoutingKey(ScBusLane.EVENT, "device", "topic", "habitat-x", null, "device", null);
        service.validate(routingKey);
        assertThat(routingKey.habitatId()).isEqualTo("habitat-x");
    }

    @Test
    void rejectsMissingLaneTopicAndPartition() {
        assertThatThrownBy(() -> service.validate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new ScRoutingKey(null, "p", "topic", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new ScRoutingKey(ScBusLane.COMMAND, " ", "topic", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new ScRoutingKey(ScBusLane.COMMAND, "p", " ", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
