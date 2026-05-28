package eu.virtualparadox.managedpostgres.diagnostics;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Renders diagnostic reports for human-readable output.
 */
public final class DiagnosticReportRenderer {

    private static final DiagnosticReportRenderer TEXT = new DiagnosticReportRenderer();

    private DiagnosticReportRenderer() {
    }

    /**
     * Returns the plain text diagnostic report renderer.
     *
     * @return plain text renderer
     */
    public static DiagnosticReportRenderer text() {
        return TEXT;
    }

    /**
     * Renders a diagnostic report as redacted plain text.
     *
     * @param report diagnostic report
     * @return redacted plain text report
     */
    public String render(final DiagnosticReport report) {
        final DiagnosticReport nonNullReport = Objects.requireNonNull(report, "report");
        final String lineSeparator = System.lineSeparator();
        final StringBuilder builder = new StringBuilder();

        for (final DiagnosticSection section : nonNullReport.sections()) {
            builder.append(section.name()).append(lineSeparator);
            section.values().entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> appendValue(builder, lineSeparator, entry));
        }

        return builder.toString();
    }

    private static void appendValue(
            final StringBuilder builder,
            final String lineSeparator,
            final Map.Entry<String, String> entry) {
        final String renderedValue = "%s=%s".formatted(
                entry.getKey(),
                CommandRedactor.redactValue(entry.getKey(), entry.getValue()));
        builder.append("  ")
                .append(CommandRedactor.redact(renderedValue))
                .append(lineSeparator);
    }
}
