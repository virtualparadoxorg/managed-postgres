package eu.virtualparadox.managedpostgres.lifecycle.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class PostgresStatusServiceTest {

    PostgresStatusServiceTest() {
    }

    @Test
    void statusServiceReturnsStoppedWhenMetadataIsAbsent() {
        final PostgresStatusService service = new PostgresStatusService(Optional::empty);

        assertThat(service.status()).isEqualTo(PostgresStatus.STOPPED);
        assertThat(service.diagnosticReport()).isEmpty();
    }

    @Test
    void statusServiceReportsStaleMetadataWithoutThrowingRawIoException() {
        final PostgresStatusService service = new PostgresStatusService(() -> {
            throw new IOException("metadata is corrupted");
        });

        assertThatCode(service::status).doesNotThrowAnyException();
        assertThat(service.status()).isEqualTo(PostgresStatus.FAILED);
        final Optional<DiagnosticReport> diagnosticReport = service.diagnosticReport();
        assertThat(diagnosticReport).isPresent();
        final DiagnosticReport report = diagnosticReport.orElseThrow();
        assertThat(report.renderText()).contains("metadata").contains("corrupted");
    }

    @Test
    void statusServiceReportsStaleMetadataWithoutThrowingWhenIoExceptionHasNoMessage() {
        final PostgresStatusService service = new PostgresStatusService(() -> {
            throw new IOException();
        });

        assertThatCode(service::status).doesNotThrowAnyException();
        assertThat(service.status()).isEqualTo(PostgresStatus.FAILED);
        final Optional<DiagnosticReport> diagnosticReport = service.diagnosticReport();
        assertThat(diagnosticReport).isPresent();
        final DiagnosticReport report = diagnosticReport.orElseThrow();
        assertThat(report.renderText()).contains("metadata").contains(IOException.class.getName());
    }

    @Test
    void statusServiceDoesNotTreatMetadataAloneAsRunning() {
        final PostgresStatusService service = new PostgresStatusService(() -> Optional.of(metadata()));

        assertThat(service.status()).isEqualTo(PostgresStatus.FAILED);
        final Optional<DiagnosticReport> diagnosticReport = service.diagnosticReport();
        assertThat(diagnosticReport).isPresent();
        final DiagnosticReport report = diagnosticReport.orElseThrow();
        assertThat(report.renderText()).contains("metadata").contains("unvalidated");
    }

    @Test
    void statusServiceUsesProbeWhenMetadataIsPresent() {
        final PostgresStatusService service = new PostgresStatusService(
                () -> Optional.of(metadata()),
                ignored -> PostgresStatusSnapshot.running());

        assertThat(service.status()).isEqualTo(PostgresStatus.RUNNING);
        assertThat(service.diagnosticReport()).isEmpty();
    }

    private static PostgresInstanceMetadata metadata() {
        final Instant now = Instant.parse("2026-05-27T00:00:00Z");
        return new PostgresInstanceMetadata(
                1,
                "instance",
                "cluster",
                "app-db",
                Path.of("pgdata"),
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                "16.4",
                16,
                "started",
                1234,
                "hash",
                now,
                now);
    }
}
