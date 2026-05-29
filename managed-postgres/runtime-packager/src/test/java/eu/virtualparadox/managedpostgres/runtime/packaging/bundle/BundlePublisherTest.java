package eu.virtualparadox.managedpostgres.runtime.packaging.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.runtime.packaging.BundleManifest;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BundlePublisherTest {

    @TempDir
    Path tempDir;

    BundlePublisherTest() {
    }

    @Test
    void writesBundleAndChecksumIntoReleaseDirectory() throws IOException {
        final Path bundleDirectory = createNormalizedBundle();
        final Path publishDirectory = tempDir.resolve("published");
        final BundlePublisher publisher = new BundlePublisher();

        final PublishResult result = publisher.publish(bundleDirectory, publishDirectory, manifest());
        final Path bundleFileName = java.util.Objects.requireNonNull(result.bundle().getFileName(), "bundle.fileName");

        assertThat(result.bundle()).exists();
        assertThat(result.bundleChecksum()).exists();
        assertThat(result.manifest()).exists();
        assertThat(Files.readString(result.bundleChecksum())).contains(bundleFileName.toString());
    }

    private Path createNormalizedBundle() throws IOException {
        final Path normalized = tempDir.resolve("normalized");
        Files.createDirectories(normalized.resolve("runtime/bin"));
        Files.writeString(normalized.resolve("runtime/bin/postgres"), "binary", StandardCharsets.UTF_8);
        Files.createDirectories(normalized.resolve("runtime/share"));
        Files.writeString(normalized.resolve("runtime/share/extension.sql"), "share", StandardCharsets.UTF_8);
        Files.writeString(normalized.resolve("manifest.json"), "{\"postgresVersion\":\"16.14\"}", StandardCharsets.UTF_8);
        return normalized;
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
}
