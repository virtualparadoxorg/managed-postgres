package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupArtifactVerifier;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifestSource;
import eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump.PgDumpBackupCreator;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreDiagnostics;

/**
 * Runtime collaborators for the logical restore service.
 *
 * @param layout PostgreSQL filesystem layout
 * @param fileSystem managed filesystem boundary
 * @param lockService lifecycle lock service
 * @param backupVerifier backup artifact verifier
 * @param safetyBackupCreator pre-restore safety backup creator
 * @param commandExecutor pg_restore command executor
 * @param manifestSource manifest source data
 * @param diagnostics restore diagnostics builder
 */
record PgRestoreServiceDependencies(
        PostgresLayout layout,
        ManagedFileSystem fileSystem,
        PostgresLockService lockService,
        BackupArtifactVerifier backupVerifier,
        PgDumpBackupCreator safetyBackupCreator,
        PgRestoreCommandExecutor commandExecutor,
        BackupManifestSource manifestSource,
        PostgresRestoreDiagnostics diagnostics) {

    PgRestoreServiceDependencies {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(fileSystem, "fileSystem");
        Objects.requireNonNull(lockService, "lockService");
        Objects.requireNonNull(backupVerifier, "backupVerifier");
        Objects.requireNonNull(safetyBackupCreator, "safetyBackupCreator");
        Objects.requireNonNull(commandExecutor, "commandExecutor");
        Objects.requireNonNull(manifestSource, "manifestSource");
        Objects.requireNonNull(diagnostics, "diagnostics");
    }
}
