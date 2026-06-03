package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class RuntimeCacheLayoutTest {

    private static final Checksum CHECKSUM =
            Checksum.parse("sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @TempDir
    private Path temporaryDirectory;

    RuntimeCacheLayoutTest() {}

    @Test
    void cachePathsAreDeterministicFromVersionAndChecksum() {
        final RuntimeCacheLayout firstLayout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));
        final RuntimeCacheLayout secondLayout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));

        final Path firstRuntime = firstLayout.runtimeDirectory("16.4", CHECKSUM);
        final Path secondRuntime = secondLayout.runtimeDirectory("16.4", CHECKSUM);

        assertThat(firstRuntime).isEqualTo(secondRuntime);
        assertThat(fileName(firstRuntime)).isEqualTo("postgres-16.4-sha256-0123456789ab");
    }

    @Test
    void cachePathRejectsVersionTraversal() {
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));

        assertThatThrownBy(() -> layout.runtimeDirectory("../16.4", CHECKSUM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
        assertThatThrownBy(() -> layout.runtimeDirectory("..", CHECKSUM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
        assertThatThrownBy(() -> layout.runtimeDirectory("16/4", CHECKSUM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void stagingDirectoryIsSiblingOfFinalRuntimeDirectory() {
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));

        final Path runtime = layout.runtimeDirectory("16.4", CHECKSUM);
        final Path staging = layout.stagingDirectory("16.4", CHECKSUM);

        assertThat(staging.getParent()).isEqualTo(runtime.getParent());
        assertThat(fileName(staging)).isEqualTo(fileName(runtime) + ".staging");
    }

    @Test
    void partialDownloadFilePathIsInsideFrameworkOwnedCacheRoot() {
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));

        final Path download = layout.downloadFile("16.4", CHECKSUM);

        assertThat(download.startsWith(layout.cacheRoot())).isTrue();
        assertThat(download.getParent()).isEqualTo(layout.downloadsDirectory());
        assertThat(fileName(download)).isEqualTo("postgres-16.4-sha256-0123456789ab.zip.download");
    }

    private static String fileName(final Path path) {
        return Objects.requireNonNull(path.getFileName(), "fileName").toString();
    }
}
