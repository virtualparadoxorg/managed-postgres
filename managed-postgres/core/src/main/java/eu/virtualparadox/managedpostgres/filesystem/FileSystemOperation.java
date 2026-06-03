package eu.virtualparadox.managedpostgres.filesystem;

import java.nio.file.Path;

/**
 * Crash-safe filesystem operation with managed staging and publication.
 */
public interface FileSystemOperation extends AutoCloseable {

    /**
     * Creates a managed staging directory as a sibling of the named target.
     *
     * @param name target directory name
     * @return newly-created staging directory
     */
    public Path createStagingDirectory(final String name);

    /**
     * Writes UTF-8 content through a sibling temporary file and atomic publish.
     *
     * @param target final file path
     * @param content UTF-8 text content
     */
    public void writeUtf8Atomically(final Path target, final String content);

    /**
     * Writes UTF-8 content atomically with requested managed permissions.
     *
     * @param target final file path
     * @param content UTF-8 text content
     * @param permissions managed file permissions
     */
    public void writeUtf8Atomically(final Path target, final String content, final ManagedFilePermissions permissions);

    /**
     * Publishes a staged directory into the target path.
     *
     * @param staging staged directory
     * @param target final directory path
     */
    public void publishDirectory(final Path staging, final Path target);

    /**
     * Publishes a staged regular file into the target path.
     *
     * @param stagingFile staged regular file
     * @param target final file path
     */
    public void publishFile(Path stagingFile, Path target);

    /**
     * Marks this operation as committed so close does not discard staging.
     */
    public void commit();

    /**
     * Closes this operation, discarding owned uncommitted staging directories.
     */
    @Override
    public void close();
}
