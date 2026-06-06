package eu.virtualparadox.managedpostgres.cli.command.support;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.CliCommonOptions;
import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared CLI command support for configuration loading and managed-postgres creation.
 */
public final class CliCommandSupport {

    private CliCommandSupport() {}

    /**
     * Resolves effective CLI configuration from common command options.
     *
     * @param commonOptions shared command options
     * @param loader YAML configuration loader
     * @return effective CLI configuration
     */
    public static CliManagedPostgresConfiguration configuration(
            final CliCommonOptions commonOptions, final CliYamlConfigurationLoader loader) {
        final CliManagedPostgresConfiguration configuration;

        try {
            configuration = Objects.requireNonNull(commonOptions, "commonOptions")
                    .toConfiguration(Objects.requireNonNull(loader, "loader"));
        } catch (IOException exception) {
            throw new IllegalArgumentException(configurationReadMessage(exception), exception);
        }

        return configuration;
    }

    /**
     * Creates a managed-postgres facade from the checked CLI configuration.
     *
     * @param factory managed-postgres factory
     * @param configuration effective CLI configuration
     * @return managed-postgres facade
     */
    public static ManagedPostgres managedPostgres(
            final Function<CliManagedPostgresConfiguration, ManagedPostgres> factory,
            final CliManagedPostgresConfiguration configuration) {
        final Function<CliManagedPostgresConfiguration, ManagedPostgres> checkedFactory =
                Objects.requireNonNull(factory, "factory");

        return Objects.requireNonNull(checkedFactory.apply(configuration), "managedPostgres");
    }

    private static String configurationReadMessage(final IOException exception) {
        return "configuration file could not be read: "
                + StringUtils.defaultIfBlank(
                        exception.getMessage(), exception.getClass().getName());
    }
}
