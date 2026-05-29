package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import java.io.IOException;
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

final class WindowsBuildExecutorTest {

    @TempDir
    Path tempDir;

    WindowsBuildExecutorTest() {
    }

    @Test
    void rejectsWindowsSourceBuildsOnNonWindowsHosts() {
        final WindowsBuildExecutor executor = new WindowsBuildExecutor(Map.of(), List.of("cmd.exe", "/c"), "Mac OS X");

        assertThatThrownBy(() -> executor.build(
                        PlatformBuildDriver.forTarget(TargetPlatform.WINDOWS_X86_64),
                        release(),
                        tempDir.resolve("source"),
                        tempDir.resolve("build")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Windows host");
    }

    @Test
    void rejectsNonWindowsBuildDrivers() {
        final WindowsBuildExecutor executor = new WindowsBuildExecutor(Map.of(), List.of("cmd.exe", "/c"), "Windows 11");

        assertThatThrownBy(() -> executor.build(
                        PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64),
                        release(),
                        tempDir.resolve("source"),
                        tempDir.resolve("build")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Windows build driver");
    }

    @Test
    void rejectsSourceTreesWithoutMsvcDirectory() {
        final WindowsBuildExecutor executor = new WindowsBuildExecutor(Map.of(), List.of("cmd.exe", "/c"), "Windows 11");

        assertThatThrownBy(() -> executor.build(
                        PlatformBuildDriver.forTarget(TargetPlatform.WINDOWS_X86_64),
                        release(),
                        tempDir.resolve("source"),
                        tempDir.resolve("build")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("src/tools/msvc");
    }

    @Test
    void buildsWindowsInstallTreeWithMsvcBuildAndInstallCommands() throws IOException {
        final Path sourceTree = createWindowsSourceTree();
        final Path toolDirectory = createToolDirectory();
        final Path buildDirectory = tempDir.resolve("build");
        final WindowsBuildExecutor executor = new WindowsBuildExecutor(
                Map.of("PATH", toolDirectory + ":" + System.getenv("PATH")),
                List.of(toolDirectory.resolve("cmd.exe").toString(), "/c"),
                "Windows 11");

        final Path installTree = executor.build(
                PlatformBuildDriver.forTarget(TargetPlatform.WINDOWS_X86_64),
                release(),
                sourceTree,
                buildDirectory);

        assertThat(installTree).isEqualTo(buildDirectory.resolve("install"));
        assertThat(installTree.resolve("bin/postgres.exe")).exists();
        assertThat(sourceTree.resolve("src/tools/msvc/build.invocation")).hasContent("build");
        assertThat(sourceTree.resolve("src/tools/msvc/install.invocation")).hasContent(installTree.toString());
    }

    private Path createWindowsSourceTree() throws IOException {
        final Path msvcDirectory = tempDir.resolve("source").resolve("src").resolve("tools").resolve("msvc");
        Files.createDirectories(msvcDirectory);
        final Path buildScript = msvcDirectory.resolve("build.bat");
        Files.writeString(
                buildScript,
                """
                #!/bin/sh
                set -eu
                printf 'build' > "$PWD/build.invocation"
                """,
                StandardCharsets.UTF_8);
        makeExecutable(buildScript);
        final Path installScript = msvcDirectory.resolve("install.bat");
        Files.writeString(
                installScript,
                """
                #!/bin/sh
                set -eu
                printf '%s' "$1" > "$PWD/install.invocation"
                mkdir -p "$1/bin" "$1/lib" "$1/share"
                : > "$1/bin/postgres.exe"
                """,
                StandardCharsets.UTF_8);
        makeExecutable(installScript);
        return tempDir.resolve("source");
    }

    private Path createToolDirectory() throws IOException {
        final Path toolDirectory = tempDir.resolve("tools");
        Files.createDirectories(toolDirectory);
        final Path commandShell = toolDirectory.resolve("cmd.exe");
        Files.writeString(
                commandShell,
                """
                #!/bin/sh
                set -eu
                if [ "$1" != "/c" ]; then
                  exit 10
                fi
                shift
                first="$1"
                shift
                case "$first" in
                  /*) ;;
                  *)
                    if [ -x "$PWD/$first" ]; then
                      first="$PWD/$first"
                    elif [ -x "$PWD/$first.bat" ]; then
                      first="$PWD/$first.bat"
                    fi
                    ;;
                esac
                exec "$first" "$@"
                """,
                StandardCharsets.UTF_8);
        makeExecutable(commandShell);
        return toolDirectory;
    }

    private static PostgresRelease release() {
        return new PostgresRelease(16, "16.14", URI.create("file:///tmp/postgresql-16.14.tar.gz"), "abc123");
    }

    private static void makeExecutable(final Path path) throws IOException {
        Files.setPosixFilePermissions(
                path,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
    }
}
