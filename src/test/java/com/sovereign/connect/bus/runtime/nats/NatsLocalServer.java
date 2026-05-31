package com.sovereign.connect.bus.runtime.nats;

import io.nats.NatsServerRunner;
import io.nats.client.Connection;
import io.nats.client.Nats;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class NatsLocalServer implements AutoCloseable {
    private static final String VERSION = "v2.14.1";

    private final NatsServerRunner runner;
    private final Connection connection;

    private NatsLocalServer(NatsServerRunner runner, Connection connection) {
        this.runner = runner;
        this.connection = connection;
    }

    static NatsLocalServer start(Path cacheDir) throws Exception {
        Path executable = natsServerExecutable(cacheDir);
        NatsServerRunner runner = NatsServerRunner.builder()
                .executablePath(executable)
                .jetstream()
                .build();
        Connection connection = Nats.connect(runner.getNatsLocalhostUri());
        return new NatsLocalServer(runner, connection);
    }

    Connection connection() {
        return connection;
    }

    @Override
    public void close() throws Exception {
        connection.close();
        runner.close();
    }

    private static Path natsServerExecutable(Path cacheDir) throws IOException {
        String configured = System.getenv("NATS_SERVER_EXECUTABLE");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        Path target = cacheDir.resolve(VERSION).resolve(executableName());
        if (Files.exists(target)) {
            return target;
        }
        Files.createDirectories(target.getParent());
        downloadAndExtract(target);
        target.toFile().setExecutable(true);
        return target;
    }

    private static void downloadAndExtract(Path target) throws IOException {
        String url = "https://github.com/nats-io/nats-server/releases/download/"
                + VERSION + "/nats-server-" + VERSION + "-" + platform() + ".zip";
        try (InputStream input = URI.create(url).toURL().openStream();
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(executableName())) {
                    Files.copy(zip, target);
                    return;
                }
            }
        }
        throw new IOException("nats-server executable not found in release archive");
    }

    private static String platform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String normalizedArch = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "amd64";
        if (os.contains("win")) {
            return "windows-" + normalizedArch;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin-" + normalizedArch;
        }
        return "linux-" + normalizedArch;
    }

    private static String executableName() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "nats-server.exe"
                : "nats-server";
    }
}
