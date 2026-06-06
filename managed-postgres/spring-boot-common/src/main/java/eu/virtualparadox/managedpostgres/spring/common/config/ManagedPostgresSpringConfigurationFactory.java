package eu.virtualparadox.managedpostgres.spring.common.config;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Maps Spring Boot managed PostgreSQL properties to the public core lifecycle builder.
 */
public final class ManagedPostgresSpringConfigurationFactory {

    private final Supplier<ManagedPostgresBuilder> builderSupplier;

    /**
     * Creates a configuration factory backed by the persistent local managed PostgreSQL builder.
     */
    public ManagedPostgresSpringConfigurationFactory() {
        this(ManagedPostgres::local);
    }

    ManagedPostgresSpringConfigurationFactory(final Supplier<ManagedPostgresBuilder> builderSupplier) {
        this.builderSupplier = Objects.requireNonNull(builderSupplier, "builderSupplier");
    }

    /**
     * Creates a managed PostgreSQL configuration from Spring Boot properties without starting PostgreSQL.
     *
     * @param properties Spring Boot managed PostgreSQL properties
     * @return managed PostgreSQL lifecycle object
     */
    public ManagedPostgres create(final ManagedPostgresSpringProperties properties) {
        final ManagedPostgresSpringProperties checkedProperties = Objects.requireNonNull(properties, "properties");
        if (!checkedProperties.enabled()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.enabled must be true before creating PostgreSQL");
        }

        final ManagedPostgresBuilder base = Objects.requireNonNull(builderSupplier.get(), "builder")
                .name(checkedProperties.name())
                .version(checkedProperties.postgresqlVersion())
                .storageProjectLocal(checkedProperties.storage().path());
        ManagedPostgresBuilder builder = ManagedPostgresConfigurer.of(base)
                .runtime(ManagedPostgresSpringRuntimeMapper.runtimeSource(checkedProperties.runtime()));
        builder = ManagedPostgresSpringNetworkMapper.configure(builder, checkedProperties.network());
        builder = configure(builder, checkedProperties.configuration());
        builder = ManagedPostgresSpringClusterMapper.configure(builder, checkedProperties.cluster());
        builder = ManagedPostgresSpringLifecycleMapper.configure(builder, checkedProperties.lifecycle());

        return builder.build();
    }

    private static ManagedPostgresBuilder configure(
            final ManagedPostgresBuilder builder,
            final ManagedPostgresSpringProperties.ConfigurationProperties properties) {
        final ManagedPostgresBuilder checkedBuilder = Objects.requireNonNull(builder, "builder");
        final ManagedPostgresSpringProperties.ConfigurationProperties checkedProperties =
                Objects.requireNonNull(properties, "properties");
        final ManagedPostgresBuilder configuredBuilder;
        if (checkedProperties.isEmpty()) {
            configuredBuilder = checkedBuilder;
        } else {
            configuredBuilder = ManagedPostgresConfigurer.of(checkedBuilder)
                    .configuration(postgresConfiguration(checkedProperties));
        }

        return configuredBuilder;
    }

    private static PostgresConfiguration postgresConfiguration(
            final ManagedPostgresSpringProperties.ConfigurationProperties properties) {
        PostgresConfiguration postgresConfiguration = properties
                .preset()
                .map(ManagedPostgresSpringConfigurationFactory::preset)
                .orElse(PostgresConfiguration.defaults());
        if (properties.maxConnections().isPresent()) {
            postgresConfiguration = postgresConfiguration.maxConnections(
                    properties.maxConnections().get().intValue());
        }
        if (properties.sharedBuffers().isPresent()) {
            postgresConfiguration = postgresConfiguration.sharedBuffers(
                    properties.sharedBuffers().get());
        }
        if (properties.tempBuffers().isPresent()) {
            postgresConfiguration =
                    postgresConfiguration.tempBuffers(properties.tempBuffers().get());
        }
        if (properties.statementTimeoutSeconds().isPresent()) {
            postgresConfiguration = postgresConfiguration.statementTimeoutSeconds(
                    properties.statementTimeoutSeconds().get().intValue());
        }

        return postgresConfiguration;
    }

    private static PostgresConfiguration preset(final String value) {
        return switch (value) {
            case "tiny" -> Resources.tiny();
            case "small" -> Resources.small();
            case "ci" -> Resources.ci();
            default -> throw new ManagedPostgresSpringException(
                    "managed-postgres.configuration.preset must be tiny, small, or ci");
        };
    }
}
