package eu.virtualparadox.managedpostgres.spring.boot4.bootstrap;

import java.util.Objects;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Spring Boot 4 adapter that registers the version-agnostic managed PostgreSQL environment
 * post-processor.
 *
 * <p>Spring Boot 4's {@code org.springframework.boot.EnvironmentPostProcessor} differs in package
 * and type identity from the Spring Boot 3 contract, so the registration binding lives in the
 * version-specific starter while the post-processing logic stays shared in
 * {@code eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresEnvironmentPostProcessor}.
 */
public final class ManagedPostgresEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private final eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresEnvironmentPostProcessor
            delegate;

    /**
     * Creates an adapter backed by the default version-agnostic environment post-processor.
     */
    public ManagedPostgresEnvironmentPostProcessor() {
        this(new eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresEnvironmentPostProcessor());
    }

    ManagedPostgresEnvironmentPostProcessor(
            final eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresEnvironmentPostProcessor
                    delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * Delegates environment post-processing to the version-agnostic component.
     *
     * @param environment Spring Boot environment
     * @param application Spring Boot application
     */
    @Override
    public void postProcessEnvironment(final ConfigurableEnvironment environment, final SpringApplication application) {
        delegate.postProcessEnvironment(environment, application);
    }

    /**
     * Returns the version-agnostic post-processor order.
     *
     * @return post-processor order
     */
    @Override
    public int getOrder() {
        return delegate.getOrder();
    }
}
