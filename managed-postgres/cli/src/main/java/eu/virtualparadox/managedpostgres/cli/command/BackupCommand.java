package eu.virtualparadox.managedpostgres.cli.command;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.support.CliPostgresCommandContext;
import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/**
 * Creates a managed PostgreSQL logical backup.
 */
@Command(
        name = "backup",
        description = "Create a managed PostgreSQL backup.",
        sortOptions = false)
public final class BackupCommand implements Callable<Integer> {

    @Mixin
    private final CliCommonOptions commonOptions = new CliCommonOptions();

    private Optional<Path> backupPath;

    private final PrintWriter output;
    private final CliPostgresCommandContext context;

    /**
     * Creates a backup command.
     *
     * @param output standard command output
     */
    public BackupCommand(final PrintWriter output) {
        this(output, new CliPostgresCommandContext());
    }

    /**
     * Creates a backup command with explicit factory and configuration loader.
     *
     * @param output standard command output
     * @param postgresFactory managed-postgres factory
     * @param configurationLoader configuration loader
     */
    public BackupCommand(
            final PrintWriter output,
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory,
            final CliYamlConfigurationLoader configurationLoader) {
        this(output, new CliPostgresCommandContext(postgresFactory, configurationLoader));
    }

    private BackupCommand(final PrintWriter output, final CliPostgresCommandContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.context = Objects.requireNonNull(context, "context");
        backupPath = Optional.empty();
    }

    @Parameters(index = "0", paramLabel = "BACKUP", description = "backup target path")
    void useBackupPath(final Path value) {
        backupPath = Optional.of(value);
    }

    /**
     * Creates the requested backup and returns the documented success exit code.
     *
     * @return command exit code
     */
    @Override
    public Integer call() {
        final CliManagedPostgresConfiguration configuration = context.configuration(commonOptions)
                .withAttachPolicy(AttachPolicy.ATTACH_IF_COMPATIBLE)
                .withStopPolicy(StopPolicy.KEEP_RUNNING);
        final Path target = backupPath.orElseThrow(() -> new IllegalArgumentException("backup path is required"));

        try (RunningPostgres runningPostgres = context.managedPostgres(configuration).start()) {
            runningPostgres.backupTo(target);
        }

        output.println("backup-created=" + target);

        return CliExitCode.OK.code();
    }
}
