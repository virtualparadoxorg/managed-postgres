package eu.virtualparadox.managedpostgres.spring.common.config;

import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Immutable Spring Boot property model for managed PostgreSQL integration.
 *
 * @param enabled whether managed PostgreSQL is enabled
 * @param name managed PostgreSQL instance name
 * @param postgresqlVersion requested PostgreSQL version
 * @param storage storage properties
 * @param runtime runtime source properties
 * @param network network properties
 * @param configuration PostgreSQL tuning properties
 * @param datasource datasource publication properties
 * @param cluster cluster bootstrap properties
 * @param lifecycle lifecycle policy properties
 */
public record ManagedPostgresSpringProperties(
        boolean enabled,
        String name,
        String postgresqlVersion,
        StorageProperties storage,
        RuntimeProperties runtime,
        NetworkProperties network,
        ConfigurationProperties configuration,
        DatasourceProperties datasource,
        ClusterProperties cluster,
        LifecycleProperties lifecycle) {

    private static final String PREFIX = "managed-postgres.";
    private static final String ENABLED = PREFIX + "enabled";
    private static final String NAME = PREFIX + "name";
    private static final String VERSION = PREFIX + "version";
    private static final String STORAGE_PATH = PREFIX + "storage.path";
    private static final String RUNTIME_SOURCE = PREFIX + "runtime.source";
    private static final String RUNTIME_PATH = PREFIX + "runtime.path";
    private static final String RUNTIME_RESOURCE = PREFIX + "runtime.resource";
    private static final String RUNTIME_REPOSITORY = PREFIX + "runtime.repository";
    private static final String RUNTIME_CHECKSUM = PREFIX + "runtime.checksum";
    private static final String RUNTIME_SIGNATURE_PUBLIC_KEY = PREFIX + "runtime.signature.public-key";
    private static final String RUNTIME_SIGNATURE_VALUE = PREFIX + "runtime.signature.value";
    private static final String RUNTIME_CACHE = PREFIX + "runtime.cache";
    private static final String NETWORK_HOST = PREFIX + "network.host";
    private static final String NETWORK_PORT_SELECTION = PREFIX + "network.port-selection";
    private static final String NETWORK_PORT = PREFIX + "network.port";
    private static final String NETWORK_FALLBACK_TO_RANDOM = PREFIX + "network.fallback-to-random";
    private static final String CONFIGURATION_PRESET = PREFIX + "configuration.preset";
    private static final String CONFIGURATION_MAX_CONNECTIONS = PREFIX + "configuration.max-connections";
    private static final String CONFIGURATION_SHARED_BUFFERS = PREFIX + "configuration.shared-buffers";
    private static final String CONFIGURATION_TEMP_BUFFERS = PREFIX + "configuration.temp-buffers";
    private static final String CONFIGURATION_STATEMENT_TIMEOUT_SECONDS =
            PREFIX + "configuration.statement-timeout-seconds";
    private static final String DATASOURCE_ENABLED = PREFIX + "datasource.enabled";
    private static final String DATASOURCE_OVERRIDE_EXISTING = PREFIX + "datasource.override-existing";
    private static final String CLUSTER_DATABASE = PREFIX + "cluster.database";
    private static final String CLUSTER_OWNER = PREFIX + "cluster.owner";
    private static final String CLUSTER_PASSWORD = PREFIX + "cluster.password";
    private static final String LIFECYCLE_REUSE_EXISTING = PREFIX + "lifecycle.reuse-existing";
    private static final String LIFECYCLE_KEEP_RUNNING = PREFIX + "lifecycle.keep-running";
    private static final String DEFAULT_NAME = "default";
    private static final String DEFAULT_VERSION = "18.4";
    private static final Path DEFAULT_STORAGE_PATH = Path.of(".local/postgres");
    private static final String DEFAULT_NETWORK_HOST = "127.0.0.1";
    private static final String DEFAULT_NETWORK_PORT_SELECTION = "stable-random";
    private static final String DEFAULT_DATABASE = "postgres";

    /**
     * Creates immutable Spring Boot property values for managed PostgreSQL integration.
     *
     * @param enabled whether managed PostgreSQL is enabled
     * @param name managed PostgreSQL instance name
     * @param postgresqlVersion requested PostgreSQL version
     * @param storage storage properties
     * @param runtime runtime source properties
     * @param network network properties
     * @param configuration PostgreSQL tuning properties
     * @param datasource datasource publication properties
     * @param cluster cluster bootstrap properties
     * @param lifecycle lifecycle policy properties
     */
    public ManagedPostgresSpringProperties {
        name = requireNonBlank(NAME, name);
        postgresqlVersion = requireNonBlank(VERSION, postgresqlVersion);
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(datasource, "datasource");
        Objects.requireNonNull(cluster, "cluster");
        Objects.requireNonNull(lifecycle, "lifecycle");
    }

    /**
     * Reads managed PostgreSQL properties from the Spring environment.
     *
     * @param environment Spring environment
     * @return immutable managed PostgreSQL properties
     */
    public static ManagedPostgresSpringProperties from(final ConfigurableEnvironment environment) {
        final ConfigurableEnvironment checkedEnvironment = Objects.requireNonNull(environment, "environment");
        final RuntimeProperties runtime = runtimeProperties(runtimeSourceProperties(checkedEnvironment));
        final ClusterProperties cluster = clusterProperties(checkedEnvironment);

        return new ManagedPostgresSpringProperties(
                booleanProperty(checkedEnvironment, ENABLED, false),
                stringProperty(checkedEnvironment, NAME, DEFAULT_NAME),
                stringProperty(checkedEnvironment, VERSION, DEFAULT_VERSION),
                new StorageProperties(pathProperty(checkedEnvironment, STORAGE_PATH, DEFAULT_STORAGE_PATH)),
                runtime,
                new NetworkProperties(
                        stringProperty(checkedEnvironment, NETWORK_HOST, DEFAULT_NETWORK_HOST),
                        stringProperty(checkedEnvironment, NETWORK_PORT_SELECTION, DEFAULT_NETWORK_PORT_SELECTION),
                        optionalIntegerProperty(checkedEnvironment, NETWORK_PORT),
                        booleanProperty(checkedEnvironment, NETWORK_FALLBACK_TO_RANDOM, false)),
                configurationProperties(checkedEnvironment),
                new DatasourceProperties(
                        booleanProperty(checkedEnvironment, DATASOURCE_ENABLED, true),
                        booleanProperty(checkedEnvironment, DATASOURCE_OVERRIDE_EXISTING, false)),
                cluster,
                new LifecycleProperties(
                        booleanProperty(checkedEnvironment, LIFECYCLE_REUSE_EXISTING, false),
                        booleanProperty(checkedEnvironment, LIFECYCLE_KEEP_RUNNING, false)));
    }

    private static ConfigurationProperties configurationProperties(final ConfigurableEnvironment environment) {
        return new ConfigurationProperties(
                optionalStringProperty(environment, CONFIGURATION_PRESET)
                        .map(ManagedPostgresSpringProperties::normalizedSource),
                optionalIntegerProperty(environment, CONFIGURATION_MAX_CONNECTIONS),
                optionalStringProperty(environment, CONFIGURATION_SHARED_BUFFERS),
                optionalStringProperty(environment, CONFIGURATION_TEMP_BUFFERS),
                optionalIntegerProperty(environment, CONFIGURATION_STATEMENT_TIMEOUT_SECONDS));
    }

    private static ClusterProperties clusterProperties(final ConfigurableEnvironment environment) {
        final Optional<String> owner = optionalStringProperty(environment, CLUSTER_OWNER);
        final Optional<Secret> password =
                optionalStringProperty(environment, CLUSTER_PASSWORD).map(Secret::of);
        validateCredentialPair(owner, password);

        return new ClusterProperties(stringProperty(environment, CLUSTER_DATABASE, DEFAULT_DATABASE), owner, password);
    }

    private static ManagedPostgresSpringRuntimeSourceProperties runtimeSourceProperties(
            final ConfigurableEnvironment environment) {
        return new ManagedPostgresSpringRuntimeSourceProperties(
                optionalStringProperty(environment, RUNTIME_SOURCE)
                        .map(ManagedPostgresSpringProperties::normalizedSource),
                optionalPathProperty(environment, RUNTIME_PATH),
                optionalStringProperty(environment, RUNTIME_RESOURCE),
                optionalStringProperty(environment, RUNTIME_REPOSITORY),
                optionalStringProperty(environment, RUNTIME_CHECKSUM),
                optionalStringProperty(environment, RUNTIME_SIGNATURE_PUBLIC_KEY),
                optionalStringProperty(environment, RUNTIME_SIGNATURE_VALUE),
                optionalPathProperty(environment, RUNTIME_CACHE));
    }

    private static RuntimeProperties runtimeProperties(final ManagedPostgresSpringRuntimeSourceProperties properties) {
        ManagedPostgresSpringRuntimeSourceValidator.validate(properties);

        return new RuntimeProperties(
                properties.effectiveSource(),
                properties.path(),
                properties.resource(),
                properties.repository(),
                properties.checksum(),
                properties.signaturePublicKey(),
                properties.signatureValue(),
                properties.cache());
    }

    private static void validateCredentialPair(final Optional<String> owner, final Optional<Secret> password) {
        if (owner.isPresent() != password.isPresent()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.cluster.owner and managed-postgres.cluster.password must be configured together");
        }
    }

    private static String normalizedSource(final String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean booleanProperty(
            final ConfigurableEnvironment environment, final String propertyName, final boolean defaultValue) {
        final Boolean value = environment.getProperty(propertyName, Boolean.class);
        final boolean resolvedValue;
        if (value == null) {
            resolvedValue = defaultValue;
        } else {
            resolvedValue = value.booleanValue();
        }

        return resolvedValue;
    }

    private static String stringProperty(
            final ConfigurableEnvironment environment, final String propertyName, final String defaultValue) {
        return optionalStringProperty(environment, propertyName).orElse(defaultValue);
    }

    private static Optional<String> optionalStringProperty(
            final ConfigurableEnvironment environment, final String propertyName) {
        return Optional.ofNullable(environment.getProperty(propertyName))
                .map(value -> requireNonBlank(propertyName, value));
    }

    private static Optional<Integer> optionalIntegerProperty(
            final ConfigurableEnvironment environment, final String propertyName) {
        return Optional.ofNullable(environment.getProperty(propertyName, Integer.class));
    }

    private static Path pathProperty(
            final ConfigurableEnvironment environment, final String propertyName, final Path defaultValue) {
        return optionalPathProperty(environment, propertyName).orElse(defaultValue);
    }

    private static Optional<Path> optionalPathProperty(
            final ConfigurableEnvironment environment, final String propertyName) {
        return optionalStringProperty(environment, propertyName).map(value -> pathValue(propertyName, value));
    }

    private static Path pathValue(final String propertyName, final String value) {
        try {
            return Path.of(value);
        } catch (final InvalidPathException exception) {
            throw new ManagedPostgresSpringException(propertyName + " is not a valid path", exception);
        }
    }

    private static String requireNonBlank(final String propertyName, final String value) {
        if (StringUtils.isBlank(value)) {
            throw new ManagedPostgresSpringException(propertyName + " must not be blank");
        }

        return value;
    }

    /**
     * Immutable storage properties.
     *
     * @param path project-local storage path
     */
    public record StorageProperties(Path path) {

        /**
         * Creates immutable storage properties.
         *
         * @param path project-local storage path
         */
        public StorageProperties {
            Objects.requireNonNull(path, "path");
        }
    }

    /**
     * Immutable runtime source properties.
     *
     * @param source runtime source name
     * @param path existing runtime path, when configured
     * @param resource classpath runtime resource, when configured
     * @param repository downloaded runtime repository, when configured
     * @param checksum classpath or downloaded runtime checksum, when configured
     * @param signaturePublicKey runtime artifact signature public key, when configured
     * @param signatureValue runtime artifact detached signature, when configured
     * @param cache classpath or downloaded runtime cache root, when configured
     */
    public record RuntimeProperties(
            String source,
            Optional<Path> path,
            Optional<String> resource,
            Optional<String> repository,
            Optional<String> checksum,
            Optional<String> signaturePublicKey,
            Optional<String> signatureValue,
            Optional<Path> cache) {

        /**
         * Creates immutable runtime source properties.
         *
         * @param source runtime source name
         * @param path existing runtime path, when configured
         * @param resource classpath runtime resource, when configured
         * @param repository downloaded runtime repository, when configured
         * @param checksum classpath or downloaded runtime checksum, when configured
         * @param signaturePublicKey runtime artifact signature public key, when configured
         * @param signatureValue runtime artifact detached signature, when configured
         * @param cache classpath or downloaded runtime cache root, when configured
         */
        public RuntimeProperties {
            source = requireNonBlank(RUNTIME_SOURCE, source);
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(checksum, "checksum");
            Objects.requireNonNull(signaturePublicKey, "signaturePublicKey");
            Objects.requireNonNull(signatureValue, "signatureValue");
            Objects.requireNonNull(cache, "cache");
        }

        /**
         * Creates immutable runtime source properties.
         *
         * @param source runtime source name
         * @param path existing runtime path, when configured
         * @param resource classpath runtime resource, when configured
         * @param checksum classpath or downloaded runtime checksum, when configured
         * @param cache classpath or downloaded runtime cache root, when configured
         */
        public RuntimeProperties(
                final String source,
                final Optional<Path> path,
                final Optional<String> resource,
                final Optional<String> checksum,
                final Optional<Path> cache) {
            this(source, path, resource, Optional.empty(), checksum, Optional.empty(), Optional.empty(), cache);
        }

        /**
         * Creates immutable runtime source properties.
         *
         * @param source runtime source name
         * @param path existing runtime path, when configured
         */
        public RuntimeProperties(final String source, final Optional<Path> path) {
            this(
                    source,
                    path,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        }
    }

    /**
     * Immutable network properties.
     *
     * @param host PostgreSQL listen host
     * @param portSelection port selection mode
     * @param port configured port, when required by the mode
     * @param fallbackToRandom whether preferred port selection may fall back to random
     */
    public record NetworkProperties(
            String host, String portSelection, Optional<Integer> port, boolean fallbackToRandom) {

        /**
         * Creates immutable network properties.
         *
         * @param host PostgreSQL listen host
         * @param portSelection port selection mode
         * @param port configured port, when required by the mode
         * @param fallbackToRandom whether preferred port selection may fall back to random
         */
        public NetworkProperties {
            host = requireNonBlank(NETWORK_HOST, host);
            portSelection =
                    requireNonBlank(NETWORK_PORT_SELECTION, portSelection).toLowerCase(Locale.ROOT);
            Objects.requireNonNull(port, "port");
        }
    }

    /**
     * Immutable PostgreSQL tuning properties.
     *
     * @param preset optional tuning preset name
     * @param maxConnections optional max connections override
     * @param sharedBuffers optional shared buffers override
     * @param tempBuffers optional temp buffers override
     * @param statementTimeoutSeconds optional statement timeout override in seconds
     */
    public record ConfigurationProperties(
            Optional<String> preset,
            Optional<Integer> maxConnections,
            Optional<String> sharedBuffers,
            Optional<String> tempBuffers,
            Optional<Integer> statementTimeoutSeconds) {

        /**
         * Creates immutable PostgreSQL tuning properties.
         *
         * @param preset optional tuning preset name
         * @param maxConnections optional max connections override
         * @param sharedBuffers optional shared buffers override
         * @param tempBuffers optional temp buffers override
         * @param statementTimeoutSeconds optional statement timeout override in seconds
         */
        public ConfigurationProperties {
            Objects.requireNonNull(preset, "preset");
            Objects.requireNonNull(maxConnections, "maxConnections");
            Objects.requireNonNull(sharedBuffers, "sharedBuffers");
            Objects.requireNonNull(tempBuffers, "tempBuffers");
            Objects.requireNonNull(statementTimeoutSeconds, "statementTimeoutSeconds");
        }

        /**
         * Returns whether no PostgreSQL tuning properties were configured.
         *
         * @return true when no tuning values were configured
         */
        public boolean isEmpty() {
            return preset.isEmpty()
                    && maxConnections.isEmpty()
                    && sharedBuffers.isEmpty()
                    && tempBuffers.isEmpty()
                    && statementTimeoutSeconds.isEmpty();
        }
    }

    /**
     * Immutable datasource publication properties.
     *
     * @param enabled whether datasource properties should be published
     * @param overrideExisting whether existing datasource properties may be replaced
     */
    public record DatasourceProperties(boolean enabled, boolean overrideExisting) {}

    /**
     * Immutable cluster bootstrap properties.
     *
     * @param database primary database name
     * @param owner primary database owner, when configured
     * @param password primary database owner password, when configured
     */
    public record ClusterProperties(String database, Optional<String> owner, Optional<Secret> password) {

        /**
         * Creates immutable cluster bootstrap properties.
         *
         * @param database primary database name
         * @param owner primary database owner, when configured
         * @param password primary database owner password, when configured
         */
        public ClusterProperties {
            database = requireNonBlank(CLUSTER_DATABASE, database);
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(password, "password");
            validateCredentialPair(owner, password);
        }

        /**
         * Returns a redacted cluster property description.
         *
         * @return redacted cluster property description
         */
        @Override
        public String toString() {
            return "ClusterProperties[database=%s, owner=%s, password=%s]"
                    .formatted(database, owner, password.map(ignored -> "REDACTED"));
        }
    }

    /**
     * Immutable lifecycle policy properties.
     *
     * @param reuseExisting whether a compatible existing managed PostgreSQL instance may be reused
     * @param keepRunning whether PostgreSQL should be left running when closed
     */
    public record LifecycleProperties(boolean reuseExisting, boolean keepRunning) {}
}
