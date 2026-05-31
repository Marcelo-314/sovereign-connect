package com.sovereign.connect.bus;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScBusArchitectureTest {
    @Test
    void busContractDoesNotImportCoreOrRuntime() throws Exception {
        Path root = Path.of("src/main/java/com/sovereign/connect/bus/contract");
        assertNoSourceContains(root, List.of("import com.sovereign.connect.core.", "import com.sovereign.connect.bus.runtime."));
    }

    @Test
    void busRuntimeDoesNotImportCore() throws Exception {
        Path root = Path.of("src/main/java/com/sovereign/connect/bus/runtime");
        assertNoSourceContains(root, List.of("import com.sovereign.connect.core."));
    }

    @Test
    void coreDoesNotImportBusRuntime() throws Exception {
        Path root = Path.of("src/main/java/com/sovereign/connect/core");
        assertNoSourceContains(root, List.of("import com.sovereign.connect.bus.runtime."));
    }

    @Test
    void busDoesNotImportPhysicalBrokerRuntimeApis() throws Exception {
        Path root = Path.of("src/main/java/com/sovereign/connect/bus");
        assertNoSourceContains(root, List.of(
                "import io.vertx.", "import io.grpc.", "import redis.clients.",
                "import io.lettuce.", "import org.apache.kafka.", "import com.rabbitmq.",
                "import org.eclipse.paho.", "import jakarta.websocket.", "import javax.websocket."
        ));
    }

    @Test
    void productionScdCommandReferenceRecordLivesUnderBusContract() {
        assertThat(Path.of("src/main/java/com/sovereign/connect/bus/contract/scd/ScdCommand.java")).exists();
    }

    @Test
    void pomsAllowOnlyIntentionalNatsBindingDependency() throws Exception {
        List<String> forbidden = List.of("jetstream", "lettuce", "vertx", "grpc-netty", "kafka-clients", "amqp-client", "mqtt");
        for (Path pom : List.of(Path.of("pom.xml"), Path.of("eib/pom.xml"))) {
            if (Files.exists(pom)) {
                String xml = Files.readString(pom).toLowerCase(Locale.ROOT);
                if (pom.equals(Path.of("pom.xml"))) {
                    assertThat(xml).contains("io.nats");
                }
                assertThat(forbidden).noneMatch(xml::contains);
            }
        }
    }

    @Test
    void dispatchStateIsNotOutboxEntryStatusAlias() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/sovereign/connect/bus/runtime/dispatch/model/DispatchState.java"));
        assertThat(source).contains("public enum DispatchState");
        assertThat(source).doesNotContain("OutboxEntryStatus");
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
}
