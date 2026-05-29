package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.nio.file.Path;
import java.util.List;
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
     * Returns deterministic configure arguments for Unix source builds.
     *
     * @param installDirectory staged installation prefix
     * @return configure command arguments
     */
    default List<String> configureArguments(final Path installDirectory) {
        final Path validatedInstallDirectory = Objects.requireNonNull(installDirectory, "installDirectory");
        return List.of(
                "--prefix=" + validatedInstallDirectory,
                "--without-readline",
                "--without-zlib",
                "--without-icu",
                "--without-ldap",
                "--without-gssapi",
                "--without-pam",
                "--without-llvm");
    }

    /**
     * Returns the rollout phase for the target handled by this driver.
     *
     * @return rollout phase
     */
    RolloutPhase rolloutPhase();
}
