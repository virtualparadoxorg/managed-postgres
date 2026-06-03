package eu.virtualparadox.managedpostgres.runtime.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

final class BundleManifestTest {

    BundleManifestTest() {}

    @Test
    void storesStableFieldsForPublishedBundle() {
        final BundleManifest manifest = new BundleManifest(
                "16.14",
                "r1",
                TargetPlatform.MACOS_AARCH64,
                "managed-postgres-runtime-pg16.14-macos-aarch64-r1.tar.gz",
                "abc123",
                Instant.parse("2026-05-29T00:00:00Z"),
                "https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz");

        assertThat(manifest.archiveFileName()).isEqualTo("managed-postgres-runtime-pg16.14-macos-aarch64-r1.tar.gz");
        assertThat(manifest.targetPlatform()).isEqualTo(TargetPlatform.MACOS_AARCH64);
    }

    @Test
    void requiresPostgresVersion() {
        assertThatThrownBy(() -> new BundleManifest(
                        " ",
                        "r1",
                        TargetPlatform.WINDOWS_X86_64,
                        "bundle.zip",
                        "abc123",
                        Instant.parse("2026-05-29T00:00:00Z"),
                        "https://example.invalid/postgresql.tar.gz"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postgresVersion");
    }

    @Test
    void requiresSourceUri() {
        assertThatThrownBy(() -> new BundleManifest(
                        "16.14",
                        "r1",
                        TargetPlatform.WINDOWS_X86_64,
                        "bundle.zip",
                        "abc123",
                        Instant.parse("2026-05-29T00:00:00Z"),
                        " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceUri");
    }
}
