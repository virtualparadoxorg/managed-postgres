package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * Returns the platform-independent minimal set of Meson feature settings
     * (option name to value) that keep the runtime bundle small and portable.
     *
     * <p>The build executor filters these against the options actually declared
     * by the source version and supplies the install prefix itself.
     *
     * @return ordered map of meson option name to value
     */
    default Map<String, String> mesonFeatureSettings() {
        final LinkedHashMap<String, String> settings = new LinkedHashMap<>();
        for (final String feature : List.of(
                "readline", "zlib", "icu", "ldap", "gssapi", "pam", "llvm", "nls",
                "libxml", "libxslt", "lz4", "zstd", "systemd", "selinux", "libcurl",
                "plperl", "plpython", "pltcl", "tap_tests", "docs", "docs_pdf",
                "bonjour", "bsd_auth", "dtrace")) {
            settings.put(feature, "disabled");
        }
        settings.put("ssl", "none");
        settings.put("uuid", "none");
        return Collections.unmodifiableMap(settings);
    }

    /**
     * Returns the rollout phase for the target handled by this driver.
     *
     * @return rollout phase
     */
    RolloutPhase rolloutPhase();
}
