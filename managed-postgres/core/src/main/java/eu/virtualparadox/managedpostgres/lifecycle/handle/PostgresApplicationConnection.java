package eu.virtualparadox.managedpostgres.lifecycle.handle;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Objects;

/**
 * Resolves the application-facing PostgreSQL identity from startup configuration and metadata.
 */
public final class PostgresApplicationConnection {

    private PostgresApplicationConnection() {}

    /**
     * Returns the from metadata result.
     *
     * @param metadata metadata value
     * @param configuration configuration value
     * @return from metadata result
     */
    public static PostgresConnectionInfo fromMetadata(
            final PostgresInstanceMetadata metadata, final StartPostgresWorkflow.Configuration configuration) {
        final PostgresInstanceMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata");

        return new PostgresConnectionInfo(
                checkedMetadata.host(),
                checkedMetadata.port(),
                checkedMetadata.database(),
                checkedMetadata.owner(),
                password(configuration));
    }

    /**
     * Returns the database result.
     *
     * @param configuration configuration value
     * @return database result
     */
    public static String database(final StartPostgresWorkflow.Configuration configuration) {
        return clusterBootstrap(configuration).database();
    }

    /**
     * Returns the owner result.
     *
     * @param configuration configuration value
     * @return owner result
     */
    public static String owner(final StartPostgresWorkflow.Configuration configuration) {
        final StartPostgresWorkflow.Configuration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final ClusterBootstrap clusterBootstrap = checkedConfiguration.clusterBootstrap();

        return clusterBootstrap
                .owner()
                .orElse(checkedConfiguration.credentials().username());
    }

    /**
     * Returns the password result.
     *
     * @param configuration configuration value
     * @return password result
     */
    public static Secret password(final StartPostgresWorkflow.Configuration configuration) {
        final StartPostgresWorkflow.Configuration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final ClusterBootstrap clusterBootstrap = checkedConfiguration.clusterBootstrap();

        return clusterBootstrap
                .password()
                .orElse(checkedConfiguration.credentials().password());
    }

    private static ClusterBootstrap clusterBootstrap(final StartPostgresWorkflow.Configuration configuration) {
        return Objects.requireNonNull(configuration, "configuration").clusterBootstrap();
    }
}
