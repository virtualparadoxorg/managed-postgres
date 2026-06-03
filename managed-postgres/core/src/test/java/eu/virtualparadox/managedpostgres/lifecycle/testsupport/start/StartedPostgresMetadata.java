package eu.virtualparadox.managedpostgres.lifecycle.testsupport.start;

import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.handle.StartedPostgresHandle;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;

public record StartedPostgresMetadata(String database, String owner) {

    public static StartedPostgresMetadata from(final RunningPostgres handle) {
        if (!(handle instanceof StartedPostgresHandle startedHandle)) {
            throw new IllegalArgumentException("handle must be a started PostgreSQL handle");
        }

        final PostgresLayout startedLayout = startedHandle.layout();
        final PostgresInstanceMetadata metadata = new MetadataStore(
                        startedLayout.metadataPath(), new FileSystemOperationJournal())
                .read()
                .orElseThrow();

        return new StartedPostgresMetadata(metadata.database(), metadata.owner());
    }
}
