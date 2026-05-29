package eu.virtualparadox.managedpostgres.runtime.packaging.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.testsupport.RawInstallTreeFixture;
import eu.virtualparadox.managedpostgres.runtime.packaging.testsupport.SourceArchiveFixture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimePackagingOrchestratorTest {

    @TempDir
    Path tempDir;

    RuntimePackagingOrchestratorTest() {
    }

    @Test
    void packagesRuntimeFromVerifiedSourceArchive() throws IOException {
        final Path sourceArchive = SourceArchiveFixture.create(tempDir, "postgresql-16.14");
        final PostgresRelease release = SourceArchiveFixture.releaseForArchive(sourceArchive);
        final Path outputDirectory = tempDir.resolve("dist");
        final Path sourceCache = tempDir.resolve("cache");
        final Path workRoot = tempDir.resolve("work");
        final BuildExecutor buildExecutor = (driver, packagedRelease, sourceTree, buildDirectory) -> {
            assertThat(driver.targetPlatform()).isEqualTo(TargetPlatform.MACOS_AARCH64);
            assertThat(packagedRelease.version()).isEqualTo("16.14");
            assertThat(sourceTree.getFileName()).hasToString("postgresql-16.14");
            assertThat(Files.exists(sourceTree.resolve("README"))).isTrue();
            try {
                return RawInstallTreeFixture.create(buildDirectory);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
        final RuntimePackagingOrchestrator orchestrator = new RuntimePackagingOrchestrator(buildExecutor);

        final RuntimePackagingResult result = orchestrator.packageRelease(new RuntimePackagingRequest(
                release,
                TargetPlatform.MACOS_AARCH64,
                "r1",
                outputDirectory,
                sourceCache,
                workRoot));

        assertThat(result.publishResult().bundle())
                .hasFileName("managed-postgres-runtime-pg16.14-macos-aarch64-r1.zip")
                .exists();
        assertThat(result.publishResult().bundleChecksum()).exists();
        assertThat(result.publishResult().manifest()).exists();
        assertThat(result.driver().targetPlatform()).isEqualTo(TargetPlatform.MACOS_AARCH64);
    }

    @Test
    void usesExtractionRootWhenArchiveDoesNotContainSingleTopLevelDirectory() throws IOException {
        final Path sourceArchive = SourceArchiveFixture.createFlat(tempDir, "postgresql-16.14-flat");
        final PostgresRelease release = SourceArchiveFixture.releaseForArchive(sourceArchive);
        final Path workRoot = tempDir.resolve("work-flat");
        final Path expectedSourceRoot = workRoot.resolve("source").resolve(TargetPlatform.MACOS_AARCH64.identifier());
        final BuildExecutor buildExecutor = (driver, packagedRelease, sourceTree, buildDirectory) -> {
            assertThat(driver.targetPlatform()).isEqualTo(TargetPlatform.MACOS_AARCH64);
            assertThat(packagedRelease.version()).isEqualTo("16.14");
            assertThat(sourceTree).isEqualTo(expectedSourceRoot);
            assertThat(Files.exists(sourceTree.resolve("README"))).isTrue();
            try {
                return RawInstallTreeFixture.create(buildDirectory);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
        final RuntimePackagingOrchestrator orchestrator = new RuntimePackagingOrchestrator(buildExecutor);

        final RuntimePackagingResult result = orchestrator.packageRelease(new RuntimePackagingRequest(
                release,
                TargetPlatform.MACOS_AARCH64,
                "r1",
                tempDir.resolve("dist-flat"),
                tempDir.resolve("cache-flat"),
                workRoot));

        assertThat(result.publishResult().bundle()).exists();
    }
}
