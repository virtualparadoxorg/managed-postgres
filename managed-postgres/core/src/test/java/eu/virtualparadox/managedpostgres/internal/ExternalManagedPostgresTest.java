package eu.virtualparadox.managedpostgres.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.exception.PostgresDestroyException;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.lifecycle.external.ExternalPostgresValidator;
import eu.virtualparadox.managedpostgres.lifecycle.probe.JdbcProbeSnapshot;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ExternalManagedPostgresTest {

    private static final PostgresConnectionInfo CONNECTION_INFO = new PostgresConnectionInfo(
            "127.0.0.1",
            15432,
            "app",
            "app",
            Secret.of("external-secret"));

    ExternalManagedPostgresTest() {
    }

    @Test
    void startValidatesAndReturnsExternalHandle() {
        final ExternalManagedPostgres postgres = new ExternalManagedPostgres(
                CONNECTION_INFO,
                successfulValidator());

        try (RunningPostgres handle = postgres.start()) {
            assertThat(handle.connectionInfo()).isEqualTo(CONNECTION_INFO);
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }
    }

    @Test
    void statusUsesExternalDoctorValidation() {
        try (ExternalManagedPostgres postgres = new ExternalManagedPostgres(
                CONNECTION_INFO,
                successfulValidator())) {

            assertThat(postgres.status()).isEqualTo(PostgresStatus.RUNNING);
        }
    }

    @Test
    void stopDoesNotStopExternalPostgres() {
        try (ExternalManagedPostgres postgres = new ExternalManagedPostgres(
                CONNECTION_INFO,
                successfulValidator())) {

            postgres.stop();

            assertThat(postgres.status()).isEqualTo(PostgresStatus.RUNNING);
        }
    }

    @Test
    void startFailsWhenExternalValidationFails() {
        try (ExternalManagedPostgres postgres = new ExternalManagedPostgres(
                CONNECTION_INFO,
                failingValidator())) {

            assertThatExceptionOfType(PostgresAttachException.class)
                    .isThrownBy(postgres::start)
                    .withMessage("External PostgreSQL validation failed");
        }
    }

    @Test
    void externalHandleDetachDoesNotExposeStopSideEffects() {
        final ExternalManagedPostgres postgres = new ExternalManagedPostgres(
                CONNECTION_INFO,
                successfulValidator());

        try (RunningPostgres handle = postgres.start()) {
            handle.stop();

            assertThat(handle.status()).isEqualTo(PostgresStatus.STOPPED);
        }
    }

    @Test
    void externalHandleBackupAndRestoreAreExplicitlyUnsupported() {
        final ExternalManagedPostgres postgres = new ExternalManagedPostgres(
                CONNECTION_INFO,
                successfulValidator());

        try (RunningPostgres handle = postgres.start()) {
            assertThatExceptionOfType(PostgresBackupException.class)
                    .isThrownBy(() -> handle.backupTo(Path.of("backup.sql")));
            assertThatExceptionOfType(PostgresRestoreException.class)
                    .isThrownBy(() -> handle.restoreFrom(
                            Path.of("backup.sql"),
                            RestoreOptions.builder().dropCurrentDatabase(true).build()));
        }
    }

    @Test
    void toStringRedactsExternalConnectionPassword() {
        try (ExternalManagedPostgres postgres = new ExternalManagedPostgres(
                CONNECTION_INFO,
                successfulValidator())) {

            assertThat(postgres.toString())
                    .contains("ExternalManagedPostgres")
                    .contains("REDACTED")
                    .doesNotContain("external-secret");
        }
    }

    @Test
    void cleanupIsANoopForExternalMode() {
        try (ExternalManagedPostgres postgres = new ExternalManagedPostgres(
                CONNECTION_INFO,
                successfulValidator())) {

            postgres.cleanup();

            assertThat(postgres.status()).isEqualTo(PostgresStatus.RUNNING);
        }
    }

    @Test
    void destroyClusterFailsForExternalMode() {
        try (ExternalManagedPostgres postgres = new ExternalManagedPostgres(
                CONNECTION_INFO,
                successfulValidator())) {

            assertThatExceptionOfType(PostgresDestroyException.class)
                    .isThrownBy(postgres::destroyCluster)
                    .withMessage("External PostgreSQL cluster destruction is unsupported");
        }
    }

    private static ExternalPostgresValidator successfulValidator() {
        return new ExternalPostgresValidator(
                connectionInfo -> new JdbcProbeSnapshot(Path.of("postgres-data"), "16.5"));
    }

    private static ExternalPostgresValidator failingValidator() {
        return new ExternalPostgresValidator(connectionInfo -> {
            throw new PostgresAttachException(
                    "JDBC attach probe failed",
                    new DiagnosticReport(List.of(new DiagnosticSection(
                            "jdbc-attach-probe",
                            Map.of("reason", "connection refused")))));
        });
    }
}
