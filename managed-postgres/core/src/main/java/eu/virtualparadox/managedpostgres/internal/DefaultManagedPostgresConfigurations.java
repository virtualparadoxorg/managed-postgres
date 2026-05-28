package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresMode;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.logging.PostgresLogs;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;

/**
 * Coordinates default managed postgres configurations behavior for managed PostgreSQL internals.
 */
public final class DefaultManagedPostgresConfigurations {

    private static final String DEFAULT_NAME = "default";
    private static final String DEFAULT_VERSION = "16.4";

    private DefaultManagedPostgresConfigurations() {
    }

    /**
     * Returns the for mode result.
     *
     * @param mode mode value
     * @return for mode result
     */
    public static ManagedPostgresConfiguration forMode(final ManagedPostgresMode mode) {
        return new ManagedPostgresConfiguration(
                DEFAULT_NAME,
                DEFAULT_VERSION,
                defaultStorage(mode),
                RuntimeSource.system(),
                defaultCredentials(mode),
                defaultNetwork(mode),
                ClusterBootstrap.defaultCluster(),
                defaultPostgresConfiguration(mode),
                PostgresLogs.defaults(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }

    private static Storage defaultStorage(final ManagedPostgresMode mode) {
        final Storage defaultStorage;
        if (mode == ManagedPostgresMode.TEMPORARY) {
            defaultStorage = Storage.temporary();
        } else {
            defaultStorage = Storage.projectLocal(".local/postgres");
        }

        return defaultStorage;
    }

    private static Credentials defaultCredentials(final ManagedPostgresMode mode) {
        final Credentials defaultCredentials;
        if (mode == ManagedPostgresMode.TEMPORARY) {
            defaultCredentials = Credentials.generated();
        } else {
            defaultCredentials = Credentials.generatedPersistent();
        }

        return defaultCredentials;
    }

    private static Network defaultNetwork(final ManagedPostgresMode mode) {
        final Network defaultNetwork;
        if (mode == ManagedPostgresMode.TEMPORARY) {
            defaultNetwork = Network.localhostOnly().randomPort();
        } else {
            defaultNetwork = Network.localhostOnly().stableRandomPort();
        }

        return defaultNetwork;
    }

    private static PostgresConfiguration defaultPostgresConfiguration(final ManagedPostgresMode mode) {
        final PostgresConfiguration defaultPostgresConfiguration;
        if (mode == ManagedPostgresMode.TEMPORARY) {
            defaultPostgresConfiguration = Resources.tiny();
        } else {
            defaultPostgresConfiguration = Resources.small();
        }

        return defaultPostgresConfiguration;
    }
}
