package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
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

final class SourceBuildExecutorTest {

    @TempDir
    Path tempDir;

    SourceBuildExecutorTest() {
    }

    @Test
    void buildsUnixInstallTreeWithConfigureAndMake() throws IOException {
        final Path sourceTree = createSourceTree();
        final Path toolDirectory = createToolDirectory();
        final Path buildDirectory = tempDir.resolve("build");
        final SourceBuildExecutor executor = new SourceBuildExecutor(
                Map.of("PATH", toolDirectory + ":" + System.getenv("PATH")),
                3,
                List.of(toolDirectory.resolve("make").toString()));

        final Path installTree = executor.build(
                PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64),
                new PostgresRelease(16, "16.14", URI.create("file:///tmp/postgresql-16.14.tar.gz"), "abc123"),
                sourceTree,
                buildDirectory);

        assertThat(installTree).isEqualTo(buildDirectory.resolve("install"));
        assertThat(installTree.resolve("bin/postgres")).exists();
        assertThat(installTree.resolve("lib")).exists();
        assertThat(installTree.resolve("share")).exists();
        assertThat(buildDirectory.resolve("configure.args"))
                .hasContent(String.join(
                        "\n",
                        "--prefix=" + installTree,
                        "--without-readline",
                        "--without-zlib",
                        "--without-icu",
                        "--without-ldap",
                        "--without-gssapi",
                        "--without-pam",
                        "--without-llvm"));
        assertThat(buildDirectory.resolve("make.jobs")).hasContent("-j3");
        assertThat(buildDirectory.resolve("make.install")).hasContent("install-world-bin");
    }

    @Test
    void buildsUnixInstallTreeWhenConfigureIsNotExecutable() throws IOException {
        final Path sourceTree = createSourceTree();
        final Path configure = sourceTree.resolve("configure");
        Files.setPosixFilePermissions(
                configure,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE));
        final Path toolDirectory = createToolDirectory();
        final Path buildDirectory = tempDir.resolve("build-non-executable-configure");
        final SourceBuildExecutor executor = new SourceBuildExecutor(
                Map.of("PATH", toolDirectory + ":" + System.getenv("PATH")),
                2,
                List.of(toolDirectory.resolve("make").toString()));

        final Path installTree = executor.build(
                PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64),
                new PostgresRelease(16, "16.14", URI.create("file:///tmp/postgresql-16.14.tar.gz"), "abc123"),
                sourceTree,
                buildDirectory);

        assertThat(installTree.resolve("bin/postgres")).exists();
        assertThat(buildDirectory.resolve("configure.args")).exists();
    }

    @Test
    void seedsGeneratedLwlockSourcesIntoBuildTreeBeforeMake() throws IOException {
        final Path sourceTree = createSourceTree();
        final Path lmgrDirectory = sourceTree.resolve("src/backend/storage/lmgr");
        Files.createDirectories(lmgrDirectory);
        Files.writeString(lmgrDirectory.resolve("lwlocknames.c"), "const char *const IndividualLWLockNames[] = {};\n");
        Files.writeString(lmgrDirectory.resolve("lwlocknames.h"), "#define NUM_INDIVIDUAL_LWLOCKS 0\n");
        final Path toolDirectory = createToolDirectory();
        final Path buildDirectory = tempDir.resolve("build-seeded-generated-sources");
        final SourceBuildExecutor executor = new SourceBuildExecutor(
                Map.of("PATH", toolDirectory + ":" + System.getenv("PATH")),
                1,
                List.of(toolDirectory.resolve("make").toString()));

        executor.build(
                PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64),
                new PostgresRelease(16, "16.14", URI.create("file:///tmp/postgresql-16.14.tar.gz"), "abc123"),
                sourceTree,
                buildDirectory);

        assertThat(buildDirectory.resolve("src/backend/storage/lmgr/lwlocknames.c"))
                .hasContent("const char *const IndividualLWLockNames[] = {};\n");
        assertThat(buildDirectory.resolve("src/backend/storage/lmgr/lwlocknames.h"))
                .hasContent("#define NUM_INDIVIDUAL_LWLOCKS 0\n");
    }

    @Test
    void rejectsWindowsUntilMsvcBuildPathExists() {
        final SourceBuildExecutor executor = new SourceBuildExecutor();

        assertThatThrownBy(() -> executor.build(
                        PlatformBuildDriver.forTarget(TargetPlatform.WINDOWS_X86_64),
                        new PostgresRelease(16, "16.14", URI.create("file:///tmp/postgresql-16.14.tar.gz"), "abc123"),
                        tempDir.resolve("source"),
                        tempDir.resolve("build")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("windows-x86_64");
    }

    @Test
    void usesSingleJobByDefaultForDeterministicSourceBuilds() {
        assertThat(SourceBuildExecutor.defaultParallelJobs()).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveParallelJobs() {
        assertThatThrownBy(() -> new SourceBuildExecutor(Map.of(), 0, List.of("make")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parallelJobs");
    }

    @Test
    void rejectsEmptyMakeCommand() {
        assertThatThrownBy(() -> new SourceBuildExecutor(Map.of(), 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("makeCommand");
    }

    private Path createSourceTree() throws IOException {
        final Path sourceTree = tempDir.resolve("source");
        Files.createDirectories(sourceTree);
        final Path configure = sourceTree.resolve("configure");
        Files.writeString(
                configure,
                """
                #!/bin/sh
                set -eu
                printf '%s\n' "$@" > "$PWD/configure.args"
                """,
                StandardCharsets.UTF_8);
        makeExecutable(configure);
        return sourceTree;
    }

    private Path createToolDirectory() throws IOException {
        final Path toolDirectory = tempDir.resolve("tools");
        Files.createDirectories(toolDirectory);
        final Path make = toolDirectory.resolve("make");
        Files.writeString(
                make,
                """
                #!/bin/sh
                set -eu
                if [ "$1" = "install-world-bin" ]; then
                  printf '%s' "$1" > "$PWD/make.install"
                  prefix="$(head -n 1 "$PWD/configure.args" | cut -d= -f2-)"
                  mkdir -p "$prefix/bin" "$prefix/lib" "$prefix/share"
                  : > "$prefix/bin/postgres"
                  exit 0
                fi
                printf '%s' "$1" > "$PWD/make.jobs"
                """,
                StandardCharsets.UTF_8);
        makeExecutable(make);
        return toolDirectory;
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
