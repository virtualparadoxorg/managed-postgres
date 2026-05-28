package eu.virtualparadox.managedpostgres.lifecycle.backup;

import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes staged logical backup sidecar artifacts.
 */
public final class BackupArtifactWriter {

    private final BackupManifestFactory manifestFactory;
    private final PostgresBackupDiagnostics diagnostics;

    /**
     * Creates a BackupArtifactWriter instance.
     *
     * @param manifestFactory manifest factory value
     * @param diagnostics diagnostics value
     */
    public BackupArtifactWriter(
            final BackupManifestFactory manifestFactory,
            final PostgresBackupDiagnostics diagnostics) {
        this.manifestFactory = Objects.requireNonNull(manifestFactory, "manifestFactory");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /**
     * Performs the write manifest operation.
     *
     * @param stagedManifest staged manifest value
     * @param checksum checksum value
     */
    public void writeManifest(final Path stagedManifest, final String checksum) {
        final BackupManifest manifest = manifestFactory.create(checksum);
        writeString(stagedManifest, BackupManifestCodec.serialize(manifest), "write-backup-manifest");
    }

    /**
     * Performs the write checksum operation.
     *
     * @param stagedChecksum staged checksum value
     * @param backupFileName backup file name value
     * @param checksum checksum value
     */
    public void writeChecksum(
            final Path stagedChecksum,
            final String backupFileName,
            final String checksum) {
        writeString(stagedChecksum, checksum + "  " + backupFileName + "\n", "write-backup-checksum");
    }

    private void writeString(final Path target, final String content, final String operation) {
        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new PostgresBackupException(
                    "Failed to write PostgreSQL backup artifact",
                    exception,
                    diagnostics.artifactWriteFailure(operation, target));
        }
    }
}
