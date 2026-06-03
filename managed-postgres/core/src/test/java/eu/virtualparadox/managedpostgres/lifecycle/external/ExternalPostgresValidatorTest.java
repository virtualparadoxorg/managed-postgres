package eu.virtualparadox.managedpostgres.lifecycle.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.lifecycle.probe.JdbcProbeSnapshot;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ExternalPostgresValidatorTest {

    private static final PostgresConnectionInfo CONNECTION_INFO =
            new PostgresConnectionInfo("127.0.0.1", 15432, "app", "app", Secret.of("external-secret"));

    ExternalPostgresValidatorTest() {}

    @Test
    void validateConfirmsExternalConnectionUsingJdbcProbe() {
        final ExternalPostgresValidator validator = new ExternalPostgresValidator(
                connectionInfo -> new JdbcProbeSnapshot(Path.of("postgres-data"), "16.5"));

        final ExternalPostgresValidation validation = validator.validate(CONNECTION_INFO);

        assertThat(validation.connectionInfo()).isEqualTo(CONNECTION_INFO);
        assertThat(validation.snapshot().serverVersion()).isEqualTo("16.5");
    }

    @Test
    void validateWrapsJdbcFailureWithExternalDiagnostics() {
        final ExternalPostgresValidator validator = new ExternalPostgresValidator(connectionInfo -> {
            throw new PostgresAttachException(
                    "JDBC attach probe failed",
                    new DiagnosticReport(List.of(
                            new DiagnosticSection("jdbc-attach-probe", Map.of("reason", "connection refused")))));
        });

        assertThatExceptionOfType(PostgresAttachException.class)
                .isThrownBy(() -> validator.validate(CONNECTION_INFO))
                .withMessage("External PostgreSQL validation failed")
                .satisfies(exception -> assertThat(exception.diagnosticReport().renderText())
                        .contains("external-connection")
                        .contains("connection refused")
                        .doesNotContain("external-secret"));
    }

    @Test
    void doctorReportsRunningWithoutExposingSecretsWhenProbeSucceeds() {
        final ExternalPostgresValidator validator = new ExternalPostgresValidator(
                connectionInfo -> new JdbcProbeSnapshot(Path.of("postgres-data"), "16.5"));

        final DoctorReport report = validator.doctor(CONNECTION_INFO);

        assertThat(report.status()).isEqualTo(PostgresStatus.RUNNING);
        assertThat(report.renderText())
                .contains("validation-only")
                .contains("serverVersion=16.5")
                .doesNotContain("external-secret");
    }

    @Test
    void doctorReportsFailedWithoutThrowingWhenProbeFails() {
        final ExternalPostgresValidator validator = new ExternalPostgresValidator(connectionInfo -> {
            throw new PostgresAttachException(
                    "JDBC attach probe failed",
                    new DiagnosticReport(List.of(
                            new DiagnosticSection("jdbc-attach-probe", Map.of("reason", "authentication failed")))));
        });

        final DoctorReport report = validator.doctor(CONNECTION_INFO);

        assertThat(report.status()).isEqualTo(PostgresStatus.FAILED);
        assertThat(report.renderText()).contains("authentication failed").doesNotContain("external-secret");
    }
}
