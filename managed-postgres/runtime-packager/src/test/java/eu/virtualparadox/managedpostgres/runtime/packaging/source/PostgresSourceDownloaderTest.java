package eu.virtualparadox.managedpostgres.runtime.packaging.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("PMD.CouplingBetweenObjects")
final class PostgresSourceDownloaderTest {

    @TempDir
    Path tempDir;

    PostgresSourceDownloaderTest() {
    }

    @Test
    void rejectsDownloadedSourceWhenChecksumMismatches() throws IOException {
        final Path downloaded = tempDir.resolve("postgresql-16.14.tar.gz");
        Files.writeString(downloaded, "test-archive", StandardCharsets.UTF_8);
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader();

        assertThatThrownBy(() -> downloader.verify(
                        downloaded,
                        "0000000000000000000000000000000000000000000000000000000000000000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum mismatch");
    }

    @Test
    void verifiesDownloadedSourceWhenChecksumMatches() throws IOException {
        final Path downloaded = tempDir.resolve("postgresql-16.14.tar.gz");
        Files.writeString(downloaded, "verified-archive", StandardCharsets.UTF_8);
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader();

        assertThat(downloader.verify(downloaded, sha256("verified-archive"))).isEqualTo(downloaded);
    }

    @Test
    void rejectsExpectedChecksumWhenNotHex() throws IOException {
        final Path downloaded = tempDir.resolve("postgresql-16.14.tar.gz");
        Files.writeString(downloaded, "verified-archive", StandardCharsets.UTF_8);
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader();

        assertThatThrownBy(() -> downloader.verify(downloaded, "not-hex"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedSha256");
    }

    @Test
    void downloadsSourceToDeterministicCachePath() throws IOException {
        final Path sourceArchive = tempDir.resolve("postgresql-16.14.tar.gz");
        Files.writeString(sourceArchive, "source-bytes", StandardCharsets.UTF_8);
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader();
        final PostgresRelease release = new PostgresRelease(
                16,
                "16.14",
                sourceArchive.toUri(),
                sha256("source-bytes"));

        final Path downloadDirectory = tempDir.resolve("downloads");
        final Path downloaded = downloader.download(release, downloadDirectory);

        assertThat(downloaded).isEqualTo(downloadDirectory.resolve("postgresql-16.14.tar.gz"));
        assertThat(Files.readString(downloaded)).isEqualTo("source-bytes");
    }

    @Test
    void rejectsUnsupportedRemoteScheme() {
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader();
        final PostgresRelease release = new PostgresRelease(
                16,
                "16.14",
                URI.create("http://example.invalid/postgresql-16.14.tar.gz"),
                "abc123");

        assertThatThrownBy(() -> downloader.download(release, tempDir.resolve("downloads")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported source tarball scheme");
    }

    @Test
    void rejectsUnofficialHttpsHost() {
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader(stubHttpClient(200, "unused"));
        final PostgresRelease release = new PostgresRelease(
                16,
                "16.14",
                URI.create("https://example.invalid/pub/source/v16.14/postgresql-16.14.tar.gz"),
                "abc123");

        assertThatThrownBy(() -> downloader.download(release, tempDir.resolve("downloads")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("official PostgreSQL source archive");
    }

    @Test
    void rejectsUnofficialHttpsPathPrefix() {
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader(stubHttpClient(200, "unused"));
        final PostgresRelease release = new PostgresRelease(
                16,
                "16.14",
                URI.create("https://ftp.postgresql.org/snapshot/v16.14/postgresql-16.14.tar.gz"),
                "abc123");

        assertThatThrownBy(() -> downloader.download(release, tempDir.resolve("downloads")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("official PostgreSQL source archive");
    }

    @Test
    void rejectsUnofficialHttpsArchiveSuffix() {
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader(stubHttpClient(200, "unused"));
        final PostgresRelease release = new PostgresRelease(
                16,
                "16.14",
                URI.create("https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.zip"),
                "abc123");

        assertThatThrownBy(() -> downloader.download(release, tempDir.resolve("downloads")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("official PostgreSQL source archive");
    }

    @Test
    void downloadsOfficialHttpsSourceWhenAllowed() {
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader(stubHttpClient(200, "official-bytes"));
        final PostgresRelease release = new PostgresRelease(
                16,
                "16.14",
                URI.create("https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz"),
                sha256("official-bytes"));

        final Path downloaded = downloader.download(release, tempDir.resolve("downloads"));

        assertThat(downloaded).isEqualTo(tempDir.resolve("downloads/postgresql-16.14.tar.gz"));
        assertThat(readString(downloaded)).isEqualTo("official-bytes");
    }

    @Test
    void rejectsNonSuccessOfficialResponse() {
        final PostgresSourceDownloader downloader = new PostgresSourceDownloader(stubHttpClient(503, "unavailable"));
        final PostgresRelease release = new PostgresRelease(
                16,
                "16.14",
                URI.create("https://ftp.postgresql.org/pub/source/v16.14/postgresql-16.14.tar.gz"),
                sha256("unavailable"));

        assertThatThrownBy(() -> downloader.download(release, tempDir.resolve("downloads")))
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasMessageContaining("failed to download PostgreSQL source archive");
    }

    private static String sha256(final String value) {
        return PostgresSourceDownloader.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String readString(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static HttpClient stubHttpClient(final int statusCode, final String responseBody) {
        return new HttpClient() {
            @Override
            public Optional<CookieHandler> cookieHandler() {
                return Optional.empty();
            }

            @Override
            public Optional<Duration> connectTimeout() {
                return Optional.empty();
            }

            @Override
            public Redirect followRedirects() {
                return Redirect.NEVER;
            }

            @Override
            public Optional<ProxySelector> proxy() {
                return Optional.empty();
            }

            @Override
            public SSLContext sslContext() {
                return defaultSslContext();
            }

            @Override
            public SSLParameters sslParameters() {
                return new SSLParameters();
            }

            @Override
            public Optional<java.net.Authenticator> authenticator() {
                return Optional.empty();
            }

            @Override
            public Version version() {
                return Version.HTTP_1_1;
            }

            @Override
            public Optional<java.util.concurrent.Executor> executor() {
                return Optional.empty();
            }

            @Override
            public <T> HttpResponse<T> send(
                    final HttpRequest request,
                    final HttpResponse.BodyHandler<T> responseBodyHandler) {
                @SuppressWarnings("unchecked")
                final T body = (T) new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
                return new StubHttpResponse<>(request, statusCode, body);
            }

            @Override
            public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                    final HttpRequest request,
                    final HttpResponse.BodyHandler<T> responseBodyHandler) {
                throw new UnsupportedOperationException("sendAsync is not used in tests");
            }

            @Override
            public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                    final HttpRequest request,
                    final HttpResponse.BodyHandler<T> responseBodyHandler,
                    final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                throw new UnsupportedOperationException("sendAsync is not used in tests");
            }
        };
    }

    private record StubHttpResponse<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (left, right) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("default SSL context is unavailable", exception);
        }
    }
}
