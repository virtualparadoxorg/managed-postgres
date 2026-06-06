package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.util.Objects;

/**
 * Build driver for musl-linked Linux PostgreSQL runtime bundles.
 *
 * @param targetPlatform Linux musl target platform
 */
public record LinuxMuslBuildDriver(TargetPlatform targetPlatform) implements PlatformBuildDriver {

    /**
     * Creates a Linux musl build driver.
     *
     * @param targetPlatform Linux musl target platform
     */
    public LinuxMuslBuildDriver {
        final TargetPlatform validatedTargetPlatform = Objects.requireNonNull(targetPlatform, "targetPlatform");
        if (validatedTargetPlatform != TargetPlatform.LINUX_X86_64_MUSL
                && validatedTargetPlatform != TargetPlatform.LINUX_AARCH64_MUSL) {
            throw new IllegalArgumentException("Linux musl build driver requires a musl Linux target platform");
        }
        targetPlatform = validatedTargetPlatform;
    }

    @Override
    public RolloutPhase rolloutPhase() {
        return RolloutPhase.PHASE_TWO;
    }
}
