package eu.virtualparadox.managedpostgres.lifecycle.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PortAllocatorTest {

    @TempDir
    private Path temporaryDirectory;

    PortAllocatorTest() {
    }

    @Test
    void randomPortAllocatorReturnsBindableUnusedPort() throws IOException {
        final PortAllocator allocator = new PortAllocator();

        try (AllocatedPort allocated = allocator.allocateRandom()) {
            assertThat(allocated.host()).isEqualTo("127.0.0.1");
            assertThat(allocated.port()).isBetween(1, 65_535);
            assertBindable(allocated);
        }
    }

    @Test
    void stableRandomPortCanBeReadFromMetadata() throws IOException {
        final int freePort = availablePort();
        final MetadataStore metadataStore = new MetadataStore(
                temporaryDirectory.resolve("state").resolve("metadata.json"),
                new FileSystemOperationJournal());
        metadataStore.write(metadata(freePort));
        final PortAllocator allocator = PortAllocator.metadataBacked(metadataStore);

        try (AllocatedPort allocated = allocator.allocateStableRandom("default")) {
            assertThat(allocated.port()).isEqualTo(freePort);
        }
    }

    @Test
    void stableRandomPortIsWrittenToMetadata() {
        final MetadataStore metadataStore = new MetadataStore(
                temporaryDirectory.resolve("state").resolve("metadata.json"),
                new FileSystemOperationJournal());
        final PortAllocator allocator = PortAllocator.metadataBacked(metadataStore);

        try (AllocatedPort allocated = allocator.allocateStableRandom("default")) {
            assertThat(metadataStore.readPort()).hasValue(allocated.port());
        }
    }

    @Test
    void stableRandomPortIsPersistedThroughStoreSeam() throws IOException {
        final InMemoryStablePortStore store = new InMemoryStablePortStore();
        final PortAllocator allocator = new PortAllocator(store);
        final int firstPort;

        try (AllocatedPort first = allocator.allocateStableRandom("default")) {
            firstPort = first.port();
            assertThat(store.port("default")).hasValue(firstPort);
        }

        try (AllocatedPort second = allocator.allocateStableRandom("default")) {
            assertThat(second.port()).isEqualTo(firstPort);
        }
    }

    @Test
    void preferredOccupiedPortFailsByDefault() throws IOException {
        final PortAllocator allocator = new PortAllocator();

        try (ServerSocket occupied = occupiedLoopbackSocket()) {
            assertThatExceptionOfType(ManagedPostgresException.class)
                    .isThrownBy(() -> allocator.allocatePreferred(occupied.getLocalPort()))
                    .withMessageContaining("Port is already in use");
        }
    }

    @Test
    void preferredOccupiedPortCanFallbackToRandomWhenConfigured() throws IOException {
        final PortAllocator allocator = new PortAllocator();

        try (ServerSocket occupied = occupiedLoopbackSocket();
                AllocatedPort allocated = allocator.allocatePreferred(
                        occupied.getLocalPort(),
                        PortAllocator.OccupiedPortPolicy.FALLBACK_TO_RANDOM)) {
            assertThat(allocated.port()).isNotEqualTo(occupied.getLocalPort());
            assertBindable(allocated);
        }
    }

    @Test
    void allocatedPortRejectsInvalidValuesAndClosesIdempotently() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new AllocatedPort(" ", 15432));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new AllocatedPort("127.0.0.1", 0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new AllocatedPort("127.0.0.1", 65_536));
        try (AllocatedPort allocatedPort = new AllocatedPort("127.0.0.1", 15432)) {

            closeAction(allocatedPort).run();
            closeAction(allocatedPort).run();

            assertThat(allocatedPort.open()).isFalse();
        }
    }

    @Test
    void allocatorRejectsBlankStableKeysAndInvalidPreferredPorts() {
        final PortAllocator allocator = new PortAllocator();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> allocator.allocateStableRandom(" "));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> allocator.allocatePreferred(0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> allocator.allocatePreferred(65_536));
    }

    private static void assertBindable(final AllocatedPort allocated) throws IOException {
        try (ServerSocket socket = occupiedLoopbackSocket(allocated.port())) {
            assertThat(socket.getLocalPort()).isEqualTo(allocated.port());
        }
    }

    private static ServerSocket occupiedLoopbackSocket() throws IOException {
        return occupiedLoopbackSocket(0);
    }

    private static ServerSocket occupiedLoopbackSocket(final int port) throws IOException {
        final ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(false);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port));

        return serverSocket;
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = occupiedLoopbackSocket()) {
            return socket.getLocalPort();
        }
    }

    private static Runnable closeAction(final AllocatedPort allocatedPort) {
        return allocatedPort::close;
    }

    private static PostgresInstanceMetadata metadata(final int port) {
        return new PostgresInstanceMetadata(
                1,
                "instance-1",
                "cluster-1",
                "default",
                Path.of("pgdata"),
                "127.0.0.1",
                port,
                "postgres",
                "postgres",
                "17.5",
                17,
                "create-new",
                1234L,
                "hash",
                Instant.parse("2026-05-27T10:15:30Z"),
                Instant.parse("2026-05-27T10:15:31Z"));
    }

    private static final class InMemoryStablePortStore implements PortAllocator.StablePortStore {

        private final Map<String, Integer> ports = new HashMap<>();

        private InMemoryStablePortStore() {
        }

        @Override
        public OptionalInt load(final String key) {
            final Integer port = ports.get(key);
            final OptionalInt result;
            if (port == null) {
                result = OptionalInt.empty();
            } else {
                result = OptionalInt.of(port);
            }

            return result;
        }

        @Override
        public void save(final String key, final int port) {
            ports.put(key, port);
        }

        OptionalInt port(final String key) {
            return load(key);
        }
    }
}
