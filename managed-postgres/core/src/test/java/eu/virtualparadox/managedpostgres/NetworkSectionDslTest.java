package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void hostRejectsNull() throws ReflectiveOperationException {
        final NetworkSection section = ManagedPostgres.create().version("18.4").network();
        assertThatThrownBy(() -> invokeHostWithNull(section))
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("host");
    }

    private static void invokeHostWithNull(final NetworkSection section) throws ReflectiveOperationException {
        NetworkSection.class.getMethod("host", String.class).invoke(section, new Object[] {null});
    }

    @Test
    void networkSectionFixedPortSelectsFixedMode() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").network().port(15999);

        assertThat(builder.configuration().network().portSelection().mode()).isEqualTo(Network.PortSelectionMode.FIXED);
        assertThat(builder.configuration().network().portSelection().port()).isEqualTo(OptionalInt.of(15999));
    }

    @Test
    void networkSectionRandomPortSelectsRandomMode() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder) ManagedPostgres.create()
                .version("18.4")
                .network()
                .stableRandomPort()
                .randomPort();

        assertThat(builder.configuration().network().portSelection().mode())
                .isEqualTo(Network.PortSelectionMode.RANDOM);
    }
}
