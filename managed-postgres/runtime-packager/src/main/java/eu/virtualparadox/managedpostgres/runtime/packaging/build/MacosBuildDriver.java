package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.util.Objects;

/**
 * Build driver for macOS PostgreSQL runtime bundles.
 *
 * @param targetPlatform macOS target platform
 */
public record MacosBuildDriver(TargetPlatform targetPlatform) implements PlatformBuildDriver {

    /**
     * Creates a macOS build driver.
     *
     * @param targetPlatform macOS target platform
     */
    public MacosBuildDriver {
        final TargetPlatform validatedTargetPlatform = Objects.requireNonNull(targetPlatform, "targetPlatform");
        if (validatedTargetPlatform != TargetPlatform.MACOS_X86_64
                && validatedTargetPlatform != TargetPlatform.MACOS_AARCH64) {
            throw new IllegalArgumentException("macOS build driver requires a macOS target platform");
        }
        targetPlatform = validatedTargetPlatform;
    }

    @Override
    public RolloutPhase rolloutPhase() {
        return RolloutPhase.PHASE_ONE;
    }
}
