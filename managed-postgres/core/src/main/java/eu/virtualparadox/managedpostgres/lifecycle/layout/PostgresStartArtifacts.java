package eu.virtualparadox.managedpostgres.lifecycle.layout;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.config.bootstrap.BootstrapExtension;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import eu.virtualparadox.managedpostgres.lifecycle.port.AllocatedPort;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Builds immutable startup artifacts derived from the selected port and user configuration.
 */
public final class PostgresStartArtifacts {

    private static final String DEFAULT_DATABASE = "postgres";
    private static final String DEFAULT_ATTACHMENT_MODE = "STARTED_BY_THIS_JVM";

    private PostgresStartArtifacts() {
    }

    /**
     * Performs the validate operation.
     *
     * @param configuration configuration value
     */
    public static void validate(final StartPostgresWorkflow.Configuration configuration) {
        majorVersion(configuration.postgresqlVersion());
    }

    /**
     * Returns the settings result.
     *
     * @param allocatedPort allocated port value
     * @return settings result
     */
    public static Map<String, String> settings(final AllocatedPort allocatedPort) {
        return settings(allocatedPort.host(), allocatedPort.port());
    }

    /**
     * Returns the settings result.
     *
     * @param configuration configuration value
     * @param allocatedPort allocated port value
     * @return settings result
     */
    public static Map<String, String> settings(
            final StartPostgresWorkflow.Configuration configuration,
            final AllocatedPort allocatedPort) {
        return settings(configuration, allocatedPort.host(), allocatedPort.port());
    }

    /**
     * Returns the settings result.
     *
     * @param host host value
     * @param port port value
     * @return settings result
     */
    public static Map<String, String> settings(final String host, final int port) {
        return Map.of(
                "listen_addresses", host,
                "password_encryption", "scram-sha-256",
                "port", Integer.toString(port));
    }

    /**
     * Returns the settings result.
     *
     * @param configuration configuration value
     * @param host host value
     * @param port port value
     * @return settings result
     */
    public static Map<String, String> settings(
            final StartPostgresWorkflow.Configuration configuration,
            final String host,
            final int port) {
        final Map<String, String> settings = new LinkedHashMap<>(settings(host, port));
        settings.putAll(postgresConfigurationSettings(configuration.postgresConfiguration()));

        return Map.copyOf(settings);
    }

    /**
     * Returns drift-hash settings that include PostgreSQL config and bootstrap decisions.
     *
     * @param configuration configuration value
     * @param allocatedPort allocated port value
     * @return drift-hash settings
     */
    public static Map<String, String> configHashSettings(
            final StartPostgresWorkflow.Configuration configuration,
            final AllocatedPort allocatedPort) {
        return configHashSettings(configuration, allocatedPort.host(), allocatedPort.port());
    }

    /**
     * Returns drift-hash settings that include PostgreSQL config and bootstrap decisions.
     *
     * @param configuration configuration value
     * @param host selected host value
     * @param port selected port value
     * @return drift-hash settings
     */
    public static Map<String, String> configHashSettings(
            final StartPostgresWorkflow.Configuration configuration,
            final String host,
            final int port) {
        final Map<String, String> hashSettings = new LinkedHashMap<>(
                legacyConfigHashSettings(configuration, host, port));
        hashSettings.put("bootstrap.database", configuration.clusterBootstrap().database());
        hashSettings.put("bootstrap.owner", effectiveOwner(configuration));

        return Map.copyOf(hashSettings);
    }

    /**
     * Returns the previous drift-hash input accepted for metadata written before bootstrap identity
     * was added to the hash.
     *
     * @param configuration configuration value
     * @param host selected host value
     * @param port selected port value
     * @return legacy drift-hash settings
     */
    public static Map<String, String> legacyConfigHashSettings(
            final StartPostgresWorkflow.Configuration configuration,
            final String host,
            final int port) {
        final Map<String, String> hashSettings = new LinkedHashMap<>(settings(configuration, host, port));
        final String extensions = extensionHashInput(configuration);
        if (StringUtils.isNotBlank(extensions)) {
            hashSettings.put("bootstrap.extensions", extensions);
        }

        return Map.copyOf(hashSettings);
    }

    /**
     * Returns the connection info result.
     *
     * @param configuration configuration value
     * @param allocatedPort allocated port value
     * @return connection info result
     */
    public static PostgresConnectionInfo connectionInfo(
            final StartPostgresWorkflow.Configuration configuration,
            final AllocatedPort allocatedPort) {
        return new PostgresConnectionInfo(
                allocatedPort.host(),
                allocatedPort.port(),
                DEFAULT_DATABASE,
                configuration.credentials().username(),
                configuration.credentials().password());
    }

    /**
     * Returns the metadata result.
     *
     * @param configuration configuration value
     * @param layout layout value
     * @param connectionInfo connection info value
     * @param configHash config hash value
     * @param pid pid value
     * @return metadata result
     */
    public static PostgresInstanceMetadata metadata(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final PostgresConnectionInfo connectionInfo,
            final String configHash,
            final long pid) {
        final Instant now = Instant.now();
        final String identifier = UUID.randomUUID().toString();

        return new PostgresInstanceMetadata(
                1,
                identifier,
                identifier,
                configuration.name(),
                layout.dataDirectory(),
                connectionInfo.host(),
                connectionInfo.port(),
                connectionInfo.database(),
                connectionInfo.username(),
                configuration.postgresqlVersion(),
                majorVersion(configuration.postgresqlVersion()),
                DEFAULT_ATTACHMENT_MODE,
                pid,
                configHash,
                now,
                now);
    }

    /**
     * Returns the major version result.
     *
     * @param postgresqlVersion postgresql version value
     * @return major version result
     */
    public static int majorVersion(final String postgresqlVersion) {
        final String firstComponent = StringUtils.substringBefore(postgresqlVersion, ".");
        int major = -1;
        if (StringUtils.isNumeric(firstComponent)) {
            major = Integer.parseInt(firstComponent);
        }
        if (major < 1) {
            throw new IllegalArgumentException("postgresqlVersion must start with a positive major version");
        }

        return major;
    }

    private static String effectiveOwner(final StartPostgresWorkflow.Configuration configuration) {
        return configuration.clusterBootstrap().owner().orElse(configuration.credentials().username());
    }

    private static String extensionHashInput(final StartPostgresWorkflow.Configuration configuration) {
        final StringBuilder builder = new StringBuilder();
        for (final BootstrapExtension extension : configuration.clusterBootstrap().extensions()) {
            builder.append(extension.name())
                    .append(':')
                    .append(extension.policy().name())
                    .append('\n');
        }

        return builder.toString();
    }

    private static Map<String, String> postgresConfigurationSettings(
            final PostgresConfiguration postgresConfiguration) {
        final Map<String, String> settings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : postgresConfiguration.asSettings().entrySet()) {
            settings.put(entry.getKey(), entry.getValue());
        }

        return Map.copyOf(settings);
    }
}
