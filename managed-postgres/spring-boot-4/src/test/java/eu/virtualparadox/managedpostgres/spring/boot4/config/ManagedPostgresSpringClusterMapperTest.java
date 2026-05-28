package eu.virtualparadox.managedpostgres.spring.boot4.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresSpringClusterMapperTest {

    ManagedPostgresSpringClusterMapperTest() {
    }

    @Test
    void clusterCredentialsMapToCoreCredentialsAndClusterBootstrap() {
        final ClusterFixture fixture = ClusterFixture.create();
        final Secret secret = Secret.of("app-password");
        final ManagedPostgresSpringProperties.ClusterProperties properties =
                new ManagedPostgresSpringProperties.ClusterProperties("app", Optional.of("app_owner"), Optional.of(secret));

        ManagedPostgresSpringClusterMapper.configure(fixture.builder(), properties);

        verify(fixture.builder()).credentials(Credentials.of("app_owner", secret));
        assertThat(fixture.clusterBootstrap().database()).isEqualTo("app");
        assertThat(fixture.clusterBootstrap().owner()).contains("app_owner");
        assertThat(fixture.clusterBootstrap().password()).contains(secret);
        assertThat(fixture.clusterBootstrap().toString()).doesNotContain("app-password");
    }

    @Test
    void clusterBootstrapWithoutOwnerKeepsDefaultOwnerAndPasswordAbsent() {
        final ClusterFixture fixture = ClusterFixture.create();
        final ManagedPostgresSpringProperties.ClusterProperties properties =
                new ManagedPostgresSpringProperties.ClusterProperties("app", Optional.empty(), Optional.empty());

        ManagedPostgresSpringClusterMapper.configure(fixture.builder(), properties);

        assertThat(fixture.clusterBootstrap().database()).isEqualTo("app");
        assertThat(fixture.clusterBootstrap().owner()).isEmpty();
        assertThat(fixture.clusterBootstrap().password()).isEmpty();
    }

    private static final class ClusterFixture {

        private final ManagedPostgresBuilder builder;
        private ClusterBootstrap clusterBootstrap;

        private ClusterFixture(final ManagedPostgresBuilder builder) {
            this.builder = builder;
            this.clusterBootstrap = ClusterBootstrap.defaultCluster();
        }

        @SuppressWarnings("unchecked")
        private static ClusterFixture create() {
            final ManagedPostgresBuilder builder = mock(ManagedPostgresBuilder.class);
            final ClusterFixture fixture = new ClusterFixture(builder);
            when(builder.credentials(any())).thenReturn(builder);
            when(builder.cluster(any(UnaryOperator.class))).thenAnswer(invocation -> {
                final UnaryOperator<ClusterBootstrap> customizer = invocation.getArgument(0);
                fixture.clusterBootstrap = customizer.apply(ClusterBootstrap.defaultCluster());

                return builder;
            });

            return fixture;
        }

        private ManagedPostgresBuilder builder() {
            return builder;
        }

        private ClusterBootstrap clusterBootstrap() {
            return clusterBootstrap;
        }
    }
}
