package eu.virtualparadox.managedpostgres.lifecycle.psql;

import java.util.Objects;

/**
 * Coordinates postgres sql literal behavior for managed PostgreSQL internals.
 */
public final class PostgresSqlLiteral {

    private PostgresSqlLiteral() {}

    /**
     * Returns the quote result.
     *
     * @param value value value
     * @return quote result
     */
    public static String quote(final String value) {
        return '\'' + Objects.requireNonNull(value, "value").replace("'", "''") + '\'';
    }
}
