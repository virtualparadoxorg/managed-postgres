package eu.virtualparadox.managedpostgres.runtime.packaging.relocate;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Makes a macOS runtime bundle position independent before it is published.
 *
 * <p>PostgreSQL records absolute build-machine load paths in every Mach-O binary and library, so a
 * freshly built macOS bundle only loads on the build machine. This relocator rewrites each install
 * id to {@code @rpath/<name>} and each bundled dependency to a {@code @loader_path}-relative
 * reference, re-signs every modified file, then verifies that no build-machine path survives and
 * that the relocated executables actually launch. Non-macOS bundles are left untouched: their ELF
 * and PE payloads already relocate via mechanisms applied at link time.
 */
public final class MacosBundleRelocator {

    private static final List<String> SMOKE_EXECUTABLES = List.of("initdb", "psql");

    private final MachOTool machOTool;

    /**
     * Creates a relocator backed by the macOS binary toolchain.
     */
    public MacosBundleRelocator() {
        this(new ProcessMachOTool());
    }

    MacosBundleRelocator(final MachOTool machOTool) {
        this.machOTool = Objects.requireNonNull(machOTool, "machOTool");
    }

    /**
     * Relocates the normalized bundle when the target is macOS.
     *
     * @param bundleDirectory normalized bundle directory
     * @param targetPlatform bundle target platform
     */
    public void relocate(final Path bundleDirectory, final TargetPlatform targetPlatform) {
        final Path bundle = Objects.requireNonNull(bundleDirectory, "bundleDirectory");
        if (!Objects.requireNonNull(targetPlatform, "targetPlatform").isMacos()) {
            return;
        }

        final Path binaryDirectory = bundle.resolve("bin");
        final Path libraryDirectory = bundle.resolve("lib");
        final List<Path> machOFiles = collectMachOFiles(binaryDirectory, libraryDirectory);
        for (final Path machOFile : machOFiles) {
            relocateMachOFile(machOFile, libraryDirectory);
        }
        verifyRelocated(machOFiles);
        smokeTest(binaryDirectory);
    }

    private void relocateMachOFile(final Path machOFile, final Path libraryDirectory) {
        final Optional<String> installId = machOTool.installId(machOFile);
        final List<String> dependencies = machOTool.dependencies(machOFile);
        boolean modified = false;

        if (installId.filter(MachORelocationPolicy::requiresRewrite).isPresent()) {
            machOTool.setInstallId(machOFile, MachORelocationPolicy.relocatedInstallId(installId.orElseThrow()));
            modified = true;
        }

        final String relativePathToLibrary = relativePathToLibrary(machOFile, libraryDirectory);
        for (final String dependency : dependencies) {
            if (isInstallId(installId, dependency) || !MachORelocationPolicy.requiresRewrite(dependency)) {
                continue;
            }
            final String dependencyFileName = MachORelocationPolicy.fileName(dependency);
            requireBundledLibrary(libraryDirectory, dependencyFileName, machOFile, dependency);
            machOTool.changeDependency(
                    machOFile,
                    dependency,
                    MachORelocationPolicy.loaderRelativeDependency(relativePathToLibrary, dependencyFileName));
            modified = true;
        }

        if (modified) {
            machOTool.signAdHoc(machOFile);
        }
    }

    private void verifyRelocated(final List<Path> machOFiles) {
        final List<String> offenders = new ArrayList<>();
        for (final Path machOFile : machOFiles) {
            final Optional<String> installId = machOTool.installId(machOFile);
            installId
                    .filter(MachORelocationPolicy::requiresRewrite)
                    .ifPresent(id -> offenders.add(machOFile + " (install id) -> " + id));
            for (final String dependency : machOTool.dependencies(machOFile)) {
                if (!isInstallId(installId, dependency) && MachORelocationPolicy.requiresRewrite(dependency)) {
                    offenders.add(machOFile + " (dependency) -> " + dependency);
                }
            }
        }
        if (!offenders.isEmpty()) {
            throw new IllegalStateException("macOS runtime relocation left non-relocatable Mach-O load paths:"
                    + System.lineSeparator() + String.join(System.lineSeparator(), offenders));
        }
    }

    private void smokeTest(final Path binaryDirectory) {
        for (final String executableName : SMOKE_EXECUTABLES) {
            final Path executable = binaryDirectory.resolve(executableName);
            if (Files.isRegularFile(executable)) {
                machOTool.runVersionCheck(executable);
            }
        }
    }

    private static boolean isInstallId(final Optional<String> installId, final String dependency) {
        return installId.map(dependency::equals).orElse(false);
    }

    private static String relativePathToLibrary(final Path machOFile, final Path libraryDirectory) {
        final Path parent = Objects.requireNonNull(machOFile.getParent(), "machOFile.parent");
        return parent.relativize(libraryDirectory).toString();
    }

    private static void requireBundledLibrary(
            final Path libraryDirectory,
            final String dependencyFileName,
            final Path machOFile,
            final String dependency) {
        if (!Files.isRegularFile(libraryDirectory.resolve(dependencyFileName))) {
            throw new IllegalStateException("macOS runtime relocation found dependency '" + dependency + "' of "
                    + machOFile + " that is not bundled under lib/: " + dependencyFileName);
        }
    }

    private static List<Path> collectMachOFiles(final Path binaryDirectory, final Path libraryDirectory) {
        final List<Path> machOFiles = new ArrayList<>();
        for (final Path root : List.of(binaryDirectory, libraryDirectory)) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> entries = Files.walk(root)) {
                entries.filter(Files::isRegularFile)
                        .filter(path -> !Files.isSymbolicLink(path))
                        .filter(MacosBundleRelocator::isMachOFile)
                        .forEach(machOFiles::add);
            } catch (final IOException exception) {
                throw new UncheckedIOException("failed to scan macOS bundle for Mach-O payload: " + root, exception);
            }
        }
        return machOFiles;
    }

    private static boolean isMachOFile(final Path file) {
        final byte[] header = new byte[4];
        final int read;
        try (InputStream input = Files.newInputStream(file)) {
            read = input.readNBytes(header, 0, header.length);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to read Mach-O header: " + file, exception);
        }
        return read >= header.length && MachORelocationPolicy.isMachOMagic(header);
    }
}
