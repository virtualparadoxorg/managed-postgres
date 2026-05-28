package eu.virtualparadox.managedpostgres.lifecycle.psql;

import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.filesystem.DirectoryPublisher;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperation;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFilePermissions;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Coordinates psql script file store behavior for managed PostgreSQL internals.
 */
public final class PsqlScriptFileStore {

    private static final String SQL_FILE = "bootstrap.sql";

    private final Path sqlDirectory;
    private final ManagedFileSystem fileSystem;
    private final PsqlBootstrapDiagnostics diagnostics;

    /**
     * Creates a PsqlScriptFileStore instance.
     *
     * @param sqlDirectory sql directory value
     * @param fileSystem file system value
     * @param diagnostics diagnostics value
     */
    public PsqlScriptFileStore(
            final Path sqlDirectory,
            final ManagedFileSystem fileSystem,
            final PsqlBootstrapDiagnostics diagnostics) {
        this.sqlDirectory = Objects.requireNonNull(sqlDirectory, "sqlDirectory");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /**
     * Performs the run sql file operation.
     *
     * @param sql sql value
     * @param command command value
     */
    public void runSqlFile(final String sql, final PsqlSqlFileCommand command) {
        Path stagingDirectory = null;
        try {
            fileSystem.createDirectories(sqlDirectory);
            stagingDirectory = fileSystem.createTemporaryDirectory(sqlDirectory, "bootstrap-sql-");
            final Path sqlFile = stagingDirectory.resolve(SQL_FILE);
            writeSqlFile(stagingDirectory, sqlFile, sql);
            command.run(sqlFile);
        } finally {
            if (stagingDirectory != null) {
                DirectoryPublisher.deleteRecursivelyIfExists(stagingDirectory);
            }
        }
    }

    private void writeSqlFile(final Path operationRoot, final Path sqlFile, final String sql) {
        try (FileSystemOperation operation = fileSystem.beginOperation("write-bootstrap-sql", operationRoot)) {
            operation.writeUtf8Atomically(sqlFile, sql, ManagedFilePermissions.ownerOnlyReadWrite());
            operation.commit();
        } catch (final UncheckedIOException | IllegalArgumentException exception) {
            throw new PostgresStartupException(
                    "Failed to write PostgreSQL bootstrap SQL",
                    exception,
                    diagnostics.sqlFileFailure(sqlFile.toString()));
        }
    }
}
