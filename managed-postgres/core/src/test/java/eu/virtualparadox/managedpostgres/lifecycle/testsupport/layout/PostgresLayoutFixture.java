package eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout;

import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import java.nio.file.Path;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;

public final class PostgresLayoutFixture {

    private PostgresLayoutFixture() {
    }

    public static PostgresLayout createdLayout(final Path storageRoot) {
        final FileSystemOperationJournal fileSystem = new FileSystemOperationJournal();
        final PostgresLayout layout = PostgresLayout.plan(new Storage(storageRoot, false), fileSystem);
        layout.createDirectories(fileSystem);

        return layout;
    }
}
