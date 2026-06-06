package eu.virtualparadox.managedpostgres.spring.common.config;

import static eu.virtualparadox.managedpostgres.spring.common.config.SpringEnvironmentFixture.environment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresSpringNetworkPropertiesTest {

    ManagedPostgresSpringNetworkPropertiesTest() {}

    @Test
    void networkPropertiesAreParsedFromEnvironment() {
        final ManagedPostgresSpringProperties properties = ManagedPostgresSpringProperties.from(environment(Map.of(
                "managed-postgres.network.host", "127.0.0.1",
                "managed-postgres.network.port-selection", "preferred",
                "managed-postgres.network.port", "15432",
                "managed-postgres.network.fallback-to-random", "true")));

        assertThat(properties.network().host()).isEqualTo("127.0.0.1");
        assertThat(properties.network().portSelection()).isEqualTo("preferred");
        assertThat(properties.network().port()).contains(15432);
        assertThat(properties.network().fallbackToRandom()).isTrue();
    }

    @Test
    void blankNetworkHostFailsBeforeLifecycleStart() {
        assertBlankPropertyFails("managed-postgres.network.host", "network.host");
    }

    @Test
    void blankNetworkPortSelectionFailsBeforeLifecycleStart() {
        assertBlankPropertyFails("managed-postgres.network.port-selection", "network.port-selection");
    }

    private static void assertBlankPropertyFails(final String propertyName, final String messagePart) {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(propertyName, "  "))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining(messagePart);
    }
}
