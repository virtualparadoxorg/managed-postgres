package eu.virtualparadox.managedpostgres.spring.common.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spring.common.config.ManagedPostgresSpringException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

public final class ManagedPostgresEnvironmentPostProcessorTest {

    private static final String RAW_PASSWORD = "boot-secret";

    ManagedPostgresEnvironmentPostProcessorTest() {}

    @AfterEach
    void resetBootstrapContext() {
        ManagedPostgresBootstrapContext.reset();
    }

    @Test
    void disabledManagedPostgresDoesNotStartAndDoesNotInjectDatasourceProperties() {
        final PostProcessorFixture fixture = PostProcessorFixture.create();
        final MockEnvironment environment = environment(Map.of());
        final ManagedPostgresEnvironmentPostProcessor postProcessor =
                new ManagedPostgresEnvironmentPostProcessor(properties -> fixture.postgres());

        postProcessor.postProcessEnvironment(environment, application());

        verify(fixture.postgres(), never()).start();
        assertThat(environment.getProperty("spring.datasource.url")).isNull();
        assertThat(ManagedPostgresBootstrapContext.current().managedPostgres()).isEmpty();
        assertThat(ManagedPostgresBootstrapContext.current().runningPostgres()).isEmpty();
    }

    @Test
    void enabledManagedPostgresStartsAndStoresRunningHandle() {
        final PostProcessorFixture fixture = PostProcessorFixture.create();
        final MockEnvironment environment = environment(Map.of("managed-postgres.enabled", "true"));
        final ManagedPostgresEnvironmentPostProcessor postProcessor =
                new ManagedPostgresEnvironmentPostProcessor(properties -> fixture.postgres());

        postProcessor.postProcessEnvironment(environment, application());

        assertThat(ManagedPostgresBootstrapContext.current().managedPostgres()).contains(fixture.postgres());
        assertThat(ManagedPostgresBootstrapContext.current().runningPostgres()).contains(fixture.runningPostgres());
        assertThat(ManagedPostgresBootstrapContext.current()
                        .metrics()
                        .orElseThrow()
                        .startupDuration())
                .isGreaterThanOrEqualTo(Duration.ZERO);
        assertThat(ManagedPostgresBootstrapContext.current()
                        .metrics()
                        .orElseThrow()
                        .installDuration())
                .isZero();
        assertThat(ManagedPostgresBootstrapContext.current()
                        .metrics()
                        .orElseThrow()
                        .healthcheckFailures())
                .isZero();
    }

    @Test
    void datasourceEnabledInjectsJdbcPropertiesAtHighestPrecedence() {
        final MockEnvironment environment = environment(Map.of(
                "managed-postgres.enabled", "true",
                "managed-postgres.datasource.override-existing", "true",
                "spring.datasource.url", "jdbc:postgresql://old:5432/old"));
        final ManagedPostgresEnvironmentPostProcessor postProcessor = postProcessorWithRunningPostgres();

        postProcessor.postProcessEnvironment(environment, application());

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://127.0.0.1:15432/app");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("app");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo(RAW_PASSWORD);
    }

    @Test
    void existingDatasourceUrlFailsByDefaultBeforeStartingPostgres() {
        final PostProcessorFixture fixture = PostProcessorFixture.create();
        final MockEnvironment environment = environment(Map.of(
                "managed-postgres.enabled", "true",
                "spring.datasource.url", "jdbc:postgresql://old:5432/old"));
        final ManagedPostgresEnvironmentPostProcessor postProcessor =
                new ManagedPostgresEnvironmentPostProcessor(properties -> fixture.postgres());

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, application()))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("spring.datasource.url");
        verify(fixture.postgres(), never()).start();
    }

    @Test
    void datasourceOverrideExistingPermitsReplacingExistingDatasourceUrl() {
        final MockEnvironment environment = environment(Map.of(
                "managed-postgres.enabled", "true",
                "managed-postgres.datasource.override-existing", "true",
                "spring.datasource.url", "jdbc:postgresql://old:5432/old"));
        final ManagedPostgresEnvironmentPostProcessor postProcessor = postProcessorWithRunningPostgres();

        postProcessor.postProcessEnvironment(environment, application());

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://127.0.0.1:15432/app");
    }

    @Test
    void datasourceDisabledStartsPostgresWithoutInjectingDatasourceProperties() {
        final MockEnvironment environment = environment(Map.of(
                "managed-postgres.enabled", "true",
                "managed-postgres.datasource.enabled", "false"));
        final ManagedPostgresEnvironmentPostProcessor postProcessor = postProcessorWithRunningPostgres();

        postProcessor.postProcessEnvironment(environment, application());

        assertThat(ManagedPostgresBootstrapContext.current().runningPostgres()).isPresent();
        assertThat(environment.getProperty("spring.datasource.url")).isNull();
        assertThat(environment.getProperty("spring.datasource.username")).isNull();
        assertThat(environment.getProperty("spring.datasource.password")).isNull();
    }

    @Test
    void startupErrorsAreManagedPostgresSpringExceptionsAndRedactSecrets() {
        final MockEnvironment environment = environment(Map.of(
                "managed-postgres.enabled", "true",
                "managed-postgres.cluster.owner", "app",
                "managed-postgres.cluster.password", RAW_PASSWORD));
        final ManagedPostgresEnvironmentPostProcessor postProcessor =
                new ManagedPostgresEnvironmentPostProcessor(properties -> {
                    throw new IllegalStateException("cannot start with " + RAW_PASSWORD);
                });

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, application()))
                .isInstanceOf(ManagedPostgresSpringException.class)
                .hasMessageContaining("REDACTED")
                .hasMessageNotContaining(RAW_PASSWORD);
    }

    private static ManagedPostgresEnvironmentPostProcessor postProcessorWithRunningPostgres() {
        final PostProcessorFixture fixture = PostProcessorFixture.create();

        return new ManagedPostgresEnvironmentPostProcessor(properties -> fixture.postgres());
    }

    private static MockEnvironment environment(final Map<String, String> properties) {
        final MockEnvironment environment = new MockEnvironment();
        properties.forEach(environment::setProperty);

        return environment;
    }

    private static SpringApplication application() {
        return new SpringApplication(ManagedPostgresEnvironmentPostProcessorTest.class);
    }

    private static final class PostProcessorFixture {

        private final ManagedPostgres postgres = mock(ManagedPostgres.class);
        private final RunningPostgres runningPostgres = mock(RunningPostgres.class);

        private PostProcessorFixture() {
            when(postgres.start()).thenReturn(runningPostgres);
            when(runningPostgres.connectionInfo())
                    .thenReturn(new PostgresConnectionInfo("127.0.0.1", 15432, "app", "app", Secret.of(RAW_PASSWORD)));
        }

        private static PostProcessorFixture create() {
            return new PostProcessorFixture();
        }

        private ManagedPostgres postgres() {
            return postgres;
        }

        private RunningPostgres runningPostgres() {
            return runningPostgres;
        }
    }
}
