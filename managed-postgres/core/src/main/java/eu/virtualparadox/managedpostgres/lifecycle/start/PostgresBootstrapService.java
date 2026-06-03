package eu.virtualparadox.managedpostgres.lifecycle.start;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.bootstrap.BootstrapExtension;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.lifecycle.psql.PsqlBootstrapClient;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinates postgres bootstrap service behavior for managed PostgreSQL internals.
 */
public final class PostgresBootstrapService {

    private final PsqlBootstrapClient bootstrapClient;
    private final Path runtimeDirectory;
    private final String postgresqlVersion;

    /**
     * Creates a PostgresBootstrapService instance.
     *
     * @param bootstrapClient bootstrap client value
     */
    public PostgresBootstrapService(final PsqlBootstrapClient bootstrapClient) {
        this(bootstrapClient, Path.of("unknown"), "unknown");
    }

    /**
     * Creates a PostgresBootstrapService instance.
     *
     * @param bootstrapClient bootstrap client value
     * @param runtimeDirectory runtime directory value
     * @param postgresqlVersion PostgreSQL version value
     */
    public PostgresBootstrapService(
            final PsqlBootstrapClient bootstrapClient, final Path runtimeDirectory, final String postgresqlVersion) {
        this.bootstrapClient = Objects.requireNonNull(bootstrapClient, "bootstrapClient");
        this.runtimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory")
                .toAbsolutePath()
                .normalize();
        this.postgresqlVersion = Objects.requireNonNull(postgresqlVersion, "postgresqlVersion");
    }

    /**
     * Returns the bootstrap result.
     *
     * @param adminConnectionInfo admin connection info value
     * @param clusterBootstrap cluster bootstrap value
     * @return bootstrap result
     */
    public PostgresConnectionInfo bootstrap(
            final PostgresConnectionInfo adminConnectionInfo, final ClusterBootstrap clusterBootstrap) {
        final BootstrapPlan plan = BootstrapPlan.from(adminConnectionInfo, clusterBootstrap);
        createRoleIfRequired(plan);
        createDatabaseIfRequired(plan);
        createExtensionsIfRequired(plan);

        return plan.applicationConnectionInfo();
    }

    private void createRoleIfRequired(final BootstrapPlan plan) {
        if (!plan.requiresRoleBootstrap()) {
            return;
        }
        if (bootstrapClient.roleExists(plan.adminConnectionInfo(), plan.roleName())) {
            verifyExistingRole(plan);
        } else {
            bootstrapClient.createRole(plan.adminConnectionInfo(), plan.roleName(), plan.ownerPassword());
        }
    }

    private void verifyExistingRole(final BootstrapPlan plan) {
        if (!bootstrapClient.roleCanLogin(plan.adminConnectionInfo(), plan.roleName())) {
            throw new PostgresStartupException(
                    "PostgreSQL bootstrap role is not login-capable",
                    bootstrapDiagnostic(Map.of(
                            "catalog", "pg_roles",
                            "role", plan.roleName(),
                            "expected", "rolcanlogin=true",
                            "actual", "rolcanlogin=false")));
        }
    }

    private void createDatabaseIfRequired(final BootstrapPlan plan) {
        if (!plan.requiresDatabaseBootstrap()) {
            return;
        }
        if (bootstrapClient.databaseExists(plan.adminConnectionInfo(), plan.databaseName())) {
            verifyExistingDatabase(plan);
        } else {
            bootstrapClient.createDatabase(plan.adminConnectionInfo(), plan.databaseName(), plan.roleName());
        }
    }

    private void verifyExistingDatabase(final BootstrapPlan plan) {
        final Optional<String> databaseOwner =
                bootstrapClient.databaseOwner(plan.adminConnectionInfo(), plan.databaseName());
        if (databaseOwner.filter(plan.roleName()::equals).isEmpty()) {
            throw new PostgresStartupException(
                    "PostgreSQL bootstrap database owner is incompatible",
                    bootstrapDiagnostic(Map.of(
                            "catalog", "pg_database",
                            "database", plan.databaseName(),
                            "expectedOwner", plan.roleName(),
                            "actualOwner", databaseOwner.orElse("<missing>"))));
        }
    }

    private void createExtensionsIfRequired(final BootstrapPlan plan) {
        for (final BootstrapExtension extension : plan.extensions()) {
            createExtensionIfRequired(plan.applicationConnectionInfo(), extension);
        }
    }

    private void createExtensionIfRequired(
            final PostgresConnectionInfo applicationConnectionInfo, final BootstrapExtension extension) {
        if (!bootstrapClient.extensionAvailable(applicationConnectionInfo, extension.name())) {
            handleUnavailableExtension(extension, applicationConnectionInfo);
            return;
        }
        if (!bootstrapClient.extensionInstalled(applicationConnectionInfo, extension.name())) {
            bootstrapClient.createExtension(applicationConnectionInfo, extension.name());
        }
    }

    private void handleUnavailableExtension(
            final BootstrapExtension extension, final PostgresConnectionInfo applicationConnectionInfo) {
        if (extension.policy() == BootstrapExtension.Policy.FAIL_IF_UNAVAILABLE) {
            throw new PostgresStartupException(
                    "PostgreSQL extension is unavailable", extensionDiagnostic(extension, applicationConnectionInfo));
        }
    }

    private DiagnosticReport extensionDiagnostic(
            final BootstrapExtension extension, final PostgresConnectionInfo applicationConnectionInfo) {
        return new DiagnosticReport(List.of(new DiagnosticSection(
                "postgres-extension",
                Map.of(
                        "extension", extension.name(),
                        "policy", extension.policy().name(),
                        "database", applicationConnectionInfo.database(),
                        "postgresqlVersion", postgresqlVersion,
                        "runtimeDirectory", runtimeDirectory.toString(),
                        "suggestedAction", "Install a runtime that provides the extension or mark it optional."))));
    }

    private static DiagnosticReport bootstrapDiagnostic(final Map<String, String> details) {
        return new DiagnosticReport(List.of(new DiagnosticSection("postgres-bootstrap", new LinkedHashMap<>(details))));
    }
}
