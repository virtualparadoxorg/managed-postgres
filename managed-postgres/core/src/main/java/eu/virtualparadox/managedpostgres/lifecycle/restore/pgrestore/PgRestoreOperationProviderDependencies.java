package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;

/**
 * Dependencies required to create {@code pg_restore} restore operations.
 *
 * @param commandRunner command runner
 * @param fileSystem managed filesystem boundary
 * @param lockService lifecycle lock service
 * @param timeout restore command timeout
 * @param clock safety backup manifest timestamp clock
 * @param frameworkVersion managed-postgres framework version
 */
record PgRestoreOperationProviderDependencies(
        CommandRunner commandRunner,
        ManagedFileSystem fileSystem,
        PostgresLockService lockService,
        Duration timeout,
        Clock clock,
        String frameworkVersion) {

    PgRestoreOperationProviderDependencies {
        Objects.requireNonNull(commandRunner, "commandRunner");
        Objects.requireNonNull(fileSystem, "fileSystem");
        Objects.requireNonNull(lockService, "lockService");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(frameworkVersion, "frameworkVersion");
    }
}
