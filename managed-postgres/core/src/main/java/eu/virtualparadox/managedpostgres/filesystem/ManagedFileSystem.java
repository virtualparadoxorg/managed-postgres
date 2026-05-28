package eu.virtualparadox.managedpostgres.filesystem;

import java.nio.file.Path;

/**
 * Filesystem boundary for managed PostgreSQL storage mutations.
 */
public interface ManagedFileSystem {

    /**
     * Ensures that a directory and its parents exist.
     *
     * @param directory directory to create
     */
    public void createDirectories(final Path directory);

    /**
     * Creates a new temporary directory below the supplied parent directory.
     *
     * @param parentDirectory parent directory
     * @param prefix temporary directory name prefix
     * @return created temporary directory
     */
    public Path createTemporaryDirectory(final Path parentDirectory, final String prefix);

    /**
     * Deletes a regular file when it exists.
     *
     * @param path file path to delete
     */
    public void deleteIfExists(final Path path);

    /**
     * Begins a managed filesystem operation rooted at the supplied path.
     *
     * @param operationName human-readable operation name
     * @param operationRoot root directory for the operation
     * @return managed filesystem operation
     */
    public FileSystemOperation beginOperation(final String operationName, final Path operationRoot);
}
