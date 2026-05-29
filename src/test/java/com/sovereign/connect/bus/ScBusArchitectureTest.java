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
                "import io.nats.", "import io.vertx.", "import io.grpc.", "import redis.clients.",
                "import io.lettuce.", "import org.apache.kafka.", "import com.rabbitmq.",
                "import org.eclipse.paho.", "import jakarta.websocket.", "import javax.websocket."
        ));
    }

    @Test
    void productionHasNoScdCommandClass() throws Exception {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            assertThat(paths.filter(path -> path.getFileName().toString().equals("ScdCommand.java")).toList()).isEmpty();
        }
    }

    @Test
    void pomsDoNotIntroducePhysicalBusBindingDependency() throws Exception {
        List<String> forbidden = List.of("io.nats", "jetstream", "lettuce", "vertx", "grpc-netty", "kafka-clients", "amqp-client", "mqtt");
        for (Path pom : List.of(Path.of("pom.xml"), Path.of("eib/pom.xml"))) {
            if (Files.exists(pom)) {
                String xml = Files.readString(pom).toLowerCase(Locale.ROOT);
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
