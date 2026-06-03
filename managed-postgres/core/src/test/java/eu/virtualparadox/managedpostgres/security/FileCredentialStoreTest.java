package eu.virtualparadox.managedpostgres.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperation;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFilePermissions;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class FileCredentialStoreTest {

    @TempDir
    private Path temporaryDirectory;

    FileCredentialStoreTest() {}

    @Test
    void credentialStoreWritesThroughManagedFileSystem() throws IOException {
        final RecordingManagedFileSystem fileSystem = new RecordingManagedFileSystem();
        final Path credentialsPath = Path.of("state", "credentials.properties");
        final CredentialStore store = new FileCredentialStore(credentialsPath, fileSystem);

        store.write(Credentials.of("postgres", Secret.of("file-credential-secret")));

        assertThat(fileSystem.writeCount()).isEqualTo(1);
        assertThat(fileSystem.path()).isEqualTo(credentialsPath);
        assertThat(fileSystem.contentLength()).isPositive();
        assertThat(fileSystem.permissions()).isEqualTo(ManagedFilePermissions.ownerOnlyReadWrite());
        assertThat(fileSystem.committed()).isTrue();
    }

    @Test
    void credentialStoreReadsPersistedCredentials() throws IOException {
        final Path credentialsPath = temporaryDirectory.resolve("credentials.properties");
        Files.writeString(
                credentialsPath,
                "username=postgres%npassword=persisted-secret%npersistent=true%nlocalTrustOnly=false%n".formatted(),
                StandardCharsets.UTF_8);

        final FileCredentialStore store = new FileCredentialStore(credentialsPath, new RecordingManagedFileSystem());

        assertThat(store.read()).hasValueSatisfying(credentials -> {
            assertThat(credentials.username()).isEqualTo("postgres");
            assertThat(credentials.password().reveal()).isEqualTo("persisted-secret");
            assertThat(credentials.persistent()).isTrue();
            assertThat(credentials.localTrustOnly()).isFalse();
        });
    }

    @Test
    void missingCredentialStoreReadsAsEmpty() throws IOException {
        final FileCredentialStore store = new FileCredentialStore(
                temporaryDirectory.resolve("missing.properties"), new RecordingManagedFileSystem());

        assertThat(store.read()).isEmpty();
    }

    @Test
    void credentialStoreRejectsMalformedCredentials() throws IOException {
        final Path credentialsPath = temporaryDirectory.resolve("credentials.properties");
        Files.writeString(credentialsPath, "password=persisted-secret%n".formatted(), StandardCharsets.UTF_8);
        final FileCredentialStore store = new FileCredentialStore(credentialsPath, new RecordingManagedFileSystem());

        assertThatThrownBy(store::read).isInstanceOf(IOException.class).hasMessageContaining("username");
    }

    private static final class RecordingManagedFileSystem implements ManagedFileSystem {

        private int writeCount;
        private Path path = Path.of("");
        private int contentLength;
        private ManagedFilePermissions permissions = ManagedFilePermissions.defaults();
        private boolean committed;

        @Override
        public void createDirectories(final Path directory) {}

        @Override
        public Path createTemporaryDirectory(final Path parentDirectory, final String prefix) {
            return parentDirectory.resolve(prefix + "test");
        }

        @Override
        public void deleteIfExists(final Path filePath) {}

        @Override
        public FileSystemOperation beginOperation(final String operationName, final Path operationRoot) {
            return new RecordingOperation();
        }

        int writeCount() {
            return writeCount;
        }

        Path path() {
            return path;
        }

        int contentLength() {
            return contentLength;
        }

        ManagedFilePermissions permissions() {
            return permissions;
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
            public void writeUtf8Atomically(final Path target, final String content) {
                writeCount++;
                path = target;
                contentLength = content.length();
            }

            @Override
            public void writeUtf8Atomically(
                    final Path target, final String content, final ManagedFilePermissions requestedPermissions) {
                writeUtf8Atomically(target, content);
                permissions = requestedPermissions;
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
