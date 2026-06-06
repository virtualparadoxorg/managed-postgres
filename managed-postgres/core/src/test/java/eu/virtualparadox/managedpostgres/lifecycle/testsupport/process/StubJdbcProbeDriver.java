package eu.virtualparadox.managedpostgres.lifecycle.testsupport.process;

import eu.virtualparadox.managedpostgres.lifecycle.testsupport.JdbcProbeProxyFactory;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.JdbcProbeScenario;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public final class StubJdbcProbeDriver implements Driver, AutoCloseable {

    private final JdbcProbeScenario scenario;
    private String url = "";
    private Properties properties = new Properties();

    private StubJdbcProbeDriver(final JdbcProbeScenario scenario) {
        this.scenario = scenario;
    }

    public static StubJdbcProbeDriver register(final JdbcProbeScenario scenario) throws SQLException {
        final StubJdbcProbeDriver driver = new StubJdbcProbeDriver(scenario);
        DriverManager.registerDriver(driver);

        return driver;
    }

    @Override
    public Connection connect(final String jdbcUrl, final Properties info) {
        url = jdbcUrl;
        properties = new Properties();
        properties.putAll(info);

        return JdbcProbeProxyFactory.connection(scenario);
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

    public String url() {
        return url;
    }

    public Properties properties() {
        final Properties copy = new Properties();
        copy.putAll(properties);

        return copy;
    }
}
