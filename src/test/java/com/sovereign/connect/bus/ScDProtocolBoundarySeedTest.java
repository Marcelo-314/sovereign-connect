package com.sovereign.connect.bus;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScDProtocolBoundarySeedTest {
    @Test
    void scdCommandIsOnlyATestStub() {
        ScdCommandStub stub = new ScdCommandStub("adapter-only");
        assertThat(stub.value()).isEqualTo("adapter-only");
    }

    @Test
    void productionDoesNotIntroduceScdCommandOrDiscoveryFactShapes() throws Exception {
        Path mainJava = Path.of("src/main/java");
        try (Stream<Path> paths = Files.walk(mainJava)) {
            assertThat(paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> mainJava.relativize(path).toString())
                    .filter(path -> path.endsWith("ScdCommand.java")
                            || path.endsWith("DeviceDiscoveredFact.java")
                            || path.endsWith("EndpointDiscoveredFact.java")
                            || path.endsWith("DeviceStateObservedFact.java")
                            || path.endsWith("EndpointStateObservedFact.java")
                            || path.endsWith("DeviceHealthObservedFact.java")
                            || path.endsWith("EndpointHealthObservedFact.java"))
                    .toList()).isEmpty();
        }
    }
}
