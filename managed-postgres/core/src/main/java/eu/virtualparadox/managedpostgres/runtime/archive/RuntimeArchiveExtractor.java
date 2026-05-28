package eu.virtualparadox.managedpostgres.runtime.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.lang3.StringUtils;

/**
 * Safely extracts PostgreSQL runtime archives into staging directories.
 */
public final class RuntimeArchiveExtractor {

    private static final byte ZIP_MAGIC_FIRST = 0x50;
    private static final byte ZIP_MAGIC_SECOND = 0x4b;
    private static final byte GZIP_MAGIC_FIRST = 0x1f;
    private static final byte GZIP_MAGIC_SECOND = (byte) 0x8b;
    private static final int SIGNATURE_LENGTH = 2;
    private static final String BIN_DIRECTORY = "bin";

    /**
     * Creates a runtime archive extractor.
     */
    public RuntimeArchiveExtractor() {
    }

    /**
     * Extracts a supported runtime archive under the supplied staging directory.
     *
     * @param archive runtime archive path
     * @param stagingDirectory target staging directory
     * @return normalized staging directory
     * @throws IOException if the archive cannot be read or extracted
     */
    public Path extract(final Path archive, final Path stagingDirectory) throws IOException {
        final Path checkedArchive = Objects.requireNonNull(archive, "archive")
                .toAbsolutePath()
                .normalize();
        final Path checkedStagingDirectory = Objects.requireNonNull(stagingDirectory, "stagingDirectory")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(checkedStagingDirectory);
        extractArchive(checkedArchive, checkedStagingDirectory, detectFormat(checkedArchive));

        return checkedStagingDirectory;
    }

    private static void extractArchive(
            final Path archive,
            final Path stagingDirectory,
            final ArchiveFormat format) throws IOException {
        if (ArchiveFormat.ZIP == format) {
            extractZip(archive, stagingDirectory);
        } else {
            extractTarGzip(archive, stagingDirectory);
        }
    }

    private static ArchiveFormat detectFormat(final Path archive) throws IOException {
        final byte[] signature = new byte[SIGNATURE_LENGTH];
        final int bytesRead;
        try (InputStream inputStream = Files.newInputStream(archive)) {
            bytesRead = inputStream.read(signature);
        }

        final ArchiveFormat format;
        if (hasZipMagic(signature, bytesRead)) {
            format = ArchiveFormat.ZIP;
        } else if (hasGzipMagic(signature, bytesRead)) {
            format = ArchiveFormat.TAR_GZIP;
        } else {
            throw new IllegalArgumentException("unsupported runtime archive format: " + archive);
        }

        return format;
    }

    private static boolean hasZipMagic(final byte[] signature, final int bytesRead) {
        return bytesRead >= SIGNATURE_LENGTH
                && signature[0] == ZIP_MAGIC_FIRST
                && signature[1] == ZIP_MAGIC_SECOND;
    }

    private static boolean hasGzipMagic(final byte[] signature, final int bytesRead) {
        return bytesRead >= SIGNATURE_LENGTH
                && signature[0] == GZIP_MAGIC_FIRST
                && signature[1] == GZIP_MAGIC_SECOND;
    }

    private static void extractZip(final Path archive, final Path stagingDirectory) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                extractZipEntry(stagingDirectory, zipInputStream, entry);
                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        }
    }

    private static void extractZipEntry(
            final Path stagingDirectory,
            final ZipInputStream zipInputStream,
            final ZipEntry entry) throws IOException {
        final Path target = safeTarget(stagingDirectory, entry.getName());
        if (entry.isDirectory()) {
            Files.createDirectories(target);
        } else {
            extractRegularFile(stagingDirectory, zipInputStream, target);
        }
    }

    private static void extractTarGzip(final Path archive, final Path stagingDirectory) throws IOException {
        try (TarGzipArchiveReader reader = new TarGzipArchiveReader(archive)) {
            reader.extractTo(stagingDirectory);
        }
    }

    private static boolean isRegularTarFile(final TarArchiveEntry entry) {
        return entry.isFile()
                && !entry.isSymbolicLink()
                && !entry.isLink()
                && !entry.isCharacterDevice()
                && !entry.isBlockDevice()
                && !entry.isFIFO();
    }

    private static void extractRegularFile(
            final Path stagingDirectory,
            final InputStream inputStream,
            final Path target) throws IOException {
        Files.createDirectories(parentDirectory(target));
        Files.copy(inputStream, target);
        repairExecutablePermission(stagingDirectory, target);
    }

    private static Path safeTarget(final Path stagingDirectory, final String entryName) {
        if (StringUtils.isBlank(entryName) || StringUtils.containsAny(entryName, '\\', ':')) {
            throw new IllegalArgumentException("archive entry is unsafe: " + entryName);
        }

        final Path target = stagingDirectory.resolve(entryName).normalize();
        if (!target.startsWith(stagingDirectory)) {
            throw new IllegalArgumentException("archive entry resolves outside staging directory: " + entryName);
        }

        return target;
    }

    private static Path parentDirectory(final Path path) {
        return Objects.requireNonNull(path.getParent(), "archive entry target parent directory");
    }

    private static void repairExecutablePermission(final Path stagingDirectory, final Path target) throws IOException {
        if (!isRuntimeBinary(stagingDirectory.relativize(target))) {
            return;
        }

        final PosixFileAttributeView view = Files.getFileAttributeView(target, PosixFileAttributeView.class);
        if (view != null) {
            final Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
            permissions.addAll(Files.getPosixFilePermissions(target));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(target, permissions);
        }
    }

    private static boolean isRuntimeBinary(final Path relativePath) {
        return relativePath.getNameCount() == 2
                && BIN_DIRECTORY.equals(relativePath.getName(0).toString());
    }

    private enum ArchiveFormat {
        ZIP,
        TAR_GZIP
    }

    private static final class TarGzipArchiveReader implements AutoCloseable {

        private final TarArchiveInputStream inputStream;

        private TarGzipArchiveReader(final Path archive) throws IOException {
            this.inputStream = new TarArchiveInputStream(
                    new GzipCompressorInputStream(Files.newInputStream(archive)));
        }

        private void extractTo(final Path stagingDirectory) throws IOException {
            TarArchiveEntry entry = nextEntry();
            while (entry != null) {
                extractEntry(stagingDirectory, entry);
                entry = nextEntry();
            }
        }

        private TarArchiveEntry nextEntry() throws IOException {
            return inputStream.getNextEntry();
        }

        private void extractEntry(
                final Path stagingDirectory,
                final TarArchiveEntry entry) throws IOException {
            final Path target = safeTarget(stagingDirectory, entry.getName());
            if (entry.isDirectory()) {
                Files.createDirectories(target);
            } else if (isRegularTarFile(entry)) {
                extractRegularFile(stagingDirectory, inputStream, target);
            } else {
                throw new IllegalArgumentException(
                        "runtime tar archive may contain only regular files and directories: " + entry.getName());
            }
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
