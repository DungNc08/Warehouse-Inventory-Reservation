package com.warehouse.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Points Testcontainers at a Docker socket before any container starts.
 * Prefer {@code /tmp/tc-docker.sock} (no {@code @} in path) — docker-java breaks on {@code @} in home paths.
 */
public final class TestcontainersEnvironment {

    private static final Path CANONICAL_SOCKET = Path.of("/tmp/tc-docker.sock");

    private TestcontainersEnvironment() {
    }

    public static void init() {
        if (System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE") != null) {
            return;
        }

        if (Files.exists(CANONICAL_SOCKET)) {
            String socketPath = CANONICAL_SOCKET.toAbsolutePath().toString();
            System.setProperty("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", socketPath);
            System.setProperty("docker.host", "unix://" + socketPath);
            return;
        }

        resolveSocket().ifPresent(socket ->
                System.setProperty(
                        "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE",
                        socket.toAbsolutePath().toString()
                )
        );
    }

    static Optional<Path> resolveSocket() {
        Path home = Path.of(System.getProperty("user.home"));
        return Stream.of(
                        home.resolve(".docker/desktop/docker-cli.sock"),
                        home.resolve(".docker/desktop/docker.sock"),
                        Path.of("/var/run/docker.sock")
                )
                .filter(Files::exists)
                .findFirst();
    }
}
