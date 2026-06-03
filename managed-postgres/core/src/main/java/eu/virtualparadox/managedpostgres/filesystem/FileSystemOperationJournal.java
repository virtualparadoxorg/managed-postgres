package eu.virtualparadox.managedpostgres.filesystem;

import eu.virtualparadox.managedpostgres.filesystem.lock.FileSystemLockManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * Creates managed filesystem operations and recovers abandoned staging paths.
 */
public final class FileSystemOperationJournal implements ManagedFileSystem {

    private static final String STAGING_SUFFIX = ".staging";
    private static final String TEMPORARY_FILE_SUFFIX = ".tmp";

    private final FileSystemOperationServices services;

    /**
     * Creates a filesystem operation journal with default collaborators.
     */
    public FileSystemOperationJournal() {
        this(FileSystemOperationServices.defaults());
    }

    private FileSystemOperationJournal(final FileSystemOperationServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /**
     * Ensures that a directory and its parents exist.
     *
     * @param directory directory to create
     */
    @Override
    public void createDirectories(final Path directory) {
        final Path checkedDirectory =
                Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();

        try {
            Files.createDirectories(checkedDirectory);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to create directory " + checkedDirectory, exception);
        }
    }

    /**
     * Creates a new temporary directory below the supplied parent directory.
     *
     * @param parentDirectory parent directory
     * @param prefix temporary directory name prefix
     * @return created temporary directory
     */
    @Override
    public Path createTemporaryDirectory(final Path parentDirectory, final String prefix) {
        final Path checkedParentDirectory = Objects.requireNonNull(parentDirectory, "parentDirectory")
                .toAbsolutePath()
                .normalize();
        final String checkedPrefix = requirePrefix(prefix);

        try {
            return Files.createTempDirectory(checkedParentDirectory, checkedPrefix)
                    .toAbsolutePath()
                    .normalize();
        } catch (final IOException exception) {
            throw new UncheckedIOException(
                    "failed to create temporary directory in " + checkedParentDirectory, exception);
        }
    }

    /**
     * Deletes a regular file when it exists.
     *
     * @param path file path to delete
     */
    @Override
    public void deleteIfExists(final Path path) {
        final Path checkedPath =
                Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        final FileSystemLockManager.FileSystemLock lock = services.lockManager().lock(checkedPath);
        try {
            Files.deleteIfExists(checkedPath);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to delete file " + checkedPath, exception);
        } finally {
            lock.close();
        }
    }

    /**
     * Begins a managed filesystem operation rooted at the supplied path.
     *
     * @param operationName human-readable operation name
     * @param operationRoot root directory for the operation
     * @return managed filesystem operation
     */
    @Override
    public FileSystemOperation beginOperation(final String operationName, final Path operationRoot) {
        final Path checkedOperationRoot = Objects.requireNonNull(operationRoot, "operationRoot")
                .toAbsolutePath()
                .normalize();

        return new JournaledFileSystemOperation(operationName, checkedOperationRoot, services);
    }

    /**
     * Recovers abandoned staging directories beneath an operation root.
     *
     * @param operationRoot root directory to scan
     * @return recovery report containing discarded and unknown staging directories
     */
    public RecoveryReport recover(final Path operationRoot) {
        final Path checkedOperationRoot = Objects.requireNonNull(operationRoot, "operationRoot")
                .toAbsolutePath()
                .normalize();
        final List<Path> discarded = new ArrayList<>();
        final List<Path> unknown = new ArrayList<>();

        if (Files.isDirectory(checkedOperationRoot)) {
            try (Stream<Path> paths = Files.list(checkedOperationRoot)) {
                recoverPaths(discarded, unknown, paths.toList());
            } catch (final IOException exception) {
                throw new UncheckedIOException(
                        "failed to recover staging directories in " + checkedOperationRoot, exception);
            }
        }

        return new RecoveryReport(discarded, unknown);
    }

    private void recoverPaths(final List<Path> discarded, final List<Path> unknown, final List<Path> paths) {
        for (final Path path : paths) {
            if (isStagingDirectory(path)) {
                recoverStagingDirectory(discarded, unknown, path);
            } else if (isAtomicTemporaryFile(path)) {
                deleteAtomicTemporaryFile(path);
            }
        }
    }

    private void recoverStagingDirectory(final List<Path> discarded, final List<Path> unknown, final Path staging) {
        if (services.ownership().isOwned(staging)) {
            DirectoryPublisher.deleteRecursivelyIfExists(staging);
            discarded.add(staging);
        } else {
            unknown.add(staging);
        }
    }

    private static void deleteAtomicTemporaryFile(final Path temporaryFile) {
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to discard staged file " + temporaryFile, exception);
        }
    }

    private static boolean isStagingDirectory(final Path path) {
        final Path fileName = path.getFileName();
        return fileName != null
                && Files.isDirectory(path)
                && fileName.toString().endsWith(STAGING_SUFFIX);
    }

    private static boolean isAtomicTemporaryFile(final Path path) {
        final Path fileName = path.getFileName();
        return fileName != null
                && Files.isRegularFile(path)
                && fileName.toString().startsWith(".")
                && fileName.toString().endsWith(TEMPORARY_FILE_SUFFIX);
    }

    private static String requirePrefix(final String prefix) {
        if (StringUtils.isBlank(prefix)) {
            throw new IllegalArgumentException("prefix must not be blank");
        }

        return prefix;
    }

    /**
     * Immutable report produced by staging recovery.
     *
     * @param discardedStagingDirectories owned staging directories that were discarded
     * @param unknownStagingDirectories staging directories without ownership markers
     */
    public record RecoveryReport(List<Path> discardedStagingDirectories, List<Path> unknownStagingDirectories) {

        /**
         * Creates a recovery report.
         *
         * @param discardedStagingDirectories owned staging directories that were discarded
         * @param unknownStagingDirectories staging directories without ownership markers
         */
        public RecoveryReport {
            discardedStagingDirectories =
                    List.copyOf(Objects.requireNonNull(discardedStagingDirectories, "discardedStagingDirectories"));
            unknownStagingDirectories =
                    List.copyOf(Objects.requireNonNull(unknownStagingDirectories, "unknownStagingDirectories"));
        }
    }
}
