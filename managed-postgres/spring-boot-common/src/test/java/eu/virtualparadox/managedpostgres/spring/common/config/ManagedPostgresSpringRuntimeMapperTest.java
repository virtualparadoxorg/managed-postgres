package eu.virtualparadox.managedpostgres.spring.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresSpringRuntimeMapperTest {

    ManagedPostgresSpringRuntimeMapperTest() {}

    @Test
    void invalidRuntimeModelIsRejectedByFactory() {
        final ManagedPostgresSpringProperties.RuntimeProperties runtime =
                new ManagedPostgresSpringProperties.RuntimeProperties("container", Optional.empty());

        assertThatThrownBy(() -> ManagedPostgresSpringRuntimeMapper.runtimeSource(runtime))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.source");
    }

    @Test
    void existingRuntimeModelWithoutPathIsRejectedByFactory() {
        final ManagedPostgresSpringProperties.RuntimeProperties runtime =
                new ManagedPostgresSpringProperties.RuntimeProperties("existing", Optional.empty());

        assertThatThrownBy(() -> ManagedPostgresSpringRuntimeMapper.runtimeSource(runtime))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.path");
    }

    @Test
    void existingRuntimeModelWithPathIsAcceptedByFactory() {
        final ManagedPostgresSpringProperties.RuntimeProperties runtime =
                new ManagedPostgresSpringProperties.RuntimeProperties("existing", Optional.of(Path.of("runtime")));

        assertThat(ManagedPostgresSpringRuntimeMapper.runtimeSource(runtime).existingPath())
                .contains(Path.of("runtime"));
    }

    @Test
    void classpathRuntimeModelWithResourceChecksumAndCacheIsAcceptedByFactory() {
        final RuntimeSignature signature = RuntimeSignature.ed25519("public-key", "signature-value");
        final ManagedPostgresSpringProperties.RuntimeProperties runtime =
                new ManagedPostgresSpringProperties.RuntimeProperties(
                        "classpath",
                        Optional.empty(),
                        Optional.of("/postgres-runtime.zip"),
                        Optional.empty(),
                        Optional.of("sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                        Optional.of("public-key"),
                        Optional.of("signature-value"),
                        Optional.of(Path.of(".local/runtime-cache")));

        assertThat(ManagedPostgresSpringRuntimeMapper.runtimeSource(runtime).classpathRuntime())
                .get()
                .satisfies(classpathRuntime -> {
                    assertThat(classpathRuntime.resource()).isEqualTo("/postgres-runtime.zip");
                    assertThat(classpathRuntime.cache())
                            .contains(RuntimeCache.projectLocal(Path.of(".local/runtime-cache")));
                    assertThat(classpathRuntime.checksum())
                            .contains("sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
                    assertThat(classpathRuntime.signature()).contains(signature);
                });
    }

    @Test
    void classpathRuntimeModelWithSignatureAndNoCacheIsAcceptedByFactory() {
        final RuntimeSignature signature = RuntimeSignature.ed25519("public-key", "signature-value");
        final ManagedPostgresSpringProperties.RuntimeProperties runtime =
                new ManagedPostgresSpringProperties.RuntimeProperties(
                        "classpath",
                        Optional.empty(),
                        Optional.of("/postgres-runtime.zip"),
                        Optional.empty(),
                        Optional.of("sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                        Optional.of("public-key"),
                        Optional.of("signature-value"),
                        Optional.empty());

        assertThat(ManagedPostgresSpringRuntimeMapper.runtimeSource(runtime).classpathRuntime())
                .get()
                .satisfies(classpathRuntime -> {
                    assertThat(classpathRuntime.signature()).contains(signature);
                    assertThat(classpathRuntime.cache()).isEmpty();
                });
    }

    @Test
    void downloadedRuntimeModelWithRepositoryChecksumAndCacheIsAcceptedByFactory() {
        final URI repositoryUri = URI.create("file:///tmp/postgres.zip");
        final String checksum = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
        final Path cacheRoot = Path.of(".local/runtime-cache");
        final RuntimeSignature signature = RuntimeSignature.ed25519("public-key", "signature-value");
        final ManagedPostgresSpringProperties.RuntimeProperties runtime =
                new ManagedPostgresSpringProperties.RuntimeProperties(
                        "downloaded",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(repositoryUri.toString()),
                        Optional.of(checksum),
                        Optional.of("public-key"),
                        Optional.of("signature-value"),
                        Optional.of(cacheRoot));

        assertThat(ManagedPostgresSpringRuntimeMapper.runtimeSource(runtime).downloadedRuntime())
                .get()
                .satisfies(downloadedRuntime -> {
                    assertThat(downloadedRuntime.repository()).contains(RuntimeRepository.custom(repositoryUri));
                    assertThat(downloadedRuntime.checksum()).contains(checksum);
                    assertThat(downloadedRuntime.signature()).contains(signature);
                    assertThat(downloadedRuntime.cache()).contains(RuntimeCache.projectLocal(cacheRoot));
                });
    }

    @Test
    void downloadedRuntimeModelWithSignatureAndNoCacheIsAcceptedByFactory() {
        final URI repositoryUri = URI.create("file:///tmp/postgres.zip");
        final String checksum = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
        final RuntimeSignature signature = RuntimeSignature.ed25519("public-key", "signature-value");
        final ManagedPostgresSpringProperties.RuntimeProperties runtime =
                new ManagedPostgresSpringProperties.RuntimeProperties(
                        "downloaded",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(repositoryUri.toString()),
                        Optional.of(checksum),
                        Optional.of("public-key"),
                        Optional.of("signature-value"),
                        Optional.empty());

        assertThat(ManagedPostgresSpringRuntimeMapper.runtimeSource(runtime).downloadedRuntime())
                .get()
                .satisfies(downloadedRuntime -> {
                    assertThat(downloadedRuntime.signature()).contains(signature);
                    assertThat(downloadedRuntime.cache()).isEmpty();
                });
    }

    @Test
    void downloadedRuntimeModelWithoutRepositoryUsesCacheOnlyShape() {
        final String checksum = "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
        final Path cacheRoot = Path.of(".local/runtime-cache");
        final ManagedPostgresSpringProperties.RuntimeProperties runtime =
                new ManagedPostgresSpringProperties.RuntimeProperties(
                        "downloaded",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(checksum),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(cacheRoot));

        assertThat(ManagedPostgresSpringRuntimeMapper.runtimeSource(runtime).downloadedRuntime())
                .get()
                .satisfies(downloadedRuntime -> {
                    assertThat(downloadedRuntime.repository()).isEmpty();
                    assertThat(downloadedRuntime.checksum()).contains(checksum);
                    assertThat(downloadedRuntime.cache()).contains(RuntimeCache.projectLocal(cacheRoot));
                });
    }
}
