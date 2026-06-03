package eu.virtualparadox.managedpostgres.runtime.platform;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Detects the current host's runtime bundle target identifier.
 *
 * <p>The identifiers match the published runtime bundle targets
 * ({@code macos-x86_64}, {@code macos-aarch64}, {@code linux-x86_64-glibc},
 * {@code linux-aarch64-glibc}, {@code linux-x86_64-musl}, {@code linux-aarch64-musl},
 * {@code windows-x86_64}) so the downloader can pick the right asset automatically.
 */
public final class HostRuntimePlatform {

    private static final String MUSL = "musl";
    private static final String GLIBC = "glibc";

    private HostRuntimePlatform() {}

    /**
     * Resolves the runtime bundle target identifier for the current host.
     *
     * @return current host target identifier
     */
    public static String currentTargetIdentifier() {
        return detect(
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""),
                HostRuntimePlatform::muslLoaderPresent);
    }

    /**
     * Resolves the target identifier for the supplied host attributes.
     *
     * @param osName operating system name (e.g. {@code os.name})
     * @param osArch architecture name (e.g. {@code os.arch})
     * @param muslProbe probe returning {@code true} when the host uses the musl C library
     * @return runtime bundle target identifier
     */
    static String detect(final String osName, final String osArch, final BooleanSupplier muslProbe) {
        final String operatingSystem = operatingSystem(Objects.requireNonNull(osName, "osName"));
        final String architecture = architecture(Objects.requireNonNull(osArch, "osArch"));
        final String identifier;
        if ("linux".equals(operatingSystem)) {
            final String libc = Objects.requireNonNull(muslProbe, "muslProbe").getAsBoolean() ? MUSL : GLIBC;
            identifier = "linux-" + architecture + "-" + libc;
        } else {
            identifier = operatingSystem + "-" + architecture;
        }
        return identifier;
    }

    private static String operatingSystem(final String osName) {
        final String normalized = osName.toLowerCase(Locale.ROOT);
        final String operatingSystem;
        if (normalized.contains("win")) {
            operatingSystem = "windows";
        } else if (normalized.contains("mac") || normalized.contains("darwin")) {
            operatingSystem = "macos";
        } else if (normalized.contains("linux")) {
            operatingSystem = "linux";
        } else {
            throw new IllegalStateException("unsupported operating system: " + osName);
        }
        return operatingSystem;
    }

    private static String architecture(final String osArch) {
        final String normalized = osArch.toLowerCase(Locale.ROOT);
        final String architecture;
        if ("aarch64".equals(normalized) || "arm64".equals(normalized)) {
            architecture = "aarch64";
        } else if ("amd64".equals(normalized) || "x86_64".equals(normalized) || "x64".equals(normalized)) {
            architecture = "x86_64";
        } else {
            throw new IllegalStateException("unsupported architecture: " + osArch);
        }
        return architecture;
    }

    /**
     * Returns whether a musl dynamic loader is present (musl systems ship
     * {@code /lib/ld-musl-<arch>.so.1}; glibc systems do not).
     *
     * @return {@code true} when a musl loader is found under {@code /lib}
     */
    private static boolean muslLoaderPresent() {
        return muslLoaderPresentIn(Path.of("/lib"));
    }

    static boolean muslLoaderPresentIn(final Path libDirectory) {
        boolean present = false;
        if (Files.isDirectory(libDirectory)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(libDirectory, "ld-musl-*.so.1")) {
                present = entries.iterator().hasNext();
            } catch (IOException exception) {
                present = false;
            }
        }
        return present;
    }
}
