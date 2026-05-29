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

class ScBusHardeningArchitectureTest {
    @Test
    void busRuntimePersistenceDoesNotImportCore() throws Exception {
        assertNoSourceContains(
                Path.of("src/main/java/com/sovereign/connect/bus/runtime/persistence"),
                List.of("import com.sovereign.connect.core.")
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
    void busDoesNotImportBrokerApis() throws Exception {
        assertNoSourceContains(
                Path.of("src/main/java/com/sovereign/connect/bus"),
                List.of(
                        "import io.nats.", "import io.vertx.", "import io.grpc.", "import redis.clients.",
                        "import io.lettuce.", "import org.apache.kafka.", "import com.rabbitmq.",
                        "import org.eclipse.paho.", "import jakarta.websocket.", "import javax.websocket."
                )
        );
    }

    @Test
    void pomsDoNotIntroducePhysicalBusBindingDependencies() throws Exception {
        List<String> forbidden = List.of("io.nats", "jetstream", "lettuce", "vertx", "grpc-netty", "kafka-clients", "amqp-client", "mqtt");
        for (Path pom : List.of(Path.of("pom.xml"), Path.of("eib/pom.xml"))) {
            if (Files.exists(pom)) {
                String xml = Files.readString(pom).toLowerCase(Locale.ROOT);
                assertThat(forbidden).noneMatch(xml::contains);
            }
        }
    }

    @Test
    void scBMigrationsUseV100OrHigherAndNotScCOwnedRange() throws Exception {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/db/migration"))) {
            List<String> badNames = paths
                    .filter(path -> path.getFileName().toString().contains("sc_b"))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> {
                        Matcher matcher = Pattern.compile("^V(\\d+)__.*\\.sql$").matcher(name);
                        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 100;
                    })
                    .toList();
            assertThat(badNames).isEmpty();
        }
    }

    @Test
    void scBMigrationCreatesOnlyScBTables() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V100__sc_b_dispatch_state_persistence.sql");
        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile("create\\s+table(?:\\s+if\\s+not\\s+exists)?\\s+([a-z0-9_]+)").matcher(sql);
        while (matcher.find()) {
            assertThat(matcher.group(1)).startsWith("sc_b_");
        }
        assertThat(sql).doesNotContain("alter table sc_c");
    }

    @Test
    void h1DispatchStatePersistenceDoesNotImportOutboxBridgeSurfaces() throws Exception {
        assertNoSourceContains(
                Path.of("src/main/java/com/sovereign/connect/bus/runtime/persistence"),
                List.of("ScOutboxDispatchReadPort", "integration.scledgerdispatch")
        );
    }

    @Test
    void busRuntimePersistenceDoesNotImportOutboxTypes() throws Exception {
        assertNoSourceContains(
                Path.of("src/main/java/com/sovereign/connect/bus/runtime/persistence"),
                List.of("OutboxEntry", "OutboxEntryStatus", "LedgerEntry")
        );
    }

    @Test
    void busTestsDoNotDependOnAdapterOwnedInfrastructure() throws Exception {
        assertNoSourceContains(
                Path.of("src/test/java/com/sovereign/connect/bus"),
                List.of("com.sovereign.connect." + "adapter", "PerConnection" + "PragmaDataSource")
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
}
