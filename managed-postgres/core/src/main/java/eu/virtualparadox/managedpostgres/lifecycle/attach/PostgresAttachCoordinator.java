package eu.virtualparadox.managedpostgres.lifecycle.attach;

import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Coordinates start-or-attach decisions for persisted PostgreSQL metadata.
 */
public final class PostgresAttachCoordinator {

    private final PostgresAttachAttemptService attachAttemptService;

    /**
     * Creates a PostgresAttachCoordinator instance.
     *
     * @param attachAttemptService attach attempt service value
     */
    public PostgresAttachCoordinator(final PostgresAttachAttemptService attachAttemptService) {
        this.attachAttemptService = Objects.requireNonNull(attachAttemptService, "attachAttemptService");
    }

    /**
     * Attempts to attach to existing metadata when policy allows it.
     *
     * @param configuration startup configuration
     * @param layout PostgreSQL filesystem layout
     * @param runtimeDirectory PostgreSQL runtime directory
     * @param metadataStore metadata store
     * @return attached handle, or empty when startup should continue
     */
    public Optional<RunningPostgres> tryAttachExisting(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final Path runtimeDirectory,
            final MetadataStore metadataStore) {
        final Optional<RunningPostgres> handle;
        if (configuration.attachExisting()) {
            handle = metadataStore.read()
                    .flatMap(metadata -> attachOrMarkStale(configuration, layout, runtimeDirectory, metadataStore, metadata));
        } else {
            handle = Optional.empty();
        }

        return handle;
    }

    private Optional<RunningPostgres> attachOrMarkStale(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final Path runtimeDirectory,
            final MetadataStore metadataStore,
            final PostgresInstanceMetadata metadata) {
        final AttachResult result = attachAttemptService.attachResult(configuration, layout, runtimeDirectory, metadata);
        final Optional<RunningPostgres> handle;
        if (result.attached()) {
            handle = result.handle();
        } else if (result.startNewAllowed()) {
            metadataStore.markStale(metadata, result.summary());
            handle = Optional.empty();
        } else {
            throw PostgresAttachFailures.attachFailure(layout, metadata, result);
        }

        return handle;
    }
}
