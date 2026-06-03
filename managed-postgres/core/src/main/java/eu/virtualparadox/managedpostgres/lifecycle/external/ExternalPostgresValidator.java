package eu.virtualparadox.managedpostgres.lifecycle.external;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.lifecycle.probe.DriverManagerJdbcProbeClient;
import eu.virtualparadox.managedpostgres.lifecycle.probe.JdbcProbeClient;
import eu.virtualparadox.managedpostgres.lifecycle.probe.JdbcProbeSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates externally managed PostgreSQL connections without lifecycle side effects.
 */
public final class ExternalPostgresValidator {

    private final JdbcProbeClient client;

    /**
     * Creates a validator backed by the default JDBC probe client.
     */
    public ExternalPostgresValidator() {
        this(new DriverManagerJdbcProbeClient());
    }

    /**
     * Creates a validator backed by the supplied JDBC probe client.
     *
     * @param client JDBC probe client
     */
    public ExternalPostgresValidator(final JdbcProbeClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * Validates an external PostgreSQL connection.
     *
     * @param connectionInfo connection details
     * @return external PostgreSQL validation result
     */
    public ExternalPostgresValidation validate(final PostgresConnectionInfo connectionInfo) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        final ExternalPostgresValidation validation;
        try {
            validation = new ExternalPostgresValidation(checkedConnectionInfo, client.probe(checkedConnectionInfo));
        } catch (final ManagedPostgresException exception) {
            throw validationFailure(checkedConnectionInfo, exception);
        }

        return validation;
    }

    /**
     * Runs external PostgreSQL diagnostics without mutating lifecycle state.
     *
     * @param connectionInfo connection details
     * @return doctor report
     */
    public DoctorReport doctor(final PostgresConnectionInfo connectionInfo) {
        final ExternalProbeAttempt attempt = probeWithoutThrowing(connectionInfo);
        final DoctorReport report;
        if (attempt.validation().isPresent()) {
            report = successReport(attempt.validation().orElseThrow());
        } else {
            report = failureReport(connectionInfo, attempt.failure().orElseThrow());
        }

        return report;
    }

    private ExternalProbeAttempt probeWithoutThrowing(final PostgresConnectionInfo connectionInfo) {
        ExternalProbeAttempt attempt;
        try {
            attempt = ExternalProbeAttempt.success(validate(connectionInfo));
        } catch (final ManagedPostgresException exception) {
            attempt = ExternalProbeAttempt.failure(exception);
        }

        return attempt;
    }

    private static DoctorReport successReport(final ExternalPostgresValidation validation) {
        return new DoctorReport(
                PostgresStatus.RUNNING, successSections(validation.connectionInfo(), validation.snapshot()));
    }

    private static DoctorReport failureReport(
            final PostgresConnectionInfo connectionInfo, final ManagedPostgresException exception) {
        return new DoctorReport(PostgresStatus.FAILED, failureSections(connectionInfo, exception));
    }

    private static PostgresAttachException validationFailure(
            final PostgresConnectionInfo connectionInfo, final ManagedPostgresException exception) {
        return new PostgresAttachException(
                "External PostgreSQL validation failed",
                exception,
                new DiagnosticReport(failureSections(connectionInfo, exception)));
    }

    private static List<DiagnosticSection> successSections(
            final PostgresConnectionInfo connectionInfo, final JdbcProbeSnapshot snapshot) {
        final List<DiagnosticSection> sections = baseSections(connectionInfo);
        sections.add(new DiagnosticSection(
                "external-postgres",
                Map.of(
                        "dataDirectory", snapshot.dataDirectory().toString(),
                        "serverVersion", snapshot.serverVersion())));

        return List.copyOf(sections);
    }

    private static List<DiagnosticSection> failureSections(
            final PostgresConnectionInfo connectionInfo, final ManagedPostgresException exception) {
        final List<DiagnosticSection> sections = baseSections(connectionInfo);
        sections.addAll(exception.diagnosticReport().sections());

        return List.copyOf(sections);
    }

    private static List<DiagnosticSection> baseSections(final PostgresConnectionInfo connectionInfo) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        final List<DiagnosticSection> sections = new ArrayList<>();
        sections.add(new DiagnosticSection(
                "external-mode",
                Map.of(
                        "behavior", "validation-only",
                        "startsPostgres", "false",
                        "stopsPostgres", "false")));
        sections.add(new DiagnosticSection(
                "external-connection",
                Map.of(
                        "host", checkedConnectionInfo.host(),
                        "port", Integer.toString(checkedConnectionInfo.port()),
                        "database", checkedConnectionInfo.database(),
                        "username", checkedConnectionInfo.username())));

        return sections;
    }

    private record ExternalProbeAttempt(
            Optional<ExternalPostgresValidation> validation, Optional<ManagedPostgresException> failure) {

        private ExternalProbeAttempt {
            Objects.requireNonNull(validation, "validation");
            Objects.requireNonNull(failure, "failure");
        }

        private static ExternalProbeAttempt success(final ExternalPostgresValidation validation) {
            return new ExternalProbeAttempt(Optional.of(validation), Optional.empty());
        }

        private static ExternalProbeAttempt failure(final ManagedPostgresException failure) {
            return new ExternalProbeAttempt(Optional.empty(), Optional.of(failure));
        }
    }
}
