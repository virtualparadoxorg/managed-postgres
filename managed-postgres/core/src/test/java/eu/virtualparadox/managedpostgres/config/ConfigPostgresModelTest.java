package eu.virtualparadox.managedpostgres.config;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresMode;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import eu.virtualparadox.managedpostgres.internal.DefaultManagedPostgresConfigurations;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public final class ConfigPostgresModelTest {

    ConfigPostgresModelTest() {
    }

    @Test
    void managedPostgresConfigurationStoresImmutablePostgresSettings() {
        final ManagedPostgresConfiguration configuration = configuration();
        final PostgresConfiguration postgresConfiguration = Resources.ci().withStatementTimeoutSeconds(15);

        assertThat(configuration.withPostgresConfiguration(postgresConfiguration).postgresConfiguration())
                .isEqualTo(postgresConfiguration);
    }

    @Test
    void persistentLocalDefaultsUseSmallPostgresResourcePreset() {
        final ManagedPostgresConfiguration configuration =
                DefaultManagedPostgresConfigurations.forMode(ManagedPostgresMode.PERSISTENT_LOCAL);

        assertThat(configuration.postgresConfiguration()).isEqualTo(Resources.small());
    }

    @Test
    void defaultsUseLatestPublishedPostgresVersion() {
        final ManagedPostgresConfiguration configuration =
                DefaultManagedPostgresConfigurations.forMode(ManagedPostgresMode.PERSISTENT_LOCAL);

        assertThat(configuration.postgresqlVersion()).isEqualTo("18.4");
    }

    @Test
    void defaultsUseOfficialDownloadedRuntimeForZeroTouchStartup() {
        final ManagedPostgresConfiguration configuration =
                DefaultManagedPostgresConfigurations.forMode(ManagedPostgresMode.PERSISTENT_LOCAL);
        final RuntimeSource runtimeSource = configuration.runtimeSource();

        assertThat(runtimeSource.kind()).isEqualTo("downloaded");
        assertThat(runtimeSource.downloadedRuntime()).isPresent();
        assertThat(runtimeSource.downloadedRuntime().orElseThrow().repository())
                .contains(RuntimeRepository.official());
    }

    private static ManagedPostgresConfiguration configuration() {
        return new ManagedPostgresConfiguration(
                "app-db",
                "16.4",
                Storage.projectLocal(Path.of("storage")),
                RuntimeSource.existing(Path.of("runtime")),
                Credentials.of("postgres", Secret.of("secret")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                Resources.small(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }
}
