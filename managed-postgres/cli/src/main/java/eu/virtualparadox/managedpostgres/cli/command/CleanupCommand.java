package eu.virtualparadox.managedpostgres.cli.command;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
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
 * Runs explicit non-destructive managed PostgreSQL cleanup.
 */
@Command(
        name = "cleanup",
        description = "Run explicit non-destructive managed PostgreSQL cleanup.",
        sortOptions = false)
public final class CleanupCommand implements Callable<Integer> {

    @Mixin
    private final CliCommonOptions commonOptions = new CliCommonOptions();

    private final PrintWriter output;
    private final CliPostgresCommandContext context;

    /**
     * Creates a cleanup command.
     *
     * @param output standard command output
     */
    public CleanupCommand(final PrintWriter output) {
        this(output, new CliPostgresCommandContext());
    }

    /**
     * Creates a cleanup command with explicit factory and configuration loader.
     *
     * @param output standard command output
     * @param postgresFactory managed-postgres factory
     * @param configurationLoader configuration loader
     */
    public CleanupCommand(
            final PrintWriter output,
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory,
            final CliYamlConfigurationLoader configurationLoader) {
        this(output, new CliPostgresCommandContext(postgresFactory, configurationLoader));
    }

    private CleanupCommand(final PrintWriter output, final CliPostgresCommandContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.context = Objects.requireNonNull(context, "context");
    }

    /**
     * Executes explicit cleanup and returns the documented success exit code.
     *
     * @return command exit code
     */
    @Override
    public Integer call() {
        final CliManagedPostgresConfiguration configuration = context.configuration(commonOptions);
        context.managedPostgres(configuration).cleanup();
        output.println("cleanup-complete");

        return CliExitCode.OK.code();
    }
}
