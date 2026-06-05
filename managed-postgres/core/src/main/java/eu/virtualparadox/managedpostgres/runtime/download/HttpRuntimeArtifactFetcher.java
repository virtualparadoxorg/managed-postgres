package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.runtime.download.progress.BytesTransferredListener;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;

/**
 * Streams PostgreSQL runtime artifacts from HTTP repositories into the partial download target.
 */
final class HttpRuntimeArtifactFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpRuntimeArtifactFetcher() {}

    static void copy(final URI uri, final Path target) throws IOException {
        copy(uri, target, BytesTransferredListener.NONE);
    }

    static void copy(final URI uri, final Path target, final BytesTransferredListener progress) throws IOException {
        final BytesTransferredListener checkedProgress = Objects.requireNonNull(progress, "progress");
        final HttpResponse<InputStream> response = send(request(uri));
        validateHttpResponse(response.statusCode());
        final long total = contentLength(response);
        try (InputStream inputStream = response.body();
                OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(
                        target,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING))) {
            stream(inputStream, outputStream, total, checkedProgress);
        }
    }

    private static void stream(
            final InputStream inputStream,
            final OutputStream outputStream,
            final long total,
            final BytesTransferredListener progress)
            throws IOException {
        final byte[] buffer = new byte[BUFFER_SIZE];
        long done = 0;
        int read = inputStream.read(buffer);
        while (read >= 0) {
            outputStream.write(buffer, 0, read);
            done += read;
            progress.onBytesTransferred(done, total);
            read = inputStream.read(buffer);
        }
    }

    private static long contentLength(final HttpResponse<InputStream> response) {
        final long declared =
                response.headers().firstValueAsLong("content-length").orElse(0L);
        return declared > 0 ? declared : 0L;
    }

    private static HttpRequest request(final URI uri) {
        return HttpRequest.newBuilder(uri).timeout(READ_TIMEOUT).GET().build();
    }

    private static HttpResponse<InputStream> send(final HttpRequest request) throws IOException {
        try {
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while downloading PostgreSQL runtime artifact", exception);
        }
    }

    private static void validateHttpResponse(final int status) throws IOException {
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " while downloading PostgreSQL runtime artifact");
        }
    }
}
