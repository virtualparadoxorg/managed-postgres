package eu.virtualparadox.managedpostgres.spi;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ManagedPostgresConfigurerTest {

    ManagedPostgresConfigurerTest() {}

    @Test
    void configurerAppliesACompleteStorageValueObject() {
        final Storage storage = new Storage(Path.of(".local/custom-temp"), true);
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgresConfigurer.of(ManagedPostgres.create().version("18.4"))
                        .storage(storage);

        assertThat(builder.configuration().storage()).isEqualTo(storage);
    }

    @Test
    void configurerAppliesACompleteNetworkValueObject() {
        final Network network = Network.localhostOnly().port(6543);
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgresConfigurer.of(ManagedPostgres.create().version("18.4"))
                        .network(network);

        assertThat(builder.configuration().network()).isEqualTo(network);
    }

    @Test
    void configurerAppliesACompleteClusterValueObject() {
        final ClusterBootstrap cluster = ClusterBootstrap.defaultCluster().database("app");
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgresConfigurer.of(ManagedPostgres.create().version("18.4"))
                        .cluster(cluster);

        assertThat(builder.configuration().clusterBootstrap()).isEqualTo(cluster);
    }
}
