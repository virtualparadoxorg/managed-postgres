package eu.virtualparadox.managedpostgres.lifecycle.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.Storage;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresLockServiceTest {

    @TempDir
    private Path storageRoot;

    PostgresLockServiceTest() {
    }

    @Test
    void lockServiceRefusesConcurrentLockAcquisitionInSameJvm() throws IOException {
        final PostgresLockService firstService = new PostgresLockService();
        final PostgresLockService secondService = new PostgresLockService();
        final Path lockPath = storageRoot.resolve(PostgresLayout.OPERATION_LOCK_FILE);

        try (HeldPostgresLock heldLock = firstService.acquire(lockPath)) {
            assertThat(heldLock.path()).isEqualTo(lockPath.toAbsolutePath().normalize());
            assertThatExceptionOfType(ManagedPostgresException.class)
                    .isThrownBy(() -> secondService.acquire(lockPath))
                    .withMessageContaining("already held");
        }
    }

    @Test
    void lockCanBeReacquiredAfterRelease() throws IOException {
        final PostgresLockService service = new PostgresLockService();
        final Path lockPath = storageRoot.resolve(PostgresLayout.MANAGER_LOCK_FILE);

        try (HeldPostgresLock first = service.acquire(lockPath)) {
            assertThat(first.path()).isEqualTo(lockPath.toAbsolutePath().normalize());
            closeAction(first).run();
            closeAction(first).run();
        }

        try (HeldPostgresLock second = service.acquire(lockPath)) {
            assertThat(second.path()).isEqualTo(lockPath.toAbsolutePath().normalize());
        }
    }

    private static Runnable closeAction(final HeldPostgresLock heldLock) {
        return heldLock::close;
    }

    @Test
    void layoutLockHelpersUseLayoutPaths() throws IOException {
        final PostgresLayout layout = PostgresLayout.create(Storage.projectLocal(storageRoot));
        final PostgresLockService service = new PostgresLockService();

        try (HeldPostgresLock runtimeLock = service.acquireRuntimeInstallLock(layout);
                HeldPostgresLock operationLock = service.acquireOperationLock(layout);
                HeldPostgresLock managerLock = service.acquireManagerLock(layout)) {
            assertThat(runtimeLock.path()).isEqualTo(layout.runtimeInstallLockPath());
            assertThat(operationLock.path()).isEqualTo(layout.operationLockPath());
            assertThat(managerLock.path()).isEqualTo(layout.managerLockPath());
        }
    }

    @Test
    void lifecycleLocksAreAcquiredInLayoutOrder() throws IOException {
        final PostgresLayout layout = PostgresLayout.create(Storage.projectLocal(storageRoot));
        final PostgresLockService service = new PostgresLockService();

        try (HeldPostgresLocks locks = service.acquireLifecycleLocks(layout)) {
            assertThat(locks.locks())
                    .extracting(HeldPostgresLock::path)
                    .containsExactlyElementsOf(layout.lockOrder());
            assertThatExceptionOfType(ManagedPostgresException.class)
                    .isThrownBy(() -> service.acquireRuntimeInstallLock(layout))
                    .withMessageContaining("already held");
        }
    }
}
