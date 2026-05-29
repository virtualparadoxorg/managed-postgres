package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.util.Objects;

/**
 * Target-specific PostgreSQL source build driver contract.
 */
public sealed interface PlatformBuildDriver
        permits LinuxGlibcBuildDriver, LinuxMuslBuildDriver, MacosBuildDriver, WindowsBuildDriver {

    /**
     * Resolves the build driver for a runtime bundle target.
     *
     * @param targetPlatform runtime bundle target
     * @return target-specific build driver
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    static PlatformBuildDriver forTarget(final TargetPlatform targetPlatform) {
        final TargetPlatform validatedTargetPlatform = Objects.requireNonNull(targetPlatform, "targetPlatform");

        return switch (validatedTargetPlatform) {
            case MACOS_X86_64, MACOS_AARCH64 -> new MacosBuildDriver(validatedTargetPlatform);
            case LINUX_X86_64_GLIBC, LINUX_AARCH64_GLIBC -> new LinuxGlibcBuildDriver(validatedTargetPlatform);
            case LINUX_X86_64_MUSL, LINUX_AARCH64_MUSL -> new LinuxMuslBuildDriver(validatedTargetPlatform);
            case WINDOWS_X86_64 -> new WindowsBuildDriver(validatedTargetPlatform);
        };
    }

    /**
     * Returns the runtime bundle target handled by this driver.
     *
     * @return runtime bundle target
     */
    TargetPlatform targetPlatform();

    /**
     * Returns the rollout phase for the target handled by this driver.
     *
     * @return rollout phase
     */
    RolloutPhase rolloutPhase();
}
