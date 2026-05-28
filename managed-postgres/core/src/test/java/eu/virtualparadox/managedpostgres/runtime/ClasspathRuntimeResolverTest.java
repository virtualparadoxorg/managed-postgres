package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.filesystem.ManagedPathOwnership;
import eu.virtualparadox.managedpostgres.internal.runtime.ResolvedRuntime;
import eu.virtualparadox.managedpostgres.runtime.classpath.ClasspathRuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.EntrySpec;
import eu.virtualparadox.managedpostgres.runtime.testsupport.TarGzipArchiveTestSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class ClasspathRuntimeResolverTest {

    @TempDir
    private Path temporaryDirectory;

    ClasspathRuntimeResolverTest() {
    }

    @Test
    void cachedValidatedRuntimeIsReusedWithoutOpeningResource() throws IOException {
        final Path archive = zipWithEntries(entry("unused", "unused"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum);
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);
        final ClassLoader failingClassLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                throw new AssertionError("cached runtime must not open classpath resource");
            }
        };

        final Path resolvedRuntime = new ClasspathRuntimeResolver(failingClassLoader).resolve(
                classpathSource("/postgres-runtime.zip", cacheRoot, checksumText),
                "16.4");

        assertThat(resolvedRuntime).isEqualTo(cachedRuntime);
    }

    @Test
    void cachedValidatedRuntimeReportsZeroInstallDuration() throws IOException {
        final Path archive = zipWithEntries(entry("unused", "unused"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum);
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);

        final ResolvedRuntime resolvedRuntime = new ClasspathRuntimeResolver(classLoader(temporaryDirectory)).resolveWithTelemetry(
                classpathSource("/unused.zip", cacheRoot, checksumText),
                "16.4");

        assertThat(resolvedRuntime.runtimeDirectory()).isEqualTo(cachedRuntime);
        assertThat(resolvedRuntime.installDuration()).isZero();
    }

    @Test
    void cachedClasspathRuntimeResolutionAppliesRuntimeCacheRetention() throws IOException {
        final Path archive = zipWithEntries(entry("unused", "unused"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum);
        final Path oldRuntime = layout.runtimesDirectory().resolve("postgres-16.3-sha256-aaaaaaaaaaaa");
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);
        RuntimeArchiveTestSupport.createUsableRuntime(oldRuntime);
        ownership.writeMarker(cachedRuntime, "install-classpath-runtime");
        ownership.writeMarker(oldRuntime, "install-classpath-runtime");
        setModifiedTime(cachedRuntime, "2026-05-28T00:00:00Z");
        setModifiedTime(oldRuntime, "2026-05-27T00:00:00Z");
        final ClassLoader failingClassLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                throw new AssertionError("cached runtime must not open classpath resource");
            }
        };

        final Path resolvedRuntime = new ClasspathRuntimeResolver(failingClassLoader).resolve(
                classpathSource(
                        "/postgres-runtime.zip",
                        RuntimeCache.projectLocal(cacheRoot).keepVersions(1),
                        checksumText),
                "16.4");

        assertThat(resolvedRuntime).isEqualTo(cachedRuntime);
        assertThat(cachedRuntime).isDirectory();
        assertThat(oldRuntime).doesNotExist();
    }

    @Test
    void missingCacheReadsVerifiesExtractsValidatesAndPublishesRuntime() throws IOException {
        final Path resourceRoot = temporaryDirectory.resolve("resources");
        final Path archive = zipWithEntries(
                resourceRoot.resolve("postgres-runtime.zip"),
                entry("bin/pg_ctl", "pg_ctl"),
                entry("bin/psql", "psql"),
                entry("bin/postgres", "postgres"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path finalRuntime = layout.runtimeDirectory("16.4", checksum);

        final Path resolvedRuntime = new ClasspathRuntimeResolver(classLoader(resourceRoot)).resolve(
                classpathSource("/postgres-runtime.zip", cacheRoot, checksumText),
                "16.4");

        assertThat(resolvedRuntime).isEqualTo(finalRuntime);
        assertThat(finalRuntime.resolve("bin").resolve("pg_ctl")).isRegularFile();
        assertThat(finalRuntime.resolve("bin").resolve("postgres")).isRegularFile();
        assertThat(layout.downloadFile("16.4", checksum)).doesNotExist();
        assertThat(layout.stagingDirectory("16.4", checksum)).doesNotExist();
    }

    @Test
    void missingCacheClasspathInstallReportsPositiveDuration() throws IOException {
        final Path resourceRoot = temporaryDirectory.resolve("resources");
        final Path archive = zipWithEntries(
                resourceRoot.resolve("postgres-runtime.zip"),
                entry("bin/pg_ctl", "pg_ctl"),
                entry("bin/psql", "psql"),
                entry("bin/postgres", "postgres"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);

        final ResolvedRuntime resolvedRuntime = new ClasspathRuntimeResolver(classLoader(resourceRoot)).resolveWithTelemetry(
                classpathSource("/postgres-runtime.zip", temporaryDirectory.resolve("cache"), checksumText),
                "16.4");

        assertThat(resolvedRuntime.runtimeDirectory()).isDirectory();
        assertThat(resolvedRuntime.installDuration()).isPositive();
    }

    @Test
    void missingCacheReadsTarGzipVerifiesExtractsValidatesAndPublishesRuntime() throws IOException {
        final Path resourceRoot = temporaryDirectory.resolve("resources");
        final Path archive = tarGzipWithEntries(
                resourceRoot.resolve("postgres-runtime.tgz"),
                entry("bin/pg_ctl", "pg_ctl"),
                entry("bin/psql", "psql"),
                entry("bin/postgres", "postgres"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path finalRuntime = layout.runtimeDirectory("16.4", checksum);

        final Path resolvedRuntime = new ClasspathRuntimeResolver(classLoader(resourceRoot)).resolve(
                classpathSource("/postgres-runtime.tgz", cacheRoot, checksumText),
                "16.4");

        assertThat(resolvedRuntime).isEqualTo(finalRuntime);
        assertThat(finalRuntime.resolve("bin").resolve("pg_ctl")).isRegularFile();
        assertThat(finalRuntime.resolve("bin").resolve("postgres")).isRegularFile();
        assertThat(layout.downloadFile("16.4", checksum)).doesNotExist();
        assertThat(layout.stagingDirectory("16.4", checksum)).doesNotExist();
    }

    @Test
    void defaultRuntimeResolverDispatchesClasspathSource() throws IOException {
        final Path archive = zipWithEntries(entry("unused", "unused"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum);
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);

        final Path resolvedRuntime = new DefaultRuntimeResolver().resolve(
                classpathSource("postgres-runtime.zip", cacheRoot, checksumText),
                "16.4");

        assertThat(resolvedRuntime).isEqualTo(cachedRuntime);
    }

    @Test
    void failedChecksumLeavesNoFinalRuntime() throws IOException {
        final Path resourceRoot = temporaryDirectory.resolve("resources");
        final Path archive = zipWithEntries(
                resourceRoot.resolve("postgres-runtime.zip"),
                entry("bin/pg_ctl", "pg_ctl"),
                entry("bin/psql", "psql"),
                entry("bin/postgres", "postgres"));
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final String wrongChecksumText =
                "sha256:0000000000000000000000000000000000000000000000000000000000000000";
        final Checksum wrongChecksum = Checksum.parse(wrongChecksumText);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);

        assertThatThrownBy(() -> new ClasspathRuntimeResolver(classLoader(resourceRoot)).resolve(
                classpathSource("postgres-runtime.zip", cacheRoot, wrongChecksumText),
                "16.4"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("classpath")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(layout.runtimeDirectory("16.4", wrongChecksum)).doesNotExist();
        assertThat(layout.downloadFile("16.4", wrongChecksum)).doesNotExist();
        assertThat(Files.size(archive)).isPositive();
    }

    @Test
    void pathTraversalArchiveLeavesNoFinalRuntime() throws IOException {
        final Path resourceRoot = temporaryDirectory.resolve("resources");
        final Path archive = zipWithEntries(resourceRoot.resolve("postgres-runtime.zip"), entry("../evil", "evil"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);

        assertThatThrownBy(() -> new ClasspathRuntimeResolver(classLoader(resourceRoot)).resolve(
                classpathSource("postgres-runtime.zip", cacheRoot, checksumText),
                "16.4"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("classpath")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(layout.runtimeDirectory("16.4", checksum)).doesNotExist();
        assertThat(layout.stagingDirectory("16.4", checksum)).doesNotExist();
        assertThat(temporaryDirectory.resolve("evil")).doesNotExist();
    }

    @Test
    void missingResourceFailsWithClearDiagnostic() {
        final RuntimeSource runtimeSource = classpathSource(
                "missing-postgres-runtime.zip",
                temporaryDirectory.resolve("cache"),
                "sha256:0000000000000000000000000000000000000000000000000000000000000000");

        assertThatThrownBy(() -> new ClasspathRuntimeResolver(getClass().getClassLoader()).resolve(
                runtimeSource,
                "16.4"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("classpath")
                .satisfies(throwable -> assertThat(((ManagedPostgresException) throwable)
                        .diagnosticReport()
                        .renderText())
                        .contains("classpath"));
    }

    private static RuntimeSource classpathSource(
            final String resource,
            final Path cacheRoot,
            final String checksumText) {
        return classpathSource(resource, RuntimeCache.projectLocal(cacheRoot), checksumText);
    }

    private static RuntimeSource classpathSource(
            final String resource,
            final RuntimeCache runtimeCache,
            final String checksumText) {
        return RuntimeSource.classpath(resource, runtime -> runtime
                .cache(runtimeCache)
                .checksum(checksumText));
    }

    private static void setModifiedTime(final Path directory, final String instant) throws IOException {
        Files.setLastModifiedTime(directory, FileTime.from(Instant.parse(instant)));
    }

    private static ClassLoader classLoader(final Path resourceRoot) {
        return new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                final Path resource = resourceRoot.resolve(name);
                final InputStream inputStream;
                if (Files.isRegularFile(resource)) {
                    try {
                        inputStream = Files.newInputStream(resource);
                    } catch (final IOException exception) {
                        throw new java.io.UncheckedIOException(exception);
                    }
                } else {
                    throw new AssertionError("missing classpath test resource: " + name);
                }

                return inputStream;
            }
        };
    }

    private Path zipWithEntries(final EntrySpec... entries) throws IOException {
        return RuntimeArchiveTestSupport.zipWithEntries(
                Files.createTempFile(temporaryDirectory, "runtime-", ".zip"),
                entries);
    }

    private static Path zipWithEntries(final Path archive, final EntrySpec... entries) throws IOException {
        return RuntimeArchiveTestSupport.zipWithEntries(archive, entries);
    }

    private static Path tarGzipWithEntries(final Path archive, final EntrySpec... entries) throws IOException {
        return TarGzipArchiveTestSupport.tarGzipWithEntries(archive, entries);
    }

    private static EntrySpec entry(final String name, final String content) {
        return RuntimeArchiveTestSupport.entry(name, content);
    }
}
