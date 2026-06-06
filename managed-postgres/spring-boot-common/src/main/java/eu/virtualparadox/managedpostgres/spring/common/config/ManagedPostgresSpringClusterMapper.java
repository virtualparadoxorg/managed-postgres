package eu.virtualparadox.managedpostgres.spring.common.config;

import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer;
import java.util.Optional;

final class ManagedPostgresSpringClusterMapper {

    private ManagedPostgresSpringClusterMapper() {}

    static ManagedPostgresBuilder configure(
            final ManagedPostgresBuilder builder, final ManagedPostgresSpringProperties.ClusterProperties cluster) {
        ManagedPostgresBuilder configuredBuilder = builder;
        if (cluster.owner().isPresent()) {
            configuredBuilder = configuredBuilder.credentials(
                    cluster.owner().orElseThrow(), cluster.password().orElseThrow());
        }

        return ManagedPostgresConfigurer.of(configuredBuilder).cluster(buildCluster(cluster));
    }

    private static ClusterBootstrap buildCluster(final ManagedPostgresSpringProperties.ClusterProperties cluster) {
        ClusterBootstrap configuredCluster = ClusterBootstrap.defaultCluster().database(cluster.database());
        final Optional<String> owner = cluster.owner();
        if (owner.isPresent()) {
            configuredCluster = ownerCluster(
                    configuredCluster, owner.orElseThrow(), cluster.password().orElseThrow());
        }

        return configuredCluster;
    }

    private static ClusterBootstrap ownerCluster(
            final ClusterBootstrap cluster, final String owner, final Secret password) {
        return cluster.owner(owner).password(password);
    }
}
