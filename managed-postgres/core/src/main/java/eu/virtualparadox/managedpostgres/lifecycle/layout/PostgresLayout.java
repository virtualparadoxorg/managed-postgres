package eu.virtualparadox.managedpostgres.lifecycle.layout;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Filesystem layout for a managed PostgreSQL instance.
 */
public final class PostgresLayout {

    /**
     * Lock file acquired before runtime installation or publication.
     */
    public static final String RUNTIME_INSTALL_LOCK_FILE = "runtime-install.lock";

    /**
     * Lock file acquired before lifecycle operations that mutate storage.
     */
    public static final String OPERATION_LOCK_FILE = "operation.lock";

    /**
     * Lock file acquired before manager-level lifecycle decisions.
     */
    public static final String MANAGER_LOCK_FILE = "manager.lock";

    private static final String DATA_DIRECTORY = "data";
    private static final String CREDENTIALS_FILE = "credentials.properties";
    private static final String LOCKS_DIRECTORY = "locks";
    private static final String METADATA_FILE = "metadata.json";
    private static final String RUNTIME_DIRECTORY = "runtime";
    private static final String STATE_DIRECTORY = "state";
    private static final String TEMPORARY_PREFIX = "instance-";

    private final Path root;
    private final Path runtimeDirectory;
    private final Path dataDirectory;
    private final Path stateDirectory;
    private final Path metadataPath;
    private final Path credentialsPath;
    private final Path locksDirectory;
    private final Path runtimeInstallLockPath;
    private final Path operationLockPath;
    private final Path managerLockPath;
    private final List<Path> lockOrder;

    private PostgresLayout(final Path root) {
        this.root = normalize(root);
        runtimeDirectory = this.root.resolve(RUNTIME_DIRECTORY);
        dataDirectory = this.root.resolve(DATA_DIRECTORY);
        stateDirectory = this.root.resolve(STATE_DIRECTORY);
        metadataPath = stateDirectory.resolve(METADATA_FILE);
        credentialsPath = stateDirectory.resolve(CREDENTIALS_FILE);
        locksDirectory = this.root.resolve(LOCKS_DIRECTORY);
        runtimeInstallLockPath = locksDirectory.resolve(RUNTIME_INSTALL_LOCK_FILE);
        operationLockPath = locksDirectory.resolve(OPERATION_LOCK_FILE);
        managerLockPath = locksDirectory.resolve(MANAGER_LOCK_FILE);
        lockOrder = List.of(runtimeInstallLockPath, operationLockPath, managerLockPath);
    }

    /**
     * Creates a layout for the supplied storage configuration.
     *
     * @param storage storage configuration
     * @return PostgreSQL filesystem layout
     */
    public static PostgresLayout create(final Storage storage) {
        return create(storage, new FileSystemOperationJournal());
    }

    /**
     * Returns the create result.
     *
     * @param storage storage value
     * @param fileSystem file system value
     * @return create result
     */
    public static PostgresLayout create(final Storage storage, final ManagedFileSystem fileSystem) {
        final PostgresLayout layout = plan(storage, fileSystem);
        layout.createDirectories(fileSystem);

        return layout;
    }

    /**
     * Returns the plan result.
     *
     * @param storage storage value
     * @param fileSystem file system value
     * @return plan result
     */
    public static PostgresLayout plan(final Storage storage, final ManagedFileSystem fileSystem) {
        final Storage checkedStorage = Objects.requireNonNull(storage, "storage");
        final ManagedFileSystem checkedFileSystem = Objects.requireNonNull(fileSystem, "fileSystem");

        try {
            final Path layoutRoot;
            if (checkedStorage.temporaryStorage()) {
                layoutRoot = createTemporaryRoot(checkedStorage.path(), checkedFileSystem);
            } else {
                layoutRoot = checkedStorage.path();
            }

            return new PostgresLayout(layoutRoot);
        } catch (UncheckedIOException exception) {
            throw layoutFailure("Failed to create PostgreSQL filesystem layout", checkedStorage.path(), exception);
        }
    }

    /**
     * Returns the layout root directory.
     *
     * @return layout root directory
     */
    public Path root() {
        return root;
    }

    /**
     * Returns the PostgreSQL runtime directory.
     *
     * @return runtime directory
     */
    public Path runtimeDirectory() {
        return runtimeDirectory;
    }

    /**
     * Returns the PostgreSQL data directory.
     *
     * @return data directory
     */
    public Path dataDirectory() {
        return dataDirectory;
    }

    /**
     * Returns the lifecycle state directory.
     *
     * @return state directory
     */
    public Path stateDirectory() {
        return stateDirectory;
    }

    /**
     * Returns the metadata file path.
     *
     * @return metadata file path
     */
    public Path metadataPath() {
        return metadataPath;
    }

    /**
     * Returns the credential store file path.
     *
     * @return credential store file path
     */
    public Path credentialsPath() {
        return credentialsPath;
    }

    /**
     * Returns the lifecycle locks directory.
     *
     * @return locks directory
     */
    public Path locksDirectory() {
        return locksDirectory;
    }

    /**
     * Returns the runtime installation lock path.
     *
     * @return runtime installation lock path
     */
    public Path runtimeInstallLockPath() {
        return runtimeInstallLockPath;
    }

    /**
     * Returns the operation lock path.
     *
     * @return operation lock path
     */
    public Path operationLockPath() {
        return operationLockPath;
    }

    /**
     * Returns the manager lock path.
     *
     * @return manager lock path
     */
    public Path managerLockPath() {
        return managerLockPath;
    }

    /**
     * Returns lock paths in required acquisition order.
     *
     * @return lock paths in runtime-install, operation, manager order
     */
    public List<Path> lockOrder() {
        return lockOrder;
    }

    /**
     * Performs the create directories operation.
     *
     * @param fileSystem file system value
     */
    public void createDirectories(final ManagedFileSystem fileSystem) {
        try {
            final ManagedFileSystem checkedFileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
            checkedFileSystem.createDirectories(root);
            checkedFileSystem.createDirectories(runtimeDirectory);
            checkedFileSystem.createDirectories(dataDirectory);
            checkedFileSystem.createDirectories(stateDirectory);
            checkedFileSystem.createDirectories(locksDirectory);
        } catch (UncheckedIOException exception) {
            throw layoutFailure("Failed to create PostgreSQL filesystem layout", root, exception);
        }
    }

    private static Path createTemporaryRoot(final Path configuredRoot, final ManagedFileSystem fileSystem) {
        final Path temporaryRoot = normalize(configuredRoot);

        fileSystem.createDirectories(temporaryRoot);

        return fileSystem.createTemporaryDirectory(temporaryRoot, TEMPORARY_PREFIX);
    }

    private static Path normalize(final Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static ManagedPostgresException layoutFailure(
            final String message, final Path root, final Throwable cause) {
        return new ManagedPostgresException(
                message,
                cause,
                new DiagnosticReport(List.of(new DiagnosticSection(
                        "postgres-layout", Map.of("root", normalize(root).toString())))));
    }
}
