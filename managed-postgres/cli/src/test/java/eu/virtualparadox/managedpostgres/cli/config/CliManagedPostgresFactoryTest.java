package eu.virtualparadox.managedpostgres.cli.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.CliCommonOptions;
import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliManagedPostgresFactoryTest {

    @TempDir
    private Path temporaryDirectory;

    CliManagedPostgresFactoryTest() {}

    @Test
    void blankNameIsRejectedBeforeLifecycleStarts() {
        assertThatThrownBy(() ->
                        CliManagedPostgresConfiguration.of(" ", "16.4", ".local/postgres", RuntimeSource.system()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void blankPostgresqlVersionIsRejectedBeforeLifecycleStarts() {
        assertThatThrownBy(() ->
                        CliManagedPostgresConfiguration.of("app-db", "", ".local/postgres", RuntimeSource.system()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version must not be blank");
    }

    @Test
    void blankStorageIsRejectedBeforeLifecycleStarts() {
        assertThatThrownBy(() -> CliManagedPostgresConfiguration.of("app-db", "16.4", " ", RuntimeSource.system()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage must not be blank");
    }

    @Test
    void omittedRuntimeMapsToSystemRuntimeSource() {
        final RuntimeSource runtimeSource = new CliRuntimeSourceFactory().create(Optional.empty(), Optional.empty());

        assertThat(runtimeSource).isEqualTo(RuntimeSource.system());
    }

    @Test
    void runtimeExistingMapsToExistingRuntimeSource() {
        final Path runtimePath = temporaryDirectory.resolve("postgres-runtime");
        final RuntimeSource runtimeSource =
                new CliRuntimeSourceFactory().create(Optional.of("existing"), Optional.of(runtimePath));

        assertThat(runtimeSource).isEqualTo(RuntimeSource.existing(runtimePath));
    }

    @Test
    void omittedRuntimeSourceWithPathMapsToExistingRuntimeSource() {
        final Path runtimePath = temporaryDirectory.resolve("postgres-runtime");
        final RuntimeSource runtimeSource =
                new CliRuntimeSourceFactory().create(Optional.empty(), Optional.of(runtimePath));

        assertThat(runtimeSource).isEqualTo(RuntimeSource.existing(runtimePath));
    }

    @Test
    void systemRuntimeRejectsPath() {
        assertThatThrownBy(() ->
                        new CliRuntimeSourceFactory().create(Optional.of("system"), Optional.of(temporaryDirectory)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.path is only valid");
    }

    @Test
    void existingRuntimeRequiresPath() {
        assertThatThrownBy(() -> new CliRuntimeSourceFactory().create(Optional.of("existing"), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existing runtime source requires a path");
    }

    @Test
    void downloadedRuntimeRequiresChecksumForDirectCliFlags() {
        assertThatThrownBy(() -> new CliRuntimeSourceFactory().create(Optional.of("downloaded"), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.source=downloaded requires runtime.checksum");
    }

    @Test
    void blankRuntimeSourceIsRejected() {
        assertThatThrownBy(() -> new CliRuntimeSourceFactory().create(Optional.of(" "), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime source must not be blank");
    }

    @Test
    void yamlRejectsNonObjectRoot() throws IOException {
        final Path configPath = writeConfiguration("- invalid");

        assertThatThrownBy(() -> new CliYamlConfigurationLoader().load(configPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configuration must be a YAML object");
    }

    @Test
    void yamlRejectsNonObjectStorageSection() throws IOException {
        final Path configPath = writeConfiguration(
                """
                managed-postgres:
                  storage: invalid
                """);

        assertThatThrownBy(() -> new CliYamlConfigurationLoader().load(configPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage must be a YAML object");
    }

    @Test
    void directCommandLineFlagsOverrideConfigFileValues() throws IOException {
        final Path configPath = writeConfiguration(
                """
                managed-postgres:
                  name: config-db
                  version: "15.7"
                  storage:
                    path: .config/postgres
                """);
        final Path runtimePath = temporaryDirectory.resolve("runtime");
        final CliCommonOptions options = CliCommonOptions.of(
                Optional.of(configPath),
                Optional.of("flag-db"),
                Optional.of("16.4"),
                Optional.of(".flag/postgres"),
                Optional.of(runtimePath));

        final CliManagedPostgresConfiguration configuration = options.toConfiguration(new CliYamlConfigurationLoader());

        assertThat(configuration.name()).isEqualTo("flag-db");
        assertThat(configuration.postgresqlVersion()).isEqualTo("16.4");
        assertThat(configuration.storagePath()).isEqualTo(Path.of(".flag/postgres"));
        assertThat(configuration.runtimeSource()).isEqualTo(RuntimeSource.existing(runtimePath));
    }

    @Test
    void factoryCreatesManagedPostgresThroughPublicApiOnly() throws NoSuchMethodException {
        final Method method =
                CliManagedPostgresFactory.class.getMethod("create", CliManagedPostgresConfiguration.class);
        final String methodSignature = method.toGenericString();
        final CliManagedPostgresConfiguration configuration =
                CliManagedPostgresConfiguration.of("app-db", "16.4", ".local/postgres", RuntimeSource.system());

        try (ManagedPostgres managedPostgres = new CliManagedPostgresFactory().create(configuration)) {
            assertThat(managedPostgres).isNotNull();
            assertThat(managedPostgres.toString()).contains("network=Network");
            assertThat(method.getReturnType()).isEqualTo(ManagedPostgres.class);
            assertThat(methodSignature)
                    .doesNotContain("Process")
                    .doesNotContain("Platform")
                    .doesNotContain("OperatingSystem")
                    .doesNotContain("CpuArchitecture")
                    .doesNotContain("LibcVariant");
        }
    }

    @Test
    void factoryMapsPostgresConfigurationThroughPublicBuilder() {
        final CliManagedPostgresConfiguration configuration = new CliManagedPostgresConfiguration(
                "app-db",
                "16.4",
                Path.of(".local/postgres"),
                RuntimeSource.system(),
                Network.localhostOnly().stableRandomPort(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                Resources.ci().maxConnections(24).sharedBuffers("96MB"));

        try (ManagedPostgres managedPostgres = new CliManagedPostgresFactory().create(configuration)) {
            assertThat(managedPostgres.toString())
                    .contains("maxConnections=OptionalInt[24]")
                    .contains("sharedBuffers=Optional[96MB]");
        }
    }

    private Path writeConfiguration(final String content) throws IOException {
        final Path configPath = temporaryDirectory.resolve("managed-postgres.yml");
        Files.writeString(configPath, content);

        return configPath;
    }
}
