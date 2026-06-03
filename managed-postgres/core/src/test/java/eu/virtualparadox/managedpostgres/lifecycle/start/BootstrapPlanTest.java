package eu.virtualparadox.managedpostgres.lifecycle.start;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.bootstrap.BootstrapExtension;
import eu.virtualparadox.managedpostgres.security.Secret;
import org.junit.jupiter.api.Test;

public final class BootstrapPlanTest {

    BootstrapPlanTest() {}

    @Test
    void defaultBootstrapPlanUsesAdminConnectionWithoutRoleOrDatabaseCreation() {
        final PostgresConnectionInfo adminConnectionInfo = adminConnectionInfo();
        final BootstrapPlan plan = BootstrapPlan.from(adminConnectionInfo, ClusterBootstrap.defaultCluster());

        assertThat(plan.applicationConnectionInfo()).isEqualTo(adminConnectionInfo);
        assertThat(plan.roleName()).isEqualTo("postgres");
        assertThat(plan.databaseName()).isEqualTo("postgres");
        assertThat(plan.requiresRoleBootstrap()).isFalse();
        assertThat(plan.requiresDatabaseBootstrap()).isFalse();
    }

    @Test
    void customBootstrapPlanUsesApplicationConnectionInfoAndBootstrapActions() {
        final Secret password = Secret.of("app-password");
        final BootstrapPlan plan = BootstrapPlan.from(
                adminConnectionInfo(),
                ClusterBootstrap.defaultCluster()
                        .database("app")
                        .owner("app_owner")
                        .password(password)
                        .extension("pgcrypto"));

        assertThat(plan.applicationConnectionInfo().database()).isEqualTo("app");
        assertThat(plan.applicationConnectionInfo().username()).isEqualTo("app_owner");
        assertThat(plan.applicationConnectionInfo().password()).isEqualTo(password);
        assertThat(plan.roleName()).isEqualTo("app_owner");
        assertThat(plan.databaseName()).isEqualTo("app");
        assertThat(plan.extensions()).containsExactly(BootstrapExtension.required("pgcrypto"));
        assertThat(plan.requiresRoleBootstrap()).isTrue();
        assertThat(plan.requiresDatabaseBootstrap()).isTrue();
        assertThat(plan.toString()).contains("app").contains("app_owner").doesNotContain("app-password");
    }

    @Test
    void customDatabaseWithoutOwnerUsesAdminRoleAndDoesNotCreateRole() {
        final BootstrapPlan plan = BootstrapPlan.from(
                adminConnectionInfo(), ClusterBootstrap.defaultCluster().database("app"));

        assertThat(plan.applicationConnectionInfo().database()).isEqualTo("app");
        assertThat(plan.applicationConnectionInfo().username()).isEqualTo("postgres");
        assertThat(plan.requiresRoleBootstrap()).isFalse();
        assertThat(plan.requiresDatabaseBootstrap()).isTrue();
    }

    private static PostgresConnectionInfo adminConnectionInfo() {
        return new PostgresConnectionInfo("127.0.0.1", 55432, "postgres", "postgres", Secret.of("admin-password"));
    }
}
