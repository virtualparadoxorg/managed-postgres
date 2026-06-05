package eu.virtualparadox.managedpostgres.runtime.classpath;

import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.filesystem.DirectoryPublisher;
import eu.virtualparadox.managedpostgres.filesystem.ManagedPathOwnership;
import eu.virtualparadox.managedpostgres.internal.runtime.signature.RuntimeSignatureVerifier;
import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.StartupPhase;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;
import eu.virtualparadox.managedpostgres.runtime.ChecksumVerifier;
import eu.virtualparadox.managedpostgres.runtime.RuntimeValidator;
import eu.virtualparadox.managedpostgres.runtime.archive.RuntimeArchiveExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Publishes classpath PostgreSQL runtime archives into the runtime cache.
 */
public final class ClasspathRuntimeCachePublisher {

    private final ClassLoader classLoader;
    private final ChecksumVerifier checksumVerifier;
    private final RuntimeArchiveExtractor extractor;
    private final RuntimeSignatureVerifier signatureVerifier;
    private final ManagedPathOwnership ownership;

    /**
     * Creates a classpath runtime cache publisher.
     *
     * @param classLoader classloader used to read runtime resources
     */
    public ClasspathRuntimeCachePublisher(final ClassLoader classLoader) {
        this(
                classLoader,
                new ChecksumVerifier(),
                new RuntimeArchiveExtractor(),
                new RuntimeSignatureVerifier(),
                new ManagedPathOwnership());
    }

    private ClasspathRuntimeCachePublisher(
            final ClassLoader classLoader,
            final ChecksumVerifier checksumVerifier,
            final RuntimeArchiveExtractor extractor,
            final RuntimeSignatureVerifier signatureVerifier,
            final ManagedPathOwnership ownership) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.checksumVerifier = Objects.requireNonNull(checksumVerifier, "checksumVerifier");
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier, "signatureVerifier");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    /**
     * Publishes the classpath runtime into the cache.
     *
     * @param context classpath runtime resolution context
     * @return resolved local PostgreSQL runtime directory
     */
    public Path publish(final ClasspathRuntimeResolutionContext context) {
        return publish(context, ManagedPostgresProgressListener.none());
    }

    /**
     * Publishes the classpath runtime into the cache on a cache miss, emitting verification and
     * extraction progress to the supplied listener.
     *
     * @param context classpath runtime resolution context
     * @param progress startup progress listener
     * @return resolved local PostgreSQL runtime directory
     */
    public Path publish(
            final ClasspathRuntimeResolutionContext context, final ManagedPostgresProgressListener progress) {
        final ClasspathRuntimeResolutionContext checkedContext = Objects.requireNonNull(context, "context");
        final ManagedPostgresProgressListener checkedProgress = Objects.requireNonNull(progress, "progress");
        final Path artifact = checkedContext.artifact();
        final Path staging = checkedContext.staging();

        try {
            failIfStagingExists(checkedContext, staging);
            copyResource(checkedContext, artifact);
            checkedProgress.onProgress(
                    new StartupProgress(StartupPhase.VERIFYING, 0, 0, "Verifying PostgreSQL runtime archive"));
            checksumVerifier.verify(artifact, checkedContext.checksum());
            checkedContext.signature().ifPresent(signature -> verifySignature(artifact, signature));
            ownership.writeMarker(staging, "install-classpath-runtime");
            checkedProgress.onProgress(
                    new StartupProgress(StartupPhase.EXTRACTING, 0, 0, "Extracting PostgreSQL runtime archive"));
            extractor.extract(artifact, staging);
            checkedContext
                    .signature()
                    .ifPresent(signature -> signatureVerifier.writeVerifiedMarker(staging, signature));
            RuntimeValidator.requireUsableRuntimeDirectory(staging);
            new DirectoryPublisher().publish(staging, checkedContext.finalRuntime());
            Files.deleteIfExists(artifact);

            return RuntimeValidator.requireUsableRuntimeDirectory(checkedContext.finalRuntime());
        } catch (final IOException exception) {
            cleanupAfterFailure(staging, artifact, exception);
            throw ClasspathRuntimeResolutionDiagnostics.failure(
                    "failed to resolve classpath PostgreSQL runtime", checkedContext.runtimeSource(), exception);
        } catch (final IllegalArgumentException | IllegalStateException | UncheckedIOException exception) {
            cleanupAfterFailure(staging, artifact, exception);
            throw ClasspathRuntimeResolutionDiagnostics.failure(
                    "failed to resolve classpath PostgreSQL runtime", checkedContext.runtimeSource(), exception);
        }
    }

    private void copyResource(final ClasspathRuntimeResolutionContext context, final Path artifact) throws IOException {
        final Path parent = artifact.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream inputStream = resourceStream(context)) {
            Files.copy(inputStream, artifact);
        }
    }

    private InputStream resourceStream(final ClasspathRuntimeResolutionContext context) {
        final InputStream inputStream = classLoader.getResourceAsStream(context.resourceName());
        if (inputStream == null) {
            throw new IllegalArgumentException("classpath runtime resource not found: " + context.resourceName());
        }

        return inputStream;
    }

    private static void failIfStagingExists(final ClasspathRuntimeResolutionContext context, final Path staging) {
        if (Files.exists(staging)) {
            throw ClasspathRuntimeResolutionDiagnostics.failure(
                    "classpath runtime staging path already exists", context.runtimeSource());
        }
    }

    private void verifySignature(final Path artifact, final RuntimeSignature signature) {
        try {
            signatureVerifier.verify(artifact, signature);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void cleanupAfterFailure(final Path staging, final Path artifact, final Throwable failure) {
        try {
            discardOwnedStaging(staging);
        } catch (final UncheckedIOException exception) {
            failure.addSuppressed(exception);
        }
        try {
            Files.deleteIfExists(artifact);
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
