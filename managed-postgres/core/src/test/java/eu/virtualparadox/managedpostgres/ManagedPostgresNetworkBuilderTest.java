package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresNetworkBuilderTest {

    ManagedPostgresNetworkBuilderTest() {}

    @Test
    void builderStoresNetworkConfiguration() {
        try (ManagedPostgres postgres = ManagedPostgres.builder()
                .network(network ->
                        network.host("127.0.0.1").preferredPort(15432).fallbackToRandom())
                .build()) {
            assertThat(postgres.toString())
                    .contains("network=Network")
                    .contains("host=127.0.0.1")
                    .contains("port=OptionalInt[15432]")
                    .contains("fallbackToRandom=true");
        }
    }

    @Test
    void builderRejectsInvalidNetworkCustomizer() throws ReflectiveOperationException {
        assertThatThrownBy(ManagedPostgresNetworkBuilderTest::invokeNetworkCustomizerWithNull)
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("customizer");
        assertThatThrownBy(() -> ManagedPostgres.builder().network(network -> null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("network");
    }

    private static void invokeNetworkCustomizerWithNull() throws ReflectiveOperationException {
        ManagedPostgresBuilder.class
                .getMethod("network", UnaryOperator.class)
                .invoke(ManagedPostgres.builder(), new Object[] {null});
    }
}
