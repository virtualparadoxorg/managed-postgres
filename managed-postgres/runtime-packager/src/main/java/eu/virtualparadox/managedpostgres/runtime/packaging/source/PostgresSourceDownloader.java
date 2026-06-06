package eu.virtualparadox.managedpostgres.runtime.packaging.source;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Downloads and verifies PostgreSQL source archives before build unpacking.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class PostgresSourceDownloader {

    private static final String FILE_SCHEME = "file";
    private static final String HTTPS_SCHEME = "https";
    private static final String OFFICIAL_SOURCE_HOST = "ftp.postgresql.org";
    private static final String OFFICIAL_SOURCE_PREFIX = "/pub/source/v";

    private final HttpClient httpClient;

    /**
     * Creates a downloader backed by the JDK default HTTP client.
     */
    public PostgresSourceDownloader() {
        this(HttpClient.newHttpClient());
    }

    PostgresSourceDownloader(final HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    /**
     * Downloads the source tarball to a deterministic cache path and verifies its checksum.
     *
     * @param release source release metadata
     * @param targetDirectory cache directory root
     * @return downloaded archive path
     */
    public Path download(final PostgresRelease release, final Path targetDirectory) {
        final PostgresRelease validatedRelease = Objects.requireNonNull(release, "release");
        final Path validatedTargetDirectory = Objects.requireNonNull(targetDirectory, "targetDirectory");
        final URI sourceTarball = validatedRelease.sourceTarball();
        final String fileName = archiveFileName(sourceTarball);
        final Path targetPath = validatedTargetDirectory.resolve(fileName);
        try {
            Files.createDirectories(validatedTargetDirectory);
            try (InputStream sourceStream = openSourceStream(sourceTarball)) {
                Files.copy(sourceStream, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to download PostgreSQL source archive", exception);
        }

        return verify(targetPath, validatedRelease.sourceTarballSha256());
    }

    /**
     * Verifies a downloaded source archive against the expected checksum.
     *
     * @param archive downloaded source archive
     * @param expectedSha256 expected SHA-256 checksum
     * @return verified archive path
     */
    public Path verify(final Path archive, final String expectedSha256) {
        final Path validatedArchive = Objects.requireNonNull(archive, "archive");
        final String validatedExpectedSha256 = requireNonBlank(expectedSha256, "expectedSha256");
        final byte[] expectedDigest = parseHexSha256(validatedExpectedSha256);
        final byte[] content;
        try {
            content = Files.readAllBytes(validatedArchive);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read PostgreSQL source archive", exception);
        }

        final byte[] actualDigest = sha256(content);
        if (!MessageDigest.isEqual(actualDigest, expectedDigest)) {
            throw new IllegalStateException("checksum mismatch for " + validatedArchive.getFileName());
        }

        return validatedArchive;
    }

    static String sha256Hex(final byte[] content) {
        return HexFormat.of().formatHex(sha256(content));
    }

    private static byte[] sha256(final byte[] content) {
        final byte[] validatedContent = Objects.requireNonNull(content, "content");
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(validatedContent);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", exception);
        }
    }

    private InputStream openSourceStream(final URI sourceTarball) throws IOException {
        final URI validatedSourceTarball = Objects.requireNonNull(sourceTarball, "sourceTarball");
        final String scheme = requireNonBlank(validatedSourceTarball.getScheme(), "sourceTarball.scheme");
        final InputStream sourceStream;
        if (FILE_SCHEME.equals(scheme)) {
            sourceStream = Files.newInputStream(Path.of(validatedSourceTarball));
        } else if (HTTPS_SCHEME.equals(scheme)) {
            sourceStream = openOfficialSourceStream(validatedSourceTarball);
        } else {
            throw new IllegalArgumentException("unsupported source tarball scheme: " + scheme);
        }

        return sourceStream;
    }

    private InputStream openOfficialSourceStream(final URI sourceTarball) throws IOException {
        requireOfficialSourceTarball(sourceTarball);
        final HttpRequest request = HttpRequest.newBuilder(sourceTarball).GET().build();
        final HttpResponse<InputStream> response = sendRequest(request);

        if (response.statusCode() != 200) {
            throw new IOException("failed to download PostgreSQL source archive: HTTP " + response.statusCode());
        }

        return response.body();
    }

    private HttpResponse<InputStream> sendRequest(final HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while downloading PostgreSQL source archive", exception);
        }
    }

    private static String archiveFileName(final URI sourceTarball) {
        final Path fileName = Path.of(requireNonBlank(sourceTarball.getPath(), "sourceTarball.path"))
                .getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("source tarball path must include a file name");
        }

        return fileName.toString();
    }

    private static byte[] parseHexSha256(final String expectedSha256) {
        try {
            return HexFormat.of().parseHex(expectedSha256);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("expectedSha256 must contain hexadecimal characters", exception);
        }
    }

    private static void requireOfficialSourceTarball(final URI sourceTarball) {
        final String host = requireNonBlank(sourceTarball.getHost(), "sourceTarball.host");
        final String path = requireNonBlank(sourceTarball.getPath(), "sourceTarball.path");
        if (!OFFICIAL_SOURCE_HOST.equals(host)) {
            throw new IllegalArgumentException("source tarball must reference the official PostgreSQL source archive");
        }
        if (!path.startsWith(OFFICIAL_SOURCE_PREFIX)) {
            throw new IllegalArgumentException("source tarball must reference the official PostgreSQL source archive");
        }
        if (!path.endsWith(".tar.gz")) {
            throw new IllegalArgumentException("source tarball must reference the official PostgreSQL source archive");
        }
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        final String checkedValue = Objects.requireNonNull(value, fieldName);
        if (checkedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return checkedValue;
    }
}
