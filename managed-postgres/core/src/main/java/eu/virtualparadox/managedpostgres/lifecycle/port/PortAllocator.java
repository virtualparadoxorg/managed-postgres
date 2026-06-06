package eu.virtualparadox.managedpostgres.lifecycle.port;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.apache.commons.lang3.StringUtils;

/**
 * Selects loopback ports for managed PostgreSQL instances.
 */
public final class PortAllocator {

    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final int RANDOM_PORT = 0;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;

    private final StablePortStore stablePortStore;

    /**
     * Creates a PortAllocator instance.
     */
    public PortAllocator() {
        this(new NoopStablePortStore());
    }

    /**
     * Creates a PortAllocator instance.
     *
     * @param stablePortStore stable port store value
     */
    public PortAllocator(final StablePortStore stablePortStore) {
        this.stablePortStore = Objects.requireNonNull(stablePortStore, "stablePortStore");
    }

    /**
     * Returns the metadata backed result.
     *
     * @param metadataStore metadata store value
     * @return metadata backed result
     */
    public static PortAllocator metadataBacked(final MetadataStore metadataStore) {
        return new PortAllocator(new MetadataStablePortStore(metadataStore));
    }

    /**
     * Allocates a random available loopback port.
     *
     * @return allocated port selection
     */
    public AllocatedPort allocateRandom() {
        return selectAvailablePort(RANDOM_PORT);
    }

    /**
     * Allocates a stable random port for a persistence key.
     *
     * @param key stable persistence key
     * @return allocated port selection
     */
    public AllocatedPort allocateStableRandom(final String key) {
        final String stableKey = requireNotBlank(key, "key");
        final OptionalInt persistedPort = stablePortStore.load(stableKey);

        final AllocatedPort allocatedPort;
        if (persistedPort.isPresent()) {
            allocatedPort = allocatePreferred(persistedPort.getAsInt());
        } else {
            allocatedPort = allocateRandom();
            stablePortStore.save(stableKey, allocatedPort.port());
        }

        return allocatedPort;
    }

    /**
     * Allocates a preferred loopback port and fails if it is occupied.
     *
     * @param preferredPort preferred port number
     * @return allocated port selection
     */
    public AllocatedPort allocatePreferred(final int preferredPort) {
        return allocatePreferred(preferredPort, OccupiedPortPolicy.FAIL);
    }

    /**
     * Allocates a preferred loopback port and falls back to a random port when it is occupied.
     *
     * @param preferredPort preferred port number
     * @return allocated port selection
     */
    public AllocatedPort allocatePreferredWithFallback(final int preferredPort) {
        return allocatePreferred(preferredPort, OccupiedPortPolicy.FALLBACK_TO_RANDOM);
    }

    /**
     * Allocates a preferred loopback port with configurable occupied-port handling.
     *
     * @param preferredPort preferred port number
     * @param occupiedPortPolicy behavior when the preferred port is occupied
     * @return allocated port selection
     */
    public AllocatedPort allocatePreferred(final int preferredPort, final OccupiedPortPolicy occupiedPortPolicy) {
        validatePort(preferredPort);
        Objects.requireNonNull(occupiedPortPolicy, "occupiedPortPolicy");

        AllocatedPort allocatedPort;
        try {
            allocatedPort = selectAvailablePort(preferredPort);
        } catch (PortInUseException exception) {
            if (occupiedPortPolicy == OccupiedPortPolicy.FALLBACK_TO_RANDOM) {
                allocatedPort = allocateRandom();
            } else {
                throw exception;
            }
        }

        return allocatedPort;
    }

    private static AllocatedPort selectAvailablePort(final int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(loopbackAddress(), port));

            return new AllocatedPort(LOOPBACK_HOST, socket.getLocalPort());
        } catch (BindException exception) {
            throw new PortInUseException("Port is already in use", port, exception);
        } catch (IOException exception) {
            throw portFailure("Failed to allocate PostgreSQL port", port, exception);
        }
    }

    private static InetAddress loopbackAddress() throws IOException {
        return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    }

    private static void validatePort(final int port) {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }

    private static ManagedPostgresException portFailure(final String message, final int port, final Throwable cause) {
        return new ManagedPostgresException(message, cause, diagnostic(port));
    }

    private static DiagnosticReport diagnostic(final int port) {
        return new DiagnosticReport(List.of(
                new DiagnosticSection("postgres-port", Map.of("host", LOOPBACK_HOST, "port", String.valueOf(port)))));
    }

    /**
     * Occupied preferred-port handling.
     */
    enum OccupiedPortPolicy {

        /**
         * Fail when the preferred port is occupied.
         */
        FAIL,

        /**
         * Allocate a random port when the preferred port is occupied.
         */
        FALLBACK_TO_RANDOM
    }

    /**
     * Persistence seam for stable random port selection.
     */
    interface StablePortStore {

        /**
         * Loads a stable port for a key.
         *
         * @param key stable port key
         * @return persisted port, or empty when none exists
         */
        public OptionalInt load(String key);

        /**
         * Saves a stable port for a key.
         *
         * @param key stable port key
         * @param port stable port
         */
        public void save(String key, int port);
    }

    private static final class NoopStablePortStore implements StablePortStore {

        private NoopStablePortStore() {}

        /**
         * {@inheritDoc}
         */
        @Override
        public OptionalInt load(final String key) {
            return OptionalInt.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void save(final String key, final int port) {
            // This allocator was created without stable persistence.
        }
    }

    private static final class MetadataStablePortStore implements StablePortStore {

        private final MetadataStore metadataStore;

        private MetadataStablePortStore(final MetadataStore metadataStore) {
            this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public OptionalInt load(final String key) {
            return metadataStore.readPort();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void save(final String key, final int port) {
            metadataStore.writePortReservation(key, port);
        }
    }

    private static final class PortInUseException extends ManagedPostgresException {

        private static final long serialVersionUID = 1L;

        private PortInUseException(final String message, final int port, final Throwable cause) {
            super(message, cause, diagnostic(port));
        }
    }
}
