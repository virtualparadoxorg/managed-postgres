package eu.virtualparadox.managedpostgres.spring.boot4.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresSpringNetworkMapperTest {

    ManagedPostgresSpringNetworkMapperTest() {}

    @Test
    void randomNetworkPropertiesMapToCoreDsl() {
        final ManagedPostgresSpringProperties.NetworkProperties properties =
                new ManagedPostgresSpringProperties.NetworkProperties("127.0.0.1", "random", Optional.empty(), false);

        final String description = managedPostgresDescription(properties);

        assertThat(description)
                .contains("Network[host=127.0.0.1")
                .contains("mode=RANDOM")
                .contains("port=OptionalInt.empty")
                .contains("fallbackToRandom=false");
    }

    @Test
    void stableRandomNetworkPropertiesMapToCoreDsl() {
        final ManagedPostgresSpringProperties.NetworkProperties properties =
                new ManagedPostgresSpringProperties.NetworkProperties(
                        "127.0.0.1", "stable-random", Optional.empty(), false);

        final String description = managedPostgresDescription(properties);

        assertThat(description)
                .contains("Network[host=127.0.0.1")
                .contains("mode=STABLE_RANDOM")
                .contains("port=OptionalInt.empty")
                .contains("fallbackToRandom=false");
    }

    @Test
    void fixedNetworkPropertiesMapToCoreDsl() {
        final ManagedPostgresSpringProperties.NetworkProperties properties =
                new ManagedPostgresSpringProperties.NetworkProperties("127.0.0.1", "fixed", Optional.of(15432), false);

        final String description = managedPostgresDescription(properties);

        assertThat(description)
                .contains("Network[host=127.0.0.1")
                .contains("mode=FIXED")
                .contains("port=OptionalInt[15432]")
                .contains("fallbackToRandom=false");
    }

    @Test
    void preferredNetworkPropertiesMapToCoreDsl() {
        final ManagedPostgresSpringProperties.NetworkProperties properties =
                new ManagedPostgresSpringProperties.NetworkProperties(
                        "127.0.0.1", "preferred", Optional.of(15432), false);

        final String description = managedPostgresDescription(properties);

        assertThat(description)
                .contains("Network[host=127.0.0.1")
                .contains("mode=PREFERRED")
                .contains("port=OptionalInt[15432]")
                .contains("fallbackToRandom=false");
    }

    @Test
    void preferredNetworkPropertiesMapToCoreDslWithFallback() {
        final ManagedPostgresSpringProperties.NetworkProperties properties =
                new ManagedPostgresSpringProperties.NetworkProperties(
                        "127.0.0.1", "preferred", Optional.of(15432), true);

        final String description = managedPostgresDescription(properties);

        assertThat(description)
                .contains("Network[host=127.0.0.1")
                .contains("mode=PREFERRED")
                .contains("port=OptionalInt[15432]")
                .contains("fallbackToRandom=true");
    }

    @Test
    void invalidNetworkPropertiesFailBeforeStartup() {
        assertThatThrownBy(() -> configure(new ManagedPostgresSpringProperties.NetworkProperties(
                        "127.0.0.1", "fixed", Optional.empty(), false)))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("network.port");
        assertThatThrownBy(() -> configure(new ManagedPostgresSpringProperties.NetworkProperties(
                        "127.0.0.1", "random", Optional.empty(), true)))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("fallback-to-random");
        assertThatThrownBy(() -> configure(new ManagedPostgresSpringProperties.NetworkProperties(
                        "127.0.0.1", "unknown", Optional.empty(), false)))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("port-selection");
    }

    private static ManagedPostgresBuilder configure(
            final ManagedPostgresSpringProperties.NetworkProperties properties) {
        return ManagedPostgresSpringNetworkMapper.configure(ManagedPostgresBuilder.local(), properties);
    }

    private static String managedPostgresDescription(
            final ManagedPostgresSpringProperties.NetworkProperties properties) {
        final ManagedPostgresBuilder builder = configure(properties);
        final String description;
        try (ManagedPostgres managedPostgres = builder.build()) {
            description = managedPostgres.toString();
        }

        return description;
    }
}
