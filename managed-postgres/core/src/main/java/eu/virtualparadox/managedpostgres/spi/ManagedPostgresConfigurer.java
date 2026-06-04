package eu.virtualparadox.managedpostgres.spi;

import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder;
import java.util.Objects;

/**
 * Integration SPI for applying complete configuration value objects to a managed PostgreSQL builder.
 *
 * <p><strong>Not for end users.</strong> The public {@link ManagedPostgresBuilder} is a fluent DSL;
 * this SPI exists only for integrations (Spring Boot, CLI) that assemble a complete value object from
 * external configuration and need to apply it programmatically.
 */
public interface ManagedPostgresConfigurer extends ManagedPostgresBuilder {

    /**
     * Views a builder as the configurer SPI.
     *
     * @param builder a managed PostgreSQL builder produced by this library
     * @return the same builder, viewed as the configurer SPI
     * @throws NullPointerException if the builder is {@code null}
     * @throws ClassCastException if the builder was not produced by this library
     */
    static ManagedPostgresConfigurer of(final ManagedPostgresBuilder builder) {
        return (ManagedPostgresConfigurer) Objects.requireNonNull(builder, "builder");
    }

    /**
     * Applies a complete storage configuration.
     *
     * @param storage storage configuration
     * @return the configurer, so value objects can be applied fluently
     */
    ManagedPostgresConfigurer storage(Storage storage);

    /**
     * Applies a complete network configuration.
     *
     * @param network network configuration
     * @return the configurer, so value objects can be applied fluently
     */
    ManagedPostgresConfigurer network(Network network);

    /**
     * Applies a complete primary application database bootstrap configuration.
     *
     * @param cluster cluster bootstrap configuration
     * @return the configurer, so value objects can be applied fluently
     */
    ManagedPostgresConfigurer cluster(ClusterBootstrap cluster);

    /**
     * Applies a complete runtime source.
     *
     * @param runtimeSource runtime source
     * @return the configurer, so value objects can be applied fluently
     */
    ManagedPostgresConfigurer runtime(RuntimeSource runtimeSource);

    /**
     * Applies a complete PostgreSQL server configuration.
     *
     * @param configuration PostgreSQL server settings
     * @return the configurer, so value objects can be applied fluently
     */
    ManagedPostgresConfigurer configuration(PostgresConfiguration configuration);
}
