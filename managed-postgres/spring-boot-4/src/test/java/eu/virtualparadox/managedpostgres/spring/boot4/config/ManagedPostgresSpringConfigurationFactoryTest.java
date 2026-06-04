package eu.virtualparadox.managedpostgres.spring.boot4.config;

import static eu.virtualparadox.managedpostgres.spring.boot4.config.SpringEnvironmentFixture.environment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresSpringConfigurationFactoryTest {

    ManagedPostgresSpringConfigurationFactoryTest() {}

    @Test
    void disabledPropertiesAreRejectedByFactory() {
        final ManagedPostgresSpringConfigurationFactory factory = new ManagedPostgresSpringConfigurationFactory();
        final ManagedPostgresSpringProperties properties = ManagedPostgresSpringProperties.from(environment(Map.of()));

        assertThatThrownBy(() -> factory.create(properties))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("enabled");
    }

    @Test
    void systemRuntimeMapsToCoreRuntimeSource() {
        final FactoryFixture fixture = FactoryFixture.create();
        final ManagedPostgresSpringProperties properties =
                properties(Map.of("managed-postgres.runtime.source", "system"));

        try (ManagedPostgres postgres = fixture.factory().create(properties)) {
            assertThat(postgres).isSameAs(fixture.postgres());
        }
        verify(fixture.builder()).runtime(RuntimeSource.system());
    }

    @Test
    void existingRuntimeMapsToCoreRuntimeSource() {
        final FactoryFixture fixture = FactoryFixture.create();
        final Path runtimePath = Path.of("runtime/postgres-16.4");
        final ManagedPostgresSpringProperties properties = properties(Map.of(
                "managed-postgres.runtime.source",
                "existing",
                "managed-postgres.runtime.path",
                runtimePath.toString()));

        fixture.factory().create(properties);

        verify(fixture.builder()).runtime(RuntimeSource.existing(runtimePath));
    }

    @Test
    void classpathRuntimeMapsToCoreRuntimeSource() {
        final FactoryFixture fixture = FactoryFixture.create();
        final String checksum = "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        final ManagedPostgresSpringProperties properties = properties(Map.of(
                "managed-postgres.runtime.source", "classpath",
                "managed-postgres.runtime.resource", "/postgres-runtime.zip",
                "managed-postgres.runtime.checksum", checksum,
                "managed-postgres.runtime.cache", ".local/runtime-cache"));

        fixture.factory().create(properties);

        verify(fixture.builder())
                .runtime(RuntimeSource.classpath("/postgres-runtime.zip", runtime -> runtime.checksum(checksum)
                        .cache(RuntimeCache.projectLocal(Path.of(".local/runtime-cache")))));
    }

    @Test
    void datasourceDisabledStillBuildsManagedPostgresConfiguration() {
        final FactoryFixture fixture = FactoryFixture.create();
        final ManagedPostgresSpringProperties properties =
                properties(Map.of("managed-postgres.datasource.enabled", "false"));

        try (ManagedPostgres postgres = fixture.factory().create(properties)) {
            assertThat(postgres).isSameAs(fixture.postgres());
        }
        verify(fixture.builder()).build();
        verify(fixture.builder(), never()).configuration(any(PostgresConfiguration.class));
    }

    @Test
    void factoryMapsCoreNameVersionAndStorage() {
        final FactoryFixture fixture = FactoryFixture.create();
        final ManagedPostgresSpringProperties properties = properties(Map.of(
                "managed-postgres.name", "app-db",
                "managed-postgres.version", "16.5",
                "managed-postgres.storage.path", ".local/app-db"));

        fixture.factory().create(properties);

        verify(fixture.builder()).name("app-db");
        verify(fixture.builder()).version("16.5");
        verify(fixture.builder()).storageProjectLocal(Path.of(".local/app-db"));
    }

    @Test
    void factoryMapsPostgresConfigurationPresetAndOverrides() {
        final FactoryFixture fixture = FactoryFixture.create();
        final ManagedPostgresSpringProperties properties = properties(Map.of(
                "managed-postgres.configuration.preset", "small",
                "managed-postgres.configuration.max-connections", "48",
                "managed-postgres.configuration.shared-buffers", "192MB",
                "managed-postgres.configuration.temp-buffers", "24MB",
                "managed-postgres.configuration.statement-timeout-seconds", "40"));

        fixture.factory().create(properties);

        verify(fixture.builder())
                .configuration(Resources.small()
                        .maxConnections(48)
                        .sharedBuffers("192MB")
                        .tempBuffers("24MB")
                        .statementTimeoutSeconds(40));
    }

    @Test
    void factoryMapsPostgresConfigurationOverridesWithoutPreset() {
        final FactoryFixture fixture = FactoryFixture.create();
        final ManagedPostgresSpringProperties properties = properties(Map.of(
                "managed-postgres.configuration.max-connections", "18",
                "managed-postgres.configuration.shared-buffers", "72MB",
                "managed-postgres.configuration.statement-timeout-seconds", "9"));

        fixture.factory().create(properties);

        verify(fixture.builder())
                .configuration(PostgresConfiguration.defaults()
                        .maxConnections(18)
                        .sharedBuffers("72MB")
                        .statementTimeoutSeconds(9));
    }

    @Test
    void factoryRejectsUnknownPostgresConfigurationPreset() {
        final FactoryFixture fixture = FactoryFixture.create();
        final ManagedPostgresSpringProperties properties =
                properties(Map.of("managed-postgres.configuration.preset", "huge"));

        assertThatThrownBy(() -> fixture.factory().create(properties))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("configuration.preset");
    }

    private static FactoryFixture createFixture(
            final ManagedPostgresConfigurer builder, final ManagedPostgres postgres) {
        when(builder.name(any())).thenReturn(builder);
        when(builder.version(any())).thenReturn(builder);
        when(builder.storageProjectLocal(any(Path.class))).thenReturn(builder);
        when(builder.runtime(any())).thenReturn(builder);
        when(builder.configuration(any(PostgresConfiguration.class))).thenReturn(builder);
        when(builder.credentials(anyString(), any(Secret.class))).thenReturn(builder);
        when(builder.network(any(Network.class))).thenReturn(builder);
        when(builder.cluster(any(ClusterBootstrap.class))).thenReturn(builder);
        when(builder.attachPolicy(any())).thenReturn(builder);
        when(builder.stopPolicy(any())).thenReturn(builder);
        when(builder.build()).thenReturn(postgres);

        return new FactoryFixture(new ManagedPostgresSpringConfigurationFactory(() -> builder), builder, postgres);
    }

    private static ManagedPostgresSpringProperties properties(final Map<String, Object> properties) {
        final java.util.HashMap<String, Object> enabledProperties = new java.util.HashMap<>(properties);
        enabledProperties.put("managed-postgres.enabled", "true");

        return ManagedPostgresSpringProperties.from(environment(enabledProperties));
    }

    private record FactoryFixture(
            ManagedPostgresSpringConfigurationFactory factory,
            ManagedPostgresConfigurer builder,
            ManagedPostgres postgres) {

        private static FactoryFixture create() {
            return createFixture(mock(ManagedPostgresConfigurer.class), mock(ManagedPostgres.class));
        }
    }
}
