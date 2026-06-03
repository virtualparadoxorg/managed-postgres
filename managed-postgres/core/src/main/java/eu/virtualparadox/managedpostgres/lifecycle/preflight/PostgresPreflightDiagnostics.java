package eu.virtualparadox.managedpostgres.lifecycle.preflight;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PostgresPreflightDiagnostics {

    private PostgresPreflightDiagnostics() {}

    static DiagnosticReport version(final Map<String, String> values) {
        return diagnostic("version-preflight", values);
    }

    static DiagnosticReport configDrift(final Map<String, String> values) {
        return diagnostic("config-drift", values);
    }

    private static DiagnosticReport diagnostic(final String section, final Map<String, String> values) {
        return new DiagnosticReport(List.of(new DiagnosticSection(
                Objects.requireNonNull(section, "section"), Objects.requireNonNull(values, "values"))));
    }
}
