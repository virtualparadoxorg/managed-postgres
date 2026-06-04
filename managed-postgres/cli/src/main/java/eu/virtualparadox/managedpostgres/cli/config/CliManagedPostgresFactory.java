package eu.virtualparadox.managedpostgres.cli.config;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer;
import java.util.Objects;

/**
 * Creates managed PostgreSQL instances from effective CLI configuration.
 */
public final class CliManagedPostgresFactory {

    /**
     * Creates a CLI managed PostgreSQL factory.
     */
    public CliManagedPostgresFactory() {}

    /**
     * Creates a managed PostgreSQL instance through the public fluent API.
     *
     * @param configuration effective CLI configuration
     * @return managed PostgreSQL instance
     */
    public ManagedPostgres create(final CliManagedPostgresConfiguration configuration) {
        final CliManagedPostgresConfiguration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");

        ManagedPostgresBuilder builder = ManagedPostgres.local()
                .name(checkedConfiguration.name())
                .version(checkedConfiguration.postgresqlVersion())
                .storageProjectLocal(checkedConfiguration.storagePath());
        builder = ManagedPostgresConfigurer.of(builder).runtime(checkedConfiguration.runtimeSource());
        builder = ManagedPostgresConfigurer.of(builder).configuration(checkedConfiguration.postgresConfiguration());
        builder = ManagedPostgresConfigurer.of(builder).network(checkedConfiguration.network());
        return builder.attachPolicy(checkedConfiguration.attachPolicy())
                .stopPolicy(checkedConfiguration.stopPolicy())
                .build();
    }
}
