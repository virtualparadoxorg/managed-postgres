package eu.virtualparadox.managedpostgres.cli.command;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.support.CliPostgresCommandContext;
import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Starts managed PostgreSQL and prints non-secret connection details.
 */
@Command(name = "start", description = "Start managed PostgreSQL.", sortOptions = false)
public final class StartCommand implements Callable<Integer> {

    @Mixin
    private final CliCommonOptions commonOptions = new CliCommonOptions();

    private final PrintWriter output;
    private final CliPostgresCommandContext context;
    private StopPolicy stopPolicy;

    /**
     * Creates a start command.
     *
     * @param output standard command output
     */
    public StartCommand(final PrintWriter output) {
        this(output, new CliPostgresCommandContext());
    }

    /**
     * Creates a start command with explicit factory and configuration loader.
     *
     * @param output standard command output
     * @param postgresFactory managed-postgres factory
     * @param configurationLoader configuration loader
     */
    public StartCommand(
            final PrintWriter output,
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory,
            final CliYamlConfigurationLoader configurationLoader) {
        this(output, new CliPostgresCommandContext(postgresFactory, configurationLoader));
    }

    private StartCommand(final PrintWriter output, final CliPostgresCommandContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.context = Objects.requireNonNull(context, "context");
        stopPolicy = StopPolicy.KEEP_RUNNING;
    }

    @Option(names = "--keep-running", description = "leave PostgreSQL running when the CLI exits")
    void useKeepRunning(final boolean value) {
        if (value) {
            stopPolicy = StopPolicy.KEEP_RUNNING;
        }
    }

    @Option(names = "--stop-on-close", description = "stop PostgreSQL when the start command closes its handle")
    void useStopOnClose(final boolean value) {
        if (value) {
            stopPolicy = StopPolicy.STOP_ON_CLOSE;
        }
    }

    /**
     * Starts PostgreSQL and returns the documented success exit code.
     *
     * @return command exit code
     */
    @Override
    public Integer call() {
        final CliManagedPostgresConfiguration configuration =
                context.configuration(commonOptions).withStopPolicy(stopPolicy);

        try (RunningPostgres runningPostgres =
                context.managedPostgres(configuration).start()) {
            renderConnection(runningPostgres.connectionInfo());
        }

        return CliExitCode.OK.code();
    }

    private void renderConnection(final PostgresConnectionInfo connectionInfo) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");

        output.println("started");
        output.println("host=" + checkedConnectionInfo.host());
        output.println("port=" + checkedConnectionInfo.port());
        output.println("database=" + checkedConnectionInfo.database());
        output.println("username=" + checkedConnectionInfo.username());
    }
}
