package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.config.DownloadedRuntime;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.runtime.platform.HostRuntimePlatform;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
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
@SuppressWarnings({
    // The resolver intentionally orchestrates the runtime source, repository, platform and HTTP
    // types to turn an official/github-release source into a concrete, verified download.
    "PMD.CouplingBetweenObjects"
})
public final class OfficialRuntimeSourceResolver {

    static final String OFFICIAL_SCHEME = "managed-postgres";
    static final String GITHUB_RELEASE_SCHEME = "github-release";
    private static final String DOWNLOADED_KIND = "downloaded";
    private static final String DEFAULT_BASE_URL = "https://github.com/virtualparadoxorg/managed-postgres-runtimes";
    private static final String DEFAULT_REVISION = "r1";
    private static final int SHA256_HEX_LENGTH = 64;

    /**
     * Base64-encoded X.509 (SubjectPublicKeyInfo) Ed25519 public key for the official runtimes repo.
     * Its private counterpart is held only in the {@code RUNTIMES_SIGNING_SECRET} CI secret; the
     * publish job signs each bundle and uploads {@code <archive>.sig} next to it.
     */
    static final String OFFICIAL_PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAAgpqMJ/qvwiRr0DZvU10GnDcPpdKuzmbFSfGkvjrcGc=";

    private final String baseUrl;
    private final String revision;
    private final Supplier<String> targetSupplier;
    private final Function<URI, String> textFetcher;
    private final Function<URI, byte[]> byteFetcher;

    /**
     * Creates a resolver against the official GitHub release repository.
     */
    public OfficialRuntimeSourceResolver() {
        this(
                DEFAULT_BASE_URL,
                DEFAULT_REVISION,
                HostRuntimePlatform::currentTargetIdentifier,
                OfficialRuntimeSourceResolver::httpGet,
                OfficialRuntimeSourceResolver::httpGetBytes);
    }

    OfficialRuntimeSourceResolver(
            final String baseUrl,
            final String revision,
            final Supplier<String> targetSupplier,
            final Function<URI, String> textFetcher,
            final Function<URI, byte[]> byteFetcher) {
        this.baseUrl = baseUrl;
        this.revision = revision;
        this.targetSupplier = targetSupplier;
        this.textFetcher = textFetcher;
        this.byteFetcher = byteFetcher;
    }

    /**
     * Resolves an official downloaded source into a concrete archive URL + checksum.
     *
     * @param source configured runtime source
     * @param postgresqlVersion requested PostgreSQL version (e.g. {@code 18.4})
     * @return a concrete downloaded source, or the input unchanged when not applicable
     */
    public RuntimeSource resolve(final RuntimeSource source, final String postgresqlVersion) {
        final Optional<DownloadedRuntime> downloaded = source.downloadedRuntime();
        final RuntimeSource result;
        if (!DOWNLOADED_KIND.equals(source.kind())
                || downloaded.isEmpty()
                || !isManagedRelease(downloaded.orElseThrow().repository())) {
            result = source;
        } else {
            final String releaseBaseUrl =
                    baseUrlFor(downloaded.orElseThrow().repository().orElseThrow());
            final String target = targetSupplier.get();
            final String tag = "pg" + postgresqlVersion + "-" + revision;
            final String archiveName =
                    "managed-postgres-runtime-pg" + postgresqlVersion + "-" + target + "-" + revision + ".zip";
            final URI archiveUri = URI.create(releaseBaseUrl + "/releases/download/" + tag + "/" + archiveName);
            final URI checksumUri = URI.create(releaseBaseUrl + "/releases/download/" + tag + "/SHA256SUMS");
            final String checksumHex = findChecksum(textFetcher.apply(checksumUri), archiveName, checksumUri);
            final DownloadedRuntime base = downloaded
                    .orElseThrow()
                    .repository(RuntimeRepository.custom(archiveUri))
                    .checksum("sha256:" + checksumHex);
            final DownloadedRuntime resolved =
                    isOfficial(downloaded.orElseThrow().repository())
                            ? base.signature(officialSignature(archiveUri))
                            : base;
            result = new RuntimeSource(
                    source.kind(), source.existingPath(), Optional.of(resolved), source.classpathRuntime());
        }
        return result;
    }

    private static boolean isManagedRelease(final Optional<RuntimeRepository> repository) {
        // Opt-in only: an official() or github-release repository triggers manifest resolution. An
        // absent or direct (http/file) repository keeps the existing semantics untouched.
        final boolean managed;
        if (repository.isEmpty()) {
            managed = false;
        } else {
            final String scheme = repository.orElseThrow().uri().getScheme();
            managed = OFFICIAL_SCHEME.equals(scheme) || GITHUB_RELEASE_SCHEME.equals(scheme);
        }
        return managed;
    }

    private static boolean isOfficial(final Optional<RuntimeRepository> repository) {
        // Only the framework's own official repo is signed with the pinned key; a custom
        // github-release repo carries no signature we can verify against that key.
        return repository.isPresent()
                && OFFICIAL_SCHEME.equals(repository.orElseThrow().uri().getScheme());
    }

    private RuntimeSignature officialSignature(final URI archiveUri) {
        final URI signatureUri = URI.create(archiveUri + ".sig");
        final String signatureBase64 = Base64.getEncoder().encodeToString(byteFetcher.apply(signatureUri));
        return RuntimeSignature.ed25519(OFFICIAL_PUBLIC_KEY_BASE64, signatureBase64);
    }

    private String baseUrlFor(final RuntimeRepository repository) {
        final URI uri = repository.uri();
        final String resolvedBase;
        if (GITHUB_RELEASE_SCHEME.equals(uri.getScheme())) {
            resolvedBase = "https://github.com/" + uri.getAuthority() + uri.getPath();
        } else {
            resolvedBase = baseUrl;
        }
        return resolvedBase;
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
        throw new IllegalStateException("no published bundle '" + archiveName + "' found in " + checksumUri
                + " (platform/version may not be published yet)");
    }

    private static String httpGet(final URI uri) {
        final HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return requireSuccessfulBody(uri, response.statusCode(), response.body());
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to fetch " + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while fetching " + uri, exception);
        }
    }

    static String requireSuccessfulBody(final URI uri, final int statusCode, final String body) {
        if (statusCode / 100 != 2) {
            throw new IllegalStateException("failed to fetch " + uri + ": HTTP " + statusCode);
        }
        return body;
    }

    private static byte[] httpGetBytes(final URI uri) {
        final HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()) {
            final HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return requireSuccessfulBytes(uri, response.statusCode(), response.body());
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to fetch " + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while fetching " + uri, exception);
        }
    }

    static byte[] requireSuccessfulBytes(final URI uri, final int statusCode, final byte[] body) {
        if (statusCode / 100 != 2) {
            throw new IllegalStateException("failed to fetch " + uri + ": HTTP " + statusCode);
        }
        return body;
    }
}
