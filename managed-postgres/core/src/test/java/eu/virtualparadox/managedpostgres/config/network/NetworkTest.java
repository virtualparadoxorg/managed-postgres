package eu.virtualparadox.managedpostgres.config.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

public final class NetworkTest {

    NetworkTest() {}

    @Test
    void localhostNetworkSupportsImmutablePortPolicies() {
        final Network network = Network.localhostOnly();

        final Network fixed = network.port(15432);
        final Network stableRandom = network.stableRandomPort();
        final Network preferredWithFallback = network.preferredPort(15433).fallbackToRandom();

        assertThat(network.host()).isEqualTo("127.0.0.1");
        assertThat(network.portSelection().mode()).isEqualTo(Network.PortSelectionMode.RANDOM);
        assertThat(network.portSelection().port()).isEmpty();
        assertThat(fixed.portSelection().mode()).isEqualTo(Network.PortSelectionMode.FIXED);
        assertThat(fixed.portSelection().port()).hasValue(15432);
        assertThat(stableRandom.portSelection().mode()).isEqualTo(Network.PortSelectionMode.STABLE_RANDOM);
        assertThat(preferredWithFallback.portSelection().mode()).isEqualTo(Network.PortSelectionMode.PREFERRED);
        assertThat(preferredWithFallback.portSelection().port()).hasValue(15433);
        assertThat(preferredWithFallback.portSelection().fallbackToRandom()).isTrue();
        assertThat(network.portSelection().mode()).isEqualTo(Network.PortSelectionMode.RANDOM);
    }

    @Test
    void networkRejectsUnsafeExposureAndInvalidPortSelections() {
        assertThatThrownBy(() -> assign(Network.localhostOnly().host("0.0.0.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("127.0.0.1");
        assertThatThrownBy(() -> assign(Network.localhostOnly().host(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("127.0.0.1");
        assertThatThrownBy(() -> assign(Network.localhostOnly().port(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> assign(Network.localhostOnly().preferredPort(65_536)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> assign(Network.localhostOnly().fallbackToRandom()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("preferredPort");
    }

    @Test
    void portSelectionRejectsInconsistentStates() {
        assertThatThrownBy(
                        () -> new Network.PortSelection(Network.PortSelectionMode.RANDOM, OptionalInt.of(15432), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Network.PortSelection(Network.PortSelectionMode.FIXED, OptionalInt.empty(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new Network.PortSelection(Network.PortSelectionMode.STABLE_RANDOM, OptionalInt.empty(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assign(final Network network) {
        assertThat(network).isNotNull();
    }
}
