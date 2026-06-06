package eu.virtualparadox.managedpostgres.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Secret value that never exposes its raw value through string conversion.
 */
public final class Secret {

    private static final int RANDOM_SECRET_BYTES = 32;
    private static final int BITS_PER_BYTE = 8;
    private static final String REDACTED = "REDACTED";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String value;
    private final int entropyBits;

    private Secret(final String value, final int entropyBits) {
        this.value = Objects.requireNonNull(value, "value");
        this.entropyBits = entropyBits;
    }

    /**
     * Creates a secret from a raw value.
     *
     * @param value raw secret value
     * @return secret
     */
    public static Secret of(final String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("secret must not be blank");
        }

        return new Secret(value, 0);
    }

    /**
     * Creates a random secret.
     *
     * @return random secret
     */
    public static Secret random() {
        final byte[] bytes = new byte[RANDOM_SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);

        return new Secret(
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), RANDOM_SECRET_BYTES * BITS_PER_BYTE);
    }

    /**
     * Creates a redacted placeholder secret.
     *
     * @return redacted placeholder secret
     */
    public static Secret redacted() {
        return new Secret(REDACTED, 0);
    }

    /**
     * Returns the minimum known generated entropy in bits.
     *
     * @return known generated entropy in bits
     */
    public int entropyBits() {
        return entropyBits;
    }

    /**
     * Returns the raw secret value for persistence or process configuration.
     *
     * @return raw secret value
     */
    public String reveal() {
        return value;
    }

    /**
     * Returns a redacted secret description.
     *
     * @return redacted secret description
     */
    @Override
    public String toString() {
        return "Secret[value=REDACTED]";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        final boolean equal;
        if (this == other) {
            equal = true;
        } else if (other instanceof Secret secret) {
            equal = value.equals(secret.value);
        } else {
            equal = false;
        }

        return equal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
