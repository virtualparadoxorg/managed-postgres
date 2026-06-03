package eu.virtualparadox.managedpostgres.spring.boot4.config;

import static eu.virtualparadox.managedpostgres.spring.boot4.config.SpringEnvironmentFixture.environment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresSpringPropertiesTest {

    private static final String RAW_PASSWORD = "spring-secret";

    ManagedPostgresSpringPropertiesTest() {}

    @Test
    void defaultPropertiesKeepManagedPostgresDisabled() {
        final ManagedPostgresSpringProperties properties = ManagedPostgresSpringProperties.from(environment(Map.of()));

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.name()).isEqualTo("default");
        assertThat(properties.postgresqlVersion()).isEqualTo("16.4");
        assertThat(properties.storage().path()).isEqualTo(Path.of(".local/postgres"));
        assertThat(properties.runtime().source()).isEqualTo("system");
        assertThat(properties.runtime().path()).isEmpty();
        assertThat(properties.runtime().repository()).isEmpty();
        assertThat(properties.network().host()).isEqualTo("127.0.0.1");
        assertThat(properties.network().portSelection()).isEqualTo("stable-random");
        assertThat(properties.network().port()).isEmpty();
        assertThat(properties.network().fallbackToRandom()).isFalse();
        assertThat(properties.configuration().isEmpty()).isTrue();
        assertThat(properties.datasource().enabled()).isTrue();
        assertThat(properties.datasource().overrideExisting()).isFalse();
        assertThat(properties.cluster().database()).isEqualTo("postgres");
        assertThat(properties.cluster().owner()).isEmpty();
        assertThat(properties.cluster().password()).isEmpty();
        assertThat(properties.lifecycle().reuseExisting()).isFalse();
        assertThat(properties.lifecycle().keepRunning()).isFalse();
    }

    @Test
    void enabledPropertyIsParsedFromEnvironment() {
        final ManagedPostgresSpringProperties properties =
                ManagedPostgresSpringProperties.from(environment(Map.of("managed-postgres.enabled", "true")));

        assertThat(properties.enabled()).isTrue();
    }

    @Test
    void postgresConfigurationPropertiesAreParsedFromEnvironment() {
        final ManagedPostgresSpringProperties properties = ManagedPostgresSpringProperties.from(environment(Map.of(
                "managed-postgres.configuration.preset", "tiny",
                "managed-postgres.configuration.max-connections", "20",
                "managed-postgres.configuration.shared-buffers", "80MB",
                "managed-postgres.configuration.temp-buffers", "8MB",
                "managed-postgres.configuration.statement-timeout-seconds", "12")));

        assertThat(properties.configuration().preset()).contains("tiny");
        assertThat(properties.configuration().maxConnections()).contains(20);
        assertThat(properties.configuration().sharedBuffers()).contains("80MB");
        assertThat(properties.configuration().tempBuffers()).contains("8MB");
        assertThat(properties.configuration().statementTimeoutSeconds()).contains(12);
    }

    @Test
    void configurationPropertiesIsEmptyReflectsEachOptionalBranch() {
        assertThat(new ManagedPostgresSpringProperties.ConfigurationProperties(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty())
                        .isEmpty())
                .isTrue();
        assertThat(new ManagedPostgresSpringProperties.ConfigurationProperties(
                                Optional.of("tiny"),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty())
                        .isEmpty())
                .isFalse();
        assertThat(new ManagedPostgresSpringProperties.ConfigurationProperties(
                                Optional.empty(), Optional.of(20), Optional.empty(), Optional.empty(), Optional.empty())
                        .isEmpty())
                .isFalse();
        assertThat(new ManagedPostgresSpringProperties.ConfigurationProperties(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of("80MB"),
                                Optional.empty(),
                                Optional.empty())
                        .isEmpty())
                .isFalse();
        assertThat(new ManagedPostgresSpringProperties.ConfigurationProperties(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of("8MB"),
                                Optional.empty())
                        .isEmpty())
                .isFalse();
        assertThat(new ManagedPostgresSpringProperties.ConfigurationProperties(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(12))
                        .isEmpty())
                .isFalse();
    }

    @Test
    void existingRuntimeSourceRequiresRuntimePath() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(
                        environment(Map.of("managed-postgres.runtime.source", "existing"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.path");
    }

    @Test
    void runtimePathWithoutRuntimeSourceInfersExistingRuntime() {
        final ManagedPostgresSpringProperties properties = ManagedPostgresSpringProperties.from(
                environment(Map.of("managed-postgres.runtime.path", "runtime/postgres-16.4")));

        assertThat(properties.runtime().source()).isEqualTo("existing");
        assertThat(properties.runtime().path()).contains(Path.of("runtime/postgres-16.4"));
    }

    @Test
    void systemRuntimeSourceRejectsRuntimePath() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(
                        "managed-postgres.runtime.source", "system",
                        "managed-postgres.runtime.path", "runtime/postgres-16.4"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.path");
    }

    @Test
    void unknownRuntimeSourceIsRejectedBeforeLifecycleStart() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(
                        environment(Map.of("managed-postgres.runtime.source", "container"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("runtime.source");
    }

    @Test
    void clusterPasswordIsSecretAndNeverAppearsInToString() {
        final ManagedPostgresSpringProperties properties = ManagedPostgresSpringProperties.from(environment(
                Map.of("managed-postgres.cluster.owner", "app", "managed-postgres.cluster.password", RAW_PASSWORD)));

        final Optional<Secret> password = properties.cluster().password();

        assertThat(password).contains(Secret.of(RAW_PASSWORD));
        assertThat(properties.toString()).contains("REDACTED").doesNotContain(RAW_PASSWORD);
    }

    @Test
    void blankNameFailsBeforeLifecycleStart() {
        assertBlankPropertyFails("managed-postgres.name", "name");
    }

    @Test
    void blankPostgreSqlVersionFailsBeforeLifecycleStart() {
        assertBlankPropertyFails("managed-postgres.version", "version");
    }

    @Test
    void blankStoragePathFailsBeforeLifecycleStart() {
        assertBlankPropertyFails("managed-postgres.storage.path", "storage.path");
    }

    @Test
    void blankClusterDatabaseFailsBeforeLifecycleStart() {
        assertBlankPropertyFails("managed-postgres.cluster.database", "cluster.database");
    }

    @Test
    void blankClusterOwnerFailsBeforeLifecycleStart() {
        assertBlankPropertyFails("managed-postgres.cluster.owner", "cluster.owner");
    }

    @Test
    void blankClusterPasswordFailsBeforeLifecycleStart() {
        assertBlankPropertyFails("managed-postgres.cluster.password", "cluster.password");
    }

    @Test
    void clusterOwnerAndPasswordMustBeConfiguredTogether() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(
                        environment(Map.of("managed-postgres.cluster.owner", "app"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("cluster.owner")
                .hasMessageContaining("cluster.password");
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(
                        environment(Map.of("managed-postgres.cluster.password", RAW_PASSWORD))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("cluster.owner")
                .hasMessageContaining("cluster.password");
    }

    @Test
    void invalidPathPropertyFailsBeforeLifecycleStart() {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(
                        environment(Map.of("managed-postgres.storage.path", "bad\u0000path"))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("storage.path")
                .hasCauseInstanceOf(java.nio.file.InvalidPathException.class);
    }

    private static void assertBlankPropertyFails(final String propertyName, final String messagePart) {
        assertThatThrownBy(() -> ManagedPostgresSpringProperties.from(environment(Map.of(propertyName, "  "))))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining(messagePart);
    }
}
