package eu.virtualparadox.managedpostgres.runtime.packaging.testsupport;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates small synthetic PostgreSQL source archives for orchestration tests.
 */
public final class SourceArchiveFixture {

    private SourceArchiveFixture() {
    }

    /**
     * Creates a minimal source archive with a single top-level source directory.
     *
     * @param tempDir test scratch directory
     * @param topLevelDirectory source root directory name
     * @return archive path
     * @throws IOException if the archive cannot be written
     */
    public static Path create(final Path tempDir, final String topLevelDirectory) throws IOException {
        final Path archive = tempDir.resolve(topLevelDirectory + ".zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeDirectory(zipOutputStream, topLevelDirectory + "/");
            writeFile(zipOutputStream, topLevelDirectory + "/README", "PostgreSQL source fixture\n");
            writeFile(zipOutputStream, topLevelDirectory + "/configure", "#!/bin/sh\nexit 0\n");
        }

        return archive;
    }

    /**
     * Creates a minimal source archive without a single top-level directory.
     *
     * @param tempDir test scratch directory
     * @param archiveName archive file stem
     * @return archive path
     * @throws IOException if the archive cannot be written
     */
    public static Path createFlat(final Path tempDir, final String archiveName) throws IOException {
        final Path archive = tempDir.resolve(archiveName + ".zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeFile(zipOutputStream, "README", "PostgreSQL source fixture\n");
            writeFile(zipOutputStream, "configure", "#!/bin/sh\nexit 0\n");
        }
        return archive;
    }

    /**
     * Builds release metadata that points at a synthetic source archive.
     *
     * @param archive synthetic source archive
     * @return release metadata with a matching checksum
     * @throws IOException if the archive cannot be read
     */
    public static PostgresRelease releaseForArchive(final Path archive) throws IOException {
        return new PostgresRelease(
                16,
                "16.14",
                archive.toUri(),
                sha256Hex(Files.readAllBytes(archive)));
    }

    private static void writeDirectory(final ZipOutputStream zipOutputStream, final String entryName) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.closeEntry();
    }

    private static void writeFile(
            final ZipOutputStream zipOutputStream,
            final String entryName,
            final String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static String sha256Hex(final byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", exception);
        }
    }
}
