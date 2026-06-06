package eu.virtualparadox.managedpostgres.runtime.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

final class PostgresReleaseTest {

    PostgresReleaseTest() {}

    @Test
    void storesSemanticVersion() {
        final PostgresRelease release = new PostgresRelease(
                16,
                "16.14",
                URI.create("https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz"),
                "abc123");

        assertThat(release.majorVersion()).isEqualTo(16);
        assertThat(release.version()).isEqualTo("16.14");
        assertThat(release.sourceTarball())
                .hasToString("https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz");
        assertThat(release.sourceTarballSha256()).isEqualTo("abc123");
    }

    @Test
    void requiresVersion() {
        assertThatThrownBy(() -> new PostgresRelease(
                        16,
                        " ",
                        URI.create("https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz"),
                        "abc123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void requiresPositiveMajorVersion() {
        assertThatThrownBy(() -> new PostgresRelease(
                        0,
                        "16.14",
                        URI.create("https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz"),
                        "abc123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("majorVersion");
    }

    @Test
    void requiresSourceTarballChecksum() {
        assertThatThrownBy(() -> new PostgresRelease(
                        16,
                        "16.14",
                        URI.create("https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz"),
                        " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceTarballSha256");
    }
}
