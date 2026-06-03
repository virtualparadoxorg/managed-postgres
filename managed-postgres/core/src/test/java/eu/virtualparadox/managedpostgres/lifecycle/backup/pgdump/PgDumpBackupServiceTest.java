package eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupChecksum;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifestSource;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PgDumpBackupServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    PgDumpBackupServiceTest() {}

    @Test
    void successfulBackupPublishesDumpManifestAndChecksum() throws IOException {
        final TestPgDump pgDump = createPgDump(recordingPgDump(0));
        final Path target = temporaryDirectory.resolve("backups").resolve("app.dump");

        service(pgDump.runtimeDirectory()).backupTo(target);

        final String checksum = BackupChecksum.sha256(target);
        assertThat(target).hasContent("fake dump\n");
        assertThat(manifestPath(target))
                .hasContent(
                        """
                        {
                          "manifestVersion": 1,
                          "createdAt": "2026-05-27T00:00:00Z",
                          "frameworkVersion": "1.0-SNAPSHOT",
                          "postgresqlVersion": "16.4",
                          "postgresqlMajor": 16,
                          "clusterId": "cluster-id",
                          "database": "app",
                          "format": "pg_dump_custom",
                          "checksumAlgorithm": "SHA-256",
                          "checksum": "__CHECKSUM__"
                        }
                        """
                                .replace("__CHECKSUM__", checksum));
        assertThat(checksumPath(target)).hasContent(checksum + "  app.dump\n");
        assertThat(Files.readString(pgDump.commandLog()))
                .contains("PGPASSWORD=set")
                .contains("-Fc")
                .contains("-d app")
                .doesNotContain("app-password");
    }

    @Test
    void existingBackupArtifactsFailBeforeRunningPgDump() throws IOException {
        final TestPgDump pgDump = createPgDump(recordingPgDump(0));
        final Path target = temporaryDirectory.resolve("existing.dump");
        Files.writeString(manifestPath(target), "existing", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service(pgDump.runtimeDirectory()).backupTo(target))
                .isInstanceOf(PostgresBackupException.class)
                .hasMessageContaining("already exists");

        assertThat(pgDump.commandLog()).doesNotExist();
    }

    @Test
    void failedPgDumpDeletesStagingAndRedactsDiagnostics() throws IOException {
        final TestPgDump pgDump = createPgDump(recordingPgDump(7));
        final Path target = temporaryDirectory.resolve("failed").resolve("app.dump");

        assertThatThrownBy(() -> service(pgDump.runtimeDirectory()).backupTo(target))
                .isInstanceOf(PostgresBackupException.class)
                .satisfies(throwable -> assertThat(((PostgresBackupException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("<redacted>")
                        .doesNotContain("app-password"));

        assertThat(target).doesNotExist();
        assertThat(stagingDirectories(Objects.requireNonNull(target.getParent(), "target parent")))
                .isEmpty();
    }

    private PgDumpBackupService service(final Path runtimeDirectory) {
        final FileSystemOperationJournal fileSystem = new FileSystemOperationJournal();
        final PostgresLayout layout = PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("storage"));

        return PgDumpBackupServiceFactory.create(new PgDumpBackupDependencies(
                layout,
                runtimeDirectory,
                new CommandRunner(),
                fileSystem,
                new PostgresLockService(),
                TIMEOUT,
                new BackupManifestSource(
                        connectionInfo(), metadata(), Clock.fixed(NOW, ZoneOffset.UTC), "1.0-SNAPSHOT")));
    }

    private TestPgDump createPgDump(final String body) throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        final Path commandLog = temporaryDirectory.resolve("pg-dump-command.log");
        Files.createDirectories(binDirectory);
        final Path pgDump = binDirectory.resolve("pg_dump");
        Files.writeString(pgDump, "#!/bin/sh\n" + body, StandardCharsets.UTF_8);
        assertThat(pgDump.toFile().setExecutable(true)).isTrue();

        return new TestPgDump(runtimeDirectory, commandLog);
    }

    private String recordingPgDump(final int exitCode) {
        return """
                printf 'PGPASSWORD=%s\\n' "${PGPASSWORD:+set}" >> "__COMMAND_LOG__"
                printf 'ARGS=%s\\n' "$*" >> "__COMMAND_LOG__"
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = '-f' ]; then
                    shift
                    printf 'fake dump\\n' > "$1"
                  fi
                  shift
                done
                printf 'PGPASSWORD=app-password password=app-password\\n' >&2
                exit __EXIT_CODE__
                """
                .replace(
                        "__COMMAND_LOG__",
                        temporaryDirectory.resolve("pg-dump-command.log").toString())
                .replace("__EXIT_CODE__", Integer.toString(exitCode));
    }

    private static Path manifestPath(final Path target) {
        return Path.of(target + ".manifest.json");
    }

    private static Path checksumPath(final Path target) {
        return Path.of(target + ".sha256");
    }

    private static List<String> stagingDirectories(final Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.map(path -> Objects.requireNonNull(path.getFileName(), "path fileName")
                            .toString())
                    .filter(name -> name.contains(".staging"))
                    .toList();
        }
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo("127.0.0.1", 55432, "app", "app", Secret.of("app-password"));
    }

    private PostgresInstanceMetadata metadata() {
        return new PostgresInstanceMetadata(
                1,
                "instance-id",
                "cluster-id",
                "app-db",
                temporaryDirectory.resolve("data"),
                "127.0.0.1",
                55432,
                "app",
                "app",
                "16.4",
                16,
                "STARTED_BY_THIS_JVM",
                123L,
                "config-hash",
                NOW,
                NOW);
    }

    private record TestPgDump(Path runtimeDirectory, Path commandLog) {}
}
