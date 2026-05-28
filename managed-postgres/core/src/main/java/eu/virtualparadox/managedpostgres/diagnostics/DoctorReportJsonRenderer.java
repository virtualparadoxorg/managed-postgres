package eu.virtualparadox.managedpostgres.diagnostics;

import eu.virtualparadox.managedpostgres.internal.JsonStrings;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Renders doctor reports as stable redacted JSON.
 */
public final class DoctorReportJsonRenderer {

    private DoctorReportJsonRenderer() {
    }

    /**
     * Returns the render result.
     *
     * @param report report value
     * @return render result
     */
    public static String render(final DoctorReport report) {
        final DoctorReport checkedReport = Objects.requireNonNull(report, "report");
        final String lineSeparator = System.lineSeparator();
        final StringBuilder builder = new StringBuilder();

        builder.append("{").append(lineSeparator)
                .append("  \"status\": ").append(JsonStrings.quote(checkedReport.status().name()))
                .append(",").append(lineSeparator)
                .append("  \"sections\": [").append(lineSeparator);
        appendSections(builder, lineSeparator, checkedReport);
        builder.append("  ]").append(lineSeparator)
                .append("}").append(lineSeparator);

        return builder.toString();
    }

    private static void appendSections(
            final StringBuilder builder,
            final String lineSeparator,
            final DoctorReport report) {
        for (int index = 0; index < report.sections().size(); index++) {
            appendSection(builder, lineSeparator, report.sections().get(index));
            if (index + 1 < report.sections().size()) {
                builder.append(",");
            }
            builder.append(lineSeparator);
        }
    }

    private static void appendSection(
            final StringBuilder builder,
            final String lineSeparator,
            final DiagnosticSection section) {
        builder.append("    {").append(lineSeparator)
                .append("      \"name\": ").append(JsonStrings.quote(section.name())).append(",").append(lineSeparator)
                .append("      \"values\": {").append(lineSeparator);
        appendValues(builder, lineSeparator, section.values());
        builder.append("      }").append(lineSeparator)
                .append("    }");
    }

    private static void appendValues(
            final StringBuilder builder,
            final String lineSeparator,
            final Map<String, String> values) {
        final int[] index = {0};
        values.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> appendValue(builder, lineSeparator, values.size(), index, entry));
    }

    private static void appendValue(
            final StringBuilder builder,
            final String lineSeparator,
            final int valueCount,
            final int[] index,
            final Map.Entry<String, String> entry) {
        final String redactedValue = CommandRedactor.redact(CommandRedactor.redactValue(entry.getKey(), entry.getValue()));
        builder.append("        ")
                .append(JsonStrings.quote(entry.getKey()))
                .append(": ")
                .append(JsonStrings.quote(redactedValue));
        index[0]++;
        if (index[0] < valueCount) {
            builder.append(",");
        }
        builder.append(lineSeparator);
    }

}
