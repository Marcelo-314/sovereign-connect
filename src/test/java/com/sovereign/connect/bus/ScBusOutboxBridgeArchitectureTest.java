package com.sovereign.connect.bus;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScBusOutboxBridgeArchitectureTest {
    @Test
    void busDoesNotImportCoreScledger() throws Exception {
        assertNoSourceContains(
                Path.of("src/main/java/com/sovereign/connect/bus"),
                List.of("import com.sovereign.connect.core.scledger.")
        );
    }

    @Test
    void coreDoesNotImportBusRuntime() throws Exception {
        assertNoSourceContains(
                Path.of("src/main/java/com/sovereign/connect/core"),
                List.of("import com.sovereign.connect.bus.runtime.")
        );
    }

    @Test
    void integrationScledgerdispatchIsOnlyPackageImportingBothScledgerAndBus() throws Exception {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            List<String> violations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> importsBothScledgerAndBus(path))
                    .filter(path -> !path.toString().contains("integration\\scledgerdispatch")
                            && !path.toString().contains("integration/scledgerdispatch"))
                    .map(Path::toString)
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void integrationScledgerdispatchDoesNotImportTopologyTemporalOrAdapters() throws Exception {
        assertNoSourceContains(
                Path.of("src/main/java/com/sovereign/connect/integration/scledgerdispatch"),
                List.of(
                        "import com.sovereign.connect.core.topology.",
                        "import com.sovereign.connect.core.temporal.",
                        "import com.sovereign.connect." + "adapter."
                )
        );
    }

    @Test
    void noBrokerDependencyIntroduced() throws Exception {
        List<String> forbidden = List.of("io.nats", "jetstream", "lettuce", "vertx", "grpc-netty", "kafka-clients", "amqp-client", "mqtt");
        for (Path pom : List.of(Path.of("pom.xml"), Path.of("eib/pom.xml"))) {
            if (Files.exists(pom)) {
                String xml = Files.readString(pom).toLowerCase(Locale.ROOT);
                assertThat(forbidden).noneMatch(xml::contains);
            }
        }
        assertNoSourceContains(
                Path.of("src/main/java"),
                List.of("import io.nats.", "import org.apache.kafka.", "import com.rabbitmq.", "import org.eclipse.paho.")
        );
    }

    @Test
    void noProductionScdCommandIntroduced() throws Exception {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            assertThat(paths.filter(path -> path.getFileName().toString().equals("ScdCommand.java")).toList()).isEmpty();
        }
    }

    @Test
    void dispatchObservationPersistenceStillDeferred() throws Exception {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            assertThat(paths
                    .filter(path -> path.getFileName().toString().contains("DispatchObservation"))
                    .map(Path::toString)
                    .filter(path -> path.contains("persistence"))
                    .toList()).isEmpty();
        }
    }

    @Test
    void h2DoesNotAddScBMigrations() throws Exception {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/db/migration"))) {
            List<String> scBMigrations = paths
                    .filter(path -> path.getFileName().toString().contains("sc_b"))
                    .map(path -> path.getFileName().toString())
                    .toList();
            assertThat(scBMigrations).containsExactly("V100__sc_b_dispatch_state_persistence.sql");
        }
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

    private boolean importsBothScledgerAndBus(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("import com.sovereign.connect.core.scledger.")
                    && Pattern.compile("import\\s+com\\.sovereign\\.connect\\.bus\\.(contract|runtime)\\.").matcher(source).find();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
