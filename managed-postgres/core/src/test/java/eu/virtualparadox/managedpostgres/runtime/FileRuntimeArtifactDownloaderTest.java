package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.lifecycle.port.AllocatedPort;
import eu.virtualparadox.managedpostgres.lifecycle.port.PortAllocator;
import eu.virtualparadox.managedpostgres.runtime.download.FileRuntimeArtifactDownloader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class FileRuntimeArtifactDownloaderTest {

    private static final Checksum SHA256_ABC =
            Checksum.parse("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    private static final Checksum SHA256_WRONG =
            Checksum.parse("sha256:0000000000000000000000000000000000000000000000000000000000000000");

    @TempDir
    private Path temporaryDirectory;

    FileRuntimeArtifactDownloaderTest() {}

    @Test
    void fileUriCopiesArtifactToPartialDownloadPath() throws IOException {
        final Path source = artifact("postgres.zip", "abc");
        final Path partialDownload = temporaryDirectory.resolve("cache").resolve("postgres.zip.download");

        final Path downloaded = new FileRuntimeArtifactDownloader()
                .download(RuntimeRepository.custom(source.toUri()), partialDownload, SHA256_ABC);

        assertThat(downloaded).isEqualTo(partialDownload);
        assertThat(Files.readString(partialDownload)).isEqualTo("abc");
    }

    @Test
    void partialTargetIsReplacedOnRetry() throws IOException {
        final Path source = artifact("postgres.zip", "abc");
        final Path partialDownload = temporaryDirectory.resolve("cache").resolve("postgres.zip.download");
        Files.createDirectories(parentDirectory(partialDownload));
        Files.writeString(partialDownload, "stale");

        new FileRuntimeArtifactDownloader()
                .download(RuntimeRepository.custom(source.toUri()), partialDownload, SHA256_ABC);

        assertThat(Files.readString(partialDownload)).isEqualTo("abc");
    }

    @Test
    void httpUriStreamsArtifactToPartialDownloadPath() throws IOException {
        final HttpTestServer server = httpServer(200, "abc");
        final Path partialDownload = temporaryDirectory.resolve("cache").resolve("postgres.zip.download");

        try (server) {
            server.start();
            final Path downloaded = new FileRuntimeArtifactDownloader()
                    .download(RuntimeRepository.custom(server.uri()), partialDownload, SHA256_ABC);

            assertThat(downloaded).isEqualTo(partialDownload);
            assertThat(Files.readString(partialDownload)).isEqualTo("abc");
        }
    }

    @Test
    void httpErrorFailsWithDiagnosticContextWithoutPartialArtifact() throws IOException {
        final HttpTestServer server = httpServer(404, "missing");
        final Path partialDownload = temporaryDirectory.resolve("cache").resolve("postgres.zip.download");

        try (server) {
            server.start();
            assertThatThrownBy(() -> new FileRuntimeArtifactDownloader()
                            .download(RuntimeRepository.custom(server.uri()), partialDownload, SHA256_ABC))
                    .isInstanceOf(ManagedPostgresException.class)
                    .hasMessageContaining("download")
                    .satisfies(throwable -> assertThat(((ManagedPostgresException) throwable)
                                    .diagnosticReport()
                                    .renderText())
                            .contains(server.uri().toString(), partialDownload.toString()));
            assertThat(partialDownload).doesNotExist();
        }
    }

    @Test
    void missingSourceFailsWithDiagnosticContext() {
        final Path source = temporaryDirectory.resolve("missing.zip");
        final Path partialDownload = temporaryDirectory.resolve("cache").resolve("postgres.zip.download");

        assertThatThrownBy(() -> new FileRuntimeArtifactDownloader()
                        .download(RuntimeRepository.custom(source.toUri()), partialDownload, SHA256_ABC))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("download")
                .satisfies(throwable -> assertThat(((ManagedPostgresException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains(source.toUri().toString(), partialDownload.toString()));
    }

    @Test
    void unsupportedUriSchemeIsRejectedByFileDownloader() {
        final Path partialDownload = temporaryDirectory.resolve("cache").resolve("postgres.zip.download");

        assertThatThrownBy(() -> new FileRuntimeArtifactDownloader()
                        .download(
                                RuntimeRepository.custom(URI.create("ftp://example.test/postgres.zip")),
                                partialDownload,
                                SHA256_ABC))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    void checksumVerificationRunsAfterCopy() throws IOException {
        final Path source = artifact("postgres.zip", "abc");
        final Path partialDownload = temporaryDirectory.resolve("cache").resolve("postgres.zip.download");

        assertThatThrownBy(() -> new FileRuntimeArtifactDownloader()
                        .download(RuntimeRepository.custom(source.toUri()), partialDownload, SHA256_WRONG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(partialDownload.toString())
                .hasMessageContaining("SHA-256");
        assertThat(Files.readString(partialDownload)).isEqualTo("abc");
    }

    private Path artifact(final String name, final String content) throws IOException {
        final Path source = temporaryDirectory.resolve(name);
        Files.writeString(source, content);

        return source;
    }

    private static Path parentDirectory(final Path path) {
        return Objects.requireNonNull(path.getParent(), "parentDirectory");
    }

    private static HttpTestServer httpServer(final int status, final String responseBody) throws IOException {
        final HttpServer server;
        final URI uri;
        try (AllocatedPort allocatedPort = new PortAllocator().allocateRandom()) {
            uri = URI.create("http://" + allocatedPort.host() + ":" + allocatedPort.port() + "/postgres.zip");
            server =
                    HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), allocatedPort.port()), 0);
            server.createContext("/postgres.zip", exchange -> respond(exchange, status, responseBody));
        }

        return new HttpTestServer(server, uri);
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

    private static void respond(final HttpExchange exchange, final int status, final String responseBody)
            throws IOException {
        final byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
