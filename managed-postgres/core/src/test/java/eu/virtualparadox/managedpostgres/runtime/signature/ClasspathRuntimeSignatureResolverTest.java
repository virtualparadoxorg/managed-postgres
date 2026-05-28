package eu.virtualparadox.managedpostgres.runtime.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.internal.runtime.signature.RuntimeSignatureVerifier;
import eu.virtualparadox.managedpostgres.runtime.Checksum;
import eu.virtualparadox.managedpostgres.runtime.classpath.ClasspathRuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.EntrySpec;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeSignatureTestSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class ClasspathRuntimeSignatureResolverTest {

    private static final String SIGNATURE_MARKER_FILE = ".managed-postgres-runtime-signature";

    @TempDir
    private Path temporaryDirectory;

    ClasspathRuntimeSignatureResolverTest() {
    }

    @Test
    void signedClasspathRuntimeVerifiesPublishesAndWritesMarker() throws IOException {
        final Path resourceRoot = temporaryDirectory.resolve("resources");
        final Path archive = archive(resourceRoot.resolve("postgres-runtime.zip"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(archive);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path finalRuntime = layout.runtimeDirectory("16.4", checksum, signature);

        final Path resolvedRuntime = new ClasspathRuntimeResolver(classLoader(resourceRoot)).resolve(
                classpathSource("postgres-runtime.zip", cacheRoot, checksumText, signature),
                "16.4");

        assertThat(resolvedRuntime).isEqualTo(finalRuntime);
        assertThat(finalRuntime.resolve("bin").resolve("postgres")).isRegularFile();
        new RuntimeSignatureVerifier().requireVerifiedMarker(finalRuntime, signature);
    }

    @Test
    void signedClasspathRuntimeMarkerSurvivesArchiveEntryWithReservedName() throws IOException {
        final Path resourceRoot = temporaryDirectory.resolve("resources");
        final Path archive = archiveWithReservedMarker(resourceRoot.resolve("postgres-runtime.zip"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(archive);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path finalRuntime = layout.runtimeDirectory("16.4", checksum, signature);

        new ClasspathRuntimeResolver(classLoader(resourceRoot)).resolve(
                classpathSource("postgres-runtime.zip", cacheRoot, checksumText, signature),
                "16.4");

        new RuntimeSignatureVerifier().requireVerifiedMarker(finalRuntime, signature);
    }

    @Test
    void invalidClasspathRuntimeSignatureLeavesNoFinalRuntimeOrStaging() throws IOException {
        final Path resourceRoot = temporaryDirectory.resolve("resources");
        final Path archive = archive(resourceRoot.resolve("postgres-runtime.zip"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeSignature signature = RuntimeSignatureTestSupport.invalidSignatureFor(archive);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);

        assertThatThrownBy(() -> new ClasspathRuntimeResolver(classLoader(resourceRoot)).resolve(
                classpathSource("postgres-runtime.zip", cacheRoot, checksumText, signature),
                "16.4"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("classpath")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(layout.runtimeDirectory("16.4", checksum, signature)).doesNotExist();
        assertThat(layout.stagingDirectory("16.4", checksum, signature)).doesNotExist();
    }

    @Test
    void signedClasspathCacheHitRequiresMatchingMarker() throws IOException {
        final Path resourceRoot = temporaryDirectory.resolve("resources");
        final Path archive = archive(resourceRoot.resolve("postgres-runtime.zip"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(archive);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum, signature);
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);

        assertThatThrownBy(() -> new ClasspathRuntimeResolver(failingClassLoader()).resolve(
                classpathSource("postgres-runtime.zip", cacheRoot, checksumText, signature),
                "16.4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature marker");
    }

    private static RuntimeSource classpathSource(
            final String resource,
            final Path cacheRoot,
            final String checksum,
            final RuntimeSignature signature) {
        return RuntimeSource.classpath(resource, runtime -> runtime
                .cache(RuntimeCache.projectLocal(cacheRoot))
                .checksum(checksum)
                .signature(signature));
    }

    private static ClassLoader classLoader(final Path resourceRoot) {
        return new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                final Path resource = resourceRoot.resolve(name);
                try {
                    return Files.newInputStream(resource);
                } catch (final IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            }
        };
    }

    private static ClassLoader failingClassLoader() {
        return new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                throw new AssertionError("signed cache hit must not read classpath resource");
            }
        };
    }

    private static Path archive(final Path archive) throws IOException {
        return RuntimeArchiveTestSupport.zipWithEntries(
                archive,
                entry("bin/pg_ctl", "pg_ctl"),
                entry("bin/psql", "psql"),
                entry("bin/postgres", "postgres"));
    }

    private static Path archiveWithReservedMarker(final Path archive) throws IOException {
        return RuntimeArchiveTestSupport.zipWithEntries(
                archive,
                entry(SIGNATURE_MARKER_FILE, "algorithm=Ed25519%nfingerprint=archive-owned%n"),
                entry("bin/pg_ctl", "pg_ctl"),
                entry("bin/psql", "psql"),
                entry("bin/postgres", "postgres"));
    }

    private static EntrySpec entry(final String name, final String content) {
        return RuntimeArchiveTestSupport.entry(name, content);
    }
}
