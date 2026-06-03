package eu.virtualparadox.managedpostgres.lifecycle.attach;

import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Internal attach attempt result.
 *
 * @param attached whether attach succeeded
 * @param summary human-readable attach summary
 * @param handle attached handle when attach succeeded
 * @param startNewAllowed whether it is safe to start a new PostgreSQL process after this attach failure
 * @param diagnosticReport structured attach diagnostics
 */
public record AttachResult(
        boolean attached,
        String summary,
        Optional<RunningPostgres> handle,
        boolean startNewAllowed,
        DiagnosticReport diagnosticReport) {

    /**
     * Creates an attach result.
     *
     * @param attached whether attach succeeded
     * @param summary human-readable attach summary
     * @param handle attached handle when attach succeeded
     * @param startNewAllowed whether it is safe to start a new PostgreSQL process after this attach failure
     */
    public AttachResult(
            final boolean attached,
            final String summary,
            final Optional<RunningPostgres> handle,
            final boolean startNewAllowed) {
        this(attached, summary, handle, startNewAllowed, emptyReport());
    }

    /**
     * Creates an attach result.
     *
     * @param attached whether attach succeeded
     * @param summary human-readable attach summary
     * @param handle attached handle when attach succeeded
     * @param startNewAllowed whether it is safe to start a new PostgreSQL process after this attach failure
     * @param diagnosticReport structured attach diagnostics
     */
    public AttachResult {
        if (StringUtils.isBlank(summary)) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(diagnosticReport, "diagnosticReport");
        if (attached && handle.isEmpty()) {
            throw new IllegalArgumentException("successful attach result must contain a handle");
        }
        if (!attached && handle.isPresent()) {
            throw new IllegalArgumentException("failed attach result must not contain a handle");
        }
        if (attached && startNewAllowed) {
            throw new IllegalArgumentException("successful attach result must not allow starting a new process");
        }
    }

    /**
     * Creates a failed attach result.
     *
     * @param summary human-readable attach summary
     * @return failed attach result
     */
    public static AttachResult failed(final String summary) {
        return failed(summary, false);
    }

    /**
     * Creates a failed attach result.
     *
     * @param summary human-readable attach summary
     * @param startNewAllowed whether it is safe to start a new PostgreSQL process after this attach failure
     * @return failed attach result
     */
    public static AttachResult failed(final String summary, final boolean startNewAllowed) {
        return failed(summary, startNewAllowed, emptyReport());
    }

    /**
     * Creates a failed attach result.
     *
     * @param summary human-readable attach summary
     * @param startNewAllowed whether it is safe to start a new PostgreSQL process after this attach failure
     * @param diagnosticReport structured attach diagnostics
     * @return failed attach result
     */
    public static AttachResult failed(
            final String summary, final boolean startNewAllowed, final DiagnosticReport diagnosticReport) {
        return new AttachResult(
                false,
                summary,
                Optional.empty(),
                startNewAllowed,
                Objects.requireNonNull(diagnosticReport, "diagnosticReport"));
    }

    /**
     * Creates a successful attach result.
     *
     * @param summary human-readable attach summary
     * @param handle attached PostgreSQL handle
     * @return successful attach result
     */
    public static AttachResult success(final String summary, final RunningPostgres handle) {
        return new AttachResult(
                true, summary, Optional.of(Objects.requireNonNull(handle, "handle")), false, emptyReport());
    }

    private static DiagnosticReport emptyReport() {
        return new DiagnosticReport(List.of());
    }
}
