package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresBuilderConfigurationTest {

    ManagedPostgresBuilderConfigurationTest() {}

    @Test
    void builderStoresPostgreSqlConfigurationAndResourcePreset() {
        try (ManagedPostgres postgres = ManagedPostgres.builder()
                .configuration(Resources.small())
                .serverConfiguration()
                .maxConnections(48)
                .sharedBuffers("192MB")
                .build()) {
            assertThat(postgres.toString())
                    .contains("postgresConfiguration")
                    .contains("maxConnections=OptionalInt[48]")
                    .contains("sharedBuffers=Optional[192MB]");
        }
    }
}
