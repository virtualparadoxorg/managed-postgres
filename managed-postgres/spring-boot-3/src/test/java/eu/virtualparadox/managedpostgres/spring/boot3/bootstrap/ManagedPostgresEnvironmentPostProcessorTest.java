package eu.virtualparadox.managedpostgres.spring.boot3.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

public final class ManagedPostgresEnvironmentPostProcessorTest {

    ManagedPostgresEnvironmentPostProcessorTest() {}

    @Test
    void implementsSpringBoot3EnvironmentPostProcessorContract() {
        assertThat(new ManagedPostgresEnvironmentPostProcessor())
                .isInstanceOf(org.springframework.boot.env.EnvironmentPostProcessor.class)
                .isInstanceOf(Ordered.class);
    }

    @Test
    void getOrderDelegatesToVersionAgnosticComponent() {
        final eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresEnvironmentPostProcessor
                delegate =
                        new eu.virtualparadox.managedpostgres.spring.common.bootstrap
                                .ManagedPostgresEnvironmentPostProcessor();
        final ManagedPostgresEnvironmentPostProcessor adapter = new ManagedPostgresEnvironmentPostProcessor(delegate);

        assertThat(adapter.getOrder()).isEqualTo(delegate.getOrder());
    }

    @Test
    void postProcessEnvironmentDelegatesAndIsNoOpWhenDisabled() {
        final ManagedPostgresEnvironmentPostProcessor adapter = new ManagedPostgresEnvironmentPostProcessor();
        final MockEnvironment environment = new MockEnvironment();

        adapter.postProcessEnvironment(
                environment, new SpringApplication(ManagedPostgresEnvironmentPostProcessorTest.class));

        assertThat(environment.getProperty("spring.datasource.url")).isNull();
    }
}
