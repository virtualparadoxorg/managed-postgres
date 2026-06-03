package eu.virtualparadox.managedpostgres.cli.command.lifecycle;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.CliCommonOptions;
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
 * Restarts managed PostgreSQL and prints non-secret connection details.
 */
@Command(name = "restart", description = "Restart managed PostgreSQL.", sortOptions = false)
public final class RestartCommand implements Callable<Integer> {

    @Mixin
    private final CliCommonOptions commonOptions = new CliCommonOptions();

    private final PrintWriter output;
    private final CliPostgresCommandContext context;
    private StopPolicy stopPolicy;

    /**
     * Creates a restart command.
     *
     * @param output standard command output
     */
    public RestartCommand(final PrintWriter output) {
        this(output, new CliPostgresCommandContext());
    }

    /**
     * Creates a restart command with explicit factory and configuration loader.
     *
     * @param output standard command output
     * @param postgresFactory managed-postgres factory
     * @param configurationLoader configuration loader
     */
    public RestartCommand(
            final PrintWriter output,
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory,
            final CliYamlConfigurationLoader configurationLoader) {
        this(output, new CliPostgresCommandContext(postgresFactory, configurationLoader));
    }

    private RestartCommand(final PrintWriter output, final CliPostgresCommandContext context) {
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

    @Option(names = "--stop-on-close", description = "stop PostgreSQL when the restart command closes its handle")
    void useStopOnClose(final boolean value) {
        if (value) {
            stopPolicy = StopPolicy.STOP_ON_CLOSE;
        }
    }

    @Override
    public Integer call() {
        final CliManagedPostgresConfiguration configuration =
                context.configuration(commonOptions).withStopPolicy(stopPolicy);
        try (ManagedPostgres managedPostgres = context.managedPostgres(configuration)) {
            managedPostgres.stop();
            try (RunningPostgres runningPostgres = managedPostgres.start()) {
                renderConnection(runningPostgres.connectionInfo());
            }
        }

        return CliExitCode.OK.code();
    }

    private void renderConnection(final PostgresConnectionInfo connectionInfo) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");

        output.println("restarted");
        output.println("host=" + checkedConnectionInfo.host());
        output.println("port=" + checkedConnectionInfo.port());
        output.println("database=" + checkedConnectionInfo.database());
        output.println("username=" + checkedConnectionInfo.username());
    }
}
