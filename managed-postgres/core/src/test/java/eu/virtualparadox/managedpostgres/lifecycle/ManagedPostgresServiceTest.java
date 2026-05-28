package eu.virtualparadox.managedpostgres.lifecycle;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.ManagedPostgresConfigurationFixture;
import eu.virtualparadox.managedpostgres.lifecycle.cleanup.CleanupManagedPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.cleanup.DestroyManagedPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorService;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.stop.StopPostgresWorkflow;

/**
 * Tests the public lifecycle service facade.
 */
public final class ManagedPostgresServiceTest {

    ManagedPostgresServiceTest() {
    }

    @Test
    void stopDelegatesToConfiguredStopWorkflow() {
        final StartPostgresWorkflow startWorkflow = mock(StartPostgresWorkflow.class);
        final StopPostgresWorkflow stopWorkflow = mock(StopPostgresWorkflow.class);
        final ManagedPostgresService service = new ManagedPostgresService(startWorkflow, stopWorkflow);
        final ManagedPostgresConfiguration configuration =
                ManagedPostgresConfigurationFixture.configuration(Path.of(".local/postgres"));

        service.stop(configuration);

        verify(stopWorkflow).stop(configuration);
    }

    @Test
    void cleanupDelegatesToCleanupWorkflow() {
        final StartPostgresWorkflow startWorkflow = mock(StartPostgresWorkflow.class);
        final StopPostgresWorkflow stopWorkflow = mock(StopPostgresWorkflow.class);
        final CleanupManagedPostgresWorkflow cleanupWorkflow = mock(CleanupManagedPostgresWorkflow.class);
        final DestroyManagedPostgresWorkflow destroyWorkflow = mock(DestroyManagedPostgresWorkflow.class);
        final ManagedPostgresService service = new ManagedPostgresService(
                startWorkflow,
                stopWorkflow,
                mock(DoctorService.class),
                cleanupWorkflow,
                destroyWorkflow);
        final ManagedPostgresConfiguration configuration =
                ManagedPostgresConfigurationFixture.configuration(Path.of(".local/postgres"));

        service.cleanup(configuration);

        verify(cleanupWorkflow).cleanup(configuration);
    }

    @Test
    void destroyClusterDelegatesToDestroyWorkflow() {
        final StartPostgresWorkflow startWorkflow = mock(StartPostgresWorkflow.class);
        final StopPostgresWorkflow stopWorkflow = mock(StopPostgresWorkflow.class);
        final CleanupManagedPostgresWorkflow cleanupWorkflow = mock(CleanupManagedPostgresWorkflow.class);
        final DestroyManagedPostgresWorkflow destroyWorkflow = mock(DestroyManagedPostgresWorkflow.class);
        final ManagedPostgresService service = new ManagedPostgresService(
                startWorkflow,
                stopWorkflow,
                mock(DoctorService.class),
                cleanupWorkflow,
                destroyWorkflow);
        final ManagedPostgresConfiguration configuration =
                ManagedPostgresConfigurationFixture.configuration(Path.of(".local/postgres"));

        service.destroyCluster(configuration);

        verify(destroyWorkflow).destroyCluster(configuration);
    }
}
