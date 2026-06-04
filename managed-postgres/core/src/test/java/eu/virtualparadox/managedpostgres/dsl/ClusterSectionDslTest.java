package eu.virtualparadox.managedpostgres.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.bootstrap.BootstrapExtension;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import org.junit.jupiter.api.Test;

final class ClusterSectionDslTest {

    ClusterSectionDslTest() {}

    @Test
    void clusterSectionConfiguresDatabaseOwnerPasswordAndExtension() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder) ManagedPostgres.create()
                .version("18.4")
                .cluster()
                .database("app")
                .owner("app_owner")
                .password("app-password")
                .extension("pgcrypto");

        final ClusterBootstrap cluster = builder.configuration().clusterBootstrap();
        assertThat(cluster.database()).isEqualTo("app");
        assertThat(cluster.owner()).contains("app_owner");
        assertThat(cluster.password()).isPresent();
        assertThat(cluster.extensions()).hasSize(1);
        assertThat(cluster.extensions().get(0).name()).isEqualTo("pgcrypto");
        assertThat(cluster.toString()).contains("REDACTED").doesNotContain("app-password");
    }

    @Test
    void clusterSectionExtensionsAccumulate() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder) ManagedPostgres.create()
                .version("18.4")
                .cluster()
                .extension("pgcrypto")
                .optionalExtension("postgis");

        final ClusterBootstrap cluster = builder.configuration().clusterBootstrap();
        assertThat(cluster.extensions())
                .containsExactly(BootstrapExtension.required("pgcrypto"), BootstrapExtension.optional("postgis"));
    }
}
