package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.CountingJdbcProbe;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata.DoctorMetadataSnapshot;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.DoctorProbeInspectorFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.DoctorProbeRequestFixture;

public final class DoctorProbeInspectorTest {

    private static final String TEST_PASSWORD = "test-password";

    @TempDir
    private Path temporaryDirectory;

    DoctorProbeInspectorTest() {
    }

    @Test
    void absentMetadataSkipsProbesAndReportsStopped() {
        final DoctorProbeSnapshot snapshot = inspector(true, CountingJdbcProbe.healthy())
                .inspect(fixture().request(DoctorMetadataSnapshot.absent()));

        assertThat(snapshot.status()).isEqualTo(PostgresStatus.STOPPED);
        assertThat(snapshot.section().values())
                .containsEntry("status", "skipped")
                .containsEntry("compatibility", "skipped")
                .containsEntry("process", "skipped")
                .containsEntry("port", "skipped")
                .containsEntry("jdbc", "skipped");
    }

    @Test
    void metadataWithMissingPidOpenPortAndHealthyJdbcReportsRunning() {
        final DoctorProbeSnapshot snapshot = inspector(true, CountingJdbcProbe.healthy())
                .inspect(fixture().request(DoctorMetadataSnapshot.present(fixture().metadata(0L))));

        assertThat(snapshot.status()).isEqualTo(PostgresStatus.RUNNING);
        assertThat(snapshot.section().values())
                .containsEntry("status", "healthy")
                .containsEntry("compatibility", "compatible")
                .containsEntry("process", "skipped")
                .containsEntry("port", "open")
                .containsEntry("jdbc", "healthy");
    }

    @Test
    void metadataWithClosedPortReportsFailedWithoutJdbcProbe() {
        final CountingJdbcProbe jdbcProbe = CountingJdbcProbe.healthy();

        final DoctorProbeSnapshot snapshot = inspector(false, jdbcProbe)
                .inspect(fixture().request(DoctorMetadataSnapshot.present(fixture().metadata(0L))));

        assertThat(snapshot.status()).isEqualTo(PostgresStatus.FAILED);
        assertThat(snapshot.section().values())
                .containsEntry("status", "unhealthy")
                .containsEntry("port", "closed")
                .containsEntry("jdbc", "skipped");
        assertThat(jdbcProbe.calls()).isZero();
    }

    @Test
    void metadataWithoutLayoutReportsFailedWithoutJdbcProbe() {
        final CountingJdbcProbe jdbcProbe = CountingJdbcProbe.healthy();

        final DoctorProbeSnapshot snapshot = inspector(true, jdbcProbe)
                .inspect(fixture().withoutLayout(DoctorMetadataSnapshot.present(fixture().metadata(0L))));

        assertThat(snapshot.status()).isEqualTo(PostgresStatus.FAILED);
        assertThat(snapshot.section().values())
                .containsEntry("status", "unhealthy")
                .containsEntry("compatibility", "skipped")
                .containsEntry("port", "skipped")
                .containsEntry("jdbc", "skipped");
        assertThat(snapshot.section().values().get("summary")).contains("layout");
        assertThat(jdbcProbe.calls()).isZero();
    }

    @Test
    void incompatibleMetadataReportsFailedWithoutJdbcProbe() {
        final CountingJdbcProbe jdbcProbe = CountingJdbcProbe.healthy();

        final DoctorProbeSnapshot snapshot = inspector(true, jdbcProbe)
                .inspect(fixture().request(DoctorMetadataSnapshot.present(fixture().metadata(0L, "wrong-hash"))));

        assertThat(snapshot.status()).isEqualTo(PostgresStatus.FAILED);
        assertThat(snapshot.section().values())
                .containsEntry("status", "unhealthy")
                .containsEntry("compatibility", "incompatible")
                .containsEntry("port", "skipped")
                .containsEntry("jdbc", "skipped");
        assertThat(snapshot.section().values().get("summary")).contains("configHash");
        assertThat(jdbcProbe.calls()).isZero();
    }

    @Test
    void incompatibleMetadataIncludesStructuredCompatibilityDiagnostics() {
        final CountingJdbcProbe jdbcProbe = CountingJdbcProbe.healthy();

        final DoctorProbeSnapshot snapshot = inspector(true, jdbcProbe)
                .inspect(fixture().request(DoctorMetadataSnapshot.present(fixture().metadata(0L, "wrong-hash"))));

        assertThat(snapshot.additionalSections())
                .singleElement()
                .satisfies(section -> {
                    assertThat(section.name()).isEqualTo("postgres-attach-compatibility");
                    assertThat(section.values())
                            .containsEntry("status", "incompatible")
                            .containsEntry("mismatchCount", "1")
                            .containsEntry("mismatch.1.field", "configHash");
                });
        assertThat(jdbcProbe.calls()).isZero();
    }

    @Test
    void metadataWithUnhealthyJdbcReportsFailed() {
        final DoctorProbeSnapshot snapshot = inspector(true, CountingJdbcProbe.unhealthy())
                .inspect(fixture().request(DoctorMetadataSnapshot.present(fixture().metadata(0L))));

        assertThat(snapshot.status()).isEqualTo(PostgresStatus.FAILED);
        assertThat(snapshot.section().values())
                .containsEntry("status", "unhealthy")
                .containsEntry("port", "open")
                .containsEntry("jdbc", "unhealthy");
        assertThat(snapshot.section().values().get("summary")).contains("different PostgreSQL data directory");
    }

    @Test
    void probeDiagnosticsDoNotRenderPasswords() {
        final DoctorProbeSnapshot snapshot = inspector(true, CountingJdbcProbe.unhealthy())
                .inspect(fixture().request(DoctorMetadataSnapshot.present(fixture().metadata(0L))));

        assertThat(snapshot.section().values().toString()).doesNotContain(TEST_PASSWORD);
    }

    private DoctorProbeInspector inspector(final boolean portOpen, final CountingJdbcProbe jdbcProbe) {
        return DoctorProbeInspectorFixture.inspector(portOpen, jdbcProbe);
    }

    private DoctorProbeRequestFixture fixture() {
        return new DoctorProbeRequestFixture(temporaryDirectory);
    }
}
