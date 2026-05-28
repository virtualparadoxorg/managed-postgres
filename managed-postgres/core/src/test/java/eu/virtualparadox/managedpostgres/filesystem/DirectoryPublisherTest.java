package eu.virtualparadox.managedpostgres.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class DirectoryPublisherTest {

    @TempDir
    private Path operationRoot;

    DirectoryPublisherTest() {
    }

    @Test
    void publishToAbsentTargetMovesStagingDirectoryIntoPlace() throws IOException {
        final Path staging = Files.createDirectory(operationRoot.resolve(".runtime.staging"));
        final Path target = operationRoot.resolve("runtime");
        Files.writeString(staging.resolve("PG_VERSION"), "16");
        final DirectoryPublisher publisher = new DirectoryPublisher();

        publisher.publish(staging, target);

        assertThat(staging).doesNotExist();
        assertThat(target).isDirectory();
        assertThat(Files.readString(target.resolve("PG_VERSION"))).isEqualTo("16");
    }

    @Test
    void publishToExistingTargetFailsBeforeMutatingEitherDirectory() throws IOException {
        final Path staging = Files.createDirectory(operationRoot.resolve(".runtime.staging"));
        final Path target = Files.createDirectory(operationRoot.resolve("runtime"));
        Files.writeString(staging.resolve("PG_VERSION"), "17");
        Files.writeString(target.resolve("PG_VERSION"), "16");
        final DirectoryPublisher publisher = new DirectoryPublisher();

        assertThatThrownBy(() -> publisher.publish(staging, target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("target already exists");

        assertThat(staging).isDirectory();
        assertThat(target).isDirectory();
        assertThat(Files.readString(staging.resolve("PG_VERSION"))).isEqualTo("17");
        assertThat(Files.readString(target.resolve("PG_VERSION"))).isEqualTo("16");
    }

    @Test
    void publishRejectsSamePathAndMissingStagingDirectory() {
        final DirectoryPublisher publisher = new DirectoryPublisher();
        final Path staging = operationRoot.resolve(".runtime.staging");

        assertThatThrownBy(() -> publisher.publish(staging, staging))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ");
        assertThatThrownBy(() -> publisher.publish(staging, operationRoot.resolve("runtime")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existing directory");
    }

    @Test
    void moveIfAbsentFailsWithoutReplacingExistingTarget() throws IOException {
        final Path source = operationRoot.resolve(".postgresql.conf.tmp");
        final Path target = operationRoot.resolve("postgresql.conf");
        Files.writeString(source, "port=15432");
        Files.writeString(target, "port=5432");

        assertThatThrownBy(() -> DirectoryPublisher.moveIfAbsent(source, target))
                .isInstanceOf(UncheckedIOException.class);

        assertThat(Files.readString(source)).isEqualTo("port=15432");
        assertThat(Files.readString(target)).isEqualTo("port=5432");
    }

    @Test
    void moveReplacingExistingPublishesNewFileContent() throws IOException {
        final Path source = operationRoot.resolve(".postgresql.conf.tmp");
        final Path target = operationRoot.resolve("postgresql.conf");
        Files.writeString(source, "port=15432");
        Files.writeString(target, "port=5432");

        DirectoryPublisher.moveReplacingExisting(source, target);

        assertThat(source).doesNotExist();
        assertThat(Files.readString(target)).isEqualTo("port=15432");
    }

    @Test
    void deleteRecursivelyHandlesMissingAndNestedDirectories() throws IOException {
        final Path directory = operationRoot.resolve("staging");
        final Path nestedDirectory = directory.resolve("nested");
        final Path nestedFile = nestedDirectory.resolve("file.txt");
        Files.createDirectories(nestedDirectory);
        Files.writeString(nestedFile, "content");

        DirectoryPublisher.deleteRecursivelyIfExists(operationRoot.resolve("missing"));
        DirectoryPublisher.deleteRecursivelyIfExists(directory);

        assertThat(directory).doesNotExist();
    }

    @Test
    void unknownStagingWithoutOwnershipMarkerIsReportedAndNotDeleted() throws IOException {
        final Path unknownStaging = Files.createDirectory(operationRoot.resolve(".runtime-orphan.staging"));
        final FileSystemOperationJournal journal = new FileSystemOperationJournal();

        final FileSystemOperationJournal.RecoveryReport report = journal.recover(operationRoot);

        assertThat(report.discardedStagingDirectories()).isEmpty();
        assertThat(report.unknownStagingDirectories()).containsExactly(unknownStaging);
        assertThat(unknownStaging).exists();
    }
}
