package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.config.DownloadedRuntime;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.runtime.platform.HostRuntimePlatform;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves the framework "official" downloaded runtime source into a concrete archive URL and
 * checksum, so the existing download pipeline can fetch, verify and cache it with no user input.
 *
 * <p>For a {@code downloaded} source whose repository is absent or the official placeholder
 * ({@code managed-postgres:official}), this detects the host platform, derives the published asset
 * name for the requested version + revision, reads its SHA-256 from the release {@code SHA256SUMS},
 * and returns a source pointing at the concrete {@code https} archive URL with that checksum.
 * Any other (custom) repository is returned unchanged.
 */
public final class OfficialRuntimeSourceResolver {

    static final String OFFICIAL_SCHEME = "managed-postgres";
    private static final String DOWNLOADED_KIND = "downloaded";
    private static final String DEFAULT_BASE_URL = "https://github.com/virtualparadoxorg/managed-postgres";
    private static final String DEFAULT_REVISION = "r1";
    private static final int SHA256_HEX_LENGTH = 64;

    private final String baseUrl;
    private final String revision;
    private final Supplier<String> targetSupplier;
    private final Function<URI, String> textFetcher;

    /**
     * Creates a resolver against the official GitHub release repository.
     */
    public OfficialRuntimeSourceResolver() {
        this(DEFAULT_BASE_URL, DEFAULT_REVISION,
                HostRuntimePlatform::currentTargetIdentifier, OfficialRuntimeSourceResolver::httpGet);
    }

    OfficialRuntimeSourceResolver(
            final String baseUrl,
            final String revision,
            final Supplier<String> targetSupplier,
            final Function<URI, String> textFetcher) {
        this.baseUrl = baseUrl;
        this.revision = revision;
        this.targetSupplier = targetSupplier;
        this.textFetcher = textFetcher;
    }

    /**
     * Resolves an official downloaded source into a concrete archive URL + checksum.
     *
     * @param source configured runtime source
     * @param postgresqlVersion requested PostgreSQL version (e.g. {@code 18.4})
     * @return a concrete downloaded source, or the input unchanged when not applicable
     */
    public RuntimeSource resolve(final RuntimeSource source, final String postgresqlVersion) {
        if (!DOWNLOADED_KIND.equals(source.kind())) {
            return source;
        }
        final Optional<DownloadedRuntime> downloaded = source.downloadedRuntime();
        if (downloaded.isEmpty() || !isOfficial(downloaded.orElseThrow().repository())) {
            return source;
        }

        final String target = targetSupplier.get();
        final String tag = "pg" + postgresqlVersion + "-" + revision;
        final String archiveName =
                "managed-postgres-runtime-pg" + postgresqlVersion + "-" + target + "-" + revision + ".zip";
        final URI archiveUri = URI.create(baseUrl + "/releases/download/" + tag + "/" + archiveName);
        final URI checksumUri = URI.create(baseUrl + "/releases/download/" + tag + "/SHA256SUMS");

        final String checksumHex = findChecksum(textFetcher.apply(checksumUri), archiveName, checksumUri);
        final DownloadedRuntime resolved = downloaded.orElseThrow()
                .repository(RuntimeRepository.custom(archiveUri))
                .checksum("sha256:" + checksumHex);
        return new RuntimeSource(source.kind(), source.existingPath(), Optional.of(resolved), source.classpathRuntime());
    }

    private static boolean isOfficial(final Optional<RuntimeRepository> repository) {
        // Opt-in only: an explicit official() repository triggers manifest resolution. An absent
        // repository keeps the existing cache-only / unconfigured semantics untouched.
        return repository.isPresent() && OFFICIAL_SCHEME.equals(repository.orElseThrow().uri().getScheme());
    }

    private static String findChecksum(final String sha256sums, final String archiveName, final URI checksumUri) {
        for (final String rawLine : sha256sums.lines().toList()) {
            final String line = rawLine.strip();
            if (line.length() <= SHA256_HEX_LENGTH) {
                continue;
            }
            final String hex = line.substring(0, SHA256_HEX_LENGTH);
            final String name = line.substring(SHA256_HEX_LENGTH).strip();
            if (name.equals(archiveName)) {
                return hex;
            }
        }
        throw new IllegalStateException(
                "no published bundle '" + archiveName + "' found in " + checksumUri
                        + " (platform/version may not be published yet)");
    }

    private static String httpGet(final URI uri) {
        final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        final HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("failed to fetch " + uri + ": HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to fetch " + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while fetching " + uri, exception);
        }
    }
}
