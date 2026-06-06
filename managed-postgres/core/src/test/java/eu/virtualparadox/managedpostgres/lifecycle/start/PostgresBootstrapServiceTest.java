package eu.virtualparadox.managedpostgres.lifecycle.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.lifecycle.psql.PsqlBootstrapClient;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class PostgresBootstrapServiceTest {

    PostgresBootstrapServiceTest() {}

    @Test
    void defaultClusterReturnsAdminConnectionWithoutPsqlCalls() {
        final RecordingBootstrapClient client = new RecordingBootstrapClient(false, false);
        final PostgresConnectionInfo connectionInfo =
                bootstrapService(client).bootstrap(adminConnectionInfo(), ClusterBootstrap.defaultCluster());

        assertThat(connectionInfo).isEqualTo(adminConnectionInfo());
        assertThat(client.calls()).isEmpty();
    }

    @Test
    void missingOwnerAndDatabaseAreCreatedBeforeApplicationConnectionIsReturned() {
        final RecordingBootstrapClient client = new RecordingBootstrapClient(false, false);
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.defaultCluster()
                .database("app")
                .owner("app_owner")
                .password(Secret.of("app-password"));

        final PostgresConnectionInfo connectionInfo =
                bootstrapService(client).bootstrap(adminConnectionInfo(), clusterBootstrap);

        assertThat(client.calls())
                .containsExactly(
                        "role-exists:app_owner",
                        "create-role:app_owner",
                        "database-exists:app",
                        "create-database:app:app_owner");
        assertThat(connectionInfo.database()).isEqualTo("app");
        assertThat(connectionInfo.username()).isEqualTo("app_owner");
        assertThat(connectionInfo.password()).isEqualTo(Secret.of("app-password"));
    }

    @Test
    void existingOwnerAndDatabaseAreReused() {
        final RecordingBootstrapClient client =
                new RecordingBootstrapClient(true, true).withRoleCanLogin(true).withDatabaseOwner("app_owner");
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.defaultCluster()
                .database("app")
                .owner("app_owner")
                .password(Secret.of("app-password"));

        bootstrapService(client).bootstrap(adminConnectionInfo(), clusterBootstrap);

        assertThat(client.calls())
                .containsExactly(
                        "role-exists:app_owner",
                        "role-can-login:app_owner",
                        "database-exists:app",
                        "database-owner:app");
    }

    @Test
    void existingOwnerWithoutLoginFailsBeforeDatabaseAndExtensions() {
        final RecordingBootstrapClient client = new RecordingBootstrapClient(true, true)
                .withRoleCanLogin(false)
                .withDatabaseOwner("app_owner")
                .withExtensionAvailable(true);
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.defaultCluster()
                .database("app")
                .owner("app_owner")
                .password(Secret.of("app-password"))
                .extension("pgcrypto");

        assertThatThrownBy(() -> bootstrapService(client).bootstrap(adminConnectionInfo(), clusterBootstrap))
                .isInstanceOf(PostgresStartupException.class)
                .hasMessageContaining("role")
                .satisfies(throwable -> assertThat(((PostgresStartupException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("postgres-bootstrap")
                        .contains("app_owner")
                        .contains("rolcanlogin")
                        .doesNotContain("admin-password")
                        .doesNotContain("app-password"));

        assertThat(client.calls()).containsExactly("role-exists:app_owner", "role-can-login:app_owner");
    }

    @Test
    void existingDatabaseWithDifferentOwnerFailsBeforeExtensions() {
        final RecordingBootstrapClient client = new RecordingBootstrapClient(true, true)
                .withRoleCanLogin(true)
                .withDatabaseOwner("other_owner")
                .withExtensionAvailable(true);
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.defaultCluster()
                .database("app")
                .owner("app_owner")
                .password(Secret.of("app-password"))
                .extension("pgcrypto");

        assertThatThrownBy(() -> bootstrapService(client).bootstrap(adminConnectionInfo(), clusterBootstrap))
                .isInstanceOf(PostgresStartupException.class)
                .hasMessageContaining("database")
                .satisfies(throwable -> assertThat(((PostgresStartupException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("postgres-bootstrap")
                        .contains("app")
                        .contains("expectedOwner")
                        .contains("app_owner")
                        .contains("actualOwner")
                        .contains("other_owner")
                        .doesNotContain("admin-password")
                        .doesNotContain("app-password"));

        assertThat(client.calls())
                .containsExactly(
                        "role-exists:app_owner",
                        "role-can-login:app_owner",
                        "database-exists:app",
                        "database-owner:app");
    }

    @Test
    void customDatabaseWithoutOwnerUsesAdminRole() {
        final RecordingBootstrapClient client = new RecordingBootstrapClient(false, false);
        final ClusterBootstrap clusterBootstrap =
                ClusterBootstrap.defaultCluster().database("app");

        final PostgresConnectionInfo connectionInfo =
                bootstrapService(client).bootstrap(adminConnectionInfo(), clusterBootstrap);

        assertThat(client.calls()).containsExactly("database-exists:app", "create-database:app:postgres");
        assertThat(connectionInfo.database()).isEqualTo("app");
        assertThat(connectionInfo.username()).isEqualTo("postgres");
    }

    @Test
    void requiredAvailableExtensionIsCreatedAfterApplicationDatabase() {
        final RecordingBootstrapClient client = new RecordingBootstrapClient(false, false)
                .withExtensionAvailable(true)
                .withExtensionInstalled(false);
        final ClusterBootstrap clusterBootstrap =
                ClusterBootstrap.defaultCluster().database("app").extension("pgcrypto");

        bootstrapService(client).bootstrap(adminConnectionInfo(), clusterBootstrap);

        assertThat(client.calls())
                .containsExactly(
                        "database-exists:app",
                        "create-database:app:postgres",
                        "extension-available:app:pgcrypto",
                        "extension-installed:app:pgcrypto",
                        "create-extension:app:pgcrypto");
    }

    @Test
    void installedExtensionIsNotCreatedAgain() {
        final RecordingBootstrapClient client = new RecordingBootstrapClient(true, true)
                .withExtensionAvailable(true)
                .withExtensionInstalled(true);
        final ClusterBootstrap clusterBootstrap =
                ClusterBootstrap.defaultCluster().database("app").extension("pgcrypto");

        bootstrapService(client).bootstrap(adminConnectionInfo(), clusterBootstrap);

        assertThat(client.calls())
                .containsExactly(
                        "database-exists:app",
                        "database-owner:app",
                        "extension-available:app:pgcrypto",
                        "extension-installed:app:pgcrypto");
    }

    @Test
    void optionalUnavailableExtensionIsSkipped() {
        final RecordingBootstrapClient client = new RecordingBootstrapClient(true, true).withExtensionAvailable(false);
        final ClusterBootstrap clusterBootstrap =
                ClusterBootstrap.defaultCluster().database("app").optionalExtension("postgis");

        bootstrapService(client).bootstrap(adminConnectionInfo(), clusterBootstrap);

        assertThat(client.calls())
                .containsExactly("database-exists:app", "database-owner:app", "extension-available:app:postgis");
    }

    @Test
    void requiredUnavailableExtensionFailsWithDiagnostic() {
        final RecordingBootstrapClient client = new RecordingBootstrapClient(true, true).withExtensionAvailable(false);
        final ClusterBootstrap clusterBootstrap =
                ClusterBootstrap.defaultCluster().database("app").extension("postgis");

        assertThatThrownBy(() -> bootstrapService(client).bootstrap(adminConnectionInfo(), clusterBootstrap))
                .isInstanceOf(PostgresStartupException.class)
                .hasMessageContaining("extension")
                .satisfies(throwable -> assertThat(((PostgresStartupException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("postgis")
                        .contains("FAIL_IF_UNAVAILABLE")
                        .contains("16.4")
                        .contains("runtime")
                        .doesNotContain("admin-password"));
    }

    private static PostgresConnectionInfo adminConnectionInfo() {
        return new PostgresConnectionInfo("127.0.0.1", 55432, "postgres", "postgres", Secret.of("admin-password"));
    }

    private static PostgresBootstrapService bootstrapService(final PsqlBootstrapClient client) {
        return new PostgresBootstrapService(client, java.nio.file.Path.of("runtime"), "16.4");
    }

    private static final class RecordingBootstrapClient implements PsqlBootstrapClient {

        private final boolean roleExists;
        private final boolean databaseExists;
        private final List<String> calls = new ArrayList<>();
        private boolean roleCanLogin = true;
        private String databaseOwner = "postgres";
        private boolean extensionAvailable;
        private boolean extensionInstalled;

        RecordingBootstrapClient(final boolean roleExists, final boolean databaseExists) {
            this.roleExists = roleExists;
            this.databaseExists = databaseExists;
        }

        RecordingBootstrapClient withExtensionAvailable(final boolean newExtensionAvailable) {
            extensionAvailable = newExtensionAvailable;

            return this;
        }

        RecordingBootstrapClient withExtensionInstalled(final boolean newExtensionInstalled) {
            extensionInstalled = newExtensionInstalled;

            return this;
        }

        RecordingBootstrapClient withRoleCanLogin(final boolean newRoleCanLogin) {
            roleCanLogin = newRoleCanLogin;

            return this;
        }

        RecordingBootstrapClient withDatabaseOwner(final String newDatabaseOwner) {
            databaseOwner = newDatabaseOwner;

            return this;
        }

        @Override
        public boolean roleExists(final PostgresConnectionInfo adminConnectionInfo, final String roleName) {
            calls.add("role-exists:" + roleName);

            return roleExists;
        }

        @Override
        public boolean databaseExists(final PostgresConnectionInfo adminConnectionInfo, final String databaseName) {
            calls.add("database-exists:" + databaseName);

            return databaseExists;
        }

        @Override
        public boolean roleCanLogin(final PostgresConnectionInfo adminConnectionInfo, final String roleName) {
            calls.add("role-can-login:" + roleName);

            return roleCanLogin;
        }

        @Override
        public Optional<String> databaseOwner(
                final PostgresConnectionInfo adminConnectionInfo, final String databaseName) {
            calls.add("database-owner:" + databaseName);

            return Optional.of(databaseOwner);
        }

        @Override
        public void createRole(
                final PostgresConnectionInfo adminConnectionInfo, final String roleName, final Secret password) {
            calls.add("create-role:" + roleName);
        }

        @Override
        public void createDatabase(
                final PostgresConnectionInfo adminConnectionInfo, final String databaseName, final String ownerName) {
            calls.add("create-database:" + databaseName + ':' + ownerName);
        }

        @Override
        public boolean extensionAvailable(
                final PostgresConnectionInfo applicationConnectionInfo, final String extensionName) {
            calls.add("extension-available:" + applicationConnectionInfo.database() + ':' + extensionName);

            return extensionAvailable;
        }

        @Override
        public boolean extensionInstalled(
                final PostgresConnectionInfo applicationConnectionInfo, final String extensionName) {
            calls.add("extension-installed:" + applicationConnectionInfo.database() + ':' + extensionName);

            return extensionInstalled;
        }

        @Override
        public void createExtension(
                final PostgresConnectionInfo applicationConnectionInfo, final String extensionName) {
            calls.add("create-extension:" + applicationConnectionInfo.database() + ':' + extensionName);
        }

        List<String> calls() {
            return List.copyOf(calls);
        }
    }
}
