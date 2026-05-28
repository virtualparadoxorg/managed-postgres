package eu.virtualparadox.managedpostgres.lifecycle.cleanup;

import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.ManagedPostgresConfigurationFixture;
import java.nio.file.Path;

final class CleanupWorkflowTestConfigurations {

    private CleanupWorkflowTestConfigurations() {
    }

    static ManagedPostgresConfiguration persistentConfiguration(final Path storageRoot) {
        return persistentConfiguration(storageRoot, CleanupPolicy.safeDefaults(), RuntimeSource.system());
    }

    static ManagedPostgresConfiguration persistentConfiguration(
            final Path storageRoot,
            final CleanupPolicy cleanupPolicy,
            final RuntimeSource runtimeSource) {
        final ManagedPostgresConfiguration base =
                ManagedPostgresConfigurationFixture.configuration(storageRoot);

        return base
                .withRuntimeSource(runtimeSource)
                .withCleanupPolicy(cleanupPolicy);
    }

    static ManagedPostgresConfiguration temporaryConfiguration(final Path storageRoot) {
        final ManagedPostgresConfiguration base = ManagedPostgresConfigurationFixture.configuration(storageRoot);

        return new ManagedPostgresConfiguration(
                "temp-db",
                "16.4",
                new Storage(storageRoot, true),
                RuntimeSource.system(),
                Credentials.generated(),
                base.network(),
                base.clusterBootstrap(),
                base.attachPolicy(),
                base.stopPolicy(),
                base.upgradePolicy(),
                base.configDriftPolicy(),
                CleanupPolicy.safeDefaults());
    }
}
