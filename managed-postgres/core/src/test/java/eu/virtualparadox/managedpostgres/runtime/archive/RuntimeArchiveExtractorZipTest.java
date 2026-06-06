package eu.virtualparadox.managedpostgres.runtime.archive;

import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.assertExecutableIfSupported;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.assertNotExecutableIfSupported;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.assertRuntimeFiles;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.directory;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.entry;
import static eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.zipWithEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.EntrySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeArchiveExtractorZipTest {

    @TempDir
    private Path temporaryDirectory;

    RuntimeArchiveExtractorZipTest() {}

    @Test
    void validZipExtractsRuntimeFilesUnderStaging() throws IOException {
        final Path archive =
                zipArchive(entry("PG_VERSION", "16"), entry("bin/pg_ctl", "pg_ctl"), entry("bin/postgres", "postgres"));
        final Path staging = temporaryDirectory.resolve("staging");

        assertThat(new RuntimeArchiveExtractor().extract(archive, staging)).isEqualTo(staging);

        assertRuntimeFiles(staging);
    }

    @Test
    void reExtractingOverAnExistingTreeOverwritesIdempotently() throws IOException {
        final Path archive =
                zipArchive(entry("PG_VERSION", "16"), entry("bin/pg_ctl", "pg_ctl"), entry("bin/postgres", "postgres"));
        final Path staging = temporaryDirectory.resolve("staging");

        new RuntimeArchiveExtractor().extract(archive, staging);
        // A second extraction into the same directory must succeed (not fail on existing files).
        new RuntimeArchiveExtractor().extract(archive, staging);

        assertRuntimeFiles(staging);
        assertThat(Files.readString(staging.resolve("PG_VERSION"))).isEqualTo("16");
    }

    @Test
    void zipParentDirectoriesAreCreatedBeforeFiles() throws IOException {
        final Path archive = zipArchive(entry("runtime/nested/PG_VERSION", "16"));
        final Path staging = temporaryDirectory.resolve("staging");

        new RuntimeArchiveExtractor().extract(archive, staging);

        assertThat(staging.resolve("runtime").resolve("nested")).isDirectory();
        assertThat(Files.readString(staging.resolve("runtime").resolve("nested").resolve("PG_VERSION")))
                .isEqualTo("16");
    }

    @Test
    void zipDirectoryEntriesAreCreated() throws IOException {
        final Path archive = zipArchive(directory("runtime/"), entry("runtime/PG_VERSION", "16"));
        final Path staging = temporaryDirectory.resolve("staging");

        new RuntimeArchiveExtractor().extract(archive, staging);

        assertThat(staging.resolve("runtime")).isDirectory();
        assertThat(Files.readString(staging.resolve("runtime").resolve("PG_VERSION")))
                .isEqualTo("16");
    }

    @Test
    void zipTraversalEntriesAreRejected() throws IOException {
        final Path archive = zipArchive(entry("../evil", "evil"));
        final Path staging = temporaryDirectory.resolve("staging");

        assertThatThrownBy(() -> new RuntimeArchiveExtractor().extract(archive, staging))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
        assertThat(temporaryDirectory.resolve("evil")).doesNotExist();
    }

    @Test
    void zipUnsafeNamesAreRejected() throws IOException {
        final Path archive = zipArchive(entry("bin\\postgres", "evil"));
        final Path staging = temporaryDirectory.resolve("staging");

        assertThatThrownBy(() -> new RuntimeArchiveExtractor().extract(archive, staging))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe");
    }

    @Test
    void zipExecutablePermissionIsRepairedForRuntimeBinFilesWhereSupported() throws IOException {
        final Path archive = zipArchive(
                entry("bin/pg_ctl", "pg_ctl"),
                entry("bin/postgres", "postgres"),
                entry("bin/initdb", "initdb"),
                entry("bin/pg_isready", "pg_isready"));
        final Path staging = temporaryDirectory.resolve("staging");

        new RuntimeArchiveExtractor().extract(archive, staging);

        assertExecutableIfSupported(staging.resolve("bin").resolve("pg_ctl"));
        assertExecutableIfSupported(staging.resolve("bin").resolve("postgres"));
        assertExecutableIfSupported(staging.resolve("bin").resolve("initdb"));
        assertExecutableIfSupported(staging.resolve("bin").resolve("pg_isready"));
    }

    @Test
    void zipNamedFilesOutsideBinAreNotMadeExecutable() throws IOException {
        final Path archive = zipArchive(entry("lib/postgres", "postgres"));
        final Path staging = temporaryDirectory.resolve("staging");

        new RuntimeArchiveExtractor().extract(archive, staging);

        assertNotExecutableIfSupported(staging.resolve("lib").resolve("postgres"));
    }

    private Path zipArchive(final EntrySpec... entries) throws IOException {
        return zipWithEntries(Files.createTempFile(temporaryDirectory, "runtime-", ".zip"), entries);
    }
}
