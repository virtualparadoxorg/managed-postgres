package eu.virtualparadox.managedpostgres.spring.boot4.config;

import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Optional;
import java.util.function.UnaryOperator;

final class ManagedPostgresSpringClusterMapper {

    private ManagedPostgresSpringClusterMapper() {}

    static ManagedPostgresBuilder configure(
            final ManagedPostgresBuilder builder, final ManagedPostgresSpringProperties.ClusterProperties cluster) {
        ManagedPostgresBuilder configuredBuilder = builder;
        if (cluster.owner().isPresent()) {
            configuredBuilder = configuredBuilder.credentials(credentials(cluster));
        }

        return configuredBuilder.cluster(clusterCustomizer(cluster));
    }

    private static Credentials credentials(final ManagedPostgresSpringProperties.ClusterProperties cluster) {
        return Credentials.of(cluster.owner().orElseThrow(), cluster.password().orElseThrow());
    }

    private static UnaryOperator<ClusterBootstrap> clusterCustomizer(
            final ManagedPostgresSpringProperties.ClusterProperties cluster) {
        return currentCluster -> {
            ClusterBootstrap configuredCluster = currentCluster.database(cluster.database());
            final Optional<String> owner = cluster.owner();
            if (owner.isPresent()) {
                configuredCluster = ownerCluster(
                        configuredCluster,
                        owner.orElseThrow(),
                        cluster.password().orElseThrow());
            }

            return configuredCluster;
        };
    }

    private static ClusterBootstrap ownerCluster(
            final ClusterBootstrap cluster, final String owner, final Secret password) {
        return cluster.owner(owner).password(password);
    }
}
