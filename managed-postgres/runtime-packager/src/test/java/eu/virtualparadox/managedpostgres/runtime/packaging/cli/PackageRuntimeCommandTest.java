package eu.virtualparadox.managedpostgres.runtime.packaging.cli;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.orchestration.RuntimePackagingOrchestrator;
import eu.virtualparadox.managedpostgres.runtime.packaging.source.PostgresSourceCatalog;
import eu.virtualparadox.managedpostgres.runtime.packaging.testsupport.RawInstallTreeFixture;
import eu.virtualparadox.managedpostgres.runtime.packaging.testsupport.SourceArchiveFixture;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageRuntimeCommandTest {

    @TempDir
    Path tempDir;

    PackageRuntimeCommandTest() {
    }

    @Test
    void requiresVersionAndTarget() {
        final StringWriter output = new StringWriter();
        final StringWriter error = new StringWriter();

        final int exitCode = RuntimePackagerMain.execute(
                new String[] {"package"},
                new PrintWriter(output, true),
                new PrintWriter(error, true));

        assertThat(exitCode).isNotZero();
    }

    @Test
    void packagesRuntimeFromRawInstallTree() throws IOException {
        final Path rawInstallTree = RawInstallTreeFixture.create(tempDir);
        final Path outputDirectory = tempDir.resolve("dist");
        final Path workRoot = tempDir.resolve("work");
        final StringWriter output = new StringWriter();
        final StringWriter error = new StringWriter();

        final int exitCode = RuntimePackagerMain.execute(
                new String[] {
                    "package",
                    "--postgres-version",
                    "16.14",
                    "--target",
                    "macos-aarch64",
                    "--revision",
                    "r1",
                    "--output",
                    outputDirectory.toString(),
                    "--work-root",
                    workRoot.toString(),
                    "--raw-install-tree",
                    rawInstallTree.toString()
                },
                new PrintWriter(output, true),
                new PrintWriter(error, true));

        assertThat(exitCode).isZero();
        assertThat(outputDirectory.resolve("managed-postgres-runtime-pg16.14-macos-aarch64-r1.zip")).exists();
        assertThat(outputDirectory.resolve("managed-postgres-runtime-pg16.14-macos-aarch64-r1.zip.sha256")).exists();
        assertThat(outputDirectory.resolve("manifest.json")).exists();
    }

    @Test
    void returnsActionableErrorWhenSourceBuildExecutionIsUnavailable() throws IOException {
        final Path outputDirectory = tempDir.resolve("dist");
        final Path workRoot = tempDir.resolve("work");
        final Path sourceCache = tempDir.resolve("cache");
        final StringWriter output = new StringWriter();
        final StringWriter error = new StringWriter();
        final Path sourceArchive = SourceArchiveFixture.create(tempDir, "postgresql-16.14");
        final PackageRuntimeCommand command = createCommand(
                output,
                error,
                sourceArchive,
                new RuntimePackagingOrchestrator(),
                outputDirectory)
                .withRequiredOptions("16.14", "windows-x86_64", "r1", outputDirectory)
                .withSourceBuildDirectories(sourceCache, workRoot);

        final int exitCode = command.call();

        assertThat(exitCode).isEqualTo(2);
        assertThat(error.toString()).contains("windows-x86_64");
    }

    @Test
    void packagesRuntimeFromSourceArchiveViaOrchestrator() throws IOException {
        final Path outputDirectory = tempDir.resolve("dist-source");
        final Path workRoot = tempDir.resolve("work-source");
        final Path sourceCache = tempDir.resolve("cache-source");
        final StringWriter output = new StringWriter();
        final StringWriter error = new StringWriter();
        final Path sourceArchive = SourceArchiveFixture.create(tempDir, "postgresql-16.14");
        final BuildExecutor buildExecutor = (driver, release, sourceTree, buildDirectory) -> {
            assertThat(driver).isEqualTo(PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64));
            assertThat(Files.exists(sourceTree.resolve("README"))).isTrue();
            try {
                return RawInstallTreeFixture.create(buildDirectory);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
        final PackageRuntimeCommand command = createCommand(
                output,
                error,
                sourceArchive,
                new RuntimePackagingOrchestrator(buildExecutor),
                outputDirectory)
                .withRequiredOptions("16.14", "macos-aarch64", "r1", outputDirectory)
                .withSourceBuildDirectories(sourceCache, workRoot);

        final int exitCode = command.call();

        assertThat(exitCode).isZero();
        assertThat(outputDirectory.resolve("managed-postgres-runtime-pg16.14-macos-aarch64-r1.zip")).exists();
        assertThat(error.toString()).isEmpty();
    }

    @Test
    void packagesRuntimeFromSourceArchiveWithDefaultWorkDirectories() throws IOException {
        final Path outputDirectory = tempDir.resolve("dist-defaults");
        final StringWriter output = new StringWriter();
        final StringWriter error = new StringWriter();
        final Path sourceArchive = SourceArchiveFixture.create(tempDir, "postgresql-16.14-defaults");
        final BuildExecutor buildExecutor = (driver, release, sourceTree, buildDirectory) -> {
            assertThat(buildDirectory).hasToString(outputDirectory.resolve(".work/build/macos-aarch64").toString());
            try {
                return RawInstallTreeFixture.create(buildDirectory);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
        final PackageRuntimeCommand command = createCommand(
                output,
                error,
                sourceArchive,
                new RuntimePackagingOrchestrator(buildExecutor),
                outputDirectory)
                .withRequiredOptions("16.14", "macos-aarch64", "r1", outputDirectory);

        final int exitCode = command.call();

        assertThat(exitCode).isZero();
        assertThat(outputDirectory.resolve(".work/source-cache/postgresql-16.14-defaults.zip")).exists();
        assertThat(error.toString()).isEmpty();
    }

    private PackageRuntimeCommand createCommand(
            final StringWriter output,
            final StringWriter error,
            final Path sourceArchive,
            final RuntimePackagingOrchestrator orchestrator,
            final Path outputDirectory) throws IOException {
        return new PackageRuntimeCommand(
                new PrintWriter(output, true),
                new PrintWriter(error, true),
                new PackageRuntimeCommandDependencies(
                        new PostgresSourceCatalog(Map.of("16.14", SourceArchiveFixture.releaseForArchive(sourceArchive))),
                        new eu.virtualparadox.managedpostgres.runtime.packaging.bundle.BundleNormalizer(),
                        new eu.virtualparadox.managedpostgres.runtime.packaging.bundle.BundlePublisher(),
                        orchestrator))
                .withRequiredOptions("16.14", "macos-aarch64", "r1", outputDirectory);
    }

}
