package eu.virtualparadox.managedpostgres.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Verifies downloaded PostgreSQL runtime artifact checksums.
 */
public final class ChecksumVerifier {

    private static final int BUFFER_SIZE = 8192;

    /**
     * Creates a checksum verifier.
     */
    public ChecksumVerifier() {}

    /**
     * Verifies that the artifact content matches the expected checksum.
     *
     * @param artifact artifact path
     * @param checksum expected checksum
     * @return verified artifact path
     * @throws IOException if the artifact cannot be read
     */
    public Path verify(final Path artifact, final Checksum checksum) throws IOException {
        final Path checkedArtifact = Objects.requireNonNull(artifact, "artifact");
        final Checksum checkedChecksum = Objects.requireNonNull(checksum, "checksum");
        final String actualHex = digestHex(checkedArtifact, checkedChecksum);
        if (!checkedChecksum.hex().equals(actualHex)) {
            throw new IllegalArgumentException(
                    "checksum mismatch for " + checkedArtifact + " using " + checkedChecksum.messageDigestAlgorithm());
        }

        return checkedArtifact;
    }

    private static String digestHex(final Path artifact, final Checksum checksum) throws IOException {
        final MessageDigest digest = messageDigest(checksum);
        final byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream inputStream = Files.newInputStream(artifact)) {
            int bytesRead = inputStream.read(buffer);
            while (bytesRead >= 0) {
                digest.update(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest messageDigest(final Checksum checksum) {
        try {
            return MessageDigest.getInstance(checksum.messageDigestAlgorithm());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "checksum algorithm is unavailable: " + checksum.messageDigestAlgorithm(), exception);
        }
    }
}
