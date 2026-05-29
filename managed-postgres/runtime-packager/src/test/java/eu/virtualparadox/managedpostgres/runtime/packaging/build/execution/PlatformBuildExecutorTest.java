package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PlatformBuildExecutorTest {

    @TempDir
    Path tempDir;

    PlatformBuildExecutorTest() {
    }

    @Test
    void delegatesWindowsTargetsToWindowsExecutor() {
        final CapturingBuildExecutor unixExecutor = new CapturingBuildExecutor(tempDir.resolve("unix-install"));
        final CapturingBuildExecutor windowsExecutor = new CapturingBuildExecutor(tempDir.resolve("windows-install"));
        final PlatformBuildExecutor executor = new PlatformBuildExecutor(unixExecutor, windowsExecutor);

        final Path installTree = executor.build(
                PlatformBuildDriver.forTarget(TargetPlatform.WINDOWS_X86_64),
                release(),
                tempDir.resolve("source"),
                tempDir.resolve("build"));

        assertThat(installTree).isEqualTo(tempDir.resolve("windows-install"));
        assertThat(unixExecutor.invocationCount).isZero();
        assertThat(windowsExecutor.invocationCount).isEqualTo(1);
    }

    @Test
    void delegatesUnixTargetsToUnixExecutor() {
        final CapturingBuildExecutor unixExecutor = new CapturingBuildExecutor(tempDir.resolve("unix-install"));
        final CapturingBuildExecutor windowsExecutor = new CapturingBuildExecutor(tempDir.resolve("windows-install"));
        final PlatformBuildExecutor executor = new PlatformBuildExecutor(unixExecutor, windowsExecutor);

        final Path installTree = executor.build(
                PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64),
                release(),
                tempDir.resolve("source"),
                tempDir.resolve("build"));

        assertThat(installTree).isEqualTo(tempDir.resolve("unix-install"));
        assertThat(unixExecutor.invocationCount).isEqualTo(1);
        assertThat(windowsExecutor.invocationCount).isZero();
    }

    private static PostgresRelease release() {
        return new PostgresRelease(16, "16.14", URI.create("file:///tmp/postgresql-16.14.tar.gz"), "abc123");
    }

    private static final class CapturingBuildExecutor implements BuildExecutor {

        private final Path installTree;
        private int invocationCount;

        private CapturingBuildExecutor(final Path installTree) {
            this.installTree = installTree;
        }

        @Override
        public Path build(
                final PlatformBuildDriver driver,
                final PostgresRelease release,
                final Path sourceTree,
                final Path buildDirectory) {
            invocationCount++;
            return installTree;
        }
    }
}
