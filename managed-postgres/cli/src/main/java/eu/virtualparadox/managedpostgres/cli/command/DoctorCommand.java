package eu.virtualparadox.managedpostgres.cli.command;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.output.CliFormatOptions;
import eu.virtualparadox.managedpostgres.cli.command.output.CliOutputFormat;
import eu.virtualparadox.managedpostgres.cli.command.support.CliPostgresCommandContext;
import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * Prints non-mutating managed PostgreSQL diagnostics without starting PostgreSQL.
 */
@Command(
        name = "doctor",
        description = "Print managed PostgreSQL diagnostics.",
        sortOptions = false)
public final class DoctorCommand implements Callable<Integer> {

    @Mixin
    private final CliCommonOptions commonOptions = new CliCommonOptions();

    @Mixin
    private final CliFormatOptions formatOptions = new CliFormatOptions();

    private final PrintWriter output;
    private final CliPostgresCommandContext context;

    /**
     * Creates a doctor command.
     *
     * @param output standard command output
     */
    public DoctorCommand(final PrintWriter output) {
        this(output, new CliPostgresCommandContext());
    }

    /**
     * Creates a doctor command with explicit factory and configuration loader.
     *
     * @param output standard command output
     * @param postgresFactory managed-postgres factory
     * @param configurationLoader configuration loader
     */
    public DoctorCommand(
            final PrintWriter output,
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory,
            final CliYamlConfigurationLoader configurationLoader) {
        this(output, new CliPostgresCommandContext(postgresFactory, configurationLoader));
    }

    private DoctorCommand(final PrintWriter output, final CliPostgresCommandContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.context = Objects.requireNonNull(context, "context");
    }

    /**
     * Prints current diagnostics and returns the documented success exit code.
     *
     * @return command exit code
     */
    @Override
    public Integer call() {
        final CliManagedPostgresConfiguration configuration = context.configuration(commonOptions);
        final DoctorReport report = context.managedPostgres(configuration).doctor();

        if (CliOutputFormat.JSON.equals(formatOptions.format())) {
            output.print(report.renderJson());
        } else {
            output.print(report.renderText());
        }

        return CliExitCode.OK.code();
    }
}
