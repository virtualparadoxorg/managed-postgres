package eu.virtualparadox.managedpostgres.spring.boot3.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresBootstrapContextTestSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public final class ManagedPostgresAutoConfigurationTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String AUTO_CONFIGURATION_CLASS = ManagedPostgresAutoConfiguration.class.getName();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ManagedPostgresAutoConfiguration.class));

    ManagedPostgresAutoConfigurationTest() {}

    @AfterEach
    void resetBootstrapContext() {
        ManagedPostgresBootstrapContextTestSupport.reset();
    }

    @Test
    void enabledManagedPostgresExposesRunningPostgresBean() {
        final BootstrapFixture fixture = BootstrapFixture.create();
        ManagedPostgresBootstrapContextTestSupport.store(fixture.postgres(), fixture.runningPostgres());

        contextRunner.withPropertyValues("managed-postgres.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(RunningPostgres.class);
            assertThat(context.getBean(RunningPostgres.class)).isSameAs(fixture.runningPostgres());
        });
    }

    @Test
    void enabledManagedPostgresExposesConnectionInfoBean() {
        final BootstrapFixture fixture = BootstrapFixture.create();
        ManagedPostgresBootstrapContextTestSupport.store(fixture.postgres(), fixture.runningPostgres());

        contextRunner.withPropertyValues("managed-postgres.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(PostgresConnectionInfo.class);
            assertThat(context.getBean(PostgresConnectionInfo.class)).isSameAs(fixture.connectionInfo());
        });
    }

    @Test
    void managedPostgresBeanReusesBootstrapHandleWithoutStartingAgain() {
        final BootstrapFixture fixture = BootstrapFixture.create();
        ManagedPostgresBootstrapContextTestSupport.store(fixture.postgres(), fixture.runningPostgres());

        contextRunner.withPropertyValues("managed-postgres.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(ManagedPostgres.class);
            assertThat(context.getBean(ManagedPostgres.class)).isSameAs(fixture.postgres());
        });
        verify(fixture.postgres(), never()).start();
    }

    @Test
    void contextCloseClosesRunningPostgresHandle() {
        final BootstrapFixture fixture = BootstrapFixture.create();
        ManagedPostgresBootstrapContextTestSupport.store(fixture.postgres(), fixture.runningPostgres());

        contextRunner.withPropertyValues("managed-postgres.enabled=true").run(context -> assertThat(context)
                .hasSingleBean(RunningPostgres.class));

        verify(fixture.runningPostgres()).close();
    }

    @Test
    void disabledManagedPostgresDoesNotExposeManagedBeans() {
        final BootstrapFixture fixture = BootstrapFixture.create();
        ManagedPostgresBootstrapContextTestSupport.store(fixture.postgres(), fixture.runningPostgres());

        contextRunner.withPropertyValues("managed-postgres.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ManagedPostgres.class);
            assertThat(context).doesNotHaveBean(RunningPostgres.class);
            assertThat(context).doesNotHaveBean(PostgresConnectionInfo.class);
        });
    }

    @Test
    void autoConfigurationImportsRegistersBoot3AutoConfiguration() throws IOException {
        assertThat(autoConfigurationImports()).contains(AUTO_CONFIGURATION_CLASS);
    }

    private static String autoConfigurationImports() throws IOException {
        final ClassLoader classLoader = ManagedPostgresAutoConfigurationTest.class.getClassLoader();
        final InputStream resourceStream = Objects.requireNonNull(
                classLoader.getResourceAsStream(AUTO_CONFIGURATION_IMPORTS), AUTO_CONFIGURATION_IMPORTS);

        try (InputStream stream = resourceStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class BootstrapFixture {

        private final ManagedPostgres postgres = mock(ManagedPostgres.class);
        private final RunningPostgres runningPostgres = mock(RunningPostgres.class);
        private final PostgresConnectionInfo connectionInfo =
                new PostgresConnectionInfo("127.0.0.1", 15432, "app", "app", Secret.of("boot-secret"));

        private BootstrapFixture() {
            when(runningPostgres.connectionInfo()).thenReturn(connectionInfo);
        }

        private static BootstrapFixture create() {
            return new BootstrapFixture();
        }

        private ManagedPostgres postgres() {
            return postgres;
        }

        private RunningPostgres runningPostgres() {
            return runningPostgres;
        }

        private PostgresConnectionInfo connectionInfo() {
            return connectionInfo;
        }
    }
}
