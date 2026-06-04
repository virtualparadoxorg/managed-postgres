package eu.virtualparadox.managedpostgres;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresMode;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.internal.DefaultManagedPostgresBuilder;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * Immutable builder for managed PostgreSQL instances.
 */
@SuppressWarnings("PMD.TooManyMethods")
public interface ManagedPostgresBuilder {

    /**
     * Creates a persistent local PostgreSQL builder.
     *
     * @return persistent local builder
     */
    public static ManagedPostgresBuilder builder() {
        return new DefaultManagedPostgresBuilder(ManagedPostgresMode.PERSISTENT_LOCAL);
    }

    /**
     * Creates a persistent local PostgreSQL builder.
     *
     * @return persistent local builder
     */
    public static ManagedPostgresBuilder local() {
        return new DefaultManagedPostgresBuilder(ManagedPostgresMode.PERSISTENT_LOCAL);
    }

    /**
     * Creates a temporary PostgreSQL builder.
     *
     * @return temporary builder
     */
    public static ManagedPostgresBuilder temporary() {
        return new DefaultManagedPostgresBuilder(ManagedPostgresMode.TEMPORARY);
    }

    /**
     * Returns a builder with the configured instance name.
     *
     * @param name PostgreSQL instance name
     * @return updated builder
     */
    public ManagedPostgresBuilder name(String name);

    /**
     * Returns a builder with the configured PostgreSQL version.
     *
     * @param postgresqlVersion PostgreSQL version
     * @return updated builder
     */
    public ManagedPostgresBuilder version(String postgresqlVersion);

    /**
     * Returns a builder with the configured storage.
     *
     * @param storage storage configuration
     * @return updated builder
     */
    public ManagedPostgresBuilder storage(Storage storage);

    /**
     * Returns a builder with the configured runtime source.
     *
     * @param runtimeSource runtime source
     * @return updated builder
     */
    public ManagedPostgresBuilder runtime(RuntimeSource runtimeSource);

    /**
     * Starts the fluent DSL for a downloaded runtime source.
     *
     * @return downloaded runtime configuration step
     */
    public DownloadedRuntimeDsl withDownloadedRuntime();

    /**
     * Returns a builder that resolves the PostgreSQL runtime from the system PATH.
     *
     * @return updated builder
     */
    public ManagedPostgresBuilder withSystemRuntime();

    /**
     * Returns a builder that uses a previously extracted runtime in the given directory.
     *
     * @param runtimeDirectory directory containing the PostgreSQL runtime
     * @return updated builder
     */
    public ManagedPostgresBuilder withExistingRuntime(Path runtimeDirectory);

    /**
     * Starts the fluent DSL for a classpath runtime archive (the checksum is mandatory).
     *
     * @param resource classpath ZIP archive resource (e.g. {@code /postgres-runtime.zip})
     * @param checksum expected archive checksum
     * @return classpath runtime configuration step
     */
    public ClasspathRuntimeDsl withClasspathRuntime(String resource, String checksum);

    /**
     * Returns a builder with the configured credentials.
     *
     * @param credentials credentials
     * @return updated builder
     */
    public ManagedPostgresBuilder credentials(Credentials credentials);

    /**
     * Enters the fluent section for loopback-only PostgreSQL network configuration.
     *
     * @return network configuration section
     */
    public NetworkSection network();

    /**
     * Returns a builder with customized localhost network and port selection.
     *
     * @param customizer network configuration customizer
     * @return updated builder
     */
    public ManagedPostgresBuilder network(UnaryOperator<Network> customizer);

    /**
     * Returns a builder with a customized primary application database bootstrap configuration.
     *
     * @param customizer cluster bootstrap customizer
     * @return updated builder
     */
    public ManagedPostgresBuilder cluster(UnaryOperator<ClusterBootstrap> customizer);

    /**
     * Returns a builder with the configured PostgreSQL server settings.
     *
     * @param configuration PostgreSQL server settings
     * @return updated builder
     */
    public ManagedPostgresBuilder configuration(PostgresConfiguration configuration);

    /**
     * Returns a builder with customized PostgreSQL server settings.
     *
     * @param customizer PostgreSQL server settings customizer
     * @return updated builder
     */
    public ManagedPostgresBuilder configuration(UnaryOperator<PostgresConfiguration> customizer);

    /**
     * Returns a builder that may reuse an existing compatible managed PostgreSQL instance.
     *
     * @return updated builder
     */
    public ManagedPostgresBuilder reuseExisting();

    /**
     * Returns a builder with the configured attach policy.
     *
     * @param attachPolicy attach policy
     * @return updated builder
     */
    public ManagedPostgresBuilder attachPolicy(AttachPolicy attachPolicy);

    /**
     * Returns a builder with the configured stop policy.
     *
     * @param stopPolicy stop policy
     * @return updated builder
     */
    public ManagedPostgresBuilder stopPolicy(StopPolicy stopPolicy);

    /**
     * Returns a builder with the configured upgrade policy.
     *
     * @param upgradePolicy upgrade policy
     * @return updated builder
     */
    public ManagedPostgresBuilder upgradePolicy(UpgradePolicy upgradePolicy);

    /**
     * Returns a builder with the configured config drift policy.
     *
     * @param configDriftPolicy config drift policy
     * @return updated builder
     */
    public ManagedPostgresBuilder configDriftPolicy(ConfigDriftPolicy configDriftPolicy);

    /**
     * Returns a builder with the configured cleanup and retention policy.
     *
     * @param cleanupPolicy cleanup and retention policy
     * @return updated builder
     */
    public ManagedPostgresBuilder cleanupPolicy(CleanupPolicy cleanupPolicy);

    /**
     * Enters the fluent section for PostgreSQL process log handling.
     *
     * @return log handling section
     */
    public LogsSection logs();

    /**
     * Builds a managed PostgreSQL instance.
     *
     * @return managed PostgreSQL instance
     */
    public ManagedPostgres build();

    /**
     * Builds and starts a managed PostgreSQL instance.
     *
     * @return running PostgreSQL handle
     */
    public RunningPostgres start();
}
