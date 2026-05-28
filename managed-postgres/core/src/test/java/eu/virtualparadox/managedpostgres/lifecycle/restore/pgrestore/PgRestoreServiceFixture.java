package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifestSource;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;

public final class PgRestoreServiceFixture {

    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final PostgresConnectionInfo CONNECTION_INFO = new PostgresConnectionInfo(
            "127.0.0.1",
            55432,
            "app",
            "app",
            Secret.of("app-password"));

    private final Path temporaryDirectory;

    public PgRestoreServiceFixture(final Path temporaryDirectory) {
        this.temporaryDirectory = Objects.requireNonNull(temporaryDirectory, "temporaryDirectory");
    }

    public PgRestoreService service(final Path runtimeDirectory) {
        return service(runtimeDirectory, layout(), new PostgresLockService());
    }

    public PgRestoreService service(
            final Path runtimeDirectory,
            final PostgresLayout layout,
            final PostgresLockService lockService) {
        return PgRestoreServiceFactory.create(new PgRestoreDependencies(
                layout,
                runtimeDirectory,
                new CommandRunner(),
                new FileSystemOperationJournal(),
                lockService,
                TIMEOUT,
                new BackupManifestSource(
                        CONNECTION_INFO,
                        metadata(),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        "1.0-SNAPSHOT")));
    }

    public PostgresLayout layout() {
        return PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("storage"));
    }

    public RestoreOptions destructiveOptions() {
        return RestoreOptions.builder()
                .dropCurrentDatabase(true)
                .createSafetyBackup(true)
                .build();
    }

    private PostgresInstanceMetadata metadata() {
        return new PostgresInstanceMetadata(
                1,
                "instance-id",
                "cluster-id",
                "app-db",
                temporaryDirectory.resolve("data"),
                CONNECTION_INFO.host(),
                CONNECTION_INFO.port(),
                CONNECTION_INFO.database(),
                CONNECTION_INFO.username(),
                "16.4",
                16,
                "STARTED_BY_THIS_JVM",
                123L,
                "config-hash",
                NOW,
                NOW);
    }
}
