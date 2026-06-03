package eu.virtualparadox.managedpostgres.runtime.download;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Streams PostgreSQL runtime artifacts from HTTP repositories into the partial download target.
 */
final class HttpRuntimeArtifactFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpRuntimeArtifactFetcher() {}

    static void copy(final URI uri, final Path target) throws IOException {
        final HttpResponse<InputStream> response = send(request(uri));
        validateHttpResponse(response.statusCode());
        try (InputStream inputStream = response.body()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
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
