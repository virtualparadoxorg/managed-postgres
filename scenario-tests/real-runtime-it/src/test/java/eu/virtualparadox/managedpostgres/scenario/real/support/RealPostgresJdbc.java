package eu.virtualparadox.managedpostgres.scenario.real.support;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * JDBC helper methods for opt-in real PostgreSQL integration tests.
 */
public final class RealPostgresJdbc {

    private static final String SELECT_ONE = "SELECT 1";
    private static final String DATA_DIRECTORY = "SHOW data_directory";

    private RealPostgresJdbc() {}

    /**
     * Opens a JDBC connection from managed PostgreSQL connection details.
     *
     * @param connectionInfo PostgreSQL connection details
     * @return opened JDBC connection
     * @throws SQLException when JDBC connection creation fails
     */
    public static Connection connection(final PostgresConnectionInfo connectionInfo) throws SQLException {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        final String url = "jdbc:postgresql://%s:%d/%s"
                .formatted(
                        checkedConnectionInfo.host(), checkedConnectionInfo.port(), checkedConnectionInfo.database());

        return DriverManager.getConnection(
                url,
                checkedConnectionInfo.username(),
                checkedConnectionInfo.password().reveal());
    }

    /**
     * Opens a JDBC connection using explicit credentials against the managed PostgreSQL database.
     *
     * @param connectionInfo PostgreSQL connection details
     * @param username explicit database username
     * @param password explicit database password
     * @return opened JDBC connection
     * @throws SQLException when JDBC connection creation fails
     */
    public static Connection connection(
            final PostgresConnectionInfo connectionInfo, final String username, final String password)
            throws SQLException {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        final String checkedUsername = Objects.requireNonNull(username, "username");
        final String checkedPassword = Objects.requireNonNull(password, "password");
        final String url = "jdbc:postgresql://%s:%d/%s"
                .formatted(
                        checkedConnectionInfo.host(), checkedConnectionInfo.port(), checkedConnectionInfo.database());

        return DriverManager.getConnection(url, checkedUsername, checkedPassword);
    }

    /**
     * Runs {@code SELECT 1}.
     *
     * @param connectionInfo PostgreSQL connection details
     * @return selected value
     * @throws SQLException when query execution fails
     */
    public static int selectOne(final PostgresConnectionInfo connectionInfo) throws SQLException {
        final int value;
        try (Connection connection = connection(connectionInfo);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(SELECT_ONE)) {
            requireRow(resultSet);
            value = resultSet.getInt(1);
        }

        return value;
    }

    /**
     * Reads the PostgreSQL data directory setting.
     *
     * @param connectionInfo PostgreSQL connection details
     * @return configured data directory
     * @throws SQLException when query execution fails
     */
    public static String dataDirectory(final PostgresConnectionInfo connectionInfo) throws SQLException {
        final String value;
        try (Connection connection = connection(connectionInfo);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(DATA_DIRECTORY)) {
            requireRow(resultSet);
            value = resultSet.getString(1);
        }

        return value;
    }

    /**
     * Reads the PostgreSQL data directory setting using explicit credentials.
     *
     * @param connectionInfo PostgreSQL connection details
     * @param username explicit database username
     * @param password explicit database password
     * @return configured data directory
     * @throws SQLException when query execution fails
     */
    public static String dataDirectory(
            final PostgresConnectionInfo connectionInfo, final String username, final String password)
            throws SQLException {
        final String value;
        try (Connection connection = connection(connectionInfo, username, password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(DATA_DIRECTORY)) {
            requireRow(resultSet);
            value = resultSet.getString(1);
        }

        return value;
    }

    private static void requireRow(final ResultSet resultSet) throws SQLException {
        if (!resultSet.next()) {
            throw new SQLException("Expected one row from PostgreSQL query");
        }
    }
}
