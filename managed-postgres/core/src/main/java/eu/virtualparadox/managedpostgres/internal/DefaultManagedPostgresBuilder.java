package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.ClasspathRuntimeDsl;
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.ClasspathRuntime;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresMode;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Default immutable managed PostgreSQL builder.
 */
@SuppressWarnings({
    // The immutable builder intentionally fronts the complete public configuration contract in one place,
    // including the runtime sub-DSLs (downloaded/classpath), so it touches many configuration types.
    "PMD.CouplingBetweenObjects"
})
public final class DefaultManagedPostgresBuilder extends AbstractManagedPostgresBuilder implements ClasspathRuntimeDsl {

    private final ManagedPostgresMode mode;

    /**
     * Creates a default builder for the supplied mode.
     *
     * @param mode managed PostgreSQL mode
     */
    public DefaultManagedPostgresBuilder(final ManagedPostgresMode mode) {
        this(mode, DefaultManagedPostgresConfigurations.forMode(mode));
    }

    private DefaultManagedPostgresBuilder(
            final ManagedPostgresMode mode, final ManagedPostgresConfiguration configuration) {
        super(configuration);
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ManagedPostgres build() {
        return new ConfiguredManagedPostgres(configuration());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RunningPostgres start() {
        return build().start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder network(final UnaryOperator<Network> customizer) {
        final UnaryOperator<Network> checkedCustomizer = Objects.requireNonNull(customizer, "customizer");
        final Network network =
                Objects.requireNonNull(checkedCustomizer.apply(configuration().network()), "network");

        return copy(configuration().withNetwork(network));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder cluster(final UnaryOperator<ClusterBootstrap> customizer) {
        final UnaryOperator<ClusterBootstrap> checkedCustomizer = Objects.requireNonNull(customizer, "customizer");
        final ClusterBootstrap clusterBootstrap =
                Objects.requireNonNull(checkedCustomizer.apply(configuration().clusterBootstrap()), "clusterBootstrap");

        return copy(configuration().withClusterBootstrap(clusterBootstrap));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder withClasspathRuntime(final String resource, final String checksum) {
        final String checkedChecksum = Objects.requireNonNull(checksum, "checksum");
        return copy(configuration()
                .withRuntimeSource(RuntimeSource.classpath(
                        Objects.requireNonNull(resource, "resource"), runtime -> runtime.checksum(checkedChecksum))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder cacheProjectLocal(final Path directory) {
        final ClasspathRuntime current = configuration()
                .runtimeSource()
                .classpathRuntime()
                .orElseThrow(() ->
                        new IllegalStateException("cacheProjectLocal requires a preceding withClasspathRuntime(...)"));
        final ClasspathRuntime updated =
                current.cache(RuntimeCache.projectLocal(Objects.requireNonNull(directory, "directory")));
        return copy(configuration().withRuntimeSource(RuntimeSource.classpath(current.resource(), runtime -> updated)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder cacheProjectLocal(final String directory) {
        return cacheProjectLocal(Path.of(Objects.requireNonNull(directory, "directory")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder upgradePolicy(final UpgradePolicy upgradePolicy) {
        return copy(configuration().withUpgradePolicy(upgradePolicy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder configDriftPolicy(final ConfigDriftPolicy configDriftPolicy) {
        return copy(configuration().withConfigDriftPolicy(configDriftPolicy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder copy(final ManagedPostgresConfiguration updatedConfiguration) {
        return new DefaultManagedPostgresBuilder(mode, updatedConfiguration);
    }
}
