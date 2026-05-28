package eu.virtualparadox.managedpostgres.lifecycle.start;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.bootstrap.BootstrapExtension;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.List;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.identity.PostgresIdentifier;

/**
 * Captures bootstrap plan details for managed PostgreSQL internals.
 *
 * @param adminConnectionInfo admin connection info value
 * @param applicationConnectionInfo application connection info value
 * @param roleName role name value
 * @param databaseName database name value
 * @param ownerPassword owner password value
 * @param extensions PostgreSQL extension bootstrap requests
 * @param requiresRoleBootstrap requires role bootstrap value
 * @param requiresDatabaseBootstrap requires database bootstrap value
 */
public record BootstrapPlan(
        PostgresConnectionInfo adminConnectionInfo,
        PostgresConnectionInfo applicationConnectionInfo,
        String roleName,
        String databaseName,
        Secret ownerPassword,
        List<BootstrapExtension> extensions,
        boolean requiresRoleBootstrap,
        boolean requiresDatabaseBootstrap) {

    private static final String DEFAULT_DATABASE = "postgres";

    /**
     * Defines the value value.
     */
    public BootstrapPlan {
        Objects.requireNonNull(adminConnectionInfo, "adminConnectionInfo");
        Objects.requireNonNull(applicationConnectionInfo, "applicationConnectionInfo");
        PostgresIdentifier.quote(roleName);
        PostgresIdentifier.quote(databaseName);
        Objects.requireNonNull(ownerPassword, "ownerPassword");
        extensions = List.copyOf(Objects.requireNonNull(extensions, "extensions"));
    }

    /**
     * Returns the from result.
     *
     * @param adminConnectionInfo admin connection info value
     * @param clusterBootstrap cluster bootstrap value
     * @return from result
     */
    public static BootstrapPlan from(
            final PostgresConnectionInfo adminConnectionInfo,
            final ClusterBootstrap clusterBootstrap) {
        final PostgresConnectionInfo checkedAdminConnectionInfo =
                Objects.requireNonNull(adminConnectionInfo, "adminConnectionInfo");
        final ClusterBootstrap checkedClusterBootstrap =
                Objects.requireNonNull(clusterBootstrap, "clusterBootstrap");
        final String roleName = checkedClusterBootstrap.owner().orElse(checkedAdminConnectionInfo.username());
        final Secret ownerPassword = checkedClusterBootstrap.password().orElse(checkedAdminConnectionInfo.password());
        final String databaseName = checkedClusterBootstrap.database();
        final PostgresConnectionInfo applicationConnectionInfo = new PostgresConnectionInfo(
                checkedAdminConnectionInfo.host(),
                checkedAdminConnectionInfo.port(),
                databaseName,
                roleName,
                ownerPassword);

        return new BootstrapPlan(
                checkedAdminConnectionInfo,
                applicationConnectionInfo,
                roleName,
                databaseName,
                ownerPassword,
                checkedClusterBootstrap.extensions(),
                checkedClusterBootstrap.owner().isPresent(),
                !DEFAULT_DATABASE.equals(databaseName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return ("BootstrapPlan[databaseName=%s, roleName=%s, ownerPassword=REDACTED, "
                + "extensions=%s, requiresRoleBootstrap=%s, requiresDatabaseBootstrap=%s]")
                .formatted(databaseName, roleName, extensions, requiresRoleBootstrap, requiresDatabaseBootstrap);
    }
}
