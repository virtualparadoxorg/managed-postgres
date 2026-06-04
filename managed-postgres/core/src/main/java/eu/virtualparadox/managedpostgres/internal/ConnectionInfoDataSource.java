package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Internal {@link DataSource} adapter that opens connections through {@link DriverManager}.
 *
 * <p>This is an implementation detail of {@link PostgresConnectionInfo#dataSource()} and is not
 * intended for direct use. No JDBC driver is bundled; the PostgreSQL driver must be on the runtime
 * classpath, exactly as for the readiness probe.
 */
public final class ConnectionInfoDataSource implements DataSource {

    private final PostgresConnectionInfo connectionInfo;

    /**
     * Creates an adapter for the supplied connection.
     *
     * @param connectionInfo connection details to open connections for
     */
    public ConnectionInfoDataSource(final PostgresConnectionInfo connectionInfo) {
        this.connectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                connectionInfo.jdbcUrl(),
                connectionInfo.username(),
                connectionInfo.password().reveal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection(final String username, final String password) throws SQLException {
        return DriverManager.getConnection(connectionInfo.jdbcUrl(), username, password);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PrintWriter getLogWriter() {
        return DriverManager.getLogWriter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogWriter(final PrintWriter out) {
        DriverManager.setLogWriter(out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLoginTimeout(final int seconds) {
        DriverManager.setLoginTimeout(seconds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLoginTimeout() {
        return DriverManager.getLoginTimeout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger is not supported");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWrapperFor(final Class<?> iface) {
        return iface.isInstance(this);
    }
}
