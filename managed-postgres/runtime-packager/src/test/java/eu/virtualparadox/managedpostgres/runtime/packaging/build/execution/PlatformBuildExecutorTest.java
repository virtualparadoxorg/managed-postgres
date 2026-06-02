package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PlatformBuildExecutorTest {

    PlatformBuildExecutorTest() {
    }

    @Test
    void delegatesEveryPlatformToTheSingleBuildExecutor() {
        final List<TargetPlatform> seen = new ArrayList<>();
        final BuildExecutor recording = (driver, release, sourceTree, buildDirectory) -> {
            seen.add(driver.targetPlatform());
            return buildDirectory.resolve("install");
        };
        final PlatformBuildExecutor executor = new PlatformBuildExecutor(recording);

        for (final TargetPlatform target : TargetPlatform.values()) {
            executor.build(PlatformBuildDriver.forTarget(target), release(), Path.of("src"), Path.of("build"));
        }

        assertThat(seen).containsExactlyElementsOf(List.of(TargetPlatform.values()));
    }

    private static PostgresRelease release() {
        return new PostgresRelease(16, "16.14", URI.create("file:///tmp/postgresql-16.14.tar.gz"), "abc123");
    }
}
