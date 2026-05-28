package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.exception.PostgresDestroyException;
import eu.virtualparadox.managedpostgres.lifecycle.external.ExternalPostgresValidator;
import eu.virtualparadox.managedpostgres.lifecycle.handle.ExternalPostgresHandle;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validation-only managed PostgreSQL facade for externally owned PostgreSQL connections.
 */
public final class ExternalManagedPostgres implements ManagedPostgres {

    private final PostgresConnectionInfo connectionInfo;
    private final ExternalPostgresValidator validator;

    /**
     * Creates an external managed PostgreSQL facade with default validation.
     *
     * @param connectionInfo connection details
     */
    public ExternalManagedPostgres(final PostgresConnectionInfo connectionInfo) {
        this(connectionInfo, new ExternalPostgresValidator());
    }

    /**
     * Creates an external managed PostgreSQL facade.
     *
     * @param connectionInfo connection details
     * @param validator external connection validator
     */
    public ExternalManagedPostgres(
            final PostgresConnectionInfo connectionInfo,
            final ExternalPostgresValidator validator) {
        this.connectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * Validates and returns an external PostgreSQL handle.
     *
     * @return external PostgreSQL handle
     */
    @Override
    public RunningPostgres start() {
        validator.validate(connectionInfo);

        return new ExternalPostgresHandle(connectionInfo);
    }

    /**
     * Returns external validation status.
     *
     * @return external validation status
     */
    @Override
    public PostgresStatus status() {
        return doctor().status();
    }

    /**
     * Runs validation-only external diagnostics.
     *
     * @return doctor report
     */
    @Override
    public DoctorReport doctor() {
        return validator.doctor(connectionInfo);
    }

    /**
     * Leaves externally owned PostgreSQL running.
     */
    @Override
    public void stop() {
        // External mode is validation-only; stop intentionally detaches only.
    }

    /**
     * External mode has no managed filesystem artifacts to clean.
     */
    @Override
    public void cleanup() {
        // External mode is validation-only; cleanup intentionally does nothing.
    }

    /**
     * External mode never owns cluster storage.
     */
    @Override
    public void destroyCluster() {
        throw new PostgresDestroyException(
                "External PostgreSQL cluster destruction is unsupported",
                new DiagnosticReport(List.of(new DiagnosticSection(
                        "postgres-destroy",
                        Map.of("mode", "external", "reason", "External mode does not own cluster storage")))));
    }

    /**
     * Leaves externally owned PostgreSQL running.
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Returns a redacted external PostgreSQL description.
     *
     * @return redacted description
     */
    @Override
    public String toString() {
        return "ExternalManagedPostgres[connectionInfo=%s]".formatted(connectionInfo);
    }
}
