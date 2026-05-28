package eu.virtualparadox.managedpostgres.cli.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliYamlRuntimeSourceConfigurationLoaderTest {

    private static final String SHA256_CHECKSUM =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String SIGNATURE_PUBLIC_KEY = "public-key";
    private static final String SIGNATURE_VALUE = "signature-value";

    @TempDir
    private Path temporaryDirectory;

    CliYamlRuntimeSourceConfigurationLoaderTest() {
    }

    @Test
    void downloadedRuntimeSourceLoadsRepositoryChecksumAndCacheFromYaml() throws IOException {
        final URI repositoryUri = URI.create("https://runtime.example.test/postgres.zip");
        final Path cacheRoot = Path.of(".local/runtime-cache");
        final RuntimeSource runtimeSource = loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: downloaded
                    repository: "https://runtime.example.test/postgres.zip"
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    signature:
                      public-key: public-key
                      value: signature-value
                    cache: .local/runtime-cache
                """);

        assertThat(runtimeSource).isEqualTo(RuntimeSource.downloaded(runtime -> runtime
                .repository(RuntimeRepository.custom(repositoryUri))
                .checksum(SHA256_CHECKSUM)
                .signature(RuntimeSignature.ed25519(SIGNATURE_PUBLIC_KEY, SIGNATURE_VALUE))
                .cache(RuntimeCache.projectLocal(cacheRoot))));
    }

    @Test
    void classpathRuntimeSourceLoadsResourceChecksumAndCacheFromYaml() throws IOException {
        final Path cacheRoot = Path.of(".local/classpath-runtime-cache");
        final RuntimeSource runtimeSource = loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: classpath
                    resource: /postgres-runtime.zip
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    signature:
                      public-key: public-key
                      value: signature-value
                    cache: .local/classpath-runtime-cache
                """);

        assertThat(runtimeSource).isEqualTo(RuntimeSource.classpath("/postgres-runtime.zip", runtime -> runtime
                .checksum(SHA256_CHECKSUM)
                .signature(RuntimeSignature.ed25519(SIGNATURE_PUBLIC_KEY, SIGNATURE_VALUE))
                .cache(RuntimeCache.projectLocal(cacheRoot))));
    }

    @Test
    void downloadedRuntimeSourceRejectsIncompatibleYamlFields() throws IOException {
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: downloaded
                    path: /usr/local/pgsql
                    repository: "https://runtime.example.test/postgres.zip"
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.path is only valid for existing runtime source");
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: downloaded
                    repository: "https://runtime.example.test/postgres.zip"
                    resource: /postgres-runtime.zip
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.resource is only valid for classpath runtime source");
    }

    @Test
    void classpathRuntimeSourceRejectsIncompatibleYamlFields() throws IOException {
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: classpath
                    repository: "https://runtime.example.test/postgres.zip"
                    resource: /postgres-runtime.zip
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.repository is only valid for downloaded runtime source");
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: classpath
                    path: /usr/local/pgsql
                    resource: /postgres-runtime.zip
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.path is only valid for existing runtime source");
    }

    @Test
    void runtimeSupplyChainFieldsRequireDownloadedOrClasspathSource() throws IOException {
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: system
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.checksum is only valid for downloaded or classpath runtime source");
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: existing
                    path: /usr/local/pgsql
                    cache: .local/runtime-cache
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.cache is only valid for downloaded or classpath runtime source");
    }

    @Test
    void downloadedAndClasspathRuntimeSourcesRequireTheirYamlFields() throws IOException {
        final RuntimeSource cacheOnlyDownloadedRuntime = loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: downloaded
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    cache: .local/runtime-cache
                """);

        assertThat(cacheOnlyDownloadedRuntime.kind()).isEqualTo("downloaded");
        assertThat(cacheOnlyDownloadedRuntime.downloadedRuntime()).hasValueSatisfying(downloadedRuntime -> {
            assertThat(downloadedRuntime.repository()).isEmpty();
            assertThat(downloadedRuntime.checksum()).contains(
                    "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        });

        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: downloaded
                    repository: "https://runtime.example.test/postgres.zip"
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.source=downloaded requires runtime.checksum");
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: classpath
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.source=classpath requires runtime.resource");
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: classpath
                    resource: /postgres-runtime.zip
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.source=classpath requires runtime.checksum");
    }

    @Test
    void signatureYamlFieldsMustBeConfiguredTogether() throws IOException {
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: downloaded
                    repository: "https://runtime.example.test/postgres.zip"
                    checksum: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    signature:
                      value: signature-value
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime signature public key and value must be configured together");
    }

    @Test
    void signatureYamlFieldsRequireDownloadedOrClasspathSource() throws IOException {
        assertThatThrownBy(() -> loadRuntimeSource("""
                managed-postgres:
                  runtime:
                    source: system
                    signature:
                      public-key: public-key
                      value: signature-value
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.signature is only valid for downloaded or classpath runtime source");
    }

    private RuntimeSource loadRuntimeSource(final String content) throws IOException {
        final Path configPath = writeConfiguration(content);
        final CliManagedPostgresConfiguration configuration = new CliYamlConfigurationLoader().load(configPath);

        return configuration.runtimeSource();
    }

    private Path writeConfiguration(final String content) throws IOException {
        final Path configPath = temporaryDirectory.resolve("managed-postgres.yml");
        Files.writeString(configPath, content);

        return configPath;
    }
}
