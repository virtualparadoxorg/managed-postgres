package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class RuntimeSourceDslTest {

    RuntimeSourceDslTest() {}

    @Test
    void withSystemRuntimeSelectsTheSystemRuntimeSource() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").withSystemRuntime();

        assertThat(builder.configuration().runtimeSource().kind()).isEqualTo("system");
    }

    @Test
    void withExistingRuntimeSelectsTheExistingDirectoryRuntimeSource() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").withExistingRuntime(Path.of("runtime"));

        assertThat(builder.configuration().runtimeSource().kind()).isEqualTo("existing");
        assertThat(builder.configuration().runtimeSource().existingPath()).contains(Path.of("runtime"));
    }
}
