package eu.virtualparadox.managedpostgres.security;

import eu.virtualparadox.managedpostgres.config.Credentials;
import org.apache.commons.lang3.StringUtils;

/**
 * Generates PostgreSQL credentials backed by cryptographically strong secrets.
 */
public final class CredentialGenerator {

    private static final String DEFAULT_USERNAME = "postgres";

    /**
     * Creates a credential generator.
     */
    public CredentialGenerator() {}

    /**
     * Generates temporary credentials for the default PostgreSQL owner.
     *
     * @return generated credentials
     */
    public Credentials generate() {
        return generate(DEFAULT_USERNAME, false);
    }

    /**
     * Generates credentials for a PostgreSQL owner.
     *
     * @param username username to place in generated credentials
     * @param persistent whether the generated credentials are intended to be persisted
     * @return generated credentials
     */
    public Credentials generate(final String username, final boolean persistent) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("username must not be blank");
        }

        return new Credentials(username, Secret.random(), persistent, false);
    }
}
