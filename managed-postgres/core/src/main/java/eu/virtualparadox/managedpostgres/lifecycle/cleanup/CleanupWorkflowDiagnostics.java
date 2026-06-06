package eu.virtualparadox.managedpostgres.lifecycle.cleanup;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.List;
import java.util.Map;

final class CleanupWorkflowDiagnostics {

    private CleanupWorkflowDiagnostics() {}

    static DiagnosticReport cleanup(final String key, final String value) {
        return report("postgres-cleanup", key, value);
    }

    static DiagnosticReport destroy(final String key, final String value) {
        return report("postgres-destroy", key, value);
    }

    private static DiagnosticReport report(final String sectionName, final String key, final String value) {
        return new DiagnosticReport(List.of(new DiagnosticSection(sectionName, Map.of(key, value))));
    }
}
