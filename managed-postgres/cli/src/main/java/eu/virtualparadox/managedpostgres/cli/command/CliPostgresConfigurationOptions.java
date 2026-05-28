package eu.virtualparadox.managedpostgres.cli.command;

import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import java.util.Objects;
import java.util.Optional;
import picocli.CommandLine.Option;

/**
 * PostgreSQL tuning options shared by managed-postgres CLI commands.
 */
final class CliPostgresConfigurationOptions {

    private Optional<String> resourcePreset;
    private Optional<Integer> maxConnections;
    private Optional<String> sharedBuffers;
    private Optional<String> tempBuffers;
    private Optional<Integer> statementTimeoutSeconds;

    /**
     * Creates empty PostgreSQL tuning options.
     */
    CliPostgresConfigurationOptions() {
        resourcePreset = Optional.empty();
        maxConnections = Optional.empty();
        sharedBuffers = Optional.empty();
        tempBuffers = Optional.empty();
        statementTimeoutSeconds = Optional.empty();
    }

    @Option(names = "--resource-preset", description = "PostgreSQL tuning preset: tiny, small, or ci")
    void useResourcePreset(final String value) {
        resourcePreset = Optional.of(value);
    }

    @Option(names = "--max-connections", description = "PostgreSQL max_connections override")
    void useMaxConnections(final int value) {
        maxConnections = Optional.of(value);
    }

    @Option(names = "--shared-buffers", description = "PostgreSQL shared_buffers override")
    void useSharedBuffers(final String value) {
        sharedBuffers = Optional.of(value);
    }

    @Option(names = "--temp-buffers", description = "PostgreSQL temp_buffers override")
    void useTempBuffers(final String value) {
        tempBuffers = Optional.of(value);
    }

    @Option(names = "--statement-timeout-seconds", description = "PostgreSQL statement_timeout override in seconds")
    void useStatementTimeoutSeconds(final int value) {
        statementTimeoutSeconds = Optional.of(value);
    }

    /**
     * Applies direct CLI tuning options over an existing PostgreSQL configuration.
     *
     * @param configuration existing PostgreSQL configuration
     * @return updated PostgreSQL configuration
     */
    PostgresConfiguration applyTo(final PostgresConfiguration configuration) {
        PostgresConfiguration postgresConfiguration = Objects.requireNonNull(configuration, "configuration");
        if (resourcePreset.isPresent()) {
            postgresConfiguration = preset(resourcePreset.get());
        }
        if (maxConnections.isPresent()) {
            postgresConfiguration = postgresConfiguration.maxConnections(maxConnections.get().intValue());
        }
        if (sharedBuffers.isPresent()) {
            postgresConfiguration = postgresConfiguration.sharedBuffers(sharedBuffers.get());
        }
        if (tempBuffers.isPresent()) {
            postgresConfiguration = postgresConfiguration.tempBuffers(tempBuffers.get());
        }
        if (statementTimeoutSeconds.isPresent()) {
            postgresConfiguration =
                    postgresConfiguration.statementTimeoutSeconds(statementTimeoutSeconds.get().intValue());
        }

        return postgresConfiguration;
    }

    private static PostgresConfiguration preset(final String value) {
        return switch (value) {
            case "tiny" -> Resources.tiny();
            case "small" -> Resources.small();
            case "ci" -> Resources.ci();
            default -> throw new IllegalArgumentException("resource preset must be tiny, small, or ci");
        };
    }
}
