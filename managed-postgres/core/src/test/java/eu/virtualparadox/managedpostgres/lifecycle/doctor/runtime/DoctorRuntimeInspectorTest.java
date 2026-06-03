package eu.virtualparadox.managedpostgres.lifecycle.doctor.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class DoctorRuntimeInspectorTest {

    @TempDir
    private Path temporaryDirectory;

    DoctorRuntimeInspectorTest() {}

    @Test
    void existingRuntimeWithRequiredBinariesReportsUsablePath() throws IOException {
        final Path runtimeDirectory = runtimeDirectoryWith("pg_ctl", "psql", "postgres");

        final DiagnosticSection section =
                new DoctorRuntimeInspector().inspect(RuntimeSource.existing(runtimeDirectory));

        assertThat(section.name()).isEqualTo("runtime");
        assertThat(section.values())
                .containsEntry("source", "existing")
                .containsEntry("status", "usable")
                .containsEntry(
                        "path", runtimeDirectory.toAbsolutePath().normalize().toString());
    }

    @Test
    void existingRuntimeMissingPostgresReportsInvalidWithoutThrowing() throws IOException {
        final Path runtimeDirectory = runtimeDirectoryWith("pg_ctl", "psql");

        final DiagnosticSection section =
                new DoctorRuntimeInspector().inspect(RuntimeSource.existing(runtimeDirectory));

        assertThat(section.values())
                .containsEntry("source", "existing")
                .containsEntry("status", "invalid")
                .containsEntry(
                        "path", runtimeDirectory.toAbsolutePath().normalize().toString());
        assertThat(section.values().get("message")).contains("bin/postgres");
    }

    @Test
    void downloadedRuntimeReportsNotInspected() {
        final DiagnosticSection section = new DoctorRuntimeInspector().inspect(RuntimeSource.downloaded());

        assertThat(section.values()).containsEntry("source", "downloaded").containsEntry("status", "not-inspected");
        assertThat(section.values().get("message")).contains("download");
    }

    @Test
    void systemRuntimeReportsNotInspected() {
        final DiagnosticSection section = new DoctorRuntimeInspector().inspect(RuntimeSource.system());

        assertThat(section.values()).containsEntry("source", "system").containsEntry("status", "not-inspected");
        assertThat(section.values().get("message")).contains("PATH");
    }

    private Path runtimeDirectoryWith(final String... binaries) throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("postgres-runtime-" + binaries.length);
        final Path binDirectory = runtimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        for (final String binary : binaries) {
            Files.createFile(binDirectory.resolve(binary));
        }

        return runtimeDirectory;
    }
}
