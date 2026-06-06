package eu.virtualparadox.managedpostgres.lifecycle.handle;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresApplicationConnectionTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-27T00:00:00Z");

    @TempDir
    private Path temporaryDirectory;

    PostgresApplicationConnectionTest() {}

    @Test
    void defaultClusterUsesAdminCredentials() {
        final StartPostgresWorkflow.Configuration configuration = configuration(ClusterBootstrap.defaultCluster());

        assertThat(PostgresApplicationConnection.database(configuration)).isEqualTo("postgres");
        assertThat(PostgresApplicationConnection.owner(configuration)).isEqualTo("postgres");
        assertThat(PostgresApplicationConnection.password(configuration).reveal())
                .isEqualTo("admin-password");
    }

    @Test
    void applicationClusterUsesBootstrapIdentityAndMetadataEndpoint() {
        final StartPostgresWorkflow.Configuration configuration = configuration(ClusterBootstrap.defaultCluster()
                .database("app")
                .owner("app_owner")
                .password(Secret.of("app-password")));
        final PostgresInstanceMetadata metadata = metadata("app", "app_owner");

        final PostgresConnectionInfo connectionInfo =
                PostgresApplicationConnection.fromMetadata(metadata, configuration);

        assertThat(connectionInfo.host()).isEqualTo("127.0.0.1");
        assertThat(connectionInfo.port()).isEqualTo(15432);
        assertThat(connectionInfo.database()).isEqualTo("app");
        assertThat(connectionInfo.username()).isEqualTo("app_owner");
        assertThat(connectionInfo.password().reveal()).isEqualTo("app-password");
    }

    private StartPostgresWorkflow.Configuration configuration(final ClusterBootstrap clusterBootstrap) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                new Storage(temporaryDirectory.resolve("storage"), false),
                RuntimeSource.existing(temporaryDirectory.resolve("runtime")),
                Credentials.of("postgres", Secret.of("admin-password")),
                Network.localhostOnly(),
                clusterBootstrap,
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }

    private PostgresInstanceMetadata metadata(final String database, final String owner) {
        return new PostgresInstanceMetadata(
                1,
                "instance-id",
                "cluster-id",
                "app-db",
                temporaryDirectory.resolve("data"),
                "127.0.0.1",
                15432,
                database,
                owner,
                "16.4",
                16,
                "STARTED_BY_THIS_JVM",
                0L,
                "config-hash",
                FIXED_INSTANT,
                FIXED_INSTANT);
    }
}
