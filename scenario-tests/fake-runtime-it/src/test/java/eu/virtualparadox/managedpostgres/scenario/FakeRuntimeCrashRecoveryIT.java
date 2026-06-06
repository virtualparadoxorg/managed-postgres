package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FakeRuntimeCrashRecoveryIT {

    private static final String TEST_PASSWORD = "test-password";

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeCrashRecoveryIT() {}

    @Test
    void startReconcilesClusterLeftAfterInterruptedStartBeforeMetadata() throws IOException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(temporaryDirectory.resolve("runtime"));
        final Path storageRoot = temporaryDirectory.resolve("cluster");
        final Path dataDirectory = storageRoot.resolve("data");
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("PG_VERSION"), "16\n", StandardCharsets.UTF_8);

        try (RunningPostgres postgres = localPostgres(storageRoot, runtime).start()) {
            assertThat(postgres.connectionInfo().host()).isEqualTo("127.0.0.1");
            assertThat(ScenarioMetadata.metadataPath(storageRoot)).isRegularFile();
            assertThat(ScenarioMetadata.require(storageRoot).dataDirectory()).isEqualTo(dataDirectory);
        }
    }

    @Test
    void runtimeValidationFailureLeavesMetadataUnwritten() throws IOException {
        final Path storageRoot = temporaryDirectory.resolve("invalid-runtime-cluster");
        final Path invalidRuntime = temporaryDirectory.resolve("invalid-runtime");
        Files.createDirectories(invalidRuntime.resolve("bin"));
        Files.writeString(
                invalidRuntime.resolve("bin").resolve("pg_ctl"), "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> localPostgres(storageRoot, invalidRuntime).start())
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("Failed to resolve PostgreSQL runtime");
        assertThat(ScenarioMetadata.metadataPath(storageRoot)).doesNotExist();
    }

    @Test
    void lockContentionFailsWithLockDiagnostic() throws IOException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(temporaryDirectory.resolve("runtime"));
        final Path storageRoot = temporaryDirectory.resolve("locked-cluster");
        final Path lockDirectory = storageRoot.resolve("locks");
        final Path lockPath = lockDirectory.resolve("runtime-install.lock");
        Files.createDirectories(lockDirectory);

        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = requireLock(channel)) {
            assertThat(lock.isValid()).isTrue();
            assertThatThrownBy(() -> localPostgres(storageRoot, runtime).start())
                    .isInstanceOf(ManagedPostgresException.class)
                    .hasMessageContaining("lifecycle lock")
                    .satisfies(failure -> assertThat(diagnosticText(failure)).contains(lockPath.toString()));
        }
    }

    private static ManagedPostgres localPostgres(final Path storageRoot, final FakePostgresRuntime runtime) {
        return localPostgres(storageRoot, runtime.runtimeDirectory());
    }

    private static ManagedPostgres localPostgres(final Path storageRoot, final Path runtimeDirectory) {
        return ManagedPostgres.local()
                .name("app-db")
                .version("16.4")
                .withExistingRuntime(runtimeDirectory)
                .storageProjectLocal(storageRoot)
                .credentials("postgres", TEST_PASSWORD)
                .build();
    }

    private static FileLock requireLock(final FileChannel channel) throws IOException {
        final FileLock lock = channel.tryLock();
        if (lock == null) {
            throw new IllegalStateException("could not acquire test lock");
        }

        return lock;
    }

    private static String diagnosticText(final Throwable failure) {
        if (!(failure instanceof ManagedPostgresException exception)) {
            throw new IllegalStateException(
                    "unexpected failure type: " + failure.getClass().getName());
        }

        return exception.diagnosticReport().renderText();
    }
}
