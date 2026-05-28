package eu.virtualparadox.managedpostgres.runtime.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.internal.runtime.signature.RuntimeSignatureVerifier;
import eu.virtualparadox.managedpostgres.runtime.Checksum;
import eu.virtualparadox.managedpostgres.runtime.download.DownloadedRuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeArtifactDownloader;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.EntrySpec;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeSignatureTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class DownloadedRuntimeSignatureResolverTest {

    private static final String SIGNATURE_MARKER_FILE = ".managed-postgres-runtime-signature";

    @TempDir
    private Path temporaryDirectory;

    DownloadedRuntimeSignatureResolverTest() {
    }

    @Test
    void signedDownloadedRuntimeVerifiesPublishesAndWritesMarker() throws IOException {
        final Path archive = archive();
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(archive);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path finalRuntime = layout.runtimeDirectory("16.4", checksum, signature);

        final Path resolvedRuntime = new DownloadedRuntimeResolver().resolve(
                downloadedSource(archive, cacheRoot, checksumText, signature),
                "16.4");

        assertThat(resolvedRuntime).isEqualTo(finalRuntime);
        assertThat(finalRuntime.resolve("bin").resolve("postgres")).isRegularFile();
        new RuntimeSignatureVerifier().requireVerifiedMarker(finalRuntime, signature);
    }

    @Test
    void signedDownloadedRuntimeMarkerSurvivesArchiveEntryWithReservedName() throws IOException {
        final Path archive = archiveWithReservedMarker();
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(archive);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path finalRuntime = layout.runtimeDirectory("16.4", checksum, signature);

        new DownloadedRuntimeResolver().resolve(
                downloadedSource(archive, cacheRoot, checksumText, signature),
                "16.4");

        new RuntimeSignatureVerifier().requireVerifiedMarker(finalRuntime, signature);
    }

    @Test
    void invalidDownloadedRuntimeSignatureLeavesNoFinalRuntimeOrStaging() throws IOException {
        final Path archive = archive();
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeSignature signature = RuntimeSignatureTestSupport.invalidSignatureFor(archive);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);

        assertThatThrownBy(() -> new DownloadedRuntimeResolver().resolve(
                downloadedSource(archive, cacheRoot, checksumText, signature),
                "16.4"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("downloaded")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(layout.runtimeDirectory("16.4", checksum, signature)).doesNotExist();
        assertThat(layout.stagingDirectory("16.4", checksum, signature)).doesNotExist();
    }

    @Test
    void signedDownloadedCacheHitRequiresMatchingMarker() throws IOException {
        final Path archive = archive();
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(archive);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum, signature);
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);
        final RuntimeArtifactDownloader downloader = (repository, target, expectedChecksum) -> {
            throw new AssertionError("signed cache hit must not redownload");
        };

        assertThatThrownBy(() -> new DownloadedRuntimeResolver(downloader).resolve(
                downloadedSource(archive, cacheRoot, checksumText, signature),
                "16.4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature marker");
    }

    private RuntimeSource downloadedSource(
            final Path archive,
            final Path cacheRoot,
            final String checksum,
            final RuntimeSignature signature) {
        return RuntimeSource.downloaded(runtime -> runtime
                .repository(RuntimeRepository.custom(archive.toUri()))
                .cache(RuntimeCache.projectLocal(cacheRoot))
                .checksum(checksum)
                .signature(signature));
    }

    private Path archive() throws IOException {
        return RuntimeArchiveTestSupport.zipWithEntries(
                Files.createTempFile(temporaryDirectory, "runtime-", ".zip"),
                entry("bin/pg_ctl", "pg_ctl"),
                entry("bin/psql", "psql"),
                entry("bin/postgres", "postgres"));
    }

    private Path archiveWithReservedMarker() throws IOException {
        return RuntimeArchiveTestSupport.zipWithEntries(
                Files.createTempFile(temporaryDirectory, "runtime-", ".zip"),
                entry(SIGNATURE_MARKER_FILE, "algorithm=Ed25519%nfingerprint=archive-owned%n"),
                entry("bin/pg_ctl", "pg_ctl"),
                entry("bin/psql", "psql"),
                entry("bin/postgres", "postgres"));
    }

    private static EntrySpec entry(final String name, final String content) {
        return RuntimeArchiveTestSupport.entry(name, content);
    }
}
