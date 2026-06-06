package eu.virtualparadox.managedpostgres.runtime.archive;

import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.assertExecutableIfSupported;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.assertRuntimeFiles;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.directory;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.entry;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.TarGzipArchiveTestSupport.tarGzipWithEntries;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.TarGzipArchiveTestSupport.tarGzipWithHardLink;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.TarGzipArchiveTestSupport.tarGzipWithSymbolicLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.EntrySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeArchiveExtractorTarGzipTest {

    @TempDir
    private Path temporaryDirectory;

    RuntimeArchiveExtractorTarGzipTest() {}

    @Test
    void validTarGzipExtractsRuntimeFilesUnderStaging() throws IOException {
        final Path archive = tarGzipArchive(
                entry("PG_VERSION", "16"), entry("bin/pg_ctl", "pg_ctl"), entry("bin/postgres", "postgres"));
        final Path staging = temporaryDirectory.resolve("staging");

        assertThat(new RuntimeArchiveExtractor().extract(archive, staging)).isEqualTo(staging);

        assertRuntimeFiles(staging);
    }

    @Test
    void tgzContentExtractsByMagicHeader() throws IOException {
        final Path archive =
                tgzArchive(entry("bin/pg_ctl", "pg_ctl"), entry("bin/psql", "psql"), entry("bin/postgres", "postgres"));
        final Path staging = temporaryDirectory.resolve("staging");

        new RuntimeArchiveExtractor().extract(archive, staging);

        assertThat(staging.resolve("bin").resolve("psql")).isRegularFile();
    }

    @Test
    void tarGzipDirectoryEntriesAreCreated() throws IOException {
        final Path archive = tarGzipArchive(directory("runtime/"), entry("runtime/PG_VERSION", "16"));
        final Path staging = temporaryDirectory.resolve("staging");

        new RuntimeArchiveExtractor().extract(archive, staging);

        assertThat(staging.resolve("runtime")).isDirectory();
        assertThat(Files.readString(staging.resolve("runtime").resolve("PG_VERSION")))
                .isEqualTo("16");
    }

    @Test
    void tarGzipTraversalEntriesAreRejected() throws IOException {
        final Path archive = tarGzipArchive(entry("../evil", "evil"));
        final Path staging = temporaryDirectory.resolve("staging");

        assertThatThrownBy(() -> new RuntimeArchiveExtractor().extract(archive, staging))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
        assertThat(temporaryDirectory.resolve("evil")).doesNotExist();
    }

    @Test
    void tarGzipLinksAreRejected() throws IOException {
        final Path symlinkArchive = tarGzipWithSymbolicLink(
                Files.createTempFile(temporaryDirectory, "runtime-", ".tar.gz"), "bin/postgres", "../evil");
        final Path hardlinkArchive = tarGzipWithHardLink(
                Files.createTempFile(temporaryDirectory, "runtime-", ".tar.gz"), "bin/psql", "bin/postgres");
        final Path staging = temporaryDirectory.resolve("staging");

        assertThatThrownBy(() -> new RuntimeArchiveExtractor().extract(symlinkArchive, staging.resolve("symbolic")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regular files and directories");
        assertThatThrownBy(() -> new RuntimeArchiveExtractor().extract(hardlinkArchive, staging.resolve("hard")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regular files and directories");
    }

    @Test
    void tarGzipExecutablePermissionIsRepairedForRuntimeBinFilesWhereSupported() throws IOException {
        final Path archive = tarGzipArchive(entry("bin/pg_ctl", "pg_ctl"), entry("bin/postgres", "postgres"));
        final Path staging = temporaryDirectory.resolve("staging");

        new RuntimeArchiveExtractor().extract(archive, staging);

        assertExecutableIfSupported(staging.resolve("bin").resolve("pg_ctl"));
        assertExecutableIfSupported(staging.resolve("bin").resolve("postgres"));
    }

    @Test
    void unsupportedArchiveBytesAreRejected() throws IOException {
        final Path archive = Files.createTempFile(temporaryDirectory, "runtime-", ".bin");
        final Path staging = temporaryDirectory.resolve("staging");
        Files.writeString(archive, "not an archive");

        assertThatThrownBy(() -> new RuntimeArchiveExtractor().extract(archive, staging))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported runtime archive");
    }

    private Path tarGzipArchive(final EntrySpec... entries) throws IOException {
        return tarGzipWithEntries(Files.createTempFile(temporaryDirectory, "runtime-", ".tar.gz"), entries);
    }

    private Path tgzArchive(final EntrySpec... entries) throws IOException {
        return tarGzipWithEntries(Files.createTempFile(temporaryDirectory, "runtime-", ".tgz"), entries);
    }
}
