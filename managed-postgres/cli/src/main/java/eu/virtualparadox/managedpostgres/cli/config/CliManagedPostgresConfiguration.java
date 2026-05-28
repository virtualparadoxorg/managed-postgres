package eu.virtualparadox.managedpostgres.cli.config;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Effective CLI configuration for a managed PostgreSQL instance.
 *
 * @param name managed PostgreSQL name
 * @param postgresqlVersion requested PostgreSQL version
 * @param storagePath project-local storage path
 * @param runtimeSource PostgreSQL runtime source
 * @param network localhost network and port selection configuration
 * @param attachPolicy start-or-attach policy
 * @param stopPolicy close-time stop policy
 * @param postgresConfiguration PostgreSQL tuning configuration
 */
public record CliManagedPostgresConfiguration(
        String name,
        String postgresqlVersion,
        Path storagePath,
        RuntimeSource runtimeSource,
        Network network,
        AttachPolicy attachPolicy,
        StopPolicy stopPolicy,
        PostgresConfiguration postgresConfiguration) {

    private static final String DEFAULT_NAME = "default";
    private static final String DEFAULT_VERSION = "16.4";
    private static final Path DEFAULT_STORAGE_PATH = Path.of(".local/postgres");

    /**
     * Creates effective CLI configuration.
     *
     * @param name managed PostgreSQL name
     * @param postgresqlVersion requested PostgreSQL version
     * @param storagePath project-local storage path
     * @param runtimeSource PostgreSQL runtime source
     * @param network localhost network and port selection configuration
     * @param attachPolicy start-or-attach policy
     * @param stopPolicy close-time stop policy
     * @param postgresConfiguration PostgreSQL tuning configuration
     */
    public CliManagedPostgresConfiguration {
        requireNonBlank(name, "name");
        requireNonBlank(postgresqlVersion, "version");
        Objects.requireNonNull(storagePath, "storagePath");
        requireNonBlank(storagePath.toString(), "storage");
        Objects.requireNonNull(runtimeSource, "runtimeSource");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(attachPolicy, "attachPolicy");
        Objects.requireNonNull(stopPolicy, "stopPolicy");
        Objects.requireNonNull(postgresConfiguration, "postgresConfiguration");
    }

    /**
     * Creates effective CLI configuration from command-style text values.
     *
     * @param name managed PostgreSQL name
     * @param postgresqlVersion requested PostgreSQL version
     * @param storagePath project-local storage path
     * @param runtimeSource PostgreSQL runtime source
     * @return effective CLI configuration
     */
    public static CliManagedPostgresConfiguration of(
            final String name,
            final String postgresqlVersion,
            final String storagePath,
            final RuntimeSource runtimeSource) {
        requireNonBlank(storagePath, "storage");

        return new CliManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                Path.of(storagePath),
                runtimeSource,
                Network.localhostOnly().stableRandomPort(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                PostgresConfiguration.defaults());
    }

    /**
     * Returns CLI configuration with default local values.
     *
     * @return default local CLI configuration
     */
    public static CliManagedPostgresConfiguration defaults() {
        return new CliManagedPostgresConfiguration(
                DEFAULT_NAME,
                DEFAULT_VERSION,
                DEFAULT_STORAGE_PATH,
                RuntimeSource.system(),
                Network.localhostOnly().stableRandomPort(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                PostgresConfiguration.defaults());
    }

    /**
     * Returns this configuration with another name.
     *
     * @param newName managed PostgreSQL name
     * @return updated CLI configuration
     */
    public CliManagedPostgresConfiguration withName(final String newName) {
        return new CliManagedPostgresConfiguration(
                newName,
                postgresqlVersion,
                storagePath,
                runtimeSource,
                network,
                attachPolicy,
                stopPolicy,
                postgresConfiguration);
    }

    /**
     * Returns this configuration with another PostgreSQL version.
     *
     * @param newPostgresqlVersion requested PostgreSQL version
     * @return updated CLI configuration
     */
    public CliManagedPostgresConfiguration withPostgresqlVersion(final String newPostgresqlVersion) {
        return new CliManagedPostgresConfiguration(
                name,
                newPostgresqlVersion,
                storagePath,
                runtimeSource,
                network,
                attachPolicy,
                stopPolicy,
                postgresConfiguration);
    }

    /**
     * Returns this configuration with another storage path.
     *
     * @param newStoragePath project-local storage path
     * @return updated CLI configuration
     */
    public CliManagedPostgresConfiguration withStoragePath(final Path newStoragePath) {
        return new CliManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                newStoragePath,
                runtimeSource,
                network,
                attachPolicy,
                stopPolicy,
                postgresConfiguration);
    }

    /**
     * Returns this configuration with another runtime source.
     *
     * @param newRuntimeSource PostgreSQL runtime source
     * @return updated CLI configuration
     */
    public CliManagedPostgresConfiguration withRuntimeSource(final RuntimeSource newRuntimeSource) {
        return new CliManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storagePath,
                newRuntimeSource,
                network,
                attachPolicy,
                stopPolicy,
                postgresConfiguration);
    }

    /**
     * Returns this configuration with another network configuration.
     *
     * @param newNetwork localhost network and port selection configuration
     * @return updated CLI configuration
     */
    public CliManagedPostgresConfiguration withNetwork(final Network newNetwork) {
        return new CliManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storagePath,
                runtimeSource,
                newNetwork,
                attachPolicy,
                stopPolicy,
                postgresConfiguration);
    }

    /**
     * Returns this configuration with another attach policy.
     *
     * @param newAttachPolicy start-or-attach policy
     * @return updated CLI configuration
     */
    public CliManagedPostgresConfiguration withAttachPolicy(final AttachPolicy newAttachPolicy) {
        return new CliManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storagePath,
                runtimeSource,
                network,
                newAttachPolicy,
                stopPolicy,
                postgresConfiguration);
    }

    /**
     * Returns this configuration with another stop policy.
     *
     * @param newStopPolicy close-time stop policy
     * @return updated CLI configuration
     */
    public CliManagedPostgresConfiguration withStopPolicy(final StopPolicy newStopPolicy) {
        return new CliManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storagePath,
                runtimeSource,
                network,
                attachPolicy,
                newStopPolicy,
                postgresConfiguration);
    }

    /**
     * Returns this configuration with another PostgreSQL tuning configuration.
     *
     * @param newPostgresConfiguration PostgreSQL tuning configuration
     * @return updated CLI configuration
     */
    public CliManagedPostgresConfiguration withPostgresConfiguration(
            final PostgresConfiguration newPostgresConfiguration) {
        return new CliManagedPostgresConfiguration(
                name,
                postgresqlVersion,
                storagePath,
                runtimeSource,
                network,
                attachPolicy,
                stopPolicy,
                newPostgresConfiguration);
    }

    private static void requireNonBlank(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
