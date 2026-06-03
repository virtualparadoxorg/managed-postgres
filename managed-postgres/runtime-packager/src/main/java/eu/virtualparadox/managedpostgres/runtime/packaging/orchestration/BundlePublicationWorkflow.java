package eu.virtualparadox.managedpostgres.runtime.packaging.orchestration;

import eu.virtualparadox.managedpostgres.runtime.packaging.BundleManifest;
import eu.virtualparadox.managedpostgres.runtime.packaging.bundle.BundleNormalizer;
import eu.virtualparadox.managedpostgres.runtime.packaging.bundle.BundlePublisher;
import eu.virtualparadox.managedpostgres.runtime.packaging.bundle.PublishResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalizes raw install trees and publishes release-ready runtime bundle artifacts.
 */
public final class BundlePublicationWorkflow {

    private final BundleNormalizer bundleNormalizer;
    private final BundlePublisher bundlePublisher;

    /**
     * Creates the default bundle publication workflow.
     */
    public BundlePublicationWorkflow() {
        this(new BundleNormalizer(), new BundlePublisher());
    }

    BundlePublicationWorkflow(final BundleNormalizer bundleNormalizer, final BundlePublisher bundlePublisher) {
        this.bundleNormalizer = Objects.requireNonNull(bundleNormalizer, "bundleNormalizer");
        this.bundlePublisher = Objects.requireNonNull(bundlePublisher, "bundlePublisher");
    }

    /**
     * Publishes a built runtime install tree for a packaging request.
     *
     * @param request source-build packaging request
     * @param rawInstallTree built raw install tree
     * @param workspace prepared source workspace
     * @return packaging result
     */
    public RuntimePackagingResult publish(
            final RuntimePackagingRequest request, final Path rawInstallTree, final PreparedSourceWorkspace workspace) {
        final RuntimePackagingRequest validatedRequest = Objects.requireNonNull(request, "request");
        final Path validatedRawInstallTree = Objects.requireNonNull(rawInstallTree, "rawInstallTree");
        final PreparedSourceWorkspace validatedWorkspace = Objects.requireNonNull(workspace, "workspace");
        final BundleManifest manifest = new BundleManifest(
                validatedRequest.release().version(),
                validatedRequest.revision(),
                validatedRequest.targetPlatform(),
                archiveFileName(
                        validatedRequest.release().version(),
                        validatedRequest.targetPlatform().identifier(),
                        validatedRequest.revision()),
                "pending",
                Instant.now(),
                validatedRequest.release().sourceTarball().toString());
        final Path normalized = bundleNormalizer.normalize(
                validatedRawInstallTree,
                validatedRequest
                        .workRoot()
                        .resolve("normalized")
                        .resolve(validatedRequest.targetPlatform().identifier()),
                manifest);
        final PublishResult publishResult =
                bundlePublisher.publish(normalized, validatedRequest.outputDirectory(), manifest);
        return new RuntimePackagingResult(publishResult, validatedWorkspace.driver());
    }

    private static String archiveFileName(
            final String postgresVersion, final String targetIdentifier, final String revision) {
        return "managed-postgres-runtime-pg" + postgresVersion + "-" + targetIdentifier + "-" + revision + ".zip";
    }
}
