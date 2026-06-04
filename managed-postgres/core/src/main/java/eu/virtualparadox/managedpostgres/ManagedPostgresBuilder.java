package eu.virtualparadox.managedpostgres;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresMode;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.internal.DefaultManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;

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
     * Stores the cluster under a project-local directory.
     *
     * @param path project-local storage directory
     * @return updated builder
     */
    public ManagedPostgresBuilder storageProjectLocal(String path);

    /**
     * Stores the cluster under a project-local directory.
     *
     * @param path project-local storage directory
     * @return updated builder
     */
    public ManagedPostgresBuilder storageProjectLocal(Path path);

    /**
     * Stores the cluster in a temporary directory removed when the instance is closed.
     *
     * @return updated builder
     */
    public ManagedPostgresBuilder temporaryStorage();

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
     * Sets the application owner credentials.
     *
     * @param username application owner username
     * @param password application owner password
     * @return updated builder
     */
    public ManagedPostgresBuilder credentials(String username, String password);

    /**
     * Sets the application owner credentials.
     *
     * @param username application owner username
     * @param password application owner password
     * @return updated builder
     */
    public ManagedPostgresBuilder credentials(String username, Secret password);

    /**
     * Uses generated, non-persistent credentials.
     *
     * @return updated builder
     */
    public ManagedPostgresBuilder generatedCredentials();

    /**
     * Uses generated credentials persisted across restarts.
     *
     * @return updated builder
     */
    public ManagedPostgresBuilder generatedPersistentCredentials();

    /**
     * Uses local-trust-only authentication (no password).
     *
     * @return updated builder
     */
    public ManagedPostgresBuilder trustLocalOnly();

    /**
     * Enters the fluent section for loopback-only PostgreSQL network configuration.
     *
     * @return network configuration section
     */
    public NetworkSection network();

    /**
     * Enters the fluent section for the primary application database bootstrap.
     *
     * @return cluster bootstrap section
     */
    public ClusterSection cluster();

    /**
     * Returns a builder with the configured PostgreSQL server settings.
     *
     * @param configuration PostgreSQL server settings
     * @return updated builder
     */
    public ManagedPostgresBuilder configuration(PostgresConfiguration configuration);

    /**
     * Enters the fluent section for PostgreSQL server tuning.
     *
     * @return server configuration section
     */
    public ConfigurationSection serverConfiguration();

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
