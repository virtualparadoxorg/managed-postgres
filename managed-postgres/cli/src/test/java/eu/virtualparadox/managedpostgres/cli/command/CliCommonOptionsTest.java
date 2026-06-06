package eu.virtualparadox.managedpostgres.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliCommonOptionsTest {

    @TempDir
    private Path temporaryDirectory;

    CliCommonOptionsTest() {}

    @Test
    void emptyOptionsUseLocalDefaults() throws IOException {
        final CliManagedPostgresConfiguration configuration =
                new CliCommonOptions().toConfiguration(new CliYamlConfigurationLoader());

        assertThat(configuration.name()).isEqualTo("default");
        assertThat(configuration.postgresqlVersion()).isEqualTo("18.4");
        assertThat(configuration.storagePath()).isEqualTo(Path.of(".local/postgres"));
        assertThat(configuration.runtimeSource())
                .isEqualTo(RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.official())));
    }

    @Test
    void picocliOptionMethodsOverlayConfigFileValues() throws IOException {
        final Path configPath = temporaryDirectory.resolve("managed-postgres.yml");
        final Path runtimePath = temporaryDirectory.resolve("runtime");
        final CliCommonOptions options = new CliCommonOptions();

        Files.writeString(
                configPath,
                """
                managed-postgres:
                  name: file-db
                  version: "15.7"
                  storage:
                    path: .file/postgres
                """);
        options.useConfig(configPath);
        options.useName("method-db");
        options.useVersion("16.4");
        options.useStorage(".method/postgres");
        options.useExistingRuntime(runtimePath);

        final CliManagedPostgresConfiguration configuration = options.toConfiguration(new CliYamlConfigurationLoader());

        assertThat(configuration.name()).isEqualTo("method-db");
        assertThat(configuration.postgresqlVersion()).isEqualTo("16.4");
        assertThat(configuration.storagePath()).isEqualTo(Path.of(".method/postgres"));
        assertThat(configuration.runtimeSource()).isEqualTo(RuntimeSource.existing(runtimePath));
    }

    @Test
    void directDownloadedRuntimeOptionsOverrideConfigFileValues() throws IOException {
        final Path configPath = temporaryDirectory.resolve("managed-postgres.yml");
        final CliCommonOptions options = new CliCommonOptions();

        Files.writeString(
                configPath,
                """
                managed-postgres:
                  runtime:
                    source: system
                """);
        options.useConfig(configPath);
        options.useRuntimeSource("downloaded");
        options.useRuntimeRepository("file:///opt/postgres/postgres-16.4.zip");
        options.useRuntimeChecksum("sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        options.useRuntimeCache(Path.of(".local/runtime-cache"));

        final CliManagedPostgresConfiguration configuration = options.toConfiguration(new CliYamlConfigurationLoader());

        assertThat(configuration.runtimeSource().kind()).isEqualTo("downloaded");
        assertThat(configuration.runtimeSource().downloadedRuntime())
                .hasValueSatisfying(runtime -> assertThat(runtime.checksum())
                        .contains("sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"));
    }

    @Test
    void directPostgresConfigurationOptionsOverlayDefaults() throws IOException {
        final CliCommonOptions options = new CliCommonOptions();
        options.postgresConfigurationOptions().useResourcePreset("tiny");
        options.postgresConfigurationOptions().useMaxConnections(28);
        options.postgresConfigurationOptions().useSharedBuffers("96MB");
        options.postgresConfigurationOptions().useTempBuffers("10MB");
        options.postgresConfigurationOptions().useStatementTimeoutSeconds(21);

        final CliManagedPostgresConfiguration configuration = options.toConfiguration(new CliYamlConfigurationLoader());

        assertThat(configuration.postgresConfiguration())
                .isEqualTo(Resources.tiny()
                        .maxConnections(28)
                        .sharedBuffers("96MB")
                        .tempBuffers("10MB")
                        .statementTimeoutSeconds(21));
    }

    @Test
    void invalidDirectResourcePresetIsRejected() {
        final CliPostgresConfigurationOptions options = new CliPostgresConfigurationOptions();
        options.useResourcePreset("huge");

        assertThatThrownBy(() -> options.applyTo(PostgresConfiguration.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resource preset");
    }
}
