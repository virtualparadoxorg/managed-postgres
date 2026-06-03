package eu.virtualparadox.managedpostgres.scenario.support;

import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class ScenarioJdbcDriver implements Driver, AutoCloseable {

    private final Path dataDirectory;
    private final String serverVersion;
    private final AtomicInteger connections;

    private ScenarioJdbcDriver(final Path dataDirectory, final String serverVersion) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion");
        connections = new AtomicInteger();
    }

    public static ScenarioJdbcDriver register(final PostgresInstanceMetadata metadata) throws SQLException {
        final ScenarioJdbcDriver driver =
                new ScenarioJdbcDriver(metadata.dataDirectory(), metadata.postgresqlVersion());
        DriverManager.registerDriver(driver);

        return driver;
    }

    @Override
    public Connection connect(final String jdbcUrl, final Properties info) {
        connections.incrementAndGet();

        return ScenarioJdbcProxies.connection(dataDirectory, serverVersion);
    }

    @Override
    public boolean acceptsURL(final String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(final String jdbcUrl, final Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("not supported");
    }

    @Override
    public void close() throws SQLException {
        DriverManager.deregisterDriver(this);
    }

    public int connections() {
        return connections.get();
    }
}
