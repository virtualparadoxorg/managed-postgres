package eu.virtualparadox.managedpostgres.spi;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import org.junit.jupiter.api.Test;

final class ManagedPostgresConfigurerConfigurationTest {

    ManagedPostgresConfigurerConfigurationTest() {}

    @Test
    void configurationAppliesPostgresConfiguration() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgresConfigurer.of(ManagedPostgres.create().version("18.4"))
                        .configuration(Resources.small());

        assertThat(builder.configuration().postgresConfiguration()).isEqualTo(Resources.small());
    }
}
