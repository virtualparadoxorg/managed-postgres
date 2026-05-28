package eu.virtualparadox.managedpostgres.lifecycle.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.exception.PostgresUpgradeException;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.StartConfigurationFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.StartWorkflowFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class StartPostgresWorkflowPreflightTest {

    @TempDir
    private Path temporaryDirectory;

    StartPostgresWorkflowPreflightTest() {
    }

    @Test
    void existingDataDirectoryMajorMismatchFailsBeforeClusterFilesAreWritten() throws IOException {
        final FakePostgresRuntime runtime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = runtime.runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final Path dataDirectory = storageRoot.resolve("data");
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("PG_VERSION"), "17%n".formatted(), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new StartWorkflowFactory().workflow()
                .start(StartConfigurationFixture.configuration(storageRoot, runtimeDirectory)))
                .isInstanceOf(PostgresUpgradeException.class)
                .satisfies(throwable -> assertThat(((PostgresUpgradeException) throwable)
                        .diagnosticReport()
                        .renderText()).contains("PG_VERSION"));

        assertThat(runtime.calls()).isEmpty();
        assertThat(storageRoot.resolve("state").resolve("credentials.properties")).doesNotExist();
        assertThat(dataDirectory.resolve("postgresql.conf")).doesNotExist();
        assertThat(dataDirectory.resolve("pg_hba.conf")).doesNotExist();
    }
}
