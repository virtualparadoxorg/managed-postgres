package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.util.Objects;

/**
 * Build driver for Windows PostgreSQL runtime bundles.
 *
 * @param targetPlatform Windows target platform
 */
public record WindowsBuildDriver(TargetPlatform targetPlatform) implements PlatformBuildDriver {

    /**
     * Creates a Windows build driver.
     *
     * @param targetPlatform Windows target platform
     */
    public WindowsBuildDriver {
        final TargetPlatform validatedTargetPlatform = Objects.requireNonNull(targetPlatform, "targetPlatform");
        if (validatedTargetPlatform != TargetPlatform.WINDOWS_X86_64) {
            throw new IllegalArgumentException("Windows build driver requires the Windows x86_64 target platform");
        }
        targetPlatform = validatedTargetPlatform;
    }

    @Override
    public RolloutPhase rolloutPhase() {
        return RolloutPhase.PHASE_ONE;
    }
}
