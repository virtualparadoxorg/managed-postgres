package eu.virtualparadox.managedpostgres.runtime.packaging.relocate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MacosBundleRelocatorTest {

    private static final int MACHO_MAGIC = 0xCFFAEDFE;
    private static final String BUILD = "/Users/runner/work/mp/install/lib/";
    private static final String SYSTEM = "/usr/lib/libSystem.B.dylib";

    @TempDir
    Path tempDir;

    MacosBundleRelocatorTest() {}

    @Test
    void rewritesInstallIdsAndDependenciesToLoaderRelativeReferencesAndResigns() throws IOException {
        final Path bundle = bundleWithStandardLayout();
        final RecordingMachOTool tool = standardTool(bundle);

        new MacosBundleRelocator(tool).relocate(bundle, TargetPlatform.MACOS_AARCH64);

        assertThat(tool.dependencies(bundle.resolve("bin/initdb")))
                .containsExactly("@loader_path/../lib/libpq.5.dylib", SYSTEM);
        assertThat(tool.installId(bundle.resolve("lib/libpq.5.dylib"))).contains("@rpath/libpq.5.dylib");
        assertThat(tool.installId(bundle.resolve("lib/dblink.dylib"))).contains("@rpath/dblink.dylib");
        assertThat(tool.dependencies(bundle.resolve("lib/dblink.dylib")))
                .containsExactly("@rpath/dblink.dylib", "@loader_path/libpq.5.dylib", SYSTEM);
        assertThat(tool.signed)
                .contains(
                        bundle.resolve("bin/initdb"),
                        bundle.resolve("bin/psql"),
                        bundle.resolve("lib/libpq.5.dylib"),
                        bundle.resolve("lib/dblink.dylib"));
    }

    @Test
    void leavesAlreadyRelocatableBinariesUntouched() throws IOException {
        final Path bundle = bundleWithStandardLayout();
        final RecordingMachOTool tool = standardTool(bundle);

        new MacosBundleRelocator(tool).relocate(bundle, TargetPlatform.MACOS_AARCH64);

        // bin/postgres only links a system library, so it is never modified nor re-signed.
        assertThat(tool.signed).doesNotContain(bundle.resolve("bin/postgres"));
    }

    @Test
    void smokeTestsRelocatedClientExecutables() throws IOException {
        final Path bundle = bundleWithStandardLayout();
        final RecordingMachOTool tool = standardTool(bundle);

        new MacosBundleRelocator(tool).relocate(bundle, TargetPlatform.MACOS_AARCH64);

        assertThat(tool.smoked).containsExactlyInAnyOrder(bundle.resolve("bin/initdb"), bundle.resolve("bin/psql"));
    }

    @Test
    void skipsNonMachOPayload() throws IOException {
        final Path bundle = bundleWithStandardLayout();
        Files.writeString(bundle.resolve("lib/libpq.a"), "!<arch>\nstatic", StandardCharsets.UTF_8);
        Files.writeString(bundle.resolve("lib/pgxs.mk"), "# makefile\n", StandardCharsets.UTF_8);
        final RecordingMachOTool tool = standardTool(bundle);

        new MacosBundleRelocator(tool).relocate(bundle, TargetPlatform.MACOS_AARCH64);

        assertThat(tool.inspected).doesNotContain(bundle.resolve("lib/libpq.a"), bundle.resolve("lib/pgxs.mk"));
    }

    @Test
    void failsWhenADependencyIsNotBundled() throws IOException {
        final Path bundle = bundleWithStandardLayout();
        final RecordingMachOTool tool = standardTool(bundle);
        tool.seedLibrary(
                bundle.resolve("lib/dblink.dylib"), BUILD + "dblink.dylib", BUILD + "libmissing.dylib", SYSTEM);

        assertThatThrownBy(() -> new MacosBundleRelocator(tool).relocate(bundle, TargetPlatform.MACOS_AARCH64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not bundled under lib/")
                .hasMessageContaining("libmissing.dylib");
    }

    @Test
    void failsWhenABuildPathSurvivesRelocation() throws IOException {
        final Path bundle = bundleWithStandardLayout();
        final RecordingMachOTool tool = standardTool(bundle);
        tool.freezeChanges = true; // simulate install_name_tool silently not applying a change

        assertThatThrownBy(() -> new MacosBundleRelocator(tool).relocate(bundle, TargetPlatform.MACOS_AARCH64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-relocatable Mach-O load paths");
    }

    @Test
    void leavesNonMacosBundlesUntouched() throws IOException {
        final Path bundle = bundleWithStandardLayout();
        final RecordingMachOTool tool = standardTool(bundle);

        new MacosBundleRelocator(tool).relocate(bundle, TargetPlatform.LINUX_X86_64_GLIBC);

        assertThat(tool.commands).isEmpty();
        assertThat(tool.signed).isEmpty();
        assertThat(tool.smoked).isEmpty();
    }

    private Path bundleWithStandardLayout() throws IOException {
        final Path bundle = tempDir.resolve("normalized");
        Files.createDirectories(bundle.resolve("bin"));
        Files.createDirectories(bundle.resolve("lib"));
        for (final String binary : List.of("initdb", "psql", "postgres")) {
            writeMachO(bundle.resolve("bin").resolve(binary));
        }
        for (final String library : List.of("libpq.5.dylib", "dblink.dylib")) {
            writeMachO(bundle.resolve("lib").resolve(library));
        }
        return bundle;
    }

    private static RecordingMachOTool standardTool(final Path bundle) {
        final RecordingMachOTool tool = new RecordingMachOTool();
        tool.seedExecutable(bundle.resolve("bin/initdb"), BUILD + "libpq.5.dylib", SYSTEM);
        tool.seedExecutable(bundle.resolve("bin/psql"), BUILD + "libpq.5.dylib", SYSTEM);
        tool.seedExecutable(bundle.resolve("bin/postgres"), SYSTEM);
        tool.seedLibrary(bundle.resolve("lib/libpq.5.dylib"), BUILD + "libpq.5.dylib", BUILD + "libpq.5.dylib", SYSTEM);
        tool.seedLibrary(
                bundle.resolve("lib/dblink.dylib"),
                BUILD + "dblink.dylib",
                BUILD + "dblink.dylib",
                BUILD + "libpq.5.dylib",
                SYSTEM);
        return tool;
    }

    private static void writeMachO(final Path file) throws IOException {
        Files.write(file, new byte[] {
            (byte) (MACHO_MAGIC >>> 24), (byte) (MACHO_MAGIC >>> 16), (byte) (MACHO_MAGIC >>> 8), (byte) MACHO_MAGIC
        });
    }

    /**
     * In-memory {@link MachOTool} that models {@code otool}/{@code install_name_tool} behaviour:
     * the install id appears as the first dependency of a library, and edits update the recorded
     * state so a later inspection observes the relocated load paths.
     */
    private static final class RecordingMachOTool implements MachOTool {

        private final Map<Path, String> installIds = new HashMap<>();
        private final Map<Path, List<String>> dependencies = new HashMap<>();
        private final List<String> commands = new ArrayList<>();
        private final List<Path> signed = new ArrayList<>();
        private final List<Path> smoked = new ArrayList<>();
        private final List<Path> inspected = new ArrayList<>();
        private boolean freezeChanges;

        private void seedExecutable(final Path file, final String... dependencyPaths) {
            dependencies.put(file, new ArrayList<>(List.of(dependencyPaths)));
        }

        private void seedLibrary(final Path file, final String installId, final String... dependencyPaths) {
            installIds.put(file, installId);
            dependencies.put(file, new ArrayList<>(List.of(dependencyPaths)));
        }

        @Override
        public Optional<String> installId(final Path file) {
            inspected.add(file);
            return Optional.ofNullable(installIds.get(file));
        }

        @Override
        public List<String> dependencies(final Path file) {
            inspected.add(file);
            return List.copyOf(dependencies.getOrDefault(file, List.of()));
        }

        @Override
        public void setInstallId(final Path file, final String installId) {
            commands.add("id " + file.getFileName() + " " + installId);
            if (freezeChanges) {
                return;
            }
            final String previous = installIds.get(file);
            final List<String> current = new ArrayList<>(dependencies.getOrDefault(file, List.of()));
            current.replaceAll(dependency -> dependency.equals(previous) ? installId : dependency);
            dependencies.put(file, current);
            installIds.put(file, installId);
        }

        @Override
        public void changeDependency(final Path file, final String oldReference, final String newReference) {
            commands.add("change " + file.getFileName() + " " + oldReference + " -> " + newReference);
            if (freezeChanges) {
                return;
            }
            final List<String> current = new ArrayList<>(dependencies.getOrDefault(file, List.of()));
            current.replaceAll(dependency -> dependency.equals(oldReference) ? newReference : dependency);
            dependencies.put(file, current);
        }

        @Override
        public void signAdHoc(final Path file) {
            signed.add(file);
        }

        @Override
        public void runVersionCheck(final Path executable) {
            smoked.add(executable);
        }
    }
}
