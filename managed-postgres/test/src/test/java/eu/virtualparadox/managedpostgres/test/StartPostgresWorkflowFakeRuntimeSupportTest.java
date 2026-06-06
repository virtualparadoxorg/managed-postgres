package eu.virtualparadox.managedpostgres.test;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StartPostgresWorkflowFakeRuntimeSupportTest {

    @TempDir
    private Path temporaryDirectory;

    StartPostgresWorkflowFakeRuntimeSupportTest() {}

    @Test
    void fakeRuntimeProvidesExecutablesNeededByStartWorkflow() throws IOException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(temporaryDirectory.resolve("runtime"));

        assertThat(Files.isExecutable(runtime.executable("initdb"))).isTrue();
        assertThat(Files.isExecutable(runtime.executable("pg_ctl"))).isTrue();
        assertThat(Files.isExecutable(runtime.executable("pg_isready"))).isTrue();
        assertThat(Files.isExecutable(runtime.executable("postgres"))).isTrue();
    }

    @Test
    void publicBuilderStartUsesFakeRuntimeWorkflow() throws IOException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(temporaryDirectory.resolve("runtime"));
        final Path storageRoot = temporaryDirectory.resolve("cluster");

        try (RunningPostgres postgres = ManagedPostgres.create()
                .name("app-db")
                .version("16.4")
                .withExistingRuntime(runtime.runtimeDirectory())
                .storageProjectLocal(storageRoot)
                .credentials("postgres", "test-password")
                .start()) {
            assertThat(postgres.connectionInfo().host()).isEqualTo("127.0.0.1");
        }

        assertThat(storageRoot.resolve("state").resolve("metadata.json")).isRegularFile();
        assertThat(storageRoot.resolve("data").resolve("postgresql.conf")).isRegularFile();
    }
}
