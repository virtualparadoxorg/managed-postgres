package eu.virtualparadox.managedpostgres.lifecycle.backup;

import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Target paths created by one logical backup operation.
 *
 * @param backupTarget final backup file path
 * @param manifestTarget final manifest sidecar path
 * @param checksumTarget final checksum sidecar path
 * @param operationRoot filesystem operation root
 * @param backupFileName final backup file name
 */
public record BackupArtifactPaths(
        Path backupTarget,
        Path manifestTarget,
        Path checksumTarget,
        Path operationRoot,
        String backupFileName) {

    private static final String CHECKSUM_SUFFIX = ".sha256";
    private static final String MANIFEST_SUFFIX = ".manifest.json";

    /**
     * Defines the value value.
     */
    public BackupArtifactPaths {
        Objects.requireNonNull(backupTarget, "backupTarget");
        Objects.requireNonNull(manifestTarget, "manifestTarget");
        Objects.requireNonNull(checksumTarget, "checksumTarget");
        Objects.requireNonNull(operationRoot, "operationRoot");
        Objects.requireNonNull(backupFileName, "backupFileName");
    }

    /**
     * Returns the from result.
     *
     * @param target target value
     * @param diagnostics diagnostics value
     * @return from result
     */
    public static BackupArtifactPaths from(final Path target, final PostgresBackupDiagnostics diagnostics) {
        final Path checkedTarget = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        final Path fileName = checkedTarget.getFileName();
        if (fileName == null) {
            throw new PostgresBackupException(
                    "Invalid PostgreSQL backup target",
                    diagnostics.invalidTarget(checkedTarget));
        }

        return new BackupArtifactPaths(
                checkedTarget,
                Path.of(checkedTarget + MANIFEST_SUFFIX),
                Path.of(checkedTarget + CHECKSUM_SUFFIX),
                Objects.requireNonNull(checkedTarget.getParent(), "backupTarget parent"),
                fileName.toString());
    }

    /**
     * Performs the require absent operation.
     *
     * @param diagnostics diagnostics value
     */
    public void requireAbsent(final PostgresBackupDiagnostics diagnostics) {
        List.of(backupTarget, manifestTarget, checksumTarget)
                .forEach(path -> requireAbsent(path, diagnostics));
    }

    private static void requireAbsent(final Path path, final PostgresBackupDiagnostics diagnostics) {
        if (Files.exists(path)) {
            throw new PostgresBackupException(
                    "PostgreSQL backup artifact already exists",
                    diagnostics.existingArtifact(path));
        }
    }
}
