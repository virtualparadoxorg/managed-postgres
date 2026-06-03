package eu.virtualparadox.managedpostgres.spring.boot4.config;

import static eu.virtualparadox.managedpostgres.spring.boot4.config.SpringEnvironmentFixture.environment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresSpringClasspathRuntimePropertiesTest {

    private static final String CHECKSUM = "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    ManagedPostgresSpringClasspathRuntimePropertiesTest() {}

    @Test
    void classpathRuntimeSourceReadsResourceChecksumAndCache() {
        final ManagedPostgresSpringProperties properties = ManagedPostgresSpringProperties.from(environment(Map.of(
                "managed-postgres.runtime.source", "classpath",
                "managed-postgres.runtime.resource", "/postgres-runtime.zip",
                "managed-postgres.runtime.checksum", CHECKSUM,
                "managed-postgres.runtime.cache", ".local/runtime-cache")));

        assertThat(properties.runtime().source()).isEqualTo("classpath");
        assertThat(properties.runtime().path()).isEmpty();
        assertThat(properties.runtime().resource()).contains("/postgres-runtime.zip");
        assertThat(properties.runtime().checksum()).contains(CHECKSUM);
        assertThat(properties.runtime().cache()).contains(Path.of(".local/runtime-cache"));
    }

    @Test
    void classpathRuntimeSourceRequiresResourceAndChecksum() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source",
                        "classpath",
                        "managed-postgres.runtime.checksum",
                        CHECKSUM))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.resource");
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "classpath",
                        "managed-postgres.runtime.resource", "/postgres-runtime.zip"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.checksum");
    }

    @Test
    void classpathRuntimeSourceRejectsRuntimePath() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "classpath",
                        "managed-postgres.runtime.path", "runtime/postgres-16.4",
                        "managed-postgres.runtime.resource", "/postgres-runtime.zip",
                        "managed-postgres.runtime.checksum", CHECKSUM))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.path");
    }

    @Test
    void nonClasspathRuntimeSourcesRejectClasspathRuntimeDetails() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "system",
                        "managed-postgres.runtime.resource", "/postgres-runtime.zip"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.resource");
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "system", "managed-postgres.runtime.checksum", CHECKSUM))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.checksum");
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "system",
                        "managed-postgres.runtime.cache", ".local/runtime-cache"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.cache");
    }
}
