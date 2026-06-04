package eu.virtualparadox.managedpostgres.spi;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import org.junit.jupiter.api.Test;

final class ManagedPostgresConfigurerRuntimeTest {

    ManagedPostgresConfigurerRuntimeTest() {}

    @Test
    void runtimeAppliesRuntimeSourceToConfiguration() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgresConfigurer.of(ManagedPostgres.create().version("18.4"))
                        .runtime(RuntimeSource.system());

        assertThat(builder.configuration().runtimeSource()).isEqualTo(RuntimeSource.system());
    }
}
