package eu.virtualparadox.managedpostgres.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperation;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFilePermissions;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class MetadataStoreTest {

    @TempDir
    private Path temporaryDirectory;

    MetadataStoreTest() {}

    @Test
    void metadataWriteIsAtomic() throws IOException {
        final RecordingManagedFileSystem fileSystem = new RecordingManagedFileSystem();
        final Path metadataPath = Path.of("state", "metadata.json");
        final MetadataStore store = new MetadataStore(metadataPath, fileSystem);

        store.write(metadata());

        assertThat(fileSystem.atomicWriteCount()).isEqualTo(1);
        assertThat(fileSystem.path()).isEqualTo(metadataPath);
        assertThat(fileSystem.content()).contains("\"schemaVersion\":1");
        assertThat(fileSystem.committed()).isTrue();
    }

    @Test
    void metadataNeverContainsRawPassword() throws IOException {
        final RecordingManagedFileSystem fileSystem = new RecordingManagedFileSystem();
        final MetadataStore store = new MetadataStore(Path.of("state", "metadata.json"), fileSystem);
        final Secret password = Secret.of("metadata-password-secret");
        final String configHash = new ConfigHashCalculator().calculate(java.util.Map.of("password", password.reveal()));

        store.write(new PostgresInstanceMetadata(
                1,
                "instance-1",
                "cluster-1",
                "default",
                Path.of("pgdata"),
                "127.0.0.1",
                5432,
                "postgres",
                "postgres",
                "17.5",
                17,
                "create-new",
                1234L,
                configHash,
                Instant.parse("2026-05-27T10:15:30Z"),
                Instant.parse("2026-05-27T10:15:31Z")));

        assertThat(fileSystem.content().indexOf(password.reveal()))
                .as("metadata leaked a raw password")
                .isEqualTo(-1);
    }

    @Test
    void metadataPortCanBeReadBack() throws IOException {
        final Path metadataPath = temporaryDirectory.resolve("metadata.json");
        final MetadataStore store = new MetadataStore(metadataPath, new FileSystemOperationJournal());

        store.write(metadata());

        assertThat(store.readPort()).hasValue(5432);
    }

    @Test
    void metadataCanBeReadBack() {
        final Path metadataPath = temporaryDirectory.resolve("metadata.json");
        final MetadataStore store = new MetadataStore(metadataPath, new FileSystemOperationJournal());
        final PostgresInstanceMetadata metadata = metadata();

        store.write(metadata);

        assertThat(store.read()).hasValue(metadata);
    }

    @Test
    void missingMetadataReadsAsEmpty() {
        final Path metadataPath = temporaryDirectory.resolve("missing-metadata.json");
        final MetadataStore store = new MetadataStore(metadataPath, new FileSystemOperationJournal());

        assertThat(store.read()).isEmpty();
        assertThat(store.readPort()).isEmpty();
    }

    @Test
    void metadataDeleteUsesManagedFilesystem() {
        final RecordingManagedFileSystem fileSystem = new RecordingManagedFileSystem();
        final Path metadataPath = Path.of("state", "metadata.json");
        final MetadataStore store = new MetadataStore(metadataPath, fileSystem);

        store.delete();

        assertThat(fileSystem.deletedPath()).isEqualTo(metadataPath);
    }

    @Test
    void portReservationWriteIsAtomicAndReadable() {
        final Path metadataPath = temporaryDirectory.resolve("reserved-port.json");
        final MetadataStore store = new MetadataStore(metadataPath, new FileSystemOperationJournal());

        store.writePortReservation("default", 15432);

        assertThat(store.readPort()).hasValue(15432);
        assertThat(store.read()).isEmpty();
        assertThat(store.serializePortReservation("default", 15432)).contains("\"metadataKind\":\"port-reservation\"");
    }

    private static PostgresInstanceMetadata metadata() {
        return new PostgresInstanceMetadata(
                1,
                "instance-1",
                "cluster-1",
                "default",
                Path.of("pgdata"),
                "127.0.0.1",
                5432,
                "postgres",
                "postgres",
                "17.5",
                17,
                "create-new",
                1234L,
                "hash",
                Instant.parse("2026-05-27T10:15:30Z"),
                Instant.parse("2026-05-27T10:15:31Z"));
    }

    private static final class RecordingManagedFileSystem implements ManagedFileSystem {

        private int atomicWriteCount;
        private Path path = Path.of("");
        private Path deletedPath = Path.of("");
        private String content = "";
        private boolean committed;

        @Override
        public void createDirectories(final Path directory) {}

        @Override
        public Path createTemporaryDirectory(final Path parentDirectory, final String prefix) {
            return parentDirectory.resolve(prefix + "test");
        }

        @Override
        public void deleteIfExists(final Path filePath) {
            deletedPath = filePath;
        }

        @Override
        public FileSystemOperation beginOperation(final String operationName, final Path operationRoot) {
            return new RecordingOperation();
        }

        int atomicWriteCount() {
            return atomicWriteCount;
        }

        Path path() {
            return path;
        }

        String content() {
            return content;
        }

        Path deletedPath() {
            return deletedPath;
        }

        boolean committed() {
            return committed;
        }

        private final class RecordingOperation implements FileSystemOperation {

            private RecordingOperation() {}

            @Override
            public Path createStagingDirectory(final String name) {
                throw new UnsupportedOperationException("staging is not used by this test");
            }

            @Override
            public void writeUtf8Atomically(final Path target, final String metadataContent) {
                atomicWriteCount++;
                path = target;
                content = metadataContent;
            }

            @Override
            public void writeUtf8Atomically(
                    final Path target, final String metadataContent, final ManagedFilePermissions permissions) {
                writeUtf8Atomically(target, metadataContent);
            }

            @Override
            public void publishDirectory(final Path staging, final Path target) {
                throw new UnsupportedOperationException("directory publication is not used by this test");
            }

            @Override
            public void publishFile(final Path stagingFile, final Path target) {
                throw new UnsupportedOperationException("file publication is not used by this test");
            }

            @Override
            public void commit() {
                committed = true;
            }

            @Override
            public void close() {}
        }
    }
}
