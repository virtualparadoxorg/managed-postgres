package eu.virtualparadox.managedpostgres.lifecycle.backup;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreDiagnostics;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Verifies logical backup artifacts before a restore operation may run.
 */
public final class BackupArtifactVerifier {

    private static final String EXPECTED_CHECKSUM_ALGORITHM = "SHA-256";

    private final PostgresRestoreDiagnostics diagnostics;

    /**
     * Creates a BackupArtifactVerifier instance.
     */
    public BackupArtifactVerifier() {
        diagnostics = new PostgresRestoreDiagnostics();
    }

    /**
     * Returns the verify result.
     *
     * @param backup backup value
     * @param connectionInfo connection info value
     * @param metadata metadata value
     * @return verify result
     */
    public BackupManifest verify(
            final Path backup, final PostgresConnectionInfo connectionInfo, final PostgresInstanceMetadata metadata) {
        final Path checkedBackup =
                Objects.requireNonNull(backup, "backup").toAbsolutePath().normalize();
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        final PostgresInstanceMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata");
        final Path manifestPath = manifestPath(checkedBackup);
        final Path checksumPath = checksumPath(checkedBackup);

        requireRegularFile(checkedBackup, "backup");
        requireRegularFile(manifestPath, "manifest");
        requireRegularFile(checksumPath, "checksum");

        final String checksum = checksum(checkedBackup);
        verifyChecksumSidecar(checkedBackup, checksumPath, checksum);
        final BackupManifest manifest = readManifest(manifestPath);
        verifyManifest(manifest, checksum, checkedConnectionInfo, checkedMetadata);

        return manifest;
    }

    private void verifyManifest(
            final BackupManifest manifest,
            final String checksum,
            final PostgresConnectionInfo connectionInfo,
            final PostgresInstanceMetadata metadata) {
        if (manifest.format() != BackupFormat.PG_DUMP_CUSTOM) {
            fail("backup format is not pg_dump custom");
        }
        if (!EXPECTED_CHECKSUM_ALGORITHM.equals(manifest.checksumAlgorithm())) {
            fail("backup checksum algorithm is not SHA-256");
        }
        if (!checksum.equals(manifest.checksum())) {
            fail("manifest checksum does not match backup checksum");
        }
        if (!connectionInfo.database().equals(manifest.database())) {
            fail("backup database does not match current connection database");
        }
        if (!metadata.clusterId().equals(manifest.clusterId())) {
            fail("backup cluster identity does not match current cluster");
        }
        if (metadata.postgresqlMajor() != manifest.postgresqlMajor()) {
            fail("backup PostgreSQL major version does not match current PostgreSQL major version");
        }
    }

    private BackupManifest readManifest(final Path manifestPath) {
        try {
            return BackupManifestCodec.deserialize(Files.readString(manifestPath, StandardCharsets.UTF_8));
        } catch (final IOException exception) {
            throw new PostgresRestoreException(
                    "Cannot read PostgreSQL backup manifest",
                    exception,
                    diagnostics.missingArtifact("manifest", manifestPath));
        }
    }

    private void verifyChecksumSidecar(final Path backup, final Path checksumPath, final String checksum) {
        final String expected = checksum + "  " + Objects.requireNonNull(backup.getFileName(), "backup fileName");
        final String actual = readChecksum(checksumPath).stripTrailing();
        if (!expected.equals(actual)) {
            fail("backup checksum sidecar does not match backup checksum");
        }
    }

    private String readChecksum(final Path checksumPath) {
        try {
            return Files.readString(checksumPath, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new PostgresRestoreException(
                    "Cannot read PostgreSQL backup checksum",
                    exception,
                    diagnostics.missingArtifact("checksum", checksumPath));
        }
    }

    private String checksum(final Path backup) {
        try {
            return BackupChecksum.sha256(backup);
        } catch (final UncheckedIOException exception) {
            throw new PostgresRestoreException(
                    "Cannot checksum PostgreSQL backup", exception, diagnostics.missingArtifact("backup", backup));
        }
    }

    private void requireRegularFile(final Path path, final String kind) {
        if (!Files.isRegularFile(path)) {
            throw new PostgresRestoreException(
                    "Missing PostgreSQL backup " + kind, diagnostics.missingArtifact(kind, path));
        }
    }

    private void fail(final String reason) {
        throw new PostgresRestoreException(
                "Incompatible PostgreSQL backup artifact: " + reason, diagnostics.incompatibleArtifact(reason));
    }

    private static Path manifestPath(final Path backup) {
        return Path.of(backup + ".manifest.json");
    }

    private static Path checksumPath(final Path backup) {
        return Path.of(backup + ".sha256");
    }
}
