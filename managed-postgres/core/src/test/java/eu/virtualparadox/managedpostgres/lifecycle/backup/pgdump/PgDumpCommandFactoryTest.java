package eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;

public final class PgDumpCommandFactoryTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @TempDir
    private Path temporaryDirectory;

    PgDumpCommandFactoryTest() {
    }

    @Test
    void pgDumpCommandUsesRuntimeBinaryCustomFormatConnectionDetailsAndPasswordEnvironment() {
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        final Path target = temporaryDirectory.resolve("backup.dump");

        final CommandRequest request = new PgDumpCommandFactory(runtimeDirectory, TIMEOUT)
                .customDump(connectionInfo(), target);

        assertThat(request.command()).containsExactly(
                runtimeDirectory.resolve("bin").resolve("pg_dump").toString(),
                "-h",
                "127.0.0.1",
                "-p",
                "55432",
                "-U",
                "app",
                "-d",
                "app",
                "-Fc",
                "-f",
                target.toString());
        assertThat(request.environment()).containsEntry("PGPASSWORD", "app-password");
        assertThat(request.renderedCommand()).doesNotContain("app-password");
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo(
                "127.0.0.1",
                55432,
                "app",
                "app",
                Secret.of("app-password"));
    }
}
