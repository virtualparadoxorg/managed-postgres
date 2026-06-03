package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.runtime.Checksum;
import eu.virtualparadox.managedpostgres.runtime.ChecksumVerifier;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Downloads runtime archive artifacts from local {@code file:} and HTTP repositories.
 */
public final class FileRuntimeArtifactDownloader implements RuntimeArtifactDownloader {

    private static final String FILE_SCHEME = "file";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    private final ChecksumVerifier checksumVerifier;

    /**
     * Creates a file runtime artifact downloader.
     */
    public FileRuntimeArtifactDownloader() {
        this(new ChecksumVerifier());
    }

    private FileRuntimeArtifactDownloader(final ChecksumVerifier checksumVerifier) {
        this.checksumVerifier = Objects.requireNonNull(checksumVerifier, "checksumVerifier");
    }

    /**
     * Copies a local file repository artifact into the partial download target and verifies its checksum.
     *
     * @param repository runtime artifact repository
     * @param target partial download target path
     * @param checksum expected artifact checksum
     * @return downloaded artifact path
     * @throws IOException if checksum verification cannot read the downloaded target
     */
    @Override
    public Path download(final RuntimeRepository repository, final Path target, final Checksum checksum)
            throws IOException {
        final RuntimeRepository checkedRepository = Objects.requireNonNull(repository, "repository");
        final Path checkedTarget =
                Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        final Checksum checkedChecksum = Objects.requireNonNull(checksum, "checksum");

        try {
            Files.createDirectories(parentDirectory(checkedTarget));
            Files.deleteIfExists(checkedTarget);
            copyArtifact(checkedRepository, checkedTarget);
            return checksumVerifier.verify(checkedTarget, checkedChecksum);
        } catch (final IOException exception) {
            throw downloadFailure(
                    "failed to download PostgreSQL runtime artifact", checkedRepository, checkedTarget, exception);
        }
    }

    private static void copyArtifact(final RuntimeRepository repository, final Path target) throws IOException {
        final URI uri = repository.uri();
        if (FILE_SCHEME.equals(uri.getScheme())) {
            copyFileArtifact(uri, target);
        } else if (isHttpScheme(uri)) {
            copyHttpArtifact(uri, target);
        } else {
            throw downloadFailure("unsupported runtime repository URI scheme", repository, target);
        }
    }

    private static void copyFileArtifact(final URI uri, final Path target) throws IOException {
        final Path source = sourcePath(uri);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyHttpArtifact(final URI uri, final Path target) throws IOException {
        HttpRuntimeArtifactFetcher.copy(uri, target);
    }

    private static Path sourcePath(final URI uri) {
        return Path.of(uri).toAbsolutePath().normalize();
    }

    private static boolean isHttpScheme(final URI uri) {
        final String scheme = uri.getScheme();
        return HTTP_SCHEME.equals(scheme) || HTTPS_SCHEME.equals(scheme);
    }

    private static Path parentDirectory(final Path path) {
        return Objects.requireNonNull(path.getParent(), "targetParent");
    }

    private static ManagedPostgresException downloadFailure(
            final String message, final RuntimeRepository repository, final Path target) {
        return new ManagedPostgresException(message, diagnostic(repository, target));
    }

    private static ManagedPostgresException downloadFailure(
            final String message, final RuntimeRepository repository, final Path target, final Throwable cause) {
        return new ManagedPostgresException(message, cause, diagnostic(repository, target));
    }

    private static DiagnosticReport diagnostic(final RuntimeRepository repository, final Path target) {
        return new DiagnosticReport(List.of(new DiagnosticSection(
                "runtime-download",
                Map.of(
                        "repository", repository.uri().toString(),
                        "target", target.toString()))));
    }
}
