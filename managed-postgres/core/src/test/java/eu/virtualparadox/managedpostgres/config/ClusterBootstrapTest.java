package eu.virtualparadox.managedpostgres.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.bootstrap.BootstrapExtension;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class ClusterBootstrapTest {

    ClusterBootstrapTest() {
    }

    @Test
    void clusterBootstrapDefaultsToPostgresDatabaseWithoutExplicitOwnerOverride() {
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.defaultCluster();

        assertThat(clusterBootstrap.database()).isEqualTo("postgres");
        assertThat(clusterBootstrap.owner()).isEmpty();
        assertThat(clusterBootstrap.password()).isEmpty();
        assertThat(clusterBootstrap.extensions()).isEmpty();
    }

    @Test
    void clusterBootstrapSupportsImmutableFluentOverrides() {
        final Secret password = Secret.of("app-password");
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.defaultCluster()
                .database("app")
                .owner("app_owner")
                .password(password);

        assertThat(clusterBootstrap.database()).isEqualTo("app");
        assertThat(clusterBootstrap.owner()).contains("app_owner");
        assertThat(clusterBootstrap.password()).contains(password);
        assertThat(clusterBootstrap.toString())
                .contains("app")
                .contains("app_owner")
                .contains("REDACTED")
                .doesNotContain("app-password");
        assertThat(ClusterBootstrap.defaultCluster().database()).isEqualTo("postgres");
    }

    @Test
    void clusterBootstrapSupportsImmutableExtensionRequests() {
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.defaultCluster()
                .extension("pgcrypto")
                .optionalExtension("postgis");

        assertThat(clusterBootstrap.extensions()).containsExactly(
                BootstrapExtension.required("pgcrypto"),
                BootstrapExtension.optional("postgis"));
        assertThatThrownBy(() -> clusterBootstrap.extensions().add(BootstrapExtension.required("uuid-ossp")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(ClusterBootstrap.defaultCluster().extensions()).isEmpty();
    }

    @Test
    void clusterBootstrapRejectsInvalidValues() {
        assertThatThrownBy(() -> new ClusterBootstrap(" ", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database");
        assertThatThrownBy(() -> new ClusterBootstrap("postgres", Optional.of(""), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
        assertThatThrownBy(() -> assertThat(ClusterBootstrap.defaultCluster().owner(" ")).isNotNull())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
        assertThatThrownBy(ClusterBootstrapTest::invokeClusterBootstrapPasswordWithNullSecret)
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("newPassword");
    }

    @Test
    void bootstrapExtensionRejectsInvalidValues() {
        assertThatThrownBy(() -> BootstrapExtension.required(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extension");
        assertThatThrownBy(() -> BootstrapExtension.optional("pg\u0000crypto"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUL");
        assertThatThrownBy(ClusterBootstrapTest::invokeBootstrapExtensionWithNullPolicy)
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("policy");
    }

    private static void invokeClusterBootstrapPasswordWithNullSecret() throws ReflectiveOperationException {
        ClusterBootstrap.class.getMethod("password", Secret.class)
                .invoke(ClusterBootstrap.defaultCluster(), new Object[] {null});
    }

    private static void invokeBootstrapExtensionWithNullPolicy() throws ReflectiveOperationException {
        BootstrapExtension.class.getConstructor(String.class, BootstrapExtension.Policy.class)
                .newInstance(new Object[] {"pgcrypto", null});
    }
}
