package eu.virtualparadox.managedpostgres.cli.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.cli.config.CliRuntimeSourceFactory;
import eu.virtualparadox.managedpostgres.cli.config.CliRuntimeSourceOptions;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CliRuntimeSourceFactoryDirectTest {

    private static final String DOWNLOADED_CHECKSUM =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CLASSPATH_CHECKSUM =
            "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SIGNATURE_PUBLIC_KEY = "public-key";
    private static final String SIGNATURE_VALUE = "signature-value";

    CliRuntimeSourceFactoryDirectTest() {
    }

    @Test
    void directDownloadedRuntimeMapsSupplyChainFields() {
        final RuntimeSource runtimeSource = new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withSource("downloaded")
                        .withRepository("file:///opt/postgres/postgres-16.4.zip")
                        .withChecksum(DOWNLOADED_CHECKSUM)
                        .withSignaturePublicKey(SIGNATURE_PUBLIC_KEY)
                        .withSignature(SIGNATURE_VALUE)
                        .withCache(Path.of(".local/runtime-cache")));

        assertThat(runtimeSource.kind()).isEqualTo("downloaded");
        assertThat(runtimeSource.downloadedRuntime()).hasValueSatisfying(runtime -> {
            assertThat(runtime.repository()).hasValueSatisfying(repository ->
                    assertThat(repository.uri()).isEqualTo(URI.create("file:///opt/postgres/postgres-16.4.zip")));
            assertThat(runtime.checksum()).contains(DOWNLOADED_CHECKSUM);
            assertThat(runtime.signature()).contains(RuntimeSignature.ed25519(SIGNATURE_PUBLIC_KEY, SIGNATURE_VALUE));
            assertThat(runtime.cache()).hasValueSatisfying(cache ->
                    assertThat(cache.root()).isEqualTo(Path.of(".local/runtime-cache")));
        });
    }

    @Test
    void directClasspathRuntimeMapsSupplyChainFields() {
        final RuntimeSource runtimeSource = new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withSource("classpath")
                        .withResource("/runtimes/postgres-16.4.zip")
                        .withChecksum(CLASSPATH_CHECKSUM)
                        .withSignaturePublicKey(SIGNATURE_PUBLIC_KEY)
                        .withSignature(SIGNATURE_VALUE)
                        .withCache(Path.of(".local/runtime-cache")));

        assertThat(runtimeSource.kind()).isEqualTo("classpath");
        assertThat(runtimeSource.classpathRuntime()).hasValueSatisfying(runtime -> {
            assertThat(runtime.resource()).isEqualTo("/runtimes/postgres-16.4.zip");
            assertThat(runtime.checksum()).contains(CLASSPATH_CHECKSUM);
            assertThat(runtime.signature()).contains(RuntimeSignature.ed25519(SIGNATURE_PUBLIC_KEY, SIGNATURE_VALUE));
            assertThat(runtime.cache()).hasValueSatisfying(cache ->
                    assertThat(cache.root()).isEqualTo(Path.of(".local/runtime-cache")));
        });
    }

    @Test
    void repositoryFieldInfersDownloadedRuntimeSource() {
        final RuntimeSource runtimeSource = new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withRepository("file:///opt/postgres/postgres-16.4.zip")
                        .withChecksum(DOWNLOADED_CHECKSUM));

        assertThat(runtimeSource.kind()).isEqualTo("downloaded");
    }

    @Test
    void downloadedRuntimeWithoutRepositoryMapsCacheOnlySource() {
        final RuntimeSource runtimeSource = new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withSource("downloaded")
                        .withChecksum(DOWNLOADED_CHECKSUM)
                        .withCache(Path.of(".local/runtime-cache")));

        assertThat(runtimeSource.kind()).isEqualTo("downloaded");
        assertThat(runtimeSource.downloadedRuntime()).hasValueSatisfying(runtime -> {
            assertThat(runtime.repository()).isEmpty();
            assertThat(runtime.checksum()).contains(DOWNLOADED_CHECKSUM);
            assertThat(runtime.cache()).hasValueSatisfying(cache ->
                    assertThat(cache.root()).isEqualTo(Path.of(".local/runtime-cache")));
        });
    }

    @Test
    void resourceFieldInfersClasspathRuntimeSource() {
        final RuntimeSource runtimeSource = new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withResource("/runtimes/postgres-16.4.zip")
                        .withChecksum(CLASSPATH_CHECKSUM));

        assertThat(runtimeSource.kind()).isEqualTo("classpath");
    }

    @Test
    void downloadedRuntimeRequiresChecksum() {
        assertThatThrownBy(() -> new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withSource("downloaded")
                        .withRepository("file:///opt/postgres/postgres-16.4.zip")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.source=downloaded requires runtime.checksum");
    }

    @Test
    void classpathRuntimeRequiresChecksum() {
        assertThatThrownBy(() -> new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withSource("classpath")
                        .withResource("/runtimes/postgres-16.4.zip")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.source=classpath requires runtime.checksum");
    }

    @Test
    void downloadedRuntimeRejectsClasspathResource() {
        assertThatThrownBy(() -> new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withSource("downloaded")
                        .withRepository("file:///opt/postgres/postgres-16.4.zip")
                        .withResource("/runtimes/postgres-16.4.zip")
                        .withChecksum(DOWNLOADED_CHECKSUM)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.resource is only valid for classpath runtime source");
    }

    @Test
    void classpathRuntimeRejectsRepository() {
        assertThatThrownBy(() -> new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withSource("classpath")
                        .withRepository("file:///opt/postgres/postgres-16.4.zip")
                        .withResource("/runtimes/postgres-16.4.zip")
                        .withChecksum(CLASSPATH_CHECKSUM)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.repository is only valid for downloaded runtime source");
    }

    @Test
    void signatureFieldsMustBeConfiguredTogether() {
        assertThatThrownBy(() -> new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withSource("downloaded")
                        .withRepository("file:///opt/postgres/postgres-16.4.zip")
                        .withChecksum(DOWNLOADED_CHECKSUM)
                        .withSignature(SIGNATURE_VALUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime signature public key and value must be configured together");
    }

    @Test
    void signatureFieldsRequireDownloadedOrClasspathRuntimeSource() {
        assertThatThrownBy(() -> new CliRuntimeSourceFactory()
                .createDirect(CliRuntimeSourceOptions.empty()
                        .withSource("existing")
                        .withPath(Path.of("runtime"))
                        .withSignaturePublicKey(SIGNATURE_PUBLIC_KEY)
                        .withSignature(SIGNATURE_VALUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime.signature is only valid for downloaded or classpath runtime source");
    }

    @Test
    void directOptionsReportsSignatureValuesAsPresent() {
        assertThat(CliRuntimeSourceOptions.empty()
                .withSignaturePublicKey(SIGNATURE_PUBLIC_KEY)
                .hasValues())
                .isTrue();
        assertThat(CliRuntimeSourceOptions.empty()
                .withSignature(SIGNATURE_VALUE)
                .hasValues())
                .isTrue();
        assertThat(CliRuntimeSourceOptions.empty()
                .withCache(Path.of("runtime-cache"))
                .hasValues())
                .isTrue();
    }
}
