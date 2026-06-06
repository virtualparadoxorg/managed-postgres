package eu.virtualparadox.managedpostgres.config.writer;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Renders PostgreSQL {@code postgresql.conf} content.
 */
public final class PostgresConfigWriter {

    private static final Map<String, String> DEFAULT_SETTINGS = Map.of(
            "listen_addresses", "127.0.0.1",
            "password_encryption", "scram-sha-256",
            "port", "5432");

    /**
     * Creates a PostgreSQL config writer.
     */
    public PostgresConfigWriter() {}

    /**
     * Renders the default {@code postgresql.conf} content.
     *
     * @return default configuration content
     */
    public String defaultConfig() {
        return write(DEFAULT_SETTINGS);
    }

    /**
     * Renders {@code postgresql.conf} content from settings.
     *
     * @param settings configuration settings
     * @return rendered configuration content
     */
    public String write(final Map<String, String> settings) {
        Objects.requireNonNull(settings, "settings");

        final StringBuilder content = new StringBuilder();
        new TreeMap<>(settings)
                .forEach((key, value) ->
                        content.append(key).append('=').append(quote(value)).append(System.lineSeparator()));

        return content.toString();
    }

    private static String quote(final String value) {
        Objects.requireNonNull(value, "value");

        final boolean numeric = value.chars().allMatch(Character::isDigit);
        final String quoted;
        if (numeric) {
            quoted = value;
        } else {
            quoted = "'%s'".formatted(value.replace("'", "''"));
        }

        return quoted;
    }
}
