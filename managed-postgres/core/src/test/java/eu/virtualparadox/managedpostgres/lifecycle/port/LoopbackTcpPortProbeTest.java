package eu.virtualparadox.managedpostgres.lifecycle.port;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresMetadataFixture;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public final class LoopbackTcpPortProbeTest {

    LoopbackTcpPortProbeTest() {}

    @Test
    void loopbackProbeAcceptsOpenLoopbackPort() throws IOException {
        try (AllocatedPort allocatedPort = new PortAllocator().allocateRandom();
                ServerSocketChannel socket = loopbackSocket(allocatedPort.port())) {
            final boolean acceptsConnection = new LoopbackTcpPortProbe()
                    .test(PostgresMetadataFixture.metadata(Path.of("data"), allocatedPort.port()));

            assertThat(socket.isOpen()).isTrue();
            assertThat(acceptsConnection).isTrue();
        }
    }

    @Test
    void loopbackProbeRejectsClosedOrNonLoopbackPorts() throws IOException {
        final int closedPort = closedLoopbackPort();

        assertThat(new LoopbackTcpPortProbe().test(PostgresMetadataFixture.metadata(Path.of("data"), closedPort)))
                .isFalse();
        assertThat(new LoopbackTcpPortProbe()
                        .test(PostgresMetadataFixture.metadata(
                                Path.of("data"), "203.0.113.10", closedPort, "16.4", 16)))
                .isFalse();
    }

    private static ServerSocketChannel loopbackSocket(final int port) throws IOException {
        final ServerSocketChannel socket = ServerSocketChannel.open();
        socket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port));

        return socket;
    }

    private static int closedLoopbackPort() {
        try (AllocatedPort allocatedPort = new PortAllocator().allocateRandom()) {
            return allocatedPort.port();
        }
    }
}
