package eu.virtualparadox.managedpostgres.lifecycle.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.JdbcProbeScenario;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.process.StubJdbcProbeDriver;

/**
 * Tests the DriverManager-backed JDBC probe client.
 */
public final class DriverManagerJdbcProbeClientTest {

    DriverManagerJdbcProbeClientTest() {
    }

    @Test
    void driverManagerClientReadsDataDirectoryAndServerVersion() throws SQLException {
        final JdbcProbeScenario scenario = JdbcProbeScenario.healthy("postgres-data", "16.4");

        try (StubJdbcProbeDriver driver = StubJdbcProbeDriver.register(scenario)) {
            final JdbcProbeSnapshot snapshot = new DriverManagerJdbcProbeClient().probe(connectionInfo());

            assertThat(snapshot.dataDirectory()).isEqualTo(Path.of("postgres-data"));
            assertThat(snapshot.serverVersion()).isEqualTo("16.4");
            assertThat(driver.properties()).containsEntry("user", "postgres");
            assertThat(driver.properties()).containsEntry("password", "secret-password");
        }
    }

    @Test
    void driverManagerClientFormatsIpv6JdbcUrl() throws SQLException {
        try (StubJdbcProbeDriver driver = StubJdbcProbeDriver.register(JdbcProbeScenario.healthy("postgres-data", "16.4"))) {
            new DriverManagerJdbcProbeClient().probe(new PostgresConnectionInfo(
                    "::1",
                    15432,
                    "postgres",
                    "postgres",
                    Secret.of("secret-password")));

            assertThat(driver.url()).isEqualTo("jdbc:postgresql://[::1]:15432/postgres");
        }
    }

    @Test
    void driverManagerClientThrowsRedactedDiagnosticsWhenQueryReturnsNoRows() throws SQLException {
        try (StubJdbcProbeDriver driver = StubJdbcProbeDriver.register(JdbcProbeScenario.emptyDataDirectory())) {
            assertThatThrownBy(() -> new DriverManagerJdbcProbeClient().probe(connectionInfo()))
                    .isInstanceOf(PostgresAttachException.class)
                    .satisfies(throwable -> assertThat(((PostgresAttachException) throwable)
                            .diagnosticReport()
                            .renderText())
                            .contains("127.0.0.1")
                            .contains("15432")
                            .contains("postgres")
                            .doesNotContain("secret-password"));
            assertThat(driver.url()).isEqualTo("jdbc:postgresql://127.0.0.1:15432/postgres");
        }
    }

    @Test
    void driverManagerClientThrowsRedactedDiagnosticsWhenDriverFails() {
        assertThatThrownBy(() -> new DriverManagerJdbcProbeClient().probe(new PostgresConnectionInfo(
                "127.0.0.1",
                1,
                "postgres",
                "postgres",
                Secret.of("secret-password"))))
                .isInstanceOf(PostgresAttachException.class)
                .satisfies(throwable -> assertThat(((PostgresAttachException) throwable)
                        .diagnosticReport()
                        .renderText()).doesNotContain("secret-password"));
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo(
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                Secret.of("secret-password"));
    }
}
