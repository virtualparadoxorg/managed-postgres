package eu.virtualparadox.managedpostgres.lifecycle.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostmasterPidFileTest {

    @TempDir
    private Path dataDirectory;

    PostmasterPidFileTest() {}

    @Test
    void missingPostmasterPidFileHasNoPid() {
        assertThat(readPid()).isEmpty();
    }

    @Test
    void malformedPostmasterPidFileHasNoPid() throws IOException {
        writePidFile("not-a-pid%n5432%n");

        assertThat(readPid()).isEmpty();
    }

    @Test
    void zeroPostmasterPidHasNoPid() throws IOException {
        writePidFile("0%n");

        assertThat(readPid()).isEmpty();
    }

    @Test
    void negativePostmasterPidHasNoPid() throws IOException {
        writePidFile("-1%n");

        assertThat(readPid()).isEmpty();
    }

    @Test
    void validPostmasterPidReturnsFirstLinePid() throws IOException {
        writePidFile("12345%n%s%n%s%n".formatted(dataDirectory, "1700000000"));

        assertThat(readPid()).hasValue(12_345L);
    }

    @Test
    void strictReadFailsWhenPostmasterPidCannotBeRead() throws IOException {
        Assumptions.assumeTrue(
                Files.getFileStore(dataDirectory).supportsFileAttributeView(PosixFileAttributeView.class));
        writePidFile("12345%n");
        final Path pidPath = dataDirectory.resolve("postmaster.pid");
        final Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(pidPath);
        try {
            Files.setPosixFilePermissions(pidPath, Set.of());

            assertThatIOException().isThrownBy(() -> PostmasterPidFile.readPidStrict(dataDirectory));
        } finally {
            Files.setPosixFilePermissions(pidPath, originalPermissions);
        }
    }

    @Test
    void safetyFailsWhenPostmasterPidCannotBeRead() throws IOException {
        Assumptions.assumeTrue(
                Files.getFileStore(dataDirectory).supportsFileAttributeView(PosixFileAttributeView.class));
        final PostgresLayout layout = PostgresLayoutFixture.createdLayout(dataDirectory.resolve("storage"));
        final Path pidPath = layout.dataDirectory().resolve("postmaster.pid");
        Files.writeString(pidPath, "12345%n");
        final Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(pidPath);
        try {
            Files.setPosixFilePermissions(pidPath, Set.of());

            assertThatThrownBy(() -> PostmasterPidSafety.failIfLivePostmaster(layout))
                    .isInstanceOf(PostgresAttachException.class)
                    .hasMessageContaining("postmaster.pid");
        } finally {
            Files.setPosixFilePermissions(pidPath, originalPermissions);
        }
    }

    private OptionalLong readPid() {
        return PostmasterPidFile.readPid(dataDirectory);
    }

    private void writePidFile(final String content) throws IOException {
        Files.writeString(dataDirectory.resolve("postmaster.pid"), content);
    }
}
