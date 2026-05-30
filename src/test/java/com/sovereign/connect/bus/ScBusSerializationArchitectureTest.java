package com.sovereign.connect.bus;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScBusSerializationArchitectureTest {

    @Test
    void serializationPackageDoesNotImportCore() throws Exception {
        assertThat(readAll("src/main/java/com/sovereign/connect/bus/runtime/serialization"))
                .doesNotContain("com.sovereign.connect.core");
    }

    @Test
    void serializationPackageDoesNotImportAdapter() throws Exception {
        assertThat(readAll("src/main/java/com/sovereign/connect/bus/runtime/serialization"))
                .doesNotContain("com.sovereign.connect." + "adapter");
    }

    @Test
    void serializationPackageDoesNotImportNatsOrTestcontainers() throws Exception {
        String sources = readAll("src/main/java/com/sovereign/connect/bus/runtime/serialization");
        assertThat(sources).doesNotContain("io.nats");
        assertThat(sources).doesNotContain("org.testcontainers");
    }

    @Test
    void payloadTypeConstantsDoNotUseJavaClassNames() throws Exception {
        String sources = readAll("src/main/java/com/sovereign/connect/bus/runtime/serialization");
        assertThat(sources).doesNotContain("PAYLOAD_TYPE = \"com.");
        assertThat(sources).doesNotContain("PAYLOAD_TYPE = \"org.");
        assertThat(sources).doesNotContain("PAYLOAD_TYPE = \"io.");
    }

    private String readAll(String dir) throws Exception {
        Path root = Path.of(dir);
        if (!Files.exists(root)) return "";
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", String::concat);
        }
    }

    private String read(Path p) {
        try {
            return Files.readString(p);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
