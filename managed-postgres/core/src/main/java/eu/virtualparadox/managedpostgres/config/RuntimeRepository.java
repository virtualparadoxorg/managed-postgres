package eu.virtualparadox.managedpostgres.config;

import java.net.URI;
import java.util.Objects;

/**
 * User-facing PostgreSQL runtime repository configuration.
 *
 * <p>The repository identifies where downloaded PostgreSQL runtime artifacts can
 * be obtained. It does not expose platform classifiers or archive mechanics to
 * the common public API.
 *
 * @param uri repository URI
 */
public record RuntimeRepository(URI uri) {

    private static final URI OFFICIAL_URI = URI.create("managed-postgres:official");

    /**
     * Creates runtime repository configuration.
     *
     * @param uri repository URI
     */
    public RuntimeRepository {
        final URI checkedUri = Objects.requireNonNull(uri, "uri");
        if (!checkedUri.isAbsolute()) {
            throw new IllegalArgumentException("runtime repository URI must be absolute");
        }

        uri = checkedUri;
    }

    /**
     * Uses the framework official runtime repository.
     *
     * @return official runtime repository configuration
     */
    public static RuntimeRepository official() {
        return new RuntimeRepository(OFFICIAL_URI);
    }

    /**
     * Uses a custom runtime repository.
     *
     * @param uri custom repository URI
     * @return custom runtime repository configuration
     */
    public static RuntimeRepository custom(final URI uri) {
        return new RuntimeRepository(uri);
    }
}
