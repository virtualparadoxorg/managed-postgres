package eu.virtualparadox.managedpostgres.runtime.download;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eu.virtualparadox.managedpostgres.lifecycle.port.AllocatedPort;
import eu.virtualparadox.managedpostgres.lifecycle.port.PortAllocator;
import eu.virtualparadox.managedpostgres.runtime.download.progress.BytesTransferredListener;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HttpRuntimeArtifactFetcherTest {

    @TempDir
    private Path temporaryDirectory;

    HttpRuntimeArtifactFetcherTest() {}

    @Test
    void reportsNonDecreasingByteCountsWithKnownContentLength() throws IOException {
        final byte[] body = bytes(200_000);
        final HttpTestServer server = httpServer(body, true);
        final Path target = temporaryDirectory.resolve("artifact.bin");
        final RecordingBytesListener listener = new RecordingBytesListener();

        try (server) {
            server.start();
            HttpRuntimeArtifactFetcher.copy(server.uri(), target, listener);
        }

        assertThat(Files.size(target)).isEqualTo(body.length);
        assertThat(listener.dones()).isSorted();
        assertThat(listener.dones().get(listener.dones().size() - 1)).isEqualTo((long) body.length);
        assertThat(listener.totals()).containsOnly((long) body.length);
    }

    @Test
    void reportsTotalZeroWhenContentLengthIsAbsent() throws IOException {
        final byte[] body = bytes(50_000);
        final HttpTestServer server = httpServer(body, false);
        final Path target = temporaryDirectory.resolve("artifact.bin");
        final RecordingBytesListener listener = new RecordingBytesListener();

        try (server) {
            server.start();
            HttpRuntimeArtifactFetcher.copy(server.uri(), target, listener);
        }

        assertThat(Files.size(target)).isEqualTo(body.length);
        assertThat(listener.totals()).containsOnly(0L);
        assertThat(listener.dones().get(listener.dones().size() - 1)).isEqualTo((long) body.length);
    }

    private static byte[] bytes(final int length) {
        final byte[] body = new byte[length];
        for (int index = 0; index < length; index++) {
            body[index] = (byte) (index % 256);
        }

        return body;
    }

    private static HttpTestServer httpServer(final byte[] body, final boolean declareContentLength) throws IOException {
        final HttpServer server;
        final URI uri;
        try (AllocatedPort allocatedPort = new PortAllocator().allocateRandom()) {
            uri = URI.create("http://" + allocatedPort.host() + ":" + allocatedPort.port() + "/postgres.zip");
            server =
                    HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), allocatedPort.port()), 0);
            server.createContext("/postgres.zip", exchange -> respond(exchange, body, declareContentLength));
        }

        return new HttpTestServer(server, uri);
    }

    private static void respond(final HttpExchange exchange, final byte[] body, final boolean declareContentLength)
            throws IOException {
        // length 0 makes the JDK server use chunked transfer encoding, so no Content-Length is sent.
        final long declaredLength = declareContentLength ? body.length : 0L;
        exchange.sendResponseHeaders(200, declaredLength);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private record HttpTestServer(HttpServer server, URI uri) implements AutoCloseable {

        private void start() {
            server.start();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class RecordingBytesListener implements BytesTransferredListener {

        private final List<Long> dones = new ArrayList<>();
        private final List<Long> totals = new ArrayList<>();

        @Override
        public void onBytesTransferred(final long done, final long total) {
            dones.add(done);
            totals.add(total);
        }

        private List<Long> dones() {
            return List.copyOf(dones);
        }

        private List<Long> totals() {
            return List.copyOf(totals);
        }
    }
}
