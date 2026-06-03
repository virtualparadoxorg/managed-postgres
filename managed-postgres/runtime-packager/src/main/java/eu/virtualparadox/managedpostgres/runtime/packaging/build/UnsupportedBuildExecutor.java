package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Placeholder build executor until platform-native source-build execution is wired in.
 */
public final class UnsupportedBuildExecutor implements BuildExecutor {

    /**
     * Creates an unsupported build executor.
     */
    public UnsupportedBuildExecutor() {}

    @Override
    public Path build(
            final PlatformBuildDriver driver,
            final PostgresRelease release,
            final Path sourceTree,
            final Path buildDirectory) {
        final PlatformBuildDriver validatedDriver = Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(sourceTree, "sourceTree");
        Objects.requireNonNull(buildDirectory, "buildDirectory");
        throw new UnsupportedOperationException("source-build driver execution is not implemented yet for "
                + validatedDriver.targetPlatform().identifier());
    }
}
