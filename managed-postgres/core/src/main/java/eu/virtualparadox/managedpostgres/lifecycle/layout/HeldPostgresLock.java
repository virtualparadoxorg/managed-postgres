package eu.virtualparadox.managedpostgres.lifecycle.layout;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Held lifecycle lock.
 */
public final class HeldPostgresLock implements AutoCloseable {

    private final Path path;
    private final FileChannel channel;
    private final FileLock fileLock;
    private final Object owner;
    private final AtomicBoolean closed = new AtomicBoolean();

    private HeldPostgresLock(
            final Path path,
            final FileChannel channel,
            final FileLock fileLock,
            final Object owner) {
        this.path = Objects.requireNonNull(path, "path");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.fileLock = Objects.requireNonNull(fileLock, "fileLock");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * Returns the open result.
     *
     * @param path path value
     * @param owner owner value
     * @param channel channel value
     * @return open result
     */
    public static HeldPostgresLock open(
            final Path path,
            final Object owner,
            final FileChannel channel) {
        try {
            final FileLock lock = channel.tryLock();
            if (lock == null) {
                throw PostgresLockFailure.create("PostgreSQL lifecycle lock is already held by another process", path);
            }

            return new HeldPostgresLock(path, channel, lock, owner);
        } catch (IOException exception) {
            closeChannelQuietly(channel, exception);
            throw PostgresLockFailure.create("Failed to acquire PostgreSQL lifecycle lock", path, exception);
        } catch (OverlappingFileLockException exception) {
            closeChannelQuietly(channel, exception);
            throw PostgresLockFailure.create(
                    "PostgreSQL lifecycle lock is already held by this process",
                    path,
                    exception);
        }
    }

    /**
     * Returns the held lock path.
     *
     * @return held lock path
     */
    public Path path() {
        return path;
    }

    /**
     * Releases this lifecycle lock.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                releaseResources();
            } finally {
                PostgresLockService.release(path, owner);
            }
        }
    }

    private void releaseResources() {
        IOException failure = null;
        try {
            fileLock.release();
        } catch (IOException exception) {
            failure = exception;
        }

        try {
            channel.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }

        if (failure != null) {
            throw PostgresLockFailure.create("Failed to release PostgreSQL lifecycle lock", path, failure);
        }
    }

    private static void closeChannelQuietly(final FileChannel channel, final Throwable originalFailure) {
        try {
            channel.close();
        } catch (IOException exception) {
            originalFailure.addSuppressed(exception);
        }
    }
}
