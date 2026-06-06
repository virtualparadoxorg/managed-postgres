package eu.virtualparadox.managedpostgres.runtime.packaging.orchestration;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Source-build packaging request parameters.
 *
 * @param release PostgreSQL release metadata
 * @param targetPlatform runtime bundle target
 * @param revision packaging revision
 * @param outputDirectory publication directory
 * @param sourceCache source archive cache directory
 * @param workRoot work root directory
 */
public record RuntimePackagingRequest(
        PostgresRelease release,
        TargetPlatform targetPlatform,
        String revision,
        Path outputDirectory,
        Path sourceCache,
        Path workRoot) {

    /**
     * Creates a runtime packaging request.
     *
     * @param release PostgreSQL release metadata
     * @param targetPlatform runtime bundle target
     * @param revision packaging revision
     * @param outputDirectory publication directory
     * @param sourceCache source archive cache directory
     * @param workRoot work root directory
     */
    public RuntimePackagingRequest {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(targetPlatform, "targetPlatform");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(sourceCache, "sourceCache");
        Objects.requireNonNull(workRoot, "workRoot");
    }
}
