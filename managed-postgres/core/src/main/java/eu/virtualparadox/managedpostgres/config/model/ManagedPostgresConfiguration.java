package eu.virtualparadox.managedpostgres.config.model;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.logging.PostgresLogs;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable managed PostgreSQL configuration used by the builder and lifecycle service.
 *
 * @param name managed instance name
 * @param postgresqlVersion requested PostgreSQL version
 * @param storage storage configuration
 * @param runtimeSource runtime source
 * @param credentials PostgreSQL credentials
 * @param network localhost network and port selection configuration
 * @param clusterBootstrap primary application database bootstrap configuration
 * @param postgresConfiguration PostgreSQL server configuration settings
 * @param logs PostgreSQL process log handling
 * @param attachPolicy attach policy
 * @param stopPolicy stop policy
 * @param upgradePolicy upgrade policy
 * @param configDriftPolicy config drift policy
 * @param cleanupPolicy cleanup and retention policy
 */
@SuppressWarnings({
        // The immutable public configuration model intentionally gathers the full PostgreSQL lifecycle contract.
        "PMD.CouplingBetweenObjects"
})
public record ManagedPostgresConfiguration(
        String name,
        String postgresqlVersion,
        Storage storage,
        RuntimeSource runtimeSource,
        Credentials credentials,
        Network network,
        ClusterBootstrap clusterBootstrap,
        PostgresConfiguration postgresConfiguration,
        PostgresLogs logs,
        AttachPolicy attachPolicy,
        StopPolicy stopPolicy,
        UpgradePolicy upgradePolicy,
        ConfigDriftPolicy configDriftPolicy,
        CleanupPolicy cleanupPolicy) {

    /**
     * Creates immutable managed PostgreSQL configuration.
     *
     * @param name managed instance name
     * @param postgresqlVersion requested PostgreSQL version
     * @param storage storage configuration
     * @param runtimeSource runtime source
     * @param credentials PostgreSQL credentials
     * @param network localhost network and port selection configuration
     * @param clusterBootstrap primary application database bootstrap configuration
     * @param postgresConfiguration PostgreSQL server configuration settings
     * @param logs PostgreSQL process log handling
     * @param attachPolicy attach policy
     * @param stopPolicy stop policy
     * @param upgradePolicy upgrade policy
     * @param configDriftPolicy config drift policy
     * @param cleanupPolicy cleanup and retention policy
     */
    public ManagedPostgresConfiguration {
        requireNonBlank(name, "name");
        requireNonBlank(postgresqlVersion, "postgresqlVersion");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(runtimeSource, "runtimeSource");
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(clusterBootstrap, "clusterBootstrap");
        Objects.requireNonNull(postgresConfiguration, "postgresConfiguration");
        Objects.requireNonNull(logs, "logs");
        Objects.requireNonNull(attachPolicy, "attachPolicy");
        Objects.requireNonNull(stopPolicy, "stopPolicy");
        Objects.requireNonNull(upgradePolicy, "upgradePolicy");
        Objects.requireNonNull(configDriftPolicy, "configDriftPolicy");
        Objects.requireNonNull(cleanupPolicy, "cleanupPolicy");
    }

    /**
     * Creates immutable managed PostgreSQL configuration without explicit PostgreSQL tuning settings.
     *
     * @param name managed instance name
     * @param postgresqlVersion requested PostgreSQL version
     * @param storage storage configuration
     * @param runtimeSource runtime source
     * @param credentials PostgreSQL credentials
     * @param network localhost network and port selection configuration
     * @param clusterBootstrap primary application database bootstrap configuration
     * @param attachPolicy attach policy
     * @param stopPolicy stop policy
     * @param upgradePolicy upgrade policy
     * @param configDriftPolicy config drift policy
     * @param cleanupPolicy cleanup and retention policy
     */
    @SuppressWarnings({
            // Backward-compatible immutable convenience constructor mirrors the record shape on purpose.
            "PMD.ExcessiveParameterList"
    })
    public ManagedPostgresConfiguration(
            final String name,
            final String postgresqlVersion,
            final Storage storage,
            final RuntimeSource runtimeSource,
            final Credentials credentials,
            final Network network,
            final ClusterBootstrap clusterBootstrap,
            final AttachPolicy attachPolicy,
            final StopPolicy stopPolicy,
            final UpgradePolicy upgradePolicy,
            final ConfigDriftPolicy configDriftPolicy,
            final CleanupPolicy cleanupPolicy) {
        this(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                PostgresConfiguration.defaults(),
                PostgresLogs.defaults(),
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Creates immutable managed PostgreSQL configuration without explicit PostgreSQL log handling.
     *
     * @param name managed instance name
     * @param postgresqlVersion requested PostgreSQL version
     * @param storage storage configuration
     * @param runtimeSource runtime source
     * @param credentials PostgreSQL credentials
     * @param network localhost network and port selection configuration
     * @param clusterBootstrap primary application database bootstrap configuration
     * @param postgresConfiguration PostgreSQL server configuration settings
     * @param attachPolicy attach policy
     * @param stopPolicy stop policy
     * @param upgradePolicy upgrade policy
     * @param configDriftPolicy config drift policy
     * @param cleanupPolicy cleanup and retention policy
     */
    @SuppressWarnings({
            // Backward-compatible immutable convenience constructor mirrors the previous public shape on purpose.
            "PMD.ExcessiveParameterList"
    })
    public ManagedPostgresConfiguration(
            final String name,
            final String postgresqlVersion,
            final Storage storage,
            final RuntimeSource runtimeSource,
            final Credentials credentials,
            final Network network,
            final ClusterBootstrap clusterBootstrap,
            final PostgresConfiguration postgresConfiguration,
            final AttachPolicy attachPolicy,
            final StopPolicy stopPolicy,
            final UpgradePolicy upgradePolicy,
            final ConfigDriftPolicy configDriftPolicy,
            final CleanupPolicy cleanupPolicy) {
        this(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                PostgresLogs.defaults(),
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another name.
     *
     * @param newName managed instance name
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withName(final String newName) {
        return new ManagedPostgresConfiguration(
                newName,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another PostgreSQL version.
     *
     * @param newPostgresqlVersion requested PostgreSQL version
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withPostgresqlVersion(final String newPostgresqlVersion) {
        return new ManagedPostgresConfiguration(
                name,
                newPostgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another storage configuration.
     *
     * @param newStorage storage configuration
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withStorage(final Storage newStorage) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                newStorage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another runtime source.
     *
     * @param newRuntimeSource runtime source
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withRuntimeSource(final RuntimeSource newRuntimeSource) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                newRuntimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another credentials configuration.
     *
     * @param newCredentials PostgreSQL credentials
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withCredentials(final Credentials newCredentials) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                newCredentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another network configuration.
     *
     * @param newNetwork localhost network and port selection configuration
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withNetwork(final Network newNetwork) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                newNetwork,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another cluster bootstrap configuration.
     *
     * @param newClusterBootstrap primary application database bootstrap configuration
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withClusterBootstrap(final ClusterBootstrap newClusterBootstrap) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                newClusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another PostgreSQL server configuration.
     *
     * @param newPostgresConfiguration PostgreSQL server configuration settings
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withPostgresConfiguration(final PostgresConfiguration newPostgresConfiguration) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                newPostgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another PostgreSQL log handling configuration.
     *
     * @param newLogs PostgreSQL process log handling
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withLogs(final PostgresLogs newLogs) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                newLogs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another attach policy.
     *
     * @param newAttachPolicy attach policy
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withAttachPolicy(final AttachPolicy newAttachPolicy) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                newAttachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another stop policy.
     *
     * @param newStopPolicy stop policy
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withStopPolicy(final StopPolicy newStopPolicy) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                newStopPolicy,
                upgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another upgrade policy.
     *
     * @param newUpgradePolicy upgrade policy
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withUpgradePolicy(final UpgradePolicy newUpgradePolicy) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                newUpgradePolicy,
                configDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another config drift policy.
     *
     * @param newConfigDriftPolicy config drift policy
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withConfigDriftPolicy(final ConfigDriftPolicy newConfigDriftPolicy) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                newConfigDriftPolicy,
                cleanupPolicy);
    }

    /**
     * Returns this configuration with another cleanup policy.
     *
     * @param newCleanupPolicy cleanup and retention policy
     * @return updated configuration
     */
    public ManagedPostgresConfiguration withCleanupPolicy(final CleanupPolicy newCleanupPolicy) {
        return new ManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storage,
                runtimeSource,
                credentials,
                network,
                clusterBootstrap,
                postgresConfiguration,
                logs,
                attachPolicy,
                stopPolicy,
                upgradePolicy,
                configDriftPolicy,
                newCleanupPolicy);
    }

    private static void requireNonBlank(final String value, final String name) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
