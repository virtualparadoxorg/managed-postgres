package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.nio.file.Path;

/**
 * Executes a target-specific PostgreSQL source build and returns the staged install tree.
 */
@FunctionalInterface
public interface BuildExecutor {

    /**
     * Builds a raw PostgreSQL install tree from an extracted source directory.
     *
     * @param driver target-specific driver metadata
     * @param release PostgreSQL release metadata
     * @param sourceTree extracted PostgreSQL source tree
     * @param buildDirectory target-specific work directory
     * @return raw install tree containing PostgreSQL runtime files
     */
    Path build(PlatformBuildDriver driver, PostgresRelease release, Path sourceTree, Path buildDirectory);
}
