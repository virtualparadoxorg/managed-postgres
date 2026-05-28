package eu.virtualparadox.managedpostgres.diagnostics;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Immutable collection of diagnostic sections.
 *
 * @param sections diagnostic sections
 */
public record DiagnosticReport(List<DiagnosticSection> sections) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an immutable diagnostic report.
     *
     * @param sections diagnostic sections
     */
    public DiagnosticReport {
        Objects.requireNonNull(sections, "sections");
        sections = List.copyOf(sections);
    }

    /**
     * Renders this report as redacted plain text.
     *
     * @return redacted plain text report
     */
    public String renderText() {
        return DiagnosticReportRenderer.text().render(this);
    }
}
