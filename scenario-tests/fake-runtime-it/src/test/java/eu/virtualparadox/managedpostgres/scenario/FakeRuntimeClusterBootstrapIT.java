package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.scenario.support.ScenarioManagedPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FakeRuntimeClusterBootstrapIT {

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeClusterBootstrapIT() {}

    @Test
    void localStartCreatesConfiguredApplicationOwnerAndDatabase() throws IOException {
        final Path psqlLog = temporaryDirectory.resolve("psql-calls.log");
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"), ScenarioShell.recordingBootstrapPsql(psqlLog));
        final Path storageRoot = temporaryDirectory.resolve("cluster");

        try (var postgres = ScenarioManagedPostgres.applicationCluster(storageRoot, runtime)
                .cluster(cluster -> cluster.extension("pgcrypto"))
                .start()) {
            assertThat(postgres.connectionInfo().database()).isEqualTo("app");
            assertThat(postgres.connectionInfo().username()).isEqualTo("app_owner");
        }

        final var metadata = ScenarioMetadata.require(storageRoot);
        assertThat(metadata.database()).isEqualTo("app");
        assertThat(metadata.owner()).isEqualTo("app_owner");
        assertThat(Files.readAllLines(psqlLog))
                .anySatisfy(call -> assertThat(call).contains("pg_roles").contains("'app_owner'"))
                .anySatisfy(call -> assertThat(call).contains("pg_database").contains("'app'"))
                .anySatisfy(call -> assertThat(call).contains("CREATE ROLE \"app_owner\" LOGIN PASSWORD"))
                .anySatisfy(call -> assertThat(call).contains("CREATE DATABASE \"app\" OWNER \"app_owner\""))
                .anySatisfy(call -> assertThat(call)
                        .contains("pg_available_extensions")
                        .contains("'pgcrypto'")
                        .contains(" -d app "))
                .anySatisfy(call -> assertThat(call)
                        .contains("pg_extension")
                        .contains("'pgcrypto'")
                        .contains(" -d app "))
                .anySatisfy(call -> assertThat(call)
                        .contains("CREATE EXTENSION IF NOT EXISTS \"pgcrypto\"")
                        .contains(" -d app "));
        assertThat(Files.readAllLines(psqlLog).stream().filter(call -> call.startsWith("psql ")))
                .allSatisfy(call -> assertThat(call).doesNotContain("app-password"));
    }
}
