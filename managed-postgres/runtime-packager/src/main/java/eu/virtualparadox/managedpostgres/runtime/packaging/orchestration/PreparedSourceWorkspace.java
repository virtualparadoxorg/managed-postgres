package eu.virtualparadox.managedpostgres.runtime.packaging.orchestration;

import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Prepared source-build workspace metadata.
 *
 * @param driver resolved target-specific build driver
 * @param sourceTree extracted PostgreSQL source tree
 * @param buildDirectory target-specific build directory
 */
public record PreparedSourceWorkspace(
        PlatformBuildDriver driver,
        Path sourceTree,
        Path buildDirectory) {

    /**
     * Creates prepared source workspace metadata.
     *
     * @param driver resolved target-specific build driver
     * @param sourceTree extracted PostgreSQL source tree
     * @param buildDirectory target-specific build directory
     */
    public PreparedSourceWorkspace {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(sourceTree, "sourceTree");
        Objects.requireNonNull(buildDirectory, "buildDirectory");
    }
}
