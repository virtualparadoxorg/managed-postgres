package eu.virtualparadox.managedpostgres.spring.common.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.spring.common.config.ManagedPostgresSpringException;
import eu.virtualparadox.managedpostgres.spring.common.config.ManagedPostgresSpringProperties;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

public final class ManagedPostgresSpringDownloadedRuntimePropertiesTest {

    private static final String CHECKSUM = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String SIGNATURE_PUBLIC_KEY = "public-key";
    private static final String SIGNATURE_VALUE = "signature-value";

    ManagedPostgresSpringDownloadedRuntimePropertiesTest() {}

    @Test
    void downloadedRuntimeSourceReadsRepositoryChecksumAndCache() {
        final ManagedPostgresSpringProperties properties = ManagedPostgresSpringProperties.from(environment(Map.of(
                "managed-postgres.runtime.source",
                "downloaded",
                "managed-postgres.runtime.repository",
                "file:///tmp/postgres.zip",
                "managed-postgres.runtime.checksum",
                CHECKSUM,
                "managed-postgres.runtime.signature.public-key",
                SIGNATURE_PUBLIC_KEY,
                "managed-postgres.runtime.signature.value",
                SIGNATURE_VALUE,
                "managed-postgres.runtime.cache",
                ".local/runtime-cache")));

        assertThat(properties.runtime().source()).isEqualTo("downloaded");
        assertThat(properties.runtime().path()).isEmpty();
        assertThat(properties.runtime().resource()).isEmpty();
        assertThat(properties.runtime().repository()).contains("file:///tmp/postgres.zip");
        assertThat(properties.runtime().checksum()).contains(CHECKSUM);
        assertThat(properties.runtime().signaturePublicKey()).contains(SIGNATURE_PUBLIC_KEY);
        assertThat(properties.runtime().signatureValue()).contains(SIGNATURE_VALUE);
        assertThat(properties.runtime().cache()).contains(Path.of(".local/runtime-cache"));
    }

    @Test
    void downloadedRuntimeSourceRequiresRepositoryAndChecksum() {
        final ManagedPostgresSpringProperties cacheOnlyProperties =
                ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "downloaded",
                        "managed-postgres.runtime.checksum", CHECKSUM,
                        "managed-postgres.runtime.cache", ".local/runtime-cache")));

        assertThat(cacheOnlyProperties.runtime().repository()).isEmpty();
        assertThat(cacheOnlyProperties.runtime().checksum()).contains(CHECKSUM);
        assertThat(cacheOnlyProperties.runtime().cache()).contains(Path.of(".local/runtime-cache"));

        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "downloaded",
                        "managed-postgres.runtime.repository", "file:///tmp/postgres.zip"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.checksum");
    }

    @Test
    void downloadedRuntimeSourceRejectsRuntimePathAndResource() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "downloaded",
                        "managed-postgres.runtime.path", "runtime/postgres-16.4",
                        "managed-postgres.runtime.repository", "file:///tmp/postgres.zip",
                        "managed-postgres.runtime.checksum", CHECKSUM))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.path");
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "downloaded",
                        "managed-postgres.runtime.resource", "/postgres-runtime.zip",
                        "managed-postgres.runtime.repository", "file:///tmp/postgres.zip",
                        "managed-postgres.runtime.checksum", CHECKSUM))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.resource");
    }

    @Test
    void nonDownloadedRuntimeSourcesRejectRuntimeRepository() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "system",
                        "managed-postgres.runtime.repository", "file:///tmp/postgres.zip"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.repository");
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "existing",
                        "managed-postgres.runtime.path", "runtime/postgres-16.4",
                        "managed-postgres.runtime.repository", "file:///tmp/postgres.zip"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.repository");
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "classpath",
                        "managed-postgres.runtime.resource", "/postgres-runtime.zip",
                        "managed-postgres.runtime.checksum", CHECKSUM,
                        "managed-postgres.runtime.repository", "file:///tmp/postgres.zip"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.repository");
    }

    @Test
    void runtimeSignaturePropertiesMustBeConfiguredTogether() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source",
                        "downloaded",
                        "managed-postgres.runtime.repository",
                        "file:///tmp/postgres.zip",
                        "managed-postgres.runtime.checksum",
                        CHECKSUM,
                        "managed-postgres.runtime.signature.value",
                        SIGNATURE_VALUE))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime signature public key and value must be configured together");
    }

    @Test
    void runtimeSignaturePropertiesRequireDownloadedOrClasspathRuntimeSource() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "system",
                        "managed-postgres.runtime.signature.public-key", SIGNATURE_PUBLIC_KEY,
                        "managed-postgres.runtime.signature.value", SIGNATURE_VALUE))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.signature is only valid for classpath or downloaded runtime source");
    }

    private static ConfigurableEnvironment environment(final Map<String, Object> properties) {
        final MockEnvironment environment = new MockEnvironment();
        properties.forEach((key, value) -> environment.setProperty(key, value.toString()));

        return environment;
    }
}
