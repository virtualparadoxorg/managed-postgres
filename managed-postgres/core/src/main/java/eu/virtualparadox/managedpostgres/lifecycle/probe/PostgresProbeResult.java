package eu.virtualparadox.managedpostgres.lifecycle.probe;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Internal health probe result with redacted diagnostics.
 *
 * @param healthy whether the probe observed a healthy PostgreSQL service
 * @param summary human readable probe summary
 * @param diagnosticReport structured diagnostic report
 */
public record PostgresProbeResult(boolean healthy, String summary, DiagnosticReport diagnosticReport) {

    /**
     * Creates a probe result.
     *
     * @param healthy whether the probe observed a healthy PostgreSQL service
     * @param summary human readable probe summary
     * @param diagnosticReport structured diagnostic report
     */
    public PostgresProbeResult {
        if (StringUtils.isBlank(summary)) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        Objects.requireNonNull(diagnosticReport, "diagnosticReport");
    }

    /**
     * Creates a healthy result without extra diagnostics.
     *
     * @param summary human readable summary
     * @return healthy probe result
     */
    public static PostgresProbeResult healthy(final String summary) {
        return new PostgresProbeResult(true, summary, emptyReport());
    }

    /**
     * Creates an unhealthy result with diagnostics.
     *
     * @param summary human readable summary
     * @param diagnosticReport structured diagnostic report
     * @return unhealthy probe result
     */
    public static PostgresProbeResult unhealthy(final String summary, final DiagnosticReport diagnosticReport) {
        return new PostgresProbeResult(false, summary, diagnosticReport);
    }

    private static DiagnosticReport emptyReport() {
        return new DiagnosticReport(List.of());
    }
}
