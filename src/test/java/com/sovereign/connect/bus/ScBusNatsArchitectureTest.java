package com.sovereign.connect.bus;

import com.sovereign.connect.bus.runtime.nats.NatsScBusPort;
import com.sovereign.connect.bus.runtime.port.ScBusPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScBusNatsArchitectureTest {
    @Test
    void natsScBusPortImplementsScBusPort() {
        assertThat(ScBusPort.class).isAssignableFrom(NatsScBusPort.class);
    }

    @Test
    void natsPackageDoesNotImportCoreAdapterOrIntegration() throws Exception {
        assertNoSourceContains(Path.of("src/main/java/com/sovereign/connect/bus/runtime/nats"), List.of(
                "import com.sovereign.connect.core.",
                "import com.sovereign.connect." + "adapter.",
                "import com.sovereign.connect.integration."
        ));
    }

    @Test
    void natsScBusPortImportsScJsonWireCodecAndWireEnvelopeValidator() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPort.java"));

        assertThat(source)
                .contains("import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec");
        assertThat(source)
                .contains("import com.sovereign.connect.bus.runtime.serialization.WireEnvelopeValidator");
    }

    @Test
    void natsSubjectBuilderImportsScSubjectIdTokenCodecAndScCorrelationTokenCodec() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/sovereign/connect/bus/runtime/nats/NatsSubjectBuilder.java"));

        assertThat(source)
                .contains("import com.sovereign.connect.bus.runtime.serialization.ScSubjectIdTokenCodec");
        assertThat(source)
                .contains("import com.sovereign.connect.bus.runtime.serialization.ScCorrelationTokenCodec");
    }

    @Test
    void serializationPackageRemainsNatsFree() throws Exception {
        assertNoSourceContains(Path.of("src/main/java/com/sovereign/connect/bus/runtime/serialization"), List.of(
                "import io.nats.",
                "import org.testcontainers.",
                "import com.sovereign.connect.bus.runtime.nats."
        ));
    }

    @Test
    void scCProductionCodeDoesNotImportNatsOrBusRuntimeNats() throws Exception {
        assertNoSourceContains(Path.of("src/main/java/com/sovereign/connect/core"), List.of(
                "import io.nats.",
                "import com.sovereign.connect.bus.runtime.nats."
        ));
        assertNoSourceContains(Path.of("src/main/java/com/sovereign/connect/adapter"), List.of(
                "import io.nats.",
                "import com.sovereign.connect.bus.runtime.nats."
        ));
    }

    @Test
    void pomsAllowOnlyIntentionalNatsBindingAndTestFixtures() throws Exception {
        String pom = Files.readString(Path.of("pom.xml")).toLowerCase(Locale.ROOT);
        assertThat(pom).contains("io.nats");
        assertThat(pom).contains("jnats");
        assertThat(pom).contains("org.testcontainers");
        assertThat(List.of("lettuce", "vertx", "grpc-netty", "kafka-clients", "amqp-client", "mqtt"))
                .noneMatch(pom::contains);
    }

    @Test
    void jetStreamHardeningClassesDoNotImportDispatchPersistencePorts() throws Exception {
        assertNoSourceContains(
                Path.of("src/main/java/com/sovereign/connect/bus/runtime/nats"),
                List.of(
                        "import com.sovereign.connect.bus.runtime.port.DispatchStateWritePort",
                        "import com.sovereign.connect.bus.runtime.port.DispatchObservationPort",
                        "import com.sovereign.connect.bus.runtime.persistence.JdbcDispatchStateRepository",
                        "import com.sovereign.connect.bus.runtime.persistence.JdbcDispatchObservationRepository"
                )
        );
    }

    private void assertNoSourceContains(Path root, List<String> forbidden) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            List<String> violations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsForbidden(path, forbidden))
                    .map(root::relativize)
                    .map(Path::toString)
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    private boolean containsForbidden(Path path, List<String> forbidden) {
        try {
            String source = Files.readString(path).toLowerCase(Locale.ROOT);
            return forbidden.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(source::contains);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String readAll(Path root) throws Exception {
        if (!Files.exists(root)) {
            return "";
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .reduce("", String::concat);
        }
    }
}
