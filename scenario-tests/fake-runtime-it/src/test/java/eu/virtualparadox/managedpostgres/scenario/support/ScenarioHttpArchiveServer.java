package eu.virtualparadox.managedpostgres.scenario.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eu.virtualparadox.managedpostgres.lifecycle.port.AllocatedPort;
import eu.virtualparadox.managedpostgres.lifecycle.port.PortAllocator;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves a runtime archive over a loopback HTTP server so downloaded-runtime scenarios exercise the
 * real HTTP fetch path.
 */
public final class ScenarioHttpArchiveServer implements AutoCloseable {

    private final HttpServer server;
    private final int port;

    private ScenarioHttpArchiveServer(final HttpServer server, final int port) {
        this.server = server;
        this.port = port;
    }

    /**
     * Starts a loopback HTTP server that serves the given archive bytes at the supplied path.
     *
     * @param archivePath request path the archive is served from
     * @param archive archive file to serve
     * @return started archive server
     * @throws IOException when the server cannot be created
     */
    public static ScenarioHttpArchiveServer serving(final String archivePath, final Path archive) throws IOException {
        final byte[] body = Files.readAllBytes(archive);
        final int port = allocatePort();
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext(archivePath, exchange -> respond(exchange, body));
        server.start();

        return new ScenarioHttpArchiveServer(server, port);
    }

    /**
     * Returns the loopback port the server is bound to.
     *
     * @return bound port
     */
    public int port() {
        return port;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static int allocatePort() {
        try (AllocatedPort allocatedPort = new PortAllocator().allocateRandom()) {
            return allocatedPort.port();
        }
    }

    private static void respond(final HttpExchange exchange, final byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
