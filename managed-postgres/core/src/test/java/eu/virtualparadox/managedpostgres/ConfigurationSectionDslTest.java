package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import org.junit.jupiter.api.Test;

final class ConfigurationSectionDslTest {

    ConfigurationSectionDslTest() {}

    @Test
    void serverConfigurationSectionSetsServerTuning() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder) ManagedPostgres.create()
                .version("18.4")
                .serverConfiguration()
                .maxConnections(48)
                .sharedBuffers("192MB")
                .tempBuffers("16MB")
                .statementTimeoutSeconds(30);

        final PostgresConfiguration configuration = builder.configuration().postgresConfiguration();
        assertThat(configuration.maxConnections().getAsInt()).isEqualTo(48);
        assertThat(configuration.sharedBuffers().get()).isEqualTo("192MB");
        assertThat(configuration.tempBuffers().get()).isEqualTo("16MB");
        assertThat(configuration.statementTimeoutSeconds().getAsInt()).isEqualTo(30);
    }
}
