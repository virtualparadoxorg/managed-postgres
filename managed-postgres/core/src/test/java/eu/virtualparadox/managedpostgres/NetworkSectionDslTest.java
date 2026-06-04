package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

final class NetworkSectionDslTest {

    NetworkSectionDslTest() {}

    @Test
    void networkSectionConfiguresHostAndPreferredPortWithFallback() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder) ManagedPostgres.create()
                .version("18.4")
                .network()
                .host("127.0.0.1")
                .preferredPort(15432)
                .fallbackToRandom();

        final Network network = builder.configuration().network();
        assertThat(network.host()).isEqualTo("127.0.0.1");
        assertThat(network.portSelection().port()).isEqualTo(OptionalInt.of(15432));
        assertThat(network.portSelection().fallbackToRandom()).isTrue();
    }

    @Test
    void networkSectionStableRandomPortSelectsStableRandomMode() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").network().stableRandomPort();

        assertThat(builder.configuration().network().portSelection().mode())
                .isEqualTo(Network.PortSelectionMode.STABLE_RANDOM);
    }
}
