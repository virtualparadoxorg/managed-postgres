package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.ClasspathRuntimeDsl;
import eu.virtualparadox.managedpostgres.ClusterSection;
import eu.virtualparadox.managedpostgres.LogsSection;
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.NetworkSection;
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
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Default immutable managed PostgreSQL builder.
 */
@SuppressWarnings({
    // The immutable builder intentionally fronts the complete public configuration contract in one place,
    // including the runtime sub-DSLs (downloaded/classpath), so it touches many configuration types.
    "PMD.CouplingBetweenObjects",
    // Each fluent section (logs, network, …) adds a handful of delegating methods; the method count is
    // an intentional consequence of surfacing the full builder contract from a single class.
    "PMD.TooManyMethods"
})
public final class DefaultManagedPostgresBuilder extends AbstractManagedPostgresBuilder
        implements ClasspathRuntimeDsl, ClusterSection, LogsSection, ManagedPostgresConfigurer, NetworkSection {

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
    public DefaultManagedPostgresBuilder network() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder host(final String host) {
        return copy(configuration().withNetwork(configuration().network().host(Objects.requireNonNull(host, "host"))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder port(final int port) {
        return copy(configuration().withNetwork(configuration().network().port(port)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder randomPort() {
        return copy(configuration().withNetwork(configuration().network().randomPort()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder stableRandomPort() {
        return copy(configuration().withNetwork(configuration().network().stableRandomPort()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder preferredPort(final int port) {
        return copy(configuration().withNetwork(configuration().network().preferredPort(port)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder fallbackToRandom() {
        return copy(configuration().withNetwork(configuration().network().fallbackToRandom()));
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
    public DefaultManagedPostgresBuilder cluster() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder database(final String database) {
        return copy(configuration()
                .withClusterBootstrap(
                        configuration().clusterBootstrap().database(Objects.requireNonNull(database, "database"))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder owner(final String owner) {
        return copy(configuration()
                .withClusterBootstrap(
                        configuration().clusterBootstrap().owner(Objects.requireNonNull(owner, "owner"))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder password(final String password) {
        return copy(configuration()
                .withClusterBootstrap(configuration()
                        .clusterBootstrap()
                        .password(Secret.of(Objects.requireNonNull(password, "password")))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder extension(final String extensionName) {
        return copy(configuration()
                .withClusterBootstrap(configuration()
                        .clusterBootstrap()
                        .extension(Objects.requireNonNull(extensionName, "extensionName"))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder optionalExtension(final String extensionName) {
        return copy(configuration()
                .withClusterBootstrap(configuration()
                        .clusterBootstrap()
                        .optionalExtension(Objects.requireNonNull(extensionName, "extensionName"))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder network(final Network network) {
        return copy(configuration().withNetwork(Objects.requireNonNull(network, "network")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder cluster(final ClusterBootstrap cluster) {
        return copy(configuration().withClusterBootstrap(Objects.requireNonNull(cluster, "cluster")));
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
    public DefaultManagedPostgresBuilder logs() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder toFiles() {
        return copy(configuration().withLogs(configuration().logs().toFiles()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder toSlf4j() {
        return copy(configuration().withLogs(configuration().logs().toSlf4j()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultManagedPostgresBuilder loggerName(final String loggerName) {
        return copy(configuration()
                .withLogs(configuration().logs().loggerName(Objects.requireNonNull(loggerName, "loggerName"))));
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
