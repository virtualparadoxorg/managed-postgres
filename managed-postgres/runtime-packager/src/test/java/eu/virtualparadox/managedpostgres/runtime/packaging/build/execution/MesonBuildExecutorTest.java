package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MesonBuildExecutorTest {

    @TempDir
    Path tempDir;

    MesonBuildExecutorTest() {
    }

    @Test
    void runsSetupCompileInstallAndFiltersOptionsToDeclaredSet() throws Exception {
        final Path sourceTree = tempDir.resolve("src");
        Files.createDirectories(sourceTree);
        Files.writeString(sourceTree.resolve("meson_options.txt"), """
                option('readline', type : 'feature', value : 'auto')
                option('zlib', type : 'feature', value : 'auto')
                option('ssl', type : 'combo', choices : ['auto', 'none', 'openssl'])
                option('plperl', type : 'feature', value : 'auto')
                """, StandardCharsets.UTF_8);

        final Path toolDir = tempDir.resolve("tools");
        final Path meson = fakeMeson(toolDir.resolve("meson"));
        final Path buildDirectory = tempDir.resolve("build");

        final MesonBuildExecutor executor = new MesonBuildExecutor(
                Map.of(), List.of(meson.toString()), new ProcessCommandExecutor());

        final Path installTree = executor.build(
                PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64),
                release(), sourceTree, buildDirectory);

        assertThat(installTree).isEqualTo(buildDirectory.resolve("install"));
        assertThat(installTree.resolve("bin/postgres")).exists();

        final String setup = Files.readString(sourceTree.resolve("setup.invocation"));
        assertThat(setup)
                .contains("setup")
                .contains("--prefix=" + buildDirectory.resolve("install"))
                .contains("-Dreadline=disabled")
                .contains("-Dssl=none")
                .contains("-Dplperl=disabled")
                .doesNotContain("-Dlibcurl")
                .doesNotContain("-Dzstd");
        assertThat(Files.readString(sourceTree.resolve("compile.invocation"))).contains("compile");
        assertThat(Files.readString(sourceTree.resolve("install.invocation"))).contains("install");
    }

    private static Path fakeMeson(final Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                #!/bin/sh
                set -eu
                sub="$1"
                case "$sub" in
                  setup)
                    builddir="$2"; srcdir="$3"
                    printf '%s\\n' "$*" > "$srcdir/setup.invocation"
                    mkdir -p "$builddir"
                    printf '%s\\n' "$srcdir" > "$builddir/.srcdir"
                    for a in "$@"; do case "$a" in --prefix=*) printf '%s\\n' "${a#--prefix=}" > "$builddir/.prefix";; esac; done
                    ;;
                  compile)
                    builddir="$3"; srcdir="$(cat "$builddir/.srcdir")"
                    printf '%s\\n' "$*" > "$srcdir/compile.invocation"
                    ;;
                  install)
                    builddir="$3"; srcdir="$(cat "$builddir/.srcdir")"; prefix="$(cat "$builddir/.prefix")"
                    printf '%s\\n' "$*" > "$srcdir/install.invocation"
                    mkdir -p "$prefix/bin"; : > "$prefix/bin/postgres"
                    ;;
                esac
                """, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        return path;
    }

    @Test
    void recoversFromConflictingPreGeneratedSourceFiles() throws Exception {
        final Path sourceTree = tempDir.resolve("src");
        Files.createDirectories(sourceTree);
        Files.writeString(sourceTree.resolve("meson_options.txt"),
                "option('readline', type : 'feature', value : 'auto')\n", StandardCharsets.UTF_8);
        final Path conflicting = sourceTree.resolve("gram.c");
        Files.writeString(conflicting, "pre-generated", StandardCharsets.UTF_8);

        final Path meson = fakeMesonWithConflict(tempDir.resolve("tools2").resolve("meson"));
        final Path buildDirectory = tempDir.resolve("build2");

        final MesonBuildExecutor executor = new MesonBuildExecutor(
                Map.of(), List.of(meson.toString()), new ProcessCommandExecutor());

        final Path installTree = executor.build(
                PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64),
                release(), sourceTree, buildDirectory);

        assertThat(conflicting).doesNotExist();
        assertThat(installTree.resolve("bin/postgres")).exists();
    }

    private static Path fakeMesonWithConflict(final Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                #!/bin/sh
                set -eu
                sub="$1"
                case "$sub" in
                  setup)
                    builddir="$2"; srcdir="$3"
                    mkdir -p "$builddir"
                    if [ -f "$srcdir/gram.c" ]; then
                      echo "Conflicting files in source directory:"
                      echo "  $srcdir/gram.c"
                      echo "The conflicting files need to be removed, either by removing the files listed"
                      echo "above, or by running configure and then make maintainer-clean."
                      exit 1
                    fi
                    printf '%s\\n' "$srcdir" > "$builddir/.srcdir"
                    for a in "$@"; do case "$a" in --prefix=*) printf '%s\\n' "${a#--prefix=}" > "$builddir/.prefix";; esac; done
                    ;;
                  compile)
                    :
                    ;;
                  install)
                    builddir="$3"; prefix="$(cat "$builddir/.prefix")"
                    mkdir -p "$prefix/bin"; : > "$prefix/bin/postgres"
                    ;;
                esac
                """, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        return path;
    }

    private static PostgresRelease release() {
        return new PostgresRelease(16, "16.14", URI.create("file:///tmp/postgresql-16.14.tar.gz"), "abc123");
    }
}
