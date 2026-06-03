package eu.virtualparadox.managedpostgres.spring.boot4.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresSpringLifecycleMapperTest {

    ManagedPostgresSpringLifecycleMapperTest() {}

    @Test
    void reuseExistingMapsToAttachIfCompatible() {
        final ManagedPostgresBuilder builder = builder();
        final ManagedPostgresSpringProperties.LifecycleProperties lifecycle =
                new ManagedPostgresSpringProperties.LifecycleProperties(true, false);

        ManagedPostgresSpringLifecycleMapper.configure(builder, lifecycle);

        verify(builder).attachPolicy(AttachPolicy.ATTACH_IF_COMPATIBLE);
    }

    @Test
    void keepRunningMapsToStopPolicy() {
        final ManagedPostgresBuilder builder = builder();
        final ManagedPostgresSpringProperties.LifecycleProperties lifecycle =
                new ManagedPostgresSpringProperties.LifecycleProperties(false, true);

        ManagedPostgresSpringLifecycleMapper.configure(builder, lifecycle);

        verify(builder).stopPolicy(StopPolicy.KEEP_RUNNING);
    }

    private static ManagedPostgresBuilder builder() {
        final ManagedPostgresBuilder builder = mock(ManagedPostgresBuilder.class);
        when(builder.attachPolicy(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
        when(builder.stopPolicy(org.mockito.ArgumentMatchers.any())).thenReturn(builder);

        return builder;
    }
}
