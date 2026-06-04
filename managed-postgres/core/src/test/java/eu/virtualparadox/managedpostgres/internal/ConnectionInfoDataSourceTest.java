package eu.virtualparadox.managedpostgres.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Tests the DriverManager-backed {@link ConnectionInfoDataSource} adapter without a live database.
 */
final class ConnectionInfoDataSourceTest {

    ConnectionInfoDataSourceTest() {}

    @Test
    void isWrapperForDataSourceIsTrue() throws SQLException {
        assertThat(dataSource().isWrapperFor(DataSource.class)).isTrue();
    }

    @Test
    void isWrapperForUnrelatedTypeIsFalse() throws SQLException {
        assertThat(dataSource().isWrapperFor(String.class)).isFalse();
    }

    @Test
    void unwrapToDataSourceReturnsInstance() throws SQLException {
        final DataSource dataSource = dataSource();

        assertThat(dataSource.unwrap(DataSource.class)).isSameAs(dataSource);
    }

    @Test
    void unwrapToUnrelatedTypeThrows() {
        assertThatThrownBy(() -> dataSource().unwrap(String.class)).isInstanceOf(SQLException.class);
    }

    @Test
    void getParentLoggerIsNotSupported() {
        final DataSource dataSource = dataSource();

        assertThatThrownBy(dataSource::getParentLogger).isInstanceOf(SQLFeatureNotSupportedException.class);
    }

    @Test
    void loginTimeoutRoundTripsViaDriverManager() throws SQLException {
        final int previous = DriverManager.getLoginTimeout();
        try {
            final DataSource dataSource = dataSource();
            dataSource.setLoginTimeout(7);

            assertThat(dataSource.getLoginTimeout()).isEqualTo(7);
        } finally {
            DriverManager.setLoginTimeout(previous);
        }
    }

    // The DriverManager log writer is process-global, borrowed state that must be restored, not
    // closed; PMD's CloseResource cannot model that ownership, hence the scoped suppression.
    @SuppressWarnings("PMD.CloseResource")
    @Test
    void logWriterRoundTripsViaDriverManager() throws SQLException {
        final PrintWriter previous = DriverManager.getLogWriter();
        try (PrintWriter writer = new PrintWriter(new StringWriter())) {
            final DataSource dataSource = dataSource();
            dataSource.setLogWriter(writer);

            assertThat(dataSource.getLogWriter()).isSameAs(writer);
        } finally {
            DriverManager.setLogWriter(previous);
        }
    }

    @Test
    void getConnectionWithoutReachableDatabaseThrows() {
        final DataSource dataSource = dataSource();

        assertThatThrownBy(dataSource::getConnection).isInstanceOf(SQLException.class);
    }

    @Test
    void getConnectionWithCredentialsWithoutReachableDatabaseThrows() {
        final DataSource dataSource = dataSource();

        assertThatThrownBy(() -> dataSource.getConnection("user", "password")).isInstanceOf(SQLException.class);
    }

    private static DataSource dataSource() {
        return new ConnectionInfoDataSource(
                new PostgresConnectionInfo("127.0.0.1", 1, "app", "postgres", Secret.of("secret")));
    }
}
