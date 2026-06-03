package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.DownloadedRuntimeDsl;
import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.logging.PostgresLogs;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import java.net.URI;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Coordinates abstract managed postgres builder behavior for managed PostgreSQL internals.
 */
@SuppressWarnings({
    // The immutable builder intentionally fronts the complete public configuration contract in one place.
    "PMD.CouplingBetweenObjects"
})
public abstract class AbstractManagedPostgresBuilder implements ManagedPostgresBuilder {

    private final ManagedPostgresConfiguration configuration;

    /**
     * Creates a AbstractManagedPostgresBuilder instance.
     *
     * @param configuration configuration value
     */
    public AbstractManagedPostgresBuilder(final ManagedPostgresConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder name(final String name) {
        return copy(configuration.withName(name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder version(final String postgresqlVersion) {
        return copy(configuration.withPostgresqlVersion(postgresqlVersion));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder storage(final Storage storage) {
        return copy(configuration.withStorage(storage));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder runtime(final RuntimeSource runtimeSource) {
        return copy(configuration.withRuntimeSource(runtimeSource));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final DownloadedRuntimeDsl withDownloadedRuntime() {
        return new DownloadedRuntimeStep(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder withSystemRuntime() {
        return runtimeSource(RuntimeSource.system());
    }

    final ManagedPostgresBuilder runtimeSource(final RuntimeSource source) {
        return copy(configuration.withRuntimeSource(Objects.requireNonNull(source, "source")));
    }

    /**
     * Fluent downloaded-runtime configuration step bound to a builder instance.
     */
    private static final class DownloadedRuntimeStep implements DownloadedRuntimeDsl {

        private static final String GITHUB_RELEASE_SCHEME = "github-release";

        private final ManagedPostgresBuilder builder;

        private DownloadedRuntimeStep(final ManagedPostgresBuilder builder) {
            this.builder = Objects.requireNonNull(builder, "builder");
        }

        @Override
        public ManagedPostgresBuilder fromOfficialRepository() {
            return builder.runtime(
                    RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.official())));
        }

        @Override
        public ManagedPostgresBuilder fromGitHubRelease(final String owner, final String repo) {
            final URI repository = URI.create(GITHUB_RELEASE_SCHEME + "://" + Objects.requireNonNull(owner, "owner")
                    + "/" + Objects.requireNonNull(repo, "repo"));
            return builder.runtime(
                    RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.custom(repository))));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder credentials(final Credentials credentials) {
        return copy(configuration.withCredentials(credentials));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder configuration(final PostgresConfiguration postgresConfiguration) {
        return copy(configuration.withPostgresConfiguration(postgresConfiguration));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder configuration(final UnaryOperator<PostgresConfiguration> customizer) {
        final UnaryOperator<PostgresConfiguration> checkedCustomizer = Objects.requireNonNull(customizer, "customizer");
        final PostgresConfiguration postgresConfiguration = Objects.requireNonNull(
                checkedCustomizer.apply(configuration.postgresConfiguration()), "postgresConfiguration");

        return copy(configuration.withPostgresConfiguration(postgresConfiguration));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder reuseExisting() {
        return copy(configuration
                .withAttachPolicy(AttachPolicy.ATTACH_IF_COMPATIBLE)
                .withStopPolicy(StopPolicy.KEEP_RUNNING));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder attachPolicy(final AttachPolicy attachPolicy) {
        return copy(configuration.withAttachPolicy(attachPolicy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder stopPolicy(final StopPolicy stopPolicy) {
        return copy(configuration.withStopPolicy(stopPolicy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder cleanupPolicy(final CleanupPolicy cleanupPolicy) {
        return copy(configuration.withCleanupPolicy(cleanupPolicy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder logs(final UnaryOperator<PostgresLogs> customizer) {
        final UnaryOperator<PostgresLogs> checkedCustomizer = Objects.requireNonNull(customizer, "customizer");
        final PostgresLogs logs = Objects.requireNonNull(checkedCustomizer.apply(configuration.logs()), "logs");

        return copy(configuration.withLogs(logs));
    }

    /**
     * Returns the configuration result.
     *
     * @return configuration result
     */
    public final ManagedPostgresConfiguration configuration() {
        return configuration;
    }

    /**
     * Returns the copy result.
     *
     * @param updatedConfiguration updated configuration value
     * @return copy result
     */
    public abstract ManagedPostgresBuilder copy(ManagedPostgresConfiguration updatedConfiguration);
}
