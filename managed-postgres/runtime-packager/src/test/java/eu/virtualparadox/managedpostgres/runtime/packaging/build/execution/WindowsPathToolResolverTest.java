package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WindowsPathToolResolverTest {

    @TempDir
    Path tempDir;

    WindowsPathToolResolverTest() {
    }

    @Test
    void resolvesFirstExecutableFromWindowsPathAlias() throws IOException {
        final Path toolDirectory = Files.createDirectories(tempDir.resolve("tools"));
        final Path executable = toolDirectory.resolve("MSBuild.exe");
        Files.writeString(executable, "", StandardCharsets.UTF_8);

        final WindowsPathToolResolver resolver = new WindowsPathToolResolver(
                Map.of("Path", toolDirectory.toString()),
                Map.of());

        assertThat(resolver.resolveExecutableOnPath("MSBuild.exe")).contains(executable);
    }

    @Test
    void prefersStrawberryPerlOverGitPerl() throws IOException {
        final Path gitPerl = createExecutable(tempDir.resolve("Git/usr/bin/perl.exe"));
        final Path strawberryPerl = createExecutable(tempDir.resolve("Strawberry/perl/bin/perl.exe"));
        final Path gitPerlDirectory = Objects.requireNonNull(gitPerl.getParent(), "gitPerl.parent");
        final Path strawberryPerlDirectory =
                Objects.requireNonNull(strawberryPerl.getParent(), "strawberryPerl.parent");

        final WindowsPathToolResolver resolver = new WindowsPathToolResolver(
                Map.of("PATH", String.join(";", gitPerlDirectory.toString(), strawberryPerlDirectory.toString())),
                Map.of());

        assertThat(resolver.resolvePreferredPerlExecutable()).contains(strawberryPerl);
    }

    @Test
    void fallsBackToFirstPerlWhenStrawberryIsAbsent() throws IOException {
        final Path gitPerl = createExecutable(tempDir.resolve("Git/usr/bin/perl.exe"));
        final Path gitPerlDirectory = Objects.requireNonNull(gitPerl.getParent(), "gitPerl.parent");

        final WindowsPathToolResolver resolver = new WindowsPathToolResolver(
                Map.of(),
                Map.of("PATH", gitPerlDirectory.toString()));

        assertThat(resolver.resolvePreferredPerlExecutable()).contains(gitPerl);
    }

    @Test
    void returnsEmptyWhenExecutableCannotBeResolved() {
        final WindowsPathToolResolver resolver = new WindowsPathToolResolver(
                Map.of("PATH", ""),
                Map.of());

        assertThat(resolver.resolveExecutableOnPath("MSBuild.exe")).isEmpty();
        assertThat(resolver.resolvePreferredPerlExecutable()).isEmpty();
    }

    @Test
    void prefersOverridePathBeforeProcessEnvironmentPath() throws IOException {
        final Path overrideExecutable = createExecutable(tempDir.resolve("override/MSBuild.exe"));
        final Path processExecutable = createExecutable(tempDir.resolve("process/MSBuild.exe"));

        final WindowsPathToolResolver resolver = new WindowsPathToolResolver(
                Map.of("PATH", Objects.requireNonNull(overrideExecutable.getParent(), "overrideExecutable.parent")
                        .toString()),
                Map.of("PATH", Objects.requireNonNull(processExecutable.getParent(), "processExecutable.parent")
                        .toString()));

        assertThat(resolver.resolveExecutableOnPath("MSBuild.exe")).contains(overrideExecutable);
    }

    private static Path createExecutable(final Path path) throws IOException {
        Files.createDirectories(Objects.requireNonNull(path.getParent(), "path.parent"));
        Files.writeString(path, "", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(
                path,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        return path;
    }
}
