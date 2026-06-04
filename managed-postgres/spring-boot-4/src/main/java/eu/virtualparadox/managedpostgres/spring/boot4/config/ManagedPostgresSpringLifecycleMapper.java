package eu.virtualparadox.managedpostgres.spring.boot4.config;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder;

final class ManagedPostgresSpringLifecycleMapper {

    private ManagedPostgresSpringLifecycleMapper() {}

    static ManagedPostgresBuilder configure(
            final ManagedPostgresBuilder builder, final ManagedPostgresSpringProperties.LifecycleProperties lifecycle) {
        ManagedPostgresBuilder configuredBuilder = builder;
        if (lifecycle.reuseExisting()) {
            configuredBuilder = configuredBuilder.attachPolicy(AttachPolicy.ATTACH_IF_COMPATIBLE);
        }
        if (lifecycle.keepRunning()) {
            configuredBuilder = configuredBuilder.stopPolicy(StopPolicy.KEEP_RUNNING);
        }

        return configuredBuilder;
    }
}
