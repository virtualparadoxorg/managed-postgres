package eu.virtualparadox.managedpostgres.lifecycle.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class BackupArtifactVerifierTest {

    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");

    @TempDir
    private Path temporaryDirectory;

    private final BackupArtifactVerifier verifier = new BackupArtifactVerifier();

    BackupArtifactVerifierTest() {}

    @Test
    void validBackupArtifactsReturnManifest() throws IOException {
        final Path backup = validBackup("app.dump", "app", 16, "cluster-id");
        final BackupManifest expected = manifest("app", 16, "cluster-id", BackupChecksum.sha256(backup));

        final BackupManifest actual = verifier.verify(backup, connectionInfo("app"), metadata(16, "cluster-id"));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void checksumMismatchFailsBeforeRestoreCommand() throws IOException {
        final Path backup = validBackup("app.dump", "app", 16, "cluster-id");
        Files.writeString(checksumPath(backup), "deadbeef  app.dump\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify(backup, connectionInfo("app"), metadata(16, "cluster-id")))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("checksum")
                .satisfies(throwable -> assertThat(((PostgresRestoreException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("checksum")
                        .doesNotContain("app-password"));
    }

    @Test
    void manifestChecksumMismatchFailsBeforeRestoreCommand() throws IOException {
        final Path backup = writeBackup("app.dump", "fake dump\n");
        writeManifest(
                backup,
                manifest("app", 16, "cluster-id", "0000000000000000000000000000000000000000000000000000000000000000"));
        writeChecksum(backup, BackupChecksum.sha256(backup));

        assertThatThrownBy(() -> verifier.verify(backup, connectionInfo("app"), metadata(16, "cluster-id")))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("manifest checksum");
    }

    @Test
    void checksumAlgorithmMismatchFailsBeforeRestoreCommand() throws IOException {
        final Path backup = writeBackup("app.dump", "fake dump\n");
        writeManifest(
                backup,
                new BackupManifest(
                        1,
                        NOW,
                        "1.0-SNAPSHOT",
                        "16.4",
                        16,
                        "cluster-id",
                        "app",
                        BackupFormat.PG_DUMP_CUSTOM,
                        "MD5",
                        BackupChecksum.sha256(backup)));
        writeChecksum(backup, BackupChecksum.sha256(backup));

        assertThatThrownBy(() -> verifier.verify(backup, connectionInfo("app"), metadata(16, "cluster-id")))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("checksum algorithm");
    }

    @Test
    void databaseMismatchFailsBeforeRestoreCommand() throws IOException {
        final Path backup = validBackup("app.dump", "other", 16, "cluster-id");

        assertThatThrownBy(() -> verifier.verify(backup, connectionInfo("app"), metadata(16, "cluster-id")))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("database");
    }

    @Test
    void clusterIdentityMismatchFailsBeforeRestoreCommand() throws IOException {
        final Path backup = validBackup("app.dump", "app", 16, "other-cluster-id");

        assertThatThrownBy(() -> verifier.verify(backup, connectionInfo("app"), metadata(16, "cluster-id")))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("cluster");
    }

    @Test
    void postgresqlMajorMismatchFailsBeforeRestoreCommand() throws IOException {
        final Path backup = validBackup("app.dump", "app", 15, "cluster-id");

        assertThatThrownBy(() -> verifier.verify(backup, connectionInfo("app"), metadata(16, "cluster-id")))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("PostgreSQL major");
    }

    @Test
    void missingManifestOrChecksumFailsWithTargetPathDiagnostics() throws IOException {
        final Path backup = writeBackup("app.dump", "fake dump\n");

        assertThatThrownBy(() -> verifier.verify(backup, connectionInfo("app"), metadata(16, "cluster-id")))
                .isInstanceOf(PostgresRestoreException.class)
                .satisfies(throwable -> assertThat(((PostgresRestoreException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("manifest")
                        .contains(manifestPath(backup).toString()));

        writeManifest(backup, manifest("app", 16, "cluster-id", BackupChecksum.sha256(backup)));

        assertThatThrownBy(() -> verifier.verify(backup, connectionInfo("app"), metadata(16, "cluster-id")))
                .isInstanceOf(PostgresRestoreException.class)
                .satisfies(throwable -> assertThat(((PostgresRestoreException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("checksum")
                        .contains(checksumPath(backup).toString()));
    }

    private Path validBackup(
            final String fileName, final String database, final int postgresqlMajor, final String clusterId)
            throws IOException {
        final Path backup = writeBackup(fileName, "fake dump\n");
        final BackupManifest manifest = manifest(database, postgresqlMajor, clusterId, BackupChecksum.sha256(backup));
        writeManifest(backup, manifest);
        writeChecksum(backup, BackupChecksum.sha256(backup));

        return backup;
    }

    private Path writeBackup(final String fileName, final String content) throws IOException {
        final Path backup = temporaryDirectory.resolve("backups").resolve(fileName);
        Files.createDirectories(Objects.requireNonNull(backup.getParent(), "backup parent"));
        Files.writeString(backup, content, StandardCharsets.UTF_8);

        return backup;
    }

    private static void writeManifest(final Path backup, final BackupManifest manifest) throws IOException {
        Files.writeString(manifestPath(backup), BackupManifestCodec.serialize(manifest), StandardCharsets.UTF_8);
    }

    private static void writeChecksum(final Path backup, final String checksum) throws IOException {
        Files.writeString(checksumPath(backup), checksum + "  " + backup.getFileName() + "\n", StandardCharsets.UTF_8);
    }

    private static Path manifestPath(final Path backup) {
        return Path.of(backup + ".manifest.json");
    }

    private static Path checksumPath(final Path backup) {
        return Path.of(backup + ".sha256");
    }

    private static BackupManifest manifest(
            final String database, final int postgresqlMajor, final String clusterId, final String checksum) {
        return new BackupManifest(
                1,
                NOW,
                "1.0-SNAPSHOT",
                postgresqlMajor + ".4",
                postgresqlMajor,
                clusterId,
                database,
                BackupFormat.PG_DUMP_CUSTOM,
                "SHA-256",
                checksum);
    }

    private static PostgresConnectionInfo connectionInfo(final String database) {
        return new PostgresConnectionInfo("127.0.0.1", 55432, database, "app", Secret.of("app-password"));
    }

    private Path metadataDataDirectory() {
        return temporaryDirectory.resolve("data");
    }

    private PostgresInstanceMetadata metadata(final int postgresqlMajor, final String clusterId) {
        return new PostgresInstanceMetadata(
                1,
                "instance-id",
                clusterId,
                "app-db",
                metadataDataDirectory(),
                "127.0.0.1",
                55432,
                "app",
                "app",
                postgresqlMajor + ".4",
                postgresqlMajor,
                "STARTED_BY_THIS_JVM",
                123L,
                "config-hash",
                NOW,
                NOW);
    }
}
