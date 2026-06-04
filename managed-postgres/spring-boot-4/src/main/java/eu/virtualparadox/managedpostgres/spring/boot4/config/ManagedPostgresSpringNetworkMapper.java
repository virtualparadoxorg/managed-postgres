package eu.virtualparadox.managedpostgres.spring.boot4.config;

import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer;
import java.util.Objects;

/**
 * Maps Spring Boot network properties to the public managed PostgreSQL network DSL.
 */
public final class ManagedPostgresSpringNetworkMapper {

    private static final String RANDOM = "random";
    private static final String STABLE_RANDOM = "stable-random";
    private static final String FIXED = "fixed";
    private static final String PREFERRED = "preferred";

    private ManagedPostgresSpringNetworkMapper() {}

    /**
     * Applies Spring Boot network properties to a managed PostgreSQL builder.
     *
     * @param builder managed PostgreSQL builder
     * @param properties Spring Boot network properties
     * @return updated builder
     */
    public static ManagedPostgresBuilder configure(
            final ManagedPostgresBuilder builder, final ManagedPostgresSpringProperties.NetworkProperties properties) {
        final ManagedPostgresBuilder checkedBuilder = Objects.requireNonNull(builder, "builder");
        final ManagedPostgresSpringProperties.NetworkProperties checkedProperties =
                Objects.requireNonNull(properties, "properties");

        return ManagedPostgresConfigurer.of(checkedBuilder)
                .network(configureNetwork(Network.localhostOnly(), checkedProperties));
    }

    private static Network configureNetwork(
            final Network network, final ManagedPostgresSpringProperties.NetworkProperties properties) {
        final Network localhostNetwork =
                Objects.requireNonNull(network, "network").host(properties.host());

        return switch (properties.portSelection()) {
            case RANDOM -> requireNoPort(localhostNetwork.randomPort(), properties);
            case STABLE_RANDOM -> requireNoPort(localhostNetwork.stableRandomPort(), properties);
            case FIXED -> localhostNetwork.port(requiredPort(properties));
            case PREFERRED -> preferred(localhostNetwork, properties);
            default -> throw new ManagedPostgresSpringException(
                    "managed-postgres.network.port-selection must be random, stable-random, fixed, or preferred");
        };
    }

    private static Network requireNoPort(
            final Network network, final ManagedPostgresSpringProperties.NetworkProperties properties) {
        if (properties.port().isPresent()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.network.port is only valid for fixed or preferred port selection");
        }
        if (properties.fallbackToRandom()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.network.fallback-to-random requires preferred port selection");
        }

        return network;
    }

    private static Network preferred(
            final Network network, final ManagedPostgresSpringProperties.NetworkProperties properties) {
        final Network preferredNetwork = network.preferredPort(requiredPort(properties));
        final Network configuredNetwork;
        if (properties.fallbackToRandom()) {
            configuredNetwork = preferredNetwork.fallbackToRandom();
        } else {
            configuredNetwork = preferredNetwork;
        }

        return configuredNetwork;
    }

    private static int requiredPort(final ManagedPostgresSpringProperties.NetworkProperties properties) {
        return properties
                .port()
                .orElseThrow(() -> new ManagedPostgresSpringException(
                        "managed-postgres.network.port is required for fixed or preferred port selection"));
    }
}
