package eu.virtualparadox.managedpostgres.scenario.support;

import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

public final class ScenarioMetadata {

    private static final String STATE_DIRECTORY = "state";
    private static final String METADATA_FILE = "metadata.json";
    private static final String STALE_METADATA_FILE = "metadata.stale.json";

    private ScenarioMetadata() {
    }

    public static Optional<PostgresInstanceMetadata> read(final Path storageRoot) {
        return new MetadataStore(metadataPath(storageRoot), new FileSystemOperationJournal()).read();
    }

    public static OptionalInt readPort(final Path storageRoot) {
        return new MetadataStore(metadataPath(storageRoot), new FileSystemOperationJournal()).readPort();
    }

    public static PostgresInstanceMetadata require(final Path storageRoot) {
        final Optional<PostgresInstanceMetadata> metadata = read(storageRoot);

        return metadata.orElseThrow(() -> new IllegalStateException("metadata not found: " + metadataPath(storageRoot)));
    }

    public static Path metadataPath(final Path storageRoot) {
        return storageRoot.resolve(STATE_DIRECTORY).resolve(METADATA_FILE);
    }

    public static Path staleMetadataPath(final Path storageRoot) {
        return storageRoot.resolve(STATE_DIRECTORY).resolve(STALE_METADATA_FILE);
    }
}
