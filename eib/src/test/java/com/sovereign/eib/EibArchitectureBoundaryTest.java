package com.sovereign.eib;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EibArchitectureBoundaryTest {

    @Test
    void mainSourcesDoNotReferenceForbiddenPackagesOrProviderIdsInEffectiveRecords() throws Exception {
        Path main = Path.of("src/main/java");
        String allSources;
        try (var files = Files.walk(main)) {
            allSources = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", String::concat);
        }

        assertThat(allSources).doesNotContain("com.sovereign.connect");
        assertThat(read(Path.of("src/main/java/com/sovereign/eib/domain/EffectiveDeviceView.java"))).doesNotContain("providerDeviceId");
        assertThat(read(Path.of("src/main/java/com/sovereign/eib/domain/EffectiveEndpointView.java"))).doesNotContain("providerEndpointId");
    }

    @Test
    void upstreamClientDoesNotRetainObsoleteRouteFragments() {
        String clientSource = read(Path.of("src/main/java/com/sovereign/eib/northbound/RestClientEibScNorthboundClient.java"));

        assertThat(clientSource)
                .doesNotContain("/topology/" + "snapshot")
                .doesNotContain("/runtime-state/" + "{subjectId}")
                .doesNotContain("status" + "=")
                .doesNotContain("limit" + "=")
                .doesNotContain("/temporal-acts/" + "signal");
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
