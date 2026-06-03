package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresMode;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Default immutable managed PostgreSQL builder.
 */
public final class DefaultManagedPostgresBuilder extends AbstractManagedPostgresBuilder {

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
