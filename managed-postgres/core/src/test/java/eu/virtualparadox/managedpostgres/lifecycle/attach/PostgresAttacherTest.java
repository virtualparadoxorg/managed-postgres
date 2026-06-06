package eu.virtualparadox.managedpostgres.lifecycle.attach;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;
import eu.virtualparadox.managedpostgres.lifecycle.handle.AttachedPostgresHandle;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresProbeResult;
import eu.virtualparadox.managedpostgres.lifecycle.process.ProcessLookup;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.process.TestProcessHandles;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresAttacherTest {

    @TempDir
    private Path temporaryDirectory;

    PostgresAttacherTest() {}

    @Test
    void deadPidMetadataCannotBeAttached() {
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> true,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(9_999L));

        assertThat(result.attached()).isFalse();
        assertThat(result.summary()).contains("PID").contains("not alive");
        assertThat(result.handle()).isEmpty();
    }

    @Test
    void alivePidThatDoesNotLookLikePostgresCannotBeAttached() {
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.of(processHandle("java", true))),
                metadata -> true,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(123L));

        assertThat(result.attached()).isFalse();
        assertThat(result.summary()).contains("not a PostgreSQL process");
        assertThat(result.handle()).isEmpty();
    }

    @Test
    void closedPortCannotBeAttached() {
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.of(processHandle("postgres", true))),
                metadata -> false,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(123L));

        assertThat(result.attached()).isFalse();
        assertThat(result.summary()).contains("Port").contains("not accepting");
        assertThat(result.handle()).isEmpty();
    }

    @Test
    void closedPortWithKnownAlivePostgresProcessDoesNotAllowStartNew() {
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.of(processHandle("postgres", true))),
                metadata -> false,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(123L));

        assertThat(result.attached()).isFalse();
        assertThat(result.startNewAllowed()).isFalse();
    }

    @Test
    void missingPidWithOpenPortAndHealthyJdbcCanAttach() {
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> true,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(0L));

        assertThat(result.attached()).isTrue();
    }

    @Test
    void deadPidMetadataAllowsStartNew() {
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> true,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(123L));

        assertThat(result.startNewAllowed()).isTrue();
    }

    @Test
    void closedPortWithoutKnownProcessAllowsStartNew() {
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> false,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(0L));

        assertThat(result.attached()).isFalse();
        assertThat(result.startNewAllowed()).isTrue();
    }

    @Test
    void unhealthyJdbcIdentityCannotBeAttached() {
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.of(processHandle("postgres", true))),
                metadata -> true,
                metadata -> PostgresProbeResult.unhealthy(
                        "JDBC probe found a different PostgreSQL data directory", new DiagnosticReport(List.of())),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(123L));

        assertThat(result.attached()).isFalse();
        assertThat(result.summary()).contains("different PostgreSQL data directory");
        assertThat(result.handle()).isEmpty();
    }

    @Test
    void unhealthyJdbcDiagnosticsArePreservedOnAttachResult() {
        final DiagnosticReport diagnostics = new DiagnosticReport(
                List.of(new DiagnosticSection("jdbc-probe", Map.of("reason", "different data directory"))));
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.of(processHandle("postgres", true))),
                metadata -> true,
                metadata -> PostgresProbeResult.unhealthy(
                        "JDBC probe found a different PostgreSQL data directory", diagnostics),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(123L));

        assertThat(result.diagnosticReport().sections()).singleElement().satisfies(section -> {
            assertThat(section.name()).isEqualTo("jdbc-probe");
            assertThat(section.values()).containsEntry("reason", "different data directory");
        });
    }

    @Test
    void healthyCompatibleMetadataReturnsAttachedHandle() {
        final PostgresAttacher attacher = new PostgresAttacher(
                ProcessLookup.fixed(Optional.of(processHandle("postgres", true))),
                metadata -> true,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"),
                metadata -> attachedHandle());

        final AttachResult result = attacher.tryAttach(metadata(123L));

        assertThat(result.attached()).isTrue();
        assertThat(result.summary()).contains("attached");
        assertThat(result.handle()).isPresent();
        try (RunningPostgres handle = result.handle().orElseThrow()) {
            assertThat(handle.connectionInfo().port()).isEqualTo(15432);
        }
    }

    private static ProcessHandle processHandle(final String command, final boolean alive) {
        return TestProcessHandles.processHandle(command, alive);
    }

    private static RunningPostgres attachedHandle() {
        return new AttachedPostgresHandle(
                connectionInfo(),
                StopPolicy.KEEP_RUNNING,
                () -> {
                    // Test handle does not stop a process.
                },
                noopBackupOperation(),
                (backup, options) -> {
                    // Test handle does not restore backups.
                });
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo("127.0.0.1", 15432, "postgres", "postgres", Secret.of("test-password"));
    }

    private static PostgresBackupOperation noopBackupOperation() {
        return target -> {
            // Test handle does not create backups.
        };
    }

    private PostgresInstanceMetadata metadata(final long pid) {
        final Instant now = Instant.parse("2026-05-27T00:00:00Z");

        return new PostgresInstanceMetadata(
                1,
                "instance-id",
                "cluster-id",
                "app-db",
                temporaryDirectory.resolve("data"),
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                "16.4",
                16,
                "STARTED_BY_THIS_JVM",
                pid,
                "config-hash",
                now,
                now);
    }
}
