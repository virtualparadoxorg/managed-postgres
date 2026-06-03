package eu.virtualparadox.managedpostgres.lifecycle.identity;

import org.apache.commons.lang3.StringUtils;

/**
 * Coordinates postgres identifier behavior for managed PostgreSQL internals.
 */
public final class PostgresIdentifier {

    private PostgresIdentifier() {}

    /**
     * Returns the quote result.
     *
     * @param identifier identifier value
     * @return quote result
     */
    public static String quote(final String identifier) {
        if (StringUtils.isBlank(identifier)) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        if (identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("identifier must not contain NUL");
        }

        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
