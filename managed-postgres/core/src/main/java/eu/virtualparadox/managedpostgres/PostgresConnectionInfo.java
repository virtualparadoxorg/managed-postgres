package eu.virtualparadox.managedpostgres;

import eu.virtualparadox.managedpostgres.internal.ConnectionInfoDataSource;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Objects;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable PostgreSQL connection details.
 *
 * @param host PostgreSQL host
 * @param port PostgreSQL port
 * @param database database name
 * @param username username
 * @param password password
 */
public record PostgresConnectionInfo(String host, int port, String database, String username, Secret password) {

    /**
     * Creates immutable PostgreSQL connection details.
     *
     * @param host PostgreSQL host
     * @param port PostgreSQL port
     * @param database database name
     * @param username username
     * @param password password
     */
    public PostgresConnectionInfo {
        if (StringUtils.isBlank(host)) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (StringUtils.isBlank(database)) {
            throw new IllegalArgumentException("database must not be blank");
        }
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Objects.requireNonNull(password, "password");
    }

    /**
     * Returns the PostgreSQL JDBC URL for this connection (host, port and database).
     *
     * @return the {@code jdbc:postgresql://host:port/database} URL
     */
    public String jdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(hostForUrl(host), port, database);
    }

    /**
     * Returns a {@link DataSource} backed by {@link java.sql.DriverManager} for this connection.
     *
     * <p>The PostgreSQL JDBC driver must be on the runtime classpath; no driver is bundled.
     *
     * @return a data source that opens connections to this PostgreSQL instance
     */
    public DataSource dataSource() {
        return new ConnectionInfoDataSource(this);
    }

    private static String hostForUrl(final String host) {
        final String formattedHost;
        if (host.contains(":") && !host.startsWith("[")) {
            formattedHost = "[" + host + "]";
        } else {
            formattedHost = host;
        }

        return formattedHost;
    }

    /**
     * Returns a redacted connection description.
     *
     * @return redacted connection description
     */
    @Override
    public String toString() {
        return "PostgresConnectionInfo[host=%s, port=%d, database=%s, username=%s, password=REDACTED]"
                .formatted(host, port, database, username);
    }
}
