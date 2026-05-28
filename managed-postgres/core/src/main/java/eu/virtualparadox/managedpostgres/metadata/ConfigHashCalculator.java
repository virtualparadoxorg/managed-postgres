package eu.virtualparadox.managedpostgres.metadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Calculates stable drift hashes for PostgreSQL configuration values.
 */
public final class ConfigHashCalculator {

    /**
     * Creates a config hash calculator.
     */
    public ConfigHashCalculator() {
    }

    /**
     * Calculates a SHA-256 hash that is stable across input map ordering.
     *
     * @param settings configuration settings
     * @return hexadecimal SHA-256 hash
     */
    public String calculate(final Map<String, String> settings) {
        Objects.requireNonNull(settings, "settings");

        final MessageDigest digest = sha256();
        new TreeMap<>(settings).forEach((key, value) -> {
            digest.update(Objects.requireNonNull(key, "key").getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        });

        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
