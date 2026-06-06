package eu.virtualparadox.managedpostgres.runtime.packaging.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.packaging.BundleManifest;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import eu.virtualparadox.managedpostgres.runtime.packaging.testsupport.RawInstallTreeFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BundleNormalizerTest {

    @TempDir
    Path tempDir;

    BundleNormalizerTest() {}

    @Test
    void producesNormalizedRuntimeDirectory() throws IOException {
        final Path rawInstallTree = RawInstallTreeFixture.create(tempDir);
        final BundleNormalizer normalizer = new BundleNormalizer();

        final Path normalized = normalizer.normalize(rawInstallTree, tempDir.resolve("normalized"), manifest());

        assertThat(normalized.resolve("bin/postgres")).exists();
        assertThat(normalized.resolve("share/extension.sql")).exists();
        assertThat(normalized.resolve("manifest.json")).exists();
        assertThat(Files.isExecutable(normalized.resolve("bin/postgres"))).isTrue();
    }

    @Test
    void rejectsMissingPostgresExecutable() throws IOException {
        final Path rawInstallTree = tempDir.resolve("raw-install");
        Files.createDirectories(rawInstallTree.resolve("bin"));
        Files.createDirectories(rawInstallTree.resolve("share"));
        final BundleNormalizer normalizer = new BundleNormalizer();

        assertThatThrownBy(() -> normalizer.normalize(rawInstallTree, tempDir.resolve("normalized"), manifest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postgres");
    }

    @Test
    void acceptsWindowsPostgresExecutable() throws IOException {
        final Path rawInstallTree = tempDir.resolve("raw-install-windows");
        Files.createDirectories(rawInstallTree.resolve("bin"));
        Files.createDirectories(rawInstallTree.resolve("share"));
        Files.writeString(rawInstallTree.resolve("bin/postgres.exe"), "binary", StandardCharsets.UTF_8);
        Files.writeString(rawInstallTree.resolve("share/extension.sql"), "-- extension\n", StandardCharsets.UTF_8);
        final BundleNormalizer normalizer = new BundleNormalizer();

        final Path normalized =
                normalizer.normalize(rawInstallTree, tempDir.resolve("normalized-windows"), windowsManifest());

        assertThat(normalized.resolve("bin/postgres.exe")).exists();
        assertThat(normalized.resolve("manifest.json")).exists();
    }

    private static BundleManifest manifest() {
        return new BundleManifest(
                "16.14",
                "r1",
                TargetPlatform.MACOS_AARCH64,
                "managed-postgres-runtime-pg16.14-macos-aarch64-r1.zip",
                "abc123",
                Instant.parse("2026-05-29T00:00:00Z"),
                "https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz");
    }

    private static BundleManifest windowsManifest() {
        return new BundleManifest(
                "16.14",
                "r1",
                TargetPlatform.WINDOWS_X86_64,
                "managed-postgres-runtime-pg16.14-windows-x86_64-r1.zip",
                "abc123",
                Instant.parse("2026-05-29T00:00:00Z"),
                "https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz");
    }
}
