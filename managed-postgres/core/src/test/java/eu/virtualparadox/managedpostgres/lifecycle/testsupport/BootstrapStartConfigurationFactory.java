package eu.virtualparadox.managedpostgres.lifecycle.testsupport;

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
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.Objects;

public final class BootstrapStartConfigurationFactory {

    private final Path storageRoot;

    public BootstrapStartConfigurationFactory(final Path temporaryDirectory) {
        this.storageRoot =
                Objects.requireNonNull(temporaryDirectory, "temporaryDirectory").resolve("local-postgres");
    }

    public StartPostgresWorkflow.Configuration configuration(
            final Path runtimeDirectory, final ClusterBootstrap clusterBootstrap) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                new Storage(storageRoot, false),
                RuntimeSource.existing(runtimeDirectory),
                Credentials.of("postgres", Secret.of("test-password")),
                Network.localhostOnly(),
                clusterBootstrap,
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }
}
