package eu.virtualparadox.managedpostgres.lifecycle.start;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.runtime.RuntimeBinaryLocator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Initializes a PostgreSQL data directory with {@code initdb}.
 */
public final class InitDbService {

    private static final String INITDB_AUTH_METHOD = "scram-sha-256";

    private final Path runtimeDirectory;
    private final CommandRunner commandRunner;

    /**
     * Creates an initdb service.
     *
     * @param runtimeDirectory PostgreSQL runtime directory
     * @param commandRunner command runner
     */
    public InitDbService(final Path runtimeDirectory, final CommandRunner commandRunner) {
        this.runtimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
    }

    /**
     * Initializes the supplied data directory.
     *
     * @param dataDirectory PostgreSQL data directory
     * @param credentials PostgreSQL credentials
     * @param passwordFile temporary password file path
     * @param timeout command timeout
     */
    public void initialize(
            final Path dataDirectory, final Credentials credentials, final Path passwordFile, final Duration timeout) {
        final CommandResult result;
        try {
            result = commandRunner.run(CommandRequest.of(command(dataDirectory, credentials, passwordFile), timeout));
        } catch (final ManagedPostgresException exception) {
            throw commandFailure("PostgreSQL initdb command failed", exception);
        }
        if (!result.successful()) {
            throw new PostgresStartupException("PostgreSQL initdb failed", diagnostic(result));
        }
    }

    private List<String> command(final Path dataDirectory, final Credentials credentials, final Path passwordFile) {
        final Path checkedDataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        final Credentials checkedCredentials = Objects.requireNonNull(credentials, "credentials");
        final Path checkedPasswordFile = Objects.requireNonNull(passwordFile, "passwordFile");

        return List.of(
                RuntimeBinaryLocator.resolveBinary(runtimeDirectory, "initdb").toString(),
                "-D",
                checkedDataDirectory.toString(),
                "-U",
                checkedCredentials.username(),
                "--auth-host=" + INITDB_AUTH_METHOD,
                "--auth-local=" + INITDB_AUTH_METHOD,
                "--pwfile=" + checkedPasswordFile);
    }

    private static DiagnosticReport diagnostic(final CommandResult commandResult) {
        return new DiagnosticReport(List.of(new DiagnosticSection(
                "initdb",
                Map.of(
                        "command", commandResult.renderedCommand(),
                        "exitCode", Integer.toString(commandResult.exitCode()),
                        "stdout", commandResult.stdout(),
                        "stderr", commandResult.stderr()))));
    }

    private static PostgresStartupException commandFailure(
            final String message, final ManagedPostgresException exception) {
        final List<DiagnosticSection> sections = new ArrayList<>();
        sections.add(new DiagnosticSection("initdb-command-failure", Map.of("message", exceptionMessage(exception))));
        sections.addAll(exception.diagnosticReport().sections());

        return new PostgresStartupException(message, exception, new DiagnosticReport(sections));
    }

    private static String exceptionMessage(final RuntimeException exception) {
        return Objects.requireNonNullElse(
                exception.getMessage(), exception.getClass().getSimpleName());
    }
}
