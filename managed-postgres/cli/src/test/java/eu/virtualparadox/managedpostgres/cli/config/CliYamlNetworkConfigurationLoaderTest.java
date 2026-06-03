package eu.virtualparadox.managedpostgres.cli.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliYamlNetworkConfigurationLoaderTest {

    @TempDir
    private Path temporaryDirectory;

    CliYamlNetworkConfigurationLoaderTest() {}

    @Test
    void configFileValuesAreLoadedFromManagedPostgresYaml() throws IOException {
        final Path runtimePath = temporaryDirectory.resolve("runtime");
        final Path configPath = writeConfiguration(String.join(
                System.lineSeparator(),
                "managed-postgres:",
                "  name: app-db",
                "  version: \"16.4\"",
                "  storage:",
                "    path: .local/postgres",
                "  runtime:",
                "    source: existing",
                "    path: " + runtimePath,
                "  network:",
                "    port-selection: preferred",
                "    port: 15432",
                "    fallback-to-random: true",
                ""));

        final CliManagedPostgresConfiguration configuration = new CliYamlConfigurationLoader().load(configPath);

        assertThat(configuration.name()).isEqualTo("app-db");
        assertThat(configuration.postgresqlVersion()).isEqualTo("16.4");
        assertThat(configuration.storagePath()).isEqualTo(Path.of(".local/postgres"));
        assertThat(configuration.runtimeSource()).isEqualTo(RuntimeSource.existing(runtimePath));
        assertThat(configuration.network()).isEqualTo(preferredWithFallback(15432));
    }

    @Test
    void emptyYamlUsesSafeDefaults() throws IOException {
        final Path configPath = writeConfiguration("");

        final CliManagedPostgresConfiguration configuration = new CliYamlConfigurationLoader().load(configPath);

        assertThat(configuration.name()).isEqualTo("default");
        assertThat(configuration.postgresqlVersion()).isEqualTo("16.4");
        assertThat(configuration.storagePath()).isEqualTo(Path.of(".local/postgres"));
        assertThat(configuration.runtimeSource()).isEqualTo(RuntimeSource.system());
        assertThat(configuration.network()).isEqualTo(stableRandomNetwork());
    }

    @Test
    void postgresConfigurationBlockMapsPresetAndOverrides() throws IOException {
        final Path configPath = writeConfiguration(
                """
                managed-postgres:
                  configuration:
                    preset: ci
                    max-connections: 40
                    shared-buffers: 160MB
                    temp-buffers: 12MB
                    statement-timeout-seconds: 45
                """);

        final CliManagedPostgresConfiguration configuration = new CliYamlConfigurationLoader().load(configPath);

        assertThat(configuration.postgresConfiguration())
                .isEqualTo(Resources.ci()
                        .maxConnections(40)
                        .sharedBuffers("160MB")
                        .tempBuffers("12MB")
                        .statementTimeoutSeconds(45));
    }

    @Test
    void postgresConfigurationBlockRejectsUnknownPreset() throws IOException {
        final Path configPath = writeConfiguration(
                """
                managed-postgres:
                  configuration:
                    preset: huge
                """);

        assertThatThrownBy(() -> new CliYamlConfigurationLoader().load(configPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preset");
    }

    @Test
    void randomNetworkSelectionRejectsPortAndFallback() throws IOException {
        assertThatThrownBy(
                        () -> loadNetwork(
                                """
                managed-postgres:
                  network:
                    port-selection: random
                    port: 15432
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network port is only valid");
        assertThatThrownBy(
                        () -> loadNetwork(
                                """
                managed-postgres:
                  network:
                    port-selection: random
                    fallback-to-random: true
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fallback-to-random requires preferred");
    }

    @Test
    void stableRandomNetworkSelectionRejectsPortAndFallback() throws IOException {
        assertThatThrownBy(
                        () -> loadNetwork(
                                """
                managed-postgres:
                  network:
                    port-selection: stable-random
                    port: 15432
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network port is only valid");
        assertThatThrownBy(
                        () -> loadNetwork(
                                """
                managed-postgres:
                  network:
                    port-selection: stable-random
                    fallback-to-random: true
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fallback-to-random requires preferred");
    }

    @Test
    void fixedNetworkSelectionRequiresPort() throws IOException {
        assertThatThrownBy(
                        () -> loadNetwork(
                                """
                managed-postgres:
                  network:
                    port-selection: fixed
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network port is required");
    }

    @Test
    void fixedNetworkSelectionMapsPort() throws IOException {
        final Network network = loadNetwork(
                """
                managed-postgres:
                  network:
                    port-selection: fixed
                    port: 15432
                """);

        assertThat(network).isEqualTo(fixedNetwork(15432));
    }

    @Test
    void preferredNetworkSelectionMapsOptionalFallback() throws IOException {
        final Network withoutFallback = loadNetwork(
                """
                managed-postgres:
                  network:
                    port-selection: preferred
                    port: 15432
                """);
        final Network withFallback = loadNetwork(
                """
                managed-postgres:
                  network:
                    port-selection: PREFERRED
                    port: 15433
                    fallback-to-random: TRUE
                """);

        assertThat(withoutFallback).isEqualTo(preferredNetwork(15432));
        assertThat(withFallback).isEqualTo(preferredWithFallback(15433));
    }

    @Test
    void yamlRejectsNonObjectNetworkSection() throws IOException {
        final Path configPath = writeConfiguration(
                """
                managed-postgres:
                  network: invalid
                """);

        assertThatThrownBy(() -> new CliYamlConfigurationLoader().load(configPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network must be a YAML object");
    }

    @Test
    void yamlRejectsInvalidNetworkValues() throws IOException {
        assertThatThrownBy(
                        () -> loadNetwork(
                                """
                managed-postgres:
                  network:
                    port-selection: invalid
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port-selection");
        assertThatThrownBy(
                        () -> loadNetwork(
                                """
                managed-postgres:
                  network:
                    port-selection: fixed
                    port: invalid
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port must be an integer");
        assertThatThrownBy(
                        () -> loadNetwork(
                                """
                managed-postgres:
                  network:
                    port-selection: preferred
                    port: 15432
                    fallback-to-random: invalid
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fallback-to-random must be true or false");
    }

    private Network loadNetwork(final String content) throws IOException {
        final Path configPath = writeConfiguration(content);
        final CliManagedPostgresConfiguration configuration = new CliYamlConfigurationLoader().load(configPath);

        return configuration.network();
    }

    private Path writeConfiguration(final String content) throws IOException {
        final Path configPath = temporaryDirectory.resolve("managed-postgres.yml");
        Files.writeString(configPath, content);

        return configPath;
    }

    private static Network stableRandomNetwork() {
        final Network localhost = Network.localhostOnly();

        return localhost.stableRandomPort();
    }

    private static Network fixedNetwork(final int port) {
        final Network localhost = Network.localhostOnly();

        return localhost.port(port);
    }

    private static Network preferredNetwork(final int port) {
        final Network localhost = Network.localhostOnly();

        return localhost.preferredPort(port);
    }

    private static Network preferredWithFallback(final int port) {
        final Network preferred = preferredNetwork(port);

        return preferred.fallbackToRandom();
    }
}
