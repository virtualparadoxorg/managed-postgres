package eu.virtualparadox.managedpostgres.lifecycle.attach;

import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Builds one attach attempt from compatibility, process, port and JDBC checks.
 */
public final class PostgresAttachAttemptService {

    private final AttachValidation validation;
    private final PostgresAttachedHandleFactory handleFactory;

    /**
     * Creates a PostgresAttachAttemptService instance.
     *
     * @param validation validation value
     * @param handleFactory handle factory value
     */
    public PostgresAttachAttemptService(
            final AttachValidation validation,
            final PostgresAttachedHandleFactory handleFactory) {
        this.validation = Objects.requireNonNull(validation, "validation");
        this.handleFactory = Objects.requireNonNull(handleFactory, "handleFactory");
    }

    /**
     * Returns the attach result result.
     *
     * @param configuration configuration value
     * @param layout layout value
     * @param runtimeDirectory runtime directory value
     * @param metadata metadata value
     * @return attach result result
     */
    public AttachResult attachResult(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final Path runtimeDirectory,
            final PostgresInstanceMetadata metadata) {
        final PostgresAttachCompatibility compatibility = new PostgresAttachCompatibility();
        final Optional<String> mismatch = compatibility.mismatch(configuration, layout, metadata);
        final AttachResult result;
        if (mismatch.isEmpty()) {
            result = attacher(configuration, layout, runtimeDirectory).tryAttach(metadata);
        } else {
            result = AttachResult.failed(
                    "PostgreSQL metadata is incompatible: " + mismatch.orElseThrow(),
                    false,
                    compatibility.diagnosticReport(configuration, layout, metadata));
        }

        return result;
    }

    private PostgresAttacher attacher(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final Path runtimeDirectory) {
        return new PostgresAttacher(
                validation.processLookup(),
                validation.portProbe(),
                metadata -> validation.jdbcProbe().apply(new AttachJdbcProbeRequest(metadata, configuration, layout)),
                metadata -> handleFactory.attachedHandle(metadata, configuration, layout, runtimeDirectory));
    }
}
