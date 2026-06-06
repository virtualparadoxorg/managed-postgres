package eu.virtualparadox.managedpostgres.config.writer;

/**
 * Renders PostgreSQL {@code pg_hba.conf} content.
 */
public final class PgHbaConfigWriter {

    private static final String DEFAULT_AUTH_METHOD = "scram-sha-256";

    /**
     * Creates a pg_hba config writer.
     */
    public PgHbaConfigWriter() {}

    /**
     * Renders the default {@code pg_hba.conf} content.
     *
     * @return default host-based authentication content
     */
    public String defaultConfig() {
        return ("local all all %s%n" + "host all all 127.0.0.1/32 %s%n" + "host all all ::1/128 %s%n")
                .formatted(DEFAULT_AUTH_METHOD, DEFAULT_AUTH_METHOD, DEFAULT_AUTH_METHOD);
    }
}
