package eu.virtualparadox.managedpostgres.security;

import eu.virtualparadox.managedpostgres.config.Credentials;
import java.io.IOException;

/**
 * Persists PostgreSQL credentials.
 */
public interface CredentialStore {

    /**
     * Writes credentials to the backing store.
     *
     * @param credentials credentials to write
     * @throws IOException when the backing store cannot be written
     */
    public void write(Credentials credentials) throws IOException;
}
