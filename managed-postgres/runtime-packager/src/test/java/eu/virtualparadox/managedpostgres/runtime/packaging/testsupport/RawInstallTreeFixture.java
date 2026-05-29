package eu.virtualparadox.managedpostgres.runtime.packaging.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;

/**
 * Shared test fixture for raw PostgreSQL install trees.
 */
public final class RawInstallTreeFixture {

    private RawInstallTreeFixture() {
    }

    /**
     * Creates a minimal raw install tree for packaging tests.
     *
     * @param root fixture root directory
     * @return raw install tree path
     * @throws IOException when fixture creation fails
     */
    public static Path create(final Path root) throws IOException {
        final Path rawInstallTree = root.resolve("raw-install");
        final Path postgresBinary = rawInstallTree.resolve("bin/postgres");
        Files.createDirectories(Objects.requireNonNull(postgresBinary.getParent(), "postgresBinary.parent"));
        Files.writeString(postgresBinary, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
        Files.createDirectories(rawInstallTree.resolve("share"));
        Files.writeString(rawInstallTree.resolve("share/extension.sql"), "-- extension\n", StandardCharsets.UTF_8);
        Files.createDirectories(rawInstallTree.resolve("lib"));
        Files.writeString(rawInstallTree.resolve("lib/libpq.a"), "archive", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(
                postgresBinary,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        return rawInstallTree;
    }
}
