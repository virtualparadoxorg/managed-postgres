package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.SourceBuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.WindowsBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Delegates source-build execution to the platform-appropriate executor.
 */
public final class PlatformBuildExecutor implements BuildExecutor {

    private final BuildExecutor unixExecutor;
    private final BuildExecutor windowsExecutor;

    /**
     * Creates a platform build executor with default Unix and Windows delegates.
     */
    public PlatformBuildExecutor() {
        this(new SourceBuildExecutor(), new WindowsBuildExecutor());
    }

    PlatformBuildExecutor(final BuildExecutor unixExecutor, final BuildExecutor windowsExecutor) {
        this.unixExecutor = Objects.requireNonNull(unixExecutor, "unixExecutor");
        this.windowsExecutor = Objects.requireNonNull(windowsExecutor, "windowsExecutor");
    }

    @Override
    public Path build(
            final PlatformBuildDriver driver,
            final PostgresRelease release,
            final Path sourceTree,
            final Path buildDirectory) {
        final BuildExecutor executor = Objects.requireNonNull(driver, "driver") instanceof WindowsBuildDriver
                ? windowsExecutor
                : unixExecutor;
        return executor.build(driver, release, sourceTree, buildDirectory);
    }
}
