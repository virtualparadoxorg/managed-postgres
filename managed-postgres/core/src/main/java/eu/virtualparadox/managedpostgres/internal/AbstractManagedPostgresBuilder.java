package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.dsl.DownloadedRuntimeDsl;
import eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Coordinates abstract managed postgres builder behavior for managed PostgreSQL internals.
 */
@SuppressWarnings({
    // The immutable builder intentionally fronts the complete public configuration contract in one place.
    "PMD.CouplingBetweenObjects",
    // The abstract builder mirrors the full interface contract; splitting it further adds no clarity.
    "PMD.TooManyMethods"
})
public abstract class AbstractManagedPostgresBuilder implements ManagedPostgresBuilder {

    private final ManagedPostgresConfiguration configuration;
    private final ManagedPostgresObservers observers;

    /**
     * Creates a AbstractManagedPostgresBuilder instance.
     *
     * @param configuration configuration value
     * @param observers startup observers value
     */
    public AbstractManagedPostgresBuilder(
            final ManagedPostgresConfiguration configuration, final ManagedPostgresObservers observers) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.observers = Objects.requireNonNull(observers, "observers");
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
    public final ManagedPostgresBuilder storageProjectLocal(final String path) {
        return copy(configuration.withStorage(Storage.projectLocal(Objects.requireNonNull(path, "path"))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder storageProjectLocal(final Path path) {
        return copy(configuration.withStorage(Storage.projectLocal(Objects.requireNonNull(path, "path"))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder temporaryStorage() {
        return copy(configuration.withStorage(Storage.temporary()));
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

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder withExistingRuntime(final Path runtimeDirectory) {
        return runtimeSource(RuntimeSource.existing(Objects.requireNonNull(runtimeDirectory, "runtimeDirectory")));
    }

    final ManagedPostgresBuilder runtimeSource(final RuntimeSource source) {
        return copy(configuration.withRuntimeSource(Objects.requireNonNull(source, "source")));
    }

    /**
     * Fluent downloaded-runtime configuration step bound to a builder instance.
     */
    private static final class DownloadedRuntimeStep implements DownloadedRuntimeDsl {

        private static final String GITHUB_RELEASE_SCHEME = "github-release";

        private final AbstractManagedPostgresBuilder builder;

        private DownloadedRuntimeStep(final AbstractManagedPostgresBuilder builder) {
            this.builder = Objects.requireNonNull(builder, "builder");
        }

        @Override
        public ManagedPostgresBuilder fromOfficialRepository() {
            return builder.runtimeSource(
                    RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.official())));
        }

        @Override
        public ManagedPostgresBuilder fromGitHubRelease(final String owner, final String repo) {
            final URI repository = URI.create(GITHUB_RELEASE_SCHEME + "://" + Objects.requireNonNull(owner, "owner")
                    + "/" + Objects.requireNonNull(repo, "repo"));
            return builder.runtimeSource(
                    RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.custom(repository))));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder credentials(final String username, final String password) {
        return credentials(username, Secret.of(Objects.requireNonNull(password, "password")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder credentials(final String username, final Secret password) {
        return copy(configuration.withCredentials(Credentials.of(
                Objects.requireNonNull(username, "username"), Objects.requireNonNull(password, "password"))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder generatedCredentials() {
        return copy(configuration.withCredentials(Credentials.generated()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder generatedPersistentCredentials() {
        return copy(configuration.withCredentials(Credentials.generatedPersistent()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ManagedPostgresBuilder trustLocalOnly() {
        return copy(configuration.withCredentials(Credentials.trustLocalOnly()));
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
    public final ManagedPostgresBuilder onProgress(final ManagedPostgresProgressListener listener) {
        return copyObservers(observers.withProgress(Objects.requireNonNull(listener, "listener")));
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
     * Returns the startup observers.
     *
     * @return startup observers
     */
    public final ManagedPostgresObservers observers() {
        return observers;
    }

    /**
     * Returns the copy result.
     *
     * @param updatedConfiguration updated configuration value
     * @return copy result
     */
    public abstract ManagedPostgresBuilder copy(ManagedPostgresConfiguration updatedConfiguration);

    /**
     * Rebuilds the concrete builder with new observers but the same configuration.
     *
     * @param updatedObservers updated observers value
     * @return copy result
     */
    public abstract ManagedPostgresBuilder copyObservers(ManagedPostgresObservers updatedObservers);
}
