package eu.virtualparadox.managedpostgres.lifecycle.testsupport.restore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupChecksum;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupFormat;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifest;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifestCodec;

public final class PgRestoreBackupFixture {

    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");

    private final Path temporaryDirectory;

    public PgRestoreBackupFixture(final Path temporaryDirectory) {
        this.temporaryDirectory = Objects.requireNonNull(temporaryDirectory, "temporaryDirectory");
    }

    public Path writeValidBackup(final String content) throws IOException {
        final Path backup = temporaryDirectory.resolve("backups").resolve("app.dump");
        Files.createDirectories(Objects.requireNonNull(backup.getParent(), "backup parent"));
        Files.writeString(backup, content, StandardCharsets.UTF_8);
        final String checksum = BackupChecksum.sha256(backup);
        final BackupManifest manifest = new BackupManifest(
                1,
                NOW,
                "1.0-SNAPSHOT",
                "16.4",
                16,
                "cluster-id",
                "app",
                BackupFormat.PG_DUMP_CUSTOM,
                "SHA-256",
                checksum);
        Files.writeString(manifestPath(backup), BackupManifestCodec.serialize(manifest), StandardCharsets.UTF_8);
        Files.writeString(
                checksumPath(backup),
                checksum + "  " + Objects.requireNonNull(backup.getFileName(), "backup fileName") + "\n",
                StandardCharsets.UTF_8);

        return backup;
    }

    public static Path manifestPath(final Path target) {
        return Path.of(target + ".manifest.json");
    }

    public static Path checksumPath(final Path target) {
        return Path.of(target + ".sha256");
    }
}
