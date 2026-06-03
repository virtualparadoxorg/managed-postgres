package eu.virtualparadox.managedpostgres.lifecycle.port;

import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Short-lived loopback TCP probe used only to check whether a persisted port accepts connections.
 */
public final class LoopbackTcpPortProbe implements Predicate<PostgresInstanceMetadata> {

    /**
     * Creates a LoopbackTcpPortProbe instance.
     */
    public LoopbackTcpPortProbe() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean test(final PostgresInstanceMetadata metadata) {
        final boolean acceptsConnection = loopbackAddress(metadata.host())
                .map(address -> tcpPortAcceptsConnection(address, metadata.port()))
                .orElse(false);

        return acceptsConnection;
    }

    private static boolean tcpPortAcceptsConnection(final InetAddress address, final int port) {
        boolean acceptsConnection = false;
        try (SocketChannel channel = SocketChannel.open()) {
            acceptsConnection = channel.connect(new InetSocketAddress(address, port));
        } catch (final IOException exception) {
            acceptsConnection = false;
        }

        return acceptsConnection;
    }

    private static Optional<InetAddress> loopbackAddress(final String host) {
        Optional<InetAddress> address;
        try {
            address = Optional.of(InetAddress.getByName(host)).filter(InetAddress::isLoopbackAddress);
        } catch (final IOException exception) {
            address = Optional.empty();
        }

        return address;
    }
}
