package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifestSource;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;

/**
 * Dependencies required for pg_restore logical restore execution.
 *
 * @param layout PostgreSQL filesystem layout
 * @param runtimeDirectory PostgreSQL runtime directory
 * @param commandRunner command runner
 * @param fileSystem managed filesystem boundary
 * @param lockService lifecycle lock service
 * @param timeout pg_restore timeout
 * @param manifestSource manifest source data for safety backups
 */
record PgRestoreDependencies(
        PostgresLayout layout,
        Path runtimeDirectory,
        CommandRunner commandRunner,
        ManagedFileSystem fileSystem,
        PostgresLockService lockService,
        Duration timeout,
        BackupManifestSource manifestSource) {

    PgRestoreDependencies {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
        Objects.requireNonNull(commandRunner, "commandRunner");
        Objects.requireNonNull(fileSystem, "fileSystem");
        Objects.requireNonNull(lockService, "lockService");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(manifestSource, "manifestSource");
    }
}
