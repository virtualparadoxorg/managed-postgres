package eu.virtualparadox.managedpostgres.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
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

    @Test
    void withClasspathRuntimeSelectsAClasspathRuntimeSourceWithChecksumAndCache() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder) ManagedPostgres.create()
                .version("18.4")
                .withClasspathRuntime("/postgres-runtime.zip", "a".repeat(64))
                .cacheProjectLocal(Path.of(".local/runtime-cache"));

        final var runtimeSource = builder.configuration().runtimeSource();
        assertThat(runtimeSource.kind()).isEqualTo("classpath");
        final var classpath = runtimeSource.classpathRuntime().orElseThrow();
        assertThat(classpath.resource()).isEqualTo("/postgres-runtime.zip");
        assertThat(classpath.checksum()).contains("a".repeat(64));
        assertThat(classpath.cache().orElseThrow().root()).isEqualTo(Path.of(".local/runtime-cache"));
    }
}
