package eu.virtualparadox.managedpostgres.lifecycle.port;

import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import java.util.Objects;

/**
 * Selects a loopback PostgreSQL port from managed PostgreSQL network configuration.
 */
public final class PostgresPortSelector {

    /**
     * Creates a PostgreSQL port selector.
     */
    public PostgresPortSelector() {
    }

    /**
     * Selects an available PostgreSQL port according to the configured network policy.
     *
     * @param configuration startup configuration
     * @param metadataStore metadata store for stable random ports
     * @return allocated port
     */
    public AllocatedPort select(
            final StartPostgresWorkflow.Configuration configuration,
            final MetadataStore metadataStore) {
        final StartPostgresWorkflow.Configuration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final PortAllocator allocator = PortAllocator.metadataBacked(
                Objects.requireNonNull(metadataStore, "metadataStore"));
        final Network.PortSelection portSelection = checkedConfiguration.network().portSelection();

        return switch (portSelection.mode()) {
            case RANDOM -> allocator.allocateRandom();
            case STABLE_RANDOM -> allocator.allocateStableRandom(checkedConfiguration.name());
            case FIXED -> allocator.allocatePreferred(portSelection.port().orElseThrow());
            case PREFERRED -> allocatePreferred(allocator, portSelection);
        };
    }

    private static AllocatedPort allocatePreferred(
            final PortAllocator allocator,
            final Network.PortSelection portSelection) {
        final AllocatedPort allocatedPort;
        if (portSelection.fallbackToRandom()) {
            allocatedPort = allocator.allocatePreferredWithFallback(portSelection.port().orElseThrow());
        } else {
            allocatedPort = allocator.allocatePreferred(portSelection.port().orElseThrow());
        }

        return allocatedPort;
    }
}
