package eu.virtualparadox.managedpostgres.cli.config;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
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

        final ManagedPostgresBuilder base = ManagedPostgres.local()
                .name(checkedConfiguration.name())
                .version(checkedConfiguration.postgresqlVersion())
                .storageProjectLocal(checkedConfiguration.storagePath());
        return ManagedPostgresConfigurer.of(base)
                .runtime(checkedConfiguration.runtimeSource())
                .configuration(checkedConfiguration.postgresConfiguration())
                .network(checkedConfiguration.network())
                .attachPolicy(checkedConfiguration.attachPolicy())
                .stopPolicy(checkedConfiguration.stopPolicy())
                .build();
    }
}
