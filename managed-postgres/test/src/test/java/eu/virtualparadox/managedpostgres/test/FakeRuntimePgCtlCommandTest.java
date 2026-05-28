package eu.virtualparadox.managedpostgres.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FakeRuntimePgCtlCommandTest {

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimePgCtlCommandTest() {
    }

    @Test
    void fakeRuntimeCreatesPgCtlCommand() throws IOException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(temporaryDirectory.resolve("runtime"));
        final Path pgCtl = runtime.executable("pg_ctl");

        assertThat(Files.isRegularFile(pgCtl)).isTrue();
        assertThat(Files.isExecutable(pgCtl)).isTrue();
        assertThat(runtime.manifest().postgresqlVersion()).isEqualTo("16.0");
    }

    @Test
    void fakeRuntimeAppliesPgCtlCommandOverride() throws IOException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"),
                FakePostgresScript.named("pg_ctl").withStdout("started"));

        assertThat(Files.readString(runtime.executable("pg_ctl"))).contains("started");
    }

    @Test
    void defaultInitDbScriptCreatesPgVersionMarker() throws IOException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(temporaryDirectory.resolve("runtime"));

        assertThat(Files.readString(runtime.executable("initdb")))
                .contains("mkdir -p \"$data_dir\"")
                .contains("\"$data_dir/PG_VERSION\"");
    }

    @Test
    void fakeRuntimeRejectsUnsupportedCommand() throws IOException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(temporaryDirectory.resolve("runtime"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> runtime.executable("unsupported"))
                .withMessageContaining("unsupported fake PostgreSQL executable");
    }

    @Test
    void fakeRuntimeWritesRuntimeZipArchive() throws IOException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(temporaryDirectory.resolve("runtime"));
        final Path archive = runtime.writeZipArchive(temporaryDirectory.resolve("artifacts").resolve("runtime.zip"));

        assertThat(Files.isRegularFile(archive)).isTrue();
        assertThat(zipEntryNames(archive))
                .contains("bin/", "bin/pg_ctl", "bin/initdb", "bin/postgres");
    }

    @Test
    void fakeRuntimeRejectsUnsupportedOverrideScript() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FakePostgresRuntime.create(
                        temporaryDirectory.resolve("runtime"),
                        FakePostgresScript.named("unsupported")))
                .withMessageContaining("unsupported fake PostgreSQL script");
    }

    private static Set<String> zipEntryNames(final Path archive) throws IOException {
        final Set<String> names = new LinkedHashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                names.add(zipEntry.getName());
                zipInputStream.closeEntry();
                zipEntry = zipInputStream.getNextEntry();
            }
        }

        return names;
    }
}
