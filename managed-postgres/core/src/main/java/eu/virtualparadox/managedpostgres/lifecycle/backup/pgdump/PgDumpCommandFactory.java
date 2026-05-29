package eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.runtime.RuntimeBinaryLocator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;

/**
 * Builds pg_dump command requests for logical backups.
 */
public final class PgDumpCommandFactory {

    private static final String PGPASSWORD = "PGPASSWORD";

    private final Path pgDump;
    private final Duration timeout;

    /**
     * Creates a PgDumpCommandFactory instance.
     *
     * @param runtimeDirectory runtime directory value
     * @param timeout timeout value
     */
    public PgDumpCommandFactory(final Path runtimeDirectory, final Duration timeout) {
        this.pgDump = RuntimeBinaryLocator.resolveBinary(
                Objects.requireNonNull(runtimeDirectory, "runtimeDirectory"),
                "pg_dump");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Returns the custom dump result.
     *
     * @param connectionInfo connection info value
     * @param target target value
     * @return custom dump result
     */
    public CommandRequest customDump(final PostgresConnectionInfo connectionInfo, final Path target) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        final Path checkedTarget = Objects.requireNonNull(target, "target");
        final List<String> command = new ArrayList<>();
        command.add(pgDump.toString());
        command.add("-h");
        command.add(checkedConnectionInfo.host());
        command.add("-p");
        command.add(Integer.toString(checkedConnectionInfo.port()));
        command.add("-U");
        command.add(checkedConnectionInfo.username());
        command.add("-d");
        command.add(checkedConnectionInfo.database());
        command.add("-Fc");
        command.add("-f");
        command.add(checkedTarget.toString());

        return CommandRequest.of(command, timeout)
                .withEnvironmentVariable(PGPASSWORD, checkedConnectionInfo.password().reveal());
    }
}
