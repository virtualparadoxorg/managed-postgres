package eu.virtualparadox.managedpostgres.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class AtomicFileWriterTest {

    @TempDir
    private Path directory;

    AtomicFileWriterTest() {}

    @Test
    void atomicWriterDoesNotExposeFinalFileBeforeCommit() throws IOException {
        final Path target = directory.resolve("postgresql.conf");
        final AtomicFileWriter writer = new AtomicFileWriter();

        final AtomicFileWriter.PendingWrite write = writer.stageUtf8(target, "port=5432");

        assertThat(target).doesNotExist();
        assertThat(write.temporaryPath()).exists();

        write.commit();

        assertThat(target).exists();
        assertThat(Files.readString(target)).isEqualTo("port=5432");
        assertThat(write.temporaryPath()).doesNotExist();
    }

    @Test
    void atomicWriterAppliesRequestedPosixFilePermissions() throws IOException {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView(PosixFileAttributeView.class));
        final Path target = directory.resolve("credentials.properties");
        final AtomicFileWriter writer = new AtomicFileWriter();
        final Set<PosixFilePermission> ownerOnly =
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

        writer.writeUtf8(target, "password=secret", ManagedFilePermissions.ownerOnlyReadWrite());

        assertThat(Files.getPosixFilePermissions(target)).containsExactlyInAnyOrderElementsOf(ownerOnly);
    }

    @Test
    void pendingWriteCanBeDiscardedWithoutPublishingTarget() throws IOException {
        final Path target = directory.resolve("postgresql.conf");
        final AtomicFileWriter.PendingWrite write = new AtomicFileWriter().stageUtf8(target, "port=5432");

        write.discard();

        assertThat(target).doesNotExist();
        assertThat(write.temporaryPath()).doesNotExist();
    }

    @Test
    void atomicWriterRejectsTargetsWithoutParentDirectory() {
        assertThatThrownBy(() -> new AtomicFileWriter().stageUtf8(rootPath(), "port=5432"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parent directory");
    }

    private static Path rootPath() {
        final Path currentDirectory = Path.of("").toAbsolutePath();

        return Objects.requireNonNull(currentDirectory.getRoot(), "root");
    }
}
