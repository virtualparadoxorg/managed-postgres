package eu.virtualparadox.managedpostgres.lifecycle.probe;

import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * JDBC probe client backed by {@link DriverManager}.
 */
public final class DriverManagerJdbcProbeClient implements JdbcProbeClient {

    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final String SHOW_DATA_DIRECTORY = "SHOW data_directory";
    private static final String SHOW_SERVER_VERSION = "SHOW server_version";

    /**
     * Creates a DriverManagerJdbcProbeClient instance.
     */
    public DriverManagerJdbcProbeClient() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcProbeSnapshot probe(final PostgresConnectionInfo connectionInfo) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(checkedConnectionInfo),
                connectionProperties(checkedConnectionInfo))) {
            return new JdbcProbeSnapshot(
                    Path.of(dataDirectory(connection)),
                    serverVersion(connection));
        } catch (final SQLException exception) {
            throw jdbcProbeFailure(checkedConnectionInfo, exception);
        }
    }

    private static String dataDirectory(final Connection connection) throws SQLException {
        final String value;
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            value = singleValue(statement.executeQuery(SHOW_DATA_DIRECTORY), SHOW_DATA_DIRECTORY);
        }

        return value;
    }

    private static String serverVersion(final Connection connection) throws SQLException {
        final String value;
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            value = singleValue(statement.executeQuery(SHOW_SERVER_VERSION), SHOW_SERVER_VERSION);
        }

        return value;
    }

    private static String singleValue(final ResultSet resultSet, final String sql) throws SQLException {
        final String value;
        try (ResultSet checkedResultSet = resultSet) {
            if (!checkedResultSet.next()) {
                throw new SQLException("JDBC probe query returned no rows: " + sql);
            }
            value = checkedResultSet.getString(1);
        }

        return value;
    }

    private static Properties connectionProperties(final PostgresConnectionInfo connectionInfo) {
        final Properties properties = new Properties();
        properties.setProperty("user", connectionInfo.username());
        properties.setProperty("password", connectionInfo.password().reveal());
        properties.setProperty("connectTimeout", Integer.toString(QUERY_TIMEOUT_SECONDS));
        properties.setProperty("socketTimeout", Integer.toString(QUERY_TIMEOUT_SECONDS));

        return properties;
    }

    private static String jdbcUrl(final PostgresConnectionInfo connectionInfo) {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                hostForUrl(connectionInfo.host()),
                connectionInfo.port(),
                connectionInfo.database());
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

    private static PostgresAttachException jdbcProbeFailure(
            final PostgresConnectionInfo connectionInfo,
            final SQLException exception) {
        return new PostgresAttachException(
                "JDBC attach probe failed",
                exception,
                new DiagnosticReport(List.of(new DiagnosticSection(
                        "jdbc-attach-probe",
                        Map.of(
                                "host", connectionInfo.host(),
                                "port", Integer.toString(connectionInfo.port()),
                                "database", connectionInfo.database(),
                                "username", connectionInfo.username(),
                                "reason", Objects.toString(exception.getMessage(), "unknown JDBC failure"))))));
    }
}
