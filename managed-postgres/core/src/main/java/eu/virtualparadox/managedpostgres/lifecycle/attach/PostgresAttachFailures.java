package eu.virtualparadox.managedpostgres.lifecycle.attach;

import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.ArrayList;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.List;
import java.util.Map;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;

/**
 * Creates attach failure diagnostics.
 */
public final class PostgresAttachFailures {

    private PostgresAttachFailures() {
    }

    /**
     * Returns the attach failure result.
     *
     * @param layout layout value
     * @param metadata metadata value
     * @param result result value
     * @return attach failure result
     */
    public static PostgresAttachException attachFailure(
            final PostgresLayout layout,
            final PostgresInstanceMetadata metadata,
            final AttachResult result) {
        final List<DiagnosticSection> sections = new ArrayList<>();
        sections.add(new DiagnosticSection(
                "postgres-attach",
                Map.of(
                        "root", layout.root().toString(),
                        "instanceId", metadata.instanceId(),
                        "reason", result.summary())));
        sections.addAll(result.diagnosticReport().sections());

        return new PostgresAttachException(
                "Existing PostgreSQL metadata could not be attached safely",
                new DiagnosticReport(sections));
    }
}
