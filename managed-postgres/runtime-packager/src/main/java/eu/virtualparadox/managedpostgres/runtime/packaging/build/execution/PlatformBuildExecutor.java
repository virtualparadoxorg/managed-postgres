package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Delegates source-build execution to the unified Meson build backend.
 */
public final class PlatformBuildExecutor implements BuildExecutor {

    private final BuildExecutor buildExecutor;

    /**
     * Creates a platform build executor backed by {@link MesonBuildExecutor}.
     */
    public PlatformBuildExecutor() {
        this(new MesonBuildExecutor());
    }

    PlatformBuildExecutor(final BuildExecutor buildExecutor) {
        this.buildExecutor = Objects.requireNonNull(buildExecutor, "buildExecutor");
    }

    @Override
    public Path build(
            final PlatformBuildDriver driver,
            final PostgresRelease release,
            final Path sourceTree,
            final Path buildDirectory) {
        return buildExecutor.build(Objects.requireNonNull(driver, "driver"), release, sourceTree, buildDirectory);
    }
}
