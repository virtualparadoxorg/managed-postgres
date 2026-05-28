package eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump;

import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;

/**
 * Dependencies required to create pg_dump backup operations.
 *
 * @param commandRunner command runner
 * @param fileSystem managed filesystem boundary
 * @param lockService lifecycle lock service
 * @param timeout backup command timeout
 * @param clock manifest timestamp clock
 * @param frameworkVersion managed-postgres framework version
 */
record PgDumpBackupOperationProviderDependencies(
        CommandRunner commandRunner,
        ManagedFileSystem fileSystem,
        PostgresLockService lockService,
        Duration timeout,
        Clock clock,
        String frameworkVersion) {

    PgDumpBackupOperationProviderDependencies {
        Objects.requireNonNull(commandRunner, "commandRunner");
        Objects.requireNonNull(fileSystem, "fileSystem");
        Objects.requireNonNull(lockService, "lockService");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(frameworkVersion, "frameworkVersion");
    }
}
