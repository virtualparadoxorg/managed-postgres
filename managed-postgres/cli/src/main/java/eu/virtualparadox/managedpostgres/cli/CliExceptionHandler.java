package eu.virtualparadox.managedpostgres.cli;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.CommandRedactor;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.exception.PostgresCleanupException;
import eu.virtualparadox.managedpostgres.exception.PostgresDestroyException;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.exception.PostgresUpgradeException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Maps CLI failures to documented exit codes and redacted user-facing output.
 */
public final class CliExceptionHandler {

    private static final Set<String> RUNTIME_SECTIONS = Set.of(
            "runtime-resolution",
            "runtime-download",
            "runtime-validation");
    private static final Set<String> CLUSTER_SECTIONS = Set.of(
            "postgres-layout",
            "metadata",
            "postmaster.pid",
            "data-directory");

    private final PrintWriter errorOutput;

    /**
     * Creates a CLI exception handler.
     *
     * @param errorOutput error output writer
     */
    public CliExceptionHandler(final PrintWriter errorOutput) {
        this.errorOutput = Objects.requireNonNull(errorOutput, "errorOutput");
    }

    /**
     * Renders a throwable and returns the documented CLI exit code.
     *
     * @param throwable failure to render
     * @return documented CLI exit code
     */
    public int handle(final Throwable throwable) {
        final Throwable checkedThrowable = Objects.requireNonNull(throwable, "throwable");
        final CliExitCode exitCode;

        if (checkedThrowable instanceof ManagedPostgresException exception) {
            exitCode = handleManagedPostgresException(exception);
        } else if (checkedThrowable instanceof IllegalArgumentException) {
            exitCode = handleConfigurationException(checkedThrowable);
        } else {
            exitCode = handleUnexpectedException(checkedThrowable);
        }

        return exitCode.code();
    }

    private CliExitCode handleManagedPostgresException(final ManagedPostgresException exception) {
        final CliExitCode exitCode = managedExitCode(exception);
        final DiagnosticReport diagnosticReport = exception.diagnosticReport();

        errorOutput.println("Managed Postgres error: " + redactedMessage(exception));
        errorOutput.print(diagnosticReport.renderText());

        return exitCode;
    }

    private CliExitCode handleConfigurationException(final Throwable throwable) {
        errorOutput.println("Configuration error: " + redactedMessage(throwable));

        return CliExitCode.CONFIGURATION_ERROR;
    }

    private CliExitCode handleUnexpectedException(final Throwable throwable) {
        errorOutput.println("Unexpected error: " + redactedMessage(throwable));

        return CliExitCode.GENERIC_ERROR;
    }

    private static CliExitCode managedExitCode(final ManagedPostgresException exception) {
        final CliExitCode exitCode;
        if (isBackupRestoreException(exception)) {
            exitCode = CliExitCode.BACKUP_RESTORE_ERROR;
        } else if (exception instanceof PostgresUpgradeException) {
            exitCode = CliExitCode.VERSION_MISMATCH;
        } else if (exception instanceof PostgresStartupException) {
            exitCode = startupExitCode(exception);
        } else if (isClusterLifecycleException(exception)) {
            exitCode = CliExitCode.CLUSTER_ERROR;
        } else {
            exitCode = diagnosticExitCode(exception);
        }

        return exitCode;
    }

    private static boolean isBackupRestoreException(final ManagedPostgresException exception) {
        final ManagedPostgresException checkedException = Objects.requireNonNull(exception, "exception");

        return checkedException instanceof PostgresBackupException
                || checkedException instanceof PostgresRestoreException;
    }

    private static boolean isClusterLifecycleException(final ManagedPostgresException exception) {
        final ManagedPostgresException checkedException = Objects.requireNonNull(exception, "exception");

        return checkedException instanceof PostgresShutdownException
                || checkedException instanceof PostgresAttachException
                || checkedException instanceof PostgresCleanupException
                || checkedException instanceof PostgresDestroyException;
    }

    private static CliExitCode startupExitCode(final ManagedPostgresException exception) {
        final CliExitCode exitCode;
        if (hasSection(exception, "startup-timeout")) {
            exitCode = CliExitCode.READINESS_TIMEOUT;
        } else {
            exitCode = CliExitCode.STARTUP_ERROR;
        }

        return exitCode;
    }

    private static CliExitCode diagnosticExitCode(final ManagedPostgresException exception) {
        final CliExitCode exitCode;
        if (hasSection(exception, "postgres-lock")) {
            exitCode = CliExitCode.LOCK_UNAVAILABLE;
        } else if (hasAnySection(exception, RUNTIME_SECTIONS)) {
            exitCode = CliExitCode.RUNTIME_ERROR;
        } else if (hasAnySection(exception, CLUSTER_SECTIONS)) {
            exitCode = CliExitCode.CLUSTER_ERROR;
        } else {
            exitCode = CliExitCode.GENERIC_ERROR;
        }

        return exitCode;
    }

    private static boolean hasAnySection(final ManagedPostgresException exception, final Set<String> sectionNames) {
        final Set<String> checkedSectionNames = Objects.requireNonNull(sectionNames, "sectionNames");

        return sectionNames(exception).stream().anyMatch(checkedSectionNames::contains);
    }

    private static boolean hasSection(final ManagedPostgresException exception, final String sectionName) {
        final String checkedSectionName = Objects.requireNonNull(sectionName, "sectionName");

        return sectionNames(exception).contains(checkedSectionName);
    }

    private static List<String> sectionNames(final ManagedPostgresException exception) {
        final DiagnosticReport diagnosticReport = exception.diagnosticReport();
        final List<String> sectionNames = diagnosticReport.sections().stream()
                .map(DiagnosticSection::name)
                .toList();

        return sectionNames;
    }

    private static String redactedMessage(final Throwable throwable) {
        final String message = Optional.ofNullable(throwable.getMessage())
                .orElse(throwable.getClass().getSimpleName());

        return CommandRedactor.redact(message);
    }
}
