package eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump;

import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupArtifactWriter;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifestFactory;
import eu.virtualparadox.managedpostgres.lifecycle.backup.PostgresBackupDiagnostics;
import java.util.Objects;

/**
 * Wires the internal pg_dump backup service collaborator graph.
 */
public final class PgDumpBackupServiceFactory {

    private PgDumpBackupServiceFactory() {}

    /**
     * Returns the create result.
     *
     * @param dependencies dependencies value
     * @return create result
     */
    public static PgDumpBackupService create(final PgDumpBackupDependencies dependencies) {
        final PgDumpBackupDependencies checkedDependencies = Objects.requireNonNull(dependencies, "dependencies");
        final PostgresBackupDiagnostics diagnostics = new PostgresBackupDiagnostics();
        final PgDumpCommandExecutor commandExecutor = new PgDumpCommandExecutor(
                new PgDumpCommandFactory(checkedDependencies.runtimeDirectory(), checkedDependencies.timeout()),
                checkedDependencies.commandRunner(),
                checkedDependencies.manifestSource().connectionInfo(),
                diagnostics);
        final BackupArtifactWriter artifactWriter =
                new BackupArtifactWriter(new BackupManifestFactory(checkedDependencies.manifestSource()), diagnostics);

        return new PgDumpBackupService(
                checkedDependencies.layout(),
                checkedDependencies.fileSystem(),
                checkedDependencies.lockService(),
                new PgDumpBackupCreator(commandExecutor, artifactWriter));
    }
}
