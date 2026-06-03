package eu.virtualparadox.managedpostgres.scenario.spring.boot4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spring.boot4.health.ManagedPostgresHealthIndicator;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

final class FakeRuntimeSpringBoot4IT {

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeSpringBoot4IT() {}

    @Test
    void springApplicationStartsFakeRuntimeAndInjectsDatasourceBeforeApplicationBeans() throws IOException {
        final SpringScenario scenario = SpringScenario.create(temporaryDirectory, generatedSecretValue());

        final ConfigurableApplicationContext context = runContext(scenario.properties());
        final Path callLog = scenario.callLog();
        try {
            final DatasourceSnapshot snapshot = context.getBean(DatasourceSnapshot.class);
            final PostgresConnectionInfo connectionInfo = context.getBean(PostgresConnectionInfo.class);

            assertThat(snapshot.url()).contains(jdbcUrl(connectionInfo));
            assertThat(snapshot.username()).contains(connectionInfo.username());
            assertThat(snapshot.password()).contains(scenario.rawSecretValue());
            assertThat(snapshot.connectionInfo()).isSameAs(connectionInfo);

            final String healthDetails = context.getBean(ManagedPostgresHealthIndicator.class)
                    .health()
                    .getDetails()
                    .toString();
            assertThat(healthDetails)
                    .doesNotContain(scenario.rawSecretValue())
                    .doesNotContain("Secret")
                    .doesNotContain("PGPASSWORD");
        } finally {
            context.close();
        }

        assertThat(Files.readString(callLog)).contains("stop");
    }

    @Test
    void existingDatasourceUrlFailsUnlessOverrideIsExplicit() throws IOException {
        final SpringScenario failingScenario =
                SpringScenario.create(temporaryDirectory.resolve("failing"), generatedSecretValue());
        final Map<String, Object> failingProperties = failingScenario.properties();
        failingProperties.put("spring.datasource.url", "jdbc:postgresql://external:5432/prod");

        assertThatThrownBy(() -> runContext(failingProperties))
                .hasMessageContaining("spring.datasource.url")
                .hasMessageNotContaining(failingScenario.rawSecretValue());

        final SpringScenario overrideScenario =
                SpringScenario.create(temporaryDirectory.resolve("override"), generatedSecretValue());
        final Map<String, Object> overrideProperties = overrideScenario.properties();
        overrideProperties.put("spring.datasource.url", "jdbc:postgresql://external:5432/prod");
        overrideProperties.put("managed-postgres.datasource.override-existing", "true");

        try (ConfigurableApplicationContext context = runContext(overrideProperties)) {
            final DatasourceSnapshot snapshot = context.getBean(DatasourceSnapshot.class);
            final PostgresConnectionInfo connectionInfo = context.getBean(PostgresConnectionInfo.class);

            assertThat(snapshot.url()).contains(jdbcUrl(connectionInfo));
        }
    }

    @Test
    void datasourceDisabledStartsFakeRuntimeAndExposesBeansWithoutDatasourceInjection() throws IOException {
        final SpringScenario scenario = SpringScenario.create(temporaryDirectory, generatedSecretValue());
        final Map<String, Object> properties = scenario.properties();
        properties.put("managed-postgres.datasource.enabled", "false");

        try (ConfigurableApplicationContext context = runContext(properties)) {
            final DatasourceSnapshot snapshot = context.getBean(DatasourceSnapshot.class);

            assertThat(context.getBean(RunningPostgres.class)).isNotNull();
            assertThat(context.getBean(PostgresConnectionInfo.class)).isNotNull();
            assertThat(snapshot.url()).isEmpty();
            assertThat(snapshot.username()).isEmpty();
            assertThat(snapshot.password()).isEmpty();
        }
    }

    private static ConfigurableApplicationContext runContext(final Map<String, Object> properties) {
        return new SpringApplicationBuilder(SpringScenarioApplication.class)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .run();
    }

    private static String jdbcUrl(final PostgresConnectionInfo connectionInfo) {
        return "jdbc:postgresql://%s:%d/%s"
                .formatted(connectionInfo.host(), connectionInfo.port(), connectionInfo.database());
    }

    private static String generatedSecretValue() {
        return Secret.random().reveal();
    }

    private record DatasourceSnapshot(
            Optional<String> url,
            Optional<String> username,
            Optional<String> password,
            PostgresConnectionInfo connectionInfo) {}

    private record SpringScenario(Path callLog, String rawSecretValue, FakePostgresRuntime runtime, Path storageRoot) {

        private static SpringScenario create(final Path root, final String rawSecretValue) throws IOException {
            final Path callLog = root.resolve("pg_ctl-calls.log");
            final FakePostgresRuntime runtime =
                    FakePostgresRuntime.create(root.resolve("runtime"), ScenarioShell.recordingPgCtl(callLog));

            return new SpringScenario(callLog, rawSecretValue, runtime, root.resolve("cluster"));
        }

        private Map<String, Object> properties() {
            final Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("managed-postgres.enabled", "true");
            properties.put("managed-postgres.name", "spring-db");
            properties.put("managed-postgres.version", "16.4");
            properties.put("managed-postgres.storage.path", storageRoot.toString());
            properties.put("managed-postgres.runtime.source", "existing");
            properties.put(
                    "managed-postgres.runtime.path", runtime.runtimeDirectory().toString());
            properties.put("managed-postgres.cluster.database", "app");
            properties.put("managed-postgres.cluster.owner", "app");
            properties.put("managed-postgres.cluster.password", rawSecretValue);

            return properties;
        }
    }

    @SpringBootApplication
    static class SpringScenarioApplication {

        SpringScenarioApplication() {}

        @Bean
        DatasourceSnapshot datasourceSnapshot(
                final Environment environment, final PostgresConnectionInfo connectionInfo) {
            return new DatasourceSnapshot(
                    Optional.ofNullable(environment.getProperty("spring.datasource.url")),
                    Optional.ofNullable(environment.getProperty("spring.datasource.username")),
                    Optional.ofNullable(environment.getProperty("spring.datasource.password")),
                    connectionInfo);
        }
    }
}
