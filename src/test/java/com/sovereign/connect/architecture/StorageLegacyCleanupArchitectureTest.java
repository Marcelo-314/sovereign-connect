package com.sovereign.connect.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StorageLegacyCleanupArchitectureTest {

    private final Path mainJava = Path.of("src/main/java");
    private final Path testJava = Path.of("src/test/java");
    private final Path pom = Path.of("pom.xml");

    @Test
    void noFasterxmlPackageUnderMainSource() {
        assertThat(mainJava.resolve("com/fasterxml"))
            .as("src/main/java/com/fasterxml must not exist")
            .doesNotExist();
    }

    @Test
    void noH2AdaptersUnderMainSource() throws IOException {
        try (Stream<Path> paths = Files.walk(mainJava)) {
            List<String> violations = paths
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> path.getFileName().toString().startsWith("H2"))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("No H2*.java files may exist under src/main/java")
                .isEmpty();
        }
    }

    @Test
    void noMainSourceReferencesH2Repositories() throws IOException {
        try (Stream<Path> paths = Files.walk(mainJava)) {
            List<String> violations = paths
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> containsAny(path,
                    "H2BaseTopologyRepository",
                    "H2TemporalActRepository",
                    "jdbc:h2:",
                    "org.h2"))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("No main source may reference H2 repositories, jdbc:h2 or org.h2")
                .isEmpty();
        }
    }

    @Test
    void noJsonDeserializeAsOrJsonSerializeAsInMainSource() throws IOException {
        try (Stream<Path> paths = Files.walk(mainJava)) {
            List<String> violations = paths
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> containsAny(path, "JsonDeserializeAs", "JsonSerializeAs"))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("JsonDeserializeAs and JsonSerializeAs must not appear in main source")
                .isEmpty();
        }
    }

    @Test
    void canonicalDomainPackagesDoNotImportJacksonAnnotations() throws IOException {
        List<Path> domainRoots = List.of(
            mainJava.resolve("com/sovereign/connect/core/topology/model"),
            mainJava.resolve("com/sovereign/connect/core/topology/port"),
            mainJava.resolve("com/sovereign/connect/core/temporal/model"),
            mainJava.resolve("com/sovereign/connect/core/temporal/port")
        );
        List<String> forbidden = List.of(
            "@JsonProperty",
            "@JsonDeserialize",
            "@JsonSerialize",
            "@JsonIgnore",
            "@JsonInclude",
            "import com.fasterxml.jackson.annotation"
        );
        for (Path root : domainRoots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String content = Files.readString(file);
                    for (String banned : forbidden) {
                        assertThat(content)
                            .as("Domain file %s must not contain %s", file, banned)
                            .doesNotContain(banned);
                    }
                }
            }
        }
    }

    @Test
    void h2DependencyIsTestScopeOnlyWhenRetained() throws IOException {
        String xml = Files.readString(pom);
        int h2Index = xml.indexOf("<artifactId>h2</artifactId>");
        if (h2Index < 0) {
            return;
        }
        int depStart = xml.lastIndexOf("<dependency>", h2Index);
        int depEnd = xml.indexOf("</dependency>", h2Index);
        assertThat(depStart).isGreaterThanOrEqualTo(0);
        assertThat(depEnd).isGreaterThan(h2Index);
        String dependencyBlock = xml.substring(depStart, depEnd + "</dependency>".length());
        assertThat(dependencyBlock)
            .as("H2 may be retained only as test scope")
            .contains("<scope>test</scope>")
            .doesNotContain("<scope>runtime</scope>")
            .doesNotContain("<scope>compile</scope>");
    }

    @Test
    void remainingH2TestsAreTaggedLegacyH2() throws IOException {
        if (!Files.exists(testJava)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(testJava)) {
            List<String> violations = paths
                .filter(path -> path.toString().endsWith("Test.java"))
                .filter(path -> containsAny(path, "H2TemporalActRepository", "jdbc:h2:", "import org.h2"))
                .filter(path -> !containsAny(path, "@Tag(\"legacy-h2\")", "@Tag('legacy-h2')"))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("Any remaining H2-backed test must be tagged @Tag(\"legacy-h2\")")
                .isEmpty();
        }
    }

    private boolean containsAny(Path file, String... needles) {
        try {
            String content = Files.readString(file);
            for (String needle : needles) {
                if (content.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
    }
}
