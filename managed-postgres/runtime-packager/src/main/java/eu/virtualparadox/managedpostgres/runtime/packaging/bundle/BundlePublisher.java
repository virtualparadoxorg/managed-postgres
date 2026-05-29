package eu.virtualparadox.managedpostgres.runtime.packaging.bundle;

import eu.virtualparadox.managedpostgres.runtime.packaging.BundleManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Publishes normalized runtime bundles into release-ready artifacts.
 */
public final class BundlePublisher {

    private final BundleArchiveWriter bundleArchiveWriter;

    /**
     * Creates a bundle publisher.
     */
    public BundlePublisher() {
        this(new BundleArchiveWriter());
    }

    BundlePublisher(final BundleArchiveWriter bundleArchiveWriter) {
        this.bundleArchiveWriter = Objects.requireNonNull(bundleArchiveWriter, "bundleArchiveWriter");
    }

    /**
     * Publishes a normalized bundle to a release directory.
     *
     * @param normalizedBundle normalized bundle directory
     * @param publishDirectory release output directory
     * @param manifest bundle manifest
     * @return published artifact paths
     */
    public PublishResult publish(
            final Path normalizedBundle,
            final Path publishDirectory,
            final BundleManifest manifest) {
        final Path validatedNormalizedBundle = Objects.requireNonNull(normalizedBundle, "normalizedBundle");
        final Path validatedPublishDirectory = Objects.requireNonNull(publishDirectory, "publishDirectory");
        final BundleManifest validatedManifest = Objects.requireNonNull(manifest, "manifest");
        final Path bundle = bundleArchiveWriter.write(
                validatedNormalizedBundle,
                validatedPublishDirectory.resolve(validatedManifest.archiveFileName()));
        final Path checksum = writeChecksum(bundle);
        final Path publishedManifest = copyManifest(validatedNormalizedBundle, validatedPublishDirectory);

        return new PublishResult(bundle, checksum, publishedManifest);
    }

    private static Path writeChecksum(final Path bundle) {
        final Path checksumPath = Path.of(bundle + ".sha256");
        try {
            final String checksum = HexFormat.of().formatHex(sha256(Files.readAllBytes(bundle)));
            Files.writeString(
                    checksumPath,
                    checksum + "  " + bundle.getFileName() + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to write runtime bundle checksum", exception);
        }

        return checksumPath;
    }

    private static Path copyManifest(final Path normalizedBundle, final Path publishDirectory) {
        final Path sourceManifest = normalizedBundle.resolve("manifest.json");
        final Path targetManifest = publishDirectory.resolve("manifest.json");
        try {
            Files.createDirectories(publishDirectory);
            Files.copy(sourceManifest, targetManifest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to publish runtime bundle manifest", exception);
        }

        return targetManifest;
    }

    private static byte[] sha256(final byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", exception);
        }
    }
}
