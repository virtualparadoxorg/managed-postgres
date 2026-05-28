package eu.virtualparadox.managedpostgres.internal;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.lifecycle.ManagedPostgresService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public final class ConfiguredManagedPostgresTest {

    ConfiguredManagedPostgresTest() {
    }

    @Test
    void stopDelegatesToLifecycleService() {
        final ManagedPostgresConfiguration configuration = configuration();
        final ManagedPostgresService service = mock(ManagedPostgresService.class);

        try (ConfiguredManagedPostgres postgres = new ConfiguredManagedPostgres(configuration, service)) {
            postgres.stop();
            verify(service).stop(configuration);
            clearInvocations(service);
        }

        verify(service).stop(configuration);
    }

    @Test
    void closeDelegatesToStop() {
        final ManagedPostgresConfiguration configuration = configuration();
        final ManagedPostgresService service = mock(ManagedPostgresService.class);

        try (ConfiguredManagedPostgres postgres = new ConfiguredManagedPostgres(configuration, service)) {
            if (postgres.toString().isBlank()) {
                throw new AssertionError("postgres representation must not be blank");
            }
        }
        verify(service).stop(configuration);
    }

    @Test
    void cleanupDelegatesToLifecycleService() {
        final ManagedPostgresConfiguration configuration = configuration();
        final ManagedPostgresService service = mock(ManagedPostgresService.class);

        try (ConfiguredManagedPostgres postgres = new ConfiguredManagedPostgres(configuration, service)) {
            postgres.cleanup();
        }

        verify(service).cleanup(configuration);
    }

    @Test
    void destroyClusterDelegatesToLifecycleService() {
        final ManagedPostgresConfiguration configuration = configuration();
        final ManagedPostgresService service = mock(ManagedPostgresService.class);

        try (ConfiguredManagedPostgres postgres = new ConfiguredManagedPostgres(configuration, service)) {
            postgres.destroyCluster();
        }

        verify(service).destroyCluster(configuration);
    }

    private static ManagedPostgresConfiguration configuration() {
        return new ManagedPostgresConfiguration(
                "app-db",
                "16.4",
                Storage.projectLocal(Path.of(".local/postgres")),
                RuntimeSource.system(),
                Credentials.generated(),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }
}
