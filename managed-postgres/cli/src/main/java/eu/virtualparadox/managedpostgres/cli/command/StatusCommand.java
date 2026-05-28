package eu.virtualparadox.managedpostgres.cli.command;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.output.CliFormatOptions;
import eu.virtualparadox.managedpostgres.cli.command.output.CliOutputFormat;
import eu.virtualparadox.managedpostgres.cli.command.support.CliPostgresCommandContext;
import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * Prints the current managed PostgreSQL lifecycle status without starting PostgreSQL.
 */
@Command(
        name = "status",
        description = "Print managed PostgreSQL status.",
        sortOptions = false)
public final class StatusCommand implements Callable<Integer> {

    @Mixin
    private final CliCommonOptions commonOptions = new CliCommonOptions();

    @Mixin
    private final CliFormatOptions formatOptions = new CliFormatOptions();

    private final PrintWriter output;
    private final CliPostgresCommandContext context;

    /**
     * Creates a status command.
     *
     * @param output standard command output
     */
    public StatusCommand(final PrintWriter output) {
        this(output, new CliPostgresCommandContext());
    }

    /**
     * Creates a status command with explicit factory and configuration loader.
     *
     * @param output standard command output
     * @param postgresFactory managed-postgres factory
     * @param configurationLoader configuration loader
     */
    public StatusCommand(
            final PrintWriter output,
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory,
            final CliYamlConfigurationLoader configurationLoader) {
        this(output, new CliPostgresCommandContext(postgresFactory, configurationLoader));
    }

    private StatusCommand(final PrintWriter output, final CliPostgresCommandContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.context = Objects.requireNonNull(context, "context");
    }

    /**
     * Prints current status and returns the documented success exit code.
     *
     * @return command exit code
     */
    @Override
    public Integer call() {
        final CliManagedPostgresConfiguration configuration = context.configuration(commonOptions);
        final PostgresStatus status = context.managedPostgres(configuration).status();

        if (CliOutputFormat.JSON.equals(formatOptions.format())) {
            output.println("{\"status\":\"" + status.name() + "\"}");
        } else {
            output.println(status.name());
        }

        return CliExitCode.OK.code();
    }
}
