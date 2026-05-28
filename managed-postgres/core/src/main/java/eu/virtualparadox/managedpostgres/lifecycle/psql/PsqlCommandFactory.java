package eu.virtualparadox.managedpostgres.lifecycle.psql;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;

/**
 * Coordinates psql command factory behavior for managed PostgreSQL internals.
 */
public final class PsqlCommandFactory {

    private static final String PGPASSWORD = "PGPASSWORD";

    private final Path psql;
    private final Duration timeout;

    /**
     * Creates a PsqlCommandFactory instance.
     *
     * @param runtimeDirectory runtime directory value
     * @param timeout timeout value
     */
    public PsqlCommandFactory(final Path runtimeDirectory, final Duration timeout) {
        this.psql = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory").resolve("bin").resolve("psql");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Returns the query result.
     *
     * @param connectionInfo connection info value
     * @param query query value
     * @return query result
     */
    public CommandRequest query(final PostgresConnectionInfo connectionInfo, final String query) {
        return command(connectionInfo, List.of("-tAc", query));
    }

    /**
     * Returns the inline result.
     *
     * @param connectionInfo connection info value
     * @param sql sql value
     * @return inline result
     */
    public CommandRequest inline(final PostgresConnectionInfo connectionInfo, final String sql) {
        return command(connectionInfo, List.of("-c", sql));
    }

    /**
     * Returns the file result.
     *
     * @param connectionInfo connection info value
     * @param sqlFile sql file value
     * @return file result
     */
    public CommandRequest file(final PostgresConnectionInfo connectionInfo, final Path sqlFile) {
        return command(connectionInfo, List.of("-f", Objects.requireNonNull(sqlFile, "sqlFile").toString()));
    }

    private CommandRequest command(final PostgresConnectionInfo connectionInfo, final List<String> arguments) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        final List<String> command = new ArrayList<>();
        command.add(psql.toString());
        command.add("-h");
        command.add(checkedConnectionInfo.host());
        command.add("-p");
        command.add(Integer.toString(checkedConnectionInfo.port()));
        command.add("-U");
        command.add(checkedConnectionInfo.username());
        command.add("-d");
        command.add(checkedConnectionInfo.database());
        command.addAll(arguments);

        return CommandRequest.of(command, timeout)
                .withEnvironmentVariable(PGPASSWORD, checkedConnectionInfo.password().reveal());
    }
}
