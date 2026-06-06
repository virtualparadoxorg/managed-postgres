package eu.virtualparadox.managedpostgres.cli.command;

import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliRuntimeSourceFactory;
import eu.virtualparadox.managedpostgres.cli.config.CliRuntimeSourceOptions;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import picocli.CommandLine.Option;

/**
 * Common managed-postgres CLI options shared by lifecycle commands.
 */
public final class CliCommonOptions {

    private Optional<Path> configPath;
    private Optional<String> name;
    private Optional<String> postgresqlVersion;
    private Optional<String> storagePath;
    private CliPostgresConfigurationOptions postgresConfigurationOptions;
    private CliRuntimeSourceOptions runtimeSourceOptions;

    /**
     * Creates empty common CLI options.
     */
    public CliCommonOptions() {
        this(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new CliPostgresConfigurationOptions(),
                CliRuntimeSourceOptions.empty());
    }

    private CliCommonOptions(
            final Optional<Path> configPath,
            final Optional<String> name,
            final Optional<String> postgresqlVersion,
            final Optional<String> storagePath,
            final CliPostgresConfigurationOptions postgresConfigurationOptions,
            final CliRuntimeSourceOptions runtimeSourceOptions) {
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.name = Objects.requireNonNull(name, "name");
        this.postgresqlVersion = Objects.requireNonNull(postgresqlVersion, "postgresqlVersion");
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
        this.postgresConfigurationOptions =
                Objects.requireNonNull(postgresConfigurationOptions, "postgresConfigurationOptions");
        this.runtimeSourceOptions = Objects.requireNonNull(runtimeSourceOptions, "runtimeSourceOptions");
    }

    /**
     * Creates common options for tests and programmatic command assembly.
     *
     * @param configPath optional YAML configuration path
     * @param name optional managed PostgreSQL name
     * @param postgresqlVersion optional PostgreSQL version
     * @param storagePath optional storage path
     * @param runtimeExistingPath optional existing runtime path
     * @return common CLI options
     */
    public static CliCommonOptions of(
            final Optional<Path> configPath,
            final Optional<String> name,
            final Optional<String> postgresqlVersion,
            final Optional<String> storagePath,
            final Optional<Path> runtimeExistingPath) {
        final CliRuntimeSourceOptions runtimeSourceOptions = runtimeExistingPath
                .map(path -> CliRuntimeSourceOptions.empty().withPath(path))
                .orElse(CliRuntimeSourceOptions.empty());

        return new CliCommonOptions(
                configPath,
                name,
                postgresqlVersion,
                storagePath,
                new CliPostgresConfigurationOptions(),
                runtimeSourceOptions);
    }

    @Option(names = "--config", description = "managed-postgres YAML configuration file")
    void useConfig(final Path value) {
        configPath = Optional.of(value);
    }

    @Option(names = "--name", description = "managed PostgreSQL instance name")
    void useName(final String value) {
        name = Optional.of(value);
    }

    @Option(names = "--version", description = "requested PostgreSQL version")
    void useVersion(final String value) {
        postgresqlVersion = Optional.of(value);
    }

    @Option(names = "--storage", description = "project-local storage path")
    void useStorage(final String value) {
        storagePath = Optional.of(value);
    }

    @Option(names = "--runtime-existing", description = "existing PostgreSQL runtime path")
    void useExistingRuntime(final Path value) {
        runtimeSourceOptions = runtimeSourceOptions.withPath(value);
    }

    @Option(names = "--runtime-source", description = "runtime source: system, existing, downloaded, or classpath")
    void useRuntimeSource(final String value) {
        runtimeSourceOptions = runtimeSourceOptions.withSource(value);
    }

    @Option(names = "--runtime-repository", description = "downloaded runtime repository URI")
    void useRuntimeRepository(final String value) {
        runtimeSourceOptions = runtimeSourceOptions.withRepository(value);
    }

    @Option(names = "--runtime-resource", description = "classpath runtime archive resource")
    void useRuntimeResource(final String value) {
        runtimeSourceOptions = runtimeSourceOptions.withResource(value);
    }

    @Option(names = "--runtime-checksum", description = "expected runtime archive checksum")
    void useRuntimeChecksum(final String value) {
        runtimeSourceOptions = runtimeSourceOptions.withChecksum(value);
    }

    @Option(names = "--runtime-signature-public-key", description = "base64 encoded runtime signature public key")
    void useRuntimeSignaturePublicKey(final String value) {
        runtimeSourceOptions = runtimeSourceOptions.withSignaturePublicKey(value);
    }

    @Option(names = "--runtime-signature", description = "base64 encoded detached runtime archive signature")
    void useRuntimeSignature(final String value) {
        runtimeSourceOptions = runtimeSourceOptions.withSignature(value);
    }

    @Option(names = "--runtime-cache", description = "framework-owned runtime cache root")
    void useRuntimeCache(final Path value) {
        runtimeSourceOptions = runtimeSourceOptions.withCache(value);
    }

    /**
     * Resolves config-file values and overlays direct command-line flags.
     *
     * @param loader YAML configuration loader
     * @return effective CLI configuration
     * @throws IOException when the YAML configuration file cannot be read
     */
    public CliManagedPostgresConfiguration toConfiguration(final CliYamlConfigurationLoader loader) throws IOException {
        final CliYamlConfigurationLoader checkedLoader = Objects.requireNonNull(loader, "loader");
        CliManagedPostgresConfiguration configuration = configPath.isPresent()
                ? checkedLoader.load(configPath.get())
                : CliManagedPostgresConfiguration.defaults();

        if (name.isPresent()) {
            configuration = configuration.withName(name.get());
        }
        if (postgresqlVersion.isPresent()) {
            configuration = configuration.withPostgresqlVersion(postgresqlVersion.get());
        }
        if (storagePath.isPresent()) {
            configuration = configuration.withStoragePath(Path.of(storagePath.get()));
        }
        if (runtimeSourceOptions.hasValues()) {
            final RuntimeSource runtimeSource = new CliRuntimeSourceFactory().createDirect(runtimeSourceOptions);
            configuration = configuration.withRuntimeSource(runtimeSource);
        }
        configuration = configuration.withPostgresConfiguration(
                postgresConfigurationOptions.applyTo(configuration.postgresConfiguration()));

        return configuration;
    }

    CliPostgresConfigurationOptions postgresConfigurationOptions() {
        return postgresConfigurationOptions;
    }
}
