package eu.virtualparadox.managedpostgres.runtime.packaging.relocate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure relocation policy for macOS Mach-O runtime payloads.
 *
 * <p>PostgreSQL is configured with an absolute {@code --prefix} at build time, so every Mach-O
 * binary and dynamic library records its load commands (its own install id and each dependency)
 * as an absolute path inside the build machine's working directory. Such a bundle only loads on
 * the build machine, because {@code dyld} cannot find those paths anywhere else.
 *
 * <p>This policy holds the side-effect-free decisions that make a bundle position independent:
 * which load paths must be rewritten, how an install id is made relocatable ({@code @rpath}), and
 * how a dependency is expressed relative to the loading binary ({@code @loader_path}). It also
 * recognises Mach-O files by their magic bytes so non-binary payload is left untouched.
 */
public final class MachORelocationPolicy {

    private static final int MH_MAGIC = 0xFEEDFACE;
    private static final int MH_MAGIC_64 = 0xFEEDFACF;
    private static final int MH_CIGAM = 0xCEFAEDFE;
    private static final int MH_CIGAM_64 = 0xCFFAEDFE;
    private static final int FAT_MAGIC = 0xCAFEBABE;
    private static final int FAT_CIGAM = 0xBEBAFECA;

    private static final Set<Integer> MACH_O_MAGICS =
            Set.of(MH_MAGIC, MH_MAGIC_64, MH_CIGAM, MH_CIGAM_64, FAT_MAGIC, FAT_CIGAM);

    private static final String COMPATIBILITY_MARKER = "(compatibility version";

    private MachORelocationPolicy() {}

    /**
     * Determines whether the supplied file header is a Mach-O magic number.
     *
     * @param header at least the first four bytes of a candidate file
     * @return {@code true} when the header is a thin or universal Mach-O magic number
     */
    public static boolean isMachOMagic(final byte[] header) {
        final boolean machO;
        if (Objects.requireNonNull(header, "header").length < 4) {
            machO = false;
        } else {
            final int magic = ((header[0] & 0xFF) << 24)
                    | ((header[1] & 0xFF) << 16)
                    | ((header[2] & 0xFF) << 8)
                    | (header[3] & 0xFF);
            machO = MACH_O_MAGICS.contains(magic);
        }
        return machO;
    }

    /**
     * Returns whether the load path already resolves relative to the loading binary.
     *
     * @param loadPath recorded Mach-O load path
     * @return {@code true} for {@code @rpath}, {@code @loader_path} and {@code @executable_path} paths
     */
    public static boolean isLoaderRelative(final String loadPath) {
        return Objects.requireNonNull(loadPath, "loadPath").startsWith("@");
    }

    /**
     * Returns whether the load path points at a macOS system library that must never be rewritten.
     *
     * @param loadPath recorded Mach-O load path
     * @return {@code true} for {@code /usr/lib} and {@code /System} system libraries
     */
    public static boolean isSystemPath(final String loadPath) {
        final String value = Objects.requireNonNull(loadPath, "loadPath");
        return value.startsWith("/usr/lib/") || value.startsWith("/System/");
    }

    /**
     * Returns whether the load path must be rewritten to become relocatable.
     *
     * <p>Any absolute path that is neither loader relative nor a system library is treated as a
     * build-machine path that must be rewritten. This is deliberately broader than matching a
     * single CI working directory, so locally produced bundles are normalised the same way.
     *
     * @param loadPath recorded Mach-O load path
     * @return {@code true} when the load path must be rewritten
     */
    public static boolean requiresRewrite(final String loadPath) {
        final String value = Objects.requireNonNull(loadPath, "loadPath");
        return !isLoaderRelative(value) && !isSystemPath(value);
    }

    /**
     * Returns the final path segment of a load path.
     *
     * @param loadPath recorded Mach-O load path
     * @return the file name component of the load path
     */
    public static String fileName(final String loadPath) {
        final String value = Objects.requireNonNull(loadPath, "loadPath");
        final int separator = value.lastIndexOf('/');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    /**
     * Returns the relocatable {@code @rpath} install id for a library.
     *
     * @param installId current absolute install id
     * @return relocatable install id of the form {@code @rpath/<name>}
     */
    public static String relocatedInstallId(final String installId) {
        return "@rpath/" + fileName(installId);
    }

    /**
     * Builds a {@code @loader_path}-relative dependency reference.
     *
     * @param relativePathToLibraryDirectory path from the binary's directory to the bundle {@code lib}
     *     directory, for example {@code ../lib} for executables or an empty string for siblings inside
     *     {@code lib}
     * @param dependencyFileName bundled dependency file name
     * @return a self-contained {@code @loader_path} dependency reference
     */
    public static String loaderRelativeDependency(
            final String relativePathToLibraryDirectory, final String dependencyFileName) {
        final String relative =
                Objects.requireNonNull(relativePathToLibraryDirectory, "relativePathToLibraryDirectory");
        final String name = Objects.requireNonNull(dependencyFileName, "dependencyFileName");
        final StringBuilder reference = new StringBuilder("@loader_path");
        if (!relative.isEmpty()) {
            reference.append('/').append(relative);
        }
        return reference.append('/').append(name).toString();
    }

    /**
     * Parses the install id printed by {@code otool -D}.
     *
     * @param otoolInstallIdOutput raw {@code otool -D} output
     * @return the install id, or empty when the Mach-O file declares none (executables)
     */
    public static Optional<String> parseInstallId(final String otoolInstallIdOutput) {
        Optional<String> installId = Optional.empty();
        for (final String raw : lines(otoolInstallIdOutput)) {
            final String line = raw.strip();
            if (!line.isEmpty() && !line.endsWith(":")) {
                installId = Optional.of(line);
                break;
            }
        }
        return installId;
    }

    /**
     * Parses the dependency load paths printed by {@code otool -L}.
     *
     * <p>For a dynamic library {@code otool -L} lists the library's own install id as the first
     * entry; it is returned here too and callers de-duplicate it against {@link #parseInstallId}.
     *
     * @param otoolDependencyOutput raw {@code otool -L} output
     * @return the recorded dependency load paths in declaration order
     */
    public static List<String> parseDependencies(final String otoolDependencyOutput) {
        final List<String> dependencies = new ArrayList<>();
        for (final String raw : lines(otoolDependencyOutput)) {
            final String line = raw.strip();
            final int marker = line.indexOf(COMPATIBILITY_MARKER);
            if (marker < 0) {
                continue;
            }
            final String dependency = line.substring(0, marker).strip();
            if (!dependency.isEmpty()) {
                dependencies.add(dependency);
            }
        }
        return dependencies;
    }

    private static List<String> lines(final String text) {
        return List.of(Objects.requireNonNull(text, "text").split("\\R", -1));
    }
}
