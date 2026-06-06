package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperation;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupArtifactPaths;
import eu.virtualparadox.managedpostgres.lifecycle.layout.HeldPostgresLock;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Restores managed PostgreSQL logical backups through {@code pg_restore}.
 */
public final class PgRestoreService implements PostgresRestoreOperation {

    private static final String OPERATION_NAME = "postgres-restore";
    private static final String SAFETY_BACKUP_MARKER = ".before-restore";
    private static final String DEFAULT_SAFETY_BACKUP_EXTENSION = ".dump";

    private final PgRestoreServiceDependencies dependencies;

    /**
     * Creates a PgRestoreService instance.
     *
     * @param dependencies dependencies value
     */
    public PgRestoreService(final PgRestoreServiceDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreFrom(final Path backup, final RestoreOptions options) {
        final Path checkedBackup =
                Objects.requireNonNull(backup, "backup").toAbsolutePath().normalize();
        final RestoreOptions checkedOptions = Objects.requireNonNull(options, "options");
        final BackupArtifactPaths safetyPaths = safetyBackupPaths(checkedBackup);
        try (HeldPostgresLock operationLock = acquireOperationLock();
                FileSystemOperation operation =
                        dependencies.fileSystem().beginOperation(OPERATION_NAME, safetyPaths.operationRoot())) {
            requireHeldLock(operationLock);
            dependencies
                    .backupVerifier()
                    .verify(
                            checkedBackup,
                            dependencies.manifestSource().connectionInfo(),
                            dependencies.manifestSource().metadata());
            requireSafetyBackup(checkedOptions);
            requireSafetyBackupArtifactsAbsent(safetyPaths);
            createSafetyBackup(operation, safetyPaths);
            dependencies.commandExecutor().restoreFrom(checkedBackup);
            operation.commit();
        }
    }

    private HeldPostgresLock acquireOperationLock() {
        try {
            return dependencies.lockService().acquireOperationLock(dependencies.layout());
        } catch (final ManagedPostgresException exception) {
            throw new PostgresRestoreException(
                    "PostgreSQL restore lock failed",
                    exception,
                    dependencies
                            .diagnostics()
                            .lockFailure(
                                    Objects.toString(
                                            exception.getMessage(),
                                            exception.getClass().getName()),
                                    dependencies.layout().operationLockPath()));
        }
    }

    private void requireSafetyBackup(final RestoreOptions options) {
        if (!options.createSafetyBackup()) {
            throw new PostgresRestoreException(
                    "PostgreSQL restore safety backup is required",
                    dependencies.diagnostics().safetyBackupRequired());
        }
    }

    private void requireSafetyBackupArtifactsAbsent(final BackupArtifactPaths paths) {
        List.of(paths.backupTarget(), paths.manifestTarget(), paths.checksumTarget())
                .forEach(this::requireSafetyBackupArtifactAbsent);
    }

    private void requireSafetyBackupArtifactAbsent(final Path path) {
        if (Files.exists(path)) {
            throw new PostgresRestoreException(
                    "PostgreSQL restore safety backup artifact already exists",
                    dependencies.diagnostics().existingSafetyBackupArtifact(path));
        }
    }

    private void createSafetyBackup(final FileSystemOperation operation, final BackupArtifactPaths safetyPaths) {
        try {
            dependencies.safetyBackupCreator().create(operation, safetyPaths);
        } catch (final PostgresBackupException exception) {
            throw new PostgresRestoreException(
                    "Failed to create PostgreSQL restore safety backup",
                    exception,
                    dependencies
                            .diagnostics()
                            .safetyBackupFailure(
                                    Objects.toString(
                                            exception.getMessage(),
                                            exception.getClass().getName()),
                                    dependencies.manifestSource().connectionInfo()));
        }
    }

    private static void requireHeldLock(final HeldPostgresLock lock) {
        Objects.requireNonNull(lock, "lock");
    }

    private static BackupArtifactPaths safetyBackupPaths(final Path backup) {
        final Path safetyBackup = safetyBackupPath(backup);
        final Path fileName = Objects.requireNonNull(safetyBackup.getFileName(), "safetyBackup fileName");

        return new BackupArtifactPaths(
                safetyBackup,
                Path.of(safetyBackup + ".manifest.json"),
                Path.of(safetyBackup + ".sha256"),
                Objects.requireNonNull(safetyBackup.getParent(), "safetyBackup parent"),
                fileName.toString());
    }

    private static Path safetyBackupPath(final Path backup) {
        final Path fileName = Objects.requireNonNull(backup.getFileName(), "backup fileName");
        final String name = fileName.toString();
        final int extensionIndex = name.lastIndexOf('.');
        final String safetyName;
        if (extensionIndex > 0) {
            safetyName = name.substring(0, extensionIndex) + SAFETY_BACKUP_MARKER + name.substring(extensionIndex);
        } else {
            safetyName = name + SAFETY_BACKUP_MARKER + DEFAULT_SAFETY_BACKUP_EXTENSION;
        }

        return backup.resolveSibling(safetyName);
    }
}
