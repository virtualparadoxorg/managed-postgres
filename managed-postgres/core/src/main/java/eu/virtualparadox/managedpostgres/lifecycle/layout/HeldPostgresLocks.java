package eu.virtualparadox.managedpostgres.lifecycle.layout;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Held lifecycle locks acquired in global PostgreSQL lock order.
 */
public final class HeldPostgresLocks implements AutoCloseable {

    private final List<HeldPostgresLock> locks;

    private HeldPostgresLocks(final List<HeldPostgresLock> locks) {
        this.locks = List.copyOf(Objects.requireNonNull(locks, "locks"));
    }

    /**
     * Returns the acquire result.
     *
     * @param service service value
     * @param layout layout value
     * @return acquire result
     */
    public static HeldPostgresLocks acquire(final PostgresLockService service, final PostgresLayout layout) {
        final List<HeldPostgresLock> acquiredLocks = new ArrayList<>();
        try {
            for (final Path lockPath : Objects.requireNonNull(layout, "layout").lockOrder()) {
                acquiredLocks.add(service.acquire(lockPath));
            }

            return new HeldPostgresLocks(acquiredLocks);
        } catch (ManagedPostgresException exception) {
            closeLocks(acquiredLocks, exception);
            throw exception;
        }
    }

    /**
     * Returns the held locks in acquisition order.
     *
     * @return held locks
     */
    public List<HeldPostgresLock> locks() {
        return locks;
    }

    /**
     * Releases lifecycle locks in reverse acquisition order.
     */
    @Override
    public void close() {
        closeLocks(locks);
    }

    private static void closeLocks(final List<HeldPostgresLock> locks) {
        for (int index = locks.size() - 1; index >= 0; index--) {
            locks.get(index).close();
        }
    }

    private static void closeLocks(
            final List<HeldPostgresLock> locks,
            final ManagedPostgresException originalFailure) {
        for (int index = locks.size() - 1; index >= 0; index--) {
            try {
                locks.get(index).close();
            } catch (ManagedPostgresException exception) {
                originalFailure.addSuppressed(exception);
            }
        }
    }
}
