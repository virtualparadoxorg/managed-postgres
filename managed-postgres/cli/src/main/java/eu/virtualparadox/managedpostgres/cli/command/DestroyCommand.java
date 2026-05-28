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
import picocli.CommandLine.Option;

/**
 * Destroys persistent managed PostgreSQL cluster storage after explicit confirmation.
 */
@Command(
        name = "destroy",
        description = "Destroy persistent managed PostgreSQL cluster storage.",
        sortOptions = false)
public final class DestroyCommand implements Callable<Integer> {

    @Mixin
    private final CliCommonOptions commonOptions = new CliCommonOptions();

    @Option(names = "--force", description = "confirm destructive cluster deletion")
    private boolean force;

    private final PrintWriter output;
    private final CliPostgresCommandContext context;

    /**
     * Creates a destroy command.
     *
     * @param output standard command output
     */
    public DestroyCommand(final PrintWriter output) {
        this(output, new CliPostgresCommandContext());
    }

    /**
     * Creates a destroy command with explicit factory and configuration loader.
     *
     * @param output standard command output
     * @param postgresFactory managed-postgres factory
     * @param configurationLoader configuration loader
     */
    public DestroyCommand(
            final PrintWriter output,
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory,
            final CliYamlConfigurationLoader configurationLoader) {
        this(output, new CliPostgresCommandContext(postgresFactory, configurationLoader));
    }

    private DestroyCommand(final PrintWriter output, final CliPostgresCommandContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.context = Objects.requireNonNull(context, "context");
        force = false;
    }

    /**
     * Destroys persistent cluster storage and returns the documented success exit code.
     *
     * @return command exit code
     */
    @Override
    public Integer call() {
        requireForce();
        final CliManagedPostgresConfiguration configuration = context.configuration(commonOptions);
        context.managedPostgres(configuration).destroyCluster();
        output.println("destroy-complete");

        return CliExitCode.OK.code();
    }

    private void requireForce() {
        if (!force) {
            throw new IllegalArgumentException("--force is required for destroy");
        }
    }
}
