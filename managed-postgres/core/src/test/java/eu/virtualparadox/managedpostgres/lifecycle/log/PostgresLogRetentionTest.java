package eu.virtualparadox.managedpostgres.lifecycle.log;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresLogRetentionTest {

    @TempDir
    private Path temporaryDirectory;

    PostgresLogRetentionTest() {}

    @Test
    void smallActiveLogIsKeptInPlace() throws IOException {
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        Files.writeString(logFile, "small", StandardCharsets.UTF_8);

        new PostgresLogRetention().prepare(logFile, policy(10L, 2));

        assertThat(Files.readString(logFile, StandardCharsets.UTF_8)).isEqualTo("small");
        assertThat(temporaryDirectory.resolve("postgres.log.1")).doesNotExist();
    }

    @Test
    void oversizedActiveLogIsRotatedToFirstSlot() throws IOException {
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        Files.writeString(logFile, "0123456789", StandardCharsets.UTF_8);

        new PostgresLogRetention().prepare(logFile, policy(10L, 2));

        assertThat(logFile).doesNotExist();
        assertThat(Files.readString(temporaryDirectory.resolve("postgres.log.1"), StandardCharsets.UTF_8))
                .isEqualTo("0123456789");
    }

    @Test
    void existingRotationsShiftAndOldestBeyondPolicyIsDeleted() throws IOException {
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        Files.writeString(logFile, "active", StandardCharsets.UTF_8);
        Files.writeString(temporaryDirectory.resolve("postgres.log.1"), "one", StandardCharsets.UTF_8);
        Files.writeString(temporaryDirectory.resolve("postgres.log.2"), "two", StandardCharsets.UTF_8);

        new PostgresLogRetention().prepare(logFile, policy(1L, 2));

        assertThat(Files.readString(temporaryDirectory.resolve("postgres.log.1"), StandardCharsets.UTF_8))
                .isEqualTo("active");
        assertThat(Files.readString(temporaryDirectory.resolve("postgres.log.2"), StandardCharsets.UTF_8))
                .isEqualTo("one");
        assertThat(temporaryDirectory.resolve("postgres.log.3")).doesNotExist();
        assertThat(temporaryDirectory.resolve("postgres.log")).doesNotExist();
    }

    @Test
    void zeroRetainedLogsDeletesOversizedActiveLogWithoutRotations() throws IOException {
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        Files.writeString(logFile, "active", StandardCharsets.UTF_8);
        Files.writeString(temporaryDirectory.resolve("postgres.log.1"), "one", StandardCharsets.UTF_8);

        new PostgresLogRetention().prepare(logFile, policy(1L, 0));

        assertThat(logFile).doesNotExist();
        assertThat(temporaryDirectory.resolve("postgres.log.1")).doesNotExist();
    }

    @Test
    void unrelatedFilesArePreserved() throws IOException {
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        final Path unrelated = temporaryDirectory.resolve("postgresql.conf");
        Files.writeString(logFile, "active", StandardCharsets.UTF_8);
        Files.writeString(unrelated, "keep", StandardCharsets.UTF_8);

        new PostgresLogRetention().prepare(logFile, policy(1L, 1));

        assertThat(Files.readString(unrelated, StandardCharsets.UTF_8)).isEqualTo("keep");
    }

    private static CleanupPolicy policy(final long rotateAboveBytes, final int retainedLogFiles) {
        return CleanupPolicy.safeDefaults()
                .rotateLogsAboveBytes(rotateAboveBytes)
                .keepLogFiles(retainedLogFiles);
    }
}
