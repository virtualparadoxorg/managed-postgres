package eu.virtualparadox.managedpostgres.filesystem;

import eu.virtualparadox.managedpostgres.filesystem.lock.FileSystemLockManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * A filesystem operation that tracks created staging directories until commit.
 */
public final class JournaledFileSystemOperation implements FileSystemOperation {

    private static final String STAGING_SUFFIX = ".staging";

    private final String operationName;
    private final Path operationRoot;
    private final FileSystemOperationServices services;
    private final List<Path> stagingDirectories = new ArrayList<>();

    private boolean committed;

    /**
     * Creates a JournaledFileSystemOperation instance.
     *
     * @param operationName operation name value
     * @param operationRoot operation root value
     * @param services services value
     */
    public JournaledFileSystemOperation(
            final String operationName, final Path operationRoot, final FileSystemOperationServices services) {
        this.operationName = requireOperationName(operationName);
        this.operationRoot = Objects.requireNonNull(operationRoot, "operationRoot");
        this.services = Objects.requireNonNull(services, "services");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path createStagingDirectory(final String name) {
        final String checkedName = requireSimpleName(name);
        final Path target = operationRoot.resolve(checkedName).toAbsolutePath().normalize();
        final Path staging = stagingPathFor(target);

        final FileSystemLockManager.FileSystemLock lock = services.lockManager().lock(target);
        try {
            createStagingDirectory(staging);
            return staging;
        } finally {
            lock.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeUtf8Atomically(final Path target, final String content) {
        writeUtf8Atomically(target, content, ManagedFilePermissions.defaults());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeUtf8Atomically(final Path target, final String content, final ManagedFilePermissions permissions) {
        final Path checkedTarget =
                Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        final ManagedFilePermissions checkedPermissions = Objects.requireNonNull(permissions, "permissions");

        final FileSystemLockManager.FileSystemLock lock = services.lockManager().lock(checkedTarget);
        try {
            services.fileWriter().writeUtf8(checkedTarget, content, checkedPermissions);
        } finally {
            lock.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publishDirectory(final Path staging, final Path target) {
        final Path checkedStaging =
                Objects.requireNonNull(staging, "staging").toAbsolutePath().normalize();
        final Path checkedTarget =
                Objects.requireNonNull(target, "target").toAbsolutePath().normalize();

        final FileSystemLockManager.FileSystemLock lock = services.lockManager().lock(checkedTarget);
        try {
            services.directoryPublisher().publish(checkedStaging, checkedTarget);
            stagingDirectories.remove(checkedStaging);
        } finally {
            lock.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publishFile(final Path stagingFile, final Path target) {
        final Path checkedStagingFile = Objects.requireNonNull(stagingFile, "stagingFile")
                .toAbsolutePath()
                .normalize();
        final Path checkedTarget =
                Objects.requireNonNull(target, "target").toAbsolutePath().normalize();

        final FileSystemLockManager.FileSystemLock lock = services.lockManager().lock(checkedTarget);
        try {
            DirectoryPublisher.moveIfAbsent(checkedStagingFile, checkedTarget);
            discardEmptyStagingDirectory(checkedStagingFile);
        } finally {
            lock.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit() {
        committed = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (committed) {
            return;
        }

        for (final Path staging : List.copyOf(stagingDirectories)) {
            if (services.ownership().isOwned(staging)) {
                DirectoryPublisher.deleteRecursivelyIfExists(staging);
            }
        }
    }

    private void discardEmptyStagingDirectory(final Path stagingFile) {
        final Path stagingDirectory = Objects.requireNonNull(stagingFile.getParent(), "stagingFile parent");
        if (stagingDirectories.contains(stagingDirectory) && containsOnlyOwnershipMarker(stagingDirectory)) {
            DirectoryPublisher.deleteRecursivelyIfExists(stagingDirectory);
            stagingDirectories.remove(stagingDirectory);
        }
    }

    private boolean containsOnlyOwnershipMarker(final Path stagingDirectory) {
        final Path ownershipMarker = services.ownership()
                .markerPath(stagingDirectory)
                .toAbsolutePath()
                .normalize();
        try (Stream<Path> paths = Files.list(stagingDirectory)) {
            return paths.allMatch(path -> path.toAbsolutePath().normalize().equals(ownershipMarker));
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to inspect staging directory " + stagingDirectory, exception);
        }
    }

    private void createStagingDirectory(final Path staging) {
        try {
            Files.createDirectories(operationRoot);
            Files.createDirectory(staging);
            services.ownership().writeMarker(staging, operationName);
            stagingDirectories.add(staging);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to create staging directory " + staging, exception);
        }
    }

    private Path stagingPathFor(final Path target) {
        final String stagingName =
                ".%s.%s.%s%s".formatted(fileName(target), safeName(operationName), UUID.randomUUID(), STAGING_SUFFIX);

        return target.resolveSibling(stagingName);
    }

    private static String requireOperationName(final String operationName) {
        if (StringUtils.isBlank(operationName)) {
            throw new IllegalArgumentException("operationName must not be blank");
        }

        return operationName;
    }

    private static String requireSimpleName(final String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (Path.of(name).isAbsolute() || Path.of(name).getNameCount() != 1) {
            throw new IllegalArgumentException("name must be a single path segment");
        }

        return name;
    }

    private static String safeName(final String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private static String fileName(final Path target) {
        final Path fileName = target.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("target must have a file name");
        }

        return fileName.toString();
    }
}
