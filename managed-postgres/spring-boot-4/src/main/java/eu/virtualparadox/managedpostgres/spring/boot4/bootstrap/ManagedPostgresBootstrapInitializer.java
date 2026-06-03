package eu.virtualparadox.managedpostgres.spring.boot4.bootstrap;

import java.util.Objects;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

final class ManagedPostgresBootstrapInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private final ManagedPostgresBootstrapContext bootstrapContext;

    ManagedPostgresBootstrapInitializer(final ManagedPostgresBootstrapContext bootstrapContext) {
        this.bootstrapContext = Objects.requireNonNull(bootstrapContext, "bootstrapContext");
    }

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        final ConfigurableApplicationContext checkedApplicationContext =
                Objects.requireNonNull(applicationContext, "applicationContext");
        final BeanDefinitionRegistry registry = registryOf(checkedApplicationContext);
        if (registry.containsBeanDefinition(ManagedPostgresBootstrapContext.BEAN_NAME)) {
            registry.removeBeanDefinition(ManagedPostgresBootstrapContext.BEAN_NAME);
        }
        registry.registerBeanDefinition(ManagedPostgresBootstrapContext.BEAN_NAME, bootstrapContextBeanDefinition());
    }

    private static BeanDefinitionRegistry registryOf(final ConfigurableApplicationContext applicationContext) {
        if (applicationContext.getBeanFactory() instanceof BeanDefinitionRegistry registry) {
            return registry;
        }

        throw new IllegalStateException("Spring application context does not support bean definition registration");
    }

    private RootBeanDefinition bootstrapContextBeanDefinition() {
        final RootBeanDefinition beanDefinition = new RootBeanDefinition(ManagedPostgresBootstrapContext.class);
        beanDefinition.setInstanceSupplier(() -> bootstrapContext);

        return beanDefinition;
    }
}
