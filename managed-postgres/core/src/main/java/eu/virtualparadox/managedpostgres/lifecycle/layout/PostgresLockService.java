package eu.virtualparadox.managedpostgres.lifecycle.layout;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.filesystem.lock.ManagedLockFiles;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Acquires fail-fast lifecycle locks for managed PostgreSQL storage.
 */
public final class PostgresLockService {

    private static final ConcurrentMap<Path, Object> HELD_LOCKS = new ConcurrentHashMap<>();

    private final ManagedLockFiles lockFiles;

    /**
     * Creates a PostgreSQL lifecycle lock service.
     */
    public PostgresLockService() {
        this(new ManagedLockFiles());
    }

    /**
     * Creates a PostgresLockService instance.
     *
     * @param lockFiles lock files value
     */
    public PostgresLockService(final ManagedLockFiles lockFiles) {
        this.lockFiles = Objects.requireNonNull(lockFiles, "lockFiles");
    }

    /**
     * Acquires all lifecycle locks in the layout-defined global order.
     *
     * @param layout PostgreSQL filesystem layout
     * @return held lifecycle locks
     */
    public HeldPostgresLocks acquireLifecycleLocks(final PostgresLayout layout) {
        return HeldPostgresLocks.acquire(this, layout);
    }

    /**
     * Acquires the runtime installation lock for a layout.
     *
     * @param layout PostgreSQL filesystem layout
     * @return held runtime installation lock
     */
    public HeldPostgresLock acquireRuntimeInstallLock(final PostgresLayout layout) {
        return acquire(Objects.requireNonNull(layout, "layout").runtimeInstallLockPath());
    }

    /**
     * Acquires the operation lock for a layout.
     *
     * @param layout PostgreSQL filesystem layout
     * @return held operation lock
     */
    public HeldPostgresLock acquireOperationLock(final PostgresLayout layout) {
        return acquire(Objects.requireNonNull(layout, "layout").operationLockPath());
    }

    /**
     * Acquires the manager lock for a layout.
     *
     * @param layout PostgreSQL filesystem layout
     * @return held manager lock
     */
    public HeldPostgresLock acquireManagerLock(final PostgresLayout layout) {
        return acquire(Objects.requireNonNull(layout, "layout").managerLockPath());
    }

    /**
     * Acquires an exclusive lifecycle lock path.
     *
     * @param lockPath lifecycle lock path
     * @return held lifecycle lock
     */
    public HeldPostgresLock acquire(final Path lockPath) {
        final Path normalizedPath = normalize(lockPath);
        final Object owner = new Object();

        if (HELD_LOCKS.putIfAbsent(normalizedPath, owner) != null) {
            throw PostgresLockFailure.create("PostgreSQL lifecycle lock is already held", normalizedPath);
        }

        try {
            return lockOpenedChannel(normalizedPath, owner, lockFiles.open(normalizedPath));
        } catch (UncheckedIOException exception) {
            HELD_LOCKS.remove(normalizedPath, owner);
            throw PostgresLockFailure.create("Failed to acquire PostgreSQL lifecycle lock", normalizedPath, exception);
        } catch (ManagedPostgresException exception) {
            HELD_LOCKS.remove(normalizedPath, owner);
            throw exception;
        }
    }

    private static Path normalize(final Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    /**
     * Performs the release operation.
     *
     * @param path path value
     * @param owner owner value
     */
    public static void release(final Path path, final Object owner) {
        HELD_LOCKS.remove(path, owner);
    }

    private static HeldPostgresLock lockOpenedChannel(final Path path, final Object owner, final FileChannel channel) {
        return HeldPostgresLock.open(path, owner, channel);
    }
}
