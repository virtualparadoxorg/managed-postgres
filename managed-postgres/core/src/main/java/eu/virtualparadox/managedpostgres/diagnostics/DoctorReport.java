package eu.virtualparadox.managedpostgres.diagnostics;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Non-mutating managed PostgreSQL doctor report.
 *
 * @param status lifecycle status observed by doctor
 * @param sections diagnostic sections
 */
public record DoctorReport(PostgresStatus status, List<DiagnosticSection> sections) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an immutable doctor report.
     *
     * @param status lifecycle status observed by doctor
     * @param sections diagnostic sections
     */
    public DoctorReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(sections, "sections");
        sections = List.copyOf(sections);
    }

    /**
     * Returns this doctor report as a generic diagnostic report.
     *
     * @return diagnostic report
     */
    public DiagnosticReport diagnosticReport() {
        return new DiagnosticReport(sections);
    }

    /**
     * Renders this doctor report as redacted plain text.
     *
     * @return redacted plain text report
     */
    public String renderText() {
        return diagnosticReport().renderText();
    }

    /**
     * Renders this doctor report as redacted stable JSON.
     *
     * @return redacted JSON report
     */
    public String renderJson() {
        return DoctorReportJsonRenderer.render(this);
    }
}
