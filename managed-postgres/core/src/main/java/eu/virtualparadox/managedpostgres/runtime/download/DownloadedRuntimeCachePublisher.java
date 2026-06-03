package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.filesystem.DirectoryPublisher;
import eu.virtualparadox.managedpostgres.filesystem.ManagedPathOwnership;
import eu.virtualparadox.managedpostgres.internal.runtime.signature.RuntimeSignatureVerifier;
import eu.virtualparadox.managedpostgres.runtime.RuntimeValidator;
import eu.virtualparadox.managedpostgres.runtime.archive.RuntimeArchiveExtractor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Coordinates downloaded runtime cache publisher behavior for managed PostgreSQL internals.
 */
public final class DownloadedRuntimeCachePublisher {

    private final RuntimeArtifactDownloader downloader;
    private final RuntimeArchiveExtractor extractor;
    private final RuntimeSignatureVerifier signatureVerifier;
    private final ManagedPathOwnership ownership;

    /**
     * Creates a DownloadedRuntimeCachePublisher instance.
     *
     * @param downloader downloader value
     */
    public DownloadedRuntimeCachePublisher(final RuntimeArtifactDownloader downloader) {
        this(downloader, new RuntimeArchiveExtractor(), new RuntimeSignatureVerifier(), new ManagedPathOwnership());
    }

    private DownloadedRuntimeCachePublisher(
            final RuntimeArtifactDownloader downloader,
            final RuntimeArchiveExtractor extractor,
            final RuntimeSignatureVerifier signatureVerifier,
            final ManagedPathOwnership ownership) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier, "signatureVerifier");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    /**
     * Returns the publish result.
     *
     * @param context context value
     * @return publish result
     */
    public Path publish(final DownloadedRuntimeResolutionContext context) {
        final DownloadedRuntimeResolutionContext checkedContext = Objects.requireNonNull(context, "context");
        final Path download = checkedContext.download();
        final Path staging = checkedContext.staging();

        try {
            failIfStagingExists(checkedContext, staging);
            final Path artifact =
                    downloader.download(repositoryForCacheMiss(checkedContext), download, checkedContext.checksum());
            checkedContext.signature().ifPresent(signature -> verifySignature(artifact, signature));
            ownership.writeMarker(staging, "install-runtime");
            extractor.extract(artifact, staging);
            checkedContext
                    .signature()
                    .ifPresent(signature -> signatureVerifier.writeVerifiedMarker(staging, signature));
            RuntimeValidator.requireUsableRuntimeDirectory(staging);
            new DirectoryPublisher().publish(staging, checkedContext.finalRuntime());
            Files.deleteIfExists(download);

            return RuntimeValidator.requireUsableRuntimeDirectory(checkedContext.finalRuntime());
        } catch (final IOException exception) {
            cleanupAfterFailure(staging, download, exception);
            throw DownloadedRuntimeResolutionDiagnostics.failure(
                    "failed to resolve downloaded PostgreSQL runtime", checkedContext.runtimeSource(), exception);
        } catch (final IllegalArgumentException | IllegalStateException | UncheckedIOException exception) {
            cleanupAfterFailure(staging, download, exception);
            throw DownloadedRuntimeResolutionDiagnostics.failure(
                    "failed to resolve downloaded PostgreSQL runtime", checkedContext.runtimeSource(), exception);
        }
    }

    private static void failIfStagingExists(final DownloadedRuntimeResolutionContext context, final Path staging) {
        if (Files.exists(staging)) {
            throw DownloadedRuntimeResolutionDiagnostics.failure(
                    "downloaded runtime staging path already exists", context.runtimeSource());
        }
    }

    private static RuntimeRepository repositoryForCacheMiss(final DownloadedRuntimeResolutionContext context) {
        return context.repository()
                .orElseThrow(() -> DownloadedRuntimeResolutionDiagnostics.failure(
                        "downloaded runtime repository is not configured and cached runtime is absent",
                        context.runtimeSource()));
    }

    private void verifySignature(final Path artifact, final RuntimeSignature signature) {
        try {
            signatureVerifier.verify(artifact, signature);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void cleanupAfterFailure(final Path staging, final Path download, final Throwable failure) {
        try {
            discardOwnedStaging(staging);
        } catch (final UncheckedIOException exception) {
            failure.addSuppressed(exception);
        }
        try {
            Files.deleteIfExists(download);
        } catch (final IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    private void discardOwnedStaging(final Path staging) {
        if (ownership.isOwned(staging)) {
            DirectoryPublisher.deleteRecursivelyIfExists(staging);
        }
    }
}
