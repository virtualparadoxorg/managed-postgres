package eu.virtualparadox.managedpostgres.runtime;

import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Expected checksum for a downloaded PostgreSQL runtime artifact.
 *
 * @param algorithm checksum algorithm identifier
 * @param hex lower-case hexadecimal checksum value
 */
public record Checksum(String algorithm, String hex) {

    private static final String SHA256 = "sha256";
    private static final int SHA256_HEX_LENGTH = 64;

    /**
     * Creates checksum value object.
     *
     * @param algorithm checksum algorithm identifier
     * @param hex hexadecimal checksum value
     */
    public Checksum {
        algorithm = requireSha256(algorithm);
        hex = requireSha256Hex(hex);
    }

    /**
     * Parses a checksum in {@code sha256:<64 hex characters>} format.
     *
     * @param value checksum text
     * @return parsed checksum
     */
    public static Checksum parse(final String value) {
        final String checksumText = Objects.requireNonNull(value, "checksum");
        if (StringUtils.isBlank(checksumText)) {
            throw new IllegalArgumentException("checksum must not be blank");
        }

        final int separator = checksumText.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("checksum must use sha256:<64 hex characters> format");
        }

        return new Checksum(checksumText.substring(0, separator), checksumText.substring(separator + 1));
    }

    /**
     * Returns the JDK message digest algorithm name.
     *
     * @return JDK message digest algorithm name
     */
    public String messageDigestAlgorithm() {
        return "SHA-256";
    }

    private static String requireSha256(final String algorithm) {
        final String checkedAlgorithm = Objects.requireNonNull(algorithm, "algorithm");
        if (!SHA256.equals(checkedAlgorithm)) {
            throw new IllegalArgumentException("checksum algorithm must be sha256");
        }

        return SHA256;
    }

    private static String requireSha256Hex(final String hex) {
        final String checkedHex = Objects.requireNonNull(hex, "hex");
        if (checkedHex.length() != SHA256_HEX_LENGTH
                || !StringUtils.containsOnly(checkedHex, "0123456789abcdefABCDEF")) {
            throw new IllegalArgumentException("sha256 checksum must contain 64 hexadecimal characters");
        }

        return lowerCaseAsciiHex(checkedHex);
    }

    private static String lowerCaseAsciiHex(final String hex) {
        final char[] characters = hex.toCharArray();
        for (int index = 0; index < characters.length; index++) {
            final char character = characters[index];
            if (character >= 'A' && character <= 'F') {
                characters[index] = (char) (character + ('a' - 'A'));
            }
        }

        return new String(characters);
    }
}
