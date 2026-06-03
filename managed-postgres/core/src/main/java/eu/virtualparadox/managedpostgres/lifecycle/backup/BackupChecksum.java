package eu.virtualparadox.managedpostgres.lifecycle.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Calculates checksums for logical backup artifacts.
 */
public final class BackupChecksum {

    private static final String SHA_256 = "SHA-256";

    private BackupChecksum() {}

    /**
     * Returns the sha256 result.
     *
     * @param path path value
     * @return sha256 result
     */
    public static String sha256(final Path path) {
        final MessageDigest digest = messageDigest();
        final Path checkedPath = Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(checkedPath);
                DigestInputStream digestInput = new DigestInputStream(input, digest)) {
            digestInput.transferTo(OutputStream.nullOutputStream());
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to checksum backup file " + checkedPath, exception);
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
