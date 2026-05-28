package eu.virtualparadox.managedpostgres.cli.command;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RestoreOptions;
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
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Restores a managed PostgreSQL logical backup with explicit safety flags.
 */
@Command(
        name = "restore",
        description = "Restore a managed PostgreSQL backup.",
        sortOptions = false)
public final class RestoreCommand implements Callable<Integer> {

    @Mixin
    private final CliCommonOptions commonOptions = new CliCommonOptions();

    @Option(names = "--drop-current-database", description = "allow destructive restore into the current database")
    private boolean dropCurrentDatabase;

    @Option(names = "--create-safety-backup", description = "create a safety backup before restore")
    private boolean createSafetyBackup;

    private Optional<Path> backupPath;

    private final PrintWriter output;
    private final CliPostgresCommandContext context;

    /**
     * Creates a restore command.
     *
     * @param output standard command output
     */
    public RestoreCommand(final PrintWriter output) {
        this(output, new CliPostgresCommandContext());
    }

    /**
     * Creates a restore command with explicit factory and configuration loader.
     *
     * @param output standard command output
     * @param postgresFactory managed-postgres factory
     * @param configurationLoader configuration loader
     */
    public RestoreCommand(
            final PrintWriter output,
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory,
            final CliYamlConfigurationLoader configurationLoader) {
        this(output, new CliPostgresCommandContext(postgresFactory, configurationLoader));
    }

    private RestoreCommand(final PrintWriter output, final CliPostgresCommandContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.context = Objects.requireNonNull(context, "context");
        backupPath = Optional.empty();
    }

    @Parameters(index = "0", paramLabel = "BACKUP", description = "backup file to restore")
    void useBackupPath(final Path value) {
        backupPath = Optional.of(value);
    }

    /**
     * Restores the requested backup and returns the documented success exit code.
     *
     * @return command exit code
     */
    @Override
    public Integer call() {
        requireFlag(dropCurrentDatabase, "--drop-current-database");
        requireFlag(createSafetyBackup, "--create-safety-backup");

        final CliManagedPostgresConfiguration configuration = context.configuration(commonOptions)
                .withAttachPolicy(AttachPolicy.ATTACH_IF_COMPATIBLE)
                .withStopPolicy(StopPolicy.KEEP_RUNNING);
        final Path backup = backupPath.orElseThrow(() -> new IllegalArgumentException("backup path is required"));
        final RestoreOptions options = RestoreOptions.builder()
                .dropCurrentDatabase(true)
                .createSafetyBackup(true)
                .build();

        try (RunningPostgres runningPostgres = context.managedPostgres(configuration).start()) {
            runningPostgres.restoreFrom(backup, options);
        }

        output.println("restored=" + backup);

        return CliExitCode.OK.code();
    }

    private static void requireFlag(final boolean enabled, final String flagName) {
        if (!enabled) {
            throw new IllegalArgumentException(flagName + " is required for restore");
        }
    }
}
