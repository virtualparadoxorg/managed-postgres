package eu.virtualparadox.managedpostgres.filesystem;

import java.util.Objects;
import eu.virtualparadox.managedpostgres.filesystem.lock.FileSystemLockManager;

/**
 * Collaborators used by managed filesystem operations.
 *
 * @param fileWriter atomic file writer
 * @param directoryPublisher directory publisher
 * @param ownership managed path ownership marker
 * @param lockManager filesystem lock manager
 */
record FileSystemOperationServices(
        AtomicFileWriter fileWriter,
        DirectoryPublisher directoryPublisher,
        ManagedPathOwnership ownership,
        FileSystemLockManager lockManager) {

    FileSystemOperationServices {
        Objects.requireNonNull(fileWriter, "fileWriter");
        Objects.requireNonNull(directoryPublisher, "directoryPublisher");
        Objects.requireNonNull(ownership, "ownership");
        Objects.requireNonNull(lockManager, "lockManager");
    }

    static FileSystemOperationServices defaults() {
        return new FileSystemOperationServices(
                new AtomicFileWriter(),
                new DirectoryPublisher(),
                new ManagedPathOwnership(),
                new FileSystemLockManager());
    }
}
