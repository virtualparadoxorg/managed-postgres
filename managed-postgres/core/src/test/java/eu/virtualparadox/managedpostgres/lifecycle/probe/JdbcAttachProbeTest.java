package eu.virtualparadox.managedpostgres.lifecycle.probe;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachJdbcProbeRequest;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.JdbcProbeScenario;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresMetadataFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.process.StubJdbcProbeDriver;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.StartConfigurationFixture;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class JdbcAttachProbeTest {

    @TempDir
    private Path temporaryDirectory;

    JdbcAttachProbeTest() {}

    @Test
    void jdbcAttachProbeConfirmsMatchingPostgresIdentity() throws SQLException {
        final AttachJdbcProbeRequest request = request(15432);

        try (StubJdbcProbeDriver driver = StubJdbcProbeDriver.register(
                JdbcProbeScenario.healthy(request.layout().dataDirectory().toString(), "16.4"))) {
            final PostgresProbeResult result = new JdbcAttachProbe().apply(request);

            assertThat(result.healthy()).isTrue();
            assertThat(result.summary()).contains("confirms PostgreSQL identity");
            assertThat(driver.url()).contains("15432");
        }
    }

    @Test
    void jdbcAttachProbeConvertsDriverFailuresToUnhealthyProbeResults() {
        final PostgresProbeResult result = new JdbcAttachProbe().apply(request(1));

        assertThat(result.healthy()).isFalse();
        assertThat(result.summary()).contains("JDBC");
        assertThat(result.diagnosticReport().renderText()).doesNotContain("test-password");
    }

    private AttachJdbcProbeRequest request(final int port) {
        final PostgresLayout layout = PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("storage"));

        return new AttachJdbcProbeRequest(
                PostgresMetadataFixture.metadata(layout.dataDirectory(), port),
                StartConfigurationFixture.configuration(
                        temporaryDirectory.resolve("storage"), temporaryDirectory.resolve("runtime")),
                layout);
    }
}
