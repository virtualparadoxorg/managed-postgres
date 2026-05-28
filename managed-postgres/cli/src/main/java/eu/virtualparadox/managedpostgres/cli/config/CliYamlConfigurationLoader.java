package eu.virtualparadox.managedpostgres.cli.config;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Loads managed-postgres CLI configuration from YAML.
 */
public final class CliYamlConfigurationLoader {

    private static final String ROOT = "managed-postgres";
    private static final String NAME = "name";
    private static final String VERSION = "version";
    private static final String STORAGE = "storage";
    private static final String RUNTIME = "runtime";
    private static final String NETWORK = "network";
    private static final String CONFIGURATION = "configuration";
    private static final String SOURCE = "source";
    private static final String PATH = "path";
    private static final String REPOSITORY = "repository";
    private static final String RESOURCE = "resource";
    private static final String CHECKSUM = "checksum";
    private static final String SIGNATURE = "signature";
    private static final String PUBLIC_KEY = "public-key";
    private static final String VALUE = "value";
    private static final String CACHE = "cache";
    private static final String PRESET = "preset";
    private static final String MAX_CONNECTIONS = "max-connections";
    private static final String SHARED_BUFFERS = "shared-buffers";
    private static final String TEMP_BUFFERS = "temp-buffers";
    private static final String STATEMENT_TIMEOUT_SECONDS = "statement-timeout-seconds";

    /**
     * Creates a YAML configuration loader.
     */
    public CliYamlConfigurationLoader() {
    }

    /**
     * Loads effective CLI configuration from a YAML file.
     *
     * @param path YAML configuration path
     * @return effective CLI configuration
     * @throws IOException when the configuration file cannot be read
     */
    public CliManagedPostgresConfiguration load(final Path path) throws IOException {
        final Path checkedPath = Objects.requireNonNull(path, "path");
        final Object loadedYaml = new Load(LoadSettings.builder().build()).loadFromString(Files.readString(checkedPath));
        final Map<?, ?> root = mapValue(loadedYaml, "configuration").orElse(Map.of());
        final Map<?, ?> configuration = section(root, ROOT).orElse(Map.of());
        final Map<?, ?> storage = section(configuration, STORAGE).orElse(Map.of());
        final Map<?, ?> runtime = section(configuration, RUNTIME).orElse(Map.of());
        final Map<?, ?> signature = section(runtime, SIGNATURE).orElse(Map.of());
        final Map<?, ?> network = section(configuration, NETWORK).orElse(Map.of());
        final Map<?, ?> postgresConfiguration = section(configuration, CONFIGURATION).orElse(Map.of());
        final RuntimeSource runtimeSource = CliYamlRuntimeSourceMapper.fromYaml(new CliYamlRuntimeSourceProperties(
                stringValue(runtime, SOURCE),
                pathValue(runtime, PATH),
                stringValue(runtime, REPOSITORY),
                stringValue(runtime, RESOURCE),
                stringValue(runtime, CHECKSUM),
                stringValue(signature, PUBLIC_KEY),
                stringValue(signature, VALUE),
                pathValue(runtime, CACHE)));

        return applyConfiguration(configuration, storage, network, postgresConfiguration, runtimeSource);
    }

    private static CliManagedPostgresConfiguration applyConfiguration(
            final Map<?, ?> configuration,
            final Map<?, ?> storage,
            final Map<?, ?> network,
            final Map<?, ?> postgresConfiguration,
            final RuntimeSource runtimeSource) {
        CliManagedPostgresConfiguration effectiveConfiguration =
                CliManagedPostgresConfiguration.defaults()
                        .withRuntimeSource(runtimeSource)
                        .withNetwork(CliNetworkConfigurationMapper.fromYaml(network))
                        .withPostgresConfiguration(postgresConfiguration(postgresConfiguration));

        final Optional<String> name = stringValue(configuration, NAME);
        if (name.isPresent()) {
            effectiveConfiguration = effectiveConfiguration.withName(name.get());
        }

        final Optional<String> version = stringValue(configuration, VERSION);
        if (version.isPresent()) {
            effectiveConfiguration = effectiveConfiguration.withPostgresqlVersion(version.get());
        }

        final Optional<Path> storagePath = pathValue(storage, PATH);
        if (storagePath.isPresent()) {
            effectiveConfiguration = effectiveConfiguration.withStoragePath(storagePath.get());
        }

        return effectiveConfiguration;
    }

    private static PostgresConfiguration postgresConfiguration(final Map<?, ?> configuration) {
        PostgresConfiguration postgresConfiguration = stringValue(configuration, PRESET)
                .map(CliYamlConfigurationLoader::preset)
                .orElse(PostgresConfiguration.defaults());

        final Optional<Integer> maxConnections = integerValue(configuration, MAX_CONNECTIONS);
        if (maxConnections.isPresent()) {
            postgresConfiguration = postgresConfiguration.maxConnections(maxConnections.get().intValue());
        }

        final Optional<String> sharedBuffers = stringValue(configuration, SHARED_BUFFERS);
        if (sharedBuffers.isPresent()) {
            postgresConfiguration = postgresConfiguration.sharedBuffers(sharedBuffers.get());
        }

        final Optional<String> tempBuffers = stringValue(configuration, TEMP_BUFFERS);
        if (tempBuffers.isPresent()) {
            postgresConfiguration = postgresConfiguration.tempBuffers(tempBuffers.get());
        }

        final Optional<Integer> statementTimeoutSeconds = integerValue(configuration, STATEMENT_TIMEOUT_SECONDS);
        if (statementTimeoutSeconds.isPresent()) {
            postgresConfiguration =
                    postgresConfiguration.statementTimeoutSeconds(statementTimeoutSeconds.get().intValue());
        }

        return postgresConfiguration;
    }

    private static Optional<Map<?, ?>> section(final Map<?, ?> values, final String key) {
        final Object value = values.get(key);
        final Optional<Map<?, ?>> section;
        if (value == null) {
            section = Optional.empty();
        } else {
            section = Optional.of(mapValue(value, key).orElseThrow());
        }

        return section;
    }

    private static Optional<Map<?, ?>> mapValue(final Object value, final String key) {
        final Optional<Map<?, ?>> map;
        if (value == null) {
            map = Optional.empty();
        } else if (value instanceof Map<?, ?> mappedValue) {
            map = Optional.of(mappedValue);
        } else {
            throw new IllegalArgumentException(key + " must be a YAML object");
        }

        return map;
    }

    private static Optional<String> stringValue(final Map<?, ?> values, final String key) {
        final Object value = values.get(key);
        final Optional<String> text;
        if (value == null) {
            text = Optional.empty();
        } else {
            text = Optional.of(value.toString());
        }

        return text;
    }

    private static Optional<Path> pathValue(final Map<?, ?> values, final String key) {
        final Optional<String> text = stringValue(values, key);
        final Optional<Path> path;
        if (text.isEmpty()) {
            path = Optional.empty();
        } else if (StringUtils.isBlank(text.get())) {
            throw new IllegalArgumentException(key + " must not be blank");
        } else {
            path = Optional.of(Path.of(text.get()));
        }

        return path;
    }

    private static Optional<Integer> integerValue(final Map<?, ?> values, final String key) {
        final Optional<String> text = stringValue(values, key);
        final Optional<Integer> number;
        if (text.isEmpty()) {
            number = Optional.empty();
        } else {
            try {
                number = Optional.of(Integer.valueOf(text.get()));
            } catch (final NumberFormatException exception) {
                throw new IllegalArgumentException(key + " must be an integer", exception);
            }
        }

        return number;
    }

    private static PostgresConfiguration preset(final String value) {
        return switch (value) {
            case "tiny" -> Resources.tiny();
            case "small" -> Resources.small();
            case "ci" -> Resources.ci();
            default -> throw new IllegalArgumentException("preset must be tiny, small, or ci");
        };
    }

}
