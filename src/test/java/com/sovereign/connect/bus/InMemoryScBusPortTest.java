package com.sovereign.connect.bus;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.dispatch.model.Failed;
import com.sovereign.connect.bus.runtime.dispatch.model.NoHandler;
import com.sovereign.connect.bus.runtime.inmemory.InMemoryScBusPort;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryScBusPortTest {
    @Test
    void dispatchesCommandToRegisteredTopicSynchronously() {
        InMemoryScBusPort port = new InMemoryScBusPort();
        AtomicReference<Object> handled = new AtomicReference<>();
        port.registerCommandHandler(ScBusTestSupport.TOPIC, envelope -> handled.set(envelope.payload()));
        assertThat(port.publishCommand(ScBusTestSupport.commandEnvelope())).isInstanceOf(Dispatched.class);
        assertThat(handled.get()).isEqualTo(new TestCommandPayload("on"));
    }

    @Test
    void dispatchesEventAndResponseToRegisteredTopics() {
        InMemoryScBusPort port = new InMemoryScBusPort();
        port.registerEventHandler(ScBusTestSupport.TOPIC, envelope -> { });
        port.registerResponseHandler(ScBusTestSupport.TOPIC, envelope -> { });
        assertThat(port.publishEvent(ScBusTestSupport.eventEnvelope())).isInstanceOf(Dispatched.class);
        assertThat(port.publishResponse(ScBusTestSupport.responseEnvelope())).isInstanceOf(Dispatched.class);
    }

    @Test
    void returnsNoHandlerWhenTopicIsUnregistered() {
        assertThat(new InMemoryScBusPort().publishCommand(ScBusTestSupport.commandEnvelope())).isInstanceOf(NoHandler.class);
    }

    @Test
    void returnsFailedForNullEnvelopeNullPayloadAndLaneMismatch() {
        InMemoryScBusPort port = new InMemoryScBusPort();
        assertThat(port.publishCommand(null)).isInstanceOf(Failed.class);
        assertThat(port.publishCommand(new ScCommandEnvelope<>(ScBusTestSupport.rootMetadata(), null, ScBusTestSupport.routing(ScBusLane.COMMAND))))
                .isInstanceOf(Failed.class);
        assertThat(port.publishCommand(new ScCommandEnvelope<>(ScBusTestSupport.rootMetadata(), new TestCommandPayload("x"), ScBusTestSupport.routing(ScBusLane.EVENT))))
                .isInstanceOf(Failed.class);
    }

    @Test
    void returnsFailedWhenHandlerThrows() {
        InMemoryScBusPort port = new InMemoryScBusPort();
        port.registerCommandHandler(ScBusTestSupport.TOPIC, envelope -> {
            throw new IllegalStateException("boom");
        });
        assertThat(port.publishCommand(ScBusTestSupport.commandEnvelope())).isInstanceOf(Failed.class);
    }

    @Test
    void rejectsBlankTopicAndNullHandlerOnRegistration() {
        InMemoryScBusPort port = new InMemoryScBusPort();
        assertThatThrownBy(() -> port.registerCommandHandler(" ", envelope -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> port.registerCommandHandler(ScBusTestSupport.TOPIC, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
