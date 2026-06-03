package eu.virtualparadox.managedpostgres.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class ClasspathRuntimeConfigTest {

    private static final String SHA256_CHECKSUM =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    ClasspathRuntimeConfigTest() {}

    @Test
    void classpathRuntimeSourceStoresResourceCacheAndChecksum() {
        final Path cacheRoot = Path.of("target/classpath-runtime-cache");

        final RuntimeSource runtimeSource = RuntimeSource.classpath(
                "/postgres-runtime.zip",
                runtime -> runtime.cache(RuntimeCache.projectLocal(cacheRoot)).checksum(SHA256_CHECKSUM));

        assertThat(runtimeSource.kind()).isEqualTo("classpath");
        assertThat(runtimeSource.existingPath()).isEmpty();
        assertThat(runtimeSource.downloadedRuntime()).isEmpty();
        assertThat(runtimeSource.classpathRuntime()).get().satisfies(classpathRuntime -> {
            assertThat(classpathRuntime.resource()).isEqualTo("/postgres-runtime.zip");
            assertThat(classpathRuntime.cache()).contains(RuntimeCache.projectLocal(cacheRoot));
            assertThat(classpathRuntime.checksum()).contains(SHA256_CHECKSUM);
        });
    }

    @Test
    void classpathRuntimeSupportsWithMethodsAsImmutableAliases() {
        final Path cacheRoot = Path.of("target/classpath-runtime-cache");
        final ClasspathRuntime classpathRuntime = ClasspathRuntime.resource("/postgres-runtime.zip")
                .withCache(RuntimeCache.projectLocal(cacheRoot))
                .withChecksum(SHA256_CHECKSUM);

        assertThat(classpathRuntime.resource()).isEqualTo("/postgres-runtime.zip");
        assertThat(classpathRuntime.cache()).contains(RuntimeCache.projectLocal(cacheRoot));
        assertThat(classpathRuntime.checksum()).contains(SHA256_CHECKSUM);
    }

    @Test
    void classpathRuntimeConfigurationRejectsInvalidValues() {
        assertThatThrownBy(() -> ClasspathRuntime.resource(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resource");
        assertThatThrownBy(ClasspathRuntimeConfigTest::classpathRuntimeWithBlankChecksum)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
        assertThatThrownBy(() -> new RuntimeSource("classpath", Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("classpath runtime");
        assertThatThrownBy(() -> new RuntimeSource(
                        "classpath",
                        Optional.of(Path.of("runtime")),
                        Optional.empty(),
                        Optional.of(ClasspathRuntime.resource("/postgres-runtime.zip")
                                .checksum(SHA256_CHECKSUM))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existing runtime source");
    }

    private static ClasspathRuntime classpathRuntimeWithBlankChecksum() {
        return ClasspathRuntime.resource("/postgres-runtime.zip").checksum("");
    }
}
