package eu.virtualparadox.managedpostgres.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresNetworkBuilderTest {

    ManagedPostgresNetworkBuilderTest() {}

    @Test
    void builderStoresNetworkConfiguration() {
        try (ManagedPostgres postgres = ManagedPostgres.create()
                .network()
                .host("127.0.0.1")
                .preferredPort(15432)
                .fallbackToRandom()
                .build()) {
            assertThat(postgres.toString())
                    .contains("network=Network")
                    .contains("host=127.0.0.1")
                    .contains("port=OptionalInt[15432]")
                    .contains("fallbackToRandom=true");
        }
    }
}
