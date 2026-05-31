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

class ScBusObservationPersistenceArchitectureTest {
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
    void noBrokerDependencyIntroduced() throws Exception {
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
        assertNoSourceContains(
                Path.of("src/main/java"),
                List.of("import org.apache.kafka.", "import com.rabbitmq.", "import org.eclipse.paho.")
        );
    }

    @Test
    void scBObservationMigrationUsesV101() {
        assertThat(Path.of("src/main/resources/db/migration/V101__sc_b_dispatch_observation_persistence.sql")).exists();
    }

    @Test
    void scBObservationMigrationCreatesOnlyScBTables() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V101__sc_b_dispatch_observation_persistence.sql"))
                .toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile("create\\s+table(?:\\s+if\\s+not\\s+exists)?\\s+([a-z0-9_]+)").matcher(sql);
        while (matcher.find()) {
            assertThat(matcher.group(1)).startsWith("sc_b_");
        }
        assertThat(sql).doesNotContain("alter table sc_c");
        assertThat(sql).doesNotContain("sc_c_outbox_entries");
        assertThat(sql).contains("foreign key (dispatch_record_id)");
        assertThat(sql).contains("references sc_b_dispatch_records(dispatch_record_id)");
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
