package eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump;

import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperation;
import java.nio.file.Path;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupArtifactPaths;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupArtifactWriter;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupChecksum;

/**
 * Creates staged pg_dump backup artifacts and publishes them atomically.
 */
public final class PgDumpBackupCreator {

    private final PgDumpCommandExecutor commandExecutor;
    private final BackupArtifactWriter artifactWriter;

    /**
     * Creates a PgDumpBackupCreator instance.
     *
     * @param commandExecutor command executor value
     * @param artifactWriter artifact writer value
     */
    public PgDumpBackupCreator(
            final PgDumpCommandExecutor commandExecutor,
            final BackupArtifactWriter artifactWriter) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.artifactWriter = Objects.requireNonNull(artifactWriter, "artifactWriter");
    }

    /**
     * Performs the create operation.
     *
     * @param operation operation value
     * @param paths paths value
     */
    public void create(final FileSystemOperation operation, final BackupArtifactPaths paths) {
        final FileSystemOperation checkedOperation = Objects.requireNonNull(operation, "operation");
        final BackupArtifactPaths checkedPaths = Objects.requireNonNull(paths, "paths");
        final Path staging = checkedOperation.createStagingDirectory(checkedPaths.backupFileName());
        final Path stagedBackup = staging.resolve(checkedPaths.backupFileName());
        commandExecutor.dumpTo(stagedBackup);
        final String checksum = BackupChecksum.sha256(stagedBackup);
        final Path stagedManifest = staging.resolve(checkedPaths.backupFileName() + ".manifest.json");
        final Path stagedChecksum = staging.resolve(checkedPaths.backupFileName() + ".sha256");
        artifactWriter.writeManifest(stagedManifest, checksum);
        artifactWriter.writeChecksum(stagedChecksum, checkedPaths.backupFileName(), checksum);
        checkedOperation.publishFile(stagedBackup, checkedPaths.backupTarget());
        checkedOperation.publishFile(stagedManifest, checkedPaths.manifestTarget());
        checkedOperation.publishFile(stagedChecksum, checkedPaths.checksumTarget());
    }
}
