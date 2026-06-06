package eu.virtualparadox.managedpostgres.filesystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.UUID;

/**
 * Writes files by staging bytes into a sibling temporary file before publication.
 */
public final class AtomicFileWriter {

    private static final String TEMPORARY_FILE_SUFFIX = ".tmp";

    /**
     * Creates an atomic file writer.
     */
    public AtomicFileWriter() {}

    /**
     * Stages UTF-8 text into a sibling temporary file without exposing the target path.
     *
     * @param target final file path
     * @param content UTF-8 text content
     * @return pending staged write
     */
    public PendingWrite stageUtf8(final Path target, final String content) {
        return stageUtf8(target, content, ManagedFilePermissions.defaults());
    }

    /**
     * Stages UTF-8 text into a sibling temporary file with requested permissions.
     *
     * @param target final file path
     * @param content UTF-8 text content
     * @param permissions managed file permissions
     * @return pending staged write
     */
    public PendingWrite stageUtf8(final Path target, final String content, final ManagedFilePermissions permissions) {
        final Path checkedTarget = requireTargetWithParent(target);
        final String checkedContent = Objects.requireNonNull(content, "content");
        final ManagedFilePermissions requestedPermissions = Objects.requireNonNull(permissions, "permissions");
        final Path targetParent = parentDirectory(checkedTarget);
        final Path temporaryPath = temporaryPathFor(checkedTarget);

        try {
            Files.createDirectories(targetParent);
            createTemporaryFile(temporaryPath, requestedPermissions);
            Files.writeString(temporaryPath, checkedContent, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
            forceFile(temporaryPath);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to stage file write for " + checkedTarget, exception);
        }

        return new PendingWrite(temporaryPath, checkedTarget);
    }

    /**
     * Writes UTF-8 text atomically into the target path.
     *
     * @param target final file path
     * @param content UTF-8 text content
     */
    public void writeUtf8(final Path target, final String content) {
        final PendingWrite write = stageUtf8(target, content);

        write.commit();
    }

    /**
     * Writes UTF-8 text atomically into the target path with requested permissions.
     *
     * @param target final file path
     * @param content UTF-8 text content
     * @param permissions managed file permissions
     */
    public void writeUtf8(final Path target, final String content, final ManagedFilePermissions permissions) {
        final PendingWrite write = stageUtf8(target, content, permissions);

        write.commit();
    }

    private static Path requireTargetWithParent(final Path target) {
        final Path checkedTarget =
                Objects.requireNonNull(target, "target").toAbsolutePath().normalize();

        if (checkedTarget.getParent() == null) {
            throw new IllegalArgumentException("target must have a parent directory");
        }

        return checkedTarget;
    }

    private static Path temporaryPathFor(final Path target) {
        final String fileName = fileName(target);
        final String temporaryFileName = "." + fileName + "." + UUID.randomUUID() + TEMPORARY_FILE_SUFFIX;

        return target.resolveSibling(temporaryFileName);
    }

    private static Path parentDirectory(final Path target) {
        final Path parent = target.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("target must have a parent directory");
        }

        return parent;
    }

    private static String fileName(final Path target) {
        final Path fileName = target.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("target must have a file name");
        }

        return fileName.toString();
    }

    private static void createTemporaryFile(final Path temporaryPath, final ManagedFilePermissions requestedPermissions)
            throws IOException {
        if (!requestedPermissions.hasExplicitPermissions() || !supportsPosix(parentDirectory(temporaryPath))) {
            Files.createFile(temporaryPath);
        } else {
            Files.createFile(
                    temporaryPath, PosixFilePermissions.asFileAttribute(requestedPermissions.posixPermissions()));
        }
    }

    private static boolean supportsPosix(final Path directory) throws IOException {
        return Files.getFileStore(directory).supportsFileAttributeView(PosixFileAttributeView.class);
    }

    private static void forceFile(final Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /**
     * Staged file write that can be committed into the target path.
     *
     * @param temporaryPath temporary staged file path
     * @param target final target path
     */
    public record PendingWrite(Path temporaryPath, Path target) {

        /**
         * Creates a pending staged file write.
         *
         * @param temporaryPath temporary staged file path
         * @param target final target path
         */
        public PendingWrite {
            Objects.requireNonNull(temporaryPath, "temporaryPath");
            Objects.requireNonNull(target, "target");
        }

        /**
         * Publishes the staged file into the target path.
         */
        public void commit() {
            DirectoryPublisher.moveReplacingExisting(temporaryPath, target);
        }

        /**
         * Discards the staged file.
         */
        public void discard() {
            try {
                Files.deleteIfExists(temporaryPath);
            } catch (final IOException exception) {
                throw new UncheckedIOException("failed to discard staged file " + temporaryPath, exception);
            }
        }
    }
}
