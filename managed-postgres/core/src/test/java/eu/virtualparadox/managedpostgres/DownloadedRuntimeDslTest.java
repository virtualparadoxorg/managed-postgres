package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import org.junit.jupiter.api.Test;

final class DownloadedRuntimeDslTest {

    DownloadedRuntimeDslTest() {
    }

    @Test
    void officialRepositoryConfiguresDownloadedRuntime() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder) ManagedPostgres.create()
                .version("18.4")
                .withDownloadedRuntime()
                .fromOfficialRepository();

        final RuntimeSource runtimeSource = builder.configuration().runtimeSource();
        assertThat(runtimeSource.kind()).isEqualTo("downloaded");
        assertThat(runtimeSource.downloadedRuntime().orElseThrow().repository().orElseThrow().uri())
                .hasToString("managed-postgres:official");
    }

    @Test
    void gitHubReleaseConfiguresDownloadedRuntime() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder) ManagedPostgres.create()
                .version("18.4")
                .withDownloadedRuntime()
                .fromGitHubRelease("acme", "pgr");

        final RuntimeSource runtimeSource = builder.configuration().runtimeSource();
        assertThat(runtimeSource.kind()).isEqualTo("downloaded");
        assertThat(runtimeSource.downloadedRuntime().orElseThrow().repository().orElseThrow().uri())
                .hasToString("github-release://acme/pgr");
    }
}
