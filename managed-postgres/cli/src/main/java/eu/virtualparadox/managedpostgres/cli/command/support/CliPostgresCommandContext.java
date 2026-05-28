package eu.virtualparadox.managedpostgres.cli.command.support;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.CliCommonOptions;
import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresFactory;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import java.util.Objects;
import java.util.function.Function;

/**
 * Context holder for CLI commands that need configuration loading and managed-postgres creation.
 */
public final class CliPostgresCommandContext {

    private final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory;
    private final CliYamlConfigurationLoader configurationLoader;

    /**
     * Creates a CLI command context with default configuration loading and facade creation.
     */
    public CliPostgresCommandContext() {
        this(new CliManagedPostgresFactory()::create, new CliYamlConfigurationLoader());
    }

    /**
     * Creates a CLI command context with explicit collaborators.
     *
     * @param postgresFactory managed-postgres factory
     * @param configurationLoader YAML configuration loader
     */
    public CliPostgresCommandContext(
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> postgresFactory,
            final CliYamlConfigurationLoader configurationLoader) {
        this.postgresFactory = Objects.requireNonNull(postgresFactory, "postgresFactory");
        this.configurationLoader = Objects.requireNonNull(configurationLoader, "configurationLoader");
    }

    /**
     * Resolves effective CLI configuration for the given common options.
     *
     * @param commonOptions shared command options
     * @return effective CLI configuration
     */
    public CliManagedPostgresConfiguration configuration(final CliCommonOptions commonOptions) {
        return CliCommandSupport.configuration(commonOptions, configurationLoader);
    }

    /**
     * Creates a managed-postgres facade for the given CLI configuration.
     *
     * @param configuration effective CLI configuration
     * @return managed-postgres facade
     */
    public ManagedPostgres managedPostgres(final CliManagedPostgresConfiguration configuration) {
        return CliCommandSupport.managedPostgres(postgresFactory, configuration);
    }
}
