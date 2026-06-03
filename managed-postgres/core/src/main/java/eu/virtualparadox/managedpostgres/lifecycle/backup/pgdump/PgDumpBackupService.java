package eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump;

import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperation;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupArtifactPaths;
import eu.virtualparadox.managedpostgres.lifecycle.backup.PostgresBackupDiagnostics;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;
import eu.virtualparadox.managedpostgres.lifecycle.layout.HeldPostgresLock;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Creates pg_dump logical backups and sidecar metadata artifacts.
 */
public final class PgDumpBackupService implements PostgresBackupOperation {

    private static final String OPERATION_NAME = "postgres-backup";

    private final PostgresLayout layout;
    private final ManagedFileSystem fileSystem;
    private final PostgresLockService lockService;
    private final PgDumpBackupCreator backupCreator;
    private final PostgresBackupDiagnostics diagnostics;

    /**
     * Creates a PgDumpBackupService instance.
     *
     * @param layout layout value
     * @param fileSystem file system value
     * @param lockService lock service value
     * @param backupCreator backup creator value
     */
    public PgDumpBackupService(
            final PostgresLayout layout,
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService,
            final PgDumpBackupCreator backupCreator) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.lockService = Objects.requireNonNull(lockService, "lockService");
        this.backupCreator = Objects.requireNonNull(backupCreator, "backupCreator");
        diagnostics = new PostgresBackupDiagnostics();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void backupTo(final Path target) {
        final BackupArtifactPaths paths = BackupArtifactPaths.from(target, diagnostics);

        try (HeldPostgresLock operationLock = lockService.acquireOperationLock(layout);
                FileSystemOperation operation = fileSystem.beginOperation(OPERATION_NAME, paths.operationRoot())) {
            requireHeldLock(operationLock);
            paths.requireAbsent(diagnostics);
            backupCreator.create(operation, paths);
            operation.commit();
        }
    }

    private static void requireHeldLock(final HeldPostgresLock lock) {
        Objects.requireNonNull(lock, "lock");
    }
}
