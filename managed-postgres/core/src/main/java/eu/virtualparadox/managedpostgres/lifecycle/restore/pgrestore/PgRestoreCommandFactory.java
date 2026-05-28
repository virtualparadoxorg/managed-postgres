package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;

/**
 * Builds {@code pg_restore} command requests for managed logical restores.
 */
public final class PgRestoreCommandFactory {

    private static final String PGPASSWORD = "PGPASSWORD";

    private final Path pgRestore;
    private final Duration timeout;

    /**
     * Creates a PgRestoreCommandFactory instance.
     *
     * @param runtimeDirectory runtime directory value
     * @param timeout timeout value
     */
    public PgRestoreCommandFactory(final Path runtimeDirectory, final Duration timeout) {
        this.pgRestore = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory").resolve("bin").resolve("pg_restore");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Returns the custom restore result.
     *
     * @param connectionInfo connection info value
     * @param backup backup value
     * @return custom restore result
     */
    public CommandRequest customRestore(final PostgresConnectionInfo connectionInfo, final Path backup) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        final Path checkedBackup = Objects.requireNonNull(backup, "backup");
        final List<String> command = new ArrayList<>();
        command.add(pgRestore.toString());
        command.add("-h");
        command.add(checkedConnectionInfo.host());
        command.add("-p");
        command.add(Integer.toString(checkedConnectionInfo.port()));
        command.add("-U");
        command.add(checkedConnectionInfo.username());
        command.add("-d");
        command.add(checkedConnectionInfo.database());
        command.add("--clean");
        command.add("--if-exists");
        command.add("--no-owner");
        command.add(checkedBackup.toString());

        return CommandRequest.of(command, timeout)
                .withEnvironmentVariable(PGPASSWORD, checkedConnectionInfo.password().reveal());
    }
}
