package eu.virtualparadox.managedpostgres.lifecycle.layout;

import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.filesystem.DirectoryPublisher;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deletes temporary PostgreSQL cluster storage with shutdown diagnostics.
 */
public final class TemporaryClusterCleanup {

    private TemporaryClusterCleanup() {
    }

    /**
     * Performs the delete operation.
     *
     * @param layout layout value
     */
    public static void delete(final PostgresLayout layout) {
        final PostgresLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        try {
            DirectoryPublisher.deleteRecursivelyIfExists(checkedLayout.root());
        } catch (final UncheckedIOException exception) {
            throw new PostgresShutdownException(
                    "Failed to delete temporary PostgreSQL cluster",
                    exception,
                    new DiagnosticReport(List.of(new DiagnosticSection(
                            "temporary-cluster-cleanup",
                            Map.of("root", checkedLayout.root().toString())))));
        }
    }
}
