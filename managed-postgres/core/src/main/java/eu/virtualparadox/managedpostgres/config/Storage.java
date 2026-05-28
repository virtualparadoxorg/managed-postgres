package eu.virtualparadox.managedpostgres.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable storage configuration for managed PostgreSQL data.
 *
 * @param path storage path
 * @param temporaryStorage whether storage is temporary
 */
public record Storage(Path path, boolean temporaryStorage) {

    /**
     * Creates immutable storage configuration.
     *
     * @param path storage path
     * @param temporaryStorage whether storage is temporary
     */
    public Storage {
        Objects.requireNonNull(path, "path");
    }

    /**
     * Creates temporary storage configuration.
     *
     * @return temporary storage
     */
    public static Storage temporary() {
        final String temporaryDirectory = Objects.requireNonNull(System.getProperty("java.io.tmpdir"), "java.io.tmpdir");

        return new Storage(Path.of(temporaryDirectory, "managed-postgres"), true);
    }

    /**
     * Creates project-local storage configuration.
     *
     * @param path project-local storage path
     * @return project-local storage
     */
    public static Storage projectLocal(final Path path) {
        return new Storage(path, false);
    }

    /**
     * Creates project-local storage configuration.
     *
     * @param path project-local storage path
     * @return project-local storage
     */
    public static Storage projectLocal(final String path) {
        return projectLocal(Path.of(path));
    }
}
