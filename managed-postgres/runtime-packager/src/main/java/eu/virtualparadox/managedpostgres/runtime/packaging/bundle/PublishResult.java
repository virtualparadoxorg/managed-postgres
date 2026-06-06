package eu.virtualparadox.managedpostgres.runtime.packaging.bundle;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Published runtime bundle artifact paths.
 *
 * @param bundle final archive artifact
 * @param bundleChecksum checksum sidecar for the final archive
 * @param manifest published manifest path
 */
public record PublishResult(Path bundle, Path bundleChecksum, Path manifest) {

    /**
     * Creates published artifact metadata.
     *
     * @param bundle final archive artifact
     * @param bundleChecksum checksum sidecar for the final archive
     * @param manifest published manifest path
     */
    public PublishResult {
        final Path validatedBundle = Objects.requireNonNull(bundle, "bundle");
        final Path validatedBundleChecksum = Objects.requireNonNull(bundleChecksum, "bundleChecksum");
        final Path validatedManifest = Objects.requireNonNull(manifest, "manifest");
        bundle = validatedBundle;
        bundleChecksum = validatedBundleChecksum;
        manifest = validatedManifest;
    }
}
