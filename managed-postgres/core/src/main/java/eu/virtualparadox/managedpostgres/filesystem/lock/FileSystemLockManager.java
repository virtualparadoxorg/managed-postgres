package eu.virtualparadox.managedpostgres.filesystem.lock;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides in-process locks for filesystem target paths.
 */
public final class FileSystemLockManager {

    private final ConcurrentMap<Path, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Creates a filesystem lock manager.
     */
    public FileSystemLockManager() {
    }

    /**
     * Acquires an exclusive lock for the supplied path.
     *
     * @param path filesystem path to lock
     * @return held filesystem lock
     */
    public FileSystemLock lock(final Path path) {
        final Path lockPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        final ReentrantLock lock = locks.computeIfAbsent(lockPath, ignored -> new ReentrantLock());

        lock.lock();

        return new FileSystemLock(lock);
    }

    /**
     * Held filesystem lock.
     */
    public static final class FileSystemLock implements AutoCloseable {

        private final ReentrantLock lock;

        private FileSystemLock(final ReentrantLock lock) {
            this.lock = lock;
        }

        /**
         * Releases this lock.
         */
        @Override
        public void close() {
            lock.unlock();
        }
    }
}
