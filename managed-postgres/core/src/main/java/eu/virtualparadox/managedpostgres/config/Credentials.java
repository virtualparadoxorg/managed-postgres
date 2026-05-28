package eu.virtualparadox.managedpostgres.config;

import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable PostgreSQL credentials configuration.
 *
 * @param username username
 * @param password password
 * @param persistent whether generated credentials should be stable
 * @param localTrustOnly whether local trust authentication is allowed
 */
public record Credentials(String username, Secret password, boolean persistent, boolean localTrustOnly) {

    /**
     * Creates immutable credentials configuration.
     *
     * @param username username
     * @param password password
     * @param persistent whether generated credentials should be stable
     * @param localTrustOnly whether local trust authentication is allowed
     */
    public Credentials {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Objects.requireNonNull(password, "password");
    }

    /**
     * Creates generated temporary credentials.
     *
     * @return generated temporary credentials
     */
    public static Credentials generated() {
        return new Credentials("postgres", Secret.random(), false, false);
    }

    /**
     * Creates generated persistent credentials.
     *
     * @return generated persistent credentials
     */
    public static Credentials generatedPersistent() {
        return new Credentials("postgres", Secret.random(), true, false);
    }

    /**
     * Creates explicit credentials.
     *
     * @param username username
     * @param password password
     * @return explicit credentials
     */
    public static Credentials of(final String username, final Secret password) {
        return new Credentials(username, password, false, false);
    }

    /**
     * Creates local trust-only credentials.
     *
     * @return local trust-only credentials
     */
    public static Credentials trustLocalOnly() {
        return new Credentials("postgres", Secret.redacted(), false, true);
    }

    /**
     * Returns a redacted credentials description.
     *
     * @return redacted credentials description
     */
    @Override
    public String toString() {
        return "Credentials[username=%s, password=REDACTED, persistent=%s, localTrustOnly=%s]"
                .formatted(username, persistent, localTrustOnly);
    }
}
