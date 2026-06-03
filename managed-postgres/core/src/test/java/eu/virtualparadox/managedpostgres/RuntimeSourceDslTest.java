package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import org.junit.jupiter.api.Test;

final class RuntimeSourceDslTest {

    RuntimeSourceDslTest() {}

    @Test
    void withSystemRuntimeSelectsTheSystemRuntimeSource() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").withSystemRuntime();

        assertThat(builder.configuration().runtimeSource().kind()).isEqualTo("system");
    }
}
