package eu.virtualparadox.managedpostgres.lifecycle.start;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.BootstrapStartConfigurationFactory;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.StartWorkflowBootstrapRuntime;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.StartWorkflowFactory;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.StartedPostgresMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class StartPostgresWorkflowBootstrapTest {

    @TempDir
    private Path temporaryDirectory;

    StartPostgresWorkflowBootstrapTest() {}

    @Test
    void customClusterBootstrapCreatesOwnerAndDatabaseBeforeMetadataIsWritten() throws IOException {
        final StartWorkflowBootstrapRuntime runtime = new StartWorkflowBootstrapRuntime(temporaryDirectory);
        final BootstrapStartConfigurationFactory configurationFactory =
                new BootstrapStartConfigurationFactory(temporaryDirectory);
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.defaultCluster()
                .database("app")
                .owner("app_owner")
                .password(Secret.of("app-password"));

        try (var handle = new StartWorkflowFactory()
                .workflow()
                .start(configurationFactory.configuration(runtime.runtimeWithBootstrapPsql(), clusterBootstrap))) {
            assertThat(handle.connectionInfo().database()).isEqualTo("app");
            assertThat(handle.connectionInfo().username()).isEqualTo("app_owner");
            assertThat(handle.connectionInfo().password()).isEqualTo(Secret.of("app-password"));

            final StartedPostgresMetadata metadata = StartedPostgresMetadata.from(handle);
            assertThat(metadata.database()).isEqualTo("app");
            assertThat(metadata.owner()).isEqualTo("app_owner");
        }

        assertThat(runtime.calls())
                .anySatisfy(call -> assertThat(call).contains("pg_roles").contains("'app_owner'"));
        assertThat(runtime.calls())
                .anySatisfy(call -> assertThat(call).contains("pg_database").contains("'app'"));
        assertThat(runtime.calls()).anySatisfy(call -> assertThat(call)
                .contains("CREATE ROLE \"app_owner\" LOGIN PASSWORD")
                .contains("'app-password'"));
        assertThat(runtime.calls())
                .anySatisfy(call -> assertThat(call).contains("CREATE DATABASE \"app\" OWNER \"app_owner\""));
        assertThat(runtime.calls().stream().filter(call -> call.startsWith("psql ")))
                .allSatisfy(call -> assertThat(call).doesNotContain("app-password"));
    }
}
