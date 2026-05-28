package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PostgresRuntimeManifestTest {

    PostgresRuntimeManifestTest() {
    }

    @Test
    void manifestFactoriesDescribeRuntimeSourceAndIdentity() {
        final PostgresRuntimeIdentity identity = new PostgresRuntimeIdentity("sha256:abc123");

        assertThat(PostgresRuntimeManifest.system("16.4"))
                .extracting(
                        PostgresRuntimeManifest::postgresqlVersion,
                        PostgresRuntimeManifest::runtimeSource,
                        PostgresRuntimeManifest::runtimeIdentity)
                .containsExactly("16.4", "system", Optional.empty());
        assertThat(PostgresRuntimeManifest.existing("16.5").runtimeSource()).isEqualTo("existing");
        assertThat(PostgresRuntimeManifest.downloaded("17.0", identity).runtimeIdentity()).hasValue(identity);
    }

    @Test
    void manifestRequiresPostgresqlVersion() {
        assertThatThrownBy(() -> PostgresRuntimeManifest.existing(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postgresqlVersion");
    }

    @Test
    void manifestRequiresRuntimeIdentityChecksumWhenDownloaded() {
        assertThatThrownBy(() -> PostgresRuntimeManifest.downloaded("16.4", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void manifestRejectsUnsupportedRuntimeSource() {
        assertThatThrownBy(() -> new PostgresRuntimeManifest("16.4", "archive", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtimeSource");
    }

    @Test
    void runtimeIdentityRequiresChecksum() {
        assertThatThrownBy(() -> new PostgresRuntimeIdentity(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void runtimeIdentityToStringDoesNotExposePlatformInternals() {
        final PostgresRuntimeIdentity identity = new PostgresRuntimeIdentity("sha256:abc123");

        assertThat(identity.toString())
                .contains("sha256:abc123")
                .doesNotContain("linux")
                .doesNotContain("darwin")
                .doesNotContain("windows")
                .doesNotContain("x86")
                .doesNotContain("amd64")
                .doesNotContain("aarch64")
                .doesNotContain("glibc")
                .doesNotContain("musl");
    }
}
