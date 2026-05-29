package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.util.Objects;

/**
 * Build driver for glibc-linked Linux PostgreSQL runtime bundles.
 *
 * @param targetPlatform Linux glibc target platform
 */
public record LinuxGlibcBuildDriver(TargetPlatform targetPlatform) implements PlatformBuildDriver {

    /**
     * Creates a Linux glibc build driver.
     *
     * @param targetPlatform Linux glibc target platform
     */
    public LinuxGlibcBuildDriver {
        final TargetPlatform validatedTargetPlatform = Objects.requireNonNull(targetPlatform, "targetPlatform");
        if (validatedTargetPlatform != TargetPlatform.LINUX_X86_64_GLIBC
                && validatedTargetPlatform != TargetPlatform.LINUX_AARCH64_GLIBC) {
            throw new IllegalArgumentException("Linux glibc build driver requires a glibc Linux target platform");
        }
        targetPlatform = validatedTargetPlatform;
    }

    @Override
    public RolloutPhase rolloutPhase() {
        final RolloutPhase rolloutPhase;
        if (targetPlatform == TargetPlatform.LINUX_X86_64_GLIBC) {
            rolloutPhase = RolloutPhase.PHASE_ONE;
        } else {
            rolloutPhase = RolloutPhase.PHASE_TWO;
        }

        return rolloutPhase;
    }
}
