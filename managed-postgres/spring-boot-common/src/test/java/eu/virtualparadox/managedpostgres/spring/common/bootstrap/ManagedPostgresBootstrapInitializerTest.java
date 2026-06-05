package eu.virtualparadox.managedpostgres.spring.common.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

final class ManagedPostgresBootstrapInitializerTest {

    ManagedPostgresBootstrapInitializerTest() {}

    @Test
    void initializeRegistersBootstrapContextBeanWhenMissing() {
        final ManagedPostgresBootstrapContext bootstrapContext = ManagedPostgresBootstrapContext.current();
        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            new ManagedPostgresBootstrapInitializer(bootstrapContext).initialize(applicationContext);

            assertThat(applicationContext
                            .getBeanFactory()
                            .containsBeanDefinition(ManagedPostgresBootstrapContext.BEAN_NAME))
                    .isTrue();
        }
    }

    @Test
    void initializeReplacesExistingBootstrapContextBeanDefinition() {
        final ManagedPostgresBootstrapContext bootstrapContext = ManagedPostgresBootstrapContext.current();
        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            applicationContext.registerBeanDefinition(
                    ManagedPostgresBootstrapContext.BEAN_NAME,
                    new org.springframework.beans.factory.support.RootBeanDefinition(String.class));

            new ManagedPostgresBootstrapInitializer(bootstrapContext).initialize(applicationContext);

            assertThat(applicationContext
                            .getBeanFactory()
                            .getBeanDefinition(ManagedPostgresBootstrapContext.BEAN_NAME)
                            .getBeanClassName())
                    .isEqualTo(ManagedPostgresBootstrapContext.class.getName());
        }
    }
}
