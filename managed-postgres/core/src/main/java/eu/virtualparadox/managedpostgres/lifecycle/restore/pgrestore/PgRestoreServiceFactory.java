package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupArtifactVerifier;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupArtifactWriter;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifestFactory;
import eu.virtualparadox.managedpostgres.lifecycle.backup.PostgresBackupDiagnostics;
import eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump.PgDumpBackupCreator;
import eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump.PgDumpCommandExecutor;
import eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump.PgDumpCommandFactory;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreDiagnostics;
import java.util.Objects;

/**
 * Wires the internal {@code pg_restore} service collaborator graph.
 */
public final class PgRestoreServiceFactory {

    private PgRestoreServiceFactory() {}

    /**
     * Returns the create result.
     *
     * @param dependencies dependencies value
     * @return create result
     */
    public static PgRestoreService create(final PgRestoreDependencies dependencies) {
        final PgRestoreDependencies checkedDependencies = Objects.requireNonNull(dependencies, "dependencies");
        final PostgresRestoreDiagnostics restoreDiagnostics = new PostgresRestoreDiagnostics();
        final PostgresBackupDiagnostics backupDiagnostics = new PostgresBackupDiagnostics();
        final PgDumpCommandExecutor safetyBackupCommandExecutor = new PgDumpCommandExecutor(
                new PgDumpCommandFactory(checkedDependencies.runtimeDirectory(), checkedDependencies.timeout()),
                checkedDependencies.commandRunner(),
                checkedDependencies.manifestSource().connectionInfo(),
                backupDiagnostics);
        final BackupArtifactWriter safetyBackupArtifactWriter = new BackupArtifactWriter(
                new BackupManifestFactory(checkedDependencies.manifestSource()), backupDiagnostics);

        return new PgRestoreService(new PgRestoreServiceDependencies(
                checkedDependencies.layout(),
                checkedDependencies.fileSystem(),
                checkedDependencies.lockService(),
                new BackupArtifactVerifier(),
                new PgDumpBackupCreator(safetyBackupCommandExecutor, safetyBackupArtifactWriter),
                new PgRestoreCommandExecutor(
                        new PgRestoreCommandFactory(
                                checkedDependencies.runtimeDirectory(), checkedDependencies.timeout()),
                        checkedDependencies.commandRunner(),
                        checkedDependencies.manifestSource().connectionInfo(),
                        restoreDiagnostics),
                checkedDependencies.manifestSource(),
                restoreDiagnostics));
    }
}
