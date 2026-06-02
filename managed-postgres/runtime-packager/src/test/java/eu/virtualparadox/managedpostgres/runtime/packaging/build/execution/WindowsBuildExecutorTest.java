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
        final String toolPath = String.join(
                ";",
                toolDirectory.resolve("Git/usr/bin").toString(),
                toolDirectory.resolve("Strawberry/perl/bin").toString(),
                toolDirectory.toString(),
                System.getenv("PATH"));
        final WindowsBuildExecutor executor = new WindowsBuildExecutor(
                Map.of("PATH", toolPath),
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
        assertThat(sourceTree.resolve("src/tools/msvc/perl.invocation"))
                .hasContent(toolDirectory.resolve("Strawberry/perl/bin/perl.exe").toString());
        final String strawberryPerlDirectory =
                toolDirectory.resolve("Strawberry/perl/bin").toString().replace("\\", "/");
        final String msbuildDirectory = toolDirectory.toString().replace("\\", "/");
        assertThat(sourceTree.resolve("src/tools/msvc/buildenv.pl"))
                .hasContent("$ENV{PATH} = \"" + strawberryPerlDirectory + ";" + msbuildDirectory + ";$ENV{PATH}\";\n");
    }

    @Test
    void mirrorsPathOverrideIntoWindowsPathAlias() {
        final Map<String, String> normalized = WindowsBuildExecutor.normalizeEnvironmentOverrides(Map.of(
                "PATH", "C:\\VS\\Tools;C:\\VS\\MSBuild",
                "INCLUDE", "C:\\VS\\Include",
                "LIB", "C:\\VS\\Lib"));

        assertThat(normalized)
                .containsEntry("PATH", "C:\\VS\\Tools;C:\\VS\\MSBuild")
                .containsEntry("Path", "C:\\VS\\Tools;C:\\VS\\MSBuild")
                .containsEntry("INCLUDE", "C:\\VS\\Include")
                .containsEntry("LIB", "C:\\VS\\Lib");
    }

    @Test
    void mirrorsWindowsPathAliasIntoUppercasePath() {
        final Map<String, String> normalized = WindowsBuildExecutor.normalizeEnvironmentOverrides(Map.of(
                "Path", "C:\\VS\\Tools;C:\\VS\\MSBuild"));

        assertThat(normalized)
                .containsEntry("Path", "C:\\VS\\Tools;C:\\VS\\MSBuild")
                .containsEntry("PATH", "C:\\VS\\Tools;C:\\VS\\MSBuild");
    }

    @Test
    void leavesDistinctPathVariantsUntouchedWhenBothArePresent() {
        final Map<String, String> normalized = WindowsBuildExecutor.normalizeEnvironmentOverrides(Map.of(
                "PATH", "C:\\Git\\usr\\bin;C:\\Windows\\System32",
                "Path", "C:\\VS\\Tools;C:\\VS\\MSBuild"));

        assertThat(normalized)
                .containsEntry("PATH", "C:\\Git\\usr\\bin;C:\\Windows\\System32")
                .containsEntry("Path", "C:\\VS\\Tools;C:\\VS\\MSBuild");
    }

    @Test
    void enablesWindowsDiagnosticsForExplicitOptInValue() {
        assertThat(WindowsBuildExecutor.diagnosticsEnabled(Map.of(
                        "MANAGED_POSTGRES_WINDOWS_DIAGNOSTICS", "1")))
                .isTrue();
    }

    @Test
    void disablesWindowsDiagnosticsForMissingOrNonOptInValues() {
        assertThat(WindowsBuildExecutor.diagnosticsEnabled(Map.of())).isFalse();
        assertThat(WindowsBuildExecutor.diagnosticsEnabled(Map.of(
                        "MANAGED_POSTGRES_WINDOWS_DIAGNOSTICS", "0")))
                .isFalse();
        assertThat(WindowsBuildExecutor.diagnosticsEnabled(Map.of(
                        "MANAGED_POSTGRES_WINDOWS_DIAGNOSTICS", "true")))
                .isFalse();
    }

    @Test
    void enablesWindowsDiagnosticsFromProcessEnvironmentWhenOverridesDoNotOptIn() {
        assertThat(WindowsBuildExecutor.diagnosticsEnabled(
                        Map.of(),
                        Map.of("MANAGED_POSTGRES_WINDOWS_DIAGNOSTICS", "1")))
                .isTrue();
    }

    @Test
    void windowsDiagnosticsScriptCapturesPathAndMsbuildResolution() {
        final String script = WindowsBuildExecutor.windowsDiagnosticsScriptContent("C:\\work\\windows-env.txt");

        assertThat(script)
                .contains("@echo off")
                .doesNotContain("(\n")
                .contains("> \"C:\\work\\windows-env.txt\" echo PATH=%PATH%")
                .contains(">> \"C:\\work\\windows-env.txt\" echo Path=%Path%")
                .contains(">> \"C:\\work\\windows-env.txt\" echo PATHEXT=%PATHEXT%")
                .contains(">> \"C:\\work\\windows-env.txt\" where msbuild 2^>^&1")
                .contains(">> \"C:\\work\\windows-env.txt\" where cl 2^>^&1")
                .contains(">> \"C:\\work\\windows-env.txt\" where link 2^>^&1")
                .contains("C:\\work\\windows-env.txt");
    }

    @Test
    void buildEnvironmentScriptPrependsResolvedPerlAndMsbuildDirectories() {
        assertThat(WindowsBuildExecutor.buildEnvironmentScriptContent(
                        List.of("C:\\Strawberry\\perl\\bin", "C:\\VS\\MSBuild\\Current\\Bin\\amd64")))
                .isEqualTo("$ENV{PATH} = \"C:/Strawberry/perl/bin;C:/VS/MSBuild/Current/Bin/amd64;$ENV{PATH}\";\n");
    }

    private Path createWindowsSourceTree() throws IOException {
        final Path msvcDirectory = tempDir.resolve("source").resolve("src").resolve("tools").resolve("msvc");
        Files.createDirectories(msvcDirectory);
        final Path buildScript = msvcDirectory.resolve("build.pl");
        Files.writeString(
                buildScript,
                """
                #!/bin/sh
                set -eu
                printf 'build' > "$PWD/build.invocation"
                """,
                StandardCharsets.UTF_8);
        makeExecutable(buildScript);
        final Path installScript = msvcDirectory.resolve("install.pl");
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
        final Path msbuildExecutable = toolDirectory.resolve("MSBuild.exe");
        Files.writeString(msbuildExecutable, "", StandardCharsets.UTF_8);
        final Path gitPerlDirectory = Files.createDirectories(toolDirectory.resolve("Git/usr/bin"));
        final Path gitPerlExecutable = gitPerlDirectory.resolve("perl.exe");
        Files.writeString(
                gitPerlExecutable,
                """
                #!/bin/sh
                set -eu
                echo "git-perl-should-not-run" >&2
                exit 17
                """,
                StandardCharsets.UTF_8);
        makeExecutable(gitPerlExecutable);
        final Path strawberryPerlDirectory = Files.createDirectories(toolDirectory.resolve("Strawberry/perl/bin"));
        final Path strawberryPerlExecutable = strawberryPerlDirectory.resolve("perl.exe");
        Files.writeString(
                strawberryPerlExecutable,
                """
                #!/bin/sh
                set -eu
                printf '%s' "$0" > "$PWD/perl.invocation"
                first="$1"
                shift
                case "$first" in
                  /*) ;;
                  *)
                    if [ -x "$PWD/$first" ]; then
                      first="$PWD/$first"
                    fi
                    ;;
                esac
                exec "$first" "$@"
                """,
                StandardCharsets.UTF_8);
        makeExecutable(strawberryPerlExecutable);
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
