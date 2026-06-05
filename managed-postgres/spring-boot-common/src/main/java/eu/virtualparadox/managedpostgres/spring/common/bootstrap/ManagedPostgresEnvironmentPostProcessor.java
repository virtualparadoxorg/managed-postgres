package eu.virtualparadox.managedpostgres.spring.common.bootstrap;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.spring.common.config.ManagedPostgresSpringConfigurationFactory;
import eu.virtualparadox.managedpostgres.spring.common.config.ManagedPostgresSpringException;
import eu.virtualparadox.managedpostgres.spring.common.config.ManagedPostgresSpringProperties;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

/**
 * Starts managed PostgreSQL early enough for Spring Boot datasource auto-configuration.
 *
 * <p>This type holds the version-agnostic post-processing logic but deliberately does not implement
 * Spring Boot's {@code EnvironmentPostProcessor}, whose package and type identity differ between
 * Spring Boot 3 and 4. Each starter registers a thin version-specific adapter that implements the
 * applicable {@code EnvironmentPostProcessor} contract and delegates to this component.
 */
public final class ManagedPostgresEnvironmentPostProcessor implements Ordered {

    private static final int ORDER = HIGHEST_PRECEDENCE + 100;
    private static final String DATASOURCE_URL = "spring.datasource.url";
    private static final String PROPERTY_SOURCE_NAME = "managed-postgres-datasource";

    private final Function<ManagedPostgresSpringProperties, ManagedPostgres> managedPostgresFactory;

    /**
     * Creates an environment post-processor backed by the default managed PostgreSQL configuration factory.
     */
    public ManagedPostgresEnvironmentPostProcessor() {
        this(new ManagedPostgresSpringConfigurationFactory()::create);
    }

    ManagedPostgresEnvironmentPostProcessor(
            final Function<ManagedPostgresSpringProperties, ManagedPostgres> managedPostgresFactory) {
        this.managedPostgresFactory = Objects.requireNonNull(managedPostgresFactory, "managedPostgresFactory");
    }

    /**
     * Starts managed PostgreSQL and publishes datasource properties when the integration is enabled.
     *
     * @param environment Spring Boot environment
     * @param application Spring Boot application
     */
    public void postProcessEnvironment(final ConfigurableEnvironment environment, final SpringApplication application) {
        final ConfigurableEnvironment checkedEnvironment = Objects.requireNonNull(environment, "environment");
        final SpringApplication checkedApplication = Objects.requireNonNull(application, "application");
        final ManagedPostgresSpringProperties properties = ManagedPostgresSpringProperties.from(checkedEnvironment);
        if (properties.enabled()) {
            startAndPublishDatasource(checkedEnvironment, checkedApplication, properties);
        }
    }

    /**
     * Returns this post-processor order.
     *
     * @return post-processor order
     */
    @Override
    public int getOrder() {
        return ORDER;
    }

    private void startAndPublishDatasource(
            final ConfigurableEnvironment environment,
            final SpringApplication application,
            final ManagedPostgresSpringProperties properties) {
        try {
            validateDatasourceOverride(environment, properties);
            final ManagedPostgresBootstrapContext bootstrapContext =
                    new ManagedPostgresBootstrapStarter().start(managedPostgresFactory.apply(properties));
            storeAndPublishDatasource(environment, application, properties, bootstrapContext);
        } catch (final ManagedPostgresSpringException exception) {
            throw exception;
        } catch (final ManagedPostgresException | IllegalArgumentException | IllegalStateException exception) {
            throw managedSpringException(exception, properties);
        }
    }

    private static void storeAndPublishDatasource(
            final ConfigurableEnvironment environment,
            final SpringApplication application,
            final ManagedPostgresSpringProperties properties,
            final ManagedPostgresBootstrapContext bootstrapContext) {
        ManagedPostgresBootstrapContext.store(bootstrapContext);
        application.addInitializers(new ManagedPostgresBootstrapInitializer(bootstrapContext));
        publishDatasourceProperties(environment, properties, requireRunningPostgres(bootstrapContext));
    }

    private static void validateDatasourceOverride(
            final ConfigurableEnvironment environment, final ManagedPostgresSpringProperties properties) {
        if (properties.datasource().enabled()
                && !properties.datasource().overrideExisting()
                && environment.containsProperty(DATASOURCE_URL)) {
            throw new ManagedPostgresSpringException(
                    "spring.datasource.url already exists; set managed-postgres.datasource.override-existing=true to replace it");
        }
    }

    private static void publishDatasourceProperties(
            final ConfigurableEnvironment environment,
            final ManagedPostgresSpringProperties properties,
            final RunningPostgres runningPostgres) {
        if (properties.datasource().enabled()) {
            final ManagedPostgresDatasourceProperties datasourceProperties =
                    ManagedPostgresDatasourceProperties.from(runningPostgres.connectionInfo());
            new ManagedPostgresPropertySourcePublisher(propertySourcesOf(environment))
                    .addFirst(PROPERTY_SOURCE_NAME, datasourceProperties.asMap());
        }
    }

    @SuppressWarnings("PMD.LawOfDemeter")
    private static MutablePropertySources propertySourcesOf(final ConfigurableEnvironment environment) {
        return environment.getPropertySources();
    }

    private static ManagedPostgresSpringException managedSpringException(
            final Exception exception, final ManagedPostgresSpringProperties properties) {
        final String message =
                Objects.toString(exception.getMessage(), exception.getClass().getName());

        return new ManagedPostgresSpringException(
                "Failed to start managed PostgreSQL: " + redacted(message, properties));
    }

    private static String redacted(final String message, final ManagedPostgresSpringProperties properties) {
        final String redactedMessage;
        if (properties.cluster().password().isPresent()) {
            redactedMessage = message.replace(
                    properties.cluster().password().orElseThrow().reveal(), "REDACTED");
        } else {
            redactedMessage = message;
        }

        return redactedMessage;
    }

    private static RunningPostgres requireRunningPostgres(final ManagedPostgresBootstrapContext bootstrapContext) {
        return Objects.requireNonNull(bootstrapContext, "bootstrapContext")
                .runningPostgres()
                .orElseThrow(() -> new ManagedPostgresSpringException(
                        "Running PostgreSQL handle is missing from Spring Boot bootstrap context"));
    }
}
