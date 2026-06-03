package eu.virtualparadox.managedpostgres.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class FileSystemOperationTest {

    @TempDir
    private Path operationRoot;

    FileSystemOperationTest() {}

    @Test
    void operationCreatesStagingDirectoryAsSiblingOfTarget() {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();

        try (FileSystemOperation operation = fileSystem.beginOperation("install-runtime", operationRoot)) {
            final Path staging = operation.createStagingDirectory("runtime");
            final Path target = operationRoot.resolve("runtime");

            assertThat(staging).exists().isDirectory();
            assertThat(staging.getParent()).isEqualTo(target.getParent());
            assertThat(staging.getFileName().toString()).contains("runtime").endsWith(".staging");
            assertThat(staging).isNotEqualTo(target);
        }
    }

    @Test
    void operationWritesOwnershipMarkerIntoStaging() throws IOException {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final ManagedPathOwnership ownership = new ManagedPathOwnership();

        try (FileSystemOperation operation = fileSystem.beginOperation("install-runtime", operationRoot)) {
            final Path staging = operation.createStagingDirectory("runtime");

            assertThat(ownership.isOwned(staging)).isTrue();
            assertThat(Files.readString(ownership.markerPath(staging))).contains("install-runtime");
        }
    }

    @Test
    void operationWritesUtf8Atomically() throws IOException {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final Path target = operationRoot.resolve("postgresql.conf");

        try (FileSystemOperation operation = fileSystem.beginOperation("configure", operationRoot)) {
            operation.writeUtf8Atomically(target, "shared_buffers=16MB");
            operation.commit();
        }

        assertThat(Files.readString(target)).isEqualTo("shared_buffers=16MB");
    }

    @Test
    void uncommittedStagingWithOwnershipMarkerIsSafeToDiscard() {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final Path staging;

        try (FileSystemOperation operation = fileSystem.beginOperation("install-runtime", operationRoot)) {
            staging = operation.createStagingDirectory("runtime");
            assertThat(staging).exists();
        }

        assertThat(staging).doesNotExist();
    }

    @Test
    void uncommittedStagingWithoutOwnershipMarkerIsPreserved() throws IOException {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        final Path staging;

        try (FileSystemOperation operation = fileSystem.beginOperation("install-runtime", operationRoot)) {
            staging = operation.createStagingDirectory("runtime");
            Files.delete(ownership.markerPath(staging));
        }

        assertThat(staging).isDirectory();
    }

    @Test
    void recoveryDiscardsAtomicTemporaryFiles() throws IOException {
        final FileSystemOperationJournal journal = new FileSystemOperationJournal();
        final Path temporaryFile = operationRoot.resolve(".initdb-password.txt.123.tmp");
        Files.createDirectories(operationRoot);
        Files.writeString(temporaryFile, "secret");

        journal.recover(operationRoot);

        assertThat(temporaryFile).doesNotExist();
    }

    @Test
    void recoveryIgnoresMissingRootsAndNonStagingPaths() throws IOException {
        final FileSystemOperationJournal journal = new FileSystemOperationJournal();
        final Path regularFile = operationRoot.resolve("postgresql.conf");
        final Path nonTemporaryHiddenFile = operationRoot.resolve(".postgresql.conf.tmp.backup");
        final Path visibleTemporaryFile = operationRoot.resolve("postgresql.conf.tmp");
        Files.createDirectories(operationRoot);
        Files.writeString(regularFile, "port=5432");
        Files.writeString(nonTemporaryHiddenFile, "keep");
        Files.writeString(visibleTemporaryFile, "keep");

        final FileSystemOperationJournal.RecoveryReport missingReport =
                journal.recover(operationRoot.resolve("missing"));
        final FileSystemOperationJournal.RecoveryReport report = journal.recover(operationRoot);

        assertThat(missingReport.discardedStagingDirectories()).isEmpty();
        assertThat(missingReport.unknownStagingDirectories()).isEmpty();
        assertThat(report.discardedStagingDirectories()).isEmpty();
        assertThat(report.unknownStagingDirectories()).isEmpty();
        assertThat(regularFile).exists();
        assertThat(nonTemporaryHiddenFile).exists();
        assertThat(visibleTemporaryFile).exists();
    }

    @Test
    void operationPublishDirectoryKeepsPublishedTargetAfterClose() throws IOException {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final Path target = operationRoot.resolve("runtime");

        try (FileSystemOperation operation = fileSystem.beginOperation("install-runtime", operationRoot)) {
            final Path staging = operation.createStagingDirectory("runtime");
            Files.writeString(staging.resolve("PG_VERSION"), "16");
            operation.publishDirectory(staging, target);
        }

        assertThat(target).isDirectory();
        assertThat(Files.readString(target.resolve("PG_VERSION"))).isEqualTo("16");
    }

    @Test
    void operationPublishFileKeepsPublishedTargetAfterClose() throws IOException {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final Path target = operationRoot.resolve("backup.dump");

        try (FileSystemOperation operation = fileSystem.beginOperation("backup", operationRoot)) {
            final Path staging = operation.createStagingDirectory("backup.dump");
            final Path stagedFile = staging.resolve("backup.dump");
            Files.writeString(stagedFile, "backup-content", StandardCharsets.UTF_8);

            operation.publishFile(stagedFile, target);
            operation.commit();
        }

        assertThat(target).hasContent("backup-content");
    }

    @Test
    void operationPublishFileFailsWithoutReplacingExistingTarget() throws IOException {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final Path target = operationRoot.resolve("backup.dump");
        Files.writeString(target, "existing", StandardCharsets.UTF_8);

        try (FileSystemOperation operation = fileSystem.beginOperation("backup", operationRoot)) {
            final Path staging = operation.createStagingDirectory("backup.dump");
            final Path stagedFile = staging.resolve("backup.dump");
            Files.writeString(stagedFile, "new-content", StandardCharsets.UTF_8);

            assertThatThrownBy(() -> operation.publishFile(stagedFile, target))
                    .isInstanceOf(UncheckedIOException.class);
        }

        assertThat(target).hasContent("existing");
    }

    @Test
    void operationPublishFileDiscardsEmptyStagingDirectoryAfterPublish() throws IOException {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final Path staging;

        try (FileSystemOperation operation = fileSystem.beginOperation("backup", operationRoot)) {
            staging = operation.createStagingDirectory("backup.dump");
            final Path stagedFile = staging.resolve("backup.dump");
            Files.writeString(stagedFile, "backup-content", StandardCharsets.UTF_8);

            operation.publishFile(stagedFile, operationRoot.resolve("backup.dump"));
        }

        assertThat(operationRoot.resolve("backup.dump")).hasContent("backup-content");
        assertThat(staging).doesNotExist();
    }

    @Test
    void operationPublishFileDoesNotDeleteUntrackedStagingParent() throws IOException {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final Path externalStaging = operationRoot.resolve("external-staging");
        final Path stagedFile = externalStaging.resolve("backup.dump");
        Files.createDirectories(externalStaging);
        Files.writeString(stagedFile, "external-backup", StandardCharsets.UTF_8);

        try (FileSystemOperation operation = fileSystem.beginOperation("backup", operationRoot)) {
            operation.publishFile(stagedFile, operationRoot.resolve("backup.dump"));
        }

        assertThat(operationRoot.resolve("backup.dump")).hasContent("external-backup");
        assertThat(externalStaging).isDirectory();
    }

    @Test
    void operationPublishFileKeepsStagingUntilEveryStagedFileIsPublished() throws IOException {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();
        final Path staging;

        try (FileSystemOperation operation = fileSystem.beginOperation("backup", operationRoot)) {
            staging = operation.createStagingDirectory("backup.dump");
            final Path stagedDump = staging.resolve("backup.dump");
            final Path stagedChecksum = staging.resolve("backup.dump.sha256");
            Files.writeString(stagedDump, "backup-content", StandardCharsets.UTF_8);
            Files.writeString(stagedChecksum, "checksum", StandardCharsets.UTF_8);

            operation.publishFile(stagedDump, operationRoot.resolve("backup.dump"));
            assertThat(staging).isDirectory();

            operation.publishFile(stagedChecksum, operationRoot.resolve("backup.dump.sha256"));
        }

        assertThat(operationRoot.resolve("backup.dump")).hasContent("backup-content");
        assertThat(operationRoot.resolve("backup.dump.sha256")).hasContent("checksum");
        assertThat(staging).doesNotExist();
    }

    @Test
    void recoveryDiscardsOwnedStagingDirectories() {
        final FileSystemOperationJournal journal = new FileSystemOperationJournal();
        final Path staging;
        try (FileSystemOperation operation = journal.beginOperation("install-runtime", operationRoot)) {
            staging = operation.createStagingDirectory("runtime");
            operation.commit();
        }

        final FileSystemOperationJournal.RecoveryReport report = journal.recover(operationRoot);

        assertThat(report.discardedStagingDirectories()).containsExactly(staging);
        assertThat(report.unknownStagingDirectories()).isEmpty();
        assertThat(staging).doesNotExist();
    }

    @Test
    void operationRejectsBlankOperationNameAndUnsafeStagingNames() {
        final ManagedFileSystem fileSystem = new FileSystemOperationJournal();

        assertThatThrownBy(() -> fileSystem.beginOperation(" ", operationRoot))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fileSystem.createTemporaryDirectory(operationRoot, " "))
                .isInstanceOf(IllegalArgumentException.class);
        try (FileSystemOperation operation = fileSystem.beginOperation("install-runtime", operationRoot)) {
            assertThatThrownBy(() -> operation.createStagingDirectory(" "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> operation.createStagingDirectory("runtime/bin"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> operation.createStagingDirectory(
                            operationRoot.resolve("runtime").toString()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
