package eu.virtualparadox.managedpostgres.runtime.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.DownloadedRuntime;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class OfficialRuntimeSourceResolverTest {

    private static final String BASE = "https://example.test/org/managed-postgres";
    private static final String GLIBC_HEX = "a".repeat(64);
    private static final String MUSL_HEX = "b".repeat(64);

    OfficialRuntimeSourceResolverTest() {
    }

    @Test
    void resolvesOfficialSourceToConcreteArchiveAndChecksum() {
        final AtomicReference<URI> requested = new AtomicReference<>();
        final String sums =
                GLIBC_HEX + "  managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip\n"
                        + MUSL_HEX + "  managed-postgres-runtime-pg18.4-linux-x86_64-musl-r1.zip\n";
        final OfficialRuntimeSourceResolver resolver = new OfficialRuntimeSourceResolver(
                BASE, "r1", () -> "linux-x86_64-glibc", uri -> {
                    requested.set(uri);
                    return sums;
                });

        final RuntimeSource resolved = resolver.resolve(
                RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.official())), "18.4");

        final DownloadedRuntime runtime = resolved.downloadedRuntime().orElseThrow();
        assertThat(runtime.repository().orElseThrow().uri()).hasToString(
                BASE + "/releases/download/pg18.4-r1/managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip");
        assertThat(runtime.checksum().orElseThrow()).isEqualTo("sha256:" + GLIBC_HEX);
        assertThat(requested.get()).hasToString(BASE + "/releases/download/pg18.4-r1/SHA256SUMS");
    }

    @Test
    void resolvesGitHubReleaseRepositoryToConcreteArchiveAndChecksum() {
        final String sums = GLIBC_HEX + "  managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip\n";
        final OfficialRuntimeSourceResolver resolver = new OfficialRuntimeSourceResolver(
                BASE, "r1", () -> "linux-x86_64-glibc", uri -> sums);

        final RuntimeSource resolved = resolver.resolve(RuntimeSource.downloaded(runtime ->
                runtime.repository(RuntimeRepository.custom(URI.create("github-release://acme/pgr")))), "18.4");

        final DownloadedRuntime runtime = resolved.downloadedRuntime().orElseThrow();
        assertThat(runtime.repository().orElseThrow().uri()).hasToString(
                "https://github.com/acme/pgr/releases/download/pg18.4-r1/"
                        + "managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip");
        assertThat(runtime.checksum().orElseThrow()).isEqualTo("sha256:" + GLIBC_HEX);
    }

    @Test
    void passesThroughCustomRepositoryUnchanged() {
        final RuntimeSource custom = RuntimeSource.downloaded(runtime -> runtime
                .repository(RuntimeRepository.custom(URI.create("https://mirror.test/pg.zip")))
                .checksum("sha256:" + MUSL_HEX));
        final OfficialRuntimeSourceResolver resolver = new OfficialRuntimeSourceResolver(
                BASE, "r1", () -> "linux-x86_64-glibc", uri -> {
                    throw new AssertionError("must not fetch for a custom repository");
                });

        assertThat(resolver.resolve(custom, "18.4")).isSameAs(custom);
    }

    @Test
    void passesThroughDownloadedSourceWithoutRepositoryUnchanged() {
        final RuntimeSource bare = RuntimeSource.downloaded();
        final OfficialRuntimeSourceResolver resolver = new OfficialRuntimeSourceResolver(
                BASE, "r1", () -> "linux-x86_64-glibc", uri -> {
                    throw new AssertionError("must not fetch when no official repository is configured");
                });

        assertThat(resolver.resolve(bare, "18.4")).isSameAs(bare);
    }

    @Test
    void passesThroughNonDownloadedSourceUnchanged() {
        final RuntimeSource system = RuntimeSource.system();
        final OfficialRuntimeSourceResolver resolver = new OfficialRuntimeSourceResolver(
                BASE, "r1", () -> "linux-x86_64-glibc", uri -> {
                    throw new AssertionError("must not fetch for a non-downloaded source");
                });

        assertThat(resolver.resolve(system, "18.4")).isSameAs(system);
    }

    @Test
    void requireSuccessfulBodyReturnsBodyOnSuccessAndThrowsOnError() {
        assertThat(OfficialRuntimeSourceResolver.requireSuccessfulBody(URI.create("https://x/y"), 200, "ok"))
                .isEqualTo("ok");
        assertThatThrownBy(() ->
                OfficialRuntimeSourceResolver.requireSuccessfulBody(URI.create("https://x/y"), 404, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void failsWhenHostPlatformIsNotPublished() {
        final OfficialRuntimeSourceResolver resolver = new OfficialRuntimeSourceResolver(
                BASE, "r1", () -> "linux-aarch64-musl",
                uri -> GLIBC_HEX + "  managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip\n");

        assertThatThrownBy(() -> resolver.resolve(
                RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.official())), "18.4"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no published bundle")
                .hasMessageContaining("linux-aarch64-musl");
    }
}
