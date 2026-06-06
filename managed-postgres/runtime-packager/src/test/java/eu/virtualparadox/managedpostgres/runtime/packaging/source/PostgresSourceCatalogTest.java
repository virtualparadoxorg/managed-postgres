package eu.virtualparadox.managedpostgres.runtime.packaging.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import org.junit.jupiter.api.Test;

final class PostgresSourceCatalogTest {

    PostgresSourceCatalogTest() {}

    @Test
    void returnsOfficialSourceTarballForVersion() {
        final PostgresRelease release = new PostgresSourceCatalog().releaseFor("16.14");

        assertThat(release.sourceTarball())
                .hasToString("https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz");
    }

    @Test
    void rejectsVersionWithoutTrustedChecksum() {
        assertThatThrownBy(() -> new PostgresSourceCatalog().releaseFor("16.15"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted checksum");
    }

    @Test
    void rejectsBlankVersion() {
        assertThatThrownBy(() -> new PostgresSourceCatalog().releaseFor(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }
}
